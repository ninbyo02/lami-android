package com.sonusid.ollama.ui.util

import com.sonusid.ollama.ui.model.InferenceStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InferenceStatsFormatterTest {
    @Test
    fun `formatTokenPerSec uses evalDurationNs`() {
        val stats = InferenceStats(completionTokens = 62, evalDurationNs = 5_000_000_000)

        assertEquals("⚡12.4 token/s", formatTokenPerSec(stats))
    }

    @Test
    fun `formatTokenPerSec returns null when evalDurationNs is missing`() {
        val stats = InferenceStats(completionTokens = 62, generationTimeMs = 5_000)

        assertNull(formatTokenPerSec(stats))
    }

    @Test
    fun `buildInferenceSummary keeps generation time fallback`() {
        val stats = InferenceStats(completionTokens = 62, generationTimeMs = 3_200)

        assertEquals("3.2s", buildInferenceSummary(stats))
    }

    @Test
    fun `buildInferenceSummary uses token per second fallback`() {
        val stats = InferenceStats(completionTokens = 62, evalDurationNs = 5_000_000_000)

        assertEquals("⚡12.4 token/s", buildInferenceSummary(stats))
    }

    @Test
    fun `buildInferenceSummary combines token per second and generation time`() {
        val stats = InferenceStats(
            completionTokens = 62,
            evalDurationNs = 5_000_000_000,
            generationTimeMs = 18_700,
        )

        assertEquals("⚡12.4 token/s · 18.7s", buildInferenceSummary(stats))
    }

    @Test
    fun `formatCompletionTokens formats with grouping`() {
        val stats = InferenceStats(completionTokens = 1696)

        assertEquals("1,696", formatCompletionTokens(stats))
    }

    @Test
    fun `formatInferenceTime adds readable suffix spacing`() {
        val stats = InferenceStats(generationTimeMs = 18_700)

        assertEquals("18.7 s", formatInferenceTime(stats))
    }
}
