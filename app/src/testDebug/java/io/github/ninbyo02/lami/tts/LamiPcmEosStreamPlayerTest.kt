package io.github.ninbyo02.lami.tts

import android.media.AudioTimestamp
import android.media.AudioTrack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAudioTrack

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [LamiPcmEosStreamPlayerTest.ClockedAudioTrack::class])
@OptIn(ExperimentalCoroutinesApi::class)
class LamiPcmEosStreamPlayerTest {
    @org.junit.Before fun normalOutputMode() {
        org.robolectric.RuntimeEnvironment.getApplication()
            .getSystemService(android.media.AudioManager::class.java).ringerMode = android.media.AudioManager.RINGER_MODE_NORMAL
    }

    @After fun resetClock() { ClockedAudioTrack.nowMs = { 0L } }

    @Test fun normalEosStopsOnlyAfterEveryWriteAndWaitsForPendingTailDespiteResetHead() = runTest {
        ClockedAudioTrack.nowMs = { testScheduler.currentTime }
        val first = FloatArray(2400) { 0.1f }
        val tail = FloatArray(2400) { -0.1f }
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        val events = mutableListOf<TtsStreamingProgress>()
        val snapshots = mutableListOf<LamiPcmPlaybackSnapshot>()
        var stats: LamiPcmStreamPlayer.Stats? = null
        chunks.send(first)
        val playback = launch {
            stats = LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks, {}, events::add,
                stopOnEos = true, onSnapshot = snapshots::add,
                eosNowNs = { testScheduler.currentTime * 1_000_000 })
        }
        runCurrent()
        val track = ClockedAudioTrack.latest
        advanceTimeBy(40)
        runCurrent()
        assertTrue(track.stops.isEmpty())
        chunks.send(tail)
        chunks.close()
        runCurrent()

        val stop = track.stops.single()
        assertEquals((first + tail).toList(), track.accepted)
        assertEquals(first.size + tail.size, stop.acceptedFrames)
        assertEquals(960, stop.headFrames)
        assertEquals(0, track.getPlaybackHeadPosition())
        assertTrue(playback.isActive)
        assertFalse(track.released)
        val remainingMs = ((stop.acceptedFrames - stop.headFrames) * 1000L + 23_999) / 24_000
        advanceTimeBy(remainingMs - 1)
        runCurrent()
        assertFalse("stop() must not cause immediate release while the tail still plays", track.released)

