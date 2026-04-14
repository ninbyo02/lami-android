package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.ContextWindowFetchState
import io.github.ninbyo02.lami.ui.model.InferenceStats
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

        assertEquals(listOf("概要"), sections.map { it.title })
        assertEquals(
            listOf("初回受信まで（端末基準）", "全体完了まで（統計基準）", "生成速度", "完了理由"),
            sections[0].items.map { it.label },
        )
        assertEquals(listOf("0.4 s", "3.6 s", "55.5 token/s", "通常終了 (stop)"), sections[0].items.map { it.value })
    }

    @Test
    fun `buildInferenceSummarySections keeps raw values even when first token time exceeds inference time`() {
        val stats = InferenceStats(
            timeToFirstTokenMs = 2_000L,
            inferenceTimeSec = 1.8,
        )

        val sections = buildInferenceSummarySections(stats)

        assertEquals("2.0 s", sections[0].items[0].value)
        assertEquals("1.8 s", sections[0].items[1].value)
    }

    @Test
    fun `buildInferenceSummarySections keeps local semi measured generation speed label`() {
        val trace = LocalInferenceTrace(
            assistantUpdateCount = 93,
            evalTimeProbe = LocalStatsCandidateProbe(
                availability = LocalStatsAvailability.AVAILABLE_NOW,
                valueSummary = "5000000000",
            ),
        )
        val stats = InferenceStats(
            outputTokens = 240,
            generationDurationNs = 5_000_000_000L,
        )

        val sections = buildInferenceSummarySections(
            stats = stats,
            localTraceForDev = trace,
        )

        assertEquals("18.6 token/s（準実測）", sections[0].items[2].value)
    }

    @Test
    fun `buildInferenceTimeBreakdown returns null when total duration is not positive`() {
        assertEquals(null, buildInferenceTimeBreakdown(InferenceStats()))
        assertEquals(null, buildInferenceTimeBreakdown(InferenceStats(modelLoadDurationNs = -1L)))
    }

    @Test
    fun `buildInferenceTimeBreakdown builds three segments with ratios`() {
        val stats = InferenceStats(
            modelLoadDurationNs = 9_000_000_000L,
            promptEvalDurationNs = 100_000_000L,
            generationDurationNs = 500_000_000L,
        )

        val breakdown = buildInferenceTimeBreakdown(stats)

        requireNotNull(breakdown)
        assertEquals(listOf("ロード", "入力", "生成"), breakdown.segments.map { it.label })
        assertEquals(listOf(94, 1, 5), breakdown.segments.map { it.percent })
        assertEquals(1.0, breakdown.segments.sumOf { it.ratio }, 0.0000001)
    }

    @Test
    fun `buildContextUsageUi returns Loading while context max is being fetched`() {
        val usage = buildContextUsageUi(
            InferenceStats(
                totalTokens = 40,
                contextWindowFetchState = ContextWindowFetchState.LOADING,
            ),
        )

        assertEquals(ContextUsageUi.Loading(used = 40), usage)
    }

    @Test
    fun `buildContextUsageUi uses totalTokens and falls back when max context is unavailable`() {
        val withoutMax = buildContextUsageUi(
            InferenceStats(
                totalTokens = 40,
                contextWindowFetchState = ContextWindowFetchState.UNAVAILABLE,
            ),
        )
        assertEquals(ContextUsageUi.WithoutMax(used = 40), withoutMax)

        val withMax = buildContextUsageUi(
            InferenceStats(
                totalTokens = 40,
                contextWindow = 4096,
                contextWindowFetchState = ContextWindowFetchState.AVAILABLE,
            ),
        )
        require(withMax is ContextUsageUi.WithMax)
        assertEquals(40, withMax.used)
        assertEquals(4096, withMax.max)
        assertEquals(1, withMax.percent)
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
        assertEquals(listOf("モデルロード時間", "入力評価時間", "生成時間", "推論時間"), sections[1].items.map { it.label })
        assertEquals(
            listOf("2.0 s（取得済み）", "1.5 s（取得済み）", "3.0 s（取得済み）", "—（未取得）"),
            sections[1].items.map { it.value },
        )
        assertEquals(listOf("画像入力"), sections[2].items.map { it.label })
        assertEquals(listOf("2枚"), sections[2].items.map { it.value })
    }


    @Test
    fun `buildInferenceDetailSections adds Ollama perceived tokens per second when streaming updates are available`() {
        val stats = InferenceStats(
            outputTokens = 240,
            tokensPerSecond = 32.4,
            generationTimeMs = 5_000L,
            assistantUpdateCount = 109,
        )

        val sections = buildInferenceDetailSections(stats)

        assertEquals(
            listOf("入力トークン", "生成トークン", "合計トークン", "実測生成速度", "体感生成速度"),
            sections[0].items.map { it.label },
        )
        assertEquals("32.4 token/s", sections[0].items[3].value)
        assertEquals("21.8 token/s", sections[0].items[4].value)
        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals(
            "semi-measured:assistantUpdateCount / generationTimeMs",
            devSection.items.first { it.label == "体感生成速度source" }.value,
        )
    }

    @Test
    fun `buildInferenceDetailSections hides Ollama perceived tokens per second when streaming updates are unavailable`() {
        val stats = InferenceStats(
            tokensPerSecond = 32.4,
            generationTimeMs = 5_000L,
            assistantUpdateCount = 0,
        )

        val sections = buildInferenceDetailSections(stats)

        assertEquals(
            listOf("入力トークン", "生成トークン", "合計トークン", "実測生成速度"),
            sections[0].items.map { it.label },
        )
    }

    @Test
    fun `buildInferenceDetailSections keeps placeholder when values are missing`() {
        val sections = buildInferenceDetailSections(InferenceStats())

        assertEquals(listOf("—", "—", "—"), sections[0].items.map { it.value })
        assertEquals(listOf("—（未取得）", "—（未取得）", "—（未取得）", "—（未取得）"), sections[1].items.map { it.value })
        assertEquals("—", sections[2].items.first().value)
    }

    @Test
    fun `buildInferenceDetailSections adds LiteRT-LM benchmark last token rows when available`() {
        val trace = LocalInferenceTrace(
            measuredTokenSnapshot = LocalInferenceMeasuredTokenSnapshot(
                lastPrefillTokenCount = 0,
                lastDecodeTokenCount = 42,
            ),
        )

        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            localTraceForDev = trace,
        )

        assertEquals(
            listOf("入力トークン", "生成トークン", "合計トークン", "直近 Prefill Token", "直近 Decode Token"),
            sections[0].items.map { it.label },
        )
        assertEquals(listOf("—", "—", "—", "0", "42"), sections[0].items.map { it.value })
    }

    @Test
    fun `buildInferenceDetailSections marks generation fallback when using evalDurationNs`() {
        val sections = buildInferenceDetailSections(
            InferenceStats(
                evalDurationNs = 1_200_000_000L,
            ),
        )

        assertEquals("1.2 s（fallback）", sections[1].items[2].value)
        assertEquals("1.2 s（取得済み）", sections[1].items[3].value)
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
