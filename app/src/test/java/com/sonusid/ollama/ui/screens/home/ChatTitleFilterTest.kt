package com.sonusid.ollama.ui.screens.home

import com.sonusid.ollama.db.entity.Chat
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTitleFilterTest {

    @Test
    fun filterChatsByTitle_matchesPartialTitle_caseInsensitive() {
        val chats = listOf(
            Chat(chatId = 1, title = "New chat"),
            Chat(chatId = 2, title = "Android Compose"),
            Chat(chatId = 3, title = "compose tips"),
        )

        val filtered = filterChatsByTitle(chats, "ComPoSe")

        assertEquals(listOf(chats[1], chats[2]), filtered)
    }
}
