package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS1Bridge(
    private val invoker: NpuStandardRouteS1Invoker = NpuStandardRouteS1Invoker(),
) {
    constructor(mode: NpuStandardRouteMode) : this(
        invoker = NpuStandardRouteS1Invoker(mode),
    )

    fun run(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(invoker.invoke())
}
