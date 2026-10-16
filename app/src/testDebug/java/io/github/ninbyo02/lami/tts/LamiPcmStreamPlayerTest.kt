package io.github.ninbyo02.lami.tts

import android.media.AudioTrack
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAudioTrack

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [LamiPcmStreamPlayerTest.PartialWriteAudioTrack::class])
class LamiPcmStreamPlayerTest {
    @Test fun producerGapDefersFinalUnderrunObservationUntilLastChunkDrains() = runTest {
        val first = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val last = floatArrayOf(0.5f, 0.6f)
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        chunks.send(first)
        var stats: LamiPcmStreamPlayer.Stats? = null
        var starts = 0
        val playback = launch {
            stats = LamiPcmStreamPlayer.play(chunks) { starts++ }
        }
        advanceUntilIdle()
        val track = PartialWriteAudioTrack.latest

        assertTrue(playback.isActive)
        assertEquals(null, stats)
        assertEquals(first.toList(), track.accepted)
        assertEquals(listOf(7), track.underrunReadings)
        assertFalse(track.drained)
        assertFalse(track.released)

        chunks.send(last)
        chunks.close()
        playback.join()

        assertEquals(LamiPcmStreamPlayer.Stats(samples = 6, underruns = 3), stats)
        assertEquals(1, starts)
        assertEquals((first + last).toList(), track.accepted)
        assertEquals(listOf(7, 10), track.underrunReadings)
        assertTrue(track.drained)
        assertEquals(listOf("stop", "release"), track.cleanup)
    }

    @Test fun cancellationWhileAwaitingNextChunkStopsAndReleasesTrack() = runTest {
        val first = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        chunks.send(first)
        var starts = 0
        var completed = false
        val playback = launch {
            LamiPcmStreamPlayer.play(chunks) { starts++ }
            completed = true
        }
        advanceUntilIdle()
        val track = PartialWriteAudioTrack.latest
        assertEquals(1, starts)
        assertEquals(first.toList(), track.accepted)
        assertTrue(playback.isActive)
        assertFalse(track.released)

        playback.cancelAndJoin()
        chunks.send(floatArrayOf(0.5f, 0.6f))
        chunks.close()
        advanceUntilIdle()

        assertTrue(playback.isCancelled)
        assertFalse(completed)
        assertEquals(first.toList(), track.accepted)
        assertEquals(1, starts)
        assertEquals(listOf("stop", "release"), track.cleanup)
        assertFalse(track.drained)
    }

    @Test fun partialAndZeroWritesPreserveSamplesAndObserveUnderrunsAfterDrain() = runTest {
        val first = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val last = floatArrayOf(0.5f, 0.6f)
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        chunks.send(first)
        chunks.send(last)
        chunks.close()
        var starts = 0

        val stats = LamiPcmStreamPlayer.play(chunks) { starts++ }
        val track = PartialWriteAudioTrack.latest

        assertEquals(1, starts)
        assertEquals(6, stats.samples)
        assertEquals(3, stats.underruns) // Platform baseline 7, final count 10.
        assertEquals((first + last).toList(), track.accepted)
        assertEquals(listOf(4, 2, 2, 1, 2, 2), track.requestSizes)
        assertEquals(listOf(7, 10), track.underrunReadings)
        assertTrue(track.drained)
        assertTrue(track.released)
    }

    @Test fun writeFailureAfterPartialProgressDoesNotReportCompletedPlayback() = runTest {
        val first = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val last = floatArrayOf(0.5f, 0.6f)
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        chunks.send(first)
        var result: Result<LamiPcmStreamPlayer.Stats>? = null
        var starts = 0
        val playback = launch {
            result = runCatching { LamiPcmStreamPlayer.play(chunks) { starts++ } }
        }
        advanceUntilIdle()
        val track = PartialWriteAudioTrack.latest
        track.writes.clear()
        track.writes.addAll(listOf(1, AudioTrack.ERROR_INVALID_OPERATION))

        chunks.send(last)
        chunks.close()
        playback.join()

        val failure = result!!.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("Streaming PCM write failed: ${AudioTrack.ERROR_INVALID_OPERATION}", failure?.message)
        assertEquals(1, starts)
        assertEquals((first + last.take(1)).toList(), track.accepted)
        assertEquals(listOf(4, 2, 2, 1, 2, 1), track.requestSizes)
        assertEquals(listOf(7), track.underrunReadings)
        assertFalse(track.drained)
        assertEquals(listOf("stop", "release"), track.cleanup)
        assertTrue(track.released)
    }

    /** Script backpressure and a delayed playback head without any audio device. */
    @Implements(AudioTrack::class)
    class PartialWriteAudioTrack : ShadowAudioTrack() {
        companion object {
            lateinit var latest: PartialWriteAudioTrack
        }

        val accepted = mutableListOf<Float>()
        val requestSizes = mutableListOf<Int>()
        val underrunReadings = mutableListOf<Int>()
        val cleanup = mutableListOf<String>()
        var drained = false
        var released = false
        val writes = ArrayDeque(listOf(2, 0, 1, 1, 0, 2))
        private var headReads = 0

        @Implementation
        fun write(samples: FloatArray, offset: Int, size: Int, mode: Int): Int {
            latest = this
            assertEquals(AudioTrack.WRITE_NON_BLOCKING, mode)
            requestSizes += size
            val count = writes.removeFirst()
            if (count < 0) return count
            assertTrue(count <= size)
            accepted.addAll(samples.slice(offset until offset + count))
            return count
        }

        @Implementation
        fun getUnderrunCount(): Int {
            // Reading before the drain finishes would miss the last two events.
            val count = if (drained) 10 else if (accepted.isEmpty()) 7 else 8
            underrunReadings += count
            return count
        }

        @Implementation
        public override fun getPlaybackHeadPosition(): Int {
            assertTrue(writes.isEmpty())
            drained = ++headReads > 1
            return if (drained) accepted.size else accepted.size - 1
        }

        @Implementation
        fun setStartThresholdInFrames(frames: Int): Int = frames

        @Implementation
        fun stop() {
            cleanup += "stop"
        }

        @Implementation
        fun release() {
            cleanup += "release"
            released = true
        }
    }
}
