package io.github.ninbyo02.lami.npu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteMode
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRoutePreferences
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1Bridge
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1Contract
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1Result
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS2DbBridge
import io.github.ninbyo02.lami.ui.screens.home.buildNpuStandardRouteS2DbSavedResult
import io.github.ninbyo02.lami.ui.screens.home.buildNpuStandardRouteS2DbSkippedResult
import io.github.ninbyo02.lami.ui.screens.home.hasNpuStandardRouteRawRoleContamination
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class NpuS2DbStabilityTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP)
            ?.takeIf { it.isNotBlank() }
            ?: timestamp()
        if (!running.compareAndSet(false, true)) {
            writeState(
                stateFile = File(appContext.filesDir, STATE_FILE_NAME),
                timestamp = timestamp,
                status = "failure",
                reason = "already_running",
                markdownFileName = "",
                csvFileName = "",
                promptIndex = intent.getIntExtra(EXTRA_PROMPT_INDEX, 0),
                promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
                timeoutMs = timeoutMs(intent),
            )
            return
        }
        val pendingResult = goAsync()
        receiverDispatcher.execute {
            var releaseRunning = true
            try {
                releaseRunning = handle(appContext, intent, timestamp)
            } catch (throwable: Throwable) {
                writeState(
                    stateFile = File(appContext.filesDir, STATE_FILE_NAME),
                    timestamp = timestamp,
                    status = "failure",
                    reason = throwable.message?.takeIf { it.isNotBlank() }
                        ?: "receiver_exception:${throwable.javaClass.simpleName}",
                    markdownFileName = "",
                    csvFileName = "",
                    promptIndex = intent.getIntExtra(EXTRA_PROMPT_INDEX, 0),
                    promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
                    timeoutMs = timeoutMs(intent),
                )
            } finally {
                if (releaseRunning) {
                    running.set(false)
                }
                pendingResult.finish()
            }
        }
    }

    private fun handle(appContext: Context, intent: Intent, timestamp: String): Boolean {
        val maxOutputTokens = NpuStandardRoutePreferences.sanitizeMaxOutputTokens(
            intent.getIntExtra(
                EXTRA_MAX_OUTPUT_TOKENS,
                NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            ),
        )
        val timeoutMs = timeoutMs(intent)
        val promptIndex = intent.getIntExtra(EXTRA_PROMPT_INDEX, 0)
        val stateFile = File(appContext.filesDir, STATE_FILE_NAME)
        if (!BuildConfig.DEBUG || BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            writeState(
                stateFile = stateFile,
                timestamp = timestamp,
                status = "blocked",
                reason = "wrong_variant",
                markdownFileName = "",
                csvFileName = "",
                promptIndex = promptIndex,
                promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
                timeoutMs = timeoutMs,
            )
            return true
        }
        if (promptIndex !in NpuS2DbStabilityReportFormatter.prompts.indices) {
            writeState(
                stateFile = stateFile,
                timestamp = timestamp,
                status = "blocked",
                reason = "invalid_prompt_index",
                markdownFileName = "",
                csvFileName = "",
                promptIndex = promptIndex,
                promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
                timeoutMs = timeoutMs,
            )
            return true
        }

        val request = NpuS2DbStabilityRunRequest(
            maxOutputTokens = maxOutputTokens,
            promptIndex = promptIndex,
            prompt = NpuS2DbStabilityReportFormatter.prompts[promptIndex],
            timeoutMs = timeoutMs,
        )
        writeState(
            stateFile = stateFile,
            timestamp = timestamp,
            status = "running",
            reason = "prompt_running",
            markdownFileName = "",
            csvFileName = "",
            promptIndex = promptIndex,
            promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
            timeoutMs = timeoutMs,
        )
        val promptRun = runPromptWithTimeout(request)
        val rows = listOf(promptRun.row)

        val promptNumber = promptIndex + 1
        val markdownFileName = "npu_s2_db_stability_${timestamp}_prompt_$promptNumber.md"
        val csvFileName = "npu_s2_db_stability_${timestamp}_prompt_$promptNumber.csv"
        File(appContext.filesDir, markdownFileName).writeText(
            NpuS2DbStabilityReportFormatter.toMarkdown(
                timestamp = timestamp,
                maxOutputTokens = maxOutputTokens,
                rows = rows,
                promptIndex = promptIndex,
                timeoutMs = timeoutMs,
            ),
        )
        File(appContext.filesDir, csvFileName).writeText(
            NpuS2DbStabilityReportFormatter.toCsv(rows),
        )
        val safeSuccess = promptRun.row.isSafeSuccessForNextPrompt()
        val status = if (safeSuccess) "success" else "failure"
        val reason = when {
            promptRun.timedOut -> "prompt_timeout"
            safeSuccess -> "completed"
            else -> "unsafe_prompt_result"
        }
        writeState(
            stateFile = stateFile,
            timestamp = timestamp,
            status = status,
            reason = reason,
            markdownFileName = markdownFileName,
            csvFileName = csvFileName,
            promptIndex = promptIndex,
            promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
            timeoutMs = timeoutMs,
        )
        return !promptRun.timedOut
    }

    private fun runPromptWithTimeout(
        request: NpuS2DbStabilityRunRequest,
    ): NpuS2DbStabilityPromptRun {
        val decodeExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "NpuS2DbStabilityDecode-${request.promptIndex + 1}")
        }
        val future = decodeExecutor.submit(
            Callable {
                runPrompt(request)
            },
        )
        return try {
            NpuS2DbStabilityPromptRun(
                row = future.get(request.timeoutMs, TimeUnit.MILLISECONDS),
                timedOut = false,
            )
        } catch (_: TimeoutException) {
            future.cancel(true)
            NpuS2DbStabilityPromptRun(
                row = NpuS2DbStabilityReportRow.failure(
                    number = request.promptIndex + 1,
                    prompt = request.prompt,
                    reason = "prompt_timeout_${request.timeoutMs}ms",
                    timeout = true,
                    notes = "automation_scope=s2_decoding_and_save_decision_logic; ui_db_integration=false; timeout_ms=${request.timeoutMs}",
                ),
                timedOut = true,
            )
        } catch (throwable: Throwable) {
            NpuS2DbStabilityPromptRun(
                row = NpuS2DbStabilityReportRow.failure(
                    number = request.promptIndex + 1,
                    prompt = request.prompt,
                    reason = throwable.message?.takeIf { it.isNotBlank() }
                        ?: "runner_exception:${throwable.javaClass.simpleName}",
                ),
                timedOut = false,
            )
        } finally {
            decodeExecutor.shutdownNow()
        }
    }

    private fun runPrompt(request: NpuS2DbStabilityRunRequest): NpuS2DbStabilityReportRow {
        val bridge = NpuStandardRouteS1Bridge(mode = NpuStandardRouteMode.S2_DB)
        val s2Bridge = NpuStandardRouteS2DbBridge()
        val s1Result = bridge.run(
            userPrompt = request.prompt,
            maxOutputTokens = request.maxOutputTokens,
        )
        val mapping = s2Bridge.prepareSaveCandidate(
            userPrompt = request.prompt,
            s1Result = s1Result,
        )
        val s2Result = if (mapping.hasSaveCandidate) {
            buildNpuStandardRouteS2DbSavedResult(s1Result)
        } else {
            buildNpuStandardRouteS2DbSkippedResult(
                s1Result = s1Result,
                failureReason = mapping.failureReason,
            )
        }
        return NpuS2DbStabilityReportRow.fromResult(
            number = request.promptIndex + 1,
            prompt = request.prompt,
            result = s2Result,
            saveCandidateReady = mapping.hasSaveCandidate,
            s2DbReason = mapping.failureReason ?: s2Result.s2DbReason,
        )
    }

    private fun writeState(
        stateFile: File,
        timestamp: String,
        status: String,
        reason: String,
        markdownFileName: String,
        csvFileName: String,
        promptIndex: Int,
        promptCount: Int,
        timeoutMs: Long,
    ) {
        stateFile.writeText(
            listOf(
                "receiver=npu_s2_db_stability_test",
                "status=$status",
                "reason=$reason",
                "automation_scope=s2_decoding_and_save_decision_logic",
                "ui_db_integration=false",
                "route_mode=S2_DB",
                "timestamp=$timestamp",
                "prompt_index=$promptIndex",
                "prompt_number=${promptIndex + 1}",
                "prompt_count=$promptCount",
                "prompt_timeout_ms=$timeoutMs",
                "markdown_file=$markdownFileName",
                "csv_file=$csvFileName",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    companion object {
        const val ACTION = "io.github.ninbyo02.lami.action.NPU_S2_DB_STABILITY_TEST"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_MAX_OUTPUT_TOKENS = "max_output_tokens"
        const val EXTRA_PROMPT_INDEX = "prompt_index"
        const val EXTRA_PROMPT_TIMEOUT_MS = "prompt_timeout_ms"
        const val STATE_FILE_NAME = "npu_s2_db_stability_state.txt"
        private const val DEFAULT_PROMPT_TIMEOUT_MS = 180_000L
        private const val MAX_PROMPT_TIMEOUT_MS = 900_000L
        private val running = AtomicBoolean(false)
        private val receiverDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "NpuS2DbStabilityReceiver")
        }

        private fun timeoutMs(intent: Intent): Long {
            val requested = intent.getIntExtra(
                EXTRA_PROMPT_TIMEOUT_MS,
                DEFAULT_PROMPT_TIMEOUT_MS.toInt(),
            ).toLong()
            return requested.coerceIn(1_000L, MAX_PROMPT_TIMEOUT_MS)
        }
    }
}

