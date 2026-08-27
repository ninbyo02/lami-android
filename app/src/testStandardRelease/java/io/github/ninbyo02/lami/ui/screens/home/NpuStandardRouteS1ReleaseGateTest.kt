package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NpuStandardRouteS1ReleaseGateTest {
    @Test
    fun `release runtime availability follows explicit packaging flag`() {
        assertFalse(BuildConfig.DEBUG)
        assertFalse(BuildConfig.CUSTOM_BUILD_EXPERIMENT)
        assertEquals(
            BuildConfig.STANDARD_NPU_RUNTIME_ENABLED,
            NpuStandardRouteS1GateConfig.runtimeAvailable,
        )
        assertEquals(
            BuildConfig.STANDARD_NPU_RUNTIME_ENABLED,
            NpuStandardRouteS1GateConfig.isEnabledForMode(NpuStandardRouteMode.S1_ONLY),
        )
    }
}
