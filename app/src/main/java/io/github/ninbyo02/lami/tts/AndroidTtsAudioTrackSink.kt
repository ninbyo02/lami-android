package io.github.ninbyo02.lami.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Android-engine output only. All writes are nonblocking and cleanup belongs to its worker. */
internal class AndroidTtsAudioTrackSink(
    context: Context,
    private val format: AndroidTtsPcmFormat,
    private val usbDevice: AudioDeviceInfo?,
    private val ensureSourceHealthy: () -> Unit,
    private val onStarted: () -> Unit,
    onRouteFailure: (String) -> Unit = {},
) : AndroidTtsPcmSink {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val minimum = AudioTrack.getMinBufferSize(format.rate,
        if (format.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT).also { check(it > 0) { "Android TTS AudioTrack buffer unavailable" } }
    private val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(format.rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(if (format.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO).build())
        .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(minimum * 2).build()
    private val output = AndroidTtsOutputGuard(silenceImmediately = {
        runCatching { track.pause() }
        runCatching { track.flush() }
    }, onFailure = onRouteFailure)
    private val routeVerified = AtomicBoolean(false)
    private val routingListener = AudioRouting.OnRoutingChangedListener { routing ->
        if (usbDevice != null) {
            val actualId = routing.routedDevice?.id
            if ((actualId != null || routeVerified.get()) && actualId != usbDevice.id) {
                output.fail("USB speech route changed")
            }
        }
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (usbDevice != null && removedDevices.any { it.id == usbDevice.id }) {
                output.fail("USB speech output disconnected")
            }
        }
    }
    private var started = false
    private var framesWritten = 0L
    private var underrunsAtStart = 0
    override val underruns: Int get() = (track.underrunCount - underrunsAtStart).coerceAtLeast(0)

    init {
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Android TTS AudioTrack initialization failed" }
            if (usbDevice != null) check(track.setPreferredDevice(usbDevice)) { "USB output rejected" }
            track.addOnRoutingChangedListener(routingListener, Handler(Looper.getMainLooper()))
            if (usbDevice != null) audio.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
            track.setStartThresholdInFrames(1)
        } catch (failure: Throwable) {
            runCatching { track.removeOnRoutingChangedListener(routingListener) }
            if (usbDevice != null) runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
            track.release()
            throw failure
        }
    }

    private fun ensureAllowed(requireUsbRoute: Boolean = started || routeVerified.get()) {
        output.ensureHealthy()
        ensureSourceHealthy()
        if (usbDevice == null) {
            check(audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) { "Android TTS stopped in silent/vibrate mode" }
        } else {
            check(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.id == usbDevice.id }) { "USB speech output disconnected or changed" }
            if (requireUsbRoute) check(track.routedDevice?.id == usbDevice.id) { "USB speech route changed" }
        }
    }

    override suspend fun write(samples: ShortArray) {
        require(samples.isNotEmpty() && samples.size % format.channels == 0)
        currentCoroutineContext().ensureActive()
        ensureAllowed()
        if (!started && usbDevice != null) verifyUsbRouteWithSilence()
        var offset = 0
        val deadline = SystemClock.elapsedRealtime() + samples.size * 1000L / format.channels / format.rate + 5_000
        while (offset < samples.size) {
            currentCoroutineContext().ensureActive()
            ensureAllowed()
            check(SystemClock.elapsedRealtime() < deadline) { "Android TTS PCM write timed out" }
            val count = output.mutate {
                ensureAllowed()
                track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            }
            check(count >= 0 && count % format.channels == 0) { "Android TTS PCM write failed: $count" }
            if (count == 0) delay(5) else {
                offset += count
                framesWritten += count / format.channels
                if (!started) {
                    output.mutate {
                        ensureAllowed(requireUsbRoute = usbDevice != null)
                        underrunsAtStart = track.underrunCount
                        track.play()
                    }
                    started = true
                    onStarted()
                }
            }
        }
    }

    private suspend fun verifyUsbRouteWithSilence() {
        // Resolve the preferred USB route using silence before submitting private speech.
        val silence = ShortArray(format.rate / 200 * format.channels)
        val written = output.mutate {
            ensureAllowed(requireUsbRoute = false)
            track.write(silence, 0, silence.size, AudioTrack.WRITE_NON_BLOCKING)
        }
        check(written > 0 && written % format.channels == 0) { "USB route preflight write failed" }
        framesWritten += written / format.channels
        output.mutate {
            ensureAllowed(requireUsbRoute = false)
            track.play()
        }
        val deadline = SystemClock.elapsedRealtime() + 1_000
        while (track.routedDevice == null) {
            currentCoroutineContext().ensureActive()
            ensureAllowed(requireUsbRoute = false)
            check(SystemClock.elapsedRealtime() < deadline) { "USB output route unavailable" }
            delay(5)
        }
        ensureAllowed(requireUsbRoute = true)
        routeVerified.set(true)
    }

    override suspend fun drain() {
        check(started) { "Android TTS did not submit audio" }
        val deadline = SystemClock.elapsedRealtime() + framesWritten * 1000L / format.rate + 5_000
        while ((track.playbackHeadPosition.toLong() and 0xffff_ffffL) < framesWritten) {
            currentCoroutineContext().ensureActive()
            ensureAllowed()
            check(SystemClock.elapsedRealtime() < deadline) { "Android TTS playback drain timed out" }
            delay(5)
        }
        ensureAllowed()
    }

    override fun close() {
        output.close {
            runCatching { track.removeOnRoutingChangedListener(routingListener) }
            if (usbDevice != null) runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            track.release()
        }
    }
}
