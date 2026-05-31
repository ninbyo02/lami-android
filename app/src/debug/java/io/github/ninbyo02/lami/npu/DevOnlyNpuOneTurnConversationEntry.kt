package io.github.ninbyo02.lami.npu

import android.content.Context
import android.util.Base64
import io.github.ninbyo02.lami.ui.screens.settings.HiddenQairt244PromptTemplateMode
import java.io.File

data class DevOnlyNpuOneTurnConversationRequest(
    val userPrompt: String,
    val contextText: String = "",
    val unsafeDevBypassPromptLengthGate: Boolean = true,
    val maxOutputTokens: Int = DevOnlyNpuOneTurnConversationContract.DEFAULT_MAX_OUTPUT_TOKENS,
    val promptTailVariant: String = DevOnlyNpuOneTurnConversationContract.DEFAULT_PROMPT_TAIL_VARIANT,
    val timeoutMs: Long = DevOnlyNpuOneTurnConversationContract.TIMEOUT_MS,
)

data class DevOnlyNpuOneTurnConversationSafety(
    val standardRouteConnected: Boolean = false,
    val backendNpuPersisted: Boolean = false,
    val db: Boolean = false,
    val tts: Boolean = false,
    val markdown: Boolean = false,
    val streaming: Boolean = false,
    val selectedPathNpuSaved: Boolean = false,
    val appTemplateMode: String = HiddenQairt244PromptTemplateMode.RAW.storageValue,
    val template: String = DevOnlyNpuOneTurnConversationContract.TEMPLATE,
    val promptTailVariant: String = DevOnlyNpuOneTurnConversationContract.DEFAULT_PROMPT_TAIL_VARIANT,
    val promptTransport: String = DevOnlyNpuOneTurnConversationContract.PROMPT_TRANSPORT,
)

data class DevOnlyNpuOneTurnConversationDisplay(
    val text: String,
    val output: String,
    val status: String,
    val reason: String,
    val nativeReached: Boolean,
    val decodeReached: Boolean,
    val npuEvidence: String,
    val fallback: Boolean,
    val freshCrash: Boolean,
    val timeout: Boolean,
    val requestedMaxOutputTokens: Int,
    val effectiveMaxOutputTokens: Int,
    val nativeMaxOutputTokensLimit: String,
    val rawLen: Int,
    val sanitizedLen: Int,
    val quality: String,
    val controlCharSummary: String,
    val rawOutputFirst200Chars: String,
    val rawOutputLast200Chars: String,
    val rawUnicodeSummary: String,
    val sanitizerApplied: String,
    val removedTemplateTokenCount: String,
    val removedPromptEcho: String,
    val replacementCharCount: String,
    val outputContainsControlChars: String,
    val rawOutput: String = "",
    val stopReason: String = "",
    val finishReason: String = "",
    val eosDetected: String = "",
    val outputTokenCount: String = "",
    val promptTokenCount: String = "",
)

object DevOnlyNpuOneTurnConversationContract {
    const val RECEIVER_ACTION = "io.github.ninbyo02.lami.action.DEV_ONLY_NPU_ONE_TURN_CONVERSATION"
    const val EXTRA_AUTO_RUN = "auto_run"
    const val EXTRA_AUTO_RUN_MATRIX = "auto_run_matrix"
    const val EXTRA_AUTO_RUN_PROMPT_TEMPLATE_MATRIX = "auto_run_prompt_template_matrix"
    const val EXTRA_MAX_OUTPUT_TOKENS = "max_output_tokens"
    const val EXTRA_PROMPT_TAIL_VARIANT = "prompt_tail_variant"
    const val EXTRA_USER_PROMPT = "user_prompt"
    const val EXTRA_CONTEXT = "context"
    const val EXTRA_UNSAFE_DEV_BYPASS_PROMPT_LENGTH_GATE = "unsafe_dev_bypass_prompt_length_gate"
    const val DEFAULT_USER_PROMPT = "こんにちは"
    const val RECEIVER_RESULT_FILE_NAME = "dev_only_npu_one_turn_conversation_result.txt"
    const val MATRIX_RESULT_FILE_NAME = "dev_only_npu_one_turn_conversation_matrix_result.txt"
    const val RECEIVER_RESULT_CODE_RECEIVED = 244
    const val TEMPLATE = "raw_dialog_tail"
    const val PROMPT_TAIL_MODE = "raw_dialog_tail"
    const val RAW_DIALOG_TAIL_VARIANT_A = "raw_dialog_tail_variant_a"
    const val RAW_DIALOG_TAIL_VARIANT_B = "raw_dialog_tail_variant_b"
    const val DEFAULT_PROMPT_TAIL_VARIANT = RAW_DIALOG_TAIL_VARIANT_B
    const val PROMPT_TRANSPORT = "base64"
    const val ROUTE_TYPE = "dev_only_one_turn_conversation"
    const val DEFAULT_MAX_OUTPUT_TOKENS = 16
    const val COMPARE_MAX_OUTPUT_TOKENS = 32
    const val MAX_OUTPUT_TOKENS = DEFAULT_MAX_OUTPUT_TOKENS
    const val TIMEOUT_MS = 60_000L
    const val INITIAL_DISPLAY_TEXT = "DEV ONLY NPU ONE TURN\nstatus=idle\nadapter_execution=manual_trigger_only"
    const val JAPANESE_ONLY_TAIL_INSTRUCTION = "必ず日本語だけで短く返答してください。"
    const val JAPANESE_ASSISTANT_PREFIX_VARIANT_B = "はい、"

