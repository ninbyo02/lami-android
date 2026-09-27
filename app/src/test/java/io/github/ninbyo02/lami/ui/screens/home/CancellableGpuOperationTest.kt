package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CancellableGpuOperationTest {
    @Test fun `user stop cancels worker and executes its cleanup`() = runTest {
        var started = false
        var cleaned = false
        val request = launch {
            runCancellableGpuOperation<Unit>(60000, StandardTestDispatcher(testScheduler)) {
                started = true
                try { awaitCancellation() } finally { cleaned = true }
            }
        }
        runCurrent(); assertTrue(started)
        request.cancel(); runCurrent()
        assertTrue(cleaned); assertTrue(request.isCancelled)
    }
    @Test fun `watchdog cancels worker without losing timeout classification`() = runTest {
        var cleaned = false
        val result = runCancellableGpuOperation<Unit>(100, StandardTestDispatcher(testScheduler)) {
            try { awaitCancellation() } finally { cleaned = true }
        }
        advanceUntilIdle()
        assertTrue(result.timedOut); assertTrue(cleaned)
    }
    @Test fun `successful null result is not a timeout`() = runTest {
        val result = runCancellableGpuOperation<String>(100, StandardTestDispatcher(testScheduler)) { null }
        assertFalse(result.timedOut); assertNull(result.value)
    }
}
