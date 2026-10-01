package io.github.ninbyo02.lami.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** One bounded clip. Cancellation always stops and releases the native track. */
internal object LamiPcmPlayer {
    suspend fun play(samples: FloatArray, onStarted: () -> Unit = {}) {
        LamiPcmContract.validate(samples)
        currentCoroutineContext().ensureActive()
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(LamiPcmContract.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES).build()
        try {
            // MODE_STATIC has STATE_NO_STATIC_DATA until the first successful write.
            check(track.state != AudioTrack.STATE_UNINITIALIZED) { "AudioTrack initialization failed" }
            var offset = 0
            while (offset < samples.size) {
                currentCoroutineContext().ensureActive()
                val count = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
                check(count > 0) { "PCM write failed: $count" }
                offset += count
            }
            currentCoroutineContext().ensureActive()
            check(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack data initialization failed" }
            track.play()
            onStarted()
            val deadline = android.os.SystemClock.elapsedRealtime() + samples.size * 1000L / LamiPcmContract.SAMPLE_RATE + 5000
            while (track.playbackHeadPosition < samples.size) {
                currentCoroutineContext().ensureActive()
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "PCM playback timed out" }
                delay(20)
            }
        } finally {
            try { if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop() }
            finally { track.release() }
        }
    }
}
