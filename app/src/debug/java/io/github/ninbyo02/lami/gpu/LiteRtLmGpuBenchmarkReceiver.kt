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
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.collect
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

@OptIn(ExperimentalApi::class)
class LiteRtLmGpuBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        if (intent.getBooleanExtra(EXTRA_COMMAND_CANCEL, false)) {
            val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP)?.takeIf { it.isNotBlank() } ?: timestamp()
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
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
        val backendVariant = backendVariant(intent)
        val closePolicy = closePolicy(intent)
        val phase = phase(intent)
        val modelPathSource = modelPathSource(intent)
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
        cancelRequested.set(false)
        val receiverCancelMarker: (String, String) -> Unit = { stage, detail ->
            writeMarker(appContext, timestamp, backendVariant, closePolicy, phase, stage, detail)
        }
        activeCancelMarker.set(receiverCancelMarker)
        val receiverCancelWatcher = startCancelRelayWatcher(appContext, timestamp)

        val pendingResult = goAsync()
        receiverDispatcher.execute {
            try {
                handle(appContext, intent, timestamp, timeoutMs, backendVariant, closePolicy, phase, modelPathSource)
            } catch (throwable: Throwable) {
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
                activeCancelMarker.compareAndSet(receiverCancelMarker, null)
                val requireProcessCleanup = closeTimeoutRequiresProcessCleanup.getAndSet(false)
                running.set(false)
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
        backendVariant: BenchmarkBackendVariant,
        closePolicy: BenchmarkClosePolicy,
        phase: BenchmarkPhase,
        modelPathSource: BenchmarkModelPathSource,
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
            var stopAfterTimeout = false
            prompts.forEach { prompt ->
                maxOutputTokensValues.forEach { maxOutputTokens ->
                    if (stopAfterTimeout) {
                        rows += LiteRtLmGpuBenchmarkRow.failure(
                            timestamp = timestamp,
                            backendVariant = backendVariant,
                            closePolicy = closePolicy,
                            phase = phase,
                            maxOutputTokensList = maxOutputTokensList,
                            prompt = prompt,
                            maxOutputTokens = maxOutputTokens,
                            modelPath = modelPath,
                            reason = "skipped_after_timeout",
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
                            timeoutMs = timeoutMs,
                            modelPathSource = modelResolution.source,
                            genericFallbackModelConfigured = modelResolution.genericFallbackModelConfigured,
                        )
                        rows += result
                        if (result.timeout) {
                            stopAfterTimeout = true
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
        timeoutMs: Long,
        modelPathSource: BenchmarkModelPathSource,
        genericFallbackModelConfigured: Boolean,
    ): LiteRtLmGpuBenchmarkRow {
        closeTimedOut.set(false)
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
                    caseTimeoutMs = timeoutMs,
                    modelPathSource = modelPathSource,
                    genericFallbackModelConfigured = genericFallbackModelConfigured,
                )
            },
        )
        activeCaseFuture.set(future)
        if (cancelRequested.get()) {
            cancelCurrentRun()
        }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS).let { row ->
                if (closeTimedOut.get()) {
                    row.copy(status = "failure", reason = "engine_close_timeout", timeout = true)
                } else {
                    row
                }
            }
        } catch (_: TimeoutException) {
            future.cancel(true)
            LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                maxOutputTokensList = maxOutputTokensList,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                modelPath = modelPath,
                reason = "case_timeout_${timeoutMs}ms",
                modelExists = true,
                modelLength = modelLength,
                timeout = true,
                freshCrash = false,
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = genericFallbackModelConfigured,
            )
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
        modelPathSource: BenchmarkModelPathSource,
        genericFallbackModelConfigured: Boolean,
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
            val rawOutput = try {
                if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY) {
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
                if (streamingThrowable is CancellationException ||
                    streamingThrowable is InterruptedException ||
                    cancelRequested.get() ||
                    Thread.currentThread().isInterrupted
                ) {
                    sendException = streamingThrowable
                    throw streamingThrowable
                }
                if (backendVariant == BenchmarkBackendVariant.GALLERY_CHAT_PARITY) {
                    sendException = streamingThrowable
                    throw streamingThrowable
                } else {
                    fallbackUsed = true
                    try {
                        val message = conversation.sendMessage(prompt)
                        firstTokenMs = SystemClock.elapsedRealtime() - decodeStartMs
                        message.contents.toString()
                    } catch (blockingThrowable: Throwable) {
                        runCatching { blockingThrowable.addSuppressed(streamingThrowable) }
                        sendException = blockingThrowable
                        throw blockingThrowable
                    }
                }
            }
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
            val outputTokens = benchmarkSnapshot?.decodeTokenCount?.takeIf { it >= 0 }
            val tokensPerSecond = benchmarkSnapshot?.decodeTokensPerSecond?.takeIf { it > 0.0 }
                ?: if (outputTokens != null && decodeDurationMs > 0L) {
                    outputTokens * 1000.0 / decodeDurationMs
                } else {
                    null
                }
            val totalMs = SystemClock.elapsedRealtime() - totalStartMs
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
                finishReason = probeNoArgString(conversation, FINISH_REASON_METHODS)
                    ?: probeNoArgString(engine, FINISH_REASON_METHODS),
                stopReason = probeNoArgString(conversation, STOP_REASON_METHODS)
                    ?: probeNoArgString(engine, STOP_REASON_METHODS),
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
                sendApiVariant = sendApiVariant(backendVariant),
                samplerTopK = samplerTopK(backendVariant),
                samplerTopP = samplerTopP(backendVariant),
                samplerTemperature = samplerTemperature(backendVariant),
                conversationConfigUsed = conversationConfigUsed(backendVariant),
                contentsApiUsed = contentsApiUsed(backendVariant),
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = genericFallbackModelConfigured,
            )
        } catch (throwable: Throwable) {
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
            val reportedThrowable = sendException ?: throwable
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
                sendApiVariant = sendApiVariant(backendVariant),
                samplerTopK = samplerTopK(backendVariant),
                samplerTopP = samplerTopP(backendVariant),
                samplerTemperature = samplerTemperature(backendVariant),
                conversationConfigUsed = conversationConfigUsed(backendVariant),
                contentsApiUsed = contentsApiUsed(backendVariant),
                modelPathSource = modelPathSource.wireValue,
                genericFallbackModelConfigured = genericFallbackModelConfigured,
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
            )
        }
    }

    private fun collectStreamingResponse(
        conversation: Conversation,
        prompt: String,
        decodeStartMs: Long,
        onFirstToken: (Long) -> Unit,
    ): String {
        val builder = StringBuilder()
        var firstSeen = false
        var lastChunk: String? = null
        runBlocking {
            conversation.sendMessageAsync(prompt).collect { message ->
                if (cancelRequested.get()) {
                    throw CancellationException("debug_foreground_ui_cancelled")
                }
                val chunk = message.contents.toString()
                val usable = chunk.takeIf { it.isNotBlank() } ?: message.toString().takeIf { it.isNotBlank() }
                if (usable.isNullOrBlank()) return@collect
                if (!firstSeen) {
                    firstSeen = true
                    onFirstToken(SystemClock.elapsedRealtime() - decodeStartMs)
                }
                if (usable == lastChunk) return@collect
                lastChunk = usable
                builder.append(usable)
            }
        }
        return builder.toString()
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
    ): String {
        val contents = Contents.of(listOf(Content.Text(prompt)))
        writeMarker(
            appContext = appContext,
            timestamp = timestamp,
            backendVariant = backendVariant,
            closePolicy = closePolicy,
            phase = phase,
            stage = "contents_created",
            detail = "send_api_variant=gallery_contents_callback contents_api_used=true content_count=${contents.contents.size} prompt_length=${prompt.length}",
            maxOutputTokensList = maxOutputTokensList,
        )

        val doneLatch = CountDownLatch(1)
        val callbackError = AtomicReference<Throwable?>(null)
        val builder = StringBuilder()
        val firstSeen = AtomicBoolean(false)
        var lastChunk: String? = null
        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                val chunk = renderGalleryParityMessageChunk(message)
                if (chunk.isBlank()) return
                if (firstSeen.compareAndSet(false, true)) {
                    val firstTokenMs = SystemClock.elapsedRealtime() - decodeStartMs
                    onFirstToken(firstTokenMs)
                    writeMarker(
                        appContext = appContext,
                        timestamp = timestamp,
                        backendVariant = backendVariant,
                        closePolicy = closePolicy,
                        phase = phase,
                        stage = "callback_first_token",
                        detail = "send_api_variant=gallery_contents_callback first_token_ms=$firstTokenMs raw_length=${chunk.length}",
                        maxOutputTokensList = maxOutputTokensList,
                    )
                }
                if (chunk == lastChunk) return
                lastChunk = chunk
                builder.append(chunk)
            }

            override fun onDone() {
                writeMarker(
                    appContext = appContext,
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    stage = "callback_send_finished",
                    detail = "send_api_variant=gallery_contents_callback raw_length=${builder.length}",
                    maxOutputTokensList = maxOutputTokensList,
                )
                doneLatch.countDown()
            }

            override fun onError(throwable: Throwable) {
                callbackError.set(throwable)
                writeMarker(
                    appContext = appContext,
                    timestamp = timestamp,
                    backendVariant = backendVariant,
                    closePolicy = closePolicy,
                    phase = phase,
                    stage = "callback_send_exception",
                    detail = "send_api_variant=gallery_contents_callback class=${throwable.javaClass.name} message=${throwable.message.orEmpty().take(200)} cause_chain=${causeChainText(throwable)}",
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
            detail = "send_api_variant=gallery_contents_callback conversation_config_used=true contents_api_used=true sampler_top_k=$GALLERY_CHAT_PARITY_TOP_K sampler_top_p=$GALLERY_CHAT_PARITY_TOP_P sampler_temperature=$GALLERY_CHAT_PARITY_TEMPERATURE",
            maxOutputTokensList = maxOutputTokensList,
        )
        try {
            conversation.sendMessageAsync(contents, callback, emptyMap<String, Any>())
        } catch (throwable: Throwable) {
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "callback_send_exception",
                detail = "send_api_variant=gallery_contents_callback class=${throwable.javaClass.name} message=${throwable.message.orEmpty().take(200)} cause_chain=${causeChainText(throwable)}",
                maxOutputTokensList = maxOutputTokensList,
            )
            throw throwable
        }
        val completed = doneLatch.await(callbackTimeoutMs, TimeUnit.MILLISECONDS)
        if (!completed) {
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "callback_send_timeout",
                detail = "send_api_variant=gallery_contents_callback timeout_ms=$callbackTimeoutMs raw_length=${builder.length}",
                maxOutputTokensList = maxOutputTokensList,
            )
            throw TimeoutException("gallery_contents_callback_timeout_${callbackTimeoutMs}ms")
        }
        callbackError.get()?.let { throw it }
        return builder.toString()
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
            .coerceIn(1_000L, 300_000L)

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
                closeFuture.get(CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
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
                    detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} target=$target timeout_ms=$CLOSE_TIMEOUT_MS",
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
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                backendVariant = backendVariant,
                closePolicy = closePolicy,
                phase = phase,
                stage = "close_exception",
                detail = "backend_variant=${backendVariant.wireValue} close_policy=${closePolicy.wireValue} phase=${phase.wireValue} target=$target class=${throwable.javaClass.name} message=${throwable.message.orEmpty()} cause_chain=${causeChainText(throwable)}",
                maxOutputTokensList = maxOutputTokensList,
            )
        }
    }

    private data class BenchmarkSnapshot(
        val timeToFirstTokenMs: Long?,
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
        const val EXTRA_PROMPTS_BASE64 = "prompts_base64"
        const val EXTRA_MAX_OUTPUT_TOKENS_LIST = "max_output_tokens_list"
        const val EXTRA_MAX_OUTPUT_TOKENS_LIST_BASE64 = "max_output_tokens_list_base64"
        const val EXTRA_BACKEND_VARIANT = "backend_variant"
        const val EXTRA_CLOSE_POLICY = "close_policy"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_TIMEOUT_MS = "timeout_ms"
        const val EXTRA_MODEL_PATH_SOURCE = "model_path_source"
        const val STATE_FILE_NAME = "litert_lm_gpu_benchmark_state.txt"
        const val CANCEL_RELAY_FILE_NAME = "litert_lm_gpu_benchmark_cancel.txt"
        const val MARKER_FILE_NAME = "litert_lm_gpu_benchmark_marker.txt"
        const val MARKER_HISTORY_FILE_NAME = "litert_lm_gpu_benchmark_marker_history.txt"
        private const val DEFAULT_TIMEOUT_MS = 60_000L
        private const val CLOSE_TIMEOUT_MS = 10_000L
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
        private val GPU_TOKEN_PROBE_MAX_OUTPUT_TOKENS_ALLOWLIST = setOf(16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 22400, 24576, 28800, 32768, 32769, 65536, 131072, 262144, 524288, 1048576)
        private val FINISH_REASON_METHODS = listOf("getFinishReason", "finishReason", "getDoneReason", "doneReason")
        private val STOP_REASON_METHODS = listOf("getStopReason", "stopReason", "getStop", "stop")
        private val running = AtomicBoolean(false)
        private val cancelRequested = AtomicBoolean(false)
        private val activeCaseFuture = AtomicReference<Future<*>?>(null)
        private val activeConversation = AtomicReference<Conversation?>(null)
        private val activeEngine = AtomicReference<Engine?>(null)
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
    val timeoutCount = rows.count { it.timeout }
    val successCount = rows.count { it.status == "success" }
    val failureCount = rows.count { it.status != "success" && !it.timeout }
    val completedRunCount = rows.count { it.reason != "skipped_after_timeout" }
    val fallbackCount = rows.count { it.fallbackUsed }
    val finalStatus = when {
        timeoutCount > 0 -> "failure"
        successCount == rows.size && rows.isNotEmpty() -> "success"
        successCount > 0 -> "partial"
        else -> "failure"
    }
    val reason = when {
        timeoutCount > 0 -> "timeout"
        successCount == rows.size && rows.isNotEmpty() -> "completed"
        successCount > 0 -> "partial_success"
        else -> rows.firstOrNull()?.reason ?: "no_rows"
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
    File(appContext.filesDir, markdownFileName(timestamp)).writeText(
        buildGpuBenchmarkMarkdown(timestamp = timestamp, timeoutMs = timeoutMs, rows = rows),
        Charsets.UTF_8,
    )
    File(appContext.filesDir, csvFileName(timestamp)).writeText(
        buildGpuBenchmarkCsv(rows),
        Charsets.UTF_8,
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
                    row.outputTokens?.toString().orEmpty(),
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
    stateFile.writeText(
        lines.joinToString("\n") + "\n",
        Charsets.UTF_8,
    )
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
        File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.MARKER_FILE_NAME).writeText(
            markerText,
            Charsets.UTF_8,
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
