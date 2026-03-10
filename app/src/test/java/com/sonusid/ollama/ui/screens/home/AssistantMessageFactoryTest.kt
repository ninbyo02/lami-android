package com.sonusid.ollama.ui.screens.home

import com.sonusid.ollama.ui.model.InferenceStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantMessageFactoryTest {
    @Test
    fun `createAssistantMessage keeps inference stats for newly generated response`() {
        val latestStats = InferenceStats(
            modelName = "qwen3-vl:30b",
            inputTokens = 80,
            outputTokens = 120,
            totalTokens = 200,
            tokensPerSecond = 24.0,
            inferenceTimeSec = 5.0,
            generationTimeMs = 5_000L,
            evalDurationNs = 4_500_000_000L,
        )

        val message = createAssistantMessage(
            chatId = 7,
            response = "ok",
            latestInferenceStats = latestStats,
        )

        assertEquals("qwen3-vl:30b", message.modelName)
        assertEquals(80, message.inputTokens)
        assertEquals(120, message.completionTokens)
        assertEquals(200, message.totalTokens)
        assertEquals(24.0, message.tokensPerSecond)
        assertEquals(5.0, message.inferenceTimeSec)
        assertEquals(5_000L, message.generationTimeMs)
        assertEquals(4_500_000_000L, message.evalDurationNs)
    }

    @Test
    fun `createAssistantMessage calculates totalTokens when field is missing`() {
        val latestStats = InferenceStats(
            inputTokens = 7,
            outputTokens = 9,
        )

        val message = createAssistantMessage(
            chatId = 7,
            response = "ok",
            latestInferenceStats = latestStats,
        )

        assertEquals(16, message.totalTokens)
    }

    @Test
    fun `createAssistantMessage does not fabricate stats without latestInferenceStats`() {
        val message = createAssistantMessage(
            chatId = 7,
            response = "error",
        )

        assertNull(message.modelName)
        assertNull(message.inputTokens)
        assertNull(message.completionTokens)
        assertNull(message.totalTokens)
        assertNull(message.tokensPerSecond)
        assertNull(message.inferenceTimeSec)
        assertNull(message.generationTimeMs)
        assertNull(message.evalDurationNs)
    }
    @Test
    fun `createAssistantMessage prefers canonical modelName over legacy model`() {
        val latestStats = InferenceStats(
            modelName = "canonical",
            model = "legacy",
        )

        val message = createAssistantMessage(
            chatId = 7,
            response = "ok",
            latestInferenceStats = latestStats,
        )

        assertEquals("canonical", message.modelName)
    }

}
