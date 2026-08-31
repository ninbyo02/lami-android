package io.github.ninbyo02.lami.gpu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Base64
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

internal enum class BenchmarkBackendVariant(
    val wireValue: String,
    val backendLabel: String,
    val configStyle: String,
) {
    GPU("gpu", "GPU", "explicit_gpu"),
    CPU("cpu", "CPU", "explicit_cpu"),
    AUTOMATIC("automatic", "Automatic", "automatic"),
    GPU_NULL_MODALITIES("gpu-null-modalities", "GPU", "explicit_gpu_null_modalities"),
    GPU_CPU_MODALITIES("gpu-cpu-modalities", "GPU", "explicit_gpu_cpu_modalities"),
    GPU_CACHE_DIR("gpu-cache-dir", "GPU", "explicit_gpu_cache_dir"),
    GPU_NULL_MAX("gpu-null-max", "GPU", "explicit_gpu_null_max_tokens"),
    GPU_ALL("gpu-all", "GPU", "explicit_gpu_cache_null_modalities_null_max"),
    GALLERY_CHAT_PARITY("gallery-chat-parity", "GPU", "gallery_chat_parity_contents_callback");

    companion object {
        fun parse(raw: String?): BenchmarkBackendVariant {
            val normalized = raw?.trim()?.lowercase(Locale.US)
            return when (normalized) {
                "default" -> AUTOMATIC
                else -> entries.firstOrNull { it.wireValue == normalized } ?: GPU
            }
        }
    }
}

internal enum class BenchmarkClosePolicy(
    val wireValue: String,
) {
    NORMAL("normal"),
    SKIP_CONVERSATION("skip-conversation"),
    SKIP_ALL("skip-all");

    val intentionallyLeakedForDiagnostic: Boolean
        get() = this != NORMAL

    companion object {
        fun parse(raw: String?): BenchmarkClosePolicy =
            entries.firstOrNull { it.wireValue == raw?.trim()?.lowercase(Locale.US) } ?: NORMAL
    }
}

internal enum class BenchmarkPhase(
    val wireValue: String,
) {
    ENGINE_ONLY("engine-only"),
    CONVERSATION_ONLY("conversation-only"),
    SEND_MESSAGE("send-message");

    companion object {
        fun parse(raw: String?): BenchmarkPhase =
            entries.firstOrNull { it.wireValue == raw?.trim()?.lowercase(Locale.US) } ?: SEND_MESSAGE
    }
}

internal enum class BenchmarkModelPathSource(
    val wireValue: String,
) {
    AUTO("auto"),
    GENERIC_FALLBACK("generic_fallback");

    companion object {
        fun parse(raw: String?): BenchmarkModelPathSource =
            entries.firstOrNull { it.wireValue == raw?.trim()?.lowercase(Locale.US) } ?: AUTO
    }
}

internal enum class BenchmarkSendApiMode(val wireValue: String) {
    FLOW_STRING("flow_string"),
    TYPED_CONTENTS_CALLBACK("typed_contents_callback");

    companion object {
        fun parse(raw: String?): BenchmarkSendApiMode =
            entries.firstOrNull { it.wireValue == raw?.trim()?.lowercase(Locale.US) } ?: FLOW_STRING
    }
}

internal data class BenchmarkMeasurementEvidence(
    val measuredPrefillTokens: Int?,
    val outputTokens: Int?,
    val prefillTokenSource: String,
    val outputTokenSource: String,
) {
    companion object {
        private const val PREFILL_SOURCE = "LiteRT benchmarkInfo.lastPrefillTokenCount"
        private const val OUTPUT_SOURCE = "LiteRT benchmarkInfo.lastDecodeTokenCount"

        fun fromPublicApi(prefillTokens: Int?, decodeTokens: Int?): BenchmarkMeasurementEvidence {
            val measuredPrefillTokens = prefillTokens?.takeIf { it >= 0 }
            val outputTokens = decodeTokens?.takeIf { it >= 0 }
            return BenchmarkMeasurementEvidence(
                measuredPrefillTokens = measuredPrefillTokens,
                outputTokens = outputTokens,
                prefillTokenSource = if (measuredPrefillTokens != null) PREFILL_SOURCE else "unavailable",
                outputTokenSource = if (outputTokens != null) OUTPUT_SOURCE else "unavailable",
            )
        }
    }
}

internal fun flowExceptionType(sendApiMode: BenchmarkSendApiMode, throwable: Throwable?): String? {
    if (sendApiMode != BenchmarkSendApiMode.FLOW_STRING) return null
    return ((throwable as? SendObservationException)?.cause ?: throwable)?.javaClass?.name
}

internal fun rethrowCancellationOrInterrupt(throwable: Throwable) {
    when (throwable) {
        is CancellationException -> throw throwable
        is InterruptedException -> {
            Thread.currentThread().interrupt()
            throw throwable
        }
    }
}

internal fun cancelTimestampMatches(activeTimestamp: String?, requestedTimestamp: String): Boolean =
    activeTimestamp != null && activeTimestamp == requestedTimestamp

internal fun mergeFlowFailureWithBlockingFailure(
    partialFlowObservation: SendObservation?,
    blockingThrowable: Throwable,
): SendObservationException {
    rethrowCancellationOrInterrupt(blockingThrowable)
    val partial = partialFlowObservation ?: SendObservation(
        rawOutput = "",
        emitCount = 0,
        nonemptyEmitCount = 0,
        firstNonemptyMs = null,
        callbackOnMessageCount = 0,
        callbackOnDoneCount = 0,
        callbackOnErrorCount = 0,
        chunkTypeLengthSummary = "not_callback",
    )
    return SendObservationException(
        partial.withFlowPartialEvidence(partialFlowObservation),
        blockingThrowable,
    )
}

internal suspend fun collectStringFlowForBenchmark(
    chunks: Flow<String>,
    elapsedMs: () -> Long,
    cancelRequested: () -> Boolean = { false },
    onFirstToken: (Long) -> Unit = {},
): SendObservation {
    val builder = StringBuilder()
    var emitCount = 0
    var nonemptyEmitCount = 0
    var firstNonemptyMs: Long? = null
    var lastChunk: String? = null
    try {
        chunks.collect { chunk ->
            emitCount += 1
            if (cancelRequested()) throw CancellationException("debug_foreground_ui_cancelled")
            if (chunk.isBlank()) return@collect
            nonemptyEmitCount += 1
            if (firstNonemptyMs == null) {
                val observedFirstNonemptyMs = elapsedMs()
                firstNonemptyMs = observedFirstNonemptyMs
                onFirstToken(observedFirstNonemptyMs)
            }
            if (chunk == lastChunk) return@collect
            lastChunk = chunk
            builder.append(chunk)
        }
    } catch (throwable: Throwable) {
        rethrowCancellationOrInterrupt(throwable)
        throw SendObservationException(
            SendObservation(
                rawOutput = builder.toString(),
                emitCount = emitCount,
                nonemptyEmitCount = nonemptyEmitCount,
                firstNonemptyMs = firstNonemptyMs,
                callbackOnMessageCount = 0,
                callbackOnDoneCount = 0,
                callbackOnErrorCount = 0,
                chunkTypeLengthSummary = "not_callback",
            ),
            throwable,
        )
    }
    return SendObservation(
        rawOutput = builder.toString(),
        emitCount = emitCount,
        nonemptyEmitCount = nonemptyEmitCount,
        firstNonemptyMs = firstNonemptyMs,
        callbackOnMessageCount = 0,
        callbackOnDoneCount = 0,
        callbackOnErrorCount = 0,
        chunkTypeLengthSummary = "not_callback",
    )
}