    fun safety(
        promptTailVariant: String = DEFAULT_PROMPT_TAIL_VARIANT,
    ): DevOnlyNpuOneTurnConversationSafety = DevOnlyNpuOneTurnConversationSafety(
        promptTailVariant = sanitizePromptTailVariant(promptTailVariant),
    )

    fun activityRequest(
        userPrompt: String?,
        contextText: String,
        unsafeDevBypassPromptLengthGate: Boolean,
        requestedMaxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
        requestedPromptTailVariant: String? = DEFAULT_PROMPT_TAIL_VARIANT,
    ): DevOnlyNpuOneTurnConversationRequest =
        DevOnlyNpuOneTurnConversationRequest(
            userPrompt = userPrompt.orEmpty().ifBlank { DEFAULT_USER_PROMPT },
            contextText = contextText,
            unsafeDevBypassPromptLengthGate = unsafeDevBypassPromptLengthGate,
            maxOutputTokens = sanitizeMaxOutputTokens(requestedMaxOutputTokens),
            promptTailVariant = sanitizePromptTailVariant(requestedPromptTailVariant),
        )

    fun sanitizeMaxOutputTokens(requestedMaxOutputTokens: Int): Int =
        if (requestedMaxOutputTokens == COMPARE_MAX_OUTPUT_TOKENS) {
            COMPARE_MAX_OUTPUT_TOKENS
        } else {
            DEFAULT_MAX_OUTPUT_TOKENS
        }

    fun sanitizePromptTailVariant(requestedPromptTailVariant: String?): String =
        when (requestedPromptTailVariant) {
            RAW_DIALOG_TAIL_VARIANT_A -> RAW_DIALOG_TAIL_VARIANT_A
            RAW_DIALOG_TAIL_VARIANT_B -> RAW_DIALOG_TAIL_VARIANT_B
            else -> DEFAULT_PROMPT_TAIL_VARIANT
        }

    fun buildRawDialogTailPrompt(
        contextText: String,
        userPrompt: String,
        promptTailVariant: String = DEFAULT_PROMPT_TAIL_VARIANT,
    ): String {
        val normalizedContext = contextText.trim()
        val normalizedUserPrompt = userPrompt.trim()
        val head = if (normalizedContext.isBlank()) {
            ""
        } else {
            "$normalizedContext\n\n"
        }
        val assistantLine = when (sanitizePromptTailVariant(promptTailVariant)) {
            RAW_DIALOG_TAIL_VARIANT_A -> "アシスタント:"
            else -> "アシスタント: $JAPANESE_ASSISTANT_PREFIX_VARIANT_B"
        }
        return "$head$JAPANESE_ONLY_TAIL_INSTRUCTION\n" +
            "ユーザー: $normalizedUserPrompt\n" +
            assistantLine
    }

    fun safetyLines(safety: DevOnlyNpuOneTurnConversationSafety = safety()): List<String> = listOf(
        "standard_route_connected=${safety.standardRouteConnected}",
        "backend_npu_persisted=${safety.backendNpuPersisted}",
        "db=${safety.db}",
        "tts=${safety.tts}",
        "markdown=${safety.markdown}",
        "streaming=${safety.streaming}",
        "selected_path_npu_saved=${safety.selectedPathNpuSaved}",
        "route_type=$ROUTE_TYPE",
        "template=${safety.template}",
        "app_template_mode=${safety.appTemplateMode}",
        "prompt_tail_mode=$PROMPT_TAIL_MODE",
        "prompt_tail_variant=${safety.promptTailVariant}",
        "prompt_transport=${safety.promptTransport}",
    )

