package io.github.ninbyo02.lami.ui.screens.home

internal fun interface NpuStandardRouteS1Provider {
    fun invoke(userPrompt: String): NpuStandardRouteS1RawResult
}
