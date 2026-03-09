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
}