@OptIn(ExperimentalApi::class)
class LiteRtLmGpuBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        if (intent.getBooleanExtra(EXTRA_COMMAND_CANCEL, false)) {
            val requestedTimestamp = intent.getStringExtra(EXTRA_TIMESTAMP)?.takeIf { it.isNotBlank() } ?: timestamp()
            val activeTimestamp = activeRunTimestamp.get()
            if (!cancelTimestampMatches(activeTimestamp, requestedTimestamp)) {
                writeMarker(
                    appContext = appContext,
                    timestamp = requestedTimestamp,
                    backendVariant = backendVariant(intent),
                    closePolicy = closePolicy(intent),
                    phase = phase(intent),
                    stage = "receiver_cancel_broadcast_ignored",
                    detail = "timestamp_mismatch active_timestamp=${activeTimestamp.orEmpty()}",
                )
                return
            }
            writeMarker(
                appContext = appContext,
                timestamp = requestedTimestamp,
                backendVariant = backendVariant(intent),
                closePolicy = closePolicy(intent),
                phase = phase(intent),
                stage = "receiver_cancel_broadcast_received",
                detail = "explicit_frontend_cancel_broadcast",
            )
            val cancelResult = goAsync()
            cancelCloseDispatcher.execute {
                try {
                    cancelCurrentRun()
                } finally {
                    cancelResult.finish()
                }
            }
            return
        }
        val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP)
            ?.takeIf { it.isNotBlank() }
            ?: timestamp()
        val timeoutMs = timeoutMs(intent)
        val deadlineElapsedRealtime = deadlineElapsedRealtime(intent)
        val backendVariant = backendVariant(intent)
        val closePolicy = closePolicy(intent)
        val phase = phase(intent)
        val modelPathSource = modelPathSource(intent)
        val sendApiMode = BenchmarkSendApiMode.parse(intent.getStringExtra(EXTRA_SEND_API_MODE))
        writeMarker(
            appContext = appContext,
            timestamp = timestamp,
            backendVariant = backendVariant,
            closePolicy = closePolicy,
            phase = phase,
            stage = "receiver_started",
            detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} model_path_source=${modelPathSource.wireValue} onReceive_enter",
        )
        val stateFile = File(appContext.filesDir, STATE_FILE_NAME)
        if (!running.compareAndSet(false, true)) {
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "receiver_started",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} model_path_source=${modelPathSource.wireValue} already_running",
            )
            writeState(
                stateFile = stateFile,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                status = "blocked",
                reason = "already_running",
                markdownFileName = "",
                csvFileName = "",
                timeoutMs = timeoutMs,
                modelPathSource = modelPathSource.wireValue,
            )
            return
        }
        activeRunTimestamp.set(timestamp)
        cancelRequested.set(false)
        val receiverCancelMarker: (String, String) -> Unit = { stage, detail ->
            writeMarker(appContext, timestamp, backendVariant, closePolicy, phase, stage, detail)
        }
        activeCancelMarker.set(receiverCancelMarker)
        val receiverCancelWatcher = startCancelRelayWatcher(appContext, timestamp)

        val pendingResult = goAsync()
        receiverDispatcher.execute {
            try {
                handle(
                    appContext,
                    intent,
                    timestamp,
                    timeoutMs,
                    deadlineElapsedRealtime,
                    backendVariant,
                    closePolicy,
                    phase,
                    modelPathSource,
                    sendApiMode,
                )
            } catch (throwable: Throwable) {
                rethrowCancellationOrInterrupt(throwable)
                writeMarker(
                    appContext = appContext,
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    stage = "receiver_exception",
                    detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} model_path_source=${modelPathSource.wireValue} ${throwable.javaClass.simpleName}:${throwable.message.orEmpty().take(120)}",
                )
                val row = LiteRtLmGpuBenchmarkRow.failure(
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    maxOutputTokensList = "unknown",
                    prompt = "",
                    maxOutputTokens = 0,
                    modelPath = "",
                    reason = throwable.message?.takeIf { it.isNotBlank() }
                        ?: "receiver_exception:${throwable.javaClass.simpleName}",
                    timeout = false,
                    freshCrash = false,
                    modelPathSource = modelPathSource.wireValue,
                    genericFallbackModelConfigured = modelPathSource != BenchmarkModelPathSource.GENERIC_FALLBACK,
                )
                writeReports(
                    appContext = appContext,
                    timestamp = timestamp,
                    timeoutMs = timeoutMs,
                    rows = listOf(row),
                )
                writeState(
                    stateFile = stateFile,
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    status = "failure",
                    reason = row.reason,
                    markdownFileName = markdownFileName(timestamp),
                    csvFileName = csvFileName(timestamp),
                    timeoutMs = timeoutMs,
                    modelPathSource = modelPathSource.wireValue,
                    genericFallbackModelConfigured = row.genericFallbackModelConfigured,
                    requestedRunCount = 1,
                    completedRunCount = 1,
                    successCount = 0,
                    failureCount = 1,
                    timeoutCount = 0,
                    fallbackCount = 0,
                )
            } finally {
                receiverCancelWatcher.cancel(true)
                activeRunTimestamp.compareAndSet(timestamp, null)
                activeCancelMarker.compareAndSet(receiverCancelMarker, null)
                val requireProcessCleanup = closeTimeoutRequiresProcessCleanup.getAndSet(false)
                if (!requireProcessCleanup) running.set(false)
                pendingResult.finish()
                if (requireProcessCleanup) {
                    processCleanupDispatcher.execute {
                        Thread.sleep(PROCESS_CLEANUP_DELAY_MS)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            }
        }
    }

    private fun handle(
        appContext: Context,
        intent: Intent,
        timestamp: String,
        timeoutMs: Long,
        deadlineElapsedRealtime: Long,
        backendVariant: BenchmarkBackendVariant,
        closePolicy: BenchmarkClosePolicy,
        phase: BenchmarkPhase,
        modelPathSource: BenchmarkModelPathSource,
        sendApiMode: BenchmarkSendApiMode,
    ) {
        val stateFile = File(appContext.filesDir, STATE_FILE_NAME)
        if (!BuildConfig.DEBUG || BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            val row = LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                maxOutputTokensList = "unknown",
                prompt = "",
                maxOutputTokens = 0,
                modelPath = "",
                reason = "wrong_variant",
                timeout = false,
                freshCrash = false,
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = modelPathSource != BenchmarkModelPathSource.GENERIC_FALLBACK,
            )
            writeReports(appContext, timestamp, timeoutMs, listOf(row))
            writeState(
                stateFile = stateFile,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                status = "blocked",
                reason = "wrong_variant",
                markdownFileName = markdownFileName(timestamp),
                csvFileName = csvFileName(timestamp),
                timeoutMs = timeoutMs,
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = row.genericFallbackModelConfigured,
                requestedRunCount = 1,
                completedRunCount = 1,
                successCount = 0,
                failureCount = 1,
                timeoutCount = 0,
                fallbackCount = 0,
            )
            return
        }

        val prompts = prompts(intent)
        val maxOutputTokensValues = maxOutputTokensValues(intent)
        val maxOutputTokensList = maxOutputTokensValues.joinToString(",")
        val requestedRunCount = prompts.size * maxOutputTokensValues.size
        val modelResolution = resolveModelPath(appContext, intent, modelPathSource)
        val modelPath = modelResolution.modelPath
        val modelFile = modelPath?.let(::File)
        writeMarker(
            appContext = appContext,
            timestamp = timestamp,
            backendVariant = backendVariant,
            closePolicy = closePolicy,
            phase = phase,
            stage = "model_resolved",
            detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} max_output_tokens_list=$maxOutputTokensList model_path_source=${modelResolution.source.wireValue} generic_fallback_model_configured=${modelResolution.genericFallbackModelConfigured} model_path=${modelPath.orEmpty()} model_exists=${modelFile?.exists() ?: false} model_length=${modelFile?.length() ?: 0L}",
            maxOutputTokensList = maxOutputTokensList,
        )
        writeState(
            stateFile = stateFile,
            timestamp = timestamp,
            backendVariant = backendVariant,
            closePolicy = closePolicy,
            phase = phase,
            status = "running",
            reason = "benchmark_running",
            markdownFileName = "",
            csvFileName = "",
            timeoutMs = timeoutMs,
            modelPathSource = modelResolution.source.wireValue,
            genericFallbackModelConfigured = modelResolution.genericFallbackModelConfigured,
            requestedRunCount = requestedRunCount,
        )

        val rows = mutableListOf<LiteRtLmGpuBenchmarkRow>()
        if (modelPath.isNullOrBlank()) {
            val reason = if (modelResolution.source == BenchmarkModelPathSource.GENERIC_FALLBACK) {
                "generic_fallback_model_missing"
            } else {
                "model_path_not_configured"
            }
            prompts.forEach { prompt ->
                maxOutputTokensValues.forEach { maxOutputTokens ->
                    rows += LiteRtLmGpuBenchmarkRow.failure(
                        timestamp = timestamp,
                        backendVariant = backendVariant,
                        closePolicy = closePolicy,
                        phase = phase,
                        maxOutputTokensList = maxOutputTokensList,
                        prompt = prompt,
                        maxOutputTokens = maxOutputTokens,
                        modelPath = "",
                        reason = reason,
                        timeout = false,
                        freshCrash = false,
                        modelPathSource = modelResolution.source.wireValue,
                        genericFallbackModelConfigured = modelResolution.genericFallbackModelConfigured,
                    )
                }
            }
        } else if (modelFile?.isFile != true || modelFile.canRead() != true || modelFile.length() <= 0L) {
            prompts.forEach { prompt ->
                maxOutputTokensValues.forEach { maxOutputTokens ->
                    rows += LiteRtLmGpuBenchmarkRow.failure(
                        timestamp = timestamp,
                        backendVariant = backendVariant,
                        closePolicy = closePolicy,
                        phase = phase,
                        maxOutputTokensList = maxOutputTokensList,
                        prompt = prompt,
                        maxOutputTokens = maxOutputTokens,
                        modelPath = modelPath,
                        reason = "model_file_invalid",
                        modelExists = modelFile?.exists() == true,
                        modelLength = modelFile?.length() ?: 0L,
                        timeout = false,
                        freshCrash = false,
                        modelPathSource = modelResolution.source.wireValue,
                        genericFallbackModelConfigured = modelResolution.genericFallbackModelConfigured,
                    )
                }
            }
        } else {
            var stopAfterUnsafeResourceState = false
            prompts.forEach { prompt ->
                maxOutputTokensValues.forEach { maxOutputTokens ->
                    if (stopAfterUnsafeResourceState) {
                        rows += LiteRtLmGpuBenchmarkRow.failure(
                            timestamp = timestamp,
                            backendVariant = backendVariant,
                            closePolicy = closePolicy,
                            phase = phase,
                            maxOutputTokensList = maxOutputTokensList,
                            prompt = prompt,
                            maxOutputTokens = maxOutputTokens,
                            modelPath = modelPath,
                            reason = "skipped_after_unsafe_resource_state",
                            modelExists = true,
                            modelLength = modelFile.length(),
                            timeout = false,
                            freshCrash = false,
                            modelPathSource = modelResolution.source.wireValue,
                            genericFallbackModelConfigured = modelResolution.genericFallbackModelConfigured,
                        )
                    } else {
                        val result = runCaseWithTimeout(
                            appContext = appContext,
                            timestamp = timestamp,
                            backendVariant = backendVariant,
                            closePolicy = closePolicy,
                            phase = phase,
                            maxOutputTokensList = maxOutputTokensList,
                            prompt = prompt,
                            maxOutputTokens = maxOutputTokens,
                            modelPath = modelPath,
                            modelLength = modelFile.length(),
                            deadlineElapsedRealtime = deadlineElapsedRealtime,
                            modelPathSource = modelResolution.source,
                            genericFallbackModelConfigured = modelResolution.genericFallbackModelConfigured,
                            sendApiMode = sendApiMode,
                        )
                        rows += result
                        if (result.timeout || closeTimeoutRequiresProcessCleanup.get()) {
                            stopAfterUnsafeResourceState = true
                        }
                    }
                }
            }
        }

        writeReports(appContext, timestamp, timeoutMs, rows)
        writeMarker(
            appContext = appContext,
            timestamp = timestamp,
            backendVariant = backendVariant,
            closePolicy = closePolicy,
            phase = phase,
            stage = "report_written",
            detail = "backend=${backendVariant.backendLabel} backend_variant=${backendVariant.wireValue} config_style=${backendVariant.configStyle} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} max_output_tokens_list=$maxOutputTokensList rows=${rows.size} markdown=${markdownFileName(timestamp)} csv=${csvFileName(timestamp)}",
            maxOutputTokensList = maxOutputTokensList,
        )
        val summary = buildLiteRtLmGpuBenchmarkRunSummary(
            rows = rows,
            requestedRunCount = requestedRunCount,
            modelPathSource = modelResolution.source.wireValue,
            genericFallbackModelConfigured = modelResolution.genericFallbackModelConfigured,
        )
        writeState(
            stateFile = stateFile,
            timestamp = timestamp,
            backendVariant = backendVariant,
            closePolicy = closePolicy,
            phase = phase,
            status = summary.status,
            reason = summary.reason,
            markdownFileName = markdownFileName(timestamp),
            csvFileName = csvFileName(timestamp),
            timeoutMs = timeoutMs,
            modelPathSource = summary.modelPathSource,
            genericFallbackModelConfigured = summary.genericFallbackModelConfigured,
            requestedRunCount = summary.requestedRunCount,
            completedRunCount = summary.completedRunCount,
            successCount = summary.successCount,
            failureCount = summary.failureCount,
            timeoutCount = summary.timeoutCount,
            fallbackCount = summary.fallbackCount,
        )
    }

    private fun runCaseWithTimeout(
        appContext: Context,
        timestamp: String,
        backendVariant: BenchmarkBackendVariant,
        closePolicy: BenchmarkClosePolicy,
        phase: BenchmarkPhase,
        prompt: String,
        maxOutputTokens: Int,
        modelPath: String,
        modelLength: Long,
        maxOutputTokensList: String,
        deadlineElapsedRealtime: Long,
        modelPathSource: BenchmarkModelPathSource,
        genericFallbackModelConfigured: Boolean,
        sendApiMode: BenchmarkSendApiMode,
    ): LiteRtLmGpuBenchmarkRow {
        val caseBudgetMs =
            (remainingRunBudgetMs(deadlineElapsedRealtime) - TERMINAL_PUBLISH_RESERVE_MS).coerceAtLeast(0L)
        if (caseBudgetMs <= 0L) {
            closeTimeoutRequiresProcessCleanup.set(true)
            return LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                maxOutputTokensList = maxOutputTokensList,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                modelPath = modelPath,
                reason = "run_deadline_expired_before_case_launch",
                modelExists = true,
                modelLength = modelLength,
                timeout = true,
                freshCrash = false,
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = genericFallbackModelConfigured,
            )
        }
        closeTimedOut.set(false)
        closeFailureReason.set(null)
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmark-$maxOutputTokens")
        }
        val future = executor.submit(
            Callable {
                runCase(
                    appContext = appContext,
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    prompt = prompt,
                    maxOutputTokens = maxOutputTokens,
                    modelPath = modelPath,
                    modelLength = modelLength,
                    maxOutputTokensList = maxOutputTokensList,
                    caseTimeoutMs = caseBudgetMs,
                    deadlineElapsedRealtime = deadlineElapsedRealtime,
                    modelPathSource = modelPathSource,
                    genericFallbackModelConfigured = genericFallbackModelConfigured,
                    sendApiMode = sendApiMode,
                )
            },
        )
        activeCaseFuture.set(future)
        if (cancelRequested.get()) {
            cancelCurrentRun()
        }
        return try {
            future.get(caseBudgetMs, TimeUnit.MILLISECONDS).let { row ->
                val closeFailure = closeFailureReason.get()
                when {
                    closeTimedOut.get() ->
                        row.copy(status = "failure", reason = "resource_close_timeout", timeout = true)
                    closeFailure != null ->
                        row.copy(status = "failure", reason = closeFailure, timeout = false)
                    else -> row
                }
            }
        } catch (_: TimeoutException) {
            future.cancel(true)
            closeTimeoutRequiresProcessCleanup.set(true)
            activeCancelMarker.get()?.invoke(
                "case_timeout_process_cleanup_required",
                "timeout_ms=$caseBudgetMs worker_may_still_be_native_blocked=true",
            )
            LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                maxOutputTokensList = maxOutputTokensList,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                modelPath = modelPath,
                reason = "case_timeout_${caseBudgetMs}ms",
                modelExists = true,
                modelLength = modelLength,
                timeout = true,
                freshCrash = false,
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = genericFallbackModelConfigured,
            )
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            future.cancel(true)
            throw interrupted
        } catch (_: CancellationException) {
            activeCancelMarker.get()?.invoke(
                "case_cancelled",
                "reason=cancelled_by_debug_foreground_ui future_cancelled=true",
            )
            val closeTimeout = closeTimedOut.get()
            LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                maxOutputTokensList = maxOutputTokensList,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                modelPath = modelPath,
                reason = if (closeTimeout) "engine_close_timeout" else "cancelled_by_debug_foreground_ui",
                modelExists = true,
                modelLength = modelLength,
                timeout = closeTimeout,
                freshCrash = false,
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = genericFallbackModelConfigured,
            )
        } catch (throwable: Throwable) {
            rethrowCancellationOrInterrupt((throwable as? ExecutionException)?.cause ?: throwable)
            LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                maxOutputTokensList = maxOutputTokensList,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                modelPath = modelPath,
                reason = throwable.message?.takeIf { it.isNotBlank() }
                    ?: "case_exception:${throwable.javaClass.simpleName}",
                modelExists = true,
                modelLength = modelLength,
                timeout = false,
                freshCrash = false,
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = genericFallbackModelConfigured,
            )
        } finally {
            activeCaseFuture.compareAndSet(future, null)
            executor.shutdownNow()
        }
    }

    private fun runCase(
        appContext: Context,
        timestamp: String,
        backendVariant: BenchmarkBackendVariant,
        closePolicy: BenchmarkClosePolicy,
        phase: BenchmarkPhase,
        prompt: String,
        maxOutputTokens: Int,
        modelPath: String,
        modelLength: Long,
        maxOutputTokensList: String,
        caseTimeoutMs: Long,
        deadlineElapsedRealtime: Long,
        modelPathSource: BenchmarkModelPathSource,
        genericFallbackModelConfigured: Boolean,
        sendApiMode: BenchmarkSendApiMode,
    ): LiteRtLmGpuBenchmarkRow {
        val totalStartMs = SystemClock.elapsedRealtime()
        var engine: Engine? = null
        var conversation: Conversation? = null
        var fallbackUsed = false
        var engineCreateMs: Long? = null
        var conversationCreateMs: Long? = null
        var firstTokenMs: Long? = null
        var decodeMs: Long? = null
        var benchmarkSnapshot: BenchmarkSnapshot? = null
        var sendException: Throwable? = null
        var flowCollectException: Throwable? = null
        return try {
            val configParts = resolveEngineConfigParts(
                appContext = appContext,
                backendVariant = backendVariant,
                maxOutputTokens = maxOutputTokens,
            )
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "backend_selected",
                detail = "close_policy=${closePolicy.wireValue} phase=${phase.wireValue} ${configParts.markerDetail(backendVariant, maxOutputTokens)}",
                maxOutputTokensList = maxOutputTokensList,
            )
            if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY) {
                writeMarker(
                    appContext = appContext,
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    stage = "gallery_parity_config_selected",
                    detail = "send_api_variant=gallery_contents_callback sampler_top_k=$GALLERY_CHAT_PARITY_TOP_K sampler_top_p=$GALLERY_CHAT_PARITY_TOP_P sampler_temperature=$GALLERY_CHAT_PARITY_TEMPERATURE conversation_config_used=true contents_api_used=true ${configParts.markerDetail(backendVariant, maxOutputTokens)}",
                    maxOutputTokensList = maxOutputTokensList,
                )
            }
            val config = configParts.buildEngineConfig(modelPath)
            val engineStartMs = SystemClock.elapsedRealtime()
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "engine_create_started",
                detail = "close_policy=${closePolicy.wireValue} phase=${phase.wireValue} max_output_tokens_list=$maxOutputTokensList ${configParts.markerDetail(backendVariant, maxOutputTokens)} prompt_length=${prompt.length}",
                maxOutputTokensList = maxOutputTokensList,
            )
            try {
                engine = Engine(config)
                engine.initialize()
                activeEngine.set(engine)
                if (cancelRequested.get()) throw CancellationException("cancelled_by_debug_foreground_ui")
                engineCreateMs = SystemClock.elapsedRealtime() - engineStartMs
            } catch (throwable: Throwable) {
                writeMarker(
                    appContext = appContext,
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    stage = "engine_create_failed",
                    detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} max_output_tokens=$maxOutputTokens max_output_tokens_list=$maxOutputTokensList ${configParts.markerDetail(backendVariant, maxOutputTokens)} class=${throwable.javaClass.name} message=${throwable.message.orEmpty().take(200)} cause_chain=${causeChainText(throwable)}",
                    maxOutputTokensList = maxOutputTokensList,
                )
                throw throwable
            }
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "engine_create_finished",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} max_output_tokens=$maxOutputTokens max_output_tokens_list=$maxOutputTokensList engine_create_ms=$engineCreateMs",
                maxOutputTokensList = maxOutputTokensList,
            )
            if (phase == BenchmarkPhase.ENGINE_ONLY) {
                val totalMs = SystemClock.elapsedRealtime() - totalStartMs
                return LiteRtLmGpuBenchmarkRow(
                    timestamp = timestamp,
                    routeType = ROUTE_TYPE,
                    backend = backendVariant.backendLabel,
                    backendVariant = backendVariant.wireValue,
                    closePolicy = closePolicy.wireValue,
                    phase = phase.wireValue,
                    prompt = prompt,
                    maxOutputTokens = maxOutputTokens,
                    maxOutputTokensList = maxOutputTokensList,
                    modelPath = modelPath,
                    modelExists = true,
                    modelLength = modelLength,
                    engineCreateMs = engineCreateMs,
                    conversationCreateMs = null,
                    firstTokenMs = null,
                    ttftMs = null,
                    decodeMs = null,
                    totalMs = totalMs,
                    outputTokens = null,
                    tokensPerSecond = null,
                    finishReason = null,
                    stopReason = null,
                    rawOutput = "",
                    sanitizedOutput = "",
                    status = "success",
                    reason = "engine_created",
                    sendExceptionClass = null,
                    sendExceptionMessage = null,
                    sendExceptionCauseChain = null,
                    intentionallyLeakedForDiagnostic = closePolicy.intentionallyLeakedForDiagnostic,
                    fallbackUsed = fallbackUsed,
                    timeout = false,
                    freshCrash = false,
                    sendApiVariant = sendApiVariant(backendVariant),
                    samplerTopK = samplerTopK(backendVariant),
                    samplerTopP = samplerTopP(backendVariant),
                    samplerTemperature = samplerTemperature(backendVariant),
                    conversationConfigUsed = conversationConfigUsed(backendVariant),
                    contentsApiUsed = contentsApiUsed(backendVariant),
                    modelPathSource = modelPathSource.wireValue,
                    genericFallbackModelConfigured = genericFallbackModelConfigured,
                )
            }

            val conversationStartMs = SystemClock.elapsedRealtime()
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "conversation_create_started",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} max_output_tokens=$maxOutputTokens max_output_tokens_list=$maxOutputTokensList",
                maxOutputTokensList = maxOutputTokensList,
            )
            val conversationConfig = if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY) {
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = GALLERY_CHAT_PARITY_TOP_K,
                        topP = GALLERY_CHAT_PARITY_TOP_P,
                        temperature = GALLERY_CHAT_PARITY_TEMPERATURE,
                    ),
                ).also {
                    writeMarker(
                        appContext = appContext,
                        timestamp = timestamp,
                        backendVariant = backendVariant,
                        closePolicy = closePolicy,
                        phase = phase,
                        stage = "conversation_config_created",
                        detail = "conversation_config_used=true sampler_top_k=$GALLERY_CHAT_PARITY_TOP_K sampler_top_p=$GALLERY_CHAT_PARITY_TOP_P sampler_temperature=$GALLERY_CHAT_PARITY_TEMPERATURE",
                        maxOutputTokensList = maxOutputTokensList,
                    )
                }
            } else {
                null
            }
            conversation = if (conversationConfig != null) {
                engine.createConversation(conversationConfig)
            } else {
                engine.createConversation()
            }
            activeConversation.set(conversation)
            if (cancelRequested.get()) throw CancellationException("cancelled_by_debug_foreground_ui")
            conversationCreateMs = SystemClock.elapsedRealtime() - conversationStartMs
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "conversation_create_finished",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} max_output_tokens=$maxOutputTokens max_output_tokens_list=$maxOutputTokensList conversation_create_ms=$conversationCreateMs",
                maxOutputTokensList = maxOutputTokensList,
            )
            if (phase == BenchmarkPhase.CONVERSATION_ONLY) {
                val totalMs = SystemClock.elapsedRealtime() - totalStartMs
                return LiteRtLmGpuBenchmarkRow(
                    timestamp = timestamp,
                    routeType = ROUTE_TYPE,
                    backend = backendVariant.backendLabel,
                    backendVariant = backendVariant.wireValue,
                    closePolicy = closePolicy.wireValue,
                    phase = phase.wireValue,
                    prompt = prompt,
                    maxOutputTokens = maxOutputTokens,
                    maxOutputTokensList = maxOutputTokensList,
                    modelPath = modelPath,
                    modelExists = true,
                    modelLength = modelLength,
                    engineCreateMs = engineCreateMs,
                    conversationCreateMs = conversationCreateMs,
                    firstTokenMs = null,
                    ttftMs = null,
                    decodeMs = null,
                    totalMs = totalMs,
                    outputTokens = null,
                    tokensPerSecond = null,
                    finishReason = null,
                    stopReason = null,
                    rawOutput = "",
                    sanitizedOutput = "",
                    status = "success",
                    reason = "conversation_created",
                    sendExceptionClass = null,
                    sendExceptionMessage = null,
                    sendExceptionCauseChain = null,
                    intentionallyLeakedForDiagnostic = closePolicy.intentionallyLeakedForDiagnostic,
                    fallbackUsed = fallbackUsed,
                    timeout = false,
                    freshCrash = false,
                    sendApiVariant = sendApiVariant(backendVariant),
                    samplerTopK = samplerTopK(backendVariant),
                    samplerTopP = samplerTopP(backendVariant),
                    samplerTemperature = samplerTemperature(backendVariant),
                    conversationConfigUsed = conversationConfigUsed(backendVariant),
                    contentsApiUsed = contentsApiUsed(backendVariant),
                    modelPathSource = modelPathSource.wireValue,
                    genericFallbackModelConfigured = genericFallbackModelConfigured,
                )
            }

            val decodeStartMs = SystemClock.elapsedRealtime()
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "prompt_started",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} max_output_tokens=$maxOutputTokens max_output_tokens_list=$maxOutputTokensList prompt_length=${prompt.length}",
                maxOutputTokensList = maxOutputTokensList,
            )
            val sendObservation = try {
                if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY || sendApiMode == BenchmarkSendApiMode.TYPED_CONTENTS_CALLBACK) {
                    collectGalleryParityCallbackResponse(
                        appContext = appContext,
                        timestamp = timestamp,
                        backendVariant = backendVariant,
                        closePolicy = closePolicy,
                        phase = phase,
                        maxOutputTokensList = maxOutputTokensList,
                        conversation = conversation,
                        prompt = prompt,
                        decodeStartMs = decodeStartMs,
                        callbackTimeoutMs = caseTimeoutMs,
                    ) { first ->
                        firstTokenMs = first
                    }
                } else {
                    collectStreamingResponse(conversation, prompt, decodeStartMs) { first ->
                        firstTokenMs = first
                    }
                }
            } catch (streamingThrowable: Throwable) {
                rethrowCancellationOrInterrupt(streamingThrowable)
                if (cancelRequested.get() || Thread.currentThread().isInterrupted) {
                    throw CancellationException("debug_foreground_ui_cancelled")
                }
                sendException = streamingThrowable
                if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY || sendApiMode == BenchmarkSendApiMode.TYPED_CONTENTS_CALLBACK) {
                    throw streamingThrowable
                } else {
                    flowCollectException = streamingThrowable
                    fallbackUsed = true
                    try {
                        val message = conversation.sendMessage(prompt)
                        val blockingOutput = message.contents.toString()
                        firstTokenMs = (SystemClock.elapsedRealtime() - decodeStartMs).takeIf { blockingOutput.isNotBlank() }
                        SendObservation.blocking(blockingOutput, firstTokenMs).withFlowPartialEvidence(
                            (streamingThrowable as? SendObservationException)?.observation,
                        )
                    } catch (blockingThrowable: Throwable) {
                        rethrowCancellationOrInterrupt(blockingThrowable)
                        runCatching { blockingThrowable.addSuppressed(streamingThrowable) }
                        throw mergeFlowFailureWithBlockingFailure(
                            (streamingThrowable as? SendObservationException)?.observation,
                            blockingThrowable,
                        )
                    }
                }
            }
            val rawOutput = sendObservation.rawOutput
            val decodeDurationMs = SystemClock.elapsedRealtime() - decodeStartMs
            decodeMs = decodeDurationMs
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "prompt_finished",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} max_output_tokens=$maxOutputTokens max_output_tokens_list=$maxOutputTokensList decode_ms=$decodeDurationMs raw_length=${rawOutput.length}",
                maxOutputTokensList = maxOutputTokensList,
            )
            benchmarkSnapshot = probeBenchmarkSnapshot(conversation)
            val sanitizedOutput = sanitizeOutput(rawOutput)
            val measurementEvidence = BenchmarkMeasurementEvidence.fromPublicApi(
                prefillTokens = benchmarkSnapshot?.measuredPrefillTokens,
                decodeTokens = benchmarkSnapshot?.decodeTokenCount,
            )
            val outputTokens = measurementEvidence.outputTokens
            val tokensPerSecond = benchmarkSnapshot?.decodeTokensPerSecond?.takeIf { it > 0.0 }
                ?: if (outputTokens != null && decodeDurationMs > 0L) {
                    outputTokens * 1000.0 / decodeDurationMs
                } else {
                    null
                }
            val totalMs = SystemClock.elapsedRealtime() - totalStartMs
            val finishReason = probeNoArgString(conversation, FINISH_REASON_METHODS)
                ?: probeNoArgString(engine, FINISH_REASON_METHODS)
            val stopReason = probeNoArgString(conversation, STOP_REASON_METHODS)
                ?: probeNoArgString(engine, STOP_REASON_METHODS)
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "benchmark_evidence",
                detail = "measured_prefill_tokens=${measurementEvidence.measuredPrefillTokens ?: "unavailable"} prefill_token_source=${measurementEvidence.prefillTokenSource} output_token_count=${measurementEvidence.outputTokens ?: "unavailable"} output_token_source=${measurementEvidence.outputTokenSource} emit_count=${sendObservation.emitCount} nonempty_emit_count=${sendObservation.nonemptyEmitCount} raw_output_length=${rawOutput.length} sanitized_output_length=${sanitizedOutput.length} first_nonempty_emit_ms=${firstTokenMs ?: "unavailable"} finish_reason_available=${finishReason != null} stop_reason_available=${stopReason != null}",
                maxOutputTokensList = maxOutputTokensList,
            )
            LiteRtLmGpuBenchmarkRow(
                timestamp = timestamp,
                routeType = ROUTE_TYPE,
                backend = backendVariant.backendLabel,
                backendVariant = backendVariant.wireValue,
                closePolicy = closePolicy.wireValue,
                phase = phase.wireValue,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                maxOutputTokensList = maxOutputTokensList,
                modelPath = modelPath,
                modelExists = true,
                modelLength = modelLength,
                engineCreateMs = engineCreateMs,
                conversationCreateMs = conversationCreateMs,
                firstTokenMs = firstTokenMs,
                ttftMs = benchmarkSnapshot?.timeToFirstTokenMs ?: firstTokenMs,
                decodeMs = decodeMs,
                totalMs = totalMs,
                outputTokens = outputTokens,
                tokensPerSecond = tokensPerSecond,
                finishReason = finishReason,
                stopReason = stopReason,
                rawOutput = rawOutput,
                sanitizedOutput = sanitizedOutput,
                status = if (sanitizedOutput.isBlank()) "failure" else "success",
                reason = if (sanitizedOutput.isBlank()) "blank_output" else "completed",
                sendExceptionClass = null,
                sendExceptionMessage = null,
                sendExceptionCauseChain = null,
                intentionallyLeakedForDiagnostic = closePolicy.intentionallyLeakedForDiagnostic,
                fallbackUsed = fallbackUsed,
                timeout = false,
                freshCrash = false,
                sendApiVariant = if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY) "gallery_contents_callback" else if (sendApiMode == BenchmarkSendApiMode.TYPED_CONTENTS_CALLBACK) sendApiMode.wireValue else sendApiVariant(backendVariant),
                samplerTopK = samplerTopK(backendVariant),
                samplerTopP = samplerTopP(backendVariant),
                samplerTemperature = samplerTemperature(backendVariant),
                conversationConfigUsed = conversationConfigUsed(backendVariant),
                contentsApiUsed = sendApiMode == BenchmarkSendApiMode.TYPED_CONTENTS_CALLBACK || contentsApiUsed(backendVariant),
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = genericFallbackModelConfigured,
                measuredPrefillTokens = measurementEvidence.measuredPrefillTokens,
                prefillTokenSource = measurementEvidence.prefillTokenSource,
                outputTokenSource = measurementEvidence.outputTokenSource,
                emitCount = sendObservation.emitCount,
                nonemptyEmitCount = sendObservation.nonemptyEmitCount,
                rawLength = rawOutput.length,
                sanitizedLength = sanitizedOutput.length,
                firstNonemptyMs = firstTokenMs,
                flowExceptionType = flowExceptionType(sendApiMode, flowCollectException),
                finishReasonAvailable = finishReason != null,
                stopReasonAvailable = stopReason != null,
                callbackOnMessageCount = sendObservation.callbackOnMessageCount,
                callbackOnDoneCount = sendObservation.callbackOnDoneCount,
                callbackOnErrorCount = sendObservation.callbackOnErrorCount,
                chunkTypeLengthSummary = sendObservation.chunkTypeLengthSummary,
                flowPartialRawOutput = sendObservation.flowPartialRawOutput,
                flowPartialEmitCount = sendObservation.flowPartialEmitCount,
                flowPartialNonemptyEmitCount = sendObservation.flowPartialNonemptyEmitCount,
                flowPartialFirstNonemptyMs = sendObservation.flowPartialFirstNonemptyMs,
            )
        } catch (throwable: Throwable) {
            rethrowCancellationOrInterrupt(throwable)
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "case_exception",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} max_output_tokens=$maxOutputTokens max_output_tokens_list=$maxOutputTokensList class=${throwable.javaClass.simpleName} message=${throwable.message.orEmpty().take(120)}",
                maxOutputTokensList = maxOutputTokensList,
            )
            val observationFailure = (throwable as? SendObservationException)
                ?: (sendException as? SendObservationException)
            val failedObservation = observationFailure?.observation
            val reportedThrowable = observationFailure?.cause ?: sendException ?: throwable
            val exceptionPrefix = if (engineCreateMs == null && conversation == null) {
                "engine_create_exception"
            } else {
                "run_exception"
            }
            LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                maxOutputTokensList = maxOutputTokensList,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                modelPath = modelPath,
                reason = exceptionReason(reportedThrowable, exceptionPrefix),
                modelExists = true,
                modelLength = modelLength,
                engineCreateMs = engineCreateMs,
                conversationCreateMs = conversationCreateMs,
                firstTokenMs = firstTokenMs,
                ttftMs = benchmarkSnapshot?.timeToFirstTokenMs ?: firstTokenMs,
                decodeMs = decodeMs,
                totalMs = SystemClock.elapsedRealtime() - totalStartMs,
                outputTokens = benchmarkSnapshot?.decodeTokenCount?.takeIf { it >= 0 },
                tokensPerSecond = benchmarkSnapshot?.decodeTokensPerSecond?.takeIf { it > 0.0 },
                sendExceptionClass = reportedThrowable.javaClass.name,
                sendExceptionMessage = reportedThrowable.message.orEmpty(),
                sendExceptionCauseChain = causeChainText(reportedThrowable),
                fallbackUsed = fallbackUsed,
                timeout = false,
                freshCrash = false,
                sendApiVariant = if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY) "gallery_contents_callback" else if (sendApiMode == BenchmarkSendApiMode.TYPED_CONTENTS_CALLBACK) sendApiMode.wireValue else sendApiVariant(backendVariant),
                samplerTopK = samplerTopK(backendVariant),
                samplerTopP = samplerTopP(backendVariant),
                samplerTemperature = samplerTemperature(backendVariant),
                conversationConfigUsed = conversationConfigUsed(backendVariant),
                contentsApiUsed = sendApiMode == BenchmarkSendApiMode.TYPED_CONTENTS_CALLBACK || contentsApiUsed(backendVariant),
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = genericFallbackModelConfigured,
            ).copy(
                emitCount = failedObservation?.emitCount ?: 0,
                nonemptyEmitCount = failedObservation?.nonemptyEmitCount ?: 0,
                rawOutput = failedObservation?.rawOutput.orEmpty(),
                rawLength = failedObservation?.rawOutput?.length ?: 0,
                firstNonemptyMs = failedObservation?.firstNonemptyMs ?: firstTokenMs,
                flowExceptionType = flowExceptionType(sendApiMode, flowCollectException),
                callbackOnMessageCount = failedObservation?.callbackOnMessageCount ?: 0,
                callbackOnDoneCount = failedObservation?.callbackOnDoneCount ?: 0,
                callbackOnErrorCount = failedObservation?.callbackOnErrorCount ?: 0,
                chunkTypeLengthSummary = failedObservation?.chunkTypeLengthSummary ?: "unavailable",
                flowPartialEmitCount = failedObservation?.flowPartialEmitCount ?: 0,
                flowPartialNonemptyEmitCount = failedObservation?.flowPartialNonemptyEmitCount ?: 0,
                flowPartialRawOutput = failedObservation?.flowPartialRawOutput.orEmpty(),
                flowPartialFirstNonemptyMs = failedObservation?.flowPartialFirstNonemptyMs,
            )
        } finally {
            closeResources(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                maxOutputTokensList = maxOutputTokensList,
                conversation = claimActiveConversation(conversation),
                engine = claimActiveEngine(engine),
                deadlineElapsedRealtime = deadlineElapsedRealtime,
            )
        }
    }

    private fun collectStreamingResponse(
        conversation: Conversation,
        prompt: String,
        decodeStartMs: Long,
        onFirstToken: (Long) -> Unit,
    ): SendObservation = runBlocking {
        collectStringFlowForBenchmark(
            chunks = conversation.sendMessageAsync(prompt).map { message ->
                message.contents.toString().takeIf { it.isNotBlank() }
                    ?: message.toString().takeIf { it.isNotBlank() }
                    ?: ""
            },
            elapsedMs = { SystemClock.elapsedRealtime() - decodeStartMs },
            cancelRequested = {
                cancelRequested.get() || Thread.currentThread().isInterrupted
            },
            onFirstToken = onFirstToken,
        )
    }

    private fun collectGalleryParityCallbackResponse(
        appContext: Context,
        timestamp: String,
        backendVariant: BenchmarkBackendVariant,
        closePolicy: BenchmarkClosePolicy,
        phase: BenchmarkPhase,
        maxOutputTokensList: String,
        conversation: Conversation,
        prompt: String,
        decodeStartMs: Long,
        callbackTimeoutMs: Long,
        onFirstToken: (Long) -> Unit,
    ): SendObservation {
        val actualVariant = if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY) "gallery_contents_callback" else "typed_contents_callback"
        val contents = Contents.of(listOf(Content.Text(prompt)))
        writeMarker(
            appContext = appContext,
            timestamp = timestamp,
            backendVariant = backendVariant,
            closePolicy = closePolicy,
            phase = phase,
            stage = "contents_created",
            detail = "send_api_variant=$actualVariant contents_api_used=true content_count=${contents.contents.size} prompt_length=${prompt.length}",
            maxOutputTokensList = maxOutputTokensList,
        )

        val doneLatch = CountDownLatch(1)
        val accumulator = CallbackObservationAccumulator()
        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                val chunk = renderGalleryParityMessageChunk(message)
                val chunkType = message.contents.contents
                    .joinToString(separator = "+") { it.javaClass.simpleName }
                    .ifBlank { message.javaClass.simpleName }
                val elapsedMs = SystemClock.elapsedRealtime() - decodeStartMs
                if (accumulator.onMessage(chunkType, chunk, elapsedMs)) {
                    onFirstToken(elapsedMs)
                    writeMarker(
                        appContext = appContext,
                        timestamp = timestamp,
                        backendVariant = backendVariant,
                        closePolicy = closePolicy,
                        phase = phase,
                        stage = "callback_first_token",
                        detail = "send_api_variant=$actualVariant first_token_ms=$elapsedMs raw_length=${chunk.length} chunk_type=$chunkType",
                        maxOutputTokensList = maxOutputTokensList,
                    )
                }
            }

            override fun onDone() {
                if (!accumulator.onDone()) return
                val snapshot = accumulator.snapshot()
                writeMarker(
                    appContext = appContext,
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    stage = "callback_send_finished",
                    detail = "send_api_variant=$actualVariant raw_length=${snapshot.rawOutput.length} callback_on_done_count=${snapshot.callbackOnDoneCount}",
                    maxOutputTokensList = maxOutputTokensList,
                )
                doneLatch.countDown()
            }

            override fun onError(throwable: Throwable) {
                if (!accumulator.onError(throwable)) return
                val snapshot = accumulator.snapshot()
                writeMarker(
                    appContext = appContext,
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    stage = "callback_send_exception",
                    detail = "send_api_variant=$actualVariant class=${throwable.javaClass.name} message=${throwable.message.orEmpty().take(200)} cause_chain=${causeChainText(throwable)} callback_on_error_count=${snapshot.callbackOnErrorCount} partial_raw_length=${snapshot.rawOutput.length}",
                    maxOutputTokensList = maxOutputTokensList,
                )
                doneLatch.countDown()
            }
        }

        writeMarker(
            appContext = appContext,
            timestamp = timestamp,
            backendVariant = backendVariant,
            closePolicy = closePolicy,
            phase = phase,
            stage = "callback_send_started",
            detail = "send_api_variant=$actualVariant conversation_config_used=${conversationConfigUsed(backendVariant)} contents_api_used=true sampler_top_k=${samplerTopK(backendVariant) ?: "none"} sampler_top_p=${samplerTopP(backendVariant) ?: "none"} sampler_temperature=${samplerTemperature(backendVariant) ?: "none"}",
            maxOutputTokensList = maxOutputTokensList,
        )
        try {
            conversation.sendMessageAsync(contents, callback, emptyMap<String, Any>())
        } catch (throwable: Throwable) {
            rethrowCancellationOrInterrupt(throwable)
            accumulator.onError(throwable)
            val snapshot = accumulator.snapshot()
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "callback_send_exception",
                detail = "send_api_variant=$actualVariant class=${throwable.javaClass.name} message=${throwable.message.orEmpty().take(200)} cause_chain=${causeChainText(throwable)} partial_raw_length=${snapshot.rawOutput.length}",
                maxOutputTokensList = maxOutputTokensList,
            )
            throw SendObservationException(snapshot, throwable)
        }
        val completed = doneLatch.await(callbackTimeoutMs, TimeUnit.MILLISECONDS)
        if (!completed) {
            if (!accumulator.onTimeout()) {
                return when (accumulator.terminalKind()) {
                    CallbackTerminalKind.DONE -> accumulator.snapshot()
                    CallbackTerminalKind.ERROR -> throw SendObservationException(
                        accumulator.snapshot(),
                        accumulator.terminalError() ?: IllegalStateException("callback_error_without_throwable"),
                    )
                    CallbackTerminalKind.ACTIVE,
                    CallbackTerminalKind.TIMEOUT,
                    -> throw SendObservationException(
                        accumulator.snapshot(),
                        TimeoutException("${actualVariant}_timeout_${callbackTimeoutMs}ms"),
                    )
                }
            }
            val timeout = TimeoutException("${actualVariant}_timeout_${callbackTimeoutMs}ms")
            val snapshot = accumulator.snapshot()
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "callback_send_timeout",
                detail = "send_api_variant=$actualVariant timeout_ms=$callbackTimeoutMs raw_length=${snapshot.rawOutput.length} chunk_type_length_summary=${snapshot.chunkTypeLengthSummary}",
                maxOutputTokensList = maxOutputTokensList,
            )
            throw SendObservationException(snapshot, timeout)
        }
        return when (accumulator.terminalKind()) {
            CallbackTerminalKind.DONE -> accumulator.snapshot()
            CallbackTerminalKind.ERROR -> throw SendObservationException(
                accumulator.snapshot(),
                accumulator.terminalError() ?: IllegalStateException("callback_error_without_throwable"),
            )
            CallbackTerminalKind.TIMEOUT,
            CallbackTerminalKind.ACTIVE,
            -> throw SendObservationException(
                accumulator.snapshot(),
                TimeoutException("${actualVariant}_terminal_state_${accumulator.terminalKind().name.lowercase()}"),
            )
        }
    }

    private fun renderGalleryParityMessageChunk(message: Message): String {
        val textContents = message.contents.contents
            .joinToString(separator = "") { content ->
                when (content) {
                    is Content.Text -> content.text
                    else -> ""
                }
            }
            .takeIf { it.isNotBlank() }
        return textContents
            ?: message.contents.toString().takeIf { it.isNotBlank() }
            ?: message.toString()
    }

    private fun probeBenchmarkSnapshot(conversation: Conversation?): BenchmarkSnapshot? {
        val benchmark = runCatching { conversation?.getBenchmarkInfo() }.getOrNull() ?: return null
        return BenchmarkSnapshot(
            timeToFirstTokenMs = secondsToMs(benchmark.timeToFirstTokenInSecond),
            measuredPrefillTokens = benchmark.lastPrefillTokenCount,
            decodeTokenCount = benchmark.lastDecodeTokenCount,
            decodeTokensPerSecond = benchmark.lastDecodeTokensPerSecond,
        )
    }

    private fun resolveModelPath(
        appContext: Context,
        intent: Intent,
        modelPathSource: BenchmarkModelPathSource,
    ): BenchmarkModelPathResolution {
        if (modelPathSource == BenchmarkModelPathSource.GENERIC_FALLBACK) {
            val relayPath = runCatching {
                File(appContext.filesDir, GENERIC_MODEL_PATH_RELAY_FILE_NAME).readText().trim().takeIf { it.isNotBlank() }
            }.getOrNull()
            val genericPath = relayPath?.takeIf { path ->
                runCatching {
                    val candidate = File(path)
                    candidate.isFile && candidate.length() > 0L && candidate.extension.equals("litertlm", ignoreCase = true)
                }.getOrDefault(false)
            } ?: runCatching {
                runBlocking { SettingsPreferences(appContext).getValidLocalGenericModelPathOrNull() }
            }.getOrNull()
            return BenchmarkModelPathResolution(
                modelPath = genericPath,
                source = BenchmarkModelPathSource.GENERIC_FALLBACK,
                genericFallbackModelConfigured = !genericPath.isNullOrBlank(),
            )
        }
        decodeBase64Extra(intent, EXTRA_MODEL_PATH_BASE64)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                return BenchmarkModelPathResolution(
                    modelPath = it,
                    source = BenchmarkModelPathSource.AUTO,
                    genericFallbackModelConfigured = true,
                )
            }
        intent.getStringExtra(EXTRA_MODEL_PATH)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                return BenchmarkModelPathResolution(
                    modelPath = it,
                    source = BenchmarkModelPathSource.AUTO,
                    genericFallbackModelConfigured = true,
                )
            }
        val settingsModelPath = runCatching {
            runBlocking { SettingsPreferences(appContext).getValidLocalBaseModelPathOrNull() }
        }.getOrNull()
        if (!settingsModelPath.isNullOrBlank()) {
            return BenchmarkModelPathResolution(
                modelPath = settingsModelPath,
                source = BenchmarkModelPathSource.AUTO,
                genericFallbackModelConfigured = true,
            )
        }
        val localModelsDir = File(appContext.filesDir, "local_models")
        val localModelPath = localModelsDir
            .listFiles { file -> file.isFile && file.extension.equals("litertlm", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.firstOrNull()
            ?.absolutePath
        return BenchmarkModelPathResolution(
            modelPath = localModelPath,
            source = BenchmarkModelPathSource.AUTO,
            genericFallbackModelConfigured = !localModelPath.isNullOrBlank(),
        )
    }

    private fun prompts(intent: Intent): List<String> {
        intent.getStringExtra(EXTRA_SINGLE_PROMPT)
            ?.takeIf { it.isNotBlank() }
            ?.let { return listOf(it) }
        val raw = decodeBase64Extra(intent, EXTRA_PROMPTS_BASE64)
            ?: intent.getStringExtra(EXTRA_PROMPTS)
            ?.takeIf { it.isNotBlank() }
            ?: return DEFAULT_PROMPTS
        val delimiter = if (raw.contains('\n')) "\n" else "|||"
        return raw.split(delimiter)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?: DEFAULT_PROMPTS
    }

    private fun maxOutputTokensValues(intent: Intent): List<Int> {
        val raw = decodeBase64Extra(intent, EXTRA_MAX_OUTPUT_TOKENS_LIST_BASE64)
            ?: intent.getStringExtra(EXTRA_MAX_OUTPUT_TOKENS_LIST)
            ?.takeIf { it.isNotBlank() }
            ?: return DEFAULT_MAX_OUTPUT_TOKENS
        return raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in GPU_TOKEN_PROBE_MAX_OUTPUT_TOKENS_ALLOWLIST }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?: DEFAULT_MAX_OUTPUT_TOKENS
    }

    private fun backendVariant(intent: Intent): BenchmarkBackendVariant =
        BenchmarkBackendVariant.parse(intent.getStringExtra(EXTRA_BACKEND_VARIANT))

    private fun closePolicy(intent: Intent): BenchmarkClosePolicy =
        BenchmarkClosePolicy.parse(intent.getStringExtra(EXTRA_CLOSE_POLICY))

    private fun phase(intent: Intent): BenchmarkPhase =
        BenchmarkPhase.parse(intent.getStringExtra(EXTRA_PHASE))

    private fun modelPathSource(intent: Intent): BenchmarkModelPathSource =
        BenchmarkModelPathSource.parse(intent.getStringExtra(EXTRA_MODEL_PATH_SOURCE))

    private fun timeoutMs(intent: Intent): Long =
        intent.getLongExtra(EXTRA_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
            .coerceIn(1_000L, 600_000L)

    private fun deadlineElapsedRealtime(intent: Intent): Long {
        val received = intent.getLongExtra(EXTRA_RUN_DEADLINE_ELAPSED_REALTIME, 0L)
        return if (received > 0L) received else SystemClock.elapsedRealtime() + timeoutMs(intent)
    }

    private fun remainingRunBudgetMs(deadlineElapsedRealtime: Long): Long =
        (deadlineElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    private fun decodeBase64Extra(intent: Intent, key: String): String? {
        val encoded = intent.getStringExtra(key)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun resolveEngineConfigParts(
        appContext: Context,
        backendVariant: BenchmarkBackendVariant,
        maxOutputTokens: Int,
    ): EngineConfigParts =
        resolveEngineConfigPartsForBenchmark(
            cacheDirPath = appContext.cacheDir.absolutePath,
            backendVariant = backendVariant,
            maxOutputTokens = maxOutputTokens,
        )

    internal fun resolveEngineConfigPartsForBenchmark(
        cacheDirPath: String,
        backendVariant: BenchmarkBackendVariant,
        maxOutputTokens: Int,
    ): EngineConfigParts {
        val cacheDir = when (backendVariant) {
            BenchmarkBackendVariant.GPU,
            BenchmarkBackendVariant.CPU,
            BenchmarkBackendVariant.GPU_NULL_MODALITIES,
            BenchmarkBackendVariant.GPU_CPU_MODALITIES,
            BenchmarkBackendVariant.GPU_CACHE_DIR,
            BenchmarkBackendVariant.GPU_NULL_MAX,
            BenchmarkBackendVariant.GPU_ALL -> cacheDirPath
            BenchmarkBackendVariant.AUTOMATIC,
            BenchmarkBackendVariant.GALLERY_CHAT_PARITY -> null
        }
        return when (backendVariant) {
            BenchmarkBackendVariant.AUTOMATIC -> EngineConfigParts(
                backend = null,
                engineBackendLabel = "Automatic",
                visionBackend = null,
                visionBackendLabel = "default",
                audioBackend = null,
                audioBackendLabel = "default",
                maxNumTokens = maxOutputTokens,
                cacheDir = cacheDir,
                useConstructorDefaultBackend = true,
            )

            BenchmarkBackendVariant.CPU -> EngineConfigParts(
                backend = Backend.CPU(),
                engineBackendLabel = "CPU",
                visionBackend = Backend.CPU(),
                visionBackendLabel = "CPU",
                audioBackend = Backend.CPU(),
                audioBackendLabel = "CPU",
                maxNumTokens = maxOutputTokens,
                cacheDir = cacheDir,
            )

            BenchmarkBackendVariant.GPU_NULL_MODALITIES -> EngineConfigParts(
                backend = Backend.GPU(),
                engineBackendLabel = "GPU",
                visionBackend = null,
                visionBackendLabel = "null",
                audioBackend = null,
                audioBackendLabel = "null",
                maxNumTokens = maxOutputTokens,
                cacheDir = cacheDir,
            )

            BenchmarkBackendVariant.GPU_CPU_MODALITIES -> EngineConfigParts(
                backend = Backend.GPU(),
                engineBackendLabel = "GPU",
                visionBackend = Backend.CPU(),
                visionBackendLabel = "CPU",
                audioBackend = Backend.CPU(),
                audioBackendLabel = "CPU",
                maxNumTokens = maxOutputTokens,
                cacheDir = cacheDir,
            )

            BenchmarkBackendVariant.GPU_NULL_MAX,
            BenchmarkBackendVariant.GPU_ALL -> EngineConfigParts(
                backend = Backend.GPU(),
                engineBackendLabel = "GPU",
                visionBackend = if (backendVariant == BenchmarkBackendVariant.GPU_ALL) null else Backend.GPU(),
                visionBackendLabel = if (backendVariant == BenchmarkBackendVariant.GPU_ALL) "null" else "GPU",
                audioBackend = if (backendVariant == BenchmarkBackendVariant.GPU_ALL) null else Backend.CPU(),
                audioBackendLabel = if (backendVariant == BenchmarkBackendVariant.GPU_ALL) "null" else "CPU",
                maxNumTokens = null,
                cacheDir = cacheDir,
            )

            BenchmarkBackendVariant.GPU,
            BenchmarkBackendVariant.GPU_CACHE_DIR -> EngineConfigParts(
                backend = Backend.GPU(),
                engineBackendLabel = "GPU",
                visionBackend = Backend.GPU(),
                visionBackendLabel = "GPU",
                audioBackend = Backend.CPU(),
                audioBackendLabel = "CPU",
                maxNumTokens = maxOutputTokens,
                cacheDir = cacheDir,
            )

            BenchmarkBackendVariant.GALLERY_CHAT_PARITY -> EngineConfigParts(
                backend = Backend.GPU(),
                engineBackendLabel = "GPU",
                visionBackend = null,
                visionBackendLabel = "null",
                audioBackend = null,
                audioBackendLabel = "null",
                maxNumTokens = GALLERY_CHAT_PARITY_MAX_NUM_TOKENS,
                cacheDir = null,
            )
        }
    }

    private fun sendApiVariant(backendVariant: BenchmarkBackendVariant): String =
        if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY) {
            "gallery_contents_callback"
        } else {
            "flow_string_with_blocking_fallback"
        }

    private fun samplerTopK(backendVariant: BenchmarkBackendVariant): Int? =
        if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY) GALLERY_CHAT_PARITY_TOP_K else null

    private fun samplerTopP(backendVariant: BenchmarkBackendVariant): Double? =
        if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY) GALLERY_CHAT_PARITY_TOP_P else null

    private fun samplerTemperature(backendVariant: BenchmarkBackendVariant): Double? =
        if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY) GALLERY_CHAT_PARITY_TEMPERATURE else null

    private fun conversationConfigUsed(backendVariant: BenchmarkBackendVariant): Boolean =
        backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY

    private fun contentsApiUsed(backendVariant: BenchmarkBackendVariant): Boolean =
        backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY

    private fun closeResources(
        appContext: Context,
        timestamp: String,
        backendVariant: BenchmarkBackendVariant,
        closePolicy: BenchmarkClosePolicy,
        phase: BenchmarkPhase,
        maxOutputTokensList: String,
        conversation: Conversation?,
        engine: Engine?,
        deadlineElapsedRealtime: Long,
    ) {
        if (conversation != null && closePolicy == BenchmarkClosePolicy.NORMAL) {
            closeOne(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                maxOutputTokensList = maxOutputTokensList,
                target = "conversation",
                deadlineElapsedRealtime = deadlineElapsedRealtime,
            ) {
                conversation.close()
            }
        } else if (conversation != null) {
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "close_skipped",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} target=conversation intentionally_leaked_for_diagnostic=true",
                maxOutputTokensList = maxOutputTokensList,
            )
        }

        if (engine != null && closePolicy != BenchmarkClosePolicy.SKIP_ALL) {
            closeOne(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                maxOutputTokensList = maxOutputTokensList,
                target = "engine",
                deadlineElapsedRealtime = deadlineElapsedRealtime,
            ) {
                engine.close()
            }
        } else if (engine != null) {
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "close_skipped",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} target=engine intentionally_leaked_for_diagnostic=true",
                maxOutputTokensList = maxOutputTokensList,
            )
        }
    }

    private fun closeOne(
        appContext: Context,
        timestamp: String,
        backendVariant: BenchmarkBackendVariant,
        closePolicy: BenchmarkClosePolicy,
        phase: BenchmarkPhase,
        maxOutputTokensList: String,
        target: String,
        deadlineElapsedRealtime: Long,
        block: () -> Unit,
    ) {
        writeMarker(
            appContext = appContext,
            timestamp = timestamp,
            backendVariant = backendVariant,
            closePolicy = closePolicy,
            phase = phase,
            stage = "close_started",
            detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} target=$target intentionally_leaked_for_diagnostic=false",
            maxOutputTokensList = maxOutputTokensList,
        )
        try {
            val closeFuture = closeTimeoutDispatcher.submit(block)
            try {
                val closeBudgetMs = minOf(
                    CLOSE_TIMEOUT_MS,
                    (remainingRunBudgetMs(deadlineElapsedRealtime) - REPORT_PUBLISH_RESERVE_MS)
                        .coerceAtLeast(0L),
                )
                if (closeBudgetMs <= 0L) throw TimeoutException("shared run deadline exhausted before $target close")
                closeFuture.get(closeBudgetMs, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                closeFuture.cancel(true)
                closeTimedOut.set(true)
                closeTimeoutRequiresProcessCleanup.set(true)
                writeMarker(
                    appContext = appContext,
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    stage = "close_timeout",
                    detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} target=$target timeout_ms=$CLOSE_TIMEOUT_MS shared_deadline_remaining_ms=${remainingRunBudgetMs(deadlineElapsedRealtime)}",
                    maxOutputTokensList = maxOutputTokensList,
                )
                return
            }
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "close_finished",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} target=$target",
                maxOutputTokensList = maxOutputTokensList,
            )
        } catch (throwable: Throwable) {
            val closeCause = (throwable as? ExecutionException)?.cause ?: throwable
            if (closeCause is CancellationException) throw closeCause
            val wasInterrupted = closeCause is InterruptedException
            val closeFailure = "resource_close_exception:$target:${closeCause.javaClass.simpleName}"
            closeFailureReason.compareAndSet(null, closeFailure)
            closeTimeoutRequiresProcessCleanup.set(true)
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "resource_close_exception_process_cleanup_required",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} target=$target class=${closeCause.javaClass.name} message=${closeCause.message.orEmpty()} cause_chain=${causeChainText(closeCause)}",
                maxOutputTokensList = maxOutputTokensList,
            )
            if (wasInterrupted) Thread.currentThread().interrupt()
        }
    }

    private data class BenchmarkSnapshot(
        val timeToFirstTokenMs: Long?,
        val measuredPrefillTokens: Int?,
        val decodeTokenCount: Int?,
        val decodeTokensPerSecond: Double?,
    )

    private data class BenchmarkModelPathResolution(
        val modelPath: String?,
        val source: BenchmarkModelPathSource,
        val genericFallbackModelConfigured: Boolean,
    )

    internal data class EngineConfigParts(
        val backend: Backend?,
        val engineBackendLabel: String,
        val visionBackend: Backend?,
        val visionBackendLabel: String,
        val audioBackend: Backend?,
        val audioBackendLabel: String,
        val maxNumTokens: Int?,
        val cacheDir: String?,
        val useConstructorDefaultBackend: Boolean = false,
    ) {
        fun buildEngineConfig(modelPath: String): EngineConfig =
            if (useConstructorDefaultBackend) {
                EngineConfig(
                    modelPath = modelPath,
                    maxNumTokens = maxNumTokens,
                    cacheDir = cacheDir,
                )
            } else {
                EngineConfig(
                    modelPath = modelPath,
                    backend = requireNotNull(backend),
                    visionBackend = visionBackend,
                    audioBackend = audioBackend,
                    maxNumTokens = maxNumTokens,
                    cacheDir = cacheDir,
                )
            }

        fun markerDetail(backendVariant: BenchmarkBackendVariant, maxOutputTokens: Int): String =
            "backend=${backendVariant.backendLabel} backend_variant=${backendVariant.wireValue} " +
                "engine_backend=$engineBackendLabel " +
                "vision_backend=$visionBackendLabel audio_backend=$audioBackendLabel " +
                "config_style=${backendVariant.configStyle} " +
                "cache_dir=${cacheDir ?: "null"} max_output_tokens=$maxOutputTokens " +
                "config_max_num_tokens=${maxNumTokens?.toString() ?: "null"} max_num_images=constructor_default"
    }

    companion object {
        const val ACTION = "io.github.ninbyo02.lami.action.LITERT_LM_GPU_BENCHMARK"
        const val EXTRA_COMMAND_CANCEL = "command_cancel"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_MODEL_PATH = "model_path"
        const val EXTRA_MODEL_PATH_BASE64 = "model_path_base64"
        const val GENERIC_MODEL_PATH_RELAY_FILE_NAME = "litert_lm_gpu_benchmark_generic_model_path.txt"
        const val EXTRA_PROMPTS = "prompts"
        const val EXTRA_SINGLE_PROMPT = "single_prompt"
        const val EXTRA_SEND_API_MODE = "send_api_mode"
        const val EXTRA_PROMPTS_BASE64 = "prompts_base64"
        const val EXTRA_MAX_OUTPUT_TOKENS_LIST = "max_output_tokens_list"
        const val EXTRA_MAX_OUTPUT_TOKENS_LIST_BASE64 = "max_output_tokens_list_base64"
        const val EXTRA_BACKEND_VARIANT = "backend_variant"
        const val EXTRA_CLOSE_POLICY = "close_policy"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_TIMEOUT_MS = "timeout_ms"
        const val EXTRA_RUN_DEADLINE_ELAPSED_REALTIME = "run_deadline_elapsed_realtime"
        const val EXTRA_MODEL_PATH_SOURCE = "model_path_source"
        const val STATE_FILE_NAME = "litert_lm_gpu_benchmark_state.txt"
        const val CANCEL_RELAY_FILE_NAME = "litert_lm_gpu_benchmark_cancel.txt"
        const val MARKER_FILE_NAME = "litert_lm_gpu_benchmark_marker.txt"
        const val MARKER_HISTORY_FILE_NAME = "litert_lm_gpu_benchmark_marker_history.txt"
        private const val DEFAULT_TIMEOUT_MS = 60_000L
        private const val CLOSE_TIMEOUT_MS = 10_000L
        private const val TERMINAL_PUBLISH_RESERVE_MS = 15_000L
        private const val REPORT_PUBLISH_RESERVE_MS = 2_000L
        private const val PROCESS_CLEANUP_DELAY_MS = 500L
        private const val CANCEL_PROCESS_CLEANUP_DELAY_MS = 5_000L
        private const val ROUTE_TYPE = "litert_lm_gpu_benchmark"
        private const val GALLERY_CHAT_PARITY_MAX_NUM_TOKENS = 4096
        private const val GALLERY_CHAT_PARITY_TOP_K = 64
        private const val GALLERY_CHAT_PARITY_TOP_P = 0.95
        private const val GALLERY_CHAT_PARITY_TEMPERATURE = 1.0
        private val DEFAULT_PROMPTS = listOf(
            "こんにちは",
            "カレーの材料を箇条書きで教えて",
        )
        private val DEFAULT_MAX_OUTPUT_TOKENS = listOf(32, 64, 128, 256)
        private val GPU_TOKEN_PROBE_MAX_OUTPUT_TOKENS_ALLOWLIST = setOf(16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 22400, 24576, 28800, 30400, 32768, 32769, 65536, 131072, 262144, 524288, 1048576)
        private val FINISH_REASON_METHODS = listOf("getFinishReason", "finishReason", "getDoneReason", "doneReason")
        private val STOP_REASON_METHODS = listOf("getStopReason", "stopReason", "getStop", "stop")
        private val running = AtomicBoolean(false)
        private val cancelRequested = AtomicBoolean(false)
        private val activeCaseFuture = AtomicReference<Future<*>?>(null)
        private val activeConversation = AtomicReference<Conversation?>(null)
        private val activeEngine = AtomicReference<Engine?>(null)
        private val activeRunTimestamp = AtomicReference<String?>(null)
        private val activeCancelMarker = AtomicReference<((String, String) -> Unit)?>(null)
        private val cancelCloseDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkCancelClose")
        }
        private val closeTimeoutDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkCloseTimeout")
        }
        private val processCleanupDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkProcessCleanup")
        }
        private val closeTimedOut = AtomicBoolean(false)
        private val closeFailureReason = AtomicReference<String?>(null)
        private val closeTimeoutRequiresProcessCleanup = AtomicBoolean(false)
        private val cancelRelayDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkCancelRelay")
        }

        private fun startCancelRelayWatcher(appContext: Context, timestamp: String): Future<*> {
            activeCancelMarker.get()?.invoke("cancel_relay_watcher_started", "timestamp=$timestamp")
            return cancelRelayDispatcher.submit {
                val relay = File(appContext.filesDir, CANCEL_RELAY_FILE_NAME)
                var observedRelay = ""
                while (!Thread.currentThread().isInterrupted && running.get()) {
                    val requested = runCatching { relay.readText(Charsets.UTF_8).trim() }.getOrDefault("")
                    if (requested.isNotBlank() && requested != observedRelay) {
                        observedRelay = requested
                        activeCancelMarker.get()?.invoke(
                            "cancel_relay_observed",
                            "timestamp_matched=${requested == timestamp}",
                        )
                    }
                    if (requested == timestamp) {
                        activeCancelMarker.get()?.invoke("cancel_relay_received", "timestamp_matched=true")
                        cancelCurrentRun()
                        return@submit
                    }
                    Thread.sleep(50L)
                }
            }
        }

        /** Cooperative cancellation used only by the debug foreground UI. */
        fun cancelCurrentRun() {
            cancelRequested.set(true)
            val marker = activeCancelMarker.get()
            val future = activeCaseFuture.getAndSet(null) ?: return
            val conversationPresent = activeConversation.get() != null
            val enginePresent = activeEngine.get() != null
            val cancelAccepted = future.cancel(true)
            marker?.invoke(
                "cancel_future_requested",
                "future_present=true cancel_accepted=$cancelAccepted conversation_present=$conversationPresent engine_present=$enginePresent",
            )
            marker?.invoke(
                "cancel_process_cleanup_scheduled",
                "delay_ms=$CANCEL_PROCESS_CLEANUP_DELAY_MS package_specific=true",
            )
            Thread({
                Thread.sleep(CANCEL_PROCESS_CLEANUP_DELAY_MS)
                marker?.invoke(
                    "cancel_process_cleanup_started",
                    "pid=${android.os.Process.myPid()} package_specific=true",
                )
                android.os.Process.killProcess(android.os.Process.myPid())
            }, "LiteRtLmGpuBenchmarkCancelProcessCleanup").start()
        }

        private fun claimActiveConversation(expected: Conversation?): Conversation? =
            if (expected != null && activeConversation.compareAndSet(expected, null)) expected else null

        private fun claimActiveEngine(expected: Engine?): Engine? =
            if (expected != null && activeEngine.compareAndSet(expected, null)) expected else null

        private val receiverDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkReceiver")
        }
    }
}

