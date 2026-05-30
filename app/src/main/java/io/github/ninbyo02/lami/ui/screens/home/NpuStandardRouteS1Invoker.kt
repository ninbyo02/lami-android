package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS1Invoker(
    private val provider: NpuStandardRouteS1Provider = NpuStandardRouteS1ProviderSelector.defaultProvider(),
) {
    constructor(mode: NpuStandardRouteMode) : this(
        provider = NpuStandardRouteS1ProviderSelector.defaultProviderForMode(mode),
    )

    fun invoke(userPrompt: String): NpuStandardRouteS1RawResult =
        provider.invoke(userPrompt)
}
