package io.github.ninbyo02.lami.npu

import android.content.Context
import io.github.ninbyo02.lami.ui.screens.settings.HiddenQairt244PromptTemplateMode
import java.io.File
import java.security.MessageDigest

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
    ): String {
        matrixResultFile.writeText(
            DevOnlyNpuPromptTemplateMatrix.buildHeader(status = "running")
                .joinToString(separator = "\n", postfix = "\n"),
        )
        val text = DevOnlyNpuPromptTemplateMatrix.run { case ->
            runCase(
                case = case,
                maxOutputTokens = maxOutputTokens,
                timeoutMs = timeoutMs,
            )
        }
        matrixResultFile.writeText(text)
        return text
    }

    private suspend fun runCase(
        case: DevOnlyNpuPromptTemplateMatrix.Case,
        maxOutputTokens: Int,
        timeoutMs: Long,
    ): DevOnlyNpuPromptTemplateMatrix.CaseResult =
        runCatching {
            val result = adapterFactory(appContext, true)
                .runDevOnlyPromptTemplateExperimentOnce(
                    prompt = case.requestPrompt,
                    templateMode = case.template.mode,
                    maxOutputTokens = maxOutputTokens,
                    timeoutMs = timeoutMs,
                )
            val values = if (nativeResultFile.isFile) {
                Qairt244NativeResultParser.parse(nativeResultFile.readText()).values
            } else {
                emptyMap()
            }
            DevOnlyNpuPromptTemplateMatrix.CaseResult.from(
                routeResult = result,
                values = values,
            )
        }.getOrElse { throwable ->
            DevOnlyNpuPromptTemplateMatrix.CaseResult.failure(
                reason = "case_failure:${throwable.javaClass.simpleName}",
                message = throwable.message.orEmpty(),
            )
        }

    private companion object {
        private const val NATIVE_RESULT_FILE_NAME = "qairt244_short_multitoken_smoke_result.txt"
    }
}

object DevOnlyNpuPromptTemplateMatrix {
    const val RESULT_FILE_NAME = "dev_only_npu_prompt_template_matrix_result.txt"

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
        "prompt_and_output_policy=hash_length_code_points_preview_only",
        "standard_route_template_unchanged=raw_dialog_tail_variant_b",
    )

    fun buildRow(
        index: Int,
        case: Case,
        result: CaseResult,
    ): List<String> = listOf(
        "case_index=$index",
        "template_name=${case.template.name}",
        "input_prompt_hash=${hash(case.inputPrompt)}",
        "input_prompt_length=${case.inputPrompt.length}",
        "input_prompt_code_points=${codePoints(case.inputPrompt)}",
        "input_prompt_preview=${preview(case.inputPrompt)}",
        "request_prompt_hash=${hash(case.requestPrompt)}",
        "request_prompt_length=${case.requestPrompt.length}",
        "request_prompt_code_points=${codePoints(case.requestPrompt)}",
        "request_prompt_preview=${preview(case.requestPrompt)}",
        "status=${result.status}",
        "reason=${result.reason}",
        "run_decode_reached=${result.runDecodeReached}",
        "fallback_used=${result.fallbackUsed}",
        "timeout=${result.timeout}",
        "fresh_crash=${result.freshCrash}",
        "raw_output_hash=${hash(result.rawOutput)}",
        "raw_output_length=${result.rawOutputLength}",
        "raw_output_code_points=${codePoints(result.rawOutput)}",
        "raw_output_preview=${preview(result.rawOutput)}",
        "sanitized_output_hash=${hash(result.sanitizedOutput)}",
        "sanitized_output_length=${result.sanitizedOutputLength}",
        "sanitized_output_code_points=${codePoints(result.sanitizedOutput)}",
        "sanitized_output_preview=${preview(result.sanitizedOutput)}",
        "quality_classification=${result.qualityClassification}",
        "elapsed_ms=${result.elapsedMs ?: "unknown"}",
        "message_preview=${preview(result.message)}",
        "case_end=true",
    )

    private fun codePoints(value: String): Int = value.codePointCount(0, value.length)

    private fun preview(value: String): String {
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

    private fun hash(value: String): String {
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
            ): CaseResult = CaseResult(
                status = "failure",
                reason = reason,
                runDecodeReached = false,
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                rawOutput = "",
                rawOutputLength = 0,
                sanitizedOutput = "",
                sanitizedOutputLength = 0,
                qualityClassification = "unknown",
                elapsedMs = null,
                message = message,
            )
        }
    }

    private const val PREVIEW_LIMIT = 32
}