internal enum class CallbackTerminalKind { ACTIVE, DONE, ERROR, TIMEOUT }

internal class CallbackObservationAccumulator {
    private val lock = Any()
    private val output = StringBuilder()
    private val chunkTypesAndLengths = mutableListOf<String>()
    private var emitCount = 0
    private var nonemptyEmitCount = 0
    private var firstNonemptyMs: Long? = null
    private var callbackOnDoneCount = 0
    private var callbackOnErrorCount = 0
    private var lastChunk: String? = null
    private var terminalKind = CallbackTerminalKind.ACTIVE
    private var terminalError: Throwable? = null

    fun onMessage(chunkType: String, chunk: String, elapsedMs: Long): Boolean = synchronized(lock) {
        if (terminalKind != CallbackTerminalKind.ACTIVE) return@synchronized false
        emitCount += 1
        chunkTypesAndLengths += "${chunkType.ifBlank { "unknown" }}:${chunk.length}"
        if (chunk.isBlank()) return@synchronized false
        nonemptyEmitCount += 1
        val isFirst = firstNonemptyMs == null
        if (isFirst) firstNonemptyMs = elapsedMs
        if (chunk != lastChunk) {
            lastChunk = chunk
            output.append(chunk)
        }
        isFirst
    }

    fun onDone(): Boolean = synchronized(lock) {
        if (terminalKind != CallbackTerminalKind.ACTIVE) return@synchronized false
        terminalKind = CallbackTerminalKind.DONE
        callbackOnDoneCount = 1
        true
    }

