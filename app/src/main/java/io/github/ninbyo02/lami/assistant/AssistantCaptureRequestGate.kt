package io.github.ninbyo02.lami.assistant

import io.github.ninbyo02.lami.asr.foundation.audio.AudioCaptureEvent

/** Invocation ownership and readiness, independent of UI/haptic delivery threads. */
internal class AssistantCaptureRequestGate {
    private var generation = 0L
    private var current: Long? = null
    private var started = false
    private var inputActive = false
    private var frameReceived = false
    private var pulsed = false
    private var captureEnded = false

    @Synchronized fun begin(): Long {
        generation++
        current = generation
        started = false; inputActive = false; frameReceived = false; pulsed = false; captureEnded = false
        return generation
    }
    @Synchronized fun isCurrent(id: Long) = current == id
    @Synchronized fun isCaptureLive(id: Long) = current == id && started && !captureEnded
    @Synchronized fun finish(id: Long) { if (current == id) current = null }
    @Synchronized fun observe(id: Long, event: AudioCaptureEvent) {
        if (current != id || captureEnded) return
        when (event) {
            AudioCaptureEvent.Started -> started = true
            AudioCaptureEvent.InputResumed -> {
                if (!inputActive) frameReceived = false
                inputActive = true
            }
            is AudioCaptureEvent.InputInterrupted -> { inputActive = false; frameReceived = false }
            is AudioCaptureEvent.Level -> if (started && inputActive) frameReceived = true
            AudioCaptureEvent.Stopped, AudioCaptureEvent.Inactive -> {
                captureEnded = true
                started = false; inputActive = false; frameReceived = false
            }
        }
    }
    @Synchronized fun claimPulse(id: Long, foreground: Boolean): Boolean {
        if (current != id || !foreground || pulsed || !started || !inputActive || !frameReceived) return false
        pulsed = true
        return true
    }
}
