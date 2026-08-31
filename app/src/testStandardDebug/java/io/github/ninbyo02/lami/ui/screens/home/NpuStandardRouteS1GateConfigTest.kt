package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1GateConfigTest {
    @Test
    fun `gate config follows custom build experiment flag while standard promotion is default off`() {
        assertEquals(BuildConfig.CUSTOM_BUILD_EXPERIMENT, NpuStandardRouteS1GateConfig.enabled)
    }

    @Test
    fun `standard debug mode OFF keeps S1 gate disabled`() {
        assertFalse(BuildConfig.CUSTOM_BUILD_EXPERIMENT)
        assertFalse(NpuStandardRouteS1GateConfig.isEnabledForMode(NpuStandardRouteMode.OFF))
    }

    @Test
    fun `standard debug mode S1 only enables S1 gate`() {
        assertFalse(BuildConfig.CUSTOM_BUILD_EXPERIMENT)
        assertTrue(NpuStandardRouteS1GateConfig.isEnabledForMode(NpuStandardRouteMode.S1_ONLY))
    }
}
