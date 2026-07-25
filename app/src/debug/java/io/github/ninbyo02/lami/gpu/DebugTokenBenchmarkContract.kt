package io.github.ninbyo02.lami.gpu

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences

internal enum class DebugTokenBenchmarkCase(
    val label: String,
    val backend: String,
    val requestedTokens: Int,
    val longContext: Boolean = false,
    val payloadBasisTokens: Int = requestedTokens,
    val payloadRatioPermille: Int = 850,
    val sendApiMode: String = "flow_string",
) {
    GPU_16("GPU 16", "gpu", 16),
    GPU_32("GPU 32", "gpu", 32),
    GPU_128("GPU 128", "gpu", 128),
    GPU_512("GPU 512", "gpu", 512),
    GPU_1024("GPU 1024", "gpu", 1024),
    GPU_2048("GPU 2048", "gpu", 2048),
    GPU_4096("GPU 4096", "gpu", 4096),
    GPU_8192("GPU 8192", "gpu", 8192),
    GPU_16384("GPU 16384", "gpu", 16384),
    GPU_32768("GPU 32768", "gpu", 32768),
    GPU_65536("GPU 65536", "gpu", 65536),
    GPU_131072("GPU 131072", "gpu", 131072),
    GPU_262144("GPU 262144", "gpu", 262144),
    GPU_524288("GPU 524288", "gpu", 524288),
    GPU_1048576("GPU 1048576", "gpu", 1048576),
    GPU_LONG_CONTEXT_2048("GPU long context 2048", "gpu", 2048, true),
    GPU_LONG_CONTEXT_8192("GPU long context 8192", "gpu", 8192, true),
    GPU_LONG_CONTEXT_16384("GPU long context 16384", "gpu", 16384, true),
    GPU_LONG_CONTEXT_22400("GPU long context 22400", "gpu", 22400, true),
    GPU_LONG_CONTEXT_24576("GPU long context 24576", "gpu", 24576, true),
    GPU_LONG_CONTEXT_28800("GPU long context 28800", "gpu", 28800, true),
    GPU_LONG_CONTEXT_30400("GPU long context 30400", "gpu", 30400, true),
    GPU_LONG_CONTEXT_32768("GPU long context 32768", "gpu", 32768, true),
    GPU_LONG_CONTEXT_32769("GPU long context 32769 boundary", "gpu", 32769, true),
    GPU_LONG_30400_R80("GPU long 30400 ratio 80%", "gpu", 30400, true, 30400, 800),
    GPU_LONG_30400_R825("GPU long 30400 ratio 82.5%", "gpu", 30400, true, 30400, 825),
    GPU_LONG_30400_R85("GPU long 30400 ratio 85%", "gpu", 30400, true, 30400, 850),
    GPU_LONG_30400_PAYLOAD_22400("GPU 30400 payload-equivalent 22400", "gpu", 30400, true, 22400, 850),
    GPU_LONG_30400_PAYLOAD_28800("GPU 30400 payload-equivalent 28800", "gpu", 30400, true, 28800, 850),
    GPU_LONG_32768_PAYLOAD_30400_R85("GPU 32768 same bytes as 30400 ratio 85%", "gpu", 32768, true, 30400, 850),
    GPU_LONG_30400_FLOW_COMPARE("GPU 30400 Flow comparison", "gpu", 30400, true, 30400, 850, "flow_string"),
    GPU_LONG_30400_TYPED_CALLBACK_COMPARE("GPU 30400 typed callback comparison", "gpu", 30400, true, 30400, 850, "typed_contents_callback"),
    CPU_32("CPU 32", "cpu", 32),
}

