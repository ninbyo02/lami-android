package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantGenerationSessionTest {
    @Test
    fun `one request owns one assistant row through completion`() {
        val started = AssistantGenerationSession(requestId = 7L, chatId = 42)
        val streaming = started.claimMessage(101)
        val finalizing = streaming.beginFinalizing()
        val completed = finalizing.complete(101)

        assertEquals(101, completed.messageId)
        assertEquals(AssistantGenerationSession.Phase.COMPLETED, completed.phase)
        assertTrue(completed.isTerminal)
        assertFalse(completed.acceptsStreamingUpdate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `session rejects a second assistant row`() {
        AssistantGenerationSession(requestId = 7L, chatId = 42)
            .claimMessage(101)
            .claimMessage(102)
    }

    @Test
    fun `finalizing session rejects delayed streaming`() {
        val session = AssistantGenerationSession(requestId = 7L, chatId = 42)
            .claimMessage(101)
            .beginFinalizing()

        assertFalse(session.acceptsStreamingUpdate)
        assertFalse(session.isTerminal)
    }
}