    fun onError(throwable: Throwable? = null): Boolean = synchronized(lock) {
        if (terminalKind != CallbackTerminalKind.ACTIVE) return@synchronized false
        terminalKind = CallbackTerminalKind.ERROR
        terminalError = throwable
        callbackOnErrorCount = 1
        true
    }

    fun onTimeout(): Boolean = synchronized(lock) {
        if (terminalKind != CallbackTerminalKind.ACTIVE) return@synchronized false
        terminalKind = CallbackTerminalKind.TIMEOUT
        true
    }

    fun terminalKind(): CallbackTerminalKind = synchronized(lock) { terminalKind }

    fun terminalError(): Throwable? = synchronized(lock) { terminalError }

    fun snapshot(): SendObservation = synchronized(lock) {
        SendObservation(
            rawOutput = output.toString(),
            emitCount = emitCount,
            nonemptyEmitCount = nonemptyEmitCount,
            firstNonemptyMs = firstNonemptyMs,
            callbackOnMessageCount = emitCount,
            callbackOnDoneCount = callbackOnDoneCount,
            callbackOnErrorCount = callbackOnErrorCount,
            chunkTypeLengthSummary = chunkTypesAndLengths.joinToString(prefix = "types_lengths:[", postfix = "]"),
        )
    }
}

