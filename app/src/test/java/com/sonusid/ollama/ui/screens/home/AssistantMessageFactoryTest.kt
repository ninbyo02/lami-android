package com.sonusid.ollama.ui.screens.home

import com.sonusid.ollama.ui.model.InferenceStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantMessageFactoryTest {
    @Test
    fun `createAssistantMessage keeps inference stats for newly generated response`() {
        val latestStats = InferenceStats(
            completionTokens = 120,
            generationTimeMs = 5_000L,
            evalDurationNs = 4_500_000_000L,
        )

        val message = createAssistantMessage(
            chatId = 7,
            response = "ok",
            latestInferenceStats = latestStats,
        )

        assertEquals(120, message.completionTokens)
        assertEquals(5_000L, message.generationTimeMs)
        assertEquals(4_500_000_000L, message.evalDurationNs)
    }

    @Test
    fun `createAssistantMessage does not fabricate stats without latestInferenceStats`() {
        val message = createAssistantMessage(
            chatId = 7,
            response = "error",
        )

        assertNull(message.completionTokens)
        assertNull(message.generationTimeMs)
        assertNull(message.evalDurationNs)
    }
}
