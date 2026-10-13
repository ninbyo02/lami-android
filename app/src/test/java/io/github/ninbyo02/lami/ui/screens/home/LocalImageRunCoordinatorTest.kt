package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class LocalImageRunCoordinatorTest {
    @Test fun imageCompletionRechecksLeaseInsidePersistenceMutexAndReleaseCannotFallback() {
        val source = File("src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt").readText()
        val finalizer = source.substringAfter("suspend fun finalizeStreamingAssistantMessageSerialized(")
            .substringBefore("fun sanitizeTextForTts")
        assertTrue(finalizer.indexOf("streamingAssistantPersistMutex.withLock") <
            finalizer.indexOf("!imageRunLease.claimCompletion()"))
        assertTrue(finalizer.indexOf("!imageRunLease.claimCompletion()") <
            finalizer.indexOf("suspend fun persist() = finalizeStreamingAssistantMessage"))
        assertTrue(finalizer.contains("imageRunLease.persistTerminal { persist() }"))
        assertTrue(source.contains("catch (failure: LocalImagePersistenceException)"))
        val npuFailure = source.substringAfter("Log.e(\"ChatScreen\", \"NPU standard route execution failed\", exception)")
            .substringBefore("suspend fun runExceptionFallback")
        assertTrue(npuFailure.contains("exception is LocalImageReleaseException"))
        assertTrue(npuFailure.contains("return@launch"))
        val runner = File("src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt").readText()
        assertTrue(runner.contains("closeEngine = { engine?.takeIf { it.isInitialized() }?.close() }"))
    }

    private fun LocalImageRunCoordinator.begin() = requireNotNull(begin(Any(), 1, "model", "NPU"))

    @Test fun doubleCancelIsIdempotentAndBlocksResendUntilJobCompletes() {
        val coordinator = LocalImageRunCoordinator()
        val lease = coordinator.begin()
        val job = Job()
        lease.bindJob(job)
        assertTrue(lease.requestCancellation("stop"))
        assertFalse(lease.requestCancellation("double-stop"))
        assertTrue(job.isCancelled)
        assertFalse(lease.acceptsCallbacks)
        assertNotNull(coordinator.begin(Any(), 2, "next", "CPU"))
    }

    @Test fun cancelWaitsForNonCancellableNativeDrainAndHistoryCleanup() = runBlocking {
        val coordinator = LocalImageRunCoordinator()
        val lease = coordinator.begin()
        val drain = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.LAZY) {
            try {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            } finally {
                lease.releasing()
                withContext(NonCancellable) { drain.await() }
            }
        }
        lease.bindJob(job)
        job.start()
        started.await()
        lease.requestCancellation("stop")
        kotlinx.coroutines.yield()
        assertTrue(coordinator.state.value.cleanupPending)
        assertNull(coordinator.begin(Any(), 1, "model", "GPU"))
        drain.complete(Unit)
        job.join()
        assertFalse(coordinator.state.value.busy)
        assertTrue(coordinator.begin().acceptsCallbacks)
        assertFalse(lease.acceptsCallbacks)
    }

    @Test fun cancellationBeforeJobBindingDoesNotStartNativeWork() = runBlocking {
        val coordinator = LocalImageRunCoordinator()
        val lease = coordinator.begin()
        var calls = 0
        lease.requestCancellation("disposed-during-preparation")
        val job = launch(start = CoroutineStart.LAZY) { calls++ }
        lease.bindJob(job)
        job.start()
        job.join()
        assertEquals(0, calls)
        assertFalse(coordinator.state.value.busy)
    }

    @Test fun oldPostedCallbackAndOldCompletionCannotOwnNewRun() {
        val coordinator = LocalImageRunCoordinator()
        val old = coordinator.begin()
        val job = Job()
        old.bindJob(job)
        job.complete()
        val next = coordinator.begin()
        old.releasing()
        assertFalse(old.acceptsCallbacks)
        assertTrue(next.acceptsCallbacks)
        assertEquals(next.generation, coordinator.state.value.generation)
    }

    @Test fun acceptedCancellationWinsOverSubsequentCompletionOrFailure() {
        val coordinator = LocalImageRunCoordinator()
        val lease = coordinator.begin()
        assertTrue(lease.requestCancellation("stop"))
        assertFalse(lease.claimCompletion())
        assertFalse(lease.claimFailure())
    }

    @Test fun concurrentStopAndCompletionHaveExactlyOneWinner() {
        val workers = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val coordinator = LocalImageRunCoordinator()
                val lease = coordinator.begin()
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val stop = workers.submit<Boolean> { ready.countDown(); start.await(); lease.requestCancellation("stop") }
                val completion = workers.submit<Boolean> { ready.countDown(); start.await(); lease.claimCompletion() }
                ready.await()
                start.countDown()
                assertNotEquals(stop.get(), completion.get())
                assertFalse(lease.acceptsCallbacks)
            }
        } finally {
            workers.shutdownNow()
        }
    }

    @Test fun terminalPersistenceClaimCannotLaterAcceptStopOrCallbacks() {
        val coordinator = LocalImageRunCoordinator()
        val lease = coordinator.begin()
        assertTrue(lease.claimCompletion())
        assertFalse(lease.requestCancellation("stop-during-db-write"))
        assertFalse(lease.claimCompletion())
        assertFalse(lease.acceptsCallbacks)
        assertTrue(coordinator.state.value.cleanupPending)
        assertNull(coordinator.begin(Any(), 1, "next", "CPU"))
    }

    @Test fun quarantineStillAllowsOneFailurePersistenceButNeverSuccessOrReuse() {
        val coordinator = LocalImageRunCoordinator()
        val lease = coordinator.begin()
        coordinator.quarantine("close-failed", listOf(Any()))
        assertFalse(lease.claimCompletion())
        assertTrue(lease.claimFailure())
        assertFalse(lease.claimFailure())
        coordinator.complete(lease)
        assertEquals(LocalImageRunPhase.RELEASE_FAILED, coordinator.state.value.phase)
    }

    @Test fun claimedPersistenceFailureCannotLeaveAnUnfinishedRowAndAdvertiseReady() = runBlocking {
        for (cancelled in listOf(false, true)) {
            val coordinator = LocalImageRunCoordinator()
            val lease = coordinator.begin()
            assertTrue(lease.claimCompletion())
            try {
                lease.persistTerminal<Int> {
                    if (cancelled) throw kotlinx.coroutines.CancellationException("disposed during DB write")
                    error("database write failed")
                }
                fail("Failed persistence must propagate")
            } catch (failure: IllegalStateException) {
                if (cancelled) assertTrue(failure is kotlinx.coroutines.CancellationException)
                else assertTrue(failure is LocalImagePersistenceException)
            }
            assertFalse(lease.claimFailure())
            coordinator.complete(lease)
            assertEquals(LocalImageRunPhase.RELEASE_FAILED, coordinator.state.value.phase)
            assertNull(coordinator.begin(Any(), 2, "next", "CPU"))
        }
    }

    @Test fun screenDisposalAndBackendSwitchUseTheSameCancellationFence() = runBlocking {
        for (reason in listOf("screen-disposed", "backend-changed", "thread-changed", "failure")) {
            val coordinator = LocalImageRunCoordinator()
            val lease = coordinator.begin()
            val drain = CompletableDeferred<Unit>()
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                try { CompletableDeferred<Unit>().await() }
                finally { withContext(NonCancellable) { drain.await() } }
            }
            lease.bindJob(job)
            lease.requestCancellation(reason)
            assertFalse(lease.acceptsCallbacks)
            assertNull(coordinator.begin(Any(), 3, "other", "CPU"))
            drain.complete(Unit)
            job.join()
            assertFalse(coordinator.state.value.busy)
        }
    }

    @Test fun releaseOrdersConversationBeforeEngine() {
        val calls = mutableListOf<String>()
        releaseLocalImageResources(Any(), Any(), { calls += "conversation" }, { calls += "engine" })
        assertEquals(listOf("conversation", "engine"), calls)
    }

    @Test fun quarantinedLifecycleFailureIsContainedButUnrelatedErrorsAreNotSwallowed() {
        assertFalse(runLocalImageLifecycleCleanup { throw LocalImageReleaseException("conversation", Exception("close")) })
        assertTrue(runLocalImageLifecycleCleanup {})
        try {
            runLocalImageLifecycleCleanup { throw kotlinx.coroutines.CancellationException("stop") }
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        try {
            runLocalImageLifecycleCleanup { error("unrelated bug") }
            fail("Unrelated failure must propagate")
        } catch (_: IllegalStateException) { }
    }

    @Test fun failedHistoryCleanupCannotAdvertiseReadyOrReuseTheRun() = runBlocking {
        val coordinator = LocalImageRunCoordinator()
        val lease = coordinator.begin()
        lease.bindAssistantMessage(123)
        lease.requestCancellation("stop")
        var captured: Int? = null
        assertFalse(lease.finishHistoryCleanup { captured = it; error("database unavailable") })
        assertEquals(123, captured)
        coordinator.complete(lease)
        assertEquals(LocalImageRunPhase.RELEASE_FAILED, coordinator.state.value.phase)
        assertNull(coordinator.begin(Any(), 1, "model", "CPU"))
    }

    @Test fun failedConversationCloseNeverFreesEngineOrAllowsUnsafeReuse() {
        val coordinator = LocalImageRunCoordinator()
        val lease = coordinator.begin()
        var engineClosed = false
        val error = runCatching {
            releaseLocalImageResources(Any(), Any(), { error("still active") }, { engineClosed = true }, coordinator::quarantine)
        }.exceptionOrNull()
        assertTrue(error is LocalImageReleaseException)
        assertFalse(engineClosed)
        assertEquals(LocalImageRunPhase.RELEASE_FAILED, coordinator.state.value.phase)
        val job = Job()
        lease.bindJob(job)
        job.complete()
        assertFalse(lease.acceptsCallbacks)
        assertNull(coordinator.begin(Any(), 1, "model", "GPU"))
    }

    @Test fun engineCloseFailureAlsoQuarantinesUntilProcessRestart() {
        val coordinator = LocalImageRunCoordinator()
        val result = runCatching {
            releaseLocalImageResources(null, Any(), {}, { error("close failed") }, coordinator::quarantine)
        }
        assertTrue(result.exceptionOrNull() is LocalImageReleaseException)
        assertTrue(coordinator.state.value.cleanupPending)
        assertNull(coordinator.begin(Any(), 1, "model", "NPU"))
    }
}
