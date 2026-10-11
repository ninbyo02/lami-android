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
class LamiSpeechPipelineTest {
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
}
