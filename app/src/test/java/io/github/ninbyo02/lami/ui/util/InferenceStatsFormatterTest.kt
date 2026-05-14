package io.github.ninbyo02.lami.ui.util

import io.github.ninbyo02.lami.ui.model.InferenceStats
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
    fun `formatTimeToFirstToken uses ms and second units`() {
        assertEquals("123 ms", formatTimeToFirstToken(InferenceStats(timeToFirstTokenMs = 123L)))
        assertEquals("1.2 s", formatTimeToFirstToken(InferenceStats(timeToFirstTokenMs = 1_230L)))
    }

    @Test
    fun `formatTimeToFirstToken keeps zero and null handling`() {
        assertEquals("0 ms", formatTimeToFirstToken(InferenceStats(timeToFirstTokenMs = 0L)))
        assertNull(formatTimeToFirstToken(InferenceStats()))
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
    fun `formatInferenceTime prioritizes totalDurationMs over inferenceTimeSec`() {
        val stats = InferenceStats(
            totalDurationMs = 3_400L,
            inferenceTimeSec = 9.9,
            generationTimeMs = 20_000L,
        )

        assertEquals("3.4 s", formatInferenceTime(stats))
    }

    @Test
    fun `duration detail formatters convert ns to seconds safely`() {
        val stats = InferenceStats(
            modelLoadDurationNs = 2_345_000_000L,
            promptEvalDurationNs = 650_000_000L,
            generationDurationNs = 4_050_000_000L,
        )

        assertEquals("2.3 s", formatModelLoadDuration(stats))
        assertEquals("0.7 s", formatPromptEvalDuration(stats))
        assertEquals("4.1 s", formatGenerationDuration(stats))
    }

    @Test
    fun `formatGenerationDuration falls back to evalDurationNs`() {
        val stats = InferenceStats(evalDurationNs = 1_200_000_000L)

        assertEquals("1.2 s", formatGenerationDuration(stats))
    }

    @Test
    fun `duration detail formatters return null for null and negative values`() {
        assertNull(formatModelLoadDuration(InferenceStats()))
        assertNull(formatPromptEvalDuration(InferenceStats(promptEvalDurationNs = -1L)))
        assertNull(formatGenerationDuration(InferenceStats(generationDurationNs = -2L)))
    }

    @Test
    fun `duration formatters show less than point one seconds for tiny positive values`() {
        val stats = InferenceStats(
            inferenceTimeSec = 0.05,
            modelLoadDurationNs = 50_000_000L,
        )

        assertEquals("<0.1 s", formatInferenceTime(stats))
        assertEquals("<0.1 s", formatModelLoadDuration(stats))
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


    @Test
    fun `formatFinishReason maps known values for user display`() {
        assertEquals("通常終了 (stop)", formatFinishReason(InferenceStats(finishReason = "stop")))
        assertEquals("トークン上限 (length)", formatFinishReason(InferenceStats(finishReason = "length")))
        assertEquals("フィルター停止 (content_filter)", formatFinishReason(InferenceStats(finishReason = "content_filter")))
        assertEquals("ユーザー停止 (cancelled)", formatFinishReason(InferenceStats(finishReason = "cancelled")))
    }

    @Test
    fun `formatFinishReason trims empty and unknown safely`() {
        assertEquals("通常終了 (stop)", formatFinishReason(InferenceStats(finishReason = "  stop  ")))
        assertNull(formatFinishReason(InferenceStats(finishReason = "   ")))
        assertEquals("other", formatFinishReason(InferenceStats(finishReason = "other")))
        assertNull(formatFinishReason(InferenceStats()))
    }

    @Test
    fun `formatImageInputCount keeps zero as valid value`() {
        assertEquals("0枚", formatImageInputCount(InferenceStats(imageInputCount = 0)))
        assertEquals("2枚", formatImageInputCount(InferenceStats(imageInputCount = 2)))
        assertNull(formatImageInputCount(InferenceStats()))
    }
}