internal class SendObservationException(
    val observation: SendObservation,
    cause: Throwable,
) : RuntimeException(cause)

internal data class SendObservation(
    val rawOutput: String,
    val emitCount: Int,
    val nonemptyEmitCount: Int,
    val firstNonemptyMs: Long?,
    val callbackOnMessageCount: Int,
    val callbackOnDoneCount: Int,
    val callbackOnErrorCount: Int,
    val chunkTypeLengthSummary: String,
    val flowPartialRawOutput: String = "",
    val flowPartialEmitCount: Int = 0,
    val flowPartialNonemptyEmitCount: Int = 0,
    val flowPartialFirstNonemptyMs: Long? = null,
) {
    fun withFlowPartialEvidence(partial: SendObservation?): SendObservation = if (partial == null) this else copy(
        flowPartialRawOutput = partial.rawOutput,
        flowPartialEmitCount = partial.emitCount,
        flowPartialNonemptyEmitCount = partial.nonemptyEmitCount,
        flowPartialFirstNonemptyMs = partial.firstNonemptyMs,
    )

    companion object {
        fun blocking(rawOutput: String, firstNonemptyMs: Long?) = SendObservation(
            rawOutput = rawOutput,
            emitCount = 1,
            nonemptyEmitCount = if (rawOutput.isBlank()) 0 else 1,
            firstNonemptyMs = firstNonemptyMs,
            callbackOnMessageCount = 0,
            callbackOnDoneCount = 0,
            callbackOnErrorCount = 0,
            chunkTypeLengthSummary = "blocking",
        )
    }
}

