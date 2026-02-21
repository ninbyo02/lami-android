package com.sonusid.ollama

import com.sonusid.ollama.db.dao.ChatDao
import com.sonusid.ollama.db.dao.MessageDao
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.db.entity.TitleSource
import com.sonusid.ollama.db.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRepositoryAutoTitleTest {

    @Test
    fun tempTitleIsAutoUpdatedWhenAssistantMessageSaved() = runTest {
        val chatDao = FakeChatDao(
            Chat(chatId = 1, title = "New chat", titleSource = TitleSource.TEMP)
        )
        val messageDao = FakeMessageDao(
            seed = listOf(Message(chatId = 1, message = "最初のユーザーメッセージです", isSendbyMe = true))
        )
        val repository = ChatRepository(messageDao, chatDao)

        repository.insertAssistantMessageAndAutoTitle(
            Message(chatId = 1, message = "assistant response", isSendbyMe = false)
        )

        val updated = chatDao.getChatById(1)
        assertEquals("最初のユーザーメッセージです", updated?.title)
        assertEquals(TitleSource.AUTO, updated?.titleSource)
    }

    @Test
    fun manualTitleIsNotUpdatedByAssistantMessage() = runTest {
        val chatDao = FakeChatDao(
            Chat(chatId = 1, title = "手動タイトル", titleSource = TitleSource.MANUAL)
        )
        val messageDao = FakeMessageDao(
            seed = listOf(Message(chatId = 1, message = "ユーザー文", isSendbyMe = true))
        )
        val repository = ChatRepository(messageDao, chatDao)

        repository.insertAssistantMessageAndAutoTitle(
            Message(chatId = 1, message = "assistant response", isSendbyMe = false)
        )

        val updated = chatDao.getChatById(1)
        assertEquals("手動タイトル", updated?.title)
        assertEquals(TitleSource.MANUAL, updated?.titleSource)
    }
}

private class FakeChatDao(initialChat: Chat) : ChatDao {
    private val chats = mutableMapOf(initialChat.chatId to initialChat)
    private val flow = MutableStateFlow(chats.values.toList())

    override suspend fun insertChat(chat: Chat) {
        chats[chat.chatId] = chat
        flow.value = chats.values.toList()
    }

    override fun getAllChats(): Flow<List<Chat>> = flow

    override suspend fun getChatById(chatId: Int): Chat? = chats[chatId]

    override suspend fun updateChatTitle(chatId: Int, title: String, titleSource: String) {
        val target = chats[chatId] ?: return
        chats[chatId] = target.copy(title = title, titleSource = titleSource)
        flow.value = chats.values.toList()
    }

    override suspend fun deleteChat(chat: Chat) {
        chats.remove(chat.chatId)
        flow.value = chats.values.toList()
    }
}

private class FakeMessageDao(seed: List<Message>) : MessageDao {
    private val messages = seed.toMutableList()

    override suspend fun insertMessage(message: Message) {
        messages += message.copy(messageID = messages.size + 1)
    }

    override fun getAllMessages(chatId: Int): Flow<List<Message>> {
        return MutableStateFlow(messages.filter { it.chatId == chatId })
    }

    override suspend fun getFirstUserMessage(chatId: Int): Message? {
        return messages.firstOrNull { it.chatId == chatId && it.isSendbyMe && it.message.trim().isNotEmpty() }
    }

    override suspend fun getFirstNonEmptyMessage(chatId: Int): Message? {
        return messages.firstOrNull { it.chatId == chatId && it.message.trim().isNotEmpty() }
    }

    override suspend fun deleteMessage(message: Message) {
        messages.removeIf { it.messageID == message.messageID }
    }
}