internal data class DebugTokenBenchmarkGateState(
    val gpu32Passed: Boolean = false,
    val gpu128Passed: Boolean = false,
) {
    // Fixed cases are safe independently; the restricted host runner owns run ordering.
    // Activity recreation must never turn an already-authorized case into a silent no-op.
    fun allows(case: DebugTokenBenchmarkCase): Boolean = when (case) {
        DebugTokenBenchmarkCase.GPU_16,
        DebugTokenBenchmarkCase.GPU_32,
        DebugTokenBenchmarkCase.GPU_128,
        DebugTokenBenchmarkCase.GPU_512,
        DebugTokenBenchmarkCase.GPU_1024,
        DebugTokenBenchmarkCase.GPU_2048,
        DebugTokenBenchmarkCase.GPU_4096,
        DebugTokenBenchmarkCase.GPU_8192,
        DebugTokenBenchmarkCase.GPU_16384,
        DebugTokenBenchmarkCase.GPU_32768,
        DebugTokenBenchmarkCase.GPU_65536,
        DebugTokenBenchmarkCase.GPU_131072,
        DebugTokenBenchmarkCase.GPU_262144,
        DebugTokenBenchmarkCase.GPU_524288,
        DebugTokenBenchmarkCase.GPU_1048576,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_2048,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_8192,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_16384,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_22400,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_24576,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_28800,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_30400,
        DebugTokenBenchmarkCase.GPU_LONG_30400_R80,
        DebugTokenBenchmarkCase.GPU_LONG_30400_R825,
        DebugTokenBenchmarkCase.GPU_LONG_30400_R85,
        DebugTokenBenchmarkCase.GPU_LONG_30400_PAYLOAD_22400,
        DebugTokenBenchmarkCase.GPU_LONG_30400_PAYLOAD_28800,
        DebugTokenBenchmarkCase.GPU_LONG_32768_PAYLOAD_30400_R85,
        DebugTokenBenchmarkCase.GPU_LONG_30400_FLOW_COMPARE,
        DebugTokenBenchmarkCase.GPU_LONG_30400_TYPED_CALLBACK_COMPARE,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32768,
        DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32769,
        DebugTokenBenchmarkCase.CPU_32,
        -> true
    }

    fun after(case: DebugTokenBenchmarkCase, passed: Boolean): DebugTokenBenchmarkGateState = when (case) {
        DebugTokenBenchmarkCase.GPU_32 -> copy(gpu32Passed = passed, gpu128Passed = false)
        DebugTokenBenchmarkCase.GPU_128 -> copy(gpu128Passed = gpu32Passed && passed)
        DebugTokenBenchmarkCase.GPU_16, DebugTokenBenchmarkCase.GPU_512, DebugTokenBenchmarkCase.GPU_1024, DebugTokenBenchmarkCase.GPU_2048, DebugTokenBenchmarkCase.GPU_4096, DebugTokenBenchmarkCase.GPU_8192, DebugTokenBenchmarkCase.GPU_16384, DebugTokenBenchmarkCase.GPU_32768, DebugTokenBenchmarkCase.GPU_65536, DebugTokenBenchmarkCase.GPU_131072, DebugTokenBenchmarkCase.GPU_262144, DebugTokenBenchmarkCase.GPU_524288, DebugTokenBenchmarkCase.GPU_1048576, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_2048, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_8192, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_16384, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_22400, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_24576, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_28800, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_30400, DebugTokenBenchmarkCase.GPU_LONG_30400_R80, DebugTokenBenchmarkCase.GPU_LONG_30400_R825, DebugTokenBenchmarkCase.GPU_LONG_30400_R85, DebugTokenBenchmarkCase.GPU_LONG_30400_PAYLOAD_22400, DebugTokenBenchmarkCase.GPU_LONG_30400_PAYLOAD_28800, DebugTokenBenchmarkCase.GPU_LONG_32768_PAYLOAD_30400_R85, DebugTokenBenchmarkCase.GPU_LONG_30400_FLOW_COMPARE, DebugTokenBenchmarkCase.GPU_LONG_30400_TYPED_CALLBACK_COMPARE, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32768, DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32769, DebugTokenBenchmarkCase.CPU_32 -> this
    }
}

internal data class DebugTokenBenchmarkResultEvidence(
    val status: String,
    val requestedTokens: Int,
    val effectiveTokens: Int?,
    val outputTokens: Int?,
    val outputTokenSource: String,
    val tokensPerSecond: Double?,
    val totalMs: Long?,
    val finishReason: String?,
    val timeout: Boolean,
    val fallback: Boolean,
    val freshCrash: Boolean,
    val finishEvidence: Boolean,
) {
    val passed: Boolean
        get() = status == "success" &&
            effectiveTokens == requestedTokens &&
            (outputTokens == null || outputTokens > 0) &&
            finishEvidence && !timeout && !fallback && !freshCrash
}