internal data class LiteRtLmGpuBenchmarkRow(
    val timestamp: String,
    val routeType: String,
    val backend: String,
    val backendVariant: String,
    val closePolicy: String,
    val phase: String,
    val prompt: String,
    val maxOutputTokens: Int,
    val maxOutputTokensList: String,
    val modelPath: String,
    val modelExists: Boolean,
    val modelLength: Long,
    val engineCreateMs: Long?,
    val conversationCreateMs: Long?,
    val firstTokenMs: Long?,
    val ttftMs: Long?,
    val decodeMs: Long?,
    val totalMs: Long?,
    val outputTokens: Int?,
    val tokensPerSecond: Double?,
    val finishReason: String?,
    val stopReason: String?,
    val rawOutput: String,
    val sanitizedOutput: String,
    val status: String,
    val reason: String,
    val sendExceptionClass: String?,
    val sendExceptionMessage: String?,
    val sendExceptionCauseChain: String?,
    val intentionallyLeakedForDiagnostic: Boolean,
    val fallbackUsed: Boolean,
    val timeout: Boolean,
    val freshCrash: Boolean,
    val sendApiVariant: String,
    val samplerTopK: Int?,
    val samplerTopP: Double?,
    val samplerTemperature: Double?,
    val conversationConfigUsed: Boolean,
    val contentsApiUsed: Boolean,
    val modelPathSource: String = BenchmarkModelPathSource.AUTO.wireValue,
    val genericFallbackModelConfigured: Boolean = true,
    val measuredPrefillTokens: Int? = null,
    val prefillTokenSource: String = "unavailable",
    val outputTokenSource: String = "unavailable",
    val emitCount: Int = 0,
    val nonemptyEmitCount: Int = 0,
    val rawLength: Int = rawOutput.length,
    val sanitizedLength: Int = sanitizedOutput.length,
    val firstNonemptyMs: Long? = null,
    val flowExceptionType: String? = null,
    val finishReasonAvailable: Boolean = false,
    val stopReasonAvailable: Boolean = false,
    val callbackOnMessageCount: Int = 0,
    val callbackOnDoneCount: Int = 0,
    val callbackOnErrorCount: Int = 0,
    val chunkTypeLengthSummary: String = "unavailable",
    val flowPartialRawOutput: String = "",
    val flowPartialEmitCount: Int = 0,
    val flowPartialNonemptyEmitCount: Int = 0,
    val flowPartialFirstNonemptyMs: Long? = null,
) {
    companion object {
        fun failure(
            timestamp: String,
            backendVariant: BenchmarkBackendVariant,
            closePolicy: BenchmarkClosePolicy,
            phase: BenchmarkPhase,
            maxOutputTokensList: String,
            prompt: String,
            maxOutputTokens: Int,
            modelPath: String,
            reason: String,
            modelExists: Boolean = false,
            modelLength: Long = 0L,
            engineCreateMs: Long? = null,
            conversationCreateMs: Long? = null,
            firstTokenMs: Long? = null,
            ttftMs: Long? = null,
            decodeMs: Long? = null,
            totalMs: Long? = null,
            outputTokens: Int? = null,
            tokensPerSecond: Double? = null,
            sendExceptionClass: String? = null,
            sendExceptionMessage: String? = null,
            sendExceptionCauseChain: String? = null,
            fallbackUsed: Boolean = false,
            timeout: Boolean,
            freshCrash: Boolean,
            sendApiVariant: String? = null,
            samplerTopK: Int? = null,
            samplerTopP: Double? = null,
            samplerTemperature: Double? = null,
            conversationConfigUsed: Boolean? = null,
            contentsApiUsed: Boolean? = null,
            modelPathSource: String = BenchmarkModelPathSource.AUTO.wireValue,
            genericFallbackModelConfigured: Boolean = true,
        ): LiteRtLmGpuBenchmarkRow {
            val isGalleryParity = backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY
            return LiteRtLmGpuBenchmarkRow(
                timestamp = timestamp,
                routeType = "litert_lm_gpu_benchmark",
                backend = backendVariant.backendLabel,
                backendVariant = backendVariant.wireValue,
                closePolicy = closePolicy.wireValue,
                phase = phase.wireValue,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                maxOutputTokensList = maxOutputTokensList,
                modelPath = modelPath,
                modelExists = modelExists,
                modelLength = modelLength,
                engineCreateMs = engineCreateMs,
                conversationCreateMs = conversationCreateMs,
                firstTokenMs = firstTokenMs,
                ttftMs = ttftMs,
                decodeMs = decodeMs,
                totalMs = totalMs,
                outputTokens = outputTokens,
                tokensPerSecond = tokensPerSecond,
                finishReason = null,
                stopReason = null,
                rawOutput = "",
                sanitizedOutput = "",
                status = "failure",
                reason = reason,
                sendExceptionClass = sendExceptionClass,
                sendExceptionMessage = sendExceptionMessage,
                sendExceptionCauseChain = sendExceptionCauseChain,
                intentionallyLeakedForDiagnostic = closePolicy.intentionallyLeakedForDiagnostic,
                fallbackUsed = fallbackUsed,
                timeout = timeout,
                freshCrash = freshCrash,
                sendApiVariant = sendApiVariant
                    ?: if (isGalleryParity) "gallery_contents_callback" else "flow_string_with_blocking_fallback",
                samplerTopK = samplerTopK ?: if (isGalleryParity) 64 else null,
                samplerTopP = samplerTopP ?: if (isGalleryParity) 0.95 else null,
                samplerTemperature = samplerTemperature ?: if (isGalleryParity) 1.0 else null,
                conversationConfigUsed = conversationConfigUsed ?: isGalleryParity,
                contentsApiUsed = contentsApiUsed ?: isGalleryParity,
                modelPathSource = modelPathSource,
                genericFallbackModelConfigured = genericFallbackModelConfigured,
            )
        }
    }
}

