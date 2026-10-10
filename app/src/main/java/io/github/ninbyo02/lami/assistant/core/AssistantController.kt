package io.github.ninbyo02.lami.assistant.core

/** ASR is intentionally replaceable while the native recognizer is under development. */
interface AssistantAsrAdapter {
    suspend fun startListening()
    suspend fun stopListening()
}

interface AssistantResponseAdapter {
    suspend fun respond(transcript: String)
    suspend fun stopResponse()
}

enum class AssistantState { IDLE, LISTENING, RESPONDING }

/** Owns the conversation lifecycle without depending on any concrete ASR/TTS engine. */
class AssistantController(
    private val asr: AssistantAsrAdapter,
    private val response: AssistantResponseAdapter,
) {
    var state: AssistantState = AssistantState.IDLE
        private set

    suspend fun start() {
        if (state != AssistantState.IDLE) return
        state = AssistantState.LISTENING
        try {
            asr.startListening()
        } catch (error: Throwable) {
            state = AssistantState.IDLE
            throw error
        }
    }

    suspend fun submitTranscript(transcript: String) {
        if (state != AssistantState.LISTENING || transcript.isBlank()) return
        state = AssistantState.RESPONDING
        try {
            asr.stopListening()
            response.respond(transcript)
        } finally {
            state = AssistantState.IDLE
        }
    }

    suspend fun stop() {
        val previous = state
        state = AssistantState.IDLE
        when (previous) {
            AssistantState.LISTENING -> asr.stopListening()
            AssistantState.RESPONDING -> response.stopResponse()
            AssistantState.IDLE -> Unit
        }
    }
}
