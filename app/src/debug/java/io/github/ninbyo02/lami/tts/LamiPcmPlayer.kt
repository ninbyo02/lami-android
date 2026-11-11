package io.github.ninbyo02.lami.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** One bounded clip. Cancellation always stops and releases the native track. */
internal object LamiPcmPlayer {
    suspend fun play(context: Context, samples: FloatArray, onStarted: () -> Unit = {}) {
        LamiPcmContract.validate(samples)
        currentCoroutineContext().ensureActive()
        val minimum = AudioTrack.getMinBufferSize(
            LamiPcmContract.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        check(minimum > 0) { "AudioTrack minimum buffer unavailable: $minimum" }
        val prebufferPlan = LamiPcmContract.startupPrebufferPlan(samples.size, minimum)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(LamiPcmContract.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(prebufferPlan.bufferSamples * Float.SIZE_BYTES).build()
        var output: LamiPcmOutputRoute? = null
        var completed = false
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack initialization failed" }
            val route = LamiPcmOutputRoute(context, track).also { output = it }
            val routeFrames = route.prepare()
            if (routeFrames == 0) track.setStartThresholdInFrames(minOf(prebufferPlan.prebufferSamples, track.bufferSizeInFrames))
            suspend fun writeUntil(target: Int, start: Int, writeMode: Int): Int {
                var offset = start
                currentCoroutineContext().ensureActive()
                val deadline = SystemClock.elapsedRealtime() + samples.size * 1000L / LamiPcmContract.SAMPLE_RATE + 5000
                while (offset < target) {
                    currentCoroutineContext().ensureActive()
                    check(SystemClock.elapsedRealtime() < deadline) { "PCM write timed out" }
                    val count = route.mutate { track.write(samples, offset, target - offset, writeMode) }
                    check(count >= 0) { "PCM write failed: $count" }
                    if (count == 0) delay(5) else offset += count
                }
                return offset
            }
            var offset = writeUntil(prebufferPlan.prebufferSamples, 0, AudioTrack.WRITE_BLOCKING)
            currentCoroutineContext().ensureActive()
            route.mutate { track.play() }
            onStarted()
            track.setStartThresholdInFrames(1)
            offset = writeUntil(samples.size, offset, AudioTrack.WRITE_NON_BLOCKING)
            check(offset == samples.size)
            val deadline = SystemClock.elapsedRealtime() + samples.size * 1000L / LamiPcmContract.SAMPLE_RATE + 5000
            while (track.playbackHeadPosition - routeFrames < samples.size) {
                currentCoroutineContext().ensureActive()
                route.ensureAllowed()
                check(SystemClock.elapsedRealtime() < deadline) { "PCM playback timed out" }
                delay(20)
            }
            route.ensureAllowed()
            completed = true
        } finally {
            val release = {
                try {
                    if (!completed) {
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
