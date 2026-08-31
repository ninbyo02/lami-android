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

    @Test
    fun `estimated code point token speed remains estimated even when decode time is measured`() {
        val uiModel = createLocalInferenceStatsUiModel(
            trace = LocalInferenceTrace(),
            stats = InferenceStats(
                tokensPerSecond = 63.5,
                tokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS,
                decodeDurationMs = 315L,
                generationDurationNs = 315_000_000L,
                totalDurationMs = 400L,
            ),
            assistantText = "こんにちは。何かお手伝いできますか？",
        )

        assertEquals("推定（出力コードポイント数）", uiModel.resolvedTokenSourceLabel)
        assertEquals(StatsUiValueSource.ESTIMATED, uiModel.tokensPerSecond.source)
        assertEquals("推定 / 実測Decode時間 × コードポイント換算", uiModel.resolvedSpeedSourceLabel)
        assertEquals("推定", uiModel.resolvedPrimarySpeedSourceLabel)
        assertNull(uiModel.resolvedBackendTokensPerSecond)
    }

}
