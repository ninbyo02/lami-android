package io.github.ninbyo02.lami.assistant

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantCaptureSerialJobsTest {
    @Test fun tripleInvocationCannotSkipTheFirstOwnersNativeCleanup() = runTest {
        val owners = AssistantCaptureSerialJobs()
        val release = CompletableDeferred<Unit>()
        var active = 0; var peak = 0; var secondStarted = false; var thirdStarted = false
        val first = owners.replace(this) {
            active++; peak = maxOf(peak, active)
            try { awaitCancellation() } finally {
                withContext(NonCancellable) { release.await(); active-- }
            }
        }
        first.start(); runCurrent()
        val second = owners.replace(this) { secondStarted = true }
        second.start(); runCurrent()
        val third = owners.replace(this) { thirdStarted = true; active++; peak = maxOf(peak, active); active-- }
        third.start(); runCurrent()
        assertFalse(secondStarted); assertFalse(thirdStarted); assertEquals(1, active)
        release.complete(Unit); runCurrent(); third.join()
        assertFalse(secondStarted); assertTrue(thirdStarted); assertEquals(1, peak); assertEquals(0, active)
    }
    @Test fun completedOwnerDoesNotBlockANewInvocation() = runTest {
        val owners = AssistantCaptureSerialJobs(); var count = 0
        repeat(3) { val next = owners.replace(this) { count++ }; next.start(); next.join() }
        assertEquals(3, count)
    }
    @Test fun cancelledWaitingInvocationNeverStartsItsMicrophone() = runTest {
        val owners = AssistantCaptureSerialJobs(); val release = CompletableDeferred<Unit>(); var started = false
        val first = owners.replace(this) { try { awaitCancellation() } finally { withContext(NonCancellable) { release.await() } } }
        first.start(); runCurrent()
        val next = owners.replace(this) { started = true }; next.start(); runCurrent(); next.cancel()
        release.complete(Unit); runCurrent(); next.join(); assertFalse(started)
    }
}
