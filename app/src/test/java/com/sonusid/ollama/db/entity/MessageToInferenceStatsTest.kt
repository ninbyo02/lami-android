package com.sonusid.ollama.db.entity

import org.junit.Assert.assertEquals
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
    fun `toInferenceStats maps persisted v6 fields`() {
        val message = Message(
            chatId = 1,
            message = "new",
            isSendbyMe = false,
            modelName = "qwen3-vl:30b",
            inputTokens = 12,
            completionTokens = 34,
            totalTokens = 46,
            tokensPerSecond = 9.5,
            inferenceTimeSec = 3.2,
            generationTimeMs = 3_500L,
            evalDurationNs = 2_000_000_000L,
        )

        val stats = message.toInferenceStats()

        assertNotNull(stats)
        assertEquals("qwen3-vl:30b", stats?.modelName)
        assertEquals(12, stats?.inputTokens)
        assertEquals(34, stats?.outputTokens)
        assertEquals(46, stats?.totalTokens)
        assertEquals(9.5, stats?.tokensPerSecond)
        assertEquals(3.2, stats?.inferenceTimeSec)
    }

    @Test
    fun `toInferenceStats keeps zero values`() {
        val message = Message(
            chatId = 1,
            message = "zero",
            isSendbyMe = false,
            inputTokens = 0,
            completionTokens = 0,
            totalTokens = 0,
            tokensPerSecond = 0.0,
            inferenceTimeSec = 0.0,
        )

        val stats = message.toInferenceStats()

        assertNotNull(stats)
        assertEquals(0, stats?.inputTokens)
        assertEquals(0, stats?.outputTokens)
        assertEquals(0, stats?.totalTokens)
        assertEquals(0.0, stats?.tokensPerSecond)
        assertEquals(0.0, stats?.inferenceTimeSec)
    }
    @Test
    fun `toInferenceStats derives inferenceTimeSec from generationTimeMs when persisted value is missing`() {
        val message = Message(
            chatId = 1,
            message = "fallback",
            isSendbyMe = false,
            generationTimeMs = 2_500L,
        )

        val stats = message.toInferenceStats()

        assertNotNull(stats)
        assertEquals(2.5, stats?.inferenceTimeSec)
    }



    @Test
    fun `toInferenceStats maps finishReason and imageInputCount`() {
        val message = Message(
            chatId = 1,
            message = "new",
            isSendbyMe = false,
            finishReason = "stop",
            imageInputCount = 0,
        )

        val stats = message.toInferenceStats()

        assertNotNull(stats)
        assertEquals("stop", stats?.finishReason)
        assertEquals(0, stats?.imageInputCount)
    }
}
