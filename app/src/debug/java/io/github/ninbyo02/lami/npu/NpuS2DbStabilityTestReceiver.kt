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
            val promptNo = promptNo(intent)
            writeState(
                stateFile = File(appContext.filesDir, STATE_FILE_NAME),
                timestamp = timestamp,
                status = "failure",
                reason = "already_running",
                markdownFileName = "",
                csvFileName = "",
                promptNo = promptNo,
                promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
                timeoutMs = timeoutMs(intent),
                promptText = promptTextOrBlank(promptNo),
                judgement = "fail",
                notes = "automation_scope=s2_decoding_and_save_decision_logic; ui_db_integration=false",
            )
            return
        }
        val pendingResult = goAsync()
        receiverDispatcher.execute {
            var releaseRunning = true
            try {
                releaseRunning = handle(appContext, intent, timestamp)
            } catch (throwable: Throwable) {
                val promptNo = promptNo(intent)
                writeState(
                    stateFile = File(appContext.filesDir, STATE_FILE_NAME),
                    timestamp = timestamp,
                    status = "failure",
                    reason = throwable.message?.takeIf { it.isNotBlank() }
                        ?: "receiver_exception:${throwable.javaClass.simpleName}",
                    markdownFileName = "",
                    csvFileName = "",
                    promptNo = promptNo,
                    promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
                    timeoutMs = timeoutMs(intent),
                    promptText = promptTextOrBlank(promptNo),
                    judgement = "fail",
                    notes = "automation_scope=s2_decoding_and_save_decision_logic; ui_db_integration=false",
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
        val promptNo = promptNo(intent)
        val stateFile = File(appContext.filesDir, STATE_FILE_NAME)
        if (!BuildConfig.DEBUG || BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            writeState(
                stateFile = stateFile,
                timestamp = timestamp,
                status = "blocked",
                reason = "wrong_variant",
                markdownFileName = "",
                csvFileName = "",
                promptNo = promptNo,
                promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
                timeoutMs = timeoutMs,
                promptText = promptTextOrBlank(promptNo),
                judgement = "fail",
                notes = "automation_scope=s2_decoding_and_save_decision_logic; ui_db_integration=false",
            )
            return true
        }
        if (promptNo !in 1..NpuS2DbStabilityReportFormatter.prompts.size) {
            writeState(
                stateFile = stateFile,
                timestamp = timestamp,
                status = "blocked",
                reason = "invalid_prompt_index",
                markdownFileName = "",
                csvFileName = "",
                promptNo = promptNo,
                promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
                timeoutMs = timeoutMs,
                promptText = "",
                judgement = "fail",
                notes = "automation_scope=s2_decoding_and_save_decision_logic; ui_db_integration=false",
            )
            return true
        }
        val promptIndex = promptNo - 1
        val promptText = NpuS2DbStabilityReportFormatter.prompts[promptIndex]

        val request = NpuS2DbStabilityRunRequest(
            maxOutputTokens = maxOutputTokens,
            promptNo = promptNo,
            prompt = promptText,
            timeoutMs = timeoutMs,
        )
        writeState(
            stateFile = stateFile,
            timestamp = timestamp,
            status = "running",
            reason = "prompt_running",
            markdownFileName = "",
            csvFileName = "",
            promptNo = promptNo,
            promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
            timeoutMs = timeoutMs,
            promptText = promptText,
            judgement = "running",
            notes = "automation_scope=s2_decoding_and_save_decision_logic; ui_db_integration=false",
        )
        val promptRun = runPromptWithTimeout(request)
        val rows = listOf(promptRun.row)

        val markdownFileName = "npu_s2_db_stability_${timestamp}_prompt_$promptNo.md"
        val csvFileName = "npu_s2_db_stability_${timestamp}_prompt_$promptNo.csv"
        File(appContext.filesDir, markdownFileName).writeText(
            NpuS2DbStabilityReportFormatter.toMarkdown(
                timestamp = timestamp,
                maxOutputTokens = maxOutputTokens,
                rows = rows,
                promptIndex = promptNo,
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
            promptNo = promptNo,
            promptCount = NpuS2DbStabilityReportFormatter.prompts.size,
            timeoutMs = timeoutMs,
            promptText = promptRun.row.prompt,
            judgement = promptRun.row.judgement,
            notes = promptRun.row.notes,
        )
        return !promptRun.timedOut
    }

    private fun runPromptWithTimeout(
        request: NpuS2DbStabilityRunRequest,
    ): NpuS2DbStabilityPromptRun {
        val decodeExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "NpuS2DbStabilityDecode-${request.promptNo}")
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
                    number = request.promptNo,
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
                    number = request.promptNo,
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
        val rawS1Result = bridge.run(
            userPrompt = request.prompt,
            maxOutputTokens = request.maxOutputTokens,
        )
        val s1Result = normalizeNpuS2DbStabilityResult(rawS1Result)
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
            number = request.promptNo,
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
        promptNo: Int,
        promptCount: Int,
        timeoutMs: Long,
        promptText: String,
        judgement: String,
        notes: String,
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
                "prompt_index=$promptNo",
                "prompt_no=$promptNo",
                "prompt_number=$promptNo",
                "prompt_count=$promptCount",
                "prompt_text=${escapeStateValue(promptText)}",
                "prompt_timeout_ms=$timeoutMs",
                "judgement=${escapeStateValue(judgement)}",
                "notes=${escapeStateValue(notes)}",
                "markdown_file=$markdownFileName",
                "csv_file=$csvFileName",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun escapeStateValue(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n")

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

        private fun promptNo(intent: Intent): Int =
            intent.getIntExtra(EXTRA_PROMPT_INDEX, 1)

        private fun promptTextOrBlank(promptNo: Int): String {
            val index = promptNo - 1
            return NpuS2DbStabilityReportFormatter.prompts.getOrNull(index).orEmpty()
        }
    }
}

private data class NpuS2DbStabilityRunRequest(
    val maxOutputTokens: Int,
    val promptNo: Int,
    val prompt: String,
    val timeoutMs: Long,
)

private data class NpuS2DbStabilityPromptRun(
    val row: NpuS2DbStabilityReportRow,
    val timedOut: Boolean,
)

internal fun normalizeNpuS2DbStabilityResult(
    result: NpuStandardRouteS1Result,
): NpuStandardRouteS1Result {
    val normalizedSanitizedOutput =
        Qairt244NpuOutputSanitizer.normalizeJapaneseInternalSpaces(result.sanitizedOutput)
    if (normalizedSanitizedOutput == result.sanitizedOutput) return result
    return result.copy(
        sanitizedOutput = normalizedSanitizedOutput,
        displayText = NpuStandardRouteS1Contract.displayText(
            selection = result.selection,
            status = result.status,
            reason = result.reason,
            rawOutput = result.rawOutput,
            sanitizedOutput = normalizedSanitizedOutput,
            qualityClassification = result.qualityClassification,
            runDecodeReached = result.runDecodeReached,
            npuBackendEvidence = result.npuBackendEvidence,
            fallbackUsed = result.fallbackUsed,
            timeout = result.timeout,
            freshCrash = result.freshCrash,
            selectedModelName = result.selectedModelName,
            selectedModelFile = result.selectedModelFile,
            npuModelEligible = result.npuModelEligible,
            timing = result.timing,
            s2DbReason = result.s2DbReason,
        ),
    )
}

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
            val normalizedSanitizedOutput =
                Qairt244NpuOutputSanitizer.normalizeJapaneseInternalSpaces(result.sanitizedOutput)
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
                sanitizedOutput = normalizedSanitizedOutput,
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
    private const val SANITIZED_OUTPUT_COLUMN_INDEX = 6

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
        "prompt_index",
        "prompt_no",
        "prompt_text",
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
            appendLine("- prompt_no: `$promptIndex`")
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
            val cells = reportCells(row)
            appendLine("| ${cells.joinToString(" | ") { markdownCell(it) }} |")
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
            val cells = reportCells(row)
            appendLine(cells.joinToString(",") { csvCell(it) })
        }
    }

    internal fun reportCells(row: NpuS2DbStabilityReportRow): List<String> =
        row.toRawCells().toMutableList().also { cells ->
            cells[SANITIZED_OUTPUT_COLUMN_INDEX] = normalizeCellSanitizedOutput(
                cells[SANITIZED_OUTPUT_COLUMN_INDEX],
            )
        }

    internal fun normalizeCellSanitizedOutput(value: String): String =
        Qairt244NpuOutputSanitizer.normalizeJapaneseInternalSpaces(value)

    private fun NpuS2DbStabilityReportRow.toRawCells(): List<String> = listOf(
        number.toString(),
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

internal object NpuS2DbStabilityFallbackReport {
    fun rowFromStateText(stateText: String): NpuS2DbStabilityReportRow {
        val state = parseState(stateText)
        val promptNo = state["prompt_no"]
            ?.toIntOrNull()
            ?: state["prompt_index"]?.toIntOrNull()
            ?: state["prompt_number"]?.toIntOrNull()
            ?: 1
        return NpuS2DbStabilityReportRow.failure(
            number = promptNo,
            prompt = unescapeStateValue(state["prompt_text"].orEmpty()),
            reason = state["reason"].orEmpty().ifBlank { "fallback_state_report" },
            timeout = state["reason"] == "prompt_timeout" || state["status"] == "timeout",
            notes = unescapeStateValue(state["notes"].orEmpty()).ifBlank {
                "automation_scope=s2_decoding_and_save_decision_logic; ui_db_integration=false; source=state_fallback"
            },
        ).copy(
            status = state["status"].orEmpty().ifBlank { "failure" },
            judgement = unescapeStateValue(state["judgement"].orEmpty()).ifBlank { "fail" },
        )
    }

    private fun parseState(stateText: String): Map<String, String> =
        stateText.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    line.substring(0, separator) to line.substring(separator + 1)
                }
            }
            .toMap()

    private fun unescapeStateValue(value: String): String =
        value.replace("\\n", "\n").replace("\\\\", "\\")
}
