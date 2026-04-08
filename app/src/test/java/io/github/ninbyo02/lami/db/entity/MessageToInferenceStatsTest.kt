package io.github.ninbyo02.lami.db.entity

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
            loadDurationNs = 900_000_000L,
            promptEvalDurationNs = 600_000_000L,
            evalDurationNs = 2_000_000_000L,
            timeToFirstTokenMs = 410L,
        )

        val stats = message.toInferenceStats()

        assertNotNull(stats)
        assertEquals("qwen3-vl:30b", stats?.modelName)
        assertEquals(12, stats?.inputTokens)
        assertEquals(34, stats?.outputTokens)
        assertEquals(46, stats?.totalTokens)
        assertEquals(9.5, stats?.tokensPerSecond)
        assertEquals(3.2, stats?.inferenceTimeSec)
        assertEquals(900_000_000L, stats?.modelLoadDurationNs)
        assertEquals(600_000_000L, stats?.promptEvalDurationNs)
        assertEquals(2_000_000_000L, stats?.generationDurationNs)
        assertEquals(410L, stats?.timeToFirstTokenMs)
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
    fun `toInferenceStats maps finishReason, first token time and imageInputCount`() {
        val message = Message(
            chatId = 1,
            message = "new",
            isSendbyMe = false,
            finishReason = "stop",
            timeToFirstTokenMs = 0L,
            imageInputCount = 0,
        )

        val stats = message.toInferenceStats()

        assertNotNull(stats)
        assertEquals("stop", stats?.finishReason)
        assertEquals(0L, stats?.timeToFirstTokenMs)
        assertEquals(0, stats?.imageInputCount)
    }
}