    fun display(
        result: DevOnlyNpuRouteResult,
        values: Map<String, String>,
        safety: DevOnlyNpuOneTurnConversationSafety = safety(),
    ): DevOnlyNpuOneTurnConversationDisplay {
        val sanitizedOutput = values["sanitized_output"].orEmpty().ifBlank {
            result.output.orEmpty()
        }
        val rawOutput = values["raw_native_output"].orEmpty().ifBlank {
            values["raw_output"].orEmpty()
        }
        val sanitizedLen = values["sanitized_output_length"]?.toIntOrNull() ?: sanitizedOutput.length
        val rawLen = values["raw_native_output_length"]?.toIntOrNull() ?: rawOutput.length
        val rawOutputFirst200Chars = values["output_first_200_chars"].orEmpty().ifBlank {
            rawOutput.take(200)
        }
        val rawOutputLast200Chars = values["output_last_200_chars"].orEmpty().ifBlank {
            rawOutput.takeLast(200)
        }
        val npuEvidence = result.backendEvidence.orEmpty().ifBlank {
            values["npu_backend_evidence"].orEmpty()
        }
        val decodeReached = values["run_decode"].orEmpty().contains("RunDecode") ||
            result.decodeElapsedMs != null
        val engineInitializeReached = values["engine_initialize"]?.let { value ->
            value != "no" && value != "false"
        } ?: false
        val nativeReached = npuEvidence.isNotBlank() || decodeReached || engineInitializeReached
        val quality = values["quality_classification"].orEmpty().ifBlank { "unknown" }
        val rawUnicodeSummary = values["output_unicode_summary"].orEmpty()
        val controlSummary = rawUnicodeSummary
            .substringAfter("control_chars=", "control_chars=unknown")
            .substringBefore(";")
        val fallback = values["fallback_used"]?.toBooleanStrictOrNull() ?: false
        val requestedMaxOutputTokens = result.maxOutputTokens
        val effectiveMaxOutputTokens = values["max_output_tokens"]?.toIntOrNull() ?: result.maxOutputTokens
        val nativeMaxOutputTokensLimit = values["native_max_output_tokens_limit"].orEmpty().ifBlank { "-" }
        val sanitizerApplied = values["sanitizer_applied"].orEmpty().ifBlank { "unknown" }
        val removedTemplateTokenCount = values["removed_template_token_count"].orEmpty().ifBlank { "unknown" }
        val removedPromptEcho = values["removed_prompt_echo"].orEmpty().ifBlank { "unknown" }
        val replacementCharCount = values["replacement_char_count"].orEmpty().ifBlank { "unknown" }
        val outputContainsControlChars = values["output_contains_control_chars"].orEmpty().ifBlank { "unknown" }
        val stopReason = values["stop_reason"].orEmpty()
        val finishReason = values["finish_reason"].orEmpty()
        val eosDetected = values["eos_detected"].orEmpty()
        val outputTokenCount = values["output_token_count"].orEmpty()
        val promptTokenCount = values["prompt_token_count"].orEmpty()
        val status = if (result.success) "success" else "failure"
        val lines = listOf(
            "DEV ONLY NPU ONE TURN",
            "sanitized_output=$sanitizedOutput",
            "status=$status",
            "reason=${result.reasonCode}",
            "requested_max_output_tokens=$requestedMaxOutputTokens",
            "effective_max_output_tokens=$effectiveMaxOutputTokens",
            "max_output_tokens=$effectiveMaxOutputTokens",
            "native_max_output_tokens_limit=$nativeMaxOutputTokensLimit",
            "native=$nativeReached",
            "decode=$decodeReached",
            "run_decode_reached=$decodeReached",
            "npu_backend_evidence=${npuEvidence.ifBlank { "-" }}",
            "npu_evidence=${npuEvidence.ifBlank { "-" }}",
            "fallback_used=$fallback",
            "fallback=$fallback",
            "fresh_crash=${result.freshCrash}",
            "timeout=${result.timeout}",
            "raw_len=$rawLen",
            "sanitized_len=$sanitizedLen",
            "quality=$quality",
            "control_chars=$controlSummary",
            "raw_output_first_200_chars=$rawOutputFirst200Chars",
            "raw_unicode_summary=$rawUnicodeSummary",
            "sanitizer_applied=$sanitizerApplied",
            "removed_template_token_count=$removedTemplateTokenCount",
            "removed_prompt_echo=$removedPromptEcho",
            "replacement_char_count=$replacementCharCount",
            "output_contains_control_chars=$outputContainsControlChars",
            "stop_reason=${stopReason.ifBlank { "unknown" }}",
            "finish_reason=${finishReason.ifBlank { "unknown" }}",
            "eos_detected=${eosDetected.ifBlank { "unknown" }}",
            "output_token_count=${outputTokenCount.ifBlank { "unavailable" }}",
            "prompt_token_count=${promptTokenCount.ifBlank { "unavailable" }}",
        ).plus(safetyLines(safety))
        return DevOnlyNpuOneTurnConversationDisplay(
            text = lines.joinToString("\n"),
            output = sanitizedOutput,
            status = status,
            reason = result.reasonCode,
            nativeReached = nativeReached,
            decodeReached = decodeReached,
            npuEvidence = npuEvidence,
            fallback = fallback,
            freshCrash = result.freshCrash,
            timeout = result.timeout,
            requestedMaxOutputTokens = requestedMaxOutputTokens,
            effectiveMaxOutputTokens = effectiveMaxOutputTokens,
            nativeMaxOutputTokensLimit = nativeMaxOutputTokensLimit,
            rawLen = rawLen,
            sanitizedLen = sanitizedLen,
            quality = quality,
            controlCharSummary = controlSummary,
            rawOutputFirst200Chars = rawOutputFirst200Chars,
            rawOutputLast200Chars = rawOutputLast200Chars,
            rawUnicodeSummary = rawUnicodeSummary,
            sanitizerApplied = sanitizerApplied,
            removedTemplateTokenCount = removedTemplateTokenCount,
            removedPromptEcho = removedPromptEcho,
            replacementCharCount = replacementCharCount,
            outputContainsControlChars = outputContainsControlChars,
            rawOutput = rawOutput,
            stopReason = stopReason,
            finishReason = finishReason,
            eosDetected = eosDetected,
            outputTokenCount = outputTokenCount,
            promptTokenCount = promptTokenCount,
        )
    }

