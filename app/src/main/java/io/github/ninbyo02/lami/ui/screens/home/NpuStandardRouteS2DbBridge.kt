package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS2DbBridge(
    private val mapper: NpuStandardRouteS2DbMapper = NpuStandardRouteS2DbMapper,
) {
    fun prepareSaveCandidate(
        userPrompt: String,
        s1Result: NpuStandardRouteS1Result,
    ): NpuStandardRouteS2DbMapping =
        mapper.map(
            userPrompt = userPrompt,
            s1Result = s1Result,
        )
}
