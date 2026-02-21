package com.sonusid.ollama.db.repository

import com.sonusid.ollama.db.dao.ChatDao
import com.sonusid.ollama.db.dao.ChatLatestMessage
import com.sonusid.ollama.db.dao.MessageDao
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.db.entity.TitleSource
import com.sonusid.ollama.utils.AutoTitleGenerator
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

    suspend fun newChat(chat: Chat) {
        chatDao.insertChat(chat)
    }

    suspend fun deleteChat(chat: Chat) {
        chatDao.deleteChat(chat)
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
