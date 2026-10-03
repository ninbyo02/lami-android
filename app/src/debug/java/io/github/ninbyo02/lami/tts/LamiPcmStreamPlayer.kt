package io.github.ninbyo02.lami.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Explicit diagnostic streaming track. Nonblocking writes preserve stop responsiveness. */
internal object LamiPcmStreamPlayer {
    data class Stats(val samples: Int, val underruns: Int)
    suspend fun play(chunks: ReceiveChannel<FloatArray>, onStarted: () -> Unit): Stats {
        val first = chunks.receive()
        require(first.isNotEmpty())
        val minimum = AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        check(minimum > 0)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(24_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minimum, first.size * Float.SIZE_BYTES)).build()
        var submitted = 0
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED)
            track.setStartThresholdInFrames(minOf(first.size, track.bufferSizeInFrames))
            suspend fun write(samples: FloatArray) {
                require(samples.isNotEmpty() && submitted + samples.size <= 256 * 1920)
                require(samples.all { it.isFinite() && it in -1f..1f })
                var offset = 0
                val deadline = SystemClock.elapsedRealtime() + 60_000
                while (offset < samples.size) {
                    currentCoroutineContext().ensureActive()
                    check(SystemClock.elapsedRealtime() < deadline) { "Streaming PCM write timed out" }
                    val count = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                    check(count >= 0) { "Streaming PCM write failed: $count" }
                    if (count == 0) delay(5) else { offset += count; submitted += count }
                }
            }
            write(first)
            track.play()
            // A short final remainder must resume after an underrun even when it
            // contains fewer frames than the initial prebuffer threshold.
            track.setStartThresholdInFrames(1)
            onStarted()
            for (chunk in chunks) write(chunk)
            val deadline = SystemClock.elapsedRealtime() + submitted * 1000L / 24_000 + 5000
            while (track.playbackHeadPosition < submitted) {
                currentCoroutineContext().ensureActive()
                check(SystemClock.elapsedRealtime() < deadline) { "Streaming playback timed out" }
                delay(10)
            }
            return Stats(submitted, track.underrunCount)
        } finally {
            try { if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop() }
            finally { track.release() }
        }
    }
}
