package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalInferenceStartCoordinatorTest {
    @Test fun `inactive job with idle state starts new`() {
        assertEquals(ExistingLocalGenerationJobPolicy.START_NEW, LocalInferenceStartCoordinator.resolveStartAction(LocalInferenceRunState(), false))
    }
    @Test fun `active job with idle state cancels stale and waits`() {
        assertEquals(ExistingLocalGenerationJobPolicy.CANCEL_STALE_AND_WAIT, LocalInferenceStartCoordinator.resolveStartAction(LocalInferenceRunState(), true))
    }
    @Test fun `active job takes precedence over running state`() {
        assertEquals(ExistingLocalGenerationJobPolicy.CANCEL_STALE_AND_WAIT, LocalInferenceStartCoordinator.resolveStartAction(LocalInferenceRunState(running = true), true))
    }
    @Test fun `running state with inactive job blocks duplicate start`() {
        assertEquals(ExistingLocalGenerationJobPolicy.ALREADY_RUNNING, LocalInferenceStartCoordinator.resolveStartAction(LocalInferenceRunState(running = true), false))
    }
}
