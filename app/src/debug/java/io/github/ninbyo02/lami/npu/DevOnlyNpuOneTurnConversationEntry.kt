package io.github.ninbyo02.lami.npu

import android.content.Context
import android.util.Base64
import io.github.ninbyo02.lami.ui.screens.settings.HiddenQairt244PromptTemplateMode
import java.io.File

data class DevOnlyNpuOneTurnConversationRequest(
    val userPrompt: String,
    val contextText: String = "",
    val unsafeDevBypassPromptLengthGate: Boolean = true,
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
)

object DevOnlyNpuOneTurnConversationContract {
    const val RECEIVER_ACTION = "io.github.ninbyo02.lami.action.DEV_ONLY_NPU_ONE_TURN_CONVERSATION"
    const val EXTRA_USER_PROMPT = "user_prompt"
    const val EXTRA_CONTEXT = "context"
    const val EXTRA_UNSAFE_DEV_BYPASS_PROMPT_LENGTH_GATE = "unsafe_dev_bypass_prompt_length_gate"
    const val DEFAULT_USER_PROMPT = "こんにちは"
    const val RECEIVER_RESULT_FILE_NAME = "dev_only_npu_one_turn_conversation_result.txt"
    const val TEMPLATE = "raw_dialog_tail"
    const val PROMPT_TAIL_MODE = "raw_dialog_tail"
    const val PROMPT_TRANSPORT = "base64"
    const val ROUTE_TYPE = "dev_only_one_turn_conversation"
    const val MAX_OUTPUT_TOKENS = 16
    const val TIMEOUT_MS = 60_000L
    const val INITIAL_DISPLAY_TEXT = "DEV ONLY NPU ONE TURN\nstatus=idle\nadapter_execution=manual_trigger_only"

    fun safety(): DevOnlyNpuOneTurnConversationSafety = DevOnlyNpuOneTurnConversationSafety()

    fun buildRawDialogTailPrompt(contextText: String, userPrompt: String): String {
        val normalizedContext = contextText.trim()
        val normalizedUserPrompt = userPrompt.trim()
        val head = if (normalizedContext.isBlank()) {
            ""
        } else {
            "$normalizedContext\n\n"
        }
        return "${head}ユーザー: $normalizedUserPrompt\nアシスタント:"
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
        val controlSummary = values["output_unicode_summary"].orEmpty()
            .substringAfter("control_chars=", "control_chars=unknown")
            .substringBefore(";")
        val fallback = values["fallback_used"]?.toBooleanStrictOrNull() ?: false
        val requestedMaxOutputTokens = result.maxOutputTokens
        val effectiveMaxOutputTokens = values["max_output_tokens"]?.toIntOrNull() ?: result.maxOutputTokens
        val nativeMaxOutputTokensLimit = values["native_max_output_tokens_limit"].orEmpty().ifBlank { "-" }
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
        safety: DevOnlyNpuOneTurnConversationSafety = safety(),
    ): String {
        val lines = listOf(
            "timestamp=$timestampMs",
            "status=failure",
            "result=failure",
            "success=false",
            "reason=$reason",
            "message=${escapeResultValue(message)}",
            "requested_max_output_tokens=$MAX_OUTPUT_TOKENS",
            "effective_max_output_tokens=$MAX_OUTPUT_TOKENS",
            "max_output_tokens=$MAX_OUTPUT_TOKENS",
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
        timestampMs: Long,
        safety: DevOnlyNpuOneTurnConversationSafety = safety(),
    ): String {
        val lines = listOf(
            "timestamp=$timestampMs",
            "status=$status",
            "result=pending",
            "success=false",
            "reason=$status",
            "requested_max_output_tokens=$MAX_OUTPUT_TOKENS",
            "effective_max_output_tokens=$MAX_OUTPUT_TOKENS",
            "max_output_tokens=$MAX_OUTPUT_TOKENS",
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
        )
        val transportedPrompt = transportPromptBase64(finalPrompt)
        val result = adapterFactory(appContext, request.unsafeDevBypassPromptLengthGate)
            .runDevOnlyConversationOnce(
                prompt = transportedPrompt,
                maxOutputTokens = DevOnlyNpuOneTurnConversationContract.MAX_OUTPUT_TOKENS,
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
