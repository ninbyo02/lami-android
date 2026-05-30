package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationDisplay

internal object RealNpuStandardRouteS1ResultMapper {
    fun fromDisplay(display: DevOnlyNpuOneTurnConversationDisplay): NpuStandardRouteS1RawResult {
        val status = if (display.status == NpuStandardRouteS1Contract.STATUS_SUCCESS) {
            NpuStandardRouteS1Contract.STATUS_SUCCESS
        } else {
            FailureNpuStandardRouteS1Provider.STATUS_FAILURE
        }
        return NpuStandardRouteS1RawResult(
            status = status,
            result = status,
            success = status == NpuStandardRouteS1Contract.STATUS_SUCCESS,
            reason = display.reason,
            rawOutput = display.rawOutputFirst200Chars,
            sanitizedOutput = display.output,
            qualityClassification = display.quality,
            runDecodeReached = display.decodeReached,
            npuBackendEvidence = display.npuEvidence,
            fallbackUsed = display.fallback,
            timeout = display.timeout,
            freshCrash = display.freshCrash,
            requestedMaxOutputTokens = display.requestedMaxOutputTokens,
            effectiveMaxOutputTokens = display.effectiveMaxOutputTokens,
        )
    }

    fun failure(reason: String): NpuStandardRouteS1RawResult =
        FailureNpuStandardRouteS1Provider(reason = reason).invoke(userPrompt = "")
}
