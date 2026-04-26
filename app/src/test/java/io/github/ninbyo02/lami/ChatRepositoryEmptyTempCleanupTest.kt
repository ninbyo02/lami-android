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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryEmptyTempCleanupTest {

    @Test
    fun deleteWhenTempPlaceholderAndNoMessages() = runTest {
        val messages = mutableListOf<Message>()
        val chatDao = EmptyChatCleanupFakeChatDao(
            chats = mutableListOf(Chat(chatId = 1, title = " New Chat ", titleSource = TitleSource.TEMP)),
            messages = messages,
        )
        val repository = ChatRepository(EmptyChatCleanupFakeMessageDao(messages), chatDao)

        val deleted = repository.deleteChatIfStillEmptyTempPlaceholder(1)

        assertTrue(deleted)
    }

    @Test
    fun keepTempChatWhenTitleIsNotPlaceholder() = runTest {
        val messages = mutableListOf<Message>()
        val chatDao = EmptyChatCleanupFakeChatDao(
            chats = mutableListOf(Chat(chatId = 1, title = "My Title", titleSource = TitleSource.TEMP)),
            messages = messages,
        )
        val repository = ChatRepository(EmptyChatCleanupFakeMessageDao(messages), chatDao)

        val deleted = repository.deleteChatIfStillEmptyTempPlaceholder(1)

        assertFalse(deleted)
    }

    @Test
    fun keepManualAndAutoSourceChats() = runTest {
        val messages = mutableListOf<Message>()
        val manualDao = EmptyChatCleanupFakeChatDao(
            chats = mutableListOf(Chat(chatId = 1, title = "New chat", titleSource = TitleSource.MANUAL)),
            messages = messages,
        )
        val autoDao = EmptyChatCleanupFakeChatDao(
            chats = mutableListOf(Chat(chatId = 2, title = "New chat", titleSource = TitleSource.AUTO)),
            messages = messages,
        )

        val manualDeleted = ChatRepository(EmptyChatCleanupFakeMessageDao(messages), manualDao)
            .deleteChatIfStillEmptyTempPlaceholder(1)
        val autoDeleted = ChatRepository(EmptyChatCleanupFakeMessageDao(messages), autoDao)
            .deleteChatIfStillEmptyTempPlaceholder(2)

        assertFalse(manualDeleted)
        assertFalse(autoDeleted)
    }

    @Test
    fun keepChatWhenMessageExists() = runTest {
        val messages = mutableListOf(Message(chatId = 1, message = "hello", isSendbyMe = true))
        val chatDao = EmptyChatCleanupFakeChatDao(
            chats = mutableListOf(Chat(chatId = 1, title = "newchat", titleSource = TitleSource.TEMP)),
            messages = messages,
        )
        val repository = ChatRepository(EmptyChatCleanupFakeMessageDao(messages), chatDao)

        val deleted = repository.deleteChatIfStillEmptyTempPlaceholder(1)

        assertFalse(deleted)
    }
}

private class EmptyChatCleanupFakeChatDao(
    chats: MutableList<Chat>,
    private val messages: MutableList<Message>,
) : ChatDao {
    private val chatMap = chats.associateBy { it.chatId }.toMutableMap()
    private val flow = MutableStateFlow(chatMap.values.toList())
    private val placeholderAliases = setOf("new chat", "newchat")

    override suspend fun insertChat(chat: Chat): Long {
        chatMap[chat.chatId] = chat
        flow.value = chatMap.values.toList()
        return chat.chatId.toLong()
    }

    override fun getAllChats(): Flow<List<Chat>> = flow

    override suspend fun getChatById(chatId: Int): Chat? = chatMap[chatId]

    override suspend fun updateChatTitle(chatId: Int, title: String, newSource: String, expectedSource: String): Int = 0

    override suspend fun deleteChatIfStillEmptyTempPlaceholder(chatId: Int, expectedSource: String): Int {
        val target = chatMap[chatId] ?: return 0
        val normalizedTitle = target.title.trim().lowercase()
        val isPlaceholderTitle = normalizedTitle.isEmpty() || normalizedTitle in placeholderAliases
        if (target.titleSource != expectedSource || !isPlaceholderTitle) {
            return 0
        }
        if (messages.any { it.chatId == chatId }) {
            return 0
        }
        chatMap.remove(chatId)
        flow.value = chatMap.values.toList()
        return 1
    }

    override suspend fun deleteEmptyTempPlaceholderChats(expectedSource: String): Int {
        val deleteTargets = chatMap.values.filter { chat ->
            val normalizedTitle = chat.title.trim().lowercase()
            val isPlaceholderTitle = normalizedTitle.isEmpty() || normalizedTitle in placeholderAliases
            chat.titleSource == expectedSource && isPlaceholderTitle && messages.none { it.chatId == chat.chatId }
        }.map { it.chatId }
        deleteTargets.forEach { chatMap.remove(it) }
        flow.value = chatMap.values.toList()
        return deleteTargets.size
    }

    override suspend fun deleteChat(chat: Chat) {
        chatMap.remove(chat.chatId)
        flow.value = chatMap.values.toList()
    }
}

private class EmptyChatCleanupFakeMessageDao(private val messages: MutableList<Message>) : MessageDao {
    override suspend fun insertMessage(message: Message) {
        messages += message
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

    override suspend fun countMessages(chatId: Int): Int {
        return messages.count { it.chatId == chatId }
    }

    override suspend fun getFirstUserMessage(chatId: Int): Message? = null

    override suspend fun getFirstNonEmptyMessage(chatId: Int): Message? = null

    override suspend fun getLatestMessagesByChatIds(chatIds: List<Int>): List<ChatLatestMessage> = emptyList()

    override suspend fun deleteMessage(message: Message) {
        messages.removeIf { it.messageID == message.messageID }
    }
}
