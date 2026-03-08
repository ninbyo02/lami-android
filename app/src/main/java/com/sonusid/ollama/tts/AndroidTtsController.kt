package com.sonusid.ollama.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

private const val DEFAULT_TTS_SPEECH_RATE = 0.92f
private const val DEFAULT_TTS_PITCH = 1.18f
private const val TTS_REFERENCE_PHRASE = "こんにちは。ラミィです。今日はどんなお手伝いをしましょうか。"

class AndroidTtsController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingSpeakText: String? = null

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

                val pendingText = pendingSpeakText
                pendingSpeakText = null
                if (!pendingText.isNullOrEmpty()) {
                    speakInternal(pendingText)
                }
            } else {
                isReady = false
                pendingSpeakText = null
                notifyPlaybackState(false)
            }
        }
    }

    fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) {
        onPlaybackStateChanged = listener
    }

    fun speak(text: String) {
        val speechText = SpeechTextBuilder.build(text).trim()
        if (speechText.isEmpty()) {
            return
        }
        if (!isReady) {
            pendingSpeakText = speechText
            return
        }
        speakInternal(speechText)
    }

    fun speakReferencePhrase() {
        speak(TTS_REFERENCE_PHRASE)
    }

    private fun speakInternal(text: String) {
        runCatching {
            tts?.setSpeechRate(DEFAULT_TTS_SPEECH_RATE)
            tts?.setPitch(DEFAULT_TTS_PITCH)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, nextUtteranceId())
        }.onFailure {
            notifyPlaybackState(false)
        }
    }

    fun stop() {
        pendingSpeakText = null
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
        pendingSpeakText = null
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