private data class NpuS2DbStabilityRunRequest(
    val maxOutputTokens: Int,
    val promptIndex: Int,
    val prompt: String,
    val timeoutMs: Long,
)

private data class NpuS2DbStabilityPromptRun(
    val row: NpuS2DbStabilityReportRow,
    val timedOut: Boolean,
)

internal data class NpuS2DbStabilityReportRow(
    val number: Int,
    val prompt: String,
    val status: String,
    val reason: String,
    val qualityClassification: String,
    val sanitizedOutput: String,
    val rawRoleContamination: Boolean,
    val db: Boolean,
    val conversationHistorySaved: Boolean,
    val runDecodeReached: Boolean,
    val fallbackUsed: Boolean,
    val timeout: Boolean,
    val freshCrash: Boolean,
    val npuS1DecodeMs: String,
    val npuS1TokensPerSecond: String,
    val judgement: String,
    val notes: String,
) {
    companion object {
        fun fromResult(
            number: Int,
            prompt: String,
            result: NpuStandardRouteS1Result,
            saveCandidateReady: Boolean,
            s2DbReason: String,
        ): NpuS2DbStabilityReportRow {
            val db = result.selection.sideEffects.db
            val conversationHistorySaved = result.selection.sideEffects.conversationHistorySaved
            val rawRoleContamination = hasNpuStandardRouteRawRoleContamination(result.rawOutput)
            val judgement = when {
                db && conversationHistorySaved && saveCandidateReady -> "pass_saved"
                rawRoleContamination && !db && !conversationHistorySaved -> "pass_blocked_raw_role_contamination"
                !db && !conversationHistorySaved -> "blocked"
                else -> "fail"
            }
            val notes = buildList {
                add("automation_scope=s2_decoding_and_save_decision_logic")
                add("ui_db_integration=false")
                if (s2DbReason.isNotBlank()) {
                    add("s2_db_reason=$s2DbReason")
                }
            }.joinToString("; ")
            return NpuS2DbStabilityReportRow(
                number = number,
                prompt = prompt,
                status = result.status,
                reason = result.reason,
                qualityClassification = result.qualityClassification,
                sanitizedOutput = result.sanitizedOutput,
                rawRoleContamination = rawRoleContamination,
                db = db,
                conversationHistorySaved = conversationHistorySaved,
                runDecodeReached = result.runDecodeReached,
                fallbackUsed = result.fallbackUsed,
                timeout = result.timeout,
                freshCrash = result.freshCrash,
                npuS1DecodeMs = NpuStandardRouteS1Contract.formatTimingMs(result.timing.decodeMs),
                npuS1TokensPerSecond = NpuStandardRouteS1Contract.formatTokensPerSecond(
                    result.timing.tokensPerSecond,
                ),
                judgement = judgement,
                notes = notes,
            )
        }

        fun failure(
            number: Int,
            prompt: String,
            reason: String,
            timeout: Boolean = false,
            freshCrash: Boolean = false,
            notes: String = "automation_scope=s2_decoding_and_save_decision_logic; ui_db_integration=false",
        ): NpuS2DbStabilityReportRow =
            NpuS2DbStabilityReportRow(
                number = number,
                prompt = prompt,
                status = "failure",
                reason = reason,
                qualityClassification = "unknown",
                sanitizedOutput = "",
                rawRoleContamination = false,
                db = false,
                conversationHistorySaved = false,
                runDecodeReached = false,
                fallbackUsed = false,
                timeout = timeout,
                freshCrash = freshCrash,
                npuS1DecodeMs = "n/a",
                npuS1TokensPerSecond = "n/a",
                judgement = "fail",
                notes = notes,
            )
    }
}