internal data class LiteRtLmGpuBenchmarkRunSummary(
    val backend: String,
    val requestedRunCount: Int,
    val completedRunCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val timeoutCount: Int,
    val fallbackCount: Int,
    val modelPathSource: String,
    val genericFallbackModelConfigured: Boolean,
    val status: String,
    val reason: String,
)

internal fun buildLiteRtLmGpuBenchmarkRunSummary(
    rows: List<LiteRtLmGpuBenchmarkRow>,
    requestedRunCount: Int = rows.size,
    modelPathSource: String = rows.map { it.modelPathSource }.distinct().singleOrNull()
        ?: BenchmarkModelPathSource.AUTO.wireValue,
    genericFallbackModelConfigured: Boolean = rows.map { it.genericFallbackModelConfigured }.distinct().singleOrNull()
        ?: true,
): LiteRtLmGpuBenchmarkRunSummary {
    val completedRows = rows.filterNot { it.reason.startsWith("skipped_after_") }
    val timeoutCount = completedRows.count { it.timeout }
    val successCount = completedRows.count { it.status == "success" }
    val failureCount = completedRows.count { it.status != "success" && !it.timeout }
    val completedRunCount = completedRows.size
    val fallbackCount = completedRows.count { it.fallbackUsed }
    val allRequestedRunsSucceeded =
        requestedRunCount > 0 && completedRunCount == requestedRunCount && successCount == requestedRunCount
    val finalStatus = when {
        timeoutCount > 0 -> "failure"
        allRequestedRunsSucceeded -> "success"
        successCount > 0 -> "partial"
        else -> "failure"
    }
    val reason = when {
        timeoutCount > 0 -> "timeout"
        allRequestedRunsSucceeded -> "completed"
        successCount > 0 -> "partial_success"
        else -> completedRows.firstOrNull()?.reason ?: rows.firstOrNull()?.reason ?: "no_rows"
    }
    return LiteRtLmGpuBenchmarkRunSummary(
        backend = rows.map { it.backend }.distinct().singleOrNull() ?: "mixed",
        requestedRunCount = requestedRunCount,
        completedRunCount = completedRunCount,
        successCount = successCount,
        failureCount = failureCount,
        timeoutCount = timeoutCount,
        fallbackCount = fallbackCount,
        modelPathSource = modelPathSource,
        genericFallbackModelConfigured = genericFallbackModelConfigured,
        status = finalStatus,
        reason = reason,
    )
}

internal fun writeReports(
    appContext: Context,
    timestamp: String,
    timeoutMs: Long,
    rows: List<LiteRtLmGpuBenchmarkRow>,
) {
    val markdown = buildGpuBenchmarkMarkdown(timestamp = timestamp, timeoutMs = timeoutMs, rows = rows)
    val csv = buildGpuBenchmarkCsv(rows)
    writeUtf8Atomically(
        File(appContext.filesDir, markdownFileName(timestamp)),
        markdown,
    )
    writeUtf8Atomically(
        File(appContext.filesDir, csvFileName(timestamp)),
        csv,
    )
}

internal fun buildGpuBenchmarkMarkdown(
    timestamp: String,
    timeoutMs: Long,
    rows: List<LiteRtLmGpuBenchmarkRow>,
): String = buildString {
    val summary = buildLiteRtLmGpuBenchmarkRunSummary(rows)
    appendLine("# LiteRT-LM GPU Benchmark")
    appendLine()
    appendLine("- timestamp: `$timestamp`")
    appendLine("- route_type: `litert_lm_gpu_benchmark`")
    appendLine("- backend: `${summary.backend}`")
    appendLine("- requested_run_count: `${summary.requestedRunCount}`")
    appendLine("- completed_run_count: `${summary.completedRunCount}`")
    appendLine("- success_count: `${summary.successCount}`")
    appendLine("- failure_count: `${summary.failureCount}`")
    appendLine("- timeout_count: `${summary.timeoutCount}`")
    appendLine("- fallback_count: `${summary.fallbackCount}`")
    appendLine("- model_path_source: `${summary.modelPathSource}`")
    appendLine("- generic_fallback_model_configured: `${summary.genericFallbackModelConfigured}`")
    appendLine("- backend_variants: `${rows.map { it.backendVariant }.distinct().joinToString(",").ifBlank { "unknown" }}`")
    appendLine("- backends: `${rows.map { it.backend }.distinct().joinToString(",").ifBlank { "unknown" }}`")
    appendLine("- close_policies: `${rows.map { it.closePolicy }.distinct().joinToString(",").ifBlank { "unknown" }}`")
    appendLine("- phases: `${rows.map { it.phase }.distinct().joinToString(",").ifBlank { "unknown" }}`")
    appendLine("- max_output_tokens_lists: `${rows.map { it.maxOutputTokensList }.distinct().joinToString(",").ifBlank { "unknown" }}`")
    appendLine("- timeout_ms: `$timeoutMs`")
    appendLine("- fallback_setting_changed: `false`")
    appendLine("- backend_npu_touched: `false`")
    appendLine("- qairt_qnn_touched: `false`")
    appendLine("- send_api_variants: `${rows.map { it.sendApiVariant }.distinct().joinToString(",").ifBlank { "unknown" }}`")
    appendLine("- conversation_config_used_values: `${rows.map { it.conversationConfigUsed }.distinct().joinToString(",")}`")
    appendLine("- contents_api_used_values: `${rows.map { it.contentsApiUsed }.distinct().joinToString(",")}`")
    appendLine()
    appendLine("| backend_variant | backend | model_path_source | generic_fallback_model_configured | close_policy | phase | prompt | max_output_tokens | max_output_tokens_list | status | reason | engine_create_ms | conversation_create_ms | first_token_ms | ttft_ms | decode_ms | total_ms | output_tokens | tokens_per_second | timeout | fallback_used | intentionally_leaked_for_diagnostic | fresh_crash | send_api_variant | sampler_top_k | sampler_top_p | sampler_temperature | conversation_config_used | contents_api_used |")
    appendLine("| --- | --- | --- | --- | --- | --- | --- | ---: | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- |")
    rows.forEach { row ->
        appendLine(
            listOf(
                row.backendVariant.mdCell(),
                row.backend.mdCell(),
                row.modelPathSource.mdCell(),
                row.genericFallbackModelConfigured.toString(),
                row.closePolicy.mdCell(),
                row.phase.mdCell(),
                row.prompt.mdCell(),
                row.maxOutputTokens.toString(),
                row.maxOutputTokensList.mdCell(),
                row.status.mdCell(),
                row.reason.mdCell(),
                row.engineCreateMs.valueText(),
                row.conversationCreateMs.valueText(),
                row.firstTokenMs.valueText(),
                row.ttftMs.valueText(),
                row.decodeMs.valueText(),
                row.totalMs.valueText(),
                row.outputTokens.valueText(),
                row.tokensPerSecond?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                row.timeout.toString(),
                row.fallbackUsed.toString(),
                row.intentionallyLeakedForDiagnostic.toString(),
                row.freshCrash.toString(),
                row.sendApiVariant.mdCell(),
                row.samplerTopK.valueText(),
                row.samplerTopP?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                row.samplerTemperature?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                row.conversationConfigUsed.toString(),
                row.contentsApiUsed.toString(),
            ).joinToString(prefix = "| ", separator = " | ", postfix = " |"),
        )
    }
    appendLine()
    rows.forEachIndexed { index, row ->
        appendLine("## Case ${index + 1}")
        appendLine()
        appendLine("- route_type: `${row.routeType}`")
        appendLine("- backend: `${row.backend}`")
        appendLine("- backend_variant: `${row.backendVariant}`")
        appendLine("- close_policy: `${row.closePolicy}`")
        appendLine("- phase: `${row.phase}`")
        appendLine("- prompt: `${row.prompt}`")
        appendLine("- max_output_tokens: `${row.maxOutputTokens}`")
        appendLine("- max_output_tokens_list: `${row.maxOutputTokensList}`")
        appendLine("- model_path_source: `${row.modelPathSource}`")
        appendLine("- generic_fallback_model_configured: `${row.genericFallbackModelConfigured}`")
        appendLine("- model_path: `${row.modelPath}`")
        appendLine("- model_exists: `${row.modelExists}`")
        appendLine("- model_length: `${row.modelLength}`")
        appendLine("- finish_reason: `${row.finishReason ?: "unknown"}`")
        appendLine("- stop_reason: `${row.stopReason ?: "unknown"}`")
        appendLine("- status: `${row.status}`")
        appendLine("- reason: `${row.reason}`")
        appendLine("- send_exception_class: `${row.sendExceptionClass ?: "none"}`")
        appendLine("- send_exception_message: `${row.sendExceptionMessage ?: "none"}`")
        appendLine("- send_exception_cause_chain: `${row.sendExceptionCauseChain ?: "none"}`")
        appendLine("- send_api_variant: `${row.sendApiVariant}`")
        appendLine("- sampler_top_k: `${row.samplerTopK?.toString() ?: "none"}`")
        appendLine("- sampler_top_p: `${row.samplerTopP?.let { String.format(Locale.US, "%.2f", it) } ?: "none"}`")
        appendLine("- sampler_temperature: `${row.samplerTemperature?.let { String.format(Locale.US, "%.1f", it) } ?: "none"}`")
        appendLine("- conversation_config_used: `${row.conversationConfigUsed}`")
        appendLine("- contents_api_used: `${row.contentsApiUsed}`")
        appendLine("- measured_prefill_tokens: `${row.measuredPrefillTokens?.toString() ?: "unavailable"}`")
        appendLine("- prefill_token_source: `${row.prefillTokenSource}`")
        appendLine("- output_token_source: `${row.outputTokenSource}`")
        appendLine("- emit_count: `${row.emitCount}`")
        appendLine("- nonempty_emit_count: `${row.nonemptyEmitCount}`")
        appendLine("- raw_output_length: `${row.rawLength}`")
        appendLine("- sanitized_output_length: `${row.sanitizedLength}`")
        appendLine("- first_nonempty_emit_ms: `${row.firstNonemptyMs?.toString() ?: "unavailable"}`")
        appendLine("- flow_exception_type: `${row.flowExceptionType ?: "none"}`")
        appendLine("- finish_reason_available: `${row.finishReasonAvailable}`")
        appendLine("- stop_reason_available: `${row.stopReasonAvailable}`")
        appendLine("- callback_on_message_count: `${row.callbackOnMessageCount}`")
        appendLine("- callback_on_done_count: `${row.callbackOnDoneCount}`")
        appendLine("- callback_on_error_count: `${row.callbackOnErrorCount}`")
        appendLine("- chunk_type_length_summary: `${row.chunkTypeLengthSummary}`")
        appendLine("- flow_partial_raw_output: `${row.flowPartialRawOutput}`")
        appendLine("- flow_partial_emit_count: `${row.flowPartialEmitCount}`")
        appendLine("- flow_partial_nonempty_emit_count: `${row.flowPartialNonemptyEmitCount}`")
        appendLine("- flow_partial_first_nonempty_ms: `${row.flowPartialFirstNonemptyMs?.toString() ?: "unavailable"}`")
        appendLine("- intentionally_leaked_for_diagnostic: `${row.intentionallyLeakedForDiagnostic}`")
        appendLine("- fallback_used: `${row.fallbackUsed}`")
        appendLine("- timeout: `${row.timeout}`")
        appendLine("- fresh_crash: `${row.freshCrash}`")
        appendLine()
        appendLine("### raw_output")
        appendLine()
        appendLine("```text")
        appendLine(row.rawOutput)
        appendLine("```")
        appendLine()
        appendLine("### sanitized_output")
        appendLine()
        appendLine("```text")
        appendLine(row.sanitizedOutput)
        appendLine("```")
        appendLine()
    }
}

