package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalConversationPolicyTest {
    @Test
    fun `history is bounded and shared with LiteRT and NPU routes`() {
        val turns = (1..14).map { index ->
            LocalConversationTurn(
                role = if (index % 2 == 0) LocalConversationRole.MODEL else LocalConversationRole.USER,
                text = "message-$index",
            )
        }

        val bounded = LocalConversationHistoryPolicy.bounded(turns)
        val npuContext = LocalConversationHistoryPolicy.npuContext(turns)

        assertEquals(LocalConversationHistoryPolicy.MAX_HISTORY_MESSAGES, bounded.size)
        assertEquals("message-3", bounded.first().text)
        assertTrue(npuContext.startsWith("ユーザー: message-3"))
        assertTrue(npuContext.endsWith("アシスタント: message-14"))
        assertTrue(!npuContext.contains("message-2\n"))
    }
}
