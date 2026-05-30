package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig

internal object NpuStandardRouteS1ProviderSelector {
    const val REAL_PROVIDER_CLASS_NAME =
        "io.github.ninbyo02.lami.ui.screens.home.RealNpuStandardRouteS1Provider"
    const val REASON_REAL_PROVIDER_UNAVAILABLE = "real_provider_unavailable_for_variant"
    const val REASON_REAL_PROVIDER_INVALID_TYPE = "real_provider_invalid_type"

    fun defaultProvider(): NpuStandardRouteS1Provider =
        if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            customBuildExperimentProvider()
        } else {
            FixedNpuStandardRouteS1Provider()
        }

    private fun customBuildExperimentProvider(): NpuStandardRouteS1Provider =
        runCatching {
            val instance = Class.forName(REAL_PROVIDER_CLASS_NAME)
                .getDeclaredConstructor()
                .newInstance()
            instance as? NpuStandardRouteS1Provider
                ?: FailureNpuStandardRouteS1Provider(reason = REASON_REAL_PROVIDER_INVALID_TYPE)
        }.getOrElse {
            FailureNpuStandardRouteS1Provider(reason = REASON_REAL_PROVIDER_UNAVAILABLE)
        }
}
