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
    fun `buildInferenceSummarySections returns model and overview sections in expected order`() {
        val stats = InferenceStats(
            modelName = "qwen2.5",
            timeToFirstTokenMs = 420L,
            inferenceTimeSec = 3.6,
            tokensPerSecond = 55.5,
            finishReason = "stop",
        )

        val sections = buildInferenceSummarySections(stats)

        assertEquals(listOf("モデル情報", "概要"), sections.map { it.title })
        assertEquals(listOf("使用モデル"), sections[0].items.map { it.label })
        assertEquals(listOf("qwen2.5"), sections[0].items.map { it.value })
        assertEquals(
            listOf("初回受信まで（端末基準）", "全体完了まで（統計基準）", "生成速度", "完了理由"),
            sections[1].items.map { it.label },
        )
        assertEquals(listOf("0.4 s", "3.6 s", "55.5 token/s", "正常終了 (stop)"), sections[1].items.map { it.value })
    }

    @Test
    fun `buildInferenceSummarySections keeps raw values even when first token time exceeds inference time`() {
        val stats = InferenceStats(
            timeToFirstTokenMs = 2_000L,
            inferenceTimeSec = 1.8,
        )

        val sections = buildInferenceSummarySections(stats)

        assertEquals("2.0 s", sections[1].items[0].value)
        assertEquals("1.8 s", sections[1].items[1].value)
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

        assertEquals(listOf("トークン", "バックエンド時間詳細", "補足"), sections.map { it.title })
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

    @Test
    fun `shouldShowInferenceTimingNote returns true when either timing exists`() {
        assertEquals(true, shouldShowInferenceTimingNote(InferenceStats(timeToFirstTokenMs = 120L)))
        assertEquals(true, shouldShowInferenceTimingNote(InferenceStats(inferenceTimeSec = 2.4)))
        assertEquals(false, shouldShowInferenceTimingNote(InferenceStats()))
    }

    @Test
    fun `inferenceTimingNoteText explains measurement source differences`() {
        assertEquals(
            "初回受信までは端末側の受信タイミング、全体完了までは推論統計の完了タイミングを示します。",
            inferenceTimingNoteText(),
        )
    }
}
