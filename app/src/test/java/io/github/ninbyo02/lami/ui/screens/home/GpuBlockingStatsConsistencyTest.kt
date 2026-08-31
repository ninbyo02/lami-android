package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuBlockingStatsConsistencyTest {
    private val blockingStats = InferenceStats(
        modelName = "gemma-4-E2B-it.litertlm",
        modelLoadDurationNs = 800_000_000L,
        promptEvalDurationNs = 27_600_000_000L,
        generationDurationNs = 0L,
        evalDurationNs = 27_600_000_000L,
        totalDurationMs = 7_200L,
        localSourceSummary = "source_summary=held-official-blocking",
    )

    @Test
    fun `blocking breakdown does not fabricate separable input or generation`() {
        val breakdown = requireNotNull(buildInferenceTimeBreakdown(blockingStats))
        assertEquals(listOf("ロード", "未計上"), breakdown.segments.map { it.label })
        assertEquals(listOf(11, 89), breakdown.segments.map { it.percent })
        assertEquals(0.8 / 7.2, breakdown.segments[0].ratio, 0.000_001)
        assertEquals(6.4 / 7.2, breakdown.segments[1].ratio, 0.000_001)
    }

    @Test
    fun `blocking detail uses the same total duration as summary`() {
        val details = buildInferenceDetailSections(
            stats = blockingStats,
            displayMode = InferenceStatsDisplayMode.DETAILED,
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
        ).single { it.title == "詳細" }.items

        val input = details.single { it.label == "入力評価時間" }.value
        val generation = details.single { it.label == "生成時間" }.value
        val inference = details.single { it.label == "推論時間" }.value

        assertTrue(input.contains("未取得（一括応答）"))
        assertTrue(generation.contains("未取得（一括応答）"))
        assertTrue(inference.contains("7.2"))
        assertFalse(inference.contains("27.6"))
    }

    @Test
    fun `blocking detail falls back to total when eval duration is zero`() {
        val stats = blockingStats.copy(
            evalDurationNs = 0L,
            totalDurationMs = 7_200L,
        )
        val details = buildInferenceDetailSections(
            stats = stats,
            displayMode = InferenceStatsDisplayMode.DETAILED,
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
        ).single { it.title == "詳細" }.items

        val input = details.single { it.label == "入力評価時間" }.value
        val generation = details.single { it.label == "生成時間" }.value
        val inference = details.single { it.label == "推論時間" }.value

        assertTrue(input.contains("未取得（一括応答）"))
        assertTrue(generation.contains("未取得（一括応答）"))
        assertTrue(inference.contains("7.2"))
        assertFalse(inference.contains("0.0"))
    }
}
