package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class GpuMemoryPreflightTest {
    @Test
    fun `allows gpu when available memory clears dynamic requirement`() {
        val decision = decideGpuMemoryPreflight(
            GpuMemoryPreflightSnapshot(
                availableMemoryMb = 10_000,
                systemLowMemory = false,
                systemThresholdMb = 512,
                modelSizeMb = 3_000,
            ),
        )

        assertTrue(decision.allowed)
        assertEquals("enough_available_memory", decision.reason)
        assertEquals(7_536L, decision.requiredAvailableMemoryMb)
    }

    @Test
    fun `blocks gpu when available memory is below model requirement`() {
        val decision = decideGpuMemoryPreflight(
            GpuMemoryPreflightSnapshot(
                availableMemoryMb = 7_000,
                systemLowMemory = false,
                systemThresholdMb = 512,
                modelSizeMb = 3_000,
            ),
        )

        assertFalse(decision.allowed)
        assertEquals("insufficient_available_memory", decision.reason)
        assertEquals(7_536L, decision.requiredAvailableMemoryMb)
    }

    @Test
    fun `blocks gpu when android reports low memory`() {
        val decision = decideGpuMemoryPreflight(
            GpuMemoryPreflightSnapshot(
                availableMemoryMb = 12_000,
                systemLowMemory = true,
                systemThresholdMb = 512,
                modelSizeMb = 2_000,
            ),
        )

        assertFalse(decision.allowed)
        assertEquals("system_low_memory", decision.reason)
    }

    @Test
    fun `blocks gpu when memory information is unavailable`() {
        val decision = decideGpuMemoryPreflight(
            GpuMemoryPreflightSnapshot(
                availableMemoryMb = null,
                systemLowMemory = false,
                systemThresholdMb = null,
                modelSizeMb = 2_000,
            ),
        )

        assertFalse(decision.allowed)
        assertEquals("available_memory_unknown", decision.reason)
    }
}