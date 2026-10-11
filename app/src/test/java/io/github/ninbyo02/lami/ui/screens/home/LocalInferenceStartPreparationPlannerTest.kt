package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInferenceStartPreparationPlannerTest {
    @Test fun `prepare clears transient start state without starting the run`() {
        val plan = LocalInferenceStartPreparationPlanner.prepare(
            requestPrompt = "hello",
            streamingUiState = LocalStreamingUiState(responseText = "old", showDelayedPlaceholder = true),
            runState = LocalInferenceRunState(running = false, stopRequested = true),
        )
        assertEquals("hello", plan.pendingUserMessageText)
        assertEquals("old", plan.streamingUiState.responseText)
        assertFalse(plan.streamingUiState.showDelayedPlaceholder)
        assertEquals(LocalInferenceRunState(running = false, stopRequested = false), plan.runState)
        assertTrue(plan.resetEngineStateToReady)
        assertTrue(plan.suppressDevDiagnosticsUntilReplyDisplayed)
        assertTrue(plan.clearComposer)
        assertTrue(plan.clearSelectedImages)
    }
}