        advanceUntilIdle()
        playback.join()
        assertTrue(track.released)
        assertTrue(checkNotNull(track.releasedAtMs) >= stop.atMs + remainingMs)
        assertEquals(LamiPcmStreamPlayer.Stats(first.size + tail.size, 0), stats)
        assertEquals(1, events.count { it.event == TtsStreamEvent.PLAYBACK_COMPLETED })
        assertTrue(snapshots.isNotEmpty())
    }

    @Test fun cancellationDuringStoppedEosDrainFlushesTailWithoutReportingCompletion() = runTest {
        ClockedAudioTrack.nowMs = { testScheduler.currentTime }
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        val events = mutableListOf<TtsStreamingProgress>()
        var completed = false
        chunks.send(FloatArray(4800) { 0.1f })
        chunks.close()
        val playback = launch {
            LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks, {}, events::add,
                stopOnEos = true, eosNowNs = { testScheduler.currentTime * 1_000_000 })
            completed = true
        }
        runCurrent()
        val track = ClockedAudioTrack.latest
        assertEquals(1, track.stops.size)
        assertEquals(AudioTrack.PLAYSTATE_STOPPED, track.getPlayState())
        advanceTimeBy(40)
        runCurrent()
        assertFalse(track.released)

        playback.cancelAndJoin()

        assertTrue(playback.isCancelled)
        assertFalse(completed)
        assertTrue(track.operations.contains("pause"))
        assertTrue(track.operations.contains("flush"))
        assertTrue(track.operations.indexOf("flush") < track.operations.indexOf("release"))
        assertTrue(track.tailDiscarded)
        assertTrue(track.released)
        assertFalse(events.any { it.event == TtsStreamEvent.PLAYBACK_COMPLETED })
    }

    @Test fun failedSourceEosAbortsPendingAudioInsteadOfReportingNormalDrain() = runTest {
        ClockedAudioTrack.nowMs = { testScheduler.currentTime }
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        val events = mutableListOf<TtsStreamingProgress>()
        var result: Result<LamiPcmStreamPlayer.Stats>? = null
        chunks.send(FloatArray(4800) { 0.1f })
        val playback = launch {
            result = runCatching {
                LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks, {}, events::add,
                    stopOnEos = true, eosNowNs = { testScheduler.currentTime * 1_000_000 })
            }
        }
        runCurrent()
        val track = ClockedAudioTrack.latest
        advanceTimeBy(40)
        runCurrent()
        assertTrue(track.stops.isEmpty())
        chunks.close(IllegalStateException("generation failed"))
        runCurrent()
        playback.join()

        assertEquals("generation failed", result?.exceptionOrNull()?.message)
        assertEquals("pause", track.operations.first { it in listOf("stop", "pause", "flush", "release") })
        assertTrue(track.tailDiscarded)
        assertTrue(track.released)
        assertFalse(events.any { it.event == TtsStreamEvent.PLAYBACK_COMPLETED })
    }

    @Test fun realMidstreamUnderrunsRemainInRawCounterSnapshotsAndFinalStats() = runTest {
        ClockedAudioTrack.nowMs = { testScheduler.currentTime }
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        val events = mutableListOf<TtsStreamingProgress>()
        val snapshots = mutableListOf<LamiPcmPlaybackSnapshot>()
        var stats: LamiPcmStreamPlayer.Stats? = null
        chunks.send(FloatArray(2400) { 0.1f })
        val playback = launch {
            stats = LamiPcmStreamPlayer.play(org.robolectric.RuntimeEnvironment.getApplication(), chunks, {}, events::add,
                stopOnEos = true, onSnapshot = snapshots::add,
                eosNowNs = { testScheduler.currentTime * 1_000_000 })
        }
        runCurrent()
        val track = ClockedAudioTrack.latest
        track.extraUnderruns = 5
        advanceTimeBy(40)
        runCurrent()
        chunks.send(FloatArray(2400) { -0.1f })
        chunks.close()
        advanceUntilIdle()
        playback.join()

        assertEquals(LamiPcmStreamPlayer.Stats(4800, 5), stats)
        assertEquals(12, track.rawUnderrunReadings.last()) // Platform baseline 7 plus real 5.
        assertEquals(5, events.single { it.event == TtsStreamEvent.PLAYBACK_COMPLETED }.underruns)
        assertTrue(snapshots.any { it.rawUnderruns == 12 && it.observedUnderruns == 5 })
        assertTrue(track.released)
    }

    @Test fun silentAndVibrateModesRejectSpeechBeforeAnyPcmIsSubmitted() = runTest {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val audio = app.getSystemService(android.media.AudioManager::class.java)
        for (mode in listOf(android.media.AudioManager.RINGER_MODE_SILENT, android.media.AudioManager.RINGER_MODE_VIBRATE)) {
            audio.ringerMode = mode
            val chunks = Channel<FloatArray>(1)
            chunks.send(FloatArray(2400) { 0.1f })
            chunks.close()
            val result = runCatching { LamiPcmStreamPlayer.play(app, chunks) { fail("Silent speech must not start") } }
            assertTrue(result.isFailure)
            assertTrue(ClockedAudioTrack.latest.accepted.isEmpty())
            assertFalse(ClockedAudioTrack.latest.operations.contains("play"))
            assertTrue(ClockedAudioTrack.latest.released)
        }
    }

    @Test fun modeChangeWakesPlayerWaitingForNextChunkAndDiscardsQueuedSpeech() = runTest {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val chunks = Channel<FloatArray>(1)
        chunks.send(FloatArray(2400) { 0.1f })
        var result: Result<LamiPcmStreamPlayer.Stats>? = null
        val playback = launch { result = runCatching { LamiPcmStreamPlayer.play(app, chunks) {} } }
        runCurrent()
        val track = ClockedAudioTrack.latest
        assertTrue(playback.isActive)
        app.getSystemService(android.media.AudioManager::class.java).ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
        app.sendBroadcast(android.content.Intent(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        runCurrent()
        playback.join()
        assertTrue(checkNotNull(result).isFailure)
        assertTrue(track.tailDiscarded)
        assertTrue(track.released)
        chunks.cancel()
    }

    @Test fun modeChangeDuringNormalEosDrainMutesTailAndDoesNotReportCompletion() = runTest {
        ClockedAudioTrack.nowMs = { testScheduler.currentTime }
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val chunks = Channel<FloatArray>(1)
        chunks.send(FloatArray(4800) { 0.1f })
        chunks.close()
        val events = mutableListOf<TtsStreamingProgress>()
        var result: Result<LamiPcmStreamPlayer.Stats>? = null
        val playback = launch {
            result = runCatching { LamiPcmStreamPlayer.play(app, chunks, {}, events::add,
                stopOnEos = true, eosNowNs = { testScheduler.currentTime * 1_000_000 }) }
        }
        runCurrent()
        val track = ClockedAudioTrack.latest
        assertEquals(1, track.stops.size)
        assertFalse(track.released)
        app.getSystemService(android.media.AudioManager::class.java).ringerMode = android.media.AudioManager.RINGER_MODE_VIBRATE
        app.sendBroadcast(android.content.Intent(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        advanceUntilIdle()
        playback.join()
        assertTrue(checkNotNull(result).isFailure)
        assertTrue(track.tailDiscarded)
        assertTrue(track.released)
        assertFalse(events.any { it.event == TtsStreamEvent.PLAYBACK_COMPLETED })
    }

    /** Position reads never advance the clock; stop immediately resets the observable head. */
    @Implements(AudioTrack::class)
    class ClockedAudioTrack : ShadowAudioTrack() {
        data class Stop(val atMs: Long, val acceptedFrames: Int, val headFrames: Int)
        companion object {
            lateinit var latest: ClockedAudioTrack
            var nowMs: () -> Long = { 0L }
        }
        val accepted = mutableListOf<Float>()
        val operations = mutableListOf<String>()
        val stops = mutableListOf<Stop>()
        val rawUnderrunReadings = mutableListOf<Int>()
        var extraUnderruns = 0
        var released = false
        var releasedAtMs: Long? = null
        var tailDiscarded = false
        private var simulatedPlayState = AudioTrack.PLAYSTATE_STOPPED
        private var startedAtMs: Long? = null
        private var pausedHead = 0
        private var resetHead = false

        init { latest = this }

        @Implementation fun write(samples: FloatArray, offset: Int, size: Int, mode: Int): Int {
            assertEquals(AudioTrack.WRITE_NON_BLOCKING, mode)
            check(!released)
            operations += "write:$size"
            accepted.addAll(samples.slice(offset until offset + size))
            return size
        }
        @Implementation fun play() {
            operations += "play"
            if (startedAtMs == null) startedAtMs = nowMs()
            simulatedPlayState = AudioTrack.PLAYSTATE_PLAYING
        }
        @Implementation fun stop() {
            stops += Stop(nowMs(), accepted.size, getPlaybackHeadPosition())
            operations += "stop"
            simulatedPlayState = AudioTrack.PLAYSTATE_STOPPED
            resetHead = true
        }
        @Implementation fun pause() {
            pausedHead = getPlaybackHeadPosition()
            operations += "pause"
            simulatedPlayState = AudioTrack.PLAYSTATE_PAUSED
        }
        @Implementation public override fun flush() {
            operations += "flush"
            tailDiscarded = true
            resetHead = true
        }
        @Implementation fun release() {
            operations += "release"
            released = true
            releasedAtMs = nowMs()
        }
        @Implementation fun getPlayState() = simulatedPlayState
        @Implementation public override fun getPlaybackHeadPosition(): Int {
            if (resetHead) return 0
            if (simulatedPlayState == AudioTrack.PLAYSTATE_PAUSED) return pausedHead
            val start = startedAtMs ?: return 0
            return ((nowMs() - start).coerceAtLeast(0) * 24).coerceAtMost(accepted.size.toLong()).toInt()
        }
        @Implementation fun getTimestamp(timestamp: AudioTimestamp): Boolean = false
        @Implementation fun getUnderrunCount(): Int = (7 + extraUnderruns).also { rawUnderrunReadings += it }
        @Implementation fun setStartThresholdInFrames(frames: Int) = frames
    }
}
