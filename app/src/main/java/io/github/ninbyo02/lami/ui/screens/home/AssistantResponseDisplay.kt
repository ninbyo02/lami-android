package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageStatus

internal data class AssistantResponseDisplay(
    val requestId: Long,
    val message: Message,
)

internal fun resolveAssistantResponseDisplay(
    session: AssistantGenerationSession?,
    transientText: String?,
    terminalResponse: AssistantResponseDisplay?,
): AssistantResponseDisplay? {
    session ?: return null
    terminalResponse?.takeIf {
        it.requestId == session.requestId && it.message.chatId == session.chatId
    }?.let { return it }
    if (session.isTerminal || transientText.isNullOrBlank()) return null
    return AssistantResponseDisplay(
        requestId = session.requestId,
        message = Message(
            messageID = session.messageId ?: (Int.MIN_VALUE / 2 + session.chatId),
            chatId = session.chatId,
            message = transientText,
            isSendbyMe = false,
            status = MessageStatus.GENERATING,
        ),
    )
}

/** Room and in-memory text update one ID; equal text in another turn remains distinct. */
internal fun mergeAssistantResponseForDisplay(
    chatId: Int?,
    messages: List<Message>,
    response: AssistantResponseDisplay?,
): List<Message> {
    val candidate = response?.message ?: return messages
    if (chatId == null || candidate.chatId != chatId) return messages
    val index = messages.indexOfFirst {
        !it.isSendbyMe && it.chatId == chatId && it.messageID == candidate.messageID
    }
    if (index < 0) return messages + candidate
    val stored = messages[index]
    if (stored.status in MessageStatus.TERMINAL) return messages
    return messages.toMutableList().also {
        it[index] = stored.copy(message = candidate.message, status = candidate.status)
    }
}
