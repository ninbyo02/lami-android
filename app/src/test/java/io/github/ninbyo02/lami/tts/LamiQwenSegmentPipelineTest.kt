package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LamiQwenSegmentPipelineTest {
    private data class Clip(val text: String, val remaining: List<String> = emptyList())

    @Test fun nextGenerationWaitsForPlaybackStartThenOverlapsWithoutPreparingTwoAhead() = runTest {
        val allowFirstStart = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val finishSecond = CompletableDeferred<Unit>()
        val prepared = mutableListOf<String>()
        val playing = mutableListOf<String>()
        val result = async {
            LamiQwenSegmentPipeline.run(Clip("one", listOf("two", "three")), Clip::remaining,
                prepareText = { Clip(it) }, makeReady = { prepared += it.text; it },
                play = { clip, started ->
                    playing += clip.text
                    if (clip.text == "one") allowFirstStart.await()
                    started()
                    when (clip.text) {
                        "one" -> finishFirst.await()
                        "two" -> finishSecond.await()
                    }
                    true
                })
        }
        runCurrent()
        assertEquals(listOf("one"), prepared)
        allowFirstStart.complete(Unit)
        runCurrent()
        assertEquals(listOf("one", "two"), prepared)
        assertEquals(listOf("one"), playing)
        // A complete second PCM waits at the rendezvous. No third native call
        // can begin while the first audio is playing, even for a fast producer.
        runCurrent()
        assertEquals(listOf("one", "two"), prepared)
        finishFirst.complete(Unit)
        runCurrent()
        assertEquals(listOf("one", "two", "three"), prepared)
        assertEquals(listOf("one", "two"), playing)
        finishSecond.complete(Unit)
        assertTrue(result.await())
        assertEquals(listOf("one", "two", "three"), playing)
    }

    @Test fun failedNextNaturalEosNeverReachesPlaybackAndCurrentValidatedAudioCanFinish() = runTest {
        val finishFirst = CompletableDeferred<Unit>()
        val played = mutableListOf<String>()
        val result = async {
            runCatching {
                LamiQwenSegmentPipeline.run(Clip("one", listOf("bad", "never")), Clip::remaining,
                    prepareText = { Clip(it) }, makeReady = {
                        check(it.text != "bad") { "Natural EOS was not reached" }
                        it
                    }, play = { clip, started -> played += clip.text; started(); finishFirst.await(); true })
            }
        }
        runCurrent()
        assertEquals(listOf("one"), played)
        assertFalse(result.isCompleted)
        finishFirst.complete(Unit)
        assertTrue(result.await().exceptionOrNull()?.message.orEmpty().contains("Natural EOS"))
        assertEquals(listOf("one"), played)
    }

    @Test fun cancellationJoinsPrefetchedNativeCleanupBeforeReturning() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val prepared = mutableListOf<String>()
        var released = false
        val result = async {
            LamiQwenSegmentPipeline.run(Clip("one", listOf("two", "never")), Clip::remaining,
                prepareText = { Clip(it) }, makeReady = {
                    prepared += it.text
                    if (it.text == "two") {
                        try { awaitCancellation() } finally {
                            withContext(NonCancellable) { cleanup.await(); released = true }
                        }
                    }
                    it
                }, play = { _, started -> started(); awaitCancellation() })
        }
        runCurrent()
        assertEquals(listOf("one", "two"), prepared)
        result.cancel()
        runCurrent()
        assertFalse(result.isCompleted)
        assertFalse(released)
        cleanup.complete(Unit)
        result.join()
        assertTrue(released)
        assertEquals(listOf("one", "two"), prepared)
    }

    @Test fun playbackFailureBeforeStartCancelsTheProducerHandshake() = runTest {
        val prepared = mutableListOf<String>()
        val result = LamiQwenSegmentPipeline.run(Clip("one", listOf("never")), Clip::remaining,
            prepareText = { Clip(it) }, makeReady = { prepared += it.text; it }, play = { _, _ -> false })
        assertFalse(result)
        assertEquals(listOf("one"), prepared)
    }

    @Test fun aPrivatePreparationCancellationIsAnErrorRatherThanAnOpenChannelHang() = runTest {
        val failure = runCatching {
            LamiQwenSegmentPipeline.run(Clip("one"), Clip::remaining,
                prepareText = { Clip(it) }, makeReady = { throw kotlinx.coroutines.CancellationException("IPC timeout") },
                play = { _, _ -> fail("No complete PCM exists"); true })
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(generateSequence(failure) { it.cause }.any { it is kotlinx.coroutines.CancellationException })
    }
}
