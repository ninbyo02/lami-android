package com.sonusid.ollama.db.entity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageInferenceStatsTest {
    @Test
    fun `isInferenceStatsMissing returns true when all stats are null`() {
        val message = Message(chatId = 1, message = "old", isSendbyMe = false)

        assertTrue(message.isInferenceStatsMissing())
    }

    @Test
    fun `isInferenceStatsMissing returns false when any stats exists`() {
        val message = Message(
            chatId = 1,
            message = "new",
            isSendbyMe = false,
            completionTokens = 42,
        )

        assertFalse(message.isInferenceStatsMissing())
    }
}
