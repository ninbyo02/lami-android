package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuStandardRouteBridgeTest {
    @Test
    fun `enabled Standard candidate validates persistent production route`() {
        assertTrue(
            shouldUsePersistentStandardRoute(
                currentFlavor = "standard",
                customBuildExperiment = false,
                standardNpuRuntimeEnabled = true,
            ),
        )
    }

    @Test
    fun `ordinary Standard debug keeps isolated one shot probe`() {
        assertFalse(
            shouldUsePersistentStandardRoute(
                currentFlavor = "standard",
                customBuildExperiment = false,
                standardNpuRuntimeEnabled = false,
            ),
        )
    }

    @Test
    fun `custom build continues to validate persistent route`() {
        assertTrue(
            shouldUsePersistentStandardRoute(
                currentFlavor = "customBuildExperiment",
                customBuildExperiment = true,
                standardNpuRuntimeEnabled = false,
            ),
        )
    }
}
