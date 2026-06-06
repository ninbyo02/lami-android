package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.ContextWindowFetchState
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        assertEquals(listOf("420 ms", "3.6 s", "55.5 token/s", "通常終了 (stop)"), sections[0].items.map { it.value })
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

        assertEquals("12.2 token/s", sections[0].items[2].value)
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

        assertEquals("17.9 token/s", sections[0].items[2].value)
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

        assertEquals(listOf("トークン", "詳細", "補足"), sections.map { it.title })
        assertEquals(
            listOf("入力トークン", "生成トークン", "合計トークン", "トークン取得元"),
            sections[0].items.map { it.label },
        )
        assertEquals(listOf("100（推定）", "240（推定）", "340（推定）", "バックエンド"), sections[0].items.map { it.value })
        assertEquals(
            listOf("速度取得元", "表示速度", "バックエンド基準速度", "Lami基準TTFT", "バックエンド基準TTFT", "モデルロード時間", "入力評価時間", "生成時間", "推論時間"),
            sections[1].items.map { it.label },
        )
        assertEquals(
            listOf("推定", "—", "—", "—", "—", "2.0 s（取得済み）", "1.5 s（取得済み）", "3.0 s（取得済み）", "—（未取得）"),
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
            listOf("入力トークン", "生成トークン", "合計トークン", "トークン取得元"),
            sections[0].items.map { it.label },
        )
        assertEquals(
            listOf("速度取得元", "表示速度", "バックエンド基準速度", "体感速度", "Lami基準TTFT", "バックエンド基準TTFT", "モデルロード時間", "入力評価時間", "生成時間", "推論時間"),
            sections[1].items.map { it.label },
        )
        assertEquals("32.4 token/s", sections[1].items.first { it.label == "バックエンド基準速度" }.value)
        assertEquals("48.0 token/s", sections[1].items.first { it.label == "体感速度" }.value)
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
            listOf("入力トークン", "生成トークン", "合計トークン", "トークン取得元"),
            sections[0].items.map { it.label },
        )
        assertEquals(
            listOf("速度取得元", "表示速度", "バックエンド基準速度", "Lami基準TTFT", "バックエンド基準TTFT", "モデルロード時間", "入力評価時間", "生成時間", "推論時間"),
            sections[1].items.map { it.label },
        )
    }

    @Test
    fun `buildInferenceDetailSections keeps placeholder when values are missing`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

        assertEquals(listOf("—（未取得）", "—（未取得）", "—（未取得）"), sections[0].items.take(3).map { it.value })
        assertEquals(listOf("—（未取得）", "—（未取得）", "—（未取得）", "—（未取得）"), sections[1].items.takeLast(4).map { it.value })
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
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = trace,
        )

        assertEquals(
            listOf("入力トークン数（未取得）", "出力トークン数（未取得）", "合計トークン（未取得）", "トークン取得元"),
            sections[0].items.map { it.label },
        )
        assertEquals(listOf("—（未取得）", "—（未取得）", "—（未取得）", "未取得"), sections[0].items.map { it.value })
        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("0", devSection.items.first { it.label == "直近 Prefill Token" }.value)
        assertEquals("42", devSection.items.first { it.label == "直近 Decode Token" }.value)
    }

    @Test
    fun `buildInferenceDetailSections shows official chunk timing diagnostics`() {
        val trace = LocalInferenceTrace(
            assistantUpdateCount = 4,
            appendEventsPerSecond = 22.5,
            officialChunkCount = 4,
            officialChunkIntervalAvgMs = 850.5,
            officialChunkIntervalMaxMs = 1_800L,
            officialChunkIntervalMinMs = 100L,
            officialChunkFirstToLastMs = 2_551L,
            officialChunkCharsAvg = 8.5,
            officialChunkCharsMax = 12,
            officialChunkCharsMin = 0,
            officialChunkEmptyCount = 1,
            officialChunkNonEmptyCount = 3,
            officialChunkEventsPerSecond = 1.6,
            officialChunkCharsPerSecond = 13.3,
        )

        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = trace,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("4", devSection.items.first { it.label == "officialChunkCount" }.value)
        assertEquals("850.5 ms", devSection.items.first { it.label == "officialChunkIntervalAvgMs" }.value)
        assertEquals("1800 ms", devSection.items.first { it.label == "officialChunkIntervalMaxMs" }.value)
        assertEquals("2551 ms", devSection.items.first { it.label == "officialChunkFirstToLastMs" }.value)
        assertEquals("8.5 chars", devSection.items.first { it.label == "officialChunkCharsAvg" }.value)
        assertEquals("1.6 events/s", devSection.items.first { it.label == "officialChunkEventsPerSecond" }.value)
        assertEquals("13.3 chars/s", devSection.items.first { it.label == "officialChunkCharsPerSecond" }.value)
        assertEquals("1", devSection.items.first { it.label == "officialChunkEmptyCount" }.value)
        assertEquals("official-chunk-sparse", devSection.items.first { it.label == "Streaming bottleneck hint" }.value)

        val summarySection = sections.first { it.title == "DEV診断サマリー" }
        assertEquals("official-chunk-sparse", summarySection.items.first { it.label == "Streaming bottleneck hint" }.value)
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
            ),
            sections[0].items.map { it.label },
        )
        assertEquals(
            listOf(
                "速度取得元",
                "表示速度",
                "バックエンド基準速度",
                "Lami基準TTFT",
                "バックエンド基準TTFT",
                "Decode時間",
                "総応答時間",
            ),
            sections[1].items.take(7).map { it.label },
        )
        assertEquals(listOf("未取得 / バックエンド基準（Engine時間）", "—", "21.5 token/s", "420 ms", "—", "2.8 s", "3.4 s"), sections[1].items.take(7).map { it.value })
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
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = trace,
        )

        assertEquals(
            listOf("入力トークン数（Tokenizer基準）", "出力トークン数（Tokenizer基準）", "合計トークン（Tokenizer基準）"),
            sections[0].items.take(3).map { it.label },
        )
        assertEquals(listOf("12（Tokenizer）", "34（Tokenizer）", "46（Tokenizer）"), sections[0].items.take(3).map { it.value })
        val summarySection = sections.first { it.title == "DEV診断サマリー" }
        assertEquals("成功", summarySection.items.first { it.label == "Tokenizer再計数" }.value)
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
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = trace,
        )

        assertEquals(
            listOf("入力トークン数（推定）", "出力トークン数（推定）", "合計トークン（推定）"),
            sections[0].items.take(3).map { it.label },
        )
        val summarySection = sections.first { it.title == "DEV診断サマリー" }
        assertEquals("未取得", summarySection.items.first { it.label == "Tokenizer再計数" }.value)
    }

    @Test
    fun `buildInferenceDetailSections marks generation fallback when using evalDurationNs`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(
                evalDurationNs = 1_200_000_000L,
            ),
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

        assertEquals("—", sections[1].items[2].value)
        assertEquals("1.2 s（取得済み）", sections[1].items.first { it.label == "推論時間" }.value)
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
    fun `buildInferenceStatsFullCopyText includes memory recovery check in developer copy`() {
        val text = buildInferenceStatsFullCopyText(
            stats = InferenceStats(modelName = "local-dev"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            sections = emptyList(),
            detailSections = emptyList(),
            memoryRecoveryCheckState = MemoryRecoveryCheckState(
                status = MEMORY_RECOVERY_STATUS_COMPLETED,
                startedAtMs = 1234L,
                snapshots = listOf(
                    memorySnapshot(
                        stage = MEMORY_STAGE_MEMORY_RECOVERY_CURRENT,
                        totalPssMb = 300,
                        nativeHeapPssMb = 100,
                        nativeHeapAllocatedMb = 24,
                        dalvikHeapPssMb = 50,
                        availableSystemMemoryMb = 1000,
                    ),
                    memorySnapshot(
                        stage = MEMORY_STAGE_MEMORY_RECOVERY_DELAYED_1S,
                        totalPssMb = 292,
                        nativeHeapPssMb = 86,
                        nativeHeapAllocatedMb = 23,
                        dalvikHeapPssMb = 49,
                        availableSystemMemoryMb = 1012,
                    ),
                    memorySnapshot(
                        stage = MEMORY_STAGE_MEMORY_RECOVERY_DELAYED_3S,
                        totalPssMb = 280,
                        nativeHeapPssMb = 70,
                        nativeHeapAllocatedMb = 22,
                        dalvikHeapPssMb = 48,
                        availableSystemMemoryMb = 1024,
                    ),
                    memorySnapshot(
                        stage = MEMORY_STAGE_MEMORY_RECOVERY_DELAYED_5S,
                        totalPssMb = 276,
                        nativeHeapPssMb = 66,
                        nativeHeapAllocatedMb = 22,
                        dalvikHeapPssMb = 48,
                        availableSystemMemoryMb = 1030,
                    ),
                ),
            ),
        )

        assertTrue(text.contains("[DEV診断: App/System memory recovery check]"))
        assertTrue(text.contains("recovery_check_status=completed"))
        assertTrue(text.contains("recovery_check_started_at_ms=1234"))
        assertTrue(text.contains("measurement_note=api_derived_approximate_may_not_match_adb_dumpsys_meminfo"))
        assertTrue(text.contains("adb_compare_hint=compare_with_adb_shell_dumpsys_meminfo_package"))
        assertTrue(text.contains("ui_note="))
        assertTrue(text.contains("memory_stage=memory_recovery_current"))
        assertTrue(text.contains("memory_stage=memory_recovery_delayed_1s"))
        assertTrue(text.contains("memory_stage=memory_recovery_delayed_3s"))
        assertTrue(text.contains("memory_stage=memory_recovery_delayed_5s"))
        assertTrue(text.contains("[DEV診断: App/System memory recovery delta]"))
        assertTrue(text.contains("delta_from_stage=memory_recovery_current"))
        assertTrue(text.contains("delta_to_stage=memory_recovery_delayed_1s"))
        assertTrue(text.contains("delta_to_stage=memory_recovery_delayed_3s"))
        assertTrue(text.contains("delta_to_stage=memory_recovery_delayed_5s"))
        assertTrue(text.contains("total_pss_delta_mb=-8"))
        assertTrue(text.contains("native_heap_pss_delta_mb=-14"))
        assertTrue(text.contains("native_heap_alloc_delta_mb=-1"))
        assertTrue(text.contains("dalvik_heap_pss_delta_mb=-1"))
        assertTrue(text.contains("system_available_memory_delta_mb=+12"))
        assertTrue(!text.contains("NPU memory"))
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

    private fun memorySnapshot(
        stage: String,
        totalPssMb: Long?,
        nativeHeapPssMb: Long?,
        nativeHeapAllocatedMb: Long?,
        dalvikHeapPssMb: Long?,
        availableSystemMemoryMb: Long?,
    ): MemorySnapshot = MemorySnapshot(
        timestampMs = 1L,
        stage = stage,
        javaHeapUsedMb = 1L,
        javaHeapMaxMb = 2L,
        totalRssMb = null,
        totalSwapPssMb = null,
        nativeHeapPssMb = nativeHeapPssMb,
        nativeHeapRssMb = null,
        nativeHeapAllocatedMb = nativeHeapAllocatedMb,
        nativeHeapSizeMb = 4L,
        dalvikHeapPssMb = dalvikHeapPssMb,
        dalvikHeapRssMb = null,
        dalvikHeapAllocatedMb = 1L,
        dalvikHeapSizeMb = 2L,
        totalPssMb = totalPssMb,
        privateDirtyMb = 1L,
        privateCleanMb = 1L,
        graphicsPssMb = null,
        stackPssMb = null,
        codePssMb = null,
        systemPssMb = null,
        unknownPssMb = null,
        availableSystemMemoryMb = availableSystemMemoryMb,
        systemMemoryThresholdMb = 9L,
        lowMemory = false,
        threadName = "test",
    )


    @Test
    fun `buildInferenceDetailSections adds accelerator probe rows in developer mode`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceBoard = "board-x",
                androidSdk = 35,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = null,
                gpuRenderer = null,
                gpuVersion = null,
                nnapiAvailable = true,
                nnapiDeprecatedWarning = true,
                nnapiDevices = emptyList(),
                probeSource = "test",
            ),
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("deprecated on Android 15+", devSection.items.first { it.label == "NNAPI warning" }.value)
        assertEquals("none/unknown", devSection.items.first { it.label == "NNAPI devices" }.value)
    }

    @Test
    fun `buildInferenceDetailSections handles accelerator probe unknown values safely`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = null,
                deviceModel = null,
                deviceBoard = null,
                androidSdk = 34,
                supportedAbis = emptyList(),
                cpuCoreCount = null,
                cpuAbi = null,
                gpuVendor = null,
                gpuRenderer = null,
                gpuVersion = null,
                nnapiAvailable = false,
                nnapiDeprecatedWarning = false,
                nnapiDevices = emptyList(),
                probeSource = "test",
                probeError = "error",
            ),
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("unknown", devSection.items.first { it.label == "GPU検出情報" }.value)
        assertEquals("error", devSection.items.first { it.label == "Error" }.value)
    }

    @Test
    fun `buildInferenceDetailSections shows gpu probe and inference rows when available`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceBoard = "board-x",
                androidSdk = 34,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = "Qualcomm",
                gpuRenderer = "Adreno",
                gpuVersion = "OpenGL ES 3.2",
                nnapiAvailable = false,
                nnapiDeprecatedWarning = false,
                nnapiDevices = emptyList(),
                probeSource = "test",
                gpuProbeSource = "egl-pbuffer",
                gpuProbeError = "egl fallback",
            ),
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertTrue(devSection.items.first { it.label == "GPU検出情報" }.value.contains("Adreno"))
        assertEquals("egl-pbuffer", devSection.items.first { it.label == "GPU Probe" }.value)
        assertEquals("egl fallback", devSection.items.first { it.label == "GPU Probe Error" }.value)
        assertEquals("gpu-possible / low", devSection.items.first { it.label == "実行経路推定" }.value)
    }

    @Test
    fun `buildInferenceDetailSections shows delegate probe rows and hides error when absent`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceBoard = "board-x",
                androidSdk = 35,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = null,
                gpuRenderer = null,
                gpuVersion = null,
                nnapiAvailable = true,
                nnapiDeprecatedWarning = true,
                nnapiDevices = emptyList(),
                probeSource = "test",
                delegateProbeSource = "reflection-safe",
                delegateSwitchingSupportedHint = "not-detected",
            ),
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("reflection-safe", devSection.items.first { it.label == "Delegate API Probe" }.value)
        assertEquals("not-detected", devSection.items.first { it.label == "Delegate switching hint" }.value)
        assertEquals("none/unknown", devSection.items.first { it.label == "Delegate option candidates" }.value)
        assertEquals("none/unknown", devSection.items.first { it.label == "Delegate backend enum values" }.value)
        assertEquals("none/unknown", devSection.items.first { it.label == "Delegate preferredBackend signatures" }.value)
        assertTrue(devSection.items.none { it.label == "Delegate Probe Error" })
        assertTrue(devSection.items.none { it.label == "Delegate backend enum probe error" })
        assertTrue(devSection.items.none { it.label == "Delegate preferredBackend signature error" })
    }

    @Test
    fun `buildInferenceDetailSections shows external qairt staged facts in developer mode`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "nubia",
                deviceModel = "NX733J",
                deviceBoard = "kalama",
                androidSdk = 35,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = "Qualcomm",
                gpuRenderer = "Adreno",
                gpuVersion = "OpenGL ES 3.2",
                nnapiAvailable = true,
                nnapiDeprecatedWarning = true,
                nnapiDevices = emptyList(),
                probeSource = "test",
                externalQairtStageStatus = "present",
                externalQairtStagePath = "/data/local/tmp/qairt",
                externalQairtQnnNetRunStatus = "available",
                externalQairtQnnPlatformValidatorStatus = "available",
                externalQairtQnnSdkVersion = "v2.46.0.260424121129",
                externalQairtGpuBackendStatus = "passed",
                externalQairtDspCore = "Hexagon Architecture V79",
                externalQairtDspBackendStatus = "passed",
                externalQairtNote = "adb-verified external stage facts",
            ),
        )

        val externalQairtSection = sections.first { it.title == "DEV診断: External QAIRT" }
        assertEquals("passed", externalQairtSection.items.first { it.label == "External QAIRT stage" }.value)
        assertEquals("/data/local/tmp/qairt", externalQairtSection.items.first { it.label == "QAIRT stage path" }.value)
        assertEquals("available", externalQairtSection.items.first { it.label == "qnn-net-run" }.value)
        assertEquals("available", externalQairtSection.items.first { it.label == "qnn-platform-validator" }.value)
        assertEquals("v2.46.0.260424121129", externalQairtSection.items.first { it.label == "QNN SDK version" }.value)
        assertEquals("passed", externalQairtSection.items.first { it.label == "External QNN GPU" }.value)
        assertEquals("Hexagon Architecture V79", externalQairtSection.items.first { it.label == "QNN DSP core" }.value)
        assertEquals("passed", externalQairtSection.items.first { it.label == "External QNN DSP/HTP" }.value)
        assertEquals("adb-verified external stage facts", externalQairtSection.items.first { it.label == "QAIRT stage note" }.value)
    }


    @Test
    fun `buildInferenceDetailSections shows npu probe none unknown by default`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceBoard = "board-x",
                androidSdk = 35,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = null,
                gpuRenderer = null,
                gpuVersion = null,
                nnapiAvailable = true,
                nnapiDeprecatedWarning = true,
                nnapiDevices = emptyList(),
                probeSource = "test",
                delegateProbeSource = "reflection-safe",
                npuProbeHint = "not-detected",
            ),
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("not-detected", devSection.items.first { it.label == "NPU probe hint" }.value)
        assertEquals("probe-only (not applied)", devSection.items.first { it.label == "NPU status" }.value)
        assertEquals("disabled (forced GPU fallback)", devSection.items.first { it.label == "NPU apply status" }.value)
        assertEquals("NPU backend candidate detected via reflection. Currently disabled for safety; GPU fallback is used for actual inference.", devSection.items.first { it.label == "NPU note" }.value)
        assertEquals("none/unknown", devSection.items.first { it.label == "NPU delegate candidates" }.value)
        assertEquals("unknown", devSection.items.first { it.label == "Backend NPU probe hint" }.value)
        assertEquals("none/unknown", devSection.items.first { it.label == "Backend NPU class candidates" }.value)
        assertEquals("none/unknown", devSection.items.first { it.label == "Backend NPU method candidates" }.value)
        assertEquals("none/unknown", devSection.items.first { it.label == "Backend NPU constructor signatures" }.value)
        assertEquals("unknown", devSection.items.first { it.label == "Backend NPU nativeLibraryDir required" }.value)
        assertEquals("probe-only", devSection.items.first { it.label == "NPU stage probe" }.value)
        assertEquals("false", devSection.items.first { it.label == "NPU constructor available" }.value)
        assertEquals("false", devSection.items.first { it.label == "NPU string constructor available" }.value)
        assertEquals("unknown", devSection.items.first { it.label == "NPU nativeLibraryDir candidate" }.value)
        assertEquals("unknown", devSection.items.first { it.label == "NPU stage probe result" }.value)
        assertEquals("—", devSection.items.first { it.label == "NPU stage probe error" }.value)
        assertEquals("none/unknown", devSection.items.first { it.label == "QNN candidates" }.value)
        assertEquals("not-detected", devSection.items.first { it.label == "QNN status" }.value)
        assertEquals("not-detected", devSection.items.first { it.label == "NNAPI delegate status" }.value)
        assertNotEquals("npu-active / high", devSection.items.first { it.label == "実行経路推定" }.value)
    }

    @Test
    fun `buildInferenceDetailSections shows qnn candidates and keeps conservative execution`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceBoard = "board-x",
                androidSdk = 35,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = "Qualcomm",
                gpuRenderer = "Adreno",
                gpuVersion = "OpenGL ES 3.2",
                nnapiAvailable = true,
                nnapiDeprecatedWarning = true,
                nnapiDevices = emptyList(),
                probeSource = "test",
                delegateProbeSource = "reflection-safe",
                qnnDelegateCandidates = listOf("LlmInferenceOptions.Builder.setQnnDelegate"),
                npuProbeHint = "qnn-candidate-detected",
            ),
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("qnn-candidate-detected", devSection.items.first { it.label == "NPU probe hint" }.value)
        assertEquals("LlmInferenceOptions.Builder.setQnnDelegate", devSection.items.first { it.label == "QNN candidates" }.value)
        assertEquals("candidate-detected", devSection.items.first { it.label == "QNN status" }.value)
        assertEquals("gpu-possible / low", devSection.items.first { it.label == "実行経路推定" }.value)
    }

    @Test
    fun `buildInferenceDetailSections shows backend npu constructor candidates without npu active`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceBoard = "board-x",
                androidSdk = 35,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = null,
                gpuRenderer = null,
                gpuVersion = null,
                nnapiAvailable = true,
                nnapiDeprecatedWarning = true,
                nnapiDevices = emptyList(),
                probeSource = "test",
                delegateProbeSource = "reflection-safe",
                backendNpuClassCandidates = listOf("Backend.NPU"),
                backendNpuMethodCandidates = listOf("NPU.nativeLibraryDir(String): NPU"),
                backendNpuConstructorSignatures = listOf("NPU(String): Backend"),
                backendNpuNativeLibraryDirRequired = "true",
                backendNpuProbeHint = "npu-backend-native-library-dir-candidate",
                npuConstructorAvailable = true,
                npuStringConstructorAvailable = true,
                npuNativeLibraryDirCandidate = "unknown",
                npuStageProbeResult = "safe",
            ),
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("npu-backend-native-library-dir-candidate", devSection.items.first { it.label == "Backend NPU probe hint" }.value)
        assertEquals("Backend.NPU", devSection.items.first { it.label == "Backend NPU class candidates" }.value)
        assertEquals("NPU.nativeLibraryDir(String): NPU", devSection.items.first { it.label == "Backend NPU method candidates" }.value)
        assertEquals("NPU(String): Backend", devSection.items.first { it.label == "Backend NPU constructor signatures" }.value)
        assertEquals("true", devSection.items.first { it.label == "Backend NPU nativeLibraryDir required" }.value)
        assertEquals("probe-only", devSection.items.first { it.label == "NPU stage probe" }.value)
        assertEquals("true", devSection.items.first { it.label == "NPU constructor available" }.value)
        assertEquals("true", devSection.items.first { it.label == "NPU string constructor available" }.value)
        assertEquals("unknown", devSection.items.first { it.label == "NPU nativeLibraryDir candidate" }.value)
        assertEquals("safe", devSection.items.first { it.label == "NPU stage probe result" }.value)
        assertNotEquals("npu-active / high", devSection.items.first { it.label == "実行経路推定" }.value)
    }

    @Test
    fun `buildInferenceDetailSections shows delegate probe error and execution reason note`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                officialFlowUsed = true,
            ),
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceBoard = "board-x",
                androidSdk = 35,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = "Qualcomm",
                gpuRenderer = "Adreno",
                gpuVersion = "OpenGL ES 3.2",
                nnapiAvailable = true,
                nnapiDeprecatedWarning = true,
                nnapiDevices = listOf("nnapi-device"),
                probeSource = "test",
                delegateProbeSource = "reflection-safe",
                delegateProbeError = "ClassNotFoundException",
                delegateOptionCandidates = listOf("LlmInferenceOptions.Builder.setPreferredBackend"),
                delegateBackendCandidates = listOf("LlmInferenceOptions.Backend"),
                delegateBackendEnumValues = listOf("CPU", "GPU"),
                delegateBackendEnumProbeError = "ClassNotFoundException",
                delegatePreferredBackendSignatures = listOf("Builder.setPreferredBackend(Backend): Builder"),
                delegatePreferredBackendSignatureProbeError = "ClassNotFoundException",
                delegateClassCandidates = listOf("LlmInferenceOptions.Builder"),
                delegateSwitchingSupportedHint = "delegate-api-candidate-detected",
            ),
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("ClassNotFoundException", devSection.items.first { it.label == "Delegate Probe Error" }.value)
        assertEquals("CPU, GPU", devSection.items.first { it.label == "Delegate backend enum values" }.value)
        assertEquals("ClassNotFoundException", devSection.items.first { it.label == "Delegate backend enum probe error" }.value)
        assertEquals("Builder.setPreferredBackend(Backend): Builder", devSection.items.first { it.label == "Delegate preferredBackend signatures" }.value)
        assertEquals("ClassNotFoundException", devSection.items.first { it.label == "Delegate preferredBackend signature error" }.value)
        assertEquals("accelerator-unknown / low", devSection.items.first { it.label == "実行経路推定" }.value)
        assertTrue(devSection.items.first { it.label == "推定理由" }.value.contains("delegate API candidate detected"))
    }

    @Test
    fun `buildInferenceDetailSections keeps execution inference conservative when preferred backend signatures exist`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceBoard = "board-x",
                androidSdk = 35,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = null,
                gpuRenderer = null,
                gpuVersion = null,
                nnapiAvailable = true,
                nnapiDeprecatedWarning = true,
                nnapiDevices = emptyList(),
                probeSource = "test",
                delegateProbeSource = "reflection-safe",
                delegateOptionCandidates = listOf("Builder.setPreferredBackend"),
                delegateBackendCandidates = listOf("LlmInference.Backend"),
                delegateBackendEnumValues = listOf("DEFAULT", "CPU", "GPU"),
                delegatePreferredBackendSignatures = listOf("Builder.setPreferredBackend(Backend): Builder"),
            ),
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("Builder.setPreferredBackend(Backend): Builder", devSection.items.first { it.label == "Delegate preferredBackend signatures" }.value)
        assertEquals("npu-candidate / low", devSection.items.first { it.label == "実行経路推定" }.value)
    }

    @Test
    fun `buildInferenceDetailSections shows preferred backend dry-run rows for GPU and keeps conservative execution`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceBoard = "board-x",
                androidSdk = 35,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = null,
                gpuRenderer = null,
                gpuVersion = null,
                nnapiAvailable = true,
                nnapiDeprecatedWarning = true,
                nnapiDevices = emptyList(),
                probeSource = "test",
            ),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("GPU", devSection.items.first { it.label == "Requested preferredBackend" }.value)
        assertEquals("not-applied", devSection.items.first { it.label == "Applied backend" }.value)
        assertEquals("not-supported", devSection.items.first { it.label == "PreferredBackend apply result" }.value)
        assertEquals("npu-candidate / low", devSection.items.first { it.label == "実行経路推定" }.value)
    }

    @Test
    fun `buildInferenceDetailSections marks Generic LiteRT-LM CPU as stable baseline`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                mediaPipeProbeModelPath = "/models/gemma-4-E2B-it.litertlm",
                requestedPreferredBackend = "CPU",
                appliedPreferredBackend = "CPU",
                preferredBackendApplyResult = "applied-engine-config",
                preferredBackendHookReached = true,
            ),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.CPU,
        )

        val summarySection = sections.first { it.title == "DEV診断サマリー" }
        assertEquals("CPU stable baseline", summarySection.items.first { it.label == "実行基準" }.value)
        assertEquals("generic-litertlm", summarySection.items.first { it.label == "model_kind" }.value)
        assertEquals("CPU", summarySection.items.first { it.label == "preferred_backend" }.value)
        assertEquals("cpu_stable_baseline", summarySection.items.first { it.label == "baseline_role" }.value)
        assertEquals("true", summarySection.items.first { it.label == "generic_model_cpu_baseline" }.value)
    }

    @Test
    fun `buildInferenceDetailSections marks Generic LiteRT-LM GPU as experimental baseline`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                mediaPipeProbeModelPath = "/models/gemma-4-E2B-it.litertlm",
                requestedPreferredBackend = "GPU",
                appliedPreferredBackend = "GPU",
                preferredBackendApplyResult = "applied-engine-config",
                preferredBackendHookReached = true,
            ),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
        )

        val summarySection = sections.first { it.title == "DEV診断サマリー" }
        assertEquals("GPU experimental", summarySection.items.first { it.label == "実行基準" }.value)
        assertEquals("generic-litertlm", summarySection.items.first { it.label == "model_kind" }.value)
        assertEquals("GPU", summarySection.items.first { it.label == "preferred_backend" }.value)
        assertEquals("gpu_experimental", summarySection.items.first { it.label == "baseline_role" }.value)
        assertEquals("false", summarySection.items.first { it.label == "generic_model_cpu_baseline" }.value)
        assertEquals(
            "GPU初期化で停止する場合はCPUを選択してください",
            summarySection.items.first { it.label == "注意" }.value,
        )
    }

    @Test
    fun `buildInferenceDetailSections shows preferred backend dry-run rows for DEFAULT`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceBoard = "board-x",
                androidSdk = 35,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = null,
                gpuRenderer = null,
                gpuVersion = null,
                nnapiAvailable = true,
                nnapiDeprecatedWarning = true,
                nnapiDevices = emptyList(),
                probeSource = "test",
            ),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("DEFAULT", devSection.items.first { it.label == "Requested preferredBackend" }.value)
        assertEquals("not-applied", devSection.items.first { it.label == "Applied backend" }.value)
        assertEquals("skipped-default", devSection.items.first { it.label == "PreferredBackend apply result" }.value)
    }


    @Test
    fun `buildInferenceDetailSections shows preferred backend apply diagnostics details`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                requestedPreferredBackend = "GPU",
                appliedPreferredBackend = "not-applied",
                preferredBackendApplyResult = "not-supported",
                preferredBackendApplyError = "NoSuchMethodException",
                preferredBackendApplyBuilderClass = "com.example.OptionsBuilder",
                preferredBackendApplyMethodCandidates = listOf("setPreferredBackend(Backend): Builder"),
                preferredBackendApplyBackendEnumCandidates = listOf("DEFAULT", "CPU", "GPU"),
                preferredBackendApplyNotSupportedReason = "no-setPreferredBackend-method",
            ),
            acceleratorProbeSnapshot = preferredBackendProbeSnapshot(),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("com.example.OptionsBuilder", devSection.items.first { it.label == "PreferredBackend builder class" }.value)
        assertEquals("setPreferredBackend(Backend): Builder", devSection.items.first { it.label == "PreferredBackend method candidates" }.value)
        assertEquals("DEFAULT, CPU, GPU", devSection.items.first { it.label == "PreferredBackend backend enum candidates" }.value)
        assertEquals("no-setPreferredBackend-method", devSection.items.first { it.label == "PreferredBackend not-supported reason" }.value)
        assertEquals("npu-candidate / low", devSection.items.first { it.label == "実行経路推定" }.value)
    }

    @Test
    fun `buildInferenceDetailSections shows preferred backend rows for NPU applied`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                requestedPreferredBackend = "NPU",
                appliedPreferredBackend = "NPU",
                preferredBackendApplyResult = "applied-engine-config",
                preferredBackendHookReached = true,
                preferredBackendHookSource = "holder-acquire-engine-config",
                preferredBackendApplyBackendEnumCandidates = listOf("DEFAULT", "CPU", "GPU", "NPU"),
            ),
            acceleratorProbeSnapshot = preferredBackendProbeSnapshot(),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.NPU,
        )
        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("NPU", devSection.items.first { it.label == "Requested preferredBackend" }.value)
        assertEquals("NPU", devSection.items.first { it.label == "Applied backend" }.value)
        assertEquals("applied-engine-config", devSection.items.first { it.label == "PreferredBackend apply result" }.value)
        assertTrue(devSection.items.none { it.label == "Effective backend note" })
        assertEquals("holder-acquire-engine-config", devSection.items.first { it.label == "PreferredBackend hook source" }.value)
        assertEquals("DEFAULT, CPU, GPU, NPU", devSection.items.first { it.label == "PreferredBackend backend enum candidates" }.value)
        assertEquals("qnn-npu-likely / medium", devSection.items.first { it.label == "実行経路推定" }.value)
        assertEquals("qnn-npu-likely / medium", sections.first { it.title == "DEV診断サマリー" }.items.first { it.label == "推定実行先" }.value)
    }

    @Test
    fun `buildInferenceDetailSections shows NPU engine-create fallback to GPU`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                requestedPreferredBackend = "NPU",
                appliedPreferredBackend = "GPU",
                preferredBackendApplyResult = "fallback-gpu-after-npu-engine-create-failed",
                preferredBackendApplyError = "IllegalStateException",
                preferredBackendHookReached = true,
            ),
            acceleratorProbeSnapshot = preferredBackendProbeSnapshot(),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.NPU,
        )
        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("fallback-gpu-after-npu-engine-create-failed", devSection.items.first { it.label == "PreferredBackend apply result" }.value)
        assertEquals("IllegalStateException", devSection.items.first { it.label == "PreferredBackend apply error" }.value)
        assertEquals("gpu-fallback / high", devSection.items.first { it.label == "実行経路推定" }.value)
    }

    @Test
    fun `buildInferenceDetailSections reports NPU request as not applied without trace`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = AcceleratorProbeSnapshot(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceBoard = "board-x",
                androidSdk = 35,
                supportedAbis = listOf("arm64-v8a"),
                cpuCoreCount = 8,
                cpuAbi = "arm64-v8a",
                gpuVendor = null,
                gpuRenderer = null,
                gpuVersion = null,
                nnapiAvailable = true,
                nnapiDeprecatedWarning = true,
                nnapiDevices = emptyList(),
                probeSource = "test",
            ),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.NPU,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("NPU", devSection.items.first { it.label == "Requested preferredBackend" }.value)
        assertEquals("not-applied", devSection.items.first { it.label == "Applied backend" }.value)
        assertEquals("not-supported", devSection.items.first { it.label == "PreferredBackend apply result" }.value)
        assertTrue(devSection.items.none { it.label == "Effective backend note" })
    }

    @Test
    fun `buildInferenceDetailSections shows NPU runtime fallback to GPU`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                requestedPreferredBackend = "NPU",
                appliedPreferredBackend = "GPU",
                preferredBackendApplyResult = "fallback-gpu-after-npu-runtime-failed",
                preferredBackendApplyError = "IllegalStateException:initialize failed",
                preferredBackendHookReached = true,
            ),
            acceleratorProbeSnapshot = preferredBackendProbeSnapshot(),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.NPU,
        )
        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("fallback-gpu-after-npu-runtime-failed", devSection.items.first { it.label == "PreferredBackend apply result" }.value)
        assertEquals("IllegalStateException:initialize failed", devSection.items.first { it.label == "PreferredBackend apply error" }.value)
        assertEquals("gpu-fallback / high", devSection.items.first { it.label == "実行経路推定" }.value)
    }

    @Test
    fun `buildInferenceDetailSections shows preferred backend engine recreate diagnostic when held engine is reused`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                requestedPreferredBackend = "GPU",
                appliedPreferredBackend = "not-applied",
                preferredBackendHookReached = false,
                heldEngineCreatePath = "holder-existing-engine",
                preferredBackendHookMissingReason = "holder-existing-engine",
            ),
            acceleratorProbeSnapshot = preferredBackendProbeSnapshot(),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("true", devSection.items.first { it.label == "PreferredBackend requires engine recreate" }.value)
        assertEquals(
            "requested preferredBackend requires a new held engine; current run reused existing engine",
            devSection.items.first { it.label == "PreferredBackend recreate reason" }.value,
        )
        assertEquals("npu-candidate / low", devSection.items.first { it.label == "実行経路推定" }.value)
    }


    @Test
    fun `buildInferenceDetailSections derives preferred backend engine recreate when trace requested is null and dry-run is GPU`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                requestedPreferredBackend = null,
                appliedPreferredBackend = "not-applied",
                preferredBackendHookReached = false,
                heldEngineCreatePath = "holder-existing-engine",
                preferredBackendHookMissingReason = "holder-existing-engine",
                preferredBackendRequiresEngineRecreate = false,
            ),
            acceleratorProbeSnapshot = preferredBackendProbeSnapshot(),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("GPU", devSection.items.first { it.label == "Requested preferredBackend" }.value)
        assertTrue(devSection.items.none { it.label == "PreferredBackend resolver dry-run setting" })
        assertTrue(devSection.items.none { it.label == "PreferredBackend resolver requested" })
        assertTrue(devSection.items.none { it.label == "PreferredBackend resolver heldExistingEngine" })
        assertTrue(devSection.items.none { it.label == "PreferredBackend resolver hookNotReached" })
        assertTrue(devSection.items.none { it.label == "PreferredBackend resolver missingReasonHeldExisting" })
        assertEquals("true", devSection.items.first { it.label == "PreferredBackend requires engine recreate" }.value)
        assertEquals(
            "requested preferredBackend requires a new held engine; current run reused existing engine",
            devSection.items.first { it.label == "PreferredBackend recreate reason" }.value,
        )
    }

    @Test
    fun `buildInferenceDetailSections derives preferred backend engine recreate when trace flag is false`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                requestedPreferredBackend = "GPU",
                appliedPreferredBackend = "not-applied",
                preferredBackendHookReached = false,
                heldEngineCreatePath = "holder-existing-engine",
                preferredBackendHookMissingReason = "holder-existing-engine",
                preferredBackendRequiresEngineRecreate = false,
            ),
            acceleratorProbeSnapshot = preferredBackendProbeSnapshot(),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("true", devSection.items.first { it.label == "PreferredBackend requires engine recreate" }.value)
        assertEquals(
            "requested preferredBackend requires a new held engine; current run reused existing engine",
            devSection.items.first { it.label == "PreferredBackend recreate reason" }.value,
        )
    }


    @Test
    fun `buildInferenceDetailSections treats null preferred backend hook as not reached for recreate diagnostic`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                requestedPreferredBackend = "GPU",
                appliedPreferredBackend = "not-applied",
                preferredBackendHookReached = null,
                heldEngineCreatePath = "holder-existing-engine",
                preferredBackendHookMissingReason = "holder-existing-engine",
                preferredBackendRequiresEngineRecreate = false,
            ),
            acceleratorProbeSnapshot = preferredBackendProbeSnapshot(),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertTrue(devSection.items.none { it.label == "PreferredBackend resolver hookNotReached" })
        assertEquals("true", devSection.items.first { it.label == "PreferredBackend requires engine recreate" }.value)
    }

    @Test
    fun `buildInferenceDetailSections hides preferred backend recreate diagnostic when recreate is not required`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                requestedPreferredBackend = "GPU",
                appliedPreferredBackend = "GPU",
                preferredBackendHookReached = true,
                heldEngineCreatePath = "holder-created-engine-config",
                preferredBackendRequiresEngineRecreate = false,
            ),
            acceleratorProbeSnapshot = preferredBackendProbeSnapshot(),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertTrue(devSection.items.none { it.label == "PreferredBackend requires engine recreate" })
        assertTrue(devSection.items.none { it.label == "PreferredBackend recreate reason" })
    }

    private fun preferredBackendProbeSnapshot(): AcceleratorProbeSnapshot =
        AcceleratorProbeSnapshot(
            deviceManufacturer = "Google",
            deviceModel = "Pixel",
            deviceBoard = "board-x",
            androidSdk = 35,
            supportedAbis = listOf("arm64-v8a"),
            cpuCoreCount = 8,
            cpuAbi = "arm64-v8a",
            gpuVendor = null,
            gpuRenderer = null,
            gpuVersion = null,
            nnapiAvailable = true,
            nnapiDeprecatedWarning = true,
            nnapiDevices = emptyList(),
            probeSource = "test",
        )
}
