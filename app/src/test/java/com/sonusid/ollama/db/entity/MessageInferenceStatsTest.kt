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
    fun `isInferenceStatsMissing returns false when any legacy stats exists`() {
        val message = Message(
            chatId = 1,
            message = "new",
            isSendbyMe = false,
            completionTokens = 42,
        )

        assertFalse(message.isInferenceStatsMissing())
    }

    @Test
    fun `isInferenceStatsMissing returns false when v6 stats exists`() {
        val message = Message(
            chatId = 1,
            message = "new",
            isSendbyMe = false,
            modelName = "qwen3-vl:30b",
        )

        assertFalse(message.isInferenceStatsMissing())
    }
    @Test
    fun `isInferenceStatsMissing returns false when timeToFirstTokenMs exists`() {
        val message = Message(
            chatId = 1,
            message = "new",
            isSendbyMe = false,
            timeToFirstTokenMs = 0L,
        )

        assertFalse(message.isInferenceStatsMissing())
    }

    @Test
    fun `isInferenceStatsMissing returns false when duration detail exists`() {
        val message = Message(
            chatId = 1,
            message = "new",
            isSendbyMe = false,
            loadDurationNs = 0L,
        )

        assertFalse(message.isInferenceStatsMissing())
    }

}
