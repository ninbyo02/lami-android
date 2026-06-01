package io.github.ninbyo02.lami.gpu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import java.io.File
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalApi::class)
class LiteRtLmGpuBenchmarkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP)
            ?.takeIf { it.isNotBlank() }
            ?: timestamp()
        val timeoutMs = timeoutMs(intent)
        writeMarker(
            appContext = appContext,
            timestamp = timestamp,
            stage = "receiver_started",
            detail = "onReceive_enter",
        )
        val stateFile = File(appContext.filesDir, STATE_FILE_NAME)
        if (!running.compareAndSet(false, true)) {
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                stage = "receiver_started",
                detail = "already_running",
            )
            writeState(
                stateFile = stateFile,
                timestamp = timestamp,
                status = "blocked",
                reason = "already_running",
                markdownFileName = "",
                csvFileName = "",
                timeoutMs = timeoutMs,
            )
            return
        }

        val pendingResult = goAsync()
        receiverDispatcher.execute {
            try {
                handle(appContext, intent, timestamp, timeoutMs)
            } catch (throwable: Throwable) {
                writeMarker(
                    appContext = appContext,
                    timestamp = timestamp,
                    stage = "receiver_exception",
                    detail = "${throwable.javaClass.simpleName}:${throwable.message.orEmpty().take(120)}",
                )
                val row = LiteRtLmGpuBenchmarkRow.failure(
                    timestamp = timestamp,
                    prompt = "",
                    maxOutputTokens = 0,
                    modelPath = "",
                    reason = throwable.message?.takeIf { it.isNotBlank() }
                        ?: "receiver_exception:${throwable.javaClass.simpleName}",
                    timeout = false,
                    freshCrash = false,
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
                    status = "failure",
                    reason = row.reason,
                    markdownFileName = markdownFileName(timestamp),
                    csvFileName = csvFileName(timestamp),
                    timeoutMs = timeoutMs,
                )
            } finally {
                running.set(false)
                pendingResult.finish()
            }
        }
    }

    private fun handle(
        appContext: Context,
        intent: Intent,
        timestamp: String,
        timeoutMs: Long,
    ) {
        val stateFile = File(appContext.filesDir, STATE_FILE_NAME)
        if (!BuildConfig.DEBUG || BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            val row = LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                prompt = "",
                maxOutputTokens = 0,
                modelPath = "",
                reason = "wrong_variant",
                timeout = false,
                freshCrash = false,
            )
            writeReports(appContext, timestamp, timeoutMs, listOf(row))
            writeState(
                stateFile = stateFile,
                timestamp = timestamp,
                status = "blocked",
                reason = "wrong_variant",
                markdownFileName = markdownFileName(timestamp),
                csvFileName = csvFileName(timestamp),
                timeoutMs = timeoutMs,
            )
            return
        }

        val prompts = prompts(intent)
        val maxOutputTokensValues = maxOutputTokensValues(intent)
        val modelPath = resolveModelPath(appContext, intent)
        val modelFile = modelPath?.let(::File)
        writeMarker(
            appContext = appContext,
            timestamp = timestamp,
            stage = "model_resolved",
            detail = "model_path=${modelPath.orEmpty()} model_exists=${modelFile?.exists() ?: false} model_length=${modelFile?.length() ?: 0L}",
        )
        writeState(
            stateFile = stateFile,
            timestamp = timestamp,
            status = "running",
            reason = "benchmark_running",
            markdownFileName = "",
            csvFileName = "",
            timeoutMs = timeoutMs,
        )

        val rows = mutableListOf<LiteRtLmGpuBenchmarkRow>()
        if (modelPath.isNullOrBlank()) {
            prompts.forEach { prompt ->
                maxOutputTokensValues.forEach { maxOutputTokens ->
                    rows += LiteRtLmGpuBenchmarkRow.failure(
                        timestamp = timestamp,
                        prompt = prompt,
                        maxOutputTokens = maxOutputTokens,
                        modelPath = "",
                        reason = "model_path_not_configured",
                        timeout = false,
                        freshCrash = false,
                    )
                }
            }
        } else if (modelFile?.isFile != true || modelFile.canRead() != true || modelFile.length() <= 0L) {
            prompts.forEach { prompt ->
                maxOutputTokensValues.forEach { maxOutputTokens ->
                    rows += LiteRtLmGpuBenchmarkRow.failure(
                        timestamp = timestamp,
                        prompt = prompt,
                        maxOutputTokens = maxOutputTokens,
                        modelPath = modelPath,
                        reason = "model_file_invalid",
                        modelExists = modelFile?.exists() == true,
                        modelLength = modelFile?.length() ?: 0L,
                        timeout = false,
                        freshCrash = false,
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
                            prompt = prompt,
                            maxOutputTokens = maxOutputTokens,
                            modelPath = modelPath,
                            reason = "skipped_after_timeout",
                            modelExists = true,
                            modelLength = modelFile.length(),
                            timeout = false,
                            freshCrash = false,
                        )
                    } else {
                        val result = runCaseWithTimeout(
                            appContext = appContext,
                            timestamp = timestamp,
                            prompt = prompt,
                            maxOutputTokens = maxOutputTokens,
                            modelPath = modelPath,
                            modelLength = modelFile.length(),
                            timeoutMs = timeoutMs,
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
            stage = "report_written",
            detail = "rows=${rows.size} markdown=${markdownFileName(timestamp)} csv=${csvFileName(timestamp)}",
        )
        val successCount = rows.count { it.status == "success" }
        val timeoutCount = rows.count { it.timeout }
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
        writeState(
            stateFile = stateFile,
            timestamp = timestamp,
            status = finalStatus,
            reason = reason,
            markdownFileName = markdownFileName(timestamp),
            csvFileName = csvFileName(timestamp),
            timeoutMs = timeoutMs,
        )
    }

    private fun runCaseWithTimeout(
        appContext: Context,
        timestamp: String,
        prompt: String,
        maxOutputTokens: Int,
        modelPath: String,
        modelLength: Long,
        timeoutMs: Long,
    ): LiteRtLmGpuBenchmarkRow {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmark-$maxOutputTokens")
        }
        val future = executor.submit(
            Callable {
                runCase(
                    appContext = appContext,
                    timestamp = timestamp,
                    prompt = prompt,
                    maxOutputTokens = maxOutputTokens,
                    modelPath = modelPath,
                    modelLength = modelLength,
                )
            },
        )
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                modelPath = modelPath,
                reason = "case_timeout_${timeoutMs}ms",
                modelExists = true,
                modelLength = modelLength,
                timeout = true,
                freshCrash = false,
            )
        } catch (throwable: Throwable) {
            LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                modelPath = modelPath,
                reason = throwable.message?.takeIf { it.isNotBlank() }
                    ?: "case_exception:${throwable.javaClass.simpleName}",
                modelExists = true,
                modelLength = modelLength,
                timeout = false,
                freshCrash = false,
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun runCase(
        appContext: Context,
        timestamp: String,
        prompt: String,
        maxOutputTokens: Int,
        modelPath: String,
        modelLength: Long,
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
        return try {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                visionBackend = Backend.GPU(),
                audioBackend = Backend.CPU(),
                maxNumTokens = maxOutputTokens,
                cacheDir = appContext.cacheDir.absolutePath,
            )
            val engineStartMs = SystemClock.elapsedRealtime()
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                stage = "engine_create_started",
                detail = "max_output_tokens=$maxOutputTokens prompt_length=${prompt.length}",
            )
            engine = Engine(config)
            engine.initialize()
            engineCreateMs = SystemClock.elapsedRealtime() - engineStartMs
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                stage = "engine_create_finished",
                detail = "max_output_tokens=$maxOutputTokens engine_create_ms=$engineCreateMs",
            )

            val conversationStartMs = SystemClock.elapsedRealtime()
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                stage = "conversation_create_started",
                detail = "max_output_tokens=$maxOutputTokens",
            )
            conversation = engine.createConversation()
            conversationCreateMs = SystemClock.elapsedRealtime() - conversationStartMs

            val decodeStartMs = SystemClock.elapsedRealtime()
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                stage = "prompt_started",
                detail = "max_output_tokens=$maxOutputTokens prompt_length=${prompt.length}",
            )
            val rawOutput = runCatching {
                collectStreamingResponse(conversation, prompt, decodeStartMs) { first ->
                    firstTokenMs = first
                }
            }.getOrElse {
                fallbackUsed = true
                val message = conversation.sendMessage(prompt)
                firstTokenMs = SystemClock.elapsedRealtime() - decodeStartMs
                message.contents.toString()
            }
            val decodeDurationMs = SystemClock.elapsedRealtime() - decodeStartMs
            decodeMs = decodeDurationMs
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                stage = "prompt_finished",
                detail = "max_output_tokens=$maxOutputTokens decode_ms=$decodeDurationMs raw_length=${rawOutput.length}",
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
                backend = "GPU",
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
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
                fallbackUsed = fallbackUsed,
                timeout = false,
                freshCrash = false,
            )
        } catch (throwable: Throwable) {
            writeMarker(
                appContext = appContext,
                timestamp = timestamp,
                stage = "case_exception",
                detail = "max_output_tokens=$maxOutputTokens class=${throwable.javaClass.simpleName} message=${throwable.message.orEmpty().take(120)}",
            )
            LiteRtLmGpuBenchmarkRow.failure(
                timestamp = timestamp,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                modelPath = modelPath,
                reason = throwable.message?.takeIf { it.isNotBlank() }
                    ?: "run_exception:${throwable.javaClass.simpleName}",
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
                fallbackUsed = fallbackUsed,
                timeout = false,
                freshCrash = false,
            )
        } finally {
            runCatching { conversation?.close() }
            runCatching { engine?.close() }
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

    private fun probeBenchmarkSnapshot(conversation: Conversation?): BenchmarkSnapshot? {
        val benchmark = runCatching { conversation?.getBenchmarkInfo() }.getOrNull() ?: return null
        return BenchmarkSnapshot(
            timeToFirstTokenMs = secondsToMs(benchmark.timeToFirstTokenInSecond),
            decodeTokenCount = benchmark.lastDecodeTokenCount,
            decodeTokensPerSecond = benchmark.lastDecodeTokensPerSecond,
        )
    }

    private fun resolveModelPath(appContext: Context, intent: Intent): String? {
        intent.getStringExtra(EXTRA_MODEL_PATH)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val settingsModelPath = runCatching {
            runBlocking { SettingsPreferences(appContext).getValidLocalBaseModelPathOrNull() }
        }.getOrNull()
        if (!settingsModelPath.isNullOrBlank()) return settingsModelPath
        val localModelsDir = File(appContext.filesDir, "local_models")
        return localModelsDir
            .listFiles { file -> file.isFile && file.extension.equals("litertlm", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.firstOrNull()
            ?.absolutePath
    }

    private fun prompts(intent: Intent): List<String> {
        val raw = intent.getStringExtra(EXTRA_PROMPTS)
            ?.takeIf { it.isNotBlank() }
            ?: return DEFAULT_PROMPTS
        return raw.split("|||")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?: DEFAULT_PROMPTS
    }

    private fun maxOutputTokensValues(intent: Intent): List<Int> {
        val raw = intent.getStringExtra(EXTRA_MAX_OUTPUT_TOKENS_LIST)
            ?.takeIf { it.isNotBlank() }
            ?: return DEFAULT_MAX_OUTPUT_TOKENS
        return raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..512 }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?: DEFAULT_MAX_OUTPUT_TOKENS
    }

    private fun timeoutMs(intent: Intent): Long =
        intent.getLongExtra(EXTRA_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
            .coerceIn(1_000L, 300_000L)

    private data class BenchmarkSnapshot(
        val timeToFirstTokenMs: Long?,
        val decodeTokenCount: Int?,
        val decodeTokensPerSecond: Double?,
    )

    companion object {
        const val ACTION = "io.github.ninbyo02.lami.action.LITERT_LM_GPU_BENCHMARK"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_MODEL_PATH = "model_path"
        const val EXTRA_PROMPTS = "prompts"
        const val EXTRA_MAX_OUTPUT_TOKENS_LIST = "max_output_tokens_list"
        const val EXTRA_TIMEOUT_MS = "timeout_ms"
        const val STATE_FILE_NAME = "litert_lm_gpu_benchmark_state.txt"
        const val MARKER_FILE_NAME = "litert_lm_gpu_benchmark_marker.txt"
        private const val DEFAULT_TIMEOUT_MS = 60_000L
        private const val ROUTE_TYPE = "litert_lm_gpu_benchmark"
        private val DEFAULT_PROMPTS = listOf(
            "こんにちは",
            "カレーの材料を箇条書きで教えて",
        )
        private val DEFAULT_MAX_OUTPUT_TOKENS = listOf(32, 64, 128, 256)
        private val FINISH_REASON_METHODS = listOf("getFinishReason", "finishReason", "getDoneReason", "doneReason")
        private val STOP_REASON_METHODS = listOf("getStopReason", "stopReason", "getStop", "stop")
        private val running = AtomicBoolean(false)
        private val receiverDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LiteRtLmGpuBenchmarkReceiver")
        }
    }
}

internal data class LiteRtLmGpuBenchmarkRow(
    val timestamp: String,
    val routeType: String,
    val backend: String,
    val prompt: String,
    val maxOutputTokens: Int,
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
    val fallbackUsed: Boolean,
    val timeout: Boolean,
    val freshCrash: Boolean,
) {
    companion object {
        fun failure(
            timestamp: String,
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
            fallbackUsed: Boolean = false,
            timeout: Boolean,
            freshCrash: Boolean,
        ): LiteRtLmGpuBenchmarkRow =
            LiteRtLmGpuBenchmarkRow(
                timestamp = timestamp,
                routeType = "litert_lm_gpu_benchmark",
                backend = "GPU",
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
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
                fallbackUsed = fallbackUsed,
                timeout = timeout,
                freshCrash = freshCrash,
            )
    }
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
    appendLine("# LiteRT-LM GPU Benchmark")
    appendLine()
    appendLine("- timestamp: `$timestamp`")
    appendLine("- route_type: `litert_lm_gpu_benchmark`")
    appendLine("- backend: `GPU`")
    appendLine("- timeout_ms: `$timeoutMs`")
    appendLine("- fallback_setting_changed: `false`")
    appendLine("- backend_npu_touched: `false`")
    appendLine("- qairt_qnn_touched: `false`")
    appendLine()
    appendLine("| prompt | max_output_tokens | status | reason | engine_create_ms | conversation_create_ms | first_token_ms | ttft_ms | decode_ms | total_ms | output_tokens | tokens_per_second | timeout | fallback_used | fresh_crash |")
    appendLine("| --- | ---: | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |")
    rows.forEach { row ->
        appendLine(
            listOf(
                row.prompt.mdCell(),
                row.maxOutputTokens.toString(),
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
                row.freshCrash.toString(),
            ).joinToString(prefix = "| ", separator = " | ", postfix = " |"),
        )
    }
    appendLine()
    rows.forEachIndexed { index, row ->
        appendLine("## Case ${index + 1}")
        appendLine()
        appendLine("- route_type: `${row.routeType}`")
        appendLine("- backend: `${row.backend}`")
        appendLine("- prompt: `${row.prompt}`")
        appendLine("- max_output_tokens: `${row.maxOutputTokens}`")
        appendLine("- model_path: `${row.modelPath}`")
        appendLine("- model_exists: `${row.modelExists}`")
        appendLine("- model_length: `${row.modelLength}`")
        appendLine("- finish_reason: `${row.finishReason ?: "unknown"}`")
        appendLine("- stop_reason: `${row.stopReason ?: "unknown"}`")
        appendLine("- status: `${row.status}`")
        appendLine("- reason: `${row.reason}`")
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
        "prompt",
        "max_output_tokens",
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
        "fallback_used",
        "timeout",
        "fresh_crash",
    )
    return buildString {
        appendLine(headers.joinToString(",") { csvCell(it) })
        rows.forEach { row ->
            appendLine(
                listOf(
                    row.timestamp,
                    row.routeType,
                    row.backend,
                    row.prompt,
                    row.maxOutputTokens.toString(),
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
                    row.fallbackUsed.toString(),
                    row.timeout.toString(),
                    row.freshCrash.toString(),
                ).joinToString(",") { csvCell(it) },
            )
        }
    }
}

private fun writeState(
    stateFile: File,
    timestamp: String,
    status: String,
    reason: String,
    markdownFileName: String,
    csvFileName: String,
    timeoutMs: Long,
) {
    stateFile.writeText(
        listOf(
            "timestamp=$timestamp",
            "route_type=litert_lm_gpu_benchmark",
            "backend=GPU",
            "status=$status",
            "reason=$reason",
            "markdown_file=$markdownFileName",
            "csv_file=$csvFileName",
            "timeout_ms=$timeoutMs",
            "fallback_setting_changed=false",
            "backend_npu_touched=false",
            "qairt_qnn_touched=false",
        ).joinToString("\n") + "\n",
        Charsets.UTF_8,
    )
}

private fun writeMarker(
    appContext: Context,
    timestamp: String,
    stage: String,
    detail: String,
) {
    runCatching {
        File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.MARKER_FILE_NAME).writeText(
            listOf(
                "timestamp=$timestamp",
                "route_type=litert_lm_gpu_benchmark",
                "backend=GPU",
                "stage=$stage",
                "detail=${detail.replace('\n', ' ').take(500)}",
                "elapsed_realtime_ms=${SystemClock.elapsedRealtime()}",
                "wall_time_ms=${System.currentTimeMillis()}",
            ).joinToString("\n") + "\n",
            Charsets.UTF_8,
        )
    }
}

private fun sanitizeOutput(raw: String): String =
    raw.replace("\u0000", "")
        .trim()

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
