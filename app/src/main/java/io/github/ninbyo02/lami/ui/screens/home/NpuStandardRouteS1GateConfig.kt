package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig

private const val ENABLE_STANDARD_DEBUG_NPU_STANDARD_ROUTE_S1 = false

internal object NpuStandardRouteS1GateConfig {
    val enabled: Boolean
        get() = BuildConfig.CUSTOM_BUILD_EXPERIMENT ||
            ENABLE_STANDARD_DEBUG_NPU_STANDARD_ROUTE_S1

    fun isEnabledForMode(mode: NpuStandardRouteMode): Boolean =
        enabled || mode.isS1Enabled()
}
