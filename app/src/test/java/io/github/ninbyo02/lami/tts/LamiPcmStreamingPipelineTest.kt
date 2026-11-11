package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LamiPcmStreamingPipelineTest {
    @Test fun slowPlaybackBackpressuresGenerationAndFlushesEos() = runTest {
        val drain = CompletableDeferred<Unit>()
        var emitted = 0
        val heard = mutableListOf<Float>()
        val result = async {
            LamiPcmStreamingPipeline.run(
                frames = { count: Int -> count },
                generate = { emit ->
                    for (count in 2..20) { emit(count); emitted++ }
                    21
                },
                decode = { count -> FloatArray(count) { 0.1f } },
                play = { chunks -> drain.await(); for (chunk in chunks) heard.addAll(chunk.toList()) },
            )
        }
        runCurrent()
        assertTrue(emitted in 1..5)
        assertTrue(heard.isEmpty())
        drain.complete(Unit)
        val completed = result.await()
        assertEquals(21, completed.codes)
        assertEquals(21, heard.size)
        assertEquals(completed.pcm.toList(), heard)
    }

    @Test fun liveModePreservesFirstPrefixAndCoalescesStaleDecodesBeforeFinalPcm() = runTest {
        val firstDecode = CompletableDeferred<Unit>()
        val finalDecode = CompletableDeferred<Unit>()
        val decoded = mutableListOf<Int>()
        val heard = mutableListOf<Float>()
        var codesComplete = false
        var pcmComplete = false
        val result = async {
            LamiPcmStreamingPipeline.run(
                frames = { count: Int -> count },
                generate = { emit ->
                    emit(2)
                    for (count in 8..64 step 8) emit(count)
                    65
                },
                decode = { count ->
                    decoded += count
                    if (count == 2) firstDecode.await() else finalDecode.await()
                    FloatArray(count) { it / 100f }
                },
                play = { chunks -> for (chunk in chunks) heard.addAll(chunk.toList()) },
                coalescePendingPrefixes = true,
                onGenerationComplete = { codesComplete = true },
                onPcmComplete = { pcmComplete = true },
            )
        }
        runCurrent()
        assertEquals(listOf(2), decoded)
        assertTrue(codesComplete)
        assertFalse(pcmComplete)

        firstDecode.complete(Unit)
        runCurrent()
        assertEquals(listOf(2, 65), decoded)
        assertEquals(listOf(0f, 0.01f), heard)
        assertFalse(pcmComplete)

        finalDecode.complete(Unit)
        val completed = result.await()
        assertTrue(pcmComplete)
        assertEquals(FloatArray(65) { it / 100f }.toList(), heard)
        assertEquals(completed.pcm.toList(), heard)
    }

    @Test fun eosAtEmittedBoundaryDoesNotDecodeOrPlayFinalPrefixTwice() = runTest {
        val decoded = mutableListOf<Int>()
        val heard = mutableListOf<Float>()
        LamiPcmStreamingPipeline.run(
            frames = { count: Int -> count },
            generate = { emit -> emit(2); emit(8); 8 },
            decode = { count -> decoded += count; FloatArray(count) { 0.1f } },
            play = { chunks -> for (chunk in chunks) heard.addAll(chunk.toList()) },
        )
        assertEquals(listOf(2, 8), decoded)
        assertEquals(8, heard.size)
    }

    @Test fun shortUtteranceWithoutIntermediatePrefixStillFlushesEos() = runTest {
        val decoded = mutableListOf<Int>()
        val heard = mutableListOf<Float>()
        LamiPcmStreamingPipeline.run(
            frames = { count: Int -> count },
            generate = { 2 },
            decode = { count -> decoded += count; FloatArray(count) { 0.1f } },
            play = { chunks -> for (chunk in chunks) heard.addAll(chunk.toList()) },
            coalescePendingPrefixes = true,
        )
        assertEquals(listOf(2), decoded)
        assertEquals(2, heard.size)
    }

    @Test fun malformedPrefixSequenceAndTruncatedEosFailWithoutCompletionSignal() = runTest {
        for (truncated in listOf(false, true)) {
            var pcmComplete = false
            val result = runCatching {
                LamiPcmStreamingPipeline.run(
                    frames = { count: Int -> count },
                    generate = { emit ->
                        emit(4)
                        if (truncated) 2 else { emit(4); 4 }
                    },
                    decode = { count -> FloatArray(count) { 0.1f } },
                    play = { chunks -> for (ignored in chunks) Unit },
                    onPcmComplete = { pcmComplete = true },
                )
            }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertFalse(pcmComplete)
        }
    }

    @Test fun changedCausalPrefixIsRejectedBeforeItsNewSamplesReachPlayback() = runTest {
        val heard = mutableListOf<Float>()
        var pcmComplete = false
        val result = runCatching {
            LamiPcmStreamingPipeline.run(
                frames = { count: Int -> count },
                generate = { emit -> emit(2); 4 },
                decode = { count -> FloatArray(count) { if (count == 2) 0.1f else 0.2f } },
                play = { chunks -> for (chunk in chunks) heard.addAll(chunk.toList()) },
                onPcmComplete = { pcmComplete = true },
            )
        }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(heard.size <= 2)
        assertTrue(heard.all { it == 0.1f })
        assertFalse(pcmComplete)
    }

    @Test fun cancellationJoinsNativeDecoderCleanupBeforeReturning() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        var released = false
        val result = async {
            LamiPcmStreamingPipeline.run(
                frames = { count: Int -> count },
                generate = { emit -> emit(2); awaitCancellation() },
                decode = {
                    try { awaitCancellation() } finally {
                        withContext(NonCancellable) { cleanup.await(); released = true }
                    }
                },
                play = { chunks -> for (ignored in chunks) Unit },
                coalescePendingPrefixes = true,
            )
        }
        runCurrent()
        val barrier = async { result.cancelAndJoin() }
        runCurrent()
        assertFalse(barrier.isCompleted)
        assertFalse(released)
        cleanup.complete(Unit)
        barrier.await()
        assertTrue(released)
    }
    @Test fun numericToleranceIsComparedAgainstEmittedSamplesInsteadOfAccumulatingDrift() = runTest {
        val heard = mutableListOf<Float>()
        val result = runCatching {
            LamiPcmStreamingPipeline.run(
                frames = { count: Int -> count },
                generate = { emit -> emit(2); emit(3); 4 },
                decode = { count -> FloatArray(count) { if (count == 2) 0f else if (count == 3) 6e-6f else 12e-6f } },
                play = { chunks -> for (chunk in chunks) heard.addAll(chunk.toList()) },
                prefixTolerance = LamiVoiceChunkProbeMath.PrefixTolerance(1e-5, 1e-5),
            )
        }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue("The third prefix exceeds the canonical peak bound", heard.size <= 3)
        assertTrue(heard.take(2).all { it == 0f })
    }
}
