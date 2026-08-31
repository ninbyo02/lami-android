package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuResidentLifecyclePolicyTest {
    @Test
    fun `background timeout releases resident NPU`() {
        val state = NpuResidentLifecycleState(
            appInForeground = false,
            backgroundedAtElapsedMs = 1_000L,
            lastUsedAtElapsedMs = 1_000L,
        )
        assertFalse(isNpuBackgroundReleaseDue(state, 1_000L + NPU_RESIDENT_BACKGROUND_TIMEOUT_MS - 1))
        assertTrue(isNpuBackgroundReleaseDue(state, 1_000L + NPU_RESIDENT_BACKGROUND_TIMEOUT_MS))
    }

    @Test
    fun `foreground never releases for idle timeout`() {
        val state = NpuResidentLifecycleState(
            appInForeground = true,
            lastUsedAtElapsedMs = 1_000L,
        )
        assertFalse(isNpuIdleReleaseDue(state, 1_000L + NPU_RESIDENT_IDLE_TIMEOUT_MS + 1))
    }

    @Test
    fun `background idle timeout releases resident NPU`() {
        val state = NpuResidentLifecycleState(
            appInForeground = false,
            lastUsedAtElapsedMs = 1_000L,
        )
        assertTrue(isNpuIdleReleaseDue(state, 1_000L + NPU_RESIDENT_IDLE_TIMEOUT_MS))
    }
}
