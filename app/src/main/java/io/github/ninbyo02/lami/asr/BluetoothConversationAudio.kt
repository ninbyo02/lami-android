package io.github.ninbyo02.lami.asr

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock

internal interface ConversationAudioRoute {
    fun start()

    fun stop()

    companion object {
        val NONE = object : ConversationAudioRoute {
            override fun start() = Unit
            override fun stop() = Unit
        }
    }
}

/** Debug-only Bluetooth call route used to evaluate the real headset microphone. */
internal class BluetoothConversationAudio(context: Context) : ConversationAudioRoute {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val monitor = Any()
    private var focus: AudioFocusRequest? = null
    private var routeOwned = false
    private var modeOwned = false

    override fun start() = synchronized(monitor) {
        if (routeOwned) return
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "Bluetooth communication routing requires Android 12 or newer"
        }
        check(audio.mode == AudioManager.MODE_NORMAL && !audio.isMusicActive) {
            "Bluetooth communication routing requires idle audio"
        }
        val target = audio.availableCommunicationDevices
            .singleOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        checkNotNull(target) { "Bluetooth communication device is unavailable" }

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        try {
            check(audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                "Bluetooth communication focus was denied"
            }
            focus = request
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            modeOwned = true
            check(audio.setCommunicationDevice(target)) {
                "Bluetooth communication route was denied"
            }
            routeOwned = true

            val deadline = SystemClock.elapsedRealtime() + ROUTE_TIMEOUT_MS
            while (audio.communicationDevice?.id != target.id) {
                check(SystemClock.elapsedRealtime() < deadline) {
                    "Bluetooth communication route timed out"
                }
                SystemClock.sleep(ROUTE_POLL_MS)
            }
        } catch (failure: RuntimeException) {
            stopLocked()
            throw failure
        }
    }

    override fun stop() = synchronized(monitor) {
        stopLocked()
    }

    private fun stopLocked() {
        if (routeOwned) runCatching { audio.clearCommunicationDevice() }
        routeOwned = false
        if (modeOwned) runCatching { audio.mode = AudioManager.MODE_NORMAL }
        modeOwned = false
        focus?.let { request -> runCatching { audio.abandonAudioFocusRequest(request) } }
        focus = null
    }

    private companion object {
        const val ROUTE_TIMEOUT_MS = 3_500L
        const val ROUTE_POLL_MS = 50L
    }
}
