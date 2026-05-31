package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationDisplay

internal object RealNpuStandardRouteS1ResultMapper {
    fun fromDisplay(
        display: DevOnlyNpuOneTurnConversationDisplay,
        userPrompt: String = "",
    ): NpuStandardRouteS1RawResult {
        val sanitizedOutput = display.output.trim()
        val questionEcho = sanitizedOutput.isNotBlank() &&
            isQuestionEcho(
                input = userPrompt,
                output = sanitizedOutput,
            )
        val assistantStub = !questionEcho && isAssistantStub(sanitizedOutput)
        val status = if (questionEcho || assistantStub) {
            FailureNpuStandardRouteS1Provider.STATUS_FAILURE
        } else if (display.status == NpuStandardRouteS1Contract.STATUS_SUCCESS) {
            NpuStandardRouteS1Contract.STATUS_SUCCESS
        } else {
            FailureNpuStandardRouteS1Provider.STATUS_FAILURE
        }
        val reason = if (questionEcho) {
            NpuStandardRouteS1Contract.REASON_QUESTION_ECHO
        } else if (assistantStub) {
            NpuStandardRouteS1Contract.REASON_ASSISTANT_STUB
        } else {
            display.reason
        }
        val qualityClassification = if (questionEcho) {
            NpuStandardRouteS1Contract.QUALITY_QUESTION_ECHO
        } else if (assistantStub) {
            NpuStandardRouteS1Contract.QUALITY_ASSISTANT_STUB
        } else {
            display.quality
        }
        return NpuStandardRouteS1RawResult(
            status = status,
            result = status,
            success = status == NpuStandardRouteS1Contract.STATUS_SUCCESS,
            reason = reason,
            rawOutput = display.rawOutputFirst200Chars,
            sanitizedOutput = sanitizedOutput,
            qualityClassification = qualityClassification,
            runDecodeReached = display.decodeReached,
            npuBackendEvidence = display.npuEvidence,
            fallbackUsed = display.fallback,
            timeout = display.timeout,
            freshCrash = display.freshCrash,
            requestedMaxOutputTokens = display.requestedMaxOutputTokens,
            effectiveMaxOutputTokens = display.effectiveMaxOutputTokens,
            npuS1OutputTokens = display.outputTokenCount.toIntOrNull(),
            npuS1TokenCountMode = if (display.outputTokenCount.toIntOrNull() != null) {
                NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_NATIVE_REPORTED
            } else {
                NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_UNAVAILABLE
            },
        )
    }

    fun failure(
        reason: String,
        maxOutputTokens: Int = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
    ): NpuStandardRouteS1RawResult =
        FailureNpuStandardRouteS1Provider(reason = reason).invoke(
            userPrompt = "",
            maxOutputTokens = maxOutputTokens,
            trace = {},
        )

    private fun isQuestionEcho(
        input: String,
        output: String,
    ): Boolean {
        val normalizedInput = input.filterNot { char -> char.isWhitespace() }
        val normalizedOutput = output.filterNot { char -> char.isWhitespace() }
        return normalizedInput.isNotBlank() && normalizedInput == normalizedOutput
    }

    private fun isAssistantStub(output: String): Boolean =
        output.trim() in assistantStubOutputs

    private val assistantStubOutputs = setOf(
        "アシスタント。",
        "アシスタント:",
        "Assistant.",
        "Assistant:",
    )
}
