package com.sonusid.ollama.ui.screens.home

import com.sonusid.ollama.ui.model.InferenceStats
import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceStatsSheetContentTest {
    @Test
    fun `inferenceStatsDetailToggleActionLabel returns short action text by expanded state`() {
        assertEquals("表示", inferenceStatsDetailToggleActionLabel(expanded = false))
        assertEquals("閉じる", inferenceStatsDetailToggleActionLabel(expanded = true))
    }

    @Test
    fun `inferenceStatsDetailToggleAccessibilityLabel keeps full meaning by expanded state`() {
        assertEquals("詳細を表示", inferenceStatsDetailToggleAccessibilityLabel(expanded = false))
        assertEquals("詳細を閉じる", inferenceStatsDetailToggleAccessibilityLabel(expanded = true))
    }

    @Test
    fun `buildInferenceDetailSections returns token and supplement sections in expected order`() {
        val stats = InferenceStats(
            inputTokens = 100,
            outputTokens = 240,
            totalTokens = 340,
            modelLoadDurationNs = 2_000_000_000L,
            promptEvalDurationNs = 1_500_000_000L,
            generationDurationNs = 3_000_000_000L,
            imageInputCount = 2,
        )

        val sections = buildInferenceDetailSections(stats)

        assertEquals(listOf("トークン", "時間詳細", "補足"), sections.map { it.title })
        assertEquals(
            listOf("入力トークン", "生成トークン", "合計トークン"),
            sections[0].items.map { it.label },
        )
        assertEquals(listOf("100", "240", "340"), sections[0].items.map { it.value })
        assertEquals(listOf("モデルロード時間", "入力評価時間", "生成時間"), sections[1].items.map { it.label })
        assertEquals(listOf("2.0 s", "1.5 s", "3.0 s"), sections[1].items.map { it.value })
        assertEquals(listOf("画像入力"), sections[2].items.map { it.label })
        assertEquals(listOf("2枚"), sections[2].items.map { it.value })
    }

    @Test
    fun `buildInferenceDetailSections keeps placeholder when values are missing`() {
        val sections = buildInferenceDetailSections(InferenceStats())

        assertEquals(listOf("—", "—", "—"), sections[0].items.map { it.value })
        assertEquals(listOf("—", "—", "—"), sections[1].items.map { it.value })
        assertEquals("—", sections[2].items.first().value)
    }
}
