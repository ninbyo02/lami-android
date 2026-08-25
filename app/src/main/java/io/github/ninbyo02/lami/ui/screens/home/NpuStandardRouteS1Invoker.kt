package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS1Invoker(
    private val provider: NpuStandardRouteS1Provider = NpuStandardRouteS1ProviderSelector.defaultProvider(),
    private val trace: (String) -> Unit = {},
) {
    constructor(
        mode: NpuStandardRouteMode,
        trace: (String) -> Unit = {},
        allowDevNativeRoute: Boolean = false,
    ) : this(
        provider = if (allowDevNativeRoute) {
            NpuStandardRouteS1ProviderSelector.devDiagnosticProviderForMode(mode)
        } else {
            NpuStandardRouteS1ProviderSelector.defaultProviderForMode(mode)
        },
        trace = trace,
    )

    fun invoke(
        userPrompt: String,
        contextText: String = "",
        maxOutputTokens: Int = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
    ): NpuStandardRouteS1RawResult {
        trace(buildNpuRealPromptHandoffTrace(stage = "invoker", userPrompt = userPrompt))
        return provider.invokeWithContext(
            userPrompt = userPrompt,
            contextText = contextText,
            maxOutputTokens = maxOutputTokens,
            trace = trace,
        )
    }
}
