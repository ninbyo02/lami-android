package io.github.ninbyo02.lami

import io.github.ninbyo02.lami.db.dao.ChatDao
import io.github.ninbyo02.lami.db.dao.ChatLatestMessage
import io.github.ninbyo02.lami.db.dao.MessageDao
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.TitleSource
import io.github.ninbyo02.lami.db.repository.ChatRepository
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
        assertEquals(1, chatDao.lastUpdateRows)
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
        assertEquals(-1, chatDao.lastUpdateRows)
    }

    @Test
    fun tempSourceButNonPlaceholderTitleIsNotUpdated() = runTest {
        val chatDao = FakeChatDao(
            Chat(chatId = 1, title = "My Title", titleSource = TitleSource.TEMP)
        )
        val messageDao = FakeMessageDao(
            seed = listOf(Message(chatId = 1, message = "ユーザー文", isSendbyMe = true))
        )
        val repository = ChatRepository(messageDao, chatDao)

        repository.insertAssistantMessageAndAutoTitle(
            Message(chatId = 1, message = "assistant response", isSendbyMe = false)
        )

        val updated = chatDao.getChatById(1)
        assertEquals("My Title", updated?.title)
        assertEquals(TitleSource.TEMP, updated?.titleSource)
        assertEquals(0, chatDao.lastUpdateRows)
    }

}

private class FakeChatDao(initialChat: Chat) : ChatDao {
    private val chats = mutableMapOf(initialChat.chatId to initialChat)
    private val flow = MutableStateFlow(chats.values.toList())
    var lastUpdateRows: Int = -1
        private set

    private val tempTitleAliases = setOf("new chat", "newchat")

    override suspend fun insertChat(chat: Chat): Long {
        chats[chat.chatId] = chat
        flow.value = chats.values.toList()
        return chat.chatId.toLong()
    }

    override fun getAllChats(): Flow<List<Chat>> = flow

    override suspend fun getChatById(chatId: Int): Chat? = chats[chatId]

    override suspend fun updateChatTitle(chatId: Int, title: String, newSource: String, expectedSource: String): Int {
        val target = chats[chatId]
        if (target == null) {
            lastUpdateRows = 0
            return 0
        }

        val isPlaceholderTitle = target.title.trim().isEmpty() || target.title.trim().lowercase() in tempTitleAliases
        if (target.titleSource != expectedSource || !isPlaceholderTitle) {
            lastUpdateRows = 0
            return 0
        }

        chats[chatId] = target.copy(title = title, titleSource = newSource)
        flow.value = chats.values.toList()
        lastUpdateRows = 1
        return 1
    }

    override suspend fun deleteChat(chat: Chat) {
        chats.remove(chat.chatId)
        flow.value = chats.values.toList()
    }

    override suspend fun deleteChatIfStillEmptyTempPlaceholder(chatId: Int, expectedSource: String): Int {
        val target = chats[chatId] ?: return 0
        val isPlaceholderTitle = target.title.trim().isEmpty() || target.title.trim().lowercase() in tempTitleAliases
        if (target.titleSource != expectedSource || !isPlaceholderTitle) {
            return 0
        }
        chats.remove(chatId)
        flow.value = chats.values.toList()
        return 1
    }

    override suspend fun deleteEmptyTempPlaceholderChats(expectedSource: String): Int {
        val targets = chats.values.filter {
            val normalizedTitle = it.title.trim().lowercase()
            val isPlaceholderTitle = normalizedTitle.isEmpty() || normalizedTitle in tempTitleAliases
            it.titleSource == expectedSource && isPlaceholderTitle
        }.map { it.chatId }
        targets.forEach { chats.remove(it) }
        flow.value = chats.values.toList()
        return targets.size
    }
}

private class FakeMessageDao(seed: List<Message>) : MessageDao {
    private val messages = seed.toMutableList()

    override suspend fun insertMessage(message: Message) {
        messages += message.copy(messageID = messages.size + 1)
    }

    override suspend fun insertMessageAndReturnId(message: Message): Long {
        insertMessage(message)
        return 1L
    }

    override fun getAllMessages(chatId: Int): Flow<List<Message>> {
        return MutableStateFlow(messages.filter { it.chatId == chatId })
    }

    override suspend fun getMessageById(messageId: Int): Message? = null

    override suspend fun updateMessage(message: Message) = Unit

    override suspend fun getFirstUserMessage(chatId: Int): Message? {
        return messages.firstOrNull { it.chatId == chatId && it.isSendbyMe && it.message.trim().isNotEmpty() }
    }

    override suspend fun getFirstNonEmptyMessage(chatId: Int): Message? {
        return messages.firstOrNull { it.chatId == chatId && it.message.trim().isNotEmpty() }
    }

    override suspend fun getLatestMessagesByChatIds(chatIds: List<Int>): List<ChatLatestMessage> {
        return chatIds.mapNotNull { chatId ->
            val latest = messages
                .withIndex()
                .filter { it.value.chatId == chatId }
                .maxWithOrNull(compareBy<IndexedValue<Message>> { it.value.messageID }.thenBy { it.index })
                ?.value

            latest?.let { ChatLatestMessage(chatId = it.chatId, message = it.message) }
        }
    }

    override suspend fun countMessages(chatId: Int): Int {
        return messages.count { it.chatId == chatId }
    }

    override suspend fun deleteMessage(message: Message) {
        messages.removeIf { it.messageID == message.messageID }
    }
}
