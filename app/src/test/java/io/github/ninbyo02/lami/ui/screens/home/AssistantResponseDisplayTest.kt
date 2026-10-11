package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantResponseDisplayTest {
    private val session = AssistantGenerationSession(10, 2, 31)
    private fun response(text: String, status: String = MessageStatus.GENERATING) =
        AssistantResponseDisplay(10, Message(31, 2, text, false, status = status))

    @Test fun `partial replaces its placeholder without appending another row`() {
        val stored = Message(31, 2, "", false, status = MessageStatus.PENDING, modelName = "model")
        val rows = mergeAssistantResponseForDisplay(2, listOf(stored), response("12ヶ月"))
        assertEquals(1, rows.size)
        assertEquals("12ヶ月", rows.single().message)
        assertEquals("model", rows.single().modelName)
    }

    @Test fun `completed row wins over late partial even with different formatting`() {
        val stored = Message(31, 2, "12ヶ月", false)
        assertEquals(listOf(stored), mergeAssistantResponseForDisplay(2, listOf(stored), response("1 2ヶ月")))
    }

    @Test fun `terminal snapshot bridges delayed Room emission using the same id`() {
        val final = response("12ヶ月", MessageStatus.COMPLETED)
        assertEquals(listOf(final.message), mergeAssistantResponseForDisplay(2, emptyList(), final))
        assertEquals(listOf(final.message), mergeAssistantResponseForDisplay(2, listOf(final.message), final))
    }

    @Test fun `equal answers from separate turns are not collapsed`() {
        val old = Message(30, 2, "12ヶ月", false)
        assertEquals(listOf(30, 31), mergeAssistantResponseForDisplay(2, listOf(old), response("12ヶ月"))
            .map { it.messageID })
    }

    @Test fun `foreign chat and stale terminal request cannot be displayed`() {
        assertEquals(emptyList<Message>(), mergeAssistantResponseForDisplay(3, emptyList(), response("old")))
        assertNull(resolveAssistantResponseDisplay(session.copy(requestId = 11), null, response("old")))
        assertNull(resolveAssistantResponseDisplay(null, "old", response("old")))
    }

    @Test fun `cancelled session rejects delayed text and completed session uses final snapshot`() {
        assertNull(resolveAssistantResponseDisplay(session.cancel(), "late", null))
        val final = response("final", MessageStatus.COMPLETED)
        assertEquals(final, resolveAssistantResponseDisplay(session.beginFinalizing().complete(31), "late", final))
    }

    @Test fun `failed and cancelled persisted rows cannot be revived by a partial`() {
        for (status in MessageStatus.TERMINAL) {
            val row = Message(31, 2, "stopped", false, status = status)
            assertEquals(listOf(row), mergeAssistantResponseForDisplay(2, listOf(row), response("late")))
        }
    }
}