internal data class DebugTokenBenchmarkUiState(
    val running: Boolean = false,
    val currentCase: DebugTokenBenchmarkCase? = null,
    val timestamp: String? = null,
    val stage: String = "idle",
    val detail: String = "Ready. One fixed case at a time.",
    val elapsedMs: Long = 0,
    val artifactPath: String? = null,
    val statePath: String? = null,
    val evidence: DebugTokenBenchmarkResultEvidence? = null,
    val gates: DebugTokenBenchmarkGateState = DebugTokenBenchmarkGateState(),
)

internal class DebugTokenBenchmarkCoordinator(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(DebugTokenBenchmarkUiState())
    val state: StateFlow<DebugTokenBenchmarkUiState> = mutableState.asStateFlow()

    fun start(case: DebugTokenBenchmarkCase): Boolean {
        if (!mutableState.value.gates.allows(case)) {
            mutableState.value = mutableState.value.copy(stage = "blocked", detail = "safety_gate_blocked")
            return false
        }
        if (!running.compareAndSet(false, true)) {
            mutableState.value = mutableState.value.copy(detail = "already_running")
            return false
        }
        cancelled.set(false)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val started = SystemClock.elapsedRealtime()
        val deadlineElapsedRealtime = started + CASE_DEADLINE_MS
        mutableState.value = mutableState.value.copy(
            running = true,
            currentCase = case,
            timestamp = timestamp,
            stage = "ui_foreground_start",
            detail = "fixed total-context / generic_fallback / send-message / normal",
            elapsedMs = 0,
            artifactPath = null,
            statePath = File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.STATE_FILE_NAME).absolutePath,
            evidence = null,
        )
        writeUiMarker(timestamp, case, "ui_foreground_start", "foreground_activity_internal_receiver_no_service")
        writeEnvironmentArtifact(timestamp, case)
        scope.launch {
            try {
                dispatch(case, timestamp, deadlineElapsedRealtime)
                awaitTerminal(case, timestamp, started, deadlineElapsedRealtime)
            } finally {
                running.set(false)
                mutableState.value = mutableState.value.copy(running = false)
            }
        }
        return true
    }

    fun cancel(reason: String = "ui_stop_or_screen_left") {
        cancelled.set(true)
        mutableState.value.timestamp?.let { timestamp ->
            File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.CANCEL_RELAY_FILE_NAME)
                .writeText(timestamp, Charsets.UTF_8)
            val cancelIntent = Intent(LiteRtLmGpuBenchmarkReceiver.ACTION).apply {
                component = ComponentName(appContext, LiteRtLmGpuBenchmarkReceiver::class.java)
                setPackage(appContext.packageName)
                putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_COMMAND_CANCEL, true)
                putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_TIMESTAMP, timestamp)
            }
            appContext.sendBroadcast(cancelIntent)
            mutableState.value.currentCase?.let { case -> writeUiMarker(timestamp, case, "ui_cancel_requested", reason) }
        }
        mutableState.value = mutableState.value.copy(stage = "cancelling", detail = reason)
    }

    fun recordLifecycle(stage: String) {
        val snapshot = mutableState.value
        val timestamp = snapshot.timestamp ?: return
        val case = snapshot.currentCase ?: return
        writeUiMarker(timestamp, case, "activity_lifecycle", stage)
    }

    private suspend fun dispatch(
        case: DebugTokenBenchmarkCase,
        timestamp: String,
        deadlineElapsedRealtime: Long,
    ) {
        val genericModelPath = withContext(Dispatchers.IO) {
            SettingsPreferences(appContext).getValidLocalGenericModelPathOrNull()
        }
        File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.GENERIC_MODEL_PATH_RELAY_FILE_NAME)
            .writeText(genericModelPath.orEmpty(), Charsets.UTF_8)
        val remainingBudgetMs = remainingRunBudgetMs(deadlineElapsedRealtime)
        if (remainingBudgetMs < MIN_RECEIVER_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                publishPreDispatchTimeout(case, timestamp, remainingBudgetMs)
            }
            return
        }
        val intent = Intent(LiteRtLmGpuBenchmarkReceiver.ACTION).apply {
            component = ComponentName(appContext, LiteRtLmGpuBenchmarkReceiver::class.java)
            setPackage(appContext.packageName)
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_TIMESTAMP, timestamp)
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_SINGLE_PROMPT, promptFor(case))
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_MAX_OUTPUT_TOKENS_LIST, case.requestedTokens.toString())
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_BACKEND_VARIANT, case.backend)
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_CLOSE_POLICY, "normal")
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_PHASE, "send-message")
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_MODEL_PATH_SOURCE, "generic_fallback")
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_SEND_API_MODE, case.sendApiMode)
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_TIMEOUT_MS, remainingBudgetMs)
            putExtra(LiteRtLmGpuBenchmarkReceiver.EXTRA_RUN_DEADLINE_ELAPSED_REALTIME, deadlineElapsedRealtime)
        }
        writeUiMarker(timestamp, case, "ui_internal_dispatch", "explicit_non_exported_receiver process_already_foreground")
        appContext.sendBroadcast(intent)
    }

    private suspend fun awaitTerminal(
        case: DebugTokenBenchmarkCase,
        timestamp: String,
        started: Long,
        deadlineElapsedRealtime: Long,
    ) {
        val stateFile = File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.STATE_FILE_NAME)
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - started
            val stateText = withContext(Dispatchers.IO) { runCatching { stateFile.readText() }.getOrDefault("") }
            val status = value(stateText, "status")
            mutableState.value = mutableState.value.copy(
                elapsedMs = elapsed,
                stage = value(stateText, "reason") ?: mutableState.value.stage,
                detail = if (cancelled.get()) "cancel_requested" else stateText.take(800),
            )
            if (value(stateText, "timestamp") == timestamp && status in TERMINAL_STATUS) {
                val csvName = value(stateText, "csv_file")
                val markdownName = value(stateText, "markdown_file")
                val expectedCsvName = "litert_lm_gpu_benchmark_$timestamp.csv"
                val expectedMarkdownName = "litert_lm_gpu_benchmark_$timestamp.md"
                val csvFile = csvName?.takeIf { it == expectedCsvName }?.let { File(appContext.filesDir, it) }
                val markdownFile = markdownName?.takeIf { it == expectedMarkdownName }?.let { File(appContext.filesDir, it) }
                val evidence = parseEvidence(case, timestamp, csvFile, markdownFile)
                val gates = mutableState.value.gates.after(case, evidence.passed)
                writeUiMarker(timestamp, case, "ui_terminal_readback", "passed=${evidence.passed} output_tokens=${evidence.outputTokens}")
                mutableState.value = mutableState.value.copy(
                    stage = if (evidence.passed) "pass" else "fail_closed",
                    detail = stateText.take(1200),
                    artifactPath = csvFile?.absolutePath,
                    evidence = evidence,
                    gates = gates,
                )
                return
            }
            val remainingBeforePollMs = remainingRunBudgetMs(deadlineElapsedRealtime)
            if (remainingBeforePollMs <= 0L) break
            delay(minOf(250L, remainingBeforePollMs))
        }
        mutableState.value = mutableState.value.copy(
            stage = "host_observation_timeout",
            detail = "fail_closed: no timestamp-matched terminal state",
            evidence = DebugTokenBenchmarkResultEvidence(
                status = "failure", requestedTokens = case.requestedTokens, effectiveTokens = null,
                outputTokens = null, outputTokenSource = "missing", tokensPerSecond = null, totalMs = null,
                finishReason = null, timeout = true, fallback = false, freshCrash = false, finishEvidence = false,
            ),
        )
    }

    private fun parseEvidence(
        case: DebugTokenBenchmarkCase,
        timestamp: String,
        csvFile: File?,
        markdownFile: File?,
    ): DebugTokenBenchmarkResultEvidence {
        val records = runCatching {
            csvFile?.readText()?.let(DebugTokenBenchmarkCsvParser::records).orEmpty()
        }.getOrDefault(emptyList())
        val headers = records.firstOrNull()?.let(DebugTokenBenchmarkCsvParser::cells).orEmpty()
        val dataLines = records.drop(1).filter { it.isNotBlank() }
        val values = dataLines.singleOrNull()?.let(DebugTokenBenchmarkCsvParser::cells).orEmpty()
        val row = headers.zip(values).toMap()
        val outputTokens = row["output_tokens"]?.toIntOrNull()
        val outputTokenSource = row["output_token_source"].orEmpty()
        val reason = row["reason"]
        val hasGeneratedOutput = !row["sanitized_output"].isNullOrBlank() || !row["raw_output"].isNullOrBlank()
        val markdownText = runCatching { markdownFile?.readText().orEmpty() }.getOrDefault("")
        val artifactTimestampMatched = row["timestamp"] == timestamp &&
            markdownText.lineSequence().any { it == "- timestamp: `$timestamp`" }
        val outputTokenProvenanceMatched = when {
            outputTokens != null -> outputTokenSource == "LiteRT benchmarkInfo.lastDecodeTokenCount"
            row["output_tokens"] == "unavailable" -> outputTokenSource == "unavailable"
            else -> false
        }
        return DebugTokenBenchmarkResultEvidence(
            status = row["status"].orEmpty(),
            requestedTokens = case.requestedTokens,
            effectiveTokens = row["max_output_tokens"]?.toIntOrNull(),
            outputTokens = outputTokens,
            outputTokenSource = outputTokenSource.ifBlank { "missing" },
            tokensPerSecond = row["tokens_per_second"]?.toDoubleOrNull(),
            totalMs = row["total_ms"]?.toLongOrNull(),
            finishReason = row["finish_reason"]?.ifBlank { row["stop_reason"] },
            timeout = row["timeout"].toBoolean(),
            fallback = row["fallback_used"].toBoolean(),
            freshCrash = row["fresh_crash"].toBoolean(),
            finishEvidence = dataLines.size == 1 &&
                reason == "completed" &&
                hasGeneratedOutput &&
                artifactTimestampMatched &&
                outputTokenProvenanceMatched &&
                csvFile?.isFile == true && csvFile.length() > 0L &&
                markdownFile?.isFile == true && markdownFile.length() > 0L,
        )
    }

    private fun csvCells(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> { cell.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { cells += cell.toString(); cell.clear() }
                else -> cell.append(char)
            }
            index++
        }
        cells += cell.toString()
        return cells
    }

    private fun value(text: String, key: String): String? = text.lineSequence()
        .firstOrNull { it.startsWith("$key=") }?.substringAfter('=')

    private fun remainingRunBudgetMs(deadlineElapsedRealtime: Long): Long =
        (deadlineElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    private fun promptFor(case: DebugTokenBenchmarkCase): String =
        if (case.longContext) LongContext.fixedPrompt(case) else TOTAL_CONTEXT_SEQUENCE_PROMPT

    private object LongContext {
        fun boundary(case: DebugTokenBenchmarkCase): String =
            if (case.requestedTokens > 32_768) {
                "rejected_over_runtime_90pct"
            } else {
                "accepted_under_runtime_90pct"
            }

        fun fixedPrompt(case: DebugTokenBenchmarkCase): String {
            val ratioPermille = if (case == DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32769) 950 else case.payloadRatioPermille
            val repetitions = (case.payloadBasisTokens * ratioPermille) / 1000
            return buildString(repetitions * 2 + 64) {
                repeat(repetitions) { append(" x") }
                append("\nReply with only the decimal digit 5.")
            }
        }
    }

    private fun writeUiMarker(timestamp: String, case: DebugTokenBenchmarkCase, stage: String, detail: String) {
        val text = "timestamp=$timestamp\nroute_type=litert_lm_gpu_benchmark\ntransport=foreground_ui_internal\nstage=$stage\ncase=${case.name}\ntotal_context_tokens=${case.requestedTokens}\npayload_basis_tokens=${case.payloadBasisTokens}\npayload_ratio_permille=${case.payloadRatioPermille}\nsend_api_mode=${case.sendApiMode}\ncontext_boundary=${if (case.longContext) LongContext.boundary(case) else "short_prompt"}\nactual_input_tokens=${if (case.longContext) "pending_benchmark_info" else "not_applicable"}\nprompt_utf8_bytes=${promptFor(case).toByteArray(Charsets.UTF_8).size}\ndetail=$detail\n\n"
        runCatching { File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.MARKER_HISTORY_FILE_NAME).appendText(text) }
    }

    private fun writeEnvironmentArtifact(timestamp: String, case: DebugTokenBenchmarkCase) {
        val runtime = Runtime.getRuntime()
        val thermal = runCatching { appContext.getSystemService(android.os.PowerManager::class.java).currentThermalStatus }.getOrNull()
        File(appContext.filesDir, "litert_lm_gpu_benchmark_environment_$timestamp.txt").writeText(
            "timestamp=$timestamp\ncase=${case.name}\nheap_free_bytes=${runtime.freeMemory()}\nheap_total_bytes=${runtime.totalMemory()}\nheap_max_bytes=${runtime.maxMemory()}\nthermal_status=${thermal ?: "unavailable"}\n",
        )
    }

    private fun publishPreDispatchTimeout(
        case: DebugTokenBenchmarkCase,
        timestamp: String,
        remainingBudgetMs: Long,
    ) {
        val reason = "run_deadline_expired_before_receiver_launch"
        val csvName = "litert_lm_gpu_benchmark_$timestamp.csv"
        val markdownName = "litert_lm_gpu_benchmark_$timestamp.md"
        writeUtf8Atomically(
            File(appContext.filesDir, csvName),
            "timestamp,status,reason,timeout,max_output_tokens,fallback_used,fresh_crash,sanitized_output,raw_output\n" +
                "$timestamp,failure,$reason,true,${case.requestedTokens},false,false,,\n",
        )
        writeUtf8Atomically(
            File(appContext.filesDir, markdownName),
            "# LiteRT-LM GPU benchmark\n\n" +
                "- timestamp: `$timestamp`\n- status: `failure`\n- reason: `$reason`\n" +
                "- remaining_budget_ms: `$remainingBudgetMs`\n",
        )
        writeUtf8Atomically(
            File(appContext.filesDir, LiteRtLmGpuBenchmarkReceiver.STATE_FILE_NAME),
            "timestamp=$timestamp\nstatus=failure\nreason=$reason\n" +
                "markdown_file=$markdownName\ncsv_file=$csvName\ntimeout_ms=$remainingBudgetMs\n" +
                "requested_run_count=1\ncompleted_run_count=1\nsuccess_count=0\n" +
                "failure_count=0\ntimeout_count=1\nfallback_count=0\n",
        )
        writeUiMarker(timestamp, case, "pre_dispatch_timeout_published", "remaining_budget_ms=$remainingBudgetMs")
    }

    companion object {
        private const val CASE_DEADLINE_MS = 600_000L
        private const val MIN_RECEIVER_TIMEOUT_MS = 1_000L
        private val TERMINAL_STATUS = setOf("success", "partial", "failure", "blocked", "timeout", "cancelled", "skipped")
        private const val TOTAL_CONTEXT_SEQUENCE_PROMPT = "Continue: 1 2 3 4"
    }
}

internal object DebugTokenBenchmarkCsvParser {
    fun records(text: String): List<String> {
        val records = mutableListOf<String>()
        val record = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '"') {
                record.append(char)
                if (quoted && index + 1 < text.length && text[index + 1] == '"') {
                    record.append('"')
                    index++
                } else {
                    quoted = !quoted
                }
            } else if ((char == '\n' || char == '\r') && !quoted) {
                records += record.toString()
                record.clear()
                if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
            } else {
                record.append(char)
            }
            index++
        }
        if (record.isNotEmpty()) records += record.toString()
        return records
    }

    fun cells(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells += cell.toString()
                    cell.clear()
                }
                else -> cell.append(char)
            }
            index++
        }
        cells += cell.toString()
        return cells
    }
}
