package io.github.ninbyo02.lami.db.repository

import io.github.ninbyo02.lami.db.dao.ChatDao
import io.github.ninbyo02.lami.db.dao.ChatLatestMessage
import io.github.ninbyo02.lami.db.dao.MessageDao
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.Message
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

    suspend fun insertAssistantMessageAndAutoTitle(message: Message) {
        messageDao.insertMessage(message)
        if (message.isSendbyMe) {
            return
        }

        val chat = chatDao.getChatById(message.chatId) ?: return
        if (chat.titleSource != TitleSource.TEMP) {
            return
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
    }

    suspend fun delete(message: Message) {
        messageDao.deleteMessage(message)
    }

}
