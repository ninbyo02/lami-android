package io.github.ninbyo02.lami.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarTimingsTest {
    @Test fun projectFeedbackEndsAtNineHundredMilliseconds() = runTest {
        val host = SnackbarHostState()
        val feedback = launch { host.showProjectShortSnackbar("voice feedback") }
        runCurrent()
        assertEquals("voice feedback", host.currentSnackbarData?.visuals?.message)
        advanceTimeBy(899)
        runCurrent()
        assertEquals("voice feedback", host.currentSnackbarData?.visuals?.message)
        advanceTimeBy(1)
        runCurrent()
        assertNull(host.currentSnackbarData)
        assertTrue(feedback.isCompleted)
    }

    @Test fun cancellingFeedbackDoesNotDismissTheNextNotification() = runTest {
        val host = SnackbarHostState()
        val feedback = launch { host.showProjectShortSnackbar("voice feedback") }
        runCurrent()
        feedback.cancel()
        runCurrent()
        val next = launch { host.showSnackbar("next", duration = SnackbarDuration.Indefinite) }
        runCurrent()
        advanceTimeBy(PROJECT_SNACKBAR_SHORT_MS)
        runCurrent()
        assertEquals("next", host.currentSnackbarData?.visuals?.message)
        next.cancel()
    }
}
