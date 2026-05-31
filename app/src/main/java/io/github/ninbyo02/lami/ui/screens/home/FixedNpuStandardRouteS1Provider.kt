package io.github.ninbyo02.lami.ui.screens.home

internal class FixedNpuStandardRouteS1Provider : NpuStandardRouteS1Provider {
    override fun invoke(
        userPrompt: String,
        maxOutputTokens: Int,
        trace: (String) -> Unit,
    ): NpuStandardRouteS1RawResult =
        NpuStandardRouteS1RawResult(
            status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
            result = NpuStandardRouteS1Contract.STATUS_SUCCESS,
            success = true,
            reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
            rawOutput = DEFAULT_DISPLAY_TEXT,
            sanitizedOutput = DEFAULT_DISPLAY_TEXT,
            qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
            runDecodeReached = true,
            npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
            fallbackUsed = false,
            timeout = false,
            freshCrash = false,
            requestedMaxOutputTokens = NpuStandardRoutePreferences.sanitizeMaxOutputTokens(maxOutputTokens),
            effectiveMaxOutputTokens = NpuStandardRoutePreferences.sanitizeMaxOutputTokens(maxOutputTokens),
        )

    companion object {
        const val DEFAULT_DISPLAY_TEXT = "こんにちは。"
    }
}
