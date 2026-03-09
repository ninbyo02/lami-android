package com.sonusid.ollama.db.entity

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MessageToInferenceStatsTest {
    @Test
    fun `toInferenceStats returns null when legacy row has all stats null`() {
        val legacyMessage = Message(chatId = 1, message = "legacy", isSendbyMe = false)

        assertNull(legacyMessage.toInferenceStats())
    }

    @Test
    fun `toInferenceStats returns stats when at least one persisted field exists`() {
        val newMessage = Message(
            chatId = 1,
            message = "new",
            isSendbyMe = false,
            generationTimeMs = 1_500L,
        )

        assertNotNull(newMessage.toInferenceStats())
    }
}
