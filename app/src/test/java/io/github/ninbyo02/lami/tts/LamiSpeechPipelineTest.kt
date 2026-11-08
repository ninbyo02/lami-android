package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LamiSpeechPipelineTest {
    @Test fun `voice input cleanup barrier waits for playback release`() = runTest {
        val release = CompletableDeferred<Unit>()
        var microphoneAllowed = false
        val pipeline = LamiSpeechPipeline(backgroundScope, {}, prepare = { it }, play = {
            try { delay(1000) } finally { withContext(NonCancellable) { release.await() } }
        })
        pipeline.enqueue("previous speech")
        runCurrent()
        val input = async {
            pipeline.stop()
            pipeline.awaitCleanup()
            microphoneAllowed = true
        }
        runCurrent()
        assertFalse(microphoneAllowed)
        release.complete(Unit)
        input.await()
        assertTrue(microphoneAllowed)
    }

    @Test fun `stop before worker starts discards queued speech and preserves replacement busy state`() = runTest {
        val finishPlayback = CompletableDeferred<Unit>()
        val prepared = mutableListOf<String>()
        val played = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val pipeline = LamiSpeechPipeline(backgroundScope, busy::add, prepare = {
            prepared += it
            it
        }, play = {
            played += it
            finishPlayback.await()
        })

        // Cancel before dispatching the worker, then immediately reuse the pipeline.
        pipeline.enqueue("discard first")
        pipeline.enqueue("discard second")
        pipeline.stop()
        pipeline.enqueue("replacement")
        assertTrue(prepared.isEmpty())
        assertTrue(played.isEmpty())
        assertEquals(listOf(true, false, true), busy)

        runCurrent()
        assertEquals(listOf("replacement"), prepared)
        assertEquals(listOf("replacement"), played)
        assertEquals(listOf(true, false, true), busy)

        finishPlayback.complete(Unit)
        runCurrent()
        assertEquals(listOf(true, false, true, false), busy)
    }

    @Test fun `stop of waiting replacement retains cleanup barrier for immediate reuse`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val pipeline = LamiSpeechPipeline(backgroundScope, busy::add, prepare = {
            events += "prepare $it"
            it
        }, play = {
            events += "play $it"
            if (it == "old") try { delay(1000) } finally {
                withContext(NonCancellable) {
                    events += "cleanup started"
                    cleanup.await()
                    events += "cleanup finished"
                }
            }
        })
        pipeline.enqueue("old")
        runCurrent()
        pipeline.replace("discard")
        runCurrent()
        assertEquals(listOf("prepare old", "play old", "cleanup started"), events)

        pipeline.stop()
        pipeline.enqueue("new")
        pipeline.enqueue("next")
        runCurrent()
        assertEquals(listOf("prepare old", "play old", "cleanup started"), events)
        assertEquals(listOf(true, false, true, false, true), busy)

        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("prepare old", "prepare new", "prepare next"),
            events.filter { it.startsWith("prepare ") })
        assertEquals(listOf("play old", "play new", "play next"),
            events.filter { it.startsWith("play ") })
        assertTrue(events.indexOf("cleanup finished") < events.indexOf("prepare new"))
        assertEquals(listOf(true, false, true, false, true, false), busy)
    }

    @Test fun `replacement waits for both player and prefetch cleanup`() = runTest {
        // Either resource can finish cancellation first; both must release before reuse.
        for (playerFinishesFirst in listOf(true, false)) {
            val playerCleanup = CompletableDeferred<Unit>()
            val producerCleanup = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val busy = mutableListOf<Boolean>()
            val pipeline = LamiSpeechPipeline(backgroundScope, busy::add, prepare = {
                events += "prepare $it"
                if (it == "prefetched") try { delay(1000) } finally {
                    withContext(NonCancellable) {
                        events += "producer cleanup started"
                        producerCleanup.await()
                        events += "producer released"
                    }
                }
                it
            }, play = {
                events += "play $it"
                if (it == "old") try { delay(1000) } finally {
                    withContext(NonCancellable) {
                        events += "player cleanup started"
                        playerCleanup.await()
                        events += "player released"
                    }
                }
            })
            pipeline.enqueue("old")
            pipeline.enqueue("prefetched")
            pipeline.enqueue("discard")
            runCurrent()
            assertTrue(events.contains("play old"))
            assertTrue(events.contains("prepare prefetched"))

            pipeline.replace("new")
            runCurrent()
            assertTrue(events.contains("player cleanup started"))
            assertTrue(events.contains("producer cleanup started"))
            assertFalse(events.contains("prepare new"))

            val firstCleanup = if (playerFinishesFirst) playerCleanup else producerCleanup
            val lastCleanup = if (playerFinishesFirst) producerCleanup else playerCleanup
            val firstReleased = if (playerFinishesFirst) "player released" else "producer released"
            val lastReleased = if (playerFinishesFirst) "producer released" else "player released"
            firstCleanup.complete(Unit)
            runCurrent()
            assertTrue(events.contains(firstReleased))
            assertFalse(events.contains(lastReleased))
            assertFalse(events.contains("prepare new"))
            assertEquals(listOf(true, false, true), busy)

            lastCleanup.complete(Unit)
            runCurrent()
            assertTrue(events.indexOf(firstReleased) < events.indexOf("prepare new"))
            assertTrue(events.indexOf(lastReleased) < events.indexOf("prepare new"))
            assertEquals(listOf("prepare old", "prepare prefetched", "prepare new"),
                events.filter { it.startsWith("prepare ") })
            assertEquals(listOf("play old", "play new"), events.filter { it.startsWith("play ") })
            assertEquals(listOf(true, false, true, false), busy)
        }
    }

    @Test fun `stop before first audio discards late prepared clip and waits for cleanup`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val pipeline = LamiSpeechPipeline(backgroundScope, busy::add, prepare = {
            events += "prepare $it"
            if (it == "old") withContext(NonCancellable) {
                cleanup.await()
                events += "prepare cleanup finished"
            }
            it
        }, play = { events += "play $it" })
        pipeline.enqueue("old")
        pipeline.enqueue("discard")
        runCurrent()
        assertEquals(listOf("prepare old"), events)

        pipeline.stop()
        pipeline.enqueue("new")
        runCurrent()
        assertEquals(listOf("prepare old"), events)
        assertEquals(listOf(true, false, true), busy)

        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("prepare old", "prepare cleanup finished", "prepare new", "play new"), events)
        assertEquals(listOf(true, false, true, false), busy)
    }

    @Test fun `prepares the next sentence during playback with exactly one clip ahead`() = runTest {
        val firstPlayed = CompletableDeferred<Unit>()
        val prepared = mutableListOf<String>()
        val played = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val pipeline = LamiSpeechPipeline(backgroundScope, busy::add,
            prepare = { prepared += it; it },
            play = { played += it; if (it == "first") firstPlayed.await() })
        pipeline.enqueue("first")
        pipeline.enqueue("second")
        pipeline.enqueue("third")
        runCurrent()
        assertEquals(listOf("first", "second"), prepared)
        assertEquals(listOf("first"), played)
        assertEquals(listOf(true), busy)
        firstPlayed.complete(Unit)
        runCurrent()
        assertEquals(listOf("first", "second", "third"), played)
        assertEquals(listOf(true, false), busy)
        pipeline.enqueue("after idle")
        runCurrent()
        assertEquals("after idle", played.last())
        assertEquals(listOf(true, false, true, false), busy)
    }

    @Test fun `stop cancels both preparation and playback without playing a prefetched clip`() = runTest {
        val events = mutableListOf<String>()
        val pipeline = LamiSpeechPipeline(backgroundScope, {}, prepare = {
            events += "prepare $it"
            if (it == "second") try { delay(1000) } finally { events += "prepare cancelled" }
            it
        }, play = {
            events += "play $it"
            try { delay(1000) } finally { events += "play cancelled" }
        })
        pipeline.enqueue("first")
        pipeline.enqueue("second")
        runCurrent()
        pipeline.stop()
        runCurrent()
        assertTrue(events.contains("prepare cancelled"))
        assertTrue(events.contains("play cancelled"))
        assertFalse(events.contains("play second"))
    }

    @Test fun `stop discards fully prepared clip waiting for playback and permits reuse`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val prepared = mutableListOf<String>()
        val played = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val pipeline = LamiSpeechPipeline(backgroundScope, busy::add, prepare = {
            prepared += it
            it
        }, play = {
            played += it
            if (it == "active") try { delay(1000) } finally {
                withContext(NonCancellable) { cleanup.await() }
            }
        })
        pipeline.enqueue("active")
        pipeline.enqueue("ready")
        pipeline.enqueue("queued")
        runCurrent()
        // Preparation has returned for ready; its rendezvous send is waiting on the player.
        assertEquals(listOf("active", "ready"), prepared)
        assertEquals(listOf("active"), played)

        pipeline.stop()
        runCurrent()
        assertEquals(listOf(true, false), busy)
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("active", "ready"), prepared)
        assertEquals(listOf("active"), played)
        assertEquals(listOf(true, false), busy)

        pipeline.enqueue("reuse")
        runCurrent()
        assertEquals(listOf("active", "ready", "reuse"), prepared)
        assertEquals(listOf("active", "reuse"), played)
        assertEquals(listOf(true, false, true, false), busy)
    }

    @Test fun `rapid replacement waits for old producer and player cleanup`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val pipeline = LamiSpeechPipeline(backgroundScope, {}, prepare = { events += "prepare $it"; it }, play = {
            events += "play $it"
            if (it == "old") try { delay(1000) } finally {
                withContext(NonCancellable) { cleanup.await(); events += "released" }
            }
        })
        pipeline.enqueue("old")
        pipeline.enqueue("discard")
        runCurrent()
        assertTrue(events.contains("prepare discard"))
        pipeline.replace("superseded")
        runCurrent()
        pipeline.replace("new")
        runCurrent()
        assertFalse(events.contains("prepare new"))
        cleanup.complete(Unit)
        runCurrent()
        assertTrue(events.indexOf("released") < events.indexOf("prepare new"))
        assertTrue(events.contains("play new"))
        assertFalse(events.contains("play discard"))
        assertFalse(events.contains("play superseded"))
    }

    @Test fun `stop during prefetch waits for preparation cleanup before replacement`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val pipeline = LamiSpeechPipeline(backgroundScope, busy::add, prepare = {
            events += "prepare $it"
            if (it == "prefetched") try { delay(1000) } finally {
                withContext(NonCancellable) {
                    events += "prepare cleanup started"
                    cleanup.await()
                    events += "prepare cleanup finished"
                }
            }
            it
        }, play = {
            events += "play $it"
            if (it == "old") delay(1000)
        })
        pipeline.enqueue("old")
        pipeline.enqueue("prefetched")
        pipeline.enqueue("discard")
        runCurrent()
        assertTrue(events.contains("prepare prefetched"))
        assertTrue(events.contains("play old"))

        pipeline.stop()
        runCurrent()
        pipeline.enqueue("new")
        runCurrent()
        assertTrue(events.contains("prepare cleanup started"))
        assertFalse(events.contains("prepare new"))
        assertEquals(listOf(true, false, true), busy)

        cleanup.complete(Unit)
        runCurrent()
        assertTrue(events.indexOf("prepare cleanup finished") < events.indexOf("prepare new"))
        assertEquals(listOf("play old", "play new"), events.filter { it.startsWith("play ") })
        assertFalse(events.contains("prepare discard"))
        assertEquals(listOf(true, false, true, false), busy)
    }

    @Test fun `stop discards replacement waiting for cleanup and permits later reuse`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val prepared = mutableListOf<String>()
        val played = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val pipeline = LamiSpeechPipeline(backgroundScope, busy::add, prepare = {
            prepared += it
            it
        }, play = {
            played += it
            if (it == "old") try { delay(1000) } finally {
                withContext(NonCancellable) { cleanup.await() }
            }
        })
        pipeline.enqueue("old")
        runCurrent()
        pipeline.replace("waiting replacement")
        pipeline.enqueue("queued replacement")
        runCurrent()
        assertEquals(listOf("old"), prepared)

        pipeline.stop()
        runCurrent()
        assertEquals(listOf(true, false, true, false), busy)
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("old"), prepared)
        assertEquals(listOf("old"), played)
        assertEquals(listOf(true, false, true, false), busy)

        pipeline.enqueue("after cleanup")
        runCurrent()
        assertEquals(listOf("old", "after cleanup"), prepared)
        assertEquals(listOf("old", "after cleanup"), played)
        assertEquals(listOf(true, false, true, false, true, false), busy)
    }

    @Test fun `enqueue after explicit stop waits for cancelled playback cleanup`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val pipeline = LamiSpeechPipeline(backgroundScope, {}, prepare = {
            events += "prepare $it"
            it
        }, play = {
            events += "play $it"
            if (it == "old") try { delay(1000) } finally {
                withContext(NonCancellable) {
                    events += "cleanup started"
                    cleanup.await()
                    events += "cleanup finished"
                }
            }
        })
        pipeline.enqueue("old")
        runCurrent()
        pipeline.stop()
        runCurrent()
        pipeline.enqueue("new")
        runCurrent()
        assertEquals(listOf("prepare old", "play old", "cleanup started"), events)
        cleanup.complete(Unit)
        runCurrent()
        assertTrue(events.indexOf("cleanup finished") < events.indexOf("prepare new"))
        assertTrue(events.contains("play new"))
    }
}
