package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.ContextWindowFetchState
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceStatsSheetContentTest {
    @Test
    fun `buildInferenceSummarySections returns model and overview sections in expected order`() {
        val stats = InferenceStats(
            modelName = "qwen2.5",
            timeToFirstTokenMs = 420L,
            inferenceTimeSec = 3.6,
            tokensPerSecond = 55.5,
            finishReason = "stop",
        )

        val sections = buildInferenceSummarySections(
            stats = stats,
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

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

        val sections = buildInferenceSummarySections(
            stats = stats,
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

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
            displayMode = InferenceStatsDisplayMode.DETAILED,
            localTraceForDev = trace,
        )

        assertEquals("18.6 token/s（準実測）", sections[0].items[2].value)
    }

    @Test
    fun `buildInferenceSummarySections prioritizes tokenizer based generation speed on LiteRT`() {
        val trace = LocalInferenceTrace(
            assistantUpdateCount = 66,
            evalTimeProbe = LocalStatsCandidateProbe(
                availability = LocalStatsAvailability.AVAILABLE_NOW,
                valueSummary = "5000000000",
            ),
        )
        val stats = InferenceStats(
            outputTokens = 44,
            decodeDurationMs = 3_700L,
            tokensPerSecond = 17.9,
            generationDurationNs = 5_000_000_000L,
        )

        val sections = buildInferenceSummarySections(
            stats = stats,
            displayMode = InferenceStatsDisplayMode.DETAILED,
            localTraceForDev = trace,
        )

        assertEquals("11.9 token/s（Tokenizer）", sections[0].items[2].value)
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

        val sections = buildInferenceDetailSections(
            stats = stats,
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

        assertEquals(listOf("トークン", "バックエンド時間詳細", "補足"), sections.map { it.title })
        assertEquals(
            listOf("入力トークン", "生成トークン", "合計トークン", "トークン取得元", "速度取得元", "Tokenizer状態"),
            sections[0].items.map { it.label },
        )
        assertEquals(listOf("100（推定）", "240（推定）", "340（推定）", "Ollama", "推定", "未実行"), sections[0].items.map { it.value })
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

        val sections = buildInferenceDetailSections(
            stats = stats,
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

        assertEquals(
            listOf("入力トークン", "生成トークン", "合計トークン", "トークン取得元", "実測生成速度", "体感生成速度", "速度取得元", "Tokenizer状態"),
            sections[0].items.map { it.label },
        )
        assertEquals("32.4 token/s", sections[0].items[4].value)
        assertEquals("21.8 token/s", sections[0].items[5].value)
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

        val sections = buildInferenceDetailSections(
            stats = stats,
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

        assertEquals(
            listOf("入力トークン", "生成トークン", "合計トークン", "トークン取得元", "実測生成速度", "速度取得元", "Tokenizer状態"),
            sections[0].items.map { it.label },
        )
    }

    @Test
    fun `buildInferenceDetailSections keeps placeholder when values are missing`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

        assertEquals(listOf("—（未取得）", "—（未取得）", "—（未取得）"), sections[0].items.take(3).map { it.value })
        assertEquals("未実行", sections[0].items.last { it.label == "Tokenizer状態" }.value)
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
            displayMode = InferenceStatsDisplayMode.DETAILED,
            localTraceForDev = trace,
        )

        assertEquals(
            listOf("入力トークン数（未取得）", "出力トークン数（未取得）", "合計トークン（未取得）", "トークン取得元", "速度取得元", "直近 Prefill Token", "直近 Decode Token", "Tokenizer状態"),
            sections[0].items.map { it.label },
        )
        assertEquals(listOf("—（未取得）", "—（未取得）", "—（未取得）", "未取得", "未取得", "0", "42", "未実行"), sections[0].items.map { it.value })
    }

    @Test
    fun `buildInferenceDetailSections adds LiteRT tokenizer timing rows and note when available`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(
                tokensPerSecond = 21.5,
                timeToFirstTokenMs = 420L,
                decodeDurationMs = 2_800L,
                totalDurationMs = 3_400L,
                notes = "tokenizer note",
            ),
            displayMode = InferenceStatsDisplayMode.DETAILED,
            localTraceForDev = LocalInferenceTrace(),
        )

        assertEquals(
            listOf(
                "入力トークン数（未取得）",
                "出力トークン数（未取得）",
                "合計トークン（未取得）",
                "トークン取得元",
                "実測生成速度",
                "速度取得元",
                "TTFT",
                "Decode時間",
                "総応答時間",
                "Tokenizer状態",
            ),
            sections[0].items.map { it.label },
        )
        assertEquals(listOf("21.5 token/s（推定）", "推定", "0.4 s", "2.8 s", "3.4 s", "未実行"), sections[0].items.takeLast(6).map { it.value })
        assertEquals("tokenizer note", sections[2].items.first { it.label == "注記" }.value)
    }

    @Test
    fun `buildInferenceDetailSections marks tokenizer based labels only when recount succeeded`() {
        val trace = LocalInferenceTrace(
            measuredTokenSnapshot = LocalInferenceMeasuredTokenSnapshot(
                inputTokens = 12,
                outputTokens = 34,
                totalTokens = 46,
                tokenCountMode = "tokenizer_recount",
                tokenizerRecountStatus = "success",
            ),
        )
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(tokenCountMode = "tokenizer_recount"),
            displayMode = InferenceStatsDisplayMode.DETAILED,
            localTraceForDev = trace,
        )

        assertEquals(
            listOf("入力トークン数（Tokenizer基準）", "出力トークン数（Tokenizer基準）", "合計トークン（Tokenizer基準）"),
            sections[0].items.take(3).map { it.label },
        )
        assertEquals(listOf("12（Tokenizer）", "34（Tokenizer）", "46（Tokenizer）"), sections[0].items.take(3).map { it.value })
        assertEquals("成功", sections[0].items.last { it.label == "Tokenizer状態" }.value)
    }

    @Test
    fun `buildInferenceDetailSections marks tokenizer failure reason and avoids tokenizer label`() {
        val trace = LocalInferenceTrace(
            measuredTokenSnapshot = LocalInferenceMeasuredTokenSnapshot(
                tokenizerRecountStatus = "skipped reason=inference-instance-not-found",
            ),
        )
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(inputTokens = 8, outputTokens = 13, totalTokens = 21),
            displayMode = InferenceStatsDisplayMode.DETAILED,
            localTraceForDev = trace,
        )

        assertEquals(
            listOf("入力トークン数（推定）", "出力トークン数（推定）", "合計トークン（推定）"),
            sections[0].items.take(3).map { it.label },
        )
        assertEquals("失敗（inference-instance-not-found）", sections[0].items.last { it.label == "Tokenizer状態" }.value)
    }

    @Test
    fun `buildInferenceDetailSections marks generation fallback when using evalDurationNs`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(
                evalDurationNs = 1_200_000_000L,
            ),
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

        assertEquals("1.2 s（fallback）", sections[1].items[2].value)
        assertEquals("1.2 s（取得済み）", sections[1].items[3].value)
    }

    @Test
    fun `buildInferenceDetailSections does not duplicate measuredTokens in DEV diagnostics`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DETAILED,
            measuredTokenSnapshotSummary = "in=1 / out=2 / total=3",
        )
        val devSection = sections.firstOrNull { it.title == "DEV診断" }
        if (devSection != null) {
            assertEquals(null, devSection.items.firstOrNull { it.label == "measuredTokens" })
        }
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

    @Test
    fun `buildInferenceStatsFullCopyText includes summary detail and measured token blocks`() {
        val stats = InferenceStats(
            modelName = "qwen2.5",
            modelLoadDurationNs = 500_000_000L,
            promptEvalDurationNs = 400_000_000L,
            generationDurationNs = 600_000_000L,
            totalTokens = 120,
            contextWindow = 4096,
        )
        val text = buildInferenceStatsFullCopyText(
            stats = stats,
            displayMode = InferenceStatsDisplayMode.DETAILED,
            sections = listOf(
                InferenceStatsSectionUi(
                    title = "概要",
                    items = listOf(InferenceStatItemUi(label = "完了理由", value = "通常終了 (stop)")),
                ),
            ),
            detailSections = listOf(
                InferenceStatsSectionUi(
                    title = "DEV診断サマリー",
                    items = listOf(InferenceStatItemUi(label = "診断", value = "ok")),
                ),
            ),
        )

        assertTrue(text.contains("推論統計"))
        assertTrue(text.contains("[モデル情報]"))
        assertTrue(text.contains("[概要]"))
        assertTrue(text.contains("[推論時間内訳]"))
        assertTrue(text.contains("[コンテキスト使用量]"))
        assertTrue(text.contains("[追加情報]"))
        assertTrue(text.contains("[DEV診断サマリー]"))
    }

    @Test
    fun `buildInferenceStatsFullCopyText keeps benchmark placeholder when measured tokens are unavailable`() {
        val text = buildInferenceStatsFullCopyText(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DETAILED,
            sections = emptyList(),
            detailSections = emptyList(),
        )

        assertTrue(text.contains("—"))
    }
}
