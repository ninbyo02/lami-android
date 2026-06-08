package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS1Bridge(
    private val invoker: NpuStandardRouteS1Invoker = NpuStandardRouteS1Invoker(),
    private val trace: (String) -> Unit = {},
) {
    constructor(
        mode: NpuStandardRouteMode,
        trace: (String) -> Unit = {},
        allowDevNativeRoute: Boolean = false,
    ) : this(
        invoker = NpuStandardRouteS1Invoker(
            mode = mode,
            trace = trace,
            allowDevNativeRoute = allowDevNativeRoute,
        ),
        trace = trace,
    )

    fun run(
        userPrompt: String,
        maxOutputTokens: Int = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
    ): NpuStandardRouteS1Result {
        trace(buildNpuRealPromptHandoffTrace(stage = "bridge", userPrompt = userPrompt))
        val decodeStartedAtNs = System.nanoTime()
        val rawResult = invoker.invoke(
            userPrompt = userPrompt,
            maxOutputTokens = maxOutputTokens,
        )
        val decodeMs = ((System.nanoTime() - decodeStartedAtNs) / 1_000_000L).coerceAtLeast(0L)
        return NpuStandardRouteS1Mapper.map(
            rawResult.copy(
                npuS1DecodeMs = rawResult.npuS1DecodeMs ?: decodeMs,
            ),
        )
    }
}
