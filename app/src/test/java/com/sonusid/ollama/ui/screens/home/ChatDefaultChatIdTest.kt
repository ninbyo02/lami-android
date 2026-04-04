package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Chat
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDefaultChatIdTest {

    @Test
    fun resolveDefaultChatId_returnsNull_whenExplicitIsNull() {
        val chats = listOf(
            Chat(chatId = 5, title = "New chat"),
            Chat(chatId = 2, title = "Old chat"),
        )

        val resolved = resolveDefaultChatId(explicitChatId = null, chats = chats)

        assertEquals(null, resolved)
    }

    @Test
    fun resolveDefaultChatId_returnsExplicitId_whenExplicitIsNotNull() {
        val chats = listOf(
            Chat(chatId = 5, title = "New chat"),
            Chat(chatId = 2, title = "Old chat"),
        )

        val resolved = resolveDefaultChatId(explicitChatId = 42, chats = chats)

        assertEquals(42, resolved)
    }
}
