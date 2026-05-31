package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS1Invoker(
    private val provider: NpuStandardRouteS1Provider = NpuStandardRouteS1ProviderSelector.defaultProvider(),
    private val trace: (String) -> Unit = {},
) {
    constructor(
        mode: NpuStandardRouteMode,
        trace: (String) -> Unit = {},
    ) : this(
        provider = NpuStandardRouteS1ProviderSelector.defaultProviderForMode(mode),
        trace = trace,
    )

    fun invoke(
        userPrompt: String,
        maxOutputTokens: Int = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
    ): NpuStandardRouteS1RawResult {
        trace(buildNpuRealPromptHandoffTrace(stage = "invoker", userPrompt = userPrompt))
        return provider.invoke(
            userPrompt = userPrompt,
            maxOutputTokens = maxOutputTokens,
            trace = trace,
        )
    }
}