    fun receiverResultText(
        display: DevOnlyNpuOneTurnConversationDisplay,
        timestampMs: Long,
        safety: DevOnlyNpuOneTurnConversationSafety = safety(),
    ): String {
        val result = display.status
        val lines = listOf(
            "timestamp=$timestampMs",
            "status=$result",
            "result=$result",
            "success=${result == "success"}",
            "reason=${display.reason}",
            "requested_max_output_tokens=${display.requestedMaxOutputTokens}",
            "effective_max_output_tokens=${display.effectiveMaxOutputTokens}",
            "max_output_tokens=${display.effectiveMaxOutputTokens}",
            "native_max_output_tokens_limit=${display.nativeMaxOutputTokensLimit}",
            "run_decode_reached=${display.decodeReached}",
            "npu_backend_evidence=${display.npuEvidence.ifBlank { "-" }}",
            "fallback_used=${display.fallback}",
            "timeout=${display.timeout}",
            "fresh_crash=${display.freshCrash}",
            "raw_len=${display.rawLen}",
            "sanitized_len=${display.sanitizedLen}",
            "raw_output_first_200_chars=${escapeResultValue(display.rawOutputFirst200Chars)}",
            "raw_output_last_200_chars=${escapeResultValue(display.rawOutputLast200Chars)}",
            "raw_unicode_summary=${escapeResultValue(display.rawUnicodeSummary)}",
            "sanitizer_applied=${display.sanitizerApplied}",
            "removed_template_token_count=${display.removedTemplateTokenCount}",
            "removed_prompt_echo=${display.removedPromptEcho}",
            "replacement_char_count=${display.replacementCharCount}",
            "output_contains_control_chars=${display.outputContainsControlChars}",
            "stop_reason=${display.stopReason.ifBlank { "unknown" }}",
            "finish_reason=${display.finishReason.ifBlank { "unknown" }}",
            "eos_detected=${display.eosDetected.ifBlank { "unknown" }}",
            "output_token_count=${display.outputTokenCount.ifBlank { "unavailable" }}",
            "prompt_token_count=${display.promptTokenCount.ifBlank { "unavailable" }}",
        ).plus(safetyLines(safety)).plus(
            listOf(
                "sanitized_output=${escapeResultValue(display.output)}",
                "quality_classification=${display.quality}",
                "output_first_200_chars=${escapeResultValue(display.output.take(200))}",
            ),
        )
        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    fun receiverFailureText(
        reason: String,
        message: String,
        timestampMs: Long,
        maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
        safety: DevOnlyNpuOneTurnConversationSafety = safety(),
    ): String {
        val effectiveMaxOutputTokens = sanitizeMaxOutputTokens(maxOutputTokens)
        val lines = listOf(
            "timestamp=$timestampMs",
            "status=failure",
            "result=failure",
            "success=false",
            "reason=$reason",
            "message=${escapeResultValue(message)}",
            "requested_max_output_tokens=$effectiveMaxOutputTokens",
            "effective_max_output_tokens=$effectiveMaxOutputTokens",
            "max_output_tokens=$effectiveMaxOutputTokens",
            "native_max_output_tokens_limit=-",
            "run_decode_reached=false",
            "npu_backend_evidence=-",
            "fallback_used=false",
            "timeout=false",
            "fresh_crash=false",
        ).plus(safetyLines(safety)).plus(
            listOf(
                "sanitized_output=",
                "quality_classification=unknown",
                "output_first_200_chars=",
            ),
        )
        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    fun receiverProgressText(
        status: String,
        action: String = "",
        packageName: String = "",
        className: String = "",
        userPromptPresent: Boolean = false,
        timestampMs: Long,
        maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
        safety: DevOnlyNpuOneTurnConversationSafety = safety(),
    ): String {
        val effectiveMaxOutputTokens = sanitizeMaxOutputTokens(maxOutputTokens)
        val lines = listOf(
            "timestamp=$timestampMs",
            "status=$status",
            "result_code=$RECEIVER_RESULT_CODE_RECEIVED",
            "action=$action",
            "package_name=$packageName",
            "class_name=$className",
            "user_prompt_present=$userPromptPresent",
            "result=pending",
            "success=false",
            "reason=$status",
            "requested_max_output_tokens=$effectiveMaxOutputTokens",
            "effective_max_output_tokens=$effectiveMaxOutputTokens",
            "max_output_tokens=$effectiveMaxOutputTokens",
            "native_max_output_tokens_limit=-",
            "run_decode_reached=false",
            "npu_backend_evidence=-",
            "fallback_used=false",
            "timeout=false",
            "fresh_crash=false",
        ).plus(safetyLines(safety)).plus(
            listOf(
                "sanitized_output=",
                "quality_classification=unknown",
                "output_first_200_chars=",
            ),
        )
        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun escapeResultValue(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n")
}

class DevOnlyNpuOneTurnConversationEntry(
    context: Context,
    private val adapterFactory: (Context, Boolean) -> Qairt244DevOnlyNpuRouteAdapter = { appContext, unsafeBypass ->
        Qairt244DevOnlyNpuRouteAdapter(
            context = appContext,
            promptTemplateMode = HiddenQairt244PromptTemplateMode.RAW,
            maxOutputTokenRangeLimit = DevOnlyNpuRouteAdapter.QAIRT244_MAX_OUTPUT_TOKENS_COMPARE_LIMIT,
            unsafeDevBypassPromptLengthGate = unsafeBypass,
        )
    },
) {
    private val appContext = context.applicationContext
    private val resultFile = File(appContext.filesDir, RESULT_FILE_NAME)

    suspend fun run(request: DevOnlyNpuOneTurnConversationRequest): DevOnlyNpuOneTurnConversationDisplay {
        val finalPrompt = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
            contextText = request.contextText,
            userPrompt = request.userPrompt,
            promptTailVariant = request.promptTailVariant,
        )
        val transportedPrompt = transportPromptBase64(finalPrompt)
        val result = adapterFactory(appContext, request.unsafeDevBypassPromptLengthGate)
            .runDevOnlyConversationOnce(
                prompt = transportedPrompt,
                maxOutputTokens = request.maxOutputTokens,
                timeoutMs = request.timeoutMs,
            )
        val values = if (resultFile.isFile) {
            Qairt244NativeResultParser.parse(resultFile.readText()).values
        } else {
            emptyMap()
        }
        return DevOnlyNpuOneTurnConversationContract.display(
            result = result,
            values = values,
            safety = DevOnlyNpuOneTurnConversationContract.safety(
                promptTailVariant = request.promptTailVariant,
            ),
        )
    }

    private fun transportPromptBase64(prompt: String): String {
        val encoded = Base64.encodeToString(prompt.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
    }

    private companion object {
        private const val RESULT_FILE_NAME = "qairt244_short_multitoken_smoke_result.txt"
    }
}
