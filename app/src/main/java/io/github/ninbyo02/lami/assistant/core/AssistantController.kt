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

/** Keeps the selected input/output route alive across listening and response turns. */
interface AssistantConversationAudio {
    suspend fun startConversation()
    suspend fun stopConversation()

    companion object {
        val NONE = object : AssistantConversationAudio {
            override suspend fun startConversation() = Unit
            override suspend fun stopConversation() = Unit
        }
    }
}

enum class AssistantState { IDLE, LISTENING, RESPONDING }

/** Owns the conversation lifecycle without depending on any concrete ASR/TTS engine. */
class AssistantController(
    private val asr: AssistantAsrAdapter,
    private val response: AssistantResponseAdapter,
    private val conversationAudio: AssistantConversationAudio = AssistantConversationAudio.NONE,
) {
    var state: AssistantState = AssistantState.IDLE
        private set
    var conversationActive: Boolean = false
        private set

    suspend fun start() {
        if (state != AssistantState.IDLE) return
        val openedHere = !conversationActive
        try {
            if (openedHere) {
                conversationAudio.startConversation()
                conversationActive = true
            }
            state = AssistantState.LISTENING
            asr.startListening()
        } catch (error: Throwable) {
            state = AssistantState.IDLE
            if (openedHere) {
                runCatching { conversationAudio.stopConversation() }
                conversationActive = false
            }
            throw error
        }
    }

    suspend fun submitTranscript(transcript: String) {
        if (state != AssistantState.LISTENING || transcript.isBlank()) return
        state = AssistantState.RESPONDING
        try {
            asr.stopListening()
            response.respond(transcript)
        } catch (error: Throwable) {
            runCatching { conversationAudio.stopConversation() }
            conversationActive = false
            throw error
        } finally {
            state = AssistantState.IDLE
        }
    }

    suspend fun stop() {
        val previous = state
        state = AssistantState.IDLE
        try {
            when (previous) {
                AssistantState.LISTENING -> asr.stopListening()
                AssistantState.RESPONDING -> response.stopResponse()
                AssistantState.IDLE -> Unit
            }
        } finally {
            if (conversationActive) {
                conversationAudio.stopConversation()
                conversationActive = false
            }
        }
    }
}
