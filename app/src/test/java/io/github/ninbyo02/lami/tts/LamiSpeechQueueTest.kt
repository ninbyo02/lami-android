package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LamiSpeechQueueTest {
    @Test fun `queued text never interrupts active utterance and keeps busy across sentences`() = runTest {
        val firstDone = CompletableDeferred<Unit>()
        val heard = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val queue = LamiSpeechQueue(backgroundScope, busy::add) {
            heard += it
            if (it == "first") firstDone.await()
        }
        queue.enqueue("first")
        runCurrent()
        queue.enqueue("second")
        queue.enqueue("third")
        runCurrent()
        assertEquals(listOf("first"), heard)
        assertEquals(listOf(true), busy)
        firstDone.complete(Unit)
        runCurrent()
        assertEquals(listOf("first", "second", "third"), heard)
        assertEquals(listOf(true, false), busy)
    }

    @Test fun `stop cancels generation and discards all pending sentences`() = runTest {
        val heard = mutableListOf<String>()
        val queue = LamiSpeechQueue(backgroundScope, {}) { heard += it; delay(1000) }
        queue.enqueue("old")
        queue.enqueue("discard")
        runCurrent()
        queue.stop()
        runCurrent()
        queue.enqueue("new")
        runCurrent()
        assertEquals(listOf("old", "new"), heard)
    }

    @Test fun `replacement waits for native playback cleanup and ignores old state callbacks`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val queue = LamiSpeechQueue(backgroundScope, busy::add) {
            events += it
            if (it == "old") try { delay(1000) } finally {
                withContext(NonCancellable) { cleanup.await(); events += "released" }
            }
        }
        queue.enqueue("old")
        queue.enqueue("discard")
        runCurrent()
        queue.replace("superseded")
        runCurrent()
        queue.replace("new")
        runCurrent()
        assertEquals(listOf("old"), events)
        assertEquals(true, busy.last())
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("old", "released", "new"), events)
        assertEquals(listOf(true, false, true, false, true, false), busy)
    }

    @Test fun `blank replacement does not cancel active speech`() = runTest {
        val heard = mutableListOf<String>()
        val queue = LamiSpeechQueue(backgroundScope, {}) { heard += it; delay(1000) }
        queue.enqueue("first")
        runCurrent()
        queue.replace("  ")
        queue.enqueue("second")
        testScheduler.advanceTimeBy(1001)
        runCurrent()
        assertEquals(listOf("first", "second"), heard)
    }
}
