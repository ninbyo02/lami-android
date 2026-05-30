package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS1Bridge(
    private val invoker: NpuStandardRouteS1Invoker = NpuStandardRouteS1Invoker(),
    private val trace: (String) -> Unit = {},
) {
    constructor(
        mode: NpuStandardRouteMode,
        trace: (String) -> Unit = {},
    ) : this(
        invoker = NpuStandardRouteS1Invoker(mode, trace),
        trace = trace,
    )

    fun run(userPrompt: String): NpuStandardRouteS1Result {
        trace(buildNpuRealPromptHandoffTrace(stage = "bridge", userPrompt = userPrompt))
        return NpuStandardRouteS1Mapper.map(invoker.invoke(userPrompt))
    }
}
