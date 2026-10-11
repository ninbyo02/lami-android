package io.github.ninbyo02.lami.ui.screens.home

/**
 * Single owner for generation-session state transitions.
 *
 * Persistence stays in [AssistantMessageLifecycleCoordinator]; this controller
 * prevents UI/backend callbacks from inventing their own lifecycle rules.
 */
internal class AssistantGenerationSessionController {
    var session: AssistantGenerationSession? = null
        private set

    fun start(requestId: Long, chatId: Int): AssistantGenerationSession {
        val next = AssistantGenerationSession(requestId = requestId, chatId = chatId)
        session = next
        return next
    }

    fun claimMessage(messageId: Int): AssistantGenerationSession? =
        session?.claimMessage(messageId)?.also { session = it }

    fun setStreamingOwner(owner: AssistantGenerationSession.StreamingOwner): AssistantGenerationSession? =
        session?.withStreamingOwner(owner)?.also { session = it }

    fun beginFinalizing(): AssistantGenerationSession? =
        session?.beginFinalizing()?.also { session = it }

    fun complete(messageId: Int): AssistantGenerationSession? =
        session?.complete(messageId)?.also { session = it }

    fun cancel(): AssistantGenerationSession? =
        session?.cancel()?.also { session = it }

    fun fail(): AssistantGenerationSession? =
        session?.fail()?.also { session = it }

    fun ownedMessageId(uiMessageId: Int?): Int? = uiMessageId ?: session?.messageId

    fun acceptsStreamingUpdate(requestId: Long? = null): Boolean {
        val current = session ?: return true
        return (requestId == null || current.requestId == requestId) &&
            current.acceptsStreamingUpdate
    }

    fun clearTerminal() {
        if (session?.isTerminal == true) session = null
    }
}
