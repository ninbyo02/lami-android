package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS1Invoker(
    private val provider: NpuStandardRouteS1Provider = FixedNpuStandardRouteS1Provider(),
) {
    fun invoke(): NpuStandardRouteS1RawResult = provider.invoke()
}
