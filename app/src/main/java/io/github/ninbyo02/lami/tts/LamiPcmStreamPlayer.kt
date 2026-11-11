package io.github.ninbyo02.lami.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioTimestamp
import android.os.SystemClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/** Bounded PCM playback shared by selected local engines. */
internal object LamiPcmStreamPlayer {
    data class Stats(val samples: Int, val underruns: Int)
    suspend fun play(context: Context, chunks: ReceiveChannel<FloatArray>, onStarted: () -> Unit): Stats =
        play(context, chunks, onStarted, {})

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun play(
        context: Context,
        chunks: ReceiveChannel<FloatArray>,
        onStarted: () -> Unit,
        onProgress: (TtsStreamingProgress) -> Unit,
        prebuffer: LamiPcmPrebufferConfig? = null,
        stopOnEos: Boolean = false,
        onSnapshot: ((LamiPcmPlaybackSnapshot) -> Unit)? = null,
        eosNowNs: () -> Long = System::nanoTime,
    ): Stats {
        fun received(chunk: FloatArray) {
            require(chunk.isNotEmpty())
            onProgress(TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, sampleCount = chunk.size,
                timestampNs = SystemClock.elapsedRealtimeNanos()))
        }
        val first = if (prebuffer == null) chunks.receive().also(::received)
            else LamiPcmPrebuffer.collect(chunks, prebuffer, SystemClock::elapsedRealtime, ::received)
        val minimum = AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        check(minimum > 0)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(24_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            // The entire startup reserve fits before play(). A paused track
            // smaller than this reserve would make nonblocking write stall.
            .setBufferSizeInBytes(maxOf(minimum, first.size * Float.SIZE_BYTES)).build()
        var submitted = 0
        var stoppedForEos = false
        var completed = false
        var output: LamiPcmOutputRoute? = null
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED)
            val route = LamiPcmOutputRoute(context, track).also { output = it }
            val routeFrames = route.prepare()
            fun playbackHead() = (track.playbackHeadPosition - routeFrames).coerceAtLeast(0)
            val underrunSession = LamiPlaybackUnderrunAccounting.session(track.underrunCount)
            fun observe(phase: String, pending: Int = 0, capturedHead: Int? = null) {
                val observer = onSnapshot ?: return
                val head = if (stoppedForEos) null else capturedHead ?: playbackHead()
                val rawCount = track.underrunCount
                observer(LamiPcmPlaybackSnapshot(phase, SystemClock.elapsedRealtimeNanos(),
                    submitted, head, pending, rawCount, underrunSession.observe(rawCount)))
            }
            if (routeFrames == 0) track.setStartThresholdInFrames(minOf(first.size, track.bufferSizeInFrames))
            suspend fun write(samples: FloatArray) {
                require(samples.isNotEmpty() && submitted.toLong() + samples.size <= LamiPcmContract.SAMPLE_RATE * 30L)
                require(samples.all { it.isFinite() && it in -1f..1f })
                var offset = 0
                val deadline = SystemClock.elapsedRealtime() + 60_000
                while (offset < samples.size) {
                    currentCoroutineContext().ensureActive()
                    check(SystemClock.elapsedRealtime() < deadline) { "Streaming PCM write timed out" }
                    observe("write", samples.size - offset)
                    val count = route.mutate {
                        track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                    }
                    check(count >= 0) { "Streaming PCM write failed: $count" }
                    if (count == 0) delay(5) else { offset += count; submitted += count }
                }
                observe("written")
            }
            write(first)
            route.mutate { track.play() }
            val playbackStartedAtNs = SystemClock.elapsedRealtimeNanos()
            onStarted()
            onProgress(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_STARTED, timestampNs = playbackStartedAtNs))
            observe("started")
            // A short final remainder must resume after an underrun even when it
            // contains fewer frames than the initial prebuffer threshold.
            track.setStartThresholdInFrames(1)
            while (true) {
                route.ensureAllowed()
                val next = select<ChannelResult<FloatArray>?> {
                    chunks.onReceiveCatching { it }
                    route.failures.onReceive { route.ensureAllowed(); error("Speech output unavailable") }
                    if (onSnapshot != null) onTimeout(20L) { null }
                }
                if (next == null) { observe("waiting"); continue }
                if (next.isClosed) {
                    next.exceptionOrNull()?.let { throw it }
                    break
                }
                val chunk = next.getOrThrow()
                received(chunk)
                write(chunk)
            }
            observe("source_eos")
            if (stopOnEos) {
                // MODE_STREAM stop drains accepted PCM. Notify EOS before an
                // empty mixer read; after stop the public head resets to zero.
                // Track the remaining frames using a monotonic drain clock,
                // optionally extended by the last presentation timestamp.
                val timestamp = AudioTimestamp()
                val hasTimestamp = runCatching { track.getTimestamp(timestamp) }.getOrDefault(false)
                val head = playbackHead().coerceIn(0, submitted)
                val guardNs = maxOf(40_000_000L,
                    (minimum / Float.SIZE_BYTES * 1_000_000_000L + 23_999L) / 24_000L)
                val drain = LamiPcmEosDrainClock(submitted, head, 24_000, eosNowNs(), guardNs,
                    timestampFrame = if (hasTimestamp) timestamp.framePosition - routeFrames else null,
                    timestampNs = if (hasTimestamp) timestamp.nanoTime else null)
                observe("eos_stop", capturedHead = head)
                route.stopForEos()
                stoppedForEos = true
                while (drain.remainingNs(eosNowNs()) > 0) {
                    currentCoroutineContext().ensureActive()
                    route.ensureAllowed()
                    delay(minOf(10L, (drain.remainingNs(eosNowNs()) + 999_999L) / 1_000_000L).coerceAtLeast(1L))
                }
                observe("eos_drained")
            } else {
                val deadline = SystemClock.elapsedRealtime() + submitted * 1000L / 24_000 + 5000
                while (playbackHead() < submitted) {
                    currentCoroutineContext().ensureActive()
                    route.ensureAllowed()
                    check(SystemClock.elapsedRealtime() < deadline) { "Streaming playback timed out" }
                    delay(10)
                }
                observe("drained")
            }
            route.ensureAllowed()
            val observedUnderruns = underrunSession.observe(track.underrunCount)
            onProgress(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_COMPLETED, sampleCount = submitted,
                timestampNs = SystemClock.elapsedRealtimeNanos(), underruns = observedUnderruns))
            completed = true
            return Stats(submitted, observedUnderruns)
        } finally {
            val release = {
                try {
                    if (!completed) {
                        // Failure and cancellation discard queued speech on every output path.
                        runCatching { track.setVolume(0f) }
                        runCatching { track.pause() }
                        runCatching { track.flush() }
                    }
                    if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
                } finally { track.release() }
            }
            output?.close(release) ?: release()
        }
    }
}
