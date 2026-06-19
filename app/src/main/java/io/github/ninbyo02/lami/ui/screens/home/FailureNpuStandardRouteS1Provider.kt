package io.github.ninbyo02.lami.ui.screens.home

internal class FailureNpuStandardRouteS1Provider(
    private val reason: String = DEFAULT_REASON,
    private val fallbackUsed: Boolean = false,
    private val timeout: Boolean = false,
    private val freshCrash: Boolean = false,
) : NpuStandardRouteS1Provider {
    override fun invoke(
        userPrompt: String,
        maxOutputTokens: Int,
        trace: (String) -> Unit,
    ): NpuStandardRouteS1RawResult =
        NpuStandardRouteS1RawResult(
            status = STATUS_FAILURE,
            result = STATUS_FAILURE,
            success = false,
            reason = reason,
            rawOutput = "",
            sanitizedOutput = "",
            qualityClassification = QUALITY_UNKNOWN,
            runDecodeReached = false,
            npuBackendEvidence = "",
            fallbackUsed = fallbackUsed,
            timeout = timeout,
            freshCrash = freshCrash,
            requestedMaxOutputTokens = NpuStandardRoutePreferences.sanitizeMaxOutputTokens(maxOutputTokens),
            effectiveMaxOutputTokens = NpuStandardRoutePreferences.sanitizeMaxOutputTokens(maxOutputTokens),
        )

    companion object {
        const val DEFAULT_REASON = "provider_failure"
        const val QUALITY_UNKNOWN = "unknown"
        const val STATUS_FAILURE = "failure"
    }
}
