package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsMemoryReleasePolicyTest {
    @Test
    fun `releases held engine when system reports low memory`() {
        val decision = decideHeldEngineReleaseForTts(
            TtsMemorySnapshot(
                lowMemory = true,
                availableMemoryMb = 2_048,
                thresholdMemoryMb = 128,
                appTotalPssMb = 300,
                appNativePssMb = 120,
            ),
        )

        assertTrue(decision.shouldReleaseHeldEngine)
        assertEquals("low-memory", decision.reason)
    }

    @Test
    fun `keeps held engine when available memory is below previous headroom`() {
        val decision = decideHeldEngineReleaseForTts(
            TtsMemorySnapshot(
                lowMemory = false,
                availableMemoryMb = 700,
                thresholdMemoryMb = 128,
                appTotalPssMb = 300,
                appNativePssMb = 120,
            ),
        )

        assertFalse(decision.shouldReleaseHeldEngine)
        assertEquals("memory-ok", decision.reason)
    }

    @Test
    fun `keeps held engine when app pss is high unless low memory is reported`() {
        val decision = decideHeldEngineReleaseForTts(
            TtsMemorySnapshot(
                lowMemory = false,
                availableMemoryMb = 2_048,
                thresholdMemoryMb = 128,
                appTotalPssMb = 900,
                appNativePssMb = 700,
            ),
        )

        assertFalse(decision.shouldReleaseHeldEngine)
        assertEquals("memory-ok", decision.reason)
    }

    @Test
    fun `keeps held engine when memory headroom is sufficient`() {
        val decision = decideHeldEngineReleaseForTts(
            TtsMemorySnapshot(
                lowMemory = false,
                availableMemoryMb = 2_048,
                thresholdMemoryMb = 128,
                appTotalPssMb = 390,
                appNativePssMb = 200,
            ),
        )

        assertFalse(decision.shouldReleaseHeldEngine)
        assertEquals("memory-ok", decision.reason)
    }
}
