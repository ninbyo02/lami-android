package com.sonusid.ollama.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class AndroidTtsController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.language = Locale.JAPANESE
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        notifyPlaybackState(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        notifyPlaybackState(false)
                    }

                    override fun onError(utteranceId: String?) {
                        notifyPlaybackState(false)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        notifyPlaybackState(false)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        notifyPlaybackState(false)
                    }
                })
            } else {
                isReady = false
                notifyPlaybackState(false)
            }
        }
    }

    fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) {
        onPlaybackStateChanged = listener
    }

    fun speak(text: String) {
        val normalizedText = text.trim()
        if (!isReady || normalizedText.isEmpty()) {
            return
        }
        runCatching {
            tts?.speak(normalizedText, TextToSpeech.QUEUE_FLUSH, null, nextUtteranceId())
        }.onFailure {
            notifyPlaybackState(false)
        }
    }

    fun stop() {
        runCatching {
            tts?.stop()
        }
        notifyPlaybackState(false)
    }

    fun shutdown() {
        stop()
        runCatching {
            tts?.shutdown()
        }
        tts = null
        isReady = false
    }

    private fun notifyPlaybackState(isPlaying: Boolean) {
        mainHandler.post {
            onPlaybackStateChanged?.invoke(isPlaying)
        }
    }

    private fun nextUtteranceId(): String = "lami-tts-${UUID.randomUUID()}"
}
