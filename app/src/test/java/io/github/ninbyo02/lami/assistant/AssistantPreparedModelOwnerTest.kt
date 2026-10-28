package io.github.ninbyo02.lami.assistant

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantPreparedModelOwnerTest {
    private class Resource(val id: String) { var closes = 0 }
    private class Fixture(val scheduler: TestCoroutineScheduler) {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        var model = "A"
        var verifies = 0
        var creates = 0
        var fail = false
        var createFail = false
        var loading: CompletableDeferred<Unit>? = null
        var creating: CompletableDeferred<Unit>? = null
        var disposing: CompletableDeferred<Unit>? = null
        val resources = mutableListOf<Resource>()
        val owner = AssistantPreparedModelOwner(scope,
            loadModel = { verifies++; loading?.await(); if (fail) error("verification failed"); model },
            key = { it },
            create = { id: String -> creates++; creating?.await(); if (createFail) error("native initialization failed"); Resource(id).also { resources.add(it) } },
            dispose = { resource: Resource -> disposing?.await(); resource.closes++ })
        fun finish() { owner.close(); scheduler.runCurrent(); scope.cancel() }
    }

    @Test fun preparationDoesNotAcquireCaptureAndOverlappingRequestsShareInitialization() = runTest {
        val f = Fixture(testScheduler); f.loading = CompletableDeferred()
        val a = f.owner.prepare(); runCurrent(); val b = f.owner.prepare()
        assertSame(a, b); assertEquals(1, f.verifies); assertEquals(0, f.creates)
        f.loading!!.complete(Unit); runCurrent()
        val ready = a.await(); assertEquals(1, f.creates)
        val lease = f.owner.acquire(ready); assertEquals("A", lease.resource.id)
        lease.close(); f.finish(); assertEquals(1, f.resources.single().closes)
    }
    @Test fun cancelledWaiterCannotCancelSharedPreparation() = runTest {
        val f = Fixture(testScheduler); f.loading = CompletableDeferred()
        val ready = f.owner.prepare(); val old = launch { ready.await() }; runCurrent(); old.cancelAndJoin()
        val current = f.owner.prepare(); assertSame(ready, current)
        f.loading!!.complete(Unit); runCurrent(); val lease = f.owner.acquire(current.await())
        assertEquals(1, f.creates); lease.close(); f.finish()
    }
    @Test fun warmRequestReverifiesFilesButReusesNativeResource() = runTest {
        val f = Fixture(testScheduler)
        val first = f.owner.prepare(); runCurrent(); val one = f.owner.acquire(first.await()); one.close()
        val second = f.owner.prepare(); runCurrent(); val two = f.owner.acquire(second.await())
        assertEquals(2, f.verifies); assertEquals(1, f.creates); assertSame(one.resource, two.resource)
        two.close(); f.finish()
    }
    @Test fun onlyOneSessionCanOwnThePreparedNativeEngine() = runTest {
        val f = Fixture(testScheduler); val future = f.owner.prepare(); runCurrent(); val ready = future.await()
        val first = f.owner.acquire(ready)
        assertTrue(runCatching { f.owner.acquire(ready) }.exceptionOrNull() is IllegalStateException)
        first.close(); first.close(); val next = f.owner.acquire(ready); next.close(); f.finish()
        assertEquals(1, f.resources.single().closes)
    }
    @Test fun verificationFailureInvalidatesAndNextExplicitRequestCanRetry() = runTest {
        val f = Fixture(testScheduler); f.fail = true
        val bad = f.owner.prepare(); runCurrent(); assertNotNull(runCatching { bad.await() }.exceptionOrNull())
        assertEquals(0, f.creates); f.fail = false
        val good = f.owner.prepare(); runCurrent(); f.owner.acquire(good.await()).close()
        assertEquals(2, f.verifies); assertEquals(1, f.creates); f.finish()
    }
    @Test fun warmVerificationFailureDisposesCachedWeights() = runTest {
        val f = Fixture(testScheduler); val a = f.owner.prepare(); runCurrent(); a.await()
        f.fail = true; val bad = f.owner.prepare(); runCurrent()
        assertNotNull(runCatching { bad.await() }.exceptionOrNull()); assertEquals(1, f.resources.single().closes)
        f.finish()
    }
    @Test fun memoryPressureDefersReleaseUntilTheOldSessionFinishes() = runTest {
        val f = Fixture(testScheduler); val a = f.owner.prepare(); runCurrent(); val prepared = a.await()
        val lease = f.owner.acquire(prepared); f.owner.invalidate(); runCurrent()
        assertEquals(0, lease.resource.closes)
        assertTrue(runCatching { f.owner.acquire(prepared) }.isFailure)
        lease.close(); runCurrent(); assertEquals(1, lease.resource.closes)
        val b = f.owner.prepare(); runCurrent(); f.owner.acquire(b.await()).close()
        assertEquals(2, f.creates); f.finish()
    }
    @Test fun changedModelCannotReuseThePreviousNativeEngine() = runTest {
        val f = Fixture(testScheduler); val a = f.owner.prepare(); runCurrent(); val old = a.await()
        f.model = "B"; val b = f.owner.prepare(); runCurrent(); val current = b.await()
        assertEquals(2, f.creates); assertEquals(1, old.slot.value.closes)
        assertTrue(runCatching { f.owner.acquire(old) }.isFailure)
        f.owner.acquire(current).close(); f.finish()
    }
    @Test fun oldFailureCannotInvalidateANewerModelOwner() = runTest {
        val f = Fixture(testScheduler); val a = f.owner.prepare(); runCurrent(); val old = a.await()
        f.model = "B"; val b = f.owner.prepare(); runCurrent(); val current = b.await()
        f.owner.invalidateIfCurrent(old)
        f.owner.acquire(current).close(); f.finish()
    }
    @Test fun activityDestructionDuringNativeInitializationDisposesLateCompletion() = runTest {
        val f = Fixture(testScheduler); f.creating = CompletableDeferred()
        val old = f.owner.prepare(); runCurrent(); f.owner.close(); f.creating!!.complete(Unit); runCurrent()
        assertTrue(runCatching { old.await() }.exceptionOrNull() is CancellationException)
        assertEquals(1, f.resources.single().closes)
        assertTrue(runCatching { f.owner.prepare() }.isFailure); f.finish()
    }
    @Test fun invalidationDuringInitializationSerializesTheReplacementInitializer() = runTest {
        val f = Fixture(testScheduler); f.creating = CompletableDeferred()
        val old = f.owner.prepare(); runCurrent(); f.owner.invalidate()
        val next = f.owner.prepare(); runCurrent(); assertEquals(1, f.creates)
        f.creating!!.complete(Unit); runCurrent()
        assertTrue(runCatching { old.await() }.isFailure)
        f.owner.acquire(next.await()).close(); assertEquals(2, f.creates)
        assertEquals(1, f.resources.first().closes); f.finish()
    }
    @Test fun closeCannotReleaseWeightsStillOwnedByACapture() = runTest {
        val f = Fixture(testScheduler); val a = f.owner.prepare(); runCurrent()
        val lease = f.owner.acquire(a.await()); f.owner.close(); runCurrent()
        assertEquals(0, lease.resource.closes); lease.close(); runCurrent()
        assertEquals(1, lease.resource.closes); f.finish()
    }
    @Test fun slowNativeDisposalMustFinishBeforeAllocatingReplacementWeights() = runTest {
        val f = Fixture(testScheduler); val a = f.owner.prepare(); runCurrent(); a.await()
        f.disposing = CompletableDeferred(); f.owner.invalidate(); runCurrent()
        val next = f.owner.prepare(); runCurrent()
        assertEquals(1, f.creates); assertFalse(next.isCompleted)
        f.disposing!!.complete(Unit); runCurrent(); f.owner.acquire(next.await()).close()
        assertEquals(2, f.creates); assertEquals(1, f.resources.first().closes); f.finish()
    }
    @Test fun nativeInitializationFailureCanRetryOnlyOnANewPreparationRequest() = runTest {
        val f = Fixture(testScheduler); f.createFail = true
        val failed = f.owner.prepare(); runCurrent(); assertTrue(runCatching { failed.await() }.isFailure)
        assertEquals(1, f.creates); assertEquals(0, f.resources.size)
        f.createFail = false; val next = f.owner.prepare(); runCurrent()
        f.owner.acquire(next.await()).close(); assertEquals(2, f.creates); f.finish()
    }
    @Test fun recreatedActivityUsesANewOwnerAndCannotAcquireAnOldTicket() = runTest {
        val old = Fixture(testScheduler); val first = old.owner.prepare(); runCurrent(); val ticket = first.await()
        old.finish()
        val recreated = Fixture(testScheduler)
        assertTrue(runCatching { recreated.owner.acquire(ticket) }.isFailure)
        val next = recreated.owner.prepare(); runCurrent(); recreated.owner.acquire(next.await()).close()
        assertEquals(1, old.resources.single().closes); assertEquals(1, recreated.creates); recreated.finish()
    }
    @Test fun replacementModelCannotAcquireCaptureUntilRetiredModelsLeaseIsReturned() = runTest {
        val f = Fixture(testScheduler); val first = f.owner.prepare(); runCurrent()
        val oldLease = f.owner.acquire(first.await()); f.model = "B"
        val next = f.owner.prepare(); runCurrent()
        assertFalse(next.isCompleted); assertEquals(1, f.creates)
        oldLease.close(); runCurrent(); val ready = next.await(); val current = f.owner.acquire(ready)
        oldLease.close(); assertTrue(runCatching { f.owner.acquire(ready) }.isFailure)
        current.close(); f.finish()
    }
    @Test fun memoryTrimDoesNotAllocateAnotherModelUntilCaptureAndDisposalComplete() = runTest {
        val f = Fixture(testScheduler); val first = f.owner.prepare(); runCurrent()
        val lease = f.owner.acquire(first.await()); f.disposing = CompletableDeferred()
        f.owner.invalidate(); val next = f.owner.prepare(); runCurrent()
        assertFalse(next.isCompleted); assertEquals(1, f.creates)
        lease.close(); runCurrent(); assertFalse(next.isCompleted); assertEquals(1, f.creates)
        f.disposing!!.complete(Unit); runCurrent(); f.owner.acquire(next.await()).close()
        assertEquals(2, f.creates); f.finish()
    }
}
