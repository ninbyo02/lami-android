package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TokenRecountExecutionCoordinatorTest {
    @Test fun serializesTwoCallersAndRejectsThirdWithoutCounting() = runTest {
        val coordinator = TokenRecountExecutionCoordinator(StandardTestDispatcher(testScheduler))
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<Int>()
        val first = async { coordinator.execute(-1) { calls += 1; release.await(); 10 } }
        runCurrent()
        val second = async { coordinator.execute(-1) { calls += 2; 20 } }
        runCurrent()
        assertEquals(-1, coordinator.execute(-1) { error("overflow must not run") })
        assertEquals(listOf(1), calls)
        release.complete(Unit)
        assertEquals(10, first.await())
        assertEquals(20, second.await())
        assertEquals(listOf(1, 2), calls)
    }

    @Test fun cancelledWaiterReleasesAdmissionWithoutEnteringCounter() = runTest {
        val coordinator = TokenRecountExecutionCoordinator(StandardTestDispatcher(testScheduler))
        val release = CompletableDeferred<Unit>()
        val active = launch { coordinator.execute(Unit) { release.await() } }
        runCurrent()
        val waiting = launch { coordinator.execute(Unit) { error("cancelled waiter entered") } }
        runCurrent()
        waiting.cancelAndJoin()
        val replacement = async { coordinator.execute(false) { true } }
        runCurrent()
        release.complete(Unit)
        active.join()
        assertTrue(replacement.await())
    }

    @Test fun activeCancellationRunsCleanupBeforeNextCount() = runTest {
        val coordinator = TokenRecountExecutionCoordinator(StandardTestDispatcher(testScheduler))
        var closed = false
        val active = launch {
            coordinator.execute(Unit) { try { awaitCancellation() } finally { closed = true } }
        }
        runCurrent()
        val waiting = async { coordinator.execute(false) { closed } }
        runCurrent()
        active.cancelAndJoin()
        assertTrue(waiting.await())
        assertEquals(7, coordinator.execute(-1) { 7 })
    }

    @Test fun failurePropagatesAndReleasesBothLocks() = runTest {
        val coordinator = TokenRecountExecutionCoordinator(StandardTestDispatcher(testScheduler))
        val failure = IllegalStateException("counter failed")
        try { coordinator.execute(Unit) { throw failure }; fail("failure swallowed") }
        catch (caught: IllegalStateException) { assertEquals(failure.message, caught.message) }
        assertEquals(7, coordinator.execute(-1) { 7 })
    }

    @Test fun scopeCancellationDoesNotLeaveIndependentCountingJob() = runTest {
        val coordinator = TokenRecountExecutionCoordinator(StandardTestDispatcher(testScheduler))
        val owner = Job()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + owner)
        var closed = false
        scope.launch { coordinator.execute(Unit) { try { awaitCancellation() } finally { closed = true } } }
        scope.launch { coordinator.execute(Unit) { error("waiting request must be cancelled") } }
        runCurrent()
        owner.cancelAndJoin()
        assertTrue(closed)
        assertEquals(7, coordinator.execute(-1) { 7 })
    }
}
