package com.sonusid.ollama.ui.util

import com.sonusid.ollama.ui.model.InferenceStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InferenceStatsFormatterTest {

    @Test
    fun `formatTokenPerSec prefers mapped tokensPerSecond`() {
        val stats = InferenceStats(tokensPerSecond = 15.94)

        assertEquals("⚡15.9 token/s", formatTokenPerSec(stats))
    }

    @Test
    fun `formatTokenPerSec uses output tokens with evalDurationNs`() {
        val stats = InferenceStats(outputTokens = 62, evalDurationNs = 5_000_000_000)

        assertEquals("⚡12.4 token/s", formatTokenPerSec(stats))
    }

    @Test
    fun `formatTokenPerSec keeps completionTokens compatibility`() {
        val stats = InferenceStats(completionTokens = 62, evalDurationNs = 5_000_000_000)

        assertEquals("⚡12.4 token/s", formatTokenPerSec(stats))
    }

    @Test
    fun `formatTokenPerSec returns null when evalDurationNs is missing`() {
        val stats = InferenceStats(outputTokens = 62, generationTimeMs = 5_000)

        assertNull(formatTokenPerSec(stats))
    }

    @Test
    fun `buildInferenceSummary keeps generation time fallback`() {
        val stats = InferenceStats(outputTokens = 62, generationTimeMs = 3_200)

        assertEquals("3.2s", buildInferenceSummary(stats))
    }

    @Test
    fun `formatOutputTokens formats with grouping`() {
        val stats = InferenceStats(outputTokens = 1696)

        assertEquals("1,696", formatOutputTokens(stats))
    }

    @Test
    fun `formatInferenceTime adds readable suffix spacing`() {
        val stats = InferenceStats(generationTimeMs = 18_700)

        assertEquals("18.7 s", formatInferenceTime(stats))
    }

    @Test
    fun `formatInferenceTime prefers mapped inferenceTimeSec`() {
        val stats = InferenceStats(inferenceTimeSec = 17.84, generationTimeMs = 20_000)

        assertEquals("17.8 s", formatInferenceTime(stats))
    }

    @Test
    fun `formatTotalTokens prefers persisted totalTokens`() {
        val stats = InferenceStats(totalTokens = 99, inputTokens = 10, outputTokens = 12)

        assertEquals("99", formatTotalTokens(stats))
    }

    @Test
    fun `formatTotalTokens falls back to input plus output`() {
        val stats = InferenceStats(inputTokens = 10, outputTokens = 12)

        assertEquals("22", formatTotalTokens(stats))
    }

    @Test
    fun `formatModelName prefers modelName and keeps legacy fallback`() {
        val stats = InferenceStats(modelName = "qwen3-vl:30b", model = "legacy-model", modelLabel = "legacy-label")

        assertEquals("qwen3-vl:30b", formatModelName(stats))
    }

    @Test
    fun `formatter keeps zero values`() {
        val stats = InferenceStats(
            outputTokens = 0,
            tokensPerSecond = 0.0,
            inferenceTimeSec = 0.0,
            totalTokens = 0,
        )

        assertEquals("⚡0.0 token/s", formatTokenPerSec(stats))
        assertEquals("0.0 s", formatInferenceTime(stats))
        assertEquals("0", formatOutputTokens(stats))
        assertEquals("0", formatTotalTokens(stats))
    }
}
