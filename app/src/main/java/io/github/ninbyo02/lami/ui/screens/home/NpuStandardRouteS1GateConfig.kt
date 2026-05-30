package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig

internal object NpuStandardRouteS1GateConfig {
    val enabled: Boolean
        get() = BuildConfig.CUSTOM_BUILD_EXPERIMENT
}
