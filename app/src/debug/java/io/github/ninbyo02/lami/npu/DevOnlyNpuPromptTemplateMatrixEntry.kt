package io.github.ninbyo02.lami.npu

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.ninbyo02.lami.ui.screens.settings.HiddenQairt244PromptTemplateMode
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class DevOnlyNpuPromptTemplateMatrixEntry(
    context: Context,
    private val adapterFactory: (Context, Boolean) -> Qairt244DevOnlyNpuRouteAdapter = { appContext, unsafeBypass ->
        Qairt244DevOnlyNpuRouteAdapter(
            context = appContext,
            maxOutputTokenRangeLimit = DevOnlyNpuRouteAdapter.QAIRT244_MAX_OUTPUT_TOKENS_COMPARE_LIMIT,
            unsafeDevBypassPromptLengthGate = unsafeBypass,
        )
    },
) {
    private val appContext = context.applicationContext
    private val nativeResultFile = File(appContext.filesDir, NATIVE_RESULT_FILE_NAME)
    private val matrixResultFile = File(appContext.filesDir, DevOnlyNpuPromptTemplateMatrix.RESULT_FILE_NAME)

    suspend fun run(
        maxOutputTokens: Int = DevOnlyNpuOneTurnConversationContract.COMPARE_MAX_OUTPUT_TOKENS,
        timeoutMs: Long = DevOnlyNpuOneTurnConversationContract.TIMEOUT_MS,
        matrixTimeoutMs: Long = DevOnlyNpuPromptTemplateMatrix.DEFAULT_MATRIX_TIMEOUT_MS,
    ): String {
        matrixResultFile.writeText(
            DevOnlyNpuPromptTemplateMatrix.buildHeader(status = "running")
                .joinToString(separator = "\n", postfix = "\n"),
        )
        appendProgress(DevOnlyNpuPromptTemplateMatrix.buildMatrixStart())
        Log.i(TAG, "matrix_start result_file=${DevOnlyNpuPromptTemplateMatrix.RESULT_FILE_NAME}")
        return runCatching {
            withTimeout(matrixTimeoutMs) {
                val consecutiveFailuresByTemplate = mutableMapOf<String, Int>()
                DevOnlyNpuPromptTemplateMatrix.cases().forEachIndexed { index, case ->
                    val caseIndex = index + 1
                    val templateName = case.template.name
                    val consecutiveFailures = consecutiveFailuresByTemplate[templateName] ?: 0
                    val result = if (consecutiveFailures >= DevOnlyNpuPromptTemplateMatrix.TEMPLATE_FAILURE_THRESHOLD) {
                        DevOnlyNpuPromptTemplateMatrix.CaseResult.skipped(
                            reason = DevOnlyNpuPromptTemplateMatrix.REASON_TEMPLATE_FAILURE_THRESHOLD,
                        ).also { skipped ->
                            appendProgress(
                                DevOnlyNpuPromptTemplateMatrix.buildTemplateSkipped(
                                    index = caseIndex,
                                    case = case,
                                    reason = skipped.reason,
                                ),
                            )
                            Log.i(TAG, "template_skipped index=$caseIndex template=$templateName reason=${skipped.reason}")
                        }
                    } else {
                        appendProgress(DevOnlyNpuPromptTemplateMatrix.buildCaseStart(caseIndex, case))
                        Log.i(TAG, "case_start index=$caseIndex template=$templateName")
                        runCase(
                            case = case,
                            maxOutputTokens = maxOutputTokens,
                            timeoutMs = timeoutMs,
                            caseIndex = caseIndex,
                        )
                    }
                    appendProgress(DevOnlyNpuPromptTemplateMatrix.buildRow(caseIndex, case, result))
                    appendProgress(DevOnlyNpuPromptTemplateMatrix.buildCaseDone(caseIndex, case, result))
                    Log.i(
                        TAG,
                        "case_done index=$caseIndex template=$templateName " +
                            "status=${result.status} reason=${result.reason}",
                    )
                    if (result.status == "success") {
                        consecutiveFailuresByTemplate[templateName] = 0
                    } else if (result.status == "failure") {
                        consecutiveFailuresByTemplate[templateName] = consecutiveFailures + 1
                    }
                }
            }
            appendProgress(listOf("status=completed"))
            Log.i(TAG, "matrix_completed")
            matrixResultFile.readText()
        }.getOrElse { throwable ->
            val reason = if (throwable is TimeoutCancellationException) {
                "matrix_timeout"
            } else {
                "matrix_failure:${throwable.javaClass.simpleName}"
            }
            appendProgress(
                listOf(
                    "status=failed",
                    "reason=$reason",
                    "message_hash=${DevOnlyNpuPromptTemplateMatrix.safeHash(throwable.message.orEmpty())}",
                    "message_preview=${DevOnlyNpuPromptTemplateMatrix.safePreview(throwable.message.orEmpty())}",
                ),
            )
            Log.i(TAG, "matrix_failed reason=$reason exception=${throwable.javaClass.simpleName}")
            matrixResultFile.readText()
        }
    }

    private suspend fun runCase(
        case: DevOnlyNpuPromptTemplateMatrix.Case,
        maxOutputTokens: Int,
        timeoutMs: Long,
        caseIndex: Int,
    ): DevOnlyNpuPromptTemplateMatrix.CaseResult =
        runCaseCatching(
            case = case,
            caseIndex = caseIndex,
        ) {
            val result = withTimeout(timeoutMs) {
                adapterFactory(appContext, true)
                    .runDevOnlyPromptTemplateExperimentOnce(
                        prompt = case.requestPrompt,
                        templateMode = case.template.mode,
                        maxOutputTokens = maxOutputTokens,
                        timeoutMs = timeoutMs,
                    )
            }
            val values = if (nativeResultFile.isFile) {
                Qairt244NativeResultParser.parse(nativeResultFile.readText()).values
            } else {
                emptyMap()
            }
            DevOnlyNpuPromptTemplateMatrix.CaseResult.from(
                routeResult = result,
                values = values,
            )
        }

    private suspend fun runCaseCatching(
        case: DevOnlyNpuPromptTemplateMatrix.Case,
        caseIndex: Int,
        block: suspend () -> DevOnlyNpuPromptTemplateMatrix.CaseResult,
    ): DevOnlyNpuPromptTemplateMatrix.CaseResult {
        val startMs = SystemClock.elapsedRealtime()
        return runCatching {
            block()
        }.getOrElse { throwable ->
            val elapsedMs = SystemClock.elapsedRealtime() - startMs
            appendProgress(
                DevOnlyNpuPromptTemplateMatrix.buildCaseFailed(
                    index = caseIndex,
                    case = case,
                    throwable = throwable,
                    elapsedMs = elapsedMs,
                ),
            )
            Log.i(
                TAG,
                "case_failed index=$caseIndex template=${case.template.name} " +
                    "exception=${throwable.javaClass.simpleName}",
            )
            val reason = if (throwable is TimeoutCancellationException) {
                "case_timeout"
            } else {
                "case_failure:${throwable.javaClass.simpleName}"
            }
            DevOnlyNpuPromptTemplateMatrix.CaseResult.failure(
                reason = reason,
                message = throwable.message.orEmpty(),
                elapsedMs = elapsedMs,
                timeout = throwable is TimeoutCancellationException,
            )
        }
    }

    private fun appendProgress(lines: List<String>) {
        matrixResultFile.appendText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    private companion object {
        private const val TAG = "NpuPromptTemplateMatrix"
        private const val NATIVE_RESULT_FILE_NAME = "qairt244_short_multitoken_smoke_result.txt"
    }
}

object DevOnlyNpuPromptTemplateMatrix {
    const val RESULT_FILE_NAME = "dev_only_npu_prompt_template_matrix_result.txt"
    const val TEMPLATE_FAILURE_THRESHOLD = 2
    const val DEFAULT_MATRIX_TIMEOUT_MS = 10 * 60 * 1000L
    const val REASON_TEMPLATE_FAILURE_THRESHOLD = "template_failure_threshold"

    val templates: List<Template> = listOf(
        Template(
            name = DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
            mode = HiddenQairt244PromptTemplateMode.RAW,
            promptBuilder = { prompt ->
                DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
                    contextText = "",
                    userPrompt = prompt,
                    promptTailVariant = DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
                )
            },
        ),
        Template(
            name = HiddenQairt244PromptTemplateMode.SIMPLE_JA_CHAT.storageValue,
            mode = HiddenQairt244PromptTemplateMode.SIMPLE_JA_CHAT,
            promptBuilder = { prompt -> prompt.trim() },
        ),
        Template(
            name = HiddenQairt244PromptTemplateMode.GEMMA_IT_LIKE.storageValue,
            mode = HiddenQairt244PromptTemplateMode.GEMMA_IT_LIKE,
            promptBuilder = { prompt -> prompt.trim() },
        ),
    )

    val prompts: List<String> = listOf(
        "こんにちは",
        "おはよう",
        "こんばんは",
        "明日の天気は",
        "あなたは誰ですか",
    )

    suspend fun run(
        caseRunner: suspend (Case) -> CaseResult,
    ): String {
        val rows = mutableListOf<String>()
        cases().forEachIndexed { index, case ->
            val result = caseRunner(case)
            rows += buildRow(
                index = index + 1,
                case = case,
                result = result,
            )
        }
        return buildHeader(status = "success")
            .plus(rows)
            .joinToString(separator = "\n", postfix = "\n")
    }

    fun cases(): List<Case> = templates.flatMap { template ->
        prompts.map { prompt ->
            Case(
                template = template,
                inputPrompt = prompt,
                requestPrompt = template.promptBuilder(prompt),
            )
        }
    }

    fun buildHeader(status: String): List<String> = listOf(
        "DEV ONLY NPU PROMPT TEMPLATE MATRIX",
        "status=$status",
        "result_file=$RESULT_FILE_NAME",
        "template_count=${templates.size}",
        "prompt_count=${prompts.size}",
        "case_count=${templates.size * prompts.size}",
        "template_failure_threshold=$TEMPLATE_FAILURE_THRESHOLD",
        "matrix_timeout_ms=$DEFAULT_MATRIX_TIMEOUT_MS",
        "prompt_and_output_policy=hash_length_code_points_preview_only",
        "standard_route_template_unchanged=raw_dialog_tail_variant_b",
    )

    fun buildMatrixStart(): List<String> = listOf(
        "matrix_start=true",
        "status=running",
    )

    fun buildCaseStart(
        index: Int,
        case: Case,
    ): List<String> = listOf(
        "case_start=true",
        "case_index=$index",
        "template_name=${case.template.name}",
        "prompt_hash=${safeHash(case.inputPrompt)}",
        "prompt_length=${case.inputPrompt.length}",
        "prompt_code_points=${codePoints(case.inputPrompt)}",
        "prompt_preview=${safePreview(case.inputPrompt)}",
    )

    fun buildCaseDone(
        index: Int,
        case: Case,
        result: CaseResult,
    ): List<String> = listOf(
        "case_done=true",
        "case_index=$index",
        "template_name=${case.template.name}",
        "status=${result.status}",
        "reason=${result.reason}",
        "elapsed_ms=${result.elapsedMs ?: "unknown"}",
    )

    fun buildCaseFailed(
        index: Int,
        case: Case,
        throwable: Throwable,
        elapsedMs: Long,
    ): List<String> = listOf(
        "case_failed=true",
        "case_index=$index",
        "template_name=${case.template.name}",
        "exception_class=${throwable.javaClass.simpleName}",
        "message_hash=${safeHash(throwable.message.orEmpty())}",
        "message_preview=${safePreview(throwable.message.orEmpty())}",
        "elapsed_ms=$elapsedMs",
    )

    fun buildTemplateSkipped(
        index: Int,
        case: Case,
        reason: String = REASON_TEMPLATE_FAILURE_THRESHOLD,
    ): List<String> = listOf(
        "template_skipped=true",
        "template_name=${case.template.name}",
        "case_index=$index",
        "reason=$reason",
    )

    fun buildRow(
        index: Int,
        case: Case,
        result: CaseResult,
    ): List<String> = listOf(
        "case_index=$index",
        "template_name=${case.template.name}",
        "input_prompt_hash=${safeHash(case.inputPrompt)}",
        "input_prompt_length=${case.inputPrompt.length}",
        "input_prompt_code_points=${codePoints(case.inputPrompt)}",
        "input_prompt_preview=${safePreview(case.inputPrompt)}",
        "request_prompt_hash=${safeHash(case.requestPrompt)}",
        "request_prompt_length=${case.requestPrompt.length}",
        "request_prompt_code_points=${codePoints(case.requestPrompt)}",
        "request_prompt_preview=${safePreview(case.requestPrompt)}",
        "status=${result.status}",
        "reason=${result.reason}",
        "run_decode_reached=${result.runDecodeReached}",
        "fallback_used=${result.fallbackUsed}",
        "timeout=${result.timeout}",
        "fresh_crash=${result.freshCrash}",
        "raw_output_hash=${safeHash(result.rawOutput)}",
        "raw_output_length=${result.rawOutputLength}",
        "raw_output_code_points=${codePoints(result.rawOutput)}",
        "raw_output_preview=${safePreview(result.rawOutput)}",
        "sanitized_output_hash=${safeHash(result.sanitizedOutput)}",
        "sanitized_output_length=${result.sanitizedOutputLength}",
        "sanitized_output_code_points=${codePoints(result.sanitizedOutput)}",
        "sanitized_output_preview=${safePreview(result.sanitizedOutput)}",
        "quality_classification=${result.qualityClassification}",
        "elapsed_ms=${result.elapsedMs ?: "unknown"}",
        "message_preview=${safePreview(result.message)}",
        "case_end=true",
    )

    private fun codePoints(value: String): Int = value.codePointCount(0, value.length)

    fun safePreview(value: String): String {
        val normalized = value.map { char -> if (char.isWhitespace()) ' ' else char }
            .joinToString(separator = "")
            .trim()
        if (normalized.isBlank()) return "-"
        return if (normalized.length <= PREVIEW_LIMIT) {
            normalized
        } else {
            normalized.take(PREVIEW_LIMIT) + "..."
        }
    }

    fun safeHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }.take(12)
    }

    data class Template(
        val name: String,
        val mode: HiddenQairt244PromptTemplateMode,
        val promptBuilder: (String) -> String,
    )

    data class Case(
        val template: Template,
        val inputPrompt: String,
        val requestPrompt: String,
    )

    data class CaseResult(
        val status: String,
        val reason: String,
        val runDecodeReached: Boolean,
        val fallbackUsed: Boolean,
        val timeout: Boolean,
        val freshCrash: Boolean,
        val rawOutput: String,
        val rawOutputLength: Int,
        val sanitizedOutput: String,
        val sanitizedOutputLength: Int,
        val qualityClassification: String,
        val elapsedMs: Long?,
        val message: String = "",
    ) {
        companion object {
            fun from(
                routeResult: DevOnlyNpuRouteResult,
                values: Map<String, String>,
            ): CaseResult {
                val rawOutput = values["raw_native_output"].orEmpty().ifBlank {
                    values["raw_output"].orEmpty().ifBlank {
                        values["output"].orEmpty()
                    }
                }
                val sanitizedOutput = values["sanitized_output"].orEmpty().ifBlank {
                    routeResult.output.orEmpty()
                }
                val rawLength = values["raw_native_output_length"]?.toIntOrNull()
                    ?: values["raw_output_length"]?.toIntOrNull()
                    ?: rawOutput.length
                val sanitizedLength = values["sanitized_output_length"]?.toIntOrNull()
                    ?: sanitizedOutput.length
                return CaseResult(
                    status = if (routeResult.success) "success" else "failure",
                    reason = routeResult.reasonCode,
                    runDecodeReached = values["run_decode"].orEmpty().contains("RunDecode") ||
                        routeResult.decodeElapsedMs != null,
                    fallbackUsed = values["fallback_used"]?.toBooleanStrictOrNull() ?: false,
                    timeout = routeResult.timeout,
                    freshCrash = routeResult.freshCrash,
                    rawOutput = rawOutput,
                    rawOutputLength = rawLength,
                    sanitizedOutput = sanitizedOutput,
                    sanitizedOutputLength = sanitizedLength,
                    qualityClassification = values["quality_classification"].orEmpty().ifBlank { "unknown" },
                    elapsedMs = routeResult.elapsedMs,
                )
            }

            fun failure(
                reason: String,
                message: String,
                elapsedMs: Long? = null,
                timeout: Boolean = false,
            ): CaseResult = CaseResult(
                status = "failure",
                reason = reason,
                runDecodeReached = false,
                fallbackUsed = false,
                timeout = timeout,
                freshCrash = false,
                rawOutput = "",
                rawOutputLength = 0,
                sanitizedOutput = "",
                sanitizedOutputLength = 0,
                qualityClassification = "unknown",
                elapsedMs = elapsedMs,
                message = message,
            )

            fun skipped(
                reason: String = REASON_TEMPLATE_FAILURE_THRESHOLD,
            ): CaseResult = CaseResult(
                status = "skipped",
                reason = reason,
                runDecodeReached = false,
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                rawOutput = "",
                rawOutputLength = 0,
                sanitizedOutput = "",
                sanitizedOutputLength = 0,
                qualityClassification = "skipped",
                elapsedMs = 0,
            )
        }
    }

    private const val PREVIEW_LIMIT = 32
}
