package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig

private const val ENABLE_STANDARD_DEBUG_NPU_STANDARD_ROUTE_S1 = false

internal object NpuStandardRouteS1GateConfig {
    val runtimeAvailable: Boolean
        get() = BuildConfig.CUSTOM_BUILD_EXPERIMENT ||
            BuildConfig.DEBUG ||
            BuildConfig.STANDARD_NPU_RUNTIME_ENABLED ||
            ENABLE_STANDARD_DEBUG_NPU_STANDARD_ROUTE_S1

    val enabled: Boolean
        get() = BuildConfig.CUSTOM_BUILD_EXPERIMENT ||
            ENABLE_STANDARD_DEBUG_NPU_STANDARD_ROUTE_S1

    fun isEnabledForMode(mode: NpuStandardRouteMode): Boolean =
        runtimeAvailable && (enabled || mode.isS1Enabled())
}
