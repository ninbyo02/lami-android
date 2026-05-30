package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS4PseudoStreamingBridge(
    private val mapper: NpuStandardRouteS4PseudoStreamingMapper = NpuStandardRouteS4PseudoStreamingMapper,
) {
    fun preparePseudoStreamingCandidate(
        s1Result: NpuStandardRouteS1Result,
        finalText: String = s1Result.displayText,
        sourceDisplayText: String = s1Result.displayText,
        minChunks: Int = NpuStandardRouteS4PseudoStreamingContract.DEFAULT_MIN_CHUNKS,
        maxChunks: Int = NpuStandardRouteS4PseudoStreamingContract.DEFAULT_MAX_CHUNKS,
    ): NpuStandardRouteS4PseudoStreamingMapping =
        mapper.map(
            s1Result = s1Result,
            finalText = finalText,
            sourceDisplayText = sourceDisplayText,
            minChunks = minChunks,
            maxChunks = maxChunks,
        )
}
