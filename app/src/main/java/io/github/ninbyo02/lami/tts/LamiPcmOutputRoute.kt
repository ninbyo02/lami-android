package io.github.ninbyo02.lami.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.ninbyo02.lami.BuildConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Applies the same private-output policy to every local PCM player. */
internal class LamiPcmOutputRoute(context: Context, private val track: AudioTrack) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val failed = Channel<Unit>(Channel.CONFLATED)
    val failures: ReceiveChannel<Unit> get() = failed
    private val usbDevice = if (BuildConfig.APPLICATION_ID.endsWith(".assistantprobe")) {
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).singleOrNull {
            (it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE) &&
                it.productName.toString().contains("Jabra Evolve2 40 SE", ignoreCase = true)
        }
    } else null
    private var failure: String? = null
    private var closed = false
    private var routeVerified = false
    private var draining = false
    private var receiverRegistered = false
    private var deviceCallbackRegistered = false
    private var routingListenerRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = synchronized(lock) {
            if (closed) return@synchronized
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                failLocked("Speech output became noisy")
            } else if (usbDevice == null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                failLocked("Speech output is blocked in silent/vibrate mode")
            }
        }
    }
    private val routingListener = AudioRouting.OnRoutingChangedListener { routing ->
        synchronized(lock) {
            if (!closed && usbDevice != null) {
                val actualId = routing.routedDevice?.id
                if (actualId != null && actualId != usbDevice.id ||
                    actualId == null && routeVerified && !draining) {
                    failLocked("USB speech route changed")
                }
            }
        }
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = synchronized(lock) {
            if (!closed && usbDevice != null && removedDevices.any { it.id == usbDevice.id }) {
                failLocked("USB speech output disconnected")
            }
        }
    }

    init {
        try {
            app.registerReceiver(receiver, IntentFilter().apply {
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            }, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            track.addOnRoutingChangedListener(routingListener, handler)
            routingListenerRegistered = true
            if (usbDevice != null) {
                audio.registerAudioDeviceCallback(deviceCallback, handler)
                deviceCallbackRegistered = true
            }
        } catch (error: Exception) {
            synchronized(lock) { unregisterLocked(); closed = true; failed.close() }
            throw error
        }
    }

    /** Returns only the silent preflight frames; the player accounts for this head offset. */
    suspend fun prepare(): Int {
        ensureAllowed()
        val usb = usbDevice ?: return 0
        mutate {
            if (!track.setPreferredDevice(usb)) failLocked("USB speech output rejected")
            checkHealthyLocked()
            track.setStartThresholdInFrames(1)
        }
        val silence = FloatArray(LamiPcmContract.SAMPLE_RATE / 200)
        val deadline = SystemClock.elapsedRealtime() + 1_000
        var written = 0
        var playing = false
        while (written < silence.size) {
            currentCoroutineContext().ensureActive()
            if (SystemClock.elapsedRealtime() >= deadline) abort("USB output preflight write timed out")
            val count = mutate {
                track.write(silence, written, silence.size - written, AudioTrack.WRITE_NON_BLOCKING)
            }
            if (count < 0 || count > silence.size - written) abort("USB output preflight write failed: $count")
            if (count == 0) {
                delay(5)
            } else {
                written += count
                // Even a very small native buffer can begin routing after its
                // first silence frame instead of filling up before play().
                if (!playing) {
                    mutate { track.play() }
                    playing = true
                }
            }
        }
        while (true) {
            currentCoroutineContext().ensureActive()
            val verified = mutate {
                val actual = track.routedDevice
                if (actual != null) {
                    if (actual.id != usb.id) failLocked("USB speech route changed")
                    checkHealthyLocked()
                    routeVerified = true
                    true
                } else false
            }
            if (verified) return written
            if (SystemClock.elapsedRealtime() >= deadline) abort("USB speech output route unavailable")
            delay(5)
        }
    }

    fun ensureAllowed(): Unit = synchronized(lock) { ensureAllowedLocked() }

    fun <T> mutate(action: () -> T): T = synchronized(lock) {
        ensureAllowedLocked()
        action()
    }

    /** AudioTrack may report no routed device while its accepted EOS tail drains. */
    fun stopForEos(): Unit = synchronized(lock) {
        ensureAllowedLocked()
        draining = true
        track.stop()
    }

    fun close(release: () -> Unit): Unit = synchronized(lock) {
        if (!closed) {
            closed = true
            unregisterLocked()
            failed.close()
            release()
        }
    }

    private fun ensureAllowedLocked() {
        checkHealthyLocked()
        if (usbDevice == null) {
            if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                failLocked("Speech output is blocked in silent/vibrate mode")
            }
        } else if (audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.id == usbDevice.id }) {
            failLocked("USB speech output disconnected")
        } else if (routeVerified) {
            val actualId = track.routedDevice?.id
            if (actualId != usbDevice.id && !(draining && actualId == null)) failLocked("USB speech route changed")
        }
        checkHealthyLocked()
    }

    private fun checkHealthyLocked() {
        check(!closed) { "Speech output is closed" }
        failure?.let { throw IllegalStateException(it) }
    }

    private fun abort(reason: String): Nothing = synchronized(lock) {
        failLocked(reason)
        throw IllegalStateException(failure ?: reason)
    }

    private fun failLocked(reason: String) {
        if (closed || failure != null) return
        failure = reason
        // EOS stop still drains accepted audio. Mute regardless of playState,
        // and prevent a concurrent write or play from undoing this abort.
        runCatching { track.setVolume(0f) }
        runCatching { track.pause() }
        runCatching { track.flush() }
        failed.trySend(Unit)
    }

    private fun unregisterLocked() {
        if (receiverRegistered) runCatching { app.unregisterReceiver(receiver) }
        if (deviceCallbackRegistered) runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
        if (routingListenerRegistered) runCatching { track.removeOnRoutingChangedListener(routingListener) }
        receiverRegistered = false
        deviceCallbackRegistered = false
        routingListenerRegistered = false
    }
}
