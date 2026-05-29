package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS1Invoker(
    private val rawResultProvider: () -> NpuStandardRouteS1RawResult = {
        NpuStandardRouteS1RawResult(
            status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
            result = NpuStandardRouteS1Contract.STATUS_SUCCESS,
            success = true,
            reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
            rawOutput = NpuStandardRouteS1Invoker.DEFAULT_DISPLAY_TEXT,
            sanitizedOutput = NpuStandardRouteS1Invoker.DEFAULT_DISPLAY_TEXT,
            qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
            runDecodeReached = true,
            npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
            fallbackUsed = false,
            timeout = false,
            freshCrash = false,
            requestedMaxOutputTokens = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS,
            effectiveMaxOutputTokens = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS,
        )
    },
) {
    fun invoke(): NpuStandardRouteS1RawResult = rawResultProvider()

    companion object {
        const val DEFAULT_DISPLAY_TEXT = "こんにちは。"
    }
}
