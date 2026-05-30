package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1GateConfigCustomBuildExperimentTest {
    @Test
    fun `custom build experiment enables S1 gate`() {
        assertTrue(NpuStandardRouteS1GateConfig.enabled)
    }

    @Test
    fun `custom build experiment keeps S1 gate compatible even when Settings mode is OFF`() {
        assertTrue(NpuStandardRouteS1GateConfig.isEnabledForMode(NpuStandardRouteMode.OFF))
    }
}
