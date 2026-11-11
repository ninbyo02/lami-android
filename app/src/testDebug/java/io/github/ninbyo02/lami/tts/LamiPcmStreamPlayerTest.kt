package io.github.ninbyo02.lami.tts

import android.media.AudioTrack
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
    @org.junit.Before fun normalOutputMode() {
        org.robolectric.RuntimeEnvironment.getApplication()
            .getSystemService(android.media.AudioManager::class.java).ringerMode = android.media.AudioManager.RINGER_MODE_NORMAL
    }

    @Test fun producerGapDefersFinalUnderrunObservationUntilLastChunkDrains() = runTest {
        val first = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val last = floatArrayOf(0.5f, 0.6f)
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        chunks.send(first)
        var stats: LamiPcmStreamPlayer.Stats? = null
        var starts = 0
        val playback = launch {
            stats = LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks) { starts++ }
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
            LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks) { starts++ }
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

    @Test fun cancellationDuringWriteBackpressureDiscardsUnwrittenSamplesAndReleasesTrack() = runTest {
        val first = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val last = floatArrayOf(0.5f, 0.6f)
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        chunks.send(first)
        var stats: LamiPcmStreamPlayer.Stats? = null
        var starts = 0
        val playback = launch {
            stats = LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks) { starts++ }
        }
        advanceUntilIdle()
        val track = PartialWriteAudioTrack.latest
        track.writes.clear()
        track.writes.addAll(listOf(1, 0, 1))
        chunks.send(last)
        chunks.close()
        // Suspend in the zero-write retry delay with one sample still unwritten.
        runCurrent()
        assertTrue(playback.isActive)
        assertEquals((first + last.take(1)).toList(), track.accepted)
        assertEquals(listOf(4, 2, 2, 1, 2, 1), track.requestSizes)
        assertFalse(track.released)

        playback.cancelAndJoin()
        advanceUntilIdle()

        assertTrue(playback.isCancelled)
        assertEquals(null, stats)
        assertEquals(1, starts)
        assertEquals((first + last.take(1)).toList(), track.accepted)
        assertEquals(listOf(4, 2, 2, 1, 2, 1), track.requestSizes)
        assertEquals(listOf(1), track.writes.toList())
        assertEquals(listOf(7), track.underrunReadings)
        assertEquals(0, track.drainHeadReads)
        assertEquals(listOf("stop", "release"), track.cleanup)
        assertTrue(track.released)
    }

    @Test fun cancellationDuringDrainDoesNotReportCompletedUnderrunStats() = runTest {
        val first = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val last = floatArrayOf(0.5f, 0.6f)
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        chunks.send(first)
        var stats: LamiPcmStreamPlayer.Stats? = null
        val playback = launch {
            stats = LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks) {}
        }
        advanceUntilIdle()
        val track = PartialWriteAudioTrack.latest
        // Accept the final chunk in one write so runCurrent reaches the drain delay.
        track.writes.clear()
        track.writes.add(2)
        chunks.send(last)
        chunks.close()
        runCurrent()

        assertTrue(playback.isActive)
        assertEquals((first + last).toList(), track.accepted)
        assertEquals(1, track.drainHeadReads)
        assertFalse(track.drained)
        assertFalse(track.released)

        playback.cancelAndJoin()
        advanceUntilIdle()

        assertTrue(playback.isCancelled)
        assertEquals(null, stats)
        assertEquals(listOf(7), track.underrunReadings)
        assertEquals(1, track.drainHeadReads)
        assertFalse(track.drained)
        assertEquals(listOf("stop", "release"), track.cleanup)
        assertTrue(track.released)
    }

    @Test fun partialAndZeroWritesPreserveSamplesAndObserveUnderrunsAfterDrain() = runTest {
        val first = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val last = floatArrayOf(0.5f, 0.6f)
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        chunks.send(first)
        chunks.send(last)
        chunks.close()
        var starts = 0

        val stats = LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks) { starts++ }
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
            result = runCatching { LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks) { starts++ } }
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

    @Test fun startCallbackPrecedesThresholdUpdateAndPostStartWrites() = runTest {
        val first = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val last = floatArrayOf(0.5f, 0.6f)
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        chunks.send(first)
        chunks.send(last)
        chunks.close()
        var starts = 0

        val stats = LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks) {
            starts++
            val track = PartialWriteAudioTrack.latest
            // Only the initial prebuffer threshold may have been configured here.
            assertEquals(1, track.startThresholds.size)
            assertEquals(first.toList(), track.accepted)
            assertEquals(0, track.drainHeadReads)
            assertFalse(track.released)
        }
        val track = PartialWriteAudioTrack.latest
        assertEquals(1, starts)
        assertEquals(2, track.startThresholds.size)
        assertEquals(1, track.startThresholds.last())
        assertEquals((first + last).toList(), track.accepted)
        assertEquals(LamiPcmStreamPlayer.Stats(samples = 6, underruns = 3), stats)
        assertEquals(listOf("stop", "release"), track.cleanup)
    }

    @Test fun startupReserveWaitsBeyondFirst160msAndPrimesBeforePlayback() = runTest {
        val first = FloatArray(24_000 * 160 / 1000) { (it % 10) / 100f }
        val next = FloatArray(24_000 * 480 / 1000) { -(it % 10) / 100f }
        val tail = FloatArray(48) { 0.2f }
        val reserve = first + next
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        val progress = mutableListOf<TtsStreamingProgress>()
        var starts = 0
        var stats: LamiPcmStreamPlayer.Stats? = null
        val createdBefore = PartialWriteAudioTrack.created
        PartialWriteAudioTrack.nextWrites = listOf(reserve.size, tail.size)
        try {
            chunks.send(first)
            val playback = launch {
                stats = LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks, onStarted = {
                    starts++
                    assertEquals(reserve.toList(), PartialWriteAudioTrack.latest.accepted)
                }, onProgress = progress::add,
                    prebuffer = LamiPcmPrebufferConfig(minimumMs = 640, maximumMs = 640, gapSafetyMarginMs = 0))
            }
            runCurrent()

            assertTrue(playback.isActive)
            assertEquals(0, starts)
            assertEquals(createdBefore, PartialWriteAudioTrack.created)
            assertEquals(listOf(first.size), progress.filter { it.event == TtsStreamEvent.PCM_CHUNK }.map { it.sampleCount })

            chunks.send(next)
            runCurrent()
            val track = PartialWriteAudioTrack.latest
            assertEquals(1, starts)
            assertEquals(reserve.toList(), track.accepted)
            assertFalse(track.released)
            assertEquals(null, stats)

            chunks.send(tail)
            chunks.close()
            playback.join()

            assertEquals((reserve + tail).toList(), track.accepted)
            assertEquals(LamiPcmStreamPlayer.Stats(reserve.size + tail.size, 3), stats)
            assertEquals(listOf(first.size, next.size, tail.size),
                progress.filter { it.event == TtsStreamEvent.PCM_CHUNK }.map { it.sampleCount })
            assertTrue(track.drained)
            assertEquals(listOf("stop", "release"), track.cleanup)
        } finally {
            PartialWriteAudioTrack.nextWrites = null
            chunks.cancel()
        }
    }

    @Test fun shortEosBelowStartupReservePlaysAndDrainsEveryTailSample() = runTest {
        val first = FloatArray(24_000 * 160 / 1000) { (it % 10) / 100f }
        val tail = floatArrayOf(-0.1f, 0f, 0.1f)
        val expected = first + tail
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        val progress = mutableListOf<TtsStreamingProgress>()
        var starts = 0
        PartialWriteAudioTrack.nextWrites = listOf(expected.size)
        try {
            chunks.send(first)
            chunks.send(tail)
            chunks.close()
            val stats = LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks, onStarted = {
                starts++
                assertEquals(expected.toList(), PartialWriteAudioTrack.latest.accepted)
            }, onProgress = progress::add,
                prebuffer = LamiPcmPrebufferConfig(minimumMs = 640, maximumMs = 640, gapSafetyMarginMs = 0))
            val track = PartialWriteAudioTrack.latest

            assertEquals(1, starts)
            assertEquals(LamiPcmStreamPlayer.Stats(expected.size, 3), stats)
            assertEquals(expected.toList(), track.accepted)
            assertEquals(listOf(first.size, tail.size),
                progress.filter { it.event == TtsStreamEvent.PCM_CHUNK }.map { it.sampleCount })
            assertEquals(expected.size, progress.single { it.event == TtsStreamEvent.PLAYBACK_COMPLETED }.sampleCount)
            assertTrue(track.drained)
            assertTrue(track.released)
            assertEquals(listOf("stop", "release"), track.cleanup)
        } finally {
            PartialWriteAudioTrack.nextWrites = null
            chunks.cancel()
        }
    }

    /** Script backpressure and a delayed playback head without any audio device. */
    @Implements(AudioTrack::class)
    class PartialWriteAudioTrack : ShadowAudioTrack() {
        companion object {
            lateinit var latest: PartialWriteAudioTrack
            var created = 0
            var nextWrites: List<Int>? = null
        }

        val accepted = mutableListOf<Float>()
        val requestSizes = mutableListOf<Int>()
        val underrunReadings = mutableListOf<Int>()
        val cleanup = mutableListOf<String>()
        val startThresholds = mutableListOf<Int>()
        var drained = false
        var released = false
        val writes = ArrayDeque(nextWrites ?: listOf(2, 0, 1, 1, 0, 2)).also { nextWrites = null }
        var drainHeadReads = 0
            private set

        init { created++ }

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
            drained = ++drainHeadReads > 1
            return if (drained) accepted.size else accepted.size - 1
        }

        @Implementation
        fun setStartThresholdInFrames(frames: Int): Int {
            startThresholds += frames
            return frames
        }

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
