package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalInferenceStatsUiModelBuilderTest {

    @Test
    fun `assistantUpdateCount と generationTimeMs から token per sec を算出できる`() {
        val actual = buildLocalAssistantUpdateBasedTokensPerSecondOrNull(
            assistantUpdateCount = 67,
            generationTimeMs = 3_600L,
        )

        requireNotNull(actual)
        assertEquals(18.6, actual, 0.02)
    }

    @Test
    fun `assistantUpdateCount が 0 以下なら null を返す`() {
        val actual = buildLocalAssistantUpdateBasedTokensPerSecondOrNull(
            assistantUpdateCount = 0,
            generationTimeMs = 3_600L,
        )

        assertNull(actual)
    }

    @Test
    fun `LiteRT tokenizer実測を token source として採用する`() {
        val uiModel = createLocalInferenceStatsUiModel(
            trace = LocalInferenceTrace(
                measuredTokenSnapshot = LocalInferenceMeasuredTokenSnapshot(
                    inputTokens = 1,
                    outputTokens = 65,
                    totalTokens = 66,
                    tokenCountMode = "mediapipe_tokenizer_recount",
                ),
            ),
            stats = InferenceStats(
                tokenCountMode = "mediapipe_tokenizer_recount",
                decodeDurationMs = 1_000L,
            ),
        )

        assertEquals(1, uiModel.resolvedInputTokens)
        assertEquals(65, uiModel.resolvedOutputTokens)
        assertEquals(66, uiModel.resolvedTotalTokens)
        assertEquals("Tokenizer", uiModel.resolvedTokenSourceLabel)
    }
}
