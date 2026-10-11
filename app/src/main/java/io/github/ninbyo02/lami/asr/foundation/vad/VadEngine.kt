package io.github.ninbyo02.lami.asr.foundation.vad

import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame

interface VadEngine : AutoCloseable {
    fun accept(frame: Pcm16Frame): VadEvent

    fun reset()
}

sealed interface VadEvent {
    data object Silence : VadEvent

    data object SpeechStarted : VadEvent

    data object Speech : VadEvent

    data object SpeechEnded : VadEvent
}
