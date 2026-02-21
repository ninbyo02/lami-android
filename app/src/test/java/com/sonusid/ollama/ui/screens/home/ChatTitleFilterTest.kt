package com.sonusid.ollama.ui.screens.home

import com.sonusid.ollama.db.entity.Chat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun formatChatPreview_normalizesTrimAndTruncates() {
        val longMessage = " 1行目\n2行目\r\n" + "a".repeat(90) + " "

        val preview = formatChatPreview(longMessage)

        assertTrue(preview.startsWith("1行目 2行目"))
        assertEquals(81, preview.length)
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun formatChatPreview_returnsEmptyForNullOrBlank() {
        assertEquals("", formatChatPreview(null))
        assertEquals("", formatChatPreview("   "))
    }
}
