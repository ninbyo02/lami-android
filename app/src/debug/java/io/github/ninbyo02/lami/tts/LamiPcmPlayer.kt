package io.github.ninbyo02.lami.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** One bounded clip. Cancellation always stops and releases the native track. */
internal object LamiPcmPlayer {
    suspend fun play(samples: FloatArray, onStarted: () -> Unit = {}) {
        LamiPcmContract.validate(samples)
        currentCoroutineContext().ensureActive()
        val minimum = AudioTrack.getMinBufferSize(
            LamiPcmContract.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        check(minimum > 0) { "AudioTrack minimum buffer unavailable: $minimum" }
        val prebufferSamples = LamiPcmContract.startupPrebufferSamples(samples.size, minimum)
        val minimumBufferSamples = (minimum + Float.SIZE_BYTES - 1) / Float.SIZE_BYTES
        val bufferSamples = maxOf(prebufferSamples, minimumBufferSamples)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(LamiPcmContract.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSamples * Float.SIZE_BYTES).build()
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack initialization failed" }
            track.setStartThresholdInFrames(minOf(prebufferSamples, track.bufferSizeInFrames))
            suspend fun writeUntil(target: Int, start: Int, writeMode: Int): Int {
                var offset = start
                currentCoroutineContext().ensureActive()
                val deadline = SystemClock.elapsedRealtime() + samples.size * 1000L / LamiPcmContract.SAMPLE_RATE + 5000
                while (offset < target) {
                    currentCoroutineContext().ensureActive()
                    check(SystemClock.elapsedRealtime() < deadline) { "PCM write timed out" }
                    val count = track.write(samples, offset, target - offset, writeMode)
                    check(count >= 0) { "PCM write failed: $count" }
                    if (count == 0) delay(5) else offset += count
                }
                return offset
            }
            var offset = writeUntil(prebufferSamples, 0, AudioTrack.WRITE_BLOCKING)
            currentCoroutineContext().ensureActive()
            track.play()
            onStarted()
            track.setStartThresholdInFrames(1)
            offset = writeUntil(samples.size, offset, AudioTrack.WRITE_NON_BLOCKING)
            check(offset == samples.size)
            val deadline = SystemClock.elapsedRealtime() + samples.size * 1000L / LamiPcmContract.SAMPLE_RATE + 5000
            while (track.playbackHeadPosition < samples.size) {
                currentCoroutineContext().ensureActive()
                check(SystemClock.elapsedRealtime() < deadline) { "PCM playback timed out" }
                delay(20)
            }
        } finally {
            try { if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop() }
            finally { track.release() }
        }
    }
}
