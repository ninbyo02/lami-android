package com.sonusid.ollama.ui.screens.home

import com.sonusid.ollama.db.entity.Chat
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDefaultChatIdTest {

    @Test
    fun resolveDefaultChatId_selectsMaxChatId_whenExplicitIsNull() {
        val chats = listOf(
            Chat(chatId = 5, title = "New chat"),
            Chat(chatId = 2, title = "Old chat"),
        )

        val resolved = resolveDefaultChatId(explicitChatId = null, chats = chats)

        assertEquals(5, resolved)
    }

    @Test
    fun resolveDefaultChatId_isOrderIndependent_whenExplicitIsNull() {
        val chats = listOf(
            Chat(chatId = 2, title = "Old chat"),
            Chat(chatId = 5, title = "New chat"),
        )

        val resolved = resolveDefaultChatId(explicitChatId = null, chats = chats)

        assertEquals(5, resolved)
    }
}
