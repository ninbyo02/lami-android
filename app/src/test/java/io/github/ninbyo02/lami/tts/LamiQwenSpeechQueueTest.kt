package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LamiQwenSpeechQueueTest {
    private data class Clip(val text: String, val remaining: List<String> = emptyList())

    @Test fun fiveQueuedShortRequestsShareOneBoundedLookaheadAndOnePlaybackOwner() = runTest {
        val startFirst = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val finishSecond = CompletableDeferred<Unit>()
        val prepared = mutableListOf<String>()
        val played = mutableListOf<String>()
        val requests = mutableListOf<LamiQwenSpeechRequest>()
        val busy = mutableListOf<Boolean>()
        val queue = LamiQwenSpeechQueue<Clip>(backgroundScope, busy::add,
            prepareRequest = { requests += it; Clip(it.text) }, remaining = Clip::remaining,
            prepareText = { text, _ -> Clip(text) }, makeReady = { prepared += it.text; it },
            play = { clip, started ->
                played += clip.text
                if (clip.text == "one") startFirst.await()
                started()
                when (clip.text) {
                    "one" -> finishFirst.await()
                    "two" -> finishSecond.await()
                }
                true
            }, onFailure = { throw AssertionError(it) })
        listOf("one", "two", "three", "four", "five").forEach(queue::enqueue)
        runCurrent()
        assertEquals(listOf("one"), prepared)
        startFirst.complete(Unit)
        runCurrent()
        assertEquals(listOf("one", "two"), prepared)
        assertEquals(listOf("one"), played)
        assertEquals(listOf(true), busy)
        finishFirst.complete(Unit)
        runCurrent()
        assertEquals(listOf("one", "two", "three"), prepared)
        assertEquals(listOf("one", "two"), played)
        assertEquals(listOf(true), busy)
        finishSecond.complete(Unit)
        runCurrent()
        assertEquals(listOf("one", "two", "three", "four", "five"), played)
        assertEquals(listOf(true, false), busy)
        assertTrue(requests.all { it.mode == LamiQwenRequestMode.QUEUED_PREPARED })
        assertEquals(listOf(true, false, false, false, false), requests.map { it.startsBurst })
        queue.stop()
    }

    @Test fun nestedLongSegmentsAndFollowingRequestsUseTheSameLookaheadBudget() = runTest {
        val finishFirst = CompletableDeferred<Unit>()
        val finishSecond = CompletableDeferred<Unit>()
        val prepared = mutableListOf<String>()
        val played = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val queue = LamiQwenSpeechQueue<Clip>(backgroundScope, busy::add,
            prepareRequest = { if (it.text == "long") Clip("part1", listOf("part2")) else Clip(it.text) },
            remaining = Clip::remaining, prepareText = { text, _ -> Clip(text) },
            makeReady = { prepared += it.text; it }, play = { clip, started ->
                played += clip.text; started()
                when (clip.text) {
                    "part1" -> finishFirst.await()
                    "part2" -> finishSecond.await()
                }
                true
            }, onFailure = { throw AssertionError(it) })
        queue.replace("long")
        busy.clear() // replace also publishes the preceding stop state.
        queue.enqueue("following")
        runCurrent()
        assertEquals(listOf("part1", "part2"), prepared)
        assertEquals(listOf("part1"), played)
        assertTrue(busy.isEmpty())
        finishFirst.complete(Unit)
        runCurrent()
        assertEquals(listOf("part1", "part2", "following"), prepared)
        assertEquals(listOf("part1", "part2"), played)
        assertTrue(busy.isEmpty())
        finishSecond.complete(Unit)
        runCurrent()
        assertEquals(listOf("part1", "part2", "following"), played)
        assertEquals(listOf(false), busy)
        queue.stop()
    }

    @Test fun sourceIdleDoesNotRetireTheQueueAndLaterTextIsAcceptedOnce() = runTest {
        val requests = mutableListOf<LamiQwenSpeechRequest>()
        val played = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val queue = LamiQwenSpeechQueue<Clip>(backgroundScope, busy::add,
            prepareRequest = { requests += it; Clip(it.text) }, remaining = Clip::remaining,
            prepareText = { text, _ -> Clip(text) }, makeReady = { it },
            play = { clip, started -> played += clip.text; started(); true }, onFailure = { throw AssertionError(it) })
        queue.enqueue("before idle")
        runCurrent()
        assertEquals(listOf(true, false), busy)
        queue.enqueue("after idle")
        runCurrent()
        assertEquals(listOf("before idle", "after idle"), played)
        assertEquals(1, requests.map { it.generation }.distinct().size)
        assertTrue(requests.all { it.startsBurst })
        assertEquals(listOf(true, false, true, false), busy)
        queue.stop()
    }

    @Test fun flushUsesDirectModeAfterJoiningTheOldPrefetchCleanup() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val requests = mutableListOf<LamiQwenSpeechRequest>()
        val played = mutableListOf<String>()
        val queue = LamiQwenSpeechQueue<Clip>(backgroundScope, {},
            prepareRequest = { requests += it; Clip(it.text) }, remaining = Clip::remaining,
            prepareText = { text, _ -> Clip(text) }, makeReady = {
                if (it.text == "prefetch") {
                    try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
                }
                it
            }, play = { clip, started ->
                played += clip.text; started()
                if (clip.text == "old") awaitCancellation()
                true
            }, onFailure = { throw AssertionError(it) })
        queue.enqueue("old")
        queue.enqueue("prefetch")
        queue.enqueue("discard")
        runCurrent()
        queue.replace("direct")
        runCurrent()
        assertEquals(listOf("old"), played)
        assertEquals(listOf("old", "prefetch"), requests.map { it.text })
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("old", "direct"), played)
        assertEquals(LamiQwenRequestMode.DIRECT, requests.last().mode)
        assertTrue(requests.last().startsBurst)
        assertNotEquals(requests.first().generation, requests.last().generation)
        queue.stop()
    }

    @Test fun eosFailureReportsAnErrorAndDiscardsTheFailedOwnersPendingText() = runTest {
        val played = mutableListOf<String>()
        val failures = mutableListOf<Exception>()
        val busy = mutableListOf<Boolean>()
        val queue = LamiQwenSpeechQueue<Clip>(backgroundScope, busy::add,
            prepareRequest = { Clip(it.text) }, remaining = Clip::remaining,
            prepareText = { text, _ -> Clip(text) }, makeReady = { check(it.text != "bad") { "EOS missing" }; it },
            play = { clip, started -> played += clip.text; started(); true }, onFailure = failures::add)
        queue.enqueue("bad")
        queue.enqueue("discard")
        runCurrent()
        assertTrue(played.isEmpty())
        assertEquals(1, failures.size)
        assertTrue(failures.single().message.orEmpty().contains("EOS missing"))
        assertEquals(listOf(true, false), busy)
        queue.enqueue("recovered")
        runCurrent()
        assertEquals(listOf("recovered"), played)
        queue.stop()
    }

    @Test fun immediatePlayersCannotCompleteBeforeBusyOwnershipIsReserved() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val busy = mutableListOf<Boolean>()
        val played = mutableListOf<String>()
        val queue = LamiQwenSpeechQueue<Clip>(scope, busy::add,
            prepareRequest = { Clip(it.text) }, remaining = Clip::remaining,
            prepareText = { text, _ -> Clip(text) }, makeReady = { it },
            play = { clip, started -> played += clip.text; started(); true }, onFailure = { throw AssertionError(it) })
        try {
            queue.enqueue("one")
            queue.enqueue("two")
            assertEquals(listOf("one", "two"), played)
            assertEquals(listOf(true, false, true, false), busy)
        } finally { queue.stop(); scope.cancel() }
    }
}