internal fun buildGpuBenchmarkCsv(rows: List<LiteRtLmGpuBenchmarkRow>): String {
    val headers = listOf(
        "timestamp",
        "route_type",
        "backend",
        "backend_variant",
        "close_policy",
        "phase",
        "prompt",
        "max_output_tokens",
        "max_output_tokens_list",
        "model_path_source",
        "generic_fallback_model_configured",
        "model_path",
        "model_exists",
        "model_length",
        "engine_create_ms",
        "conversation_create_ms",
        "first_token_ms",
        "ttft_ms",
        "decode_ms",
        "total_ms",
        "output_tokens",
        "tokens_per_second",
        "finish_reason",
        "stop_reason",
        "raw_output",
        "sanitized_output",
        "status",
        "reason",
        "send_exception_class",
        "send_exception_message",
        "send_exception_cause_chain",
        "intentionally_leaked_for_diagnostic",
        "fallback_used",
        "timeout",
        "fresh_crash",
        "send_api_variant",
        "sampler_top_k",
        "sampler_top_p",
        "sampler_temperature",
        "conversation_config_used",
        "contents_api_used",
        "measured_prefill_tokens",
        "prefill_token_source",
        "output_token_source",
        "emit_count",
        "nonempty_emit_count",
        "raw_output_length",
        "sanitized_output_length",
        "first_nonempty_emit_ms",
        "flow_exception_type",
        "finish_reason_available",
        "stop_reason_available",
        "callback_on_message_count",
        "callback_on_done_count",
        "callback_on_error_count",
        "chunk_type_length_summary",
        "flow_partial_raw_output",
        "flow_partial_emit_count",
        "flow_partial_nonempty_emit_count",
        "flow_partial_first_nonempty_ms",
    )
    return buildString {
        appendLine(headers.joinToString(",") { csvCell(it) })
        rows.forEach { row ->
            appendLine(
                listOf(
                    row.timestamp,
                    row.routeType,
                    row.backend,
                    row.backendVariant,
                    row.closePolicy,
                    row.phase,
                    row.prompt,
                    row.maxOutputTokens.toString(),
                    row.maxOutputTokensList,
                    row.modelPathSource,
                    row.genericFallbackModelConfigured.toString(),
                    row.modelPath,
                    row.modelExists.toString(),
                    row.modelLength.toString(),
                    row.engineCreateMs?.toString().orEmpty(),
                    row.conversationCreateMs?.toString().orEmpty(),
                    row.firstTokenMs?.toString().orEmpty(),
                    row.ttftMs?.toString().orEmpty(),
                    row.decodeMs?.toString().orEmpty(),
                    row.totalMs?.toString().orEmpty(),
                    row.outputTokens?.toString() ?: "unavailable",
                    row.tokensPerSecond?.let { String.format(Locale.US, "%.4f", it) }.orEmpty(),
                    row.finishReason.orEmpty(),
                    row.stopReason.orEmpty(),
                    row.rawOutput,
                    row.sanitizedOutput,
                    row.status,
                    row.reason,
                    row.sendExceptionClass.orEmpty(),
                    row.sendExceptionMessage.orEmpty(),
                    row.sendExceptionCauseChain.orEmpty(),
                    row.intentionallyLeakedForDiagnostic.toString(),
                    row.fallbackUsed.toString(),
                    row.timeout.toString(),
                    row.freshCrash.toString(),
                    row.sendApiVariant,
                    row.samplerTopK?.toString().orEmpty(),
                    row.samplerTopP?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
                    row.samplerTemperature?.let { String.format(Locale.US, "%.1f", it) }.orEmpty(),
                    row.conversationConfigUsed.toString(),
                    row.contentsApiUsed.toString(),
                    row.measuredPrefillTokens?.toString() ?: "unavailable",
                    row.prefillTokenSource,
                    row.outputTokenSource,
                    row.emitCount.toString(),
                    row.nonemptyEmitCount.toString(),
                    row.rawLength.toString(),
                    row.sanitizedLength.toString(),
                    row.firstNonemptyMs?.toString().orEmpty(),
                    row.flowExceptionType.orEmpty(),
                    row.finishReasonAvailable.toString(),
                    row.stopReasonAvailable.toString(),
                    row.callbackOnMessageCount.toString(),
                    row.callbackOnDoneCount.toString(),
                    row.callbackOnErrorCount.toString(),
                    row.chunkTypeLengthSummary,
                    row.flowPartialRawOutput,
                    row.flowPartialEmitCount.toString(),
                    row.flowPartialNonemptyEmitCount.toString(),
                    row.flowPartialFirstNonemptyMs?.toString().orEmpty(),
                ).joinToString(",") { csvCell(it) },
            )
        }
    }
}

private fun writeState(
    stateFile: File,
    timestamp: String,
    backendVariant: BenchmarkBackendVariant,
    closePolicy: BenchmarkClosePolicy,
    phase: BenchmarkPhase,
    status: String,
    reason: String,
    markdownFileName: String,
    csvFileName: String,
    timeoutMs: Long,
    modelPathSource: String = BenchmarkModelPathSource.AUTO.wireValue,
    genericFallbackModelConfigured: Boolean = true,
    requestedRunCount: Int? = null,
    completedRunCount: Int? = null,
    successCount: Int? = null,
    failureCount: Int? = null,
    timeoutCount: Int? = null,
    fallbackCount: Int? = null,
) {
    val lines = buildList {
        addAll(
            listOf(
            "timestamp=$timestamp",
            "route_type=litert_lm_gpu_benchmark",
            "backend=${backendVariant.backendLabel}",
            "backend_variant=${backendVariant.wireValue}",
            "close_policy=${closePolicy.wireValue}",
            "phase=${phase.wireValue}",
            "intentionally_leaked_for_diagnostic=${closePolicy.intentionallyLeakedForDiagnostic}",
            "status=$status",
            "reason=$reason",
            "markdown_file=$markdownFileName",
            "csv_file=$csvFileName",
            "timeout_ms=$timeoutMs",
            "fallback_setting_changed=false",
            "backend_npu_touched=false",
            "qairt_qnn_touched=false",
            "model_path_source=$modelPathSource",
            "generic_fallback_model_configured=$genericFallbackModelConfigured",
            ),
        )
        requestedRunCount?.let { add("requested_run_count=$it") }
        completedRunCount?.let { add("completed_run_count=$it") }
        successCount?.let { add("success_count=$it") }
        failureCount?.let { add("failure_count=$it") }
        timeoutCount?.let { add("timeout_count=$it") }
        fallbackCount?.let { add("fallback_count=$it") }
    }
    writeUtf8Atomically(stateFile, lines.joinToString("\n") + "\n")
}

internal fun writeUtf8Atomically(target: File, content: String) {
    val parent = target.parentFile ?: error("atomic write target has no parent: $target")
    val temporary = File.createTempFile(".${target.name}.", ".tmp", parent)
    try {
        FileOutputStream(temporary).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        Files.move(
            temporary.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        FileChannel.open(parent.toPath(), StandardOpenOption.READ).use { directory ->
            directory.force(true)
        }
    } finally {
        temporary.delete()
    }
}

private fun writeMarker(
    appContext: Context,
    timestamp: String,
    backendVariant: BenchmarkBackendVariant,
    closePolicy: BenchmarkClosePolicy,
    phase: BenchmarkPhase,
    stage: String,
    detail: String,
    maxOutputTokensList: String = "unknown",
) {
    runCatching {
        val sanitizedDetail = detail.replace('\n', ' ').take(500)
        val elapsedRealtimeMs = SystemClock.elapsedRealtime()
        val wallTimeMs = System.currentTimeMillis()
        val markerText = listOf(
            "timestamp=$timestamp",
            "route_type=litert_lm_gpu_benchmark",
            "backend=${backendVariant.backendLabel}",
            "backend_variant=${backendVariant.wireValue}",
            "close_policy=${closePolicy.wireValue}",
            "phase=${phase.wireValue}",
            "max_output_tokens_list=$maxOutputTokensList",
            "stage=$stage",
            "detail=$sanitizedDetail",
            "elapsed_realtime_ms=$elapsedRealtimeMs",
            "wall_time_ms=$wallTimeMs",
        ).joinToString("\n") + "\n"
        writeUtf8Atomically(
            File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.MARKER_FILE_NAME),
            markerText,
        )
        File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.MARKER_HISTORY_FILE_NAME).appendText(
            markerText + "\n",
            Charsets.UTF_8,
        )
    }
}

private fun sanitizeOutput(raw: String): String =
    raw.replace("\u0000", "")
        .trim()

private fun exceptionReason(throwable: Throwable, prefix: String): String =
    "$prefix:${throwable.javaClass.name}:${throwable.message.orEmpty()}"

private fun causeChainText(throwable: Throwable): String {
    val parts = mutableListOf<String>()
    val seen = mutableSetOf<Throwable>()

    fun appendThrowable(label: String, value: Throwable?, depth: Int) {
        if (value == null) return
        if (!seen.add(value)) {
            parts += "$label[$depth]=cycle:${value.javaClass.name}"
            return
        }
        parts += "$label[$depth]=${value.javaClass.name}:${value.message.orEmpty()}"
        value.suppressed.forEachIndexed { index, suppressed ->
            appendThrowable("$label[$depth].suppressed[$index]", suppressed, depth + 1)
        }
        appendThrowable("$label.cause", value.cause, depth + 1)
    }

    appendThrowable("root", throwable, 0)
    return parts.joinToString(" -> ")
}

private fun probeNoArgString(target: Any?, methodNames: List<String>): String? {
    if (target == null) return null
    methodNames.forEach { name ->
        val method: Method = target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.isEmpty()
        } ?: return@forEach
        val value = runCatching { method.invoke(target) }.getOrNull()
            ?: return@forEach
        return value.toString().takeIf { it.isNotBlank() }
    }
    return null
}

private fun secondsToMs(seconds: Double): Long? =
    seconds.takeIf { it >= 0.0 && it.isFinite() }
        ?.let { (it * 1000.0).toLong() }

private fun timestamp(): String =
    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

private fun markdownFileName(timestamp: String): String =
    "litert_lm_gpu_benchmark_${timestamp}.md"

private fun csvFileName(timestamp: String): String =
    "litert_lm_gpu_benchmark_${timestamp}.csv"

private fun Long?.valueText(): String = this?.toString().orEmpty()

private fun Int?.valueText(): String = this?.toString().orEmpty()

private fun String.mdCell(): String =
    replace("|", "\\|")
        .replace("\n", "<br>")

private fun csvCell(value: String): String =
    "\"" + value.replace("\"", "\"\"") + "\""
