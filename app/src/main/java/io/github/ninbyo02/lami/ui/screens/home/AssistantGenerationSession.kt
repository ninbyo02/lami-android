package io.github.ninbyo02.lami.ui.screens.home

/**
 * Owns exactly one assistant message lifecycle for one generation request.
 *
 * Backend generation, UI streaming and TTS are consumers/producers around this state;
 * none of them may create a second assistant row after the session becomes terminal.
 */
internal data class AssistantGenerationSession(
    val requestId: Long,
    val chatId: Int,
    val messageId: Int? = null,
    val phase: Phase = Phase.GENERATING,
    val streamingOwner: StreamingOwner = StreamingOwner.GENERIC_UI,
) {
    enum class StreamingOwner {
        GENERIC_UI,
        NPU_NATIVE,
    }

    enum class Phase {
        GENERATING,
        FINALIZING,
        COMPLETED,
        CANCELLED,
        FAILED,
    }

    val isTerminal: Boolean
        get() = phase == Phase.COMPLETED || phase == Phase.CANCELLED || phase == Phase.FAILED

    val acceptsStreamingUpdate: Boolean
        get() = phase == Phase.GENERATING

    val genericUiOwnsPersistence: Boolean
        get() = streamingOwner == StreamingOwner.GENERIC_UI

    fun withStreamingOwner(owner: StreamingOwner): AssistantGenerationSession = copy(streamingOwner = owner)

    fun claimMessage(messageId: Int): AssistantGenerationSession {
        require(messageId > 0)
        require(this.messageId == null || this.messageId == messageId) {
            "generation session cannot own multiple assistant rows"
        }
        return copy(messageId = messageId)
    }

    fun beginFinalizing(): AssistantGenerationSession {
        require(phase == Phase.GENERATING)
        return copy(phase = Phase.FINALIZING)
    }

    fun complete(messageId: Int): AssistantGenerationSession {
        require(phase == Phase.FINALIZING)
        return claimMessage(messageId).copy(phase = Phase.COMPLETED)
    }

    fun cancel(): AssistantGenerationSession =
        if (isTerminal) this else copy(phase = Phase.CANCELLED)

    fun fail(): AssistantGenerationSession =
        if (isTerminal) this else copy(phase = Phase.FAILED)
}
