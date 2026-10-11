package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInferenceCleanupPlannerTest {
    @Test fun `stale generation finishes run and clears transient ui`() {
        val plan = LocalInferenceCleanupPlanner.staleGeneration(
            runState = LocalInferenceRunState(running = true, stopRequested = true),
            streamingUiState = LocalStreamingUiState(responseText = "partial", showDelayedPlaceholder = true),
            partialStreamingState = LocalPartialStreamingState(didReceiveRealPartial = true, realPartialChunkCount = 3),
        )
        assertEquals(LocalInferenceRunState(running = false, stopRequested = true), plan.runState)
        assertEquals(LocalStreamingUiState(), plan.streamingUiState)
        assertEquals(LocalPartialStreamingState(didReceiveRealPartial = true, realPartialChunkCount = 3), plan.partialStreamingState)
        assertTrue(plan.clearPendingUserMessage)
        assertTrue(plan.resetEngineStateToReady)
    }
}