private fun NpuS2DbStabilityReportRow.isSafeSuccessForNextPrompt(): Boolean =
    status == "success" &&
        runDecodeReached &&
        !fallbackUsed &&
        !timeout &&
        !freshCrash &&
        judgement != "fail"

internal object NpuS2DbStabilityReportFormatter {
    val prompts: List<String> = listOf(
        "こんにちは",
        "ああああ",
        "明日の天気は",
        "Pythonについて一言で教えて",
        "1+1は？",
        "自己紹介して",
        "日本語で短く返答してください",
        "箇条書きで3つ教えて",
        "今日の予定を確認したい",
        "ありがとう",
    )

    private val headers = listOf(
        "No",
        "prompt",
        "status",
        "reason",
        "quality_classification",
        "sanitized_output",
        "raw_role_contamination",
        "db",
        "conversation_history_saved",
        "run_decode_reached",
        "fallback_used",
        "timeout",
        "fresh_crash",
        "npu_s1_decode_ms",
        "npu_s1_tokens_per_second",
        "judgement",
        "notes",
    )

    fun toMarkdown(
        timestamp: String,
        maxOutputTokens: Int,
        rows: List<NpuS2DbStabilityReportRow>,
        promptIndex: Int? = null,
        timeoutMs: Long? = null,
    ): String = buildString {
        appendLine("# NPU S2_DB Stability Test")
        appendLine()
        appendLine("- timestamp: `$timestamp`")
        appendLine("- model_condition: `Qualcomm/sm8750 LiteRT-LM`")
        appendLine("- route_mode: `S2_DB`")
        appendLine("- max_output_tokens: `$maxOutputTokens`")
        if (promptIndex != null) {
            appendLine("- execution_mode: `single_prompt`")
            appendLine("- prompt_index: `$promptIndex`")
            appendLine("- prompt_number: `${promptIndex + 1}`")
        }
        if (timeoutMs != null) {
            appendLine("- prompt_timeout_ms: `$timeoutMs`")
        }
        appendLine("- fallback_required: `false`")
        appendLine("- automation_scope: `s2_decoding_and_save_decision_logic`")
        appendLine("- ui_db_integration: `false`")
        appendLine()
        appendLine("| ${headers.joinToString(" | ")} |")
        appendLine("| ${headers.joinToString(" | ") { "---" }} |")
        rows.forEach { row ->
            appendLine("| ${row.toCells().joinToString(" | ") { markdownCell(it) }} |")
        }
        appendLine()
        appendLine("## S3 Gate")
        appendLine()
        appendLine("- Normal prompts should save with `db=true` and `conversation_history_saved=true`.")
        appendLine("- Raw role contamination must remain blocked with `db=false`.")
        appendLine("- All rows must keep `fallback_used=false`, `fresh_crash=false`, `timeout=false`, and `run_decode_reached=true` for NPU stability evidence.")
        appendLine("- DB-saved responses must not contain role contamination.")
        appendLine("- This runner does not verify real ChatScreen DB insertion or duplicate history rows.")
    }

    fun toCsv(rows: List<NpuS2DbStabilityReportRow>): String = buildString {
        appendLine(headers.joinToString(",") { csvCell(it) })
        rows.forEach { row ->
            appendLine(row.toCells().joinToString(",") { csvCell(it) })
        }
    }

    private fun NpuS2DbStabilityReportRow.toCells(): List<String> = listOf(
        number.toString(),
        prompt,
        status,
        reason,
        qualityClassification,
        sanitizedOutput,
        rawRoleContamination.toString(),
        db.toString(),
        conversationHistorySaved.toString(),
        runDecodeReached.toString(),
        fallbackUsed.toString(),
        timeout.toString(),
        freshCrash.toString(),
        npuS1DecodeMs,
        npuS1TokensPerSecond,
        judgement,
        notes,
    )

    private fun markdownCell(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\n", "<br>")
            .replace("|", "\\|")
            .ifBlank { " " }

    private fun csvCell(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""
}
