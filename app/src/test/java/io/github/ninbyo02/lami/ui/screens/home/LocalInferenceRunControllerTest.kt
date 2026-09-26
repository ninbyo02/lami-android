package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalInferenceRunControllerTest {
    @Test fun `start marks running and clears stale stop`() {
        assertEquals(LocalInferenceRunState(running = true, stopRequested = false), LocalInferenceRunController.start(LocalInferenceRunState(stopRequested = true)))
    }
    @Test fun `request stop preserves running`() {
        assertEquals(LocalInferenceRunState(running = true, stopRequested = true), LocalInferenceRunController.requestStop(LocalInferenceRunState(running = true)))
    }
    @Test fun `finish preserves stop request`() {
        assertEquals(LocalInferenceRunState(running = false, stopRequested = true), LocalInferenceRunController.finish(LocalInferenceRunState(running = true, stopRequested = true)))
    }
    @Test fun `clear stop request preserves running`() {
        assertEquals(LocalInferenceRunState(running = true, stopRequested = false), LocalInferenceRunController.clearStopRequest(LocalInferenceRunState(running = true, stopRequested = true)))
    }
}
