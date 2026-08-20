package io.github.ninbyo02.lami.db.repository

import io.github.ninbyo02.lami.db.dao.ChatDao
import io.github.ninbyo02.lami.db.dao.ChatLatestMessage
import io.github.ninbyo02.lami.db.dao.MessageDao
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageErrorCode
import io.github.ninbyo02.lami.db.entity.MessageStatus
import io.github.ninbyo02.lami.db.entity.TitleSource
import io.github.ninbyo02.lami.utils.AutoTitleGenerator
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val messageDao: MessageDao, private val chatDao: ChatDao) {

    val allChats: Flow<List<Chat>> = chatDao.getAllChats()

    fun getMessages(chatId: Int) = messageDao.getAllMessages(chatId)

    suspend fun getLatestMessagesByChatIds(chatIds: List<Int>): List<ChatLatestMessage> {
        if (chatIds.isEmpty()) {
            return emptyList()
        }
        return messageDao.getLatestMessagesByChatIds(chatIds)
    }

    suspend fun newChat(chat: Chat): Int {
        return chatDao.insertChat(chat).toInt()
    }

    suspend fun deleteChat(chat: Chat) {
        chatDao.deleteChat(chat)
    }


    suspend fun deleteChatIfStillEmptyTempPlaceholder(chatId: Int): Boolean {
        if (messageDao.countMessages(chatId) != 0) {
            return false
        }
        val deletedRows = chatDao.deleteChatIfStillEmptyTempPlaceholder(
            chatId = chatId,
            expectedSource = TitleSource.TEMP,
        )
        return deletedRows == 1
    }

    suspend fun cleanupEmptyTempPlaceholderChats(): Int {
        return chatDao.deleteEmptyTempPlaceholderChats(expectedSource = TitleSource.TEMP)
    }

    suspend fun insert(message: Message) {
        messageDao.insertMessage(message)
    }

    suspend fun getMessageById(messageId: Int): Message? {
        return messageDao.getMessageById(messageId)
    }

    suspend fun updateMessage(message: Message) {
        messageDao.updateMessage(message)
    }

    suspend fun markAssistantMessageGenerating(
        messageId: Int,
        updatedAtEpochMs: Long = System.currentTimeMillis(),
    ): Boolean = transitionAssistantMessage(
        messageId = messageId,
        expectedStatuses = listOf(MessageStatus.PENDING),
        newStatus = MessageStatus.GENERATING,
        errorCode = null,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    suspend fun completeAssistantMessage(
        messageId: Int,
        message: String,
        updatedAtEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        return messageDao.completeInFlightAssistantMessage(
            messageId = messageId,
            message = message,
            updatedAtEpochMs = updatedAtEpochMs,
        ) == 1
    }

    suspend fun cancelAssistantMessage(
        messageId: Int,
        updatedAtEpochMs: Long = System.currentTimeMillis(),
    ): Boolean = transitionAssistantMessage(
        messageId = messageId,
        expectedStatuses = MessageStatus.IN_FLIGHT.toList(),
        newStatus = MessageStatus.CANCELLED,
        errorCode = MessageErrorCode.USER_CANCELLED,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    suspend fun failAssistantMessage(
        messageId: Int,
        message: String? = null,
        errorCode: String = MessageErrorCode.GENERATION_FAILED,
        updatedAtEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        return messageDao.failInFlightAssistantMessage(
            messageId = messageId,
            message = message,
            errorCode = errorCode.ifBlank { MessageErrorCode.GENERATION_FAILED },
            updatedAtEpochMs = updatedAtEpochMs,
        ) == 1
    }

    suspend fun updateGeneratingAssistantMessageContent(
        messageId: Int,
        message: String,
        updatedAtEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        return messageDao.updateAssistantMessageContentIfStatus(
            messageId = messageId,
            expectedStatus = MessageStatus.GENERATING,
            message = message,
            updatedAtEpochMs = updatedAtEpochMs,
        ) == 1
    }

    suspend fun interruptInFlightAssistantMessagesAfterRestart(
        processStartedAtEpochMs: Long,
        updatedAtEpochMs: Long = System.currentTimeMillis(),
    ): Int {
        return messageDao.interruptInFlightAssistantMessagesAfterRestart(
            processStartedAtEpochMs = processStartedAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }

    private suspend fun transitionAssistantMessage(
        messageId: Int,
        expectedStatuses: List<String>,
        newStatus: String,
        errorCode: String?,
        updatedAtEpochMs: Long,
    ): Boolean {
        return messageDao.transitionAssistantMessageStatus(
            messageId = messageId,
            expectedStatuses = expectedStatuses,
            newStatus = newStatus,
            errorCode = errorCode,
            updatedAtEpochMs = updatedAtEpochMs,
        ) == 1
    }

    suspend fun insertAssistantMessageAndAutoTitleAndReturnId(message: Message): Long {
        require(!message.isSendbyMe) {
            "insertAssistantMessageAndAutoTitleAndReturnId accepts assistant messages only"
        }
        val insertedId = messageDao.insertMessageAndReturnId(message)
        val chat = chatDao.getChatById(message.chatId) ?: return insertedId
        if (chat.titleSource != TitleSource.TEMP) {
            return insertedId
        }

        val seedMessage = messageDao.getFirstUserMessage(message.chatId)?.message
            ?: messageDao.getFirstNonEmptyMessage(message.chatId)?.message

        val generatedTitle = AutoTitleGenerator.generateTitle(seedMessage)
        chatDao.updateChatTitle(
            chatId = message.chatId,
            title = generatedTitle,
            newSource = TitleSource.AUTO,
            expectedSource = TitleSource.TEMP
        )
        return insertedId
    }

    suspend fun insertAssistantMessageAndAutoTitle(message: Message) {
        insertAssistantMessageAndAutoTitleAndReturnId(message)
    }

    suspend fun delete(message: Message) {
        messageDao.deleteMessage(message)
    }

}
