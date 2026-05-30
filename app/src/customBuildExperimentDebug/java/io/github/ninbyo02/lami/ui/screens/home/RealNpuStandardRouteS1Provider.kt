package io.github.ninbyo02.lami.ui.screens.home

internal class RealNpuStandardRouteS1Provider : NpuStandardRouteS1Provider {
    override fun invoke(): NpuStandardRouteS1RawResult =
        FailureNpuStandardRouteS1Provider(reason = REASON_NOT_IMPLEMENTED).invoke()

    companion object {
        const val REASON_NOT_IMPLEMENTED = "real_provider_not_implemented"
    }
}
