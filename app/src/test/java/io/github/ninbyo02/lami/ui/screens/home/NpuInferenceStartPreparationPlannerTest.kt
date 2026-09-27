package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuInferenceStartPreparationPlannerTest {
    @Test fun `prepare reuses common state and starts npu run`() {
        val plan = NpuInferenceStartPreparationPlanner.prepare(
            requestPrompt = "hello",
            streamingUiState = LocalStreamingUiState(responseText = "old", showDelayedPlaceholder = true),
            runState = LocalInferenceRunState(stopRequested = true),
        )
        assertEquals("hello", plan.common.pendingUserMessageText)
        assertFalse(plan.common.streamingUiState.showDelayedPlaceholder)
        assertEquals(LocalInferenceRunState(running = true, stopRequested = false), plan.runState)
        assertTrue(plan.clearRouteDisplayText)
        assertTrue(plan.clearPhaseUiAppendText)
        assertTrue(plan.clearFallbackText)
        assertTrue(plan.clearPseudoStreamingText)
        assertTrue(plan.resetPseudoStreamingActive)
        assertTrue(plan.resetStreamingSentenceTtsBlocked)
        assertTrue(plan.collapseDevDiagnostics)
    }
}
