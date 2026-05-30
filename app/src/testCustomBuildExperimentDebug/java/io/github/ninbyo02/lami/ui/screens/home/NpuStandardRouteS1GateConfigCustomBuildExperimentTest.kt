package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1GateConfigCustomBuildExperimentTest {
    @Test
    fun `custom build experiment enables S1 gate`() {
        assertTrue(NpuStandardRouteS1GateConfig.enabled)
    }
}
