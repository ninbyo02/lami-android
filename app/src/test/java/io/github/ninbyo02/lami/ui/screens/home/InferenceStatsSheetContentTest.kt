package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.ContextWindowFetchState
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `buildInferenceStatsFullCopyText includes holder create close dump in developer copy`() {
        val diagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCreateCalled = true,
            nativeCloseCalled = true,
            nativeDiagnosticsCalled = true,
            holderCreateRequested = true,
            holderCreateSucceeded = true,
            holderId = "native-holder-1",
            holderOpen = false,
            holderCloseRequested = true,
            holderCloseSucceeded = true,
            holderDoubleCloseSafe = true,
            status = "closed",
            reason = "holder_closed_without_decode",
        )
        val text = buildInferenceStatsFullCopyText(
            stats = InferenceStats(modelName = "local-dev"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            sections = emptyList(),
            detailSections = emptyList(),
            npuPersistentHolderCreateCloseState = NpuPersistentHolderCreateCloseProbeState(
                status = "completed",
                reason = "holder_closed_without_decode",
                createResult = NpuPersistentHolderApiResult(
                    status = "created",
                    reason = "app_jni_holder_lifecycle_created_without_engine_create",
                    holderId = "native-holder-1",
                    diagnostics = diagnostics,
                    nativeSummary = formatNpuPersistentHolderNativeStubProbeSummary(diagnostics),
                ),
                diagnosticsAfterCreate = diagnostics,
                closeResult = NpuPersistentHolderApiResult(
                    status = "closed",
                    reason = "holder_closed_without_decode",
                    holderId = "native-holder-1",
                    diagnostics = diagnostics,
                    nativeSummary = formatNpuPersistentHolderNativeStubProbeSummary(diagnostics),
                ),
                diagnosticsAfterClose = diagnostics,
            ),
        )

        assertTrue(text.contains("[DEV診断: NPU persistent holder create close full dump]"))
        assertTrue(text.contains("test_name=NPU Persistent Holder Create Close Probe"))
        assertTrue(text.contains("holder_create_called=true"))
        assertTrue(text.contains("holder_close_called=true"))
        assertTrue(text.contains("native_run_called=false"))
        assertTrue(text.contains("npu_decode_called=false"))
        assertTrue(text.contains("generate_called=false"))
        assertTrue(text.contains("qnn_decode_called=false"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
    }

    @Test
    fun `buildInferenceStatsFullCopyText includes holder run once dump in developer copy`() {
        val text = buildInferenceStatsFullCopyText(
            stats = InferenceStats(modelName = "local-dev"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            sections = emptyList(),
            detailSections = emptyList(),
            npuPersistentHolderRunOnceState = NpuPersistentHolderRunOnceProbeState(
                status = "completed",
                reason = "success",
                runResult = NpuPersistentHolderApiResult(
                    status = "run_ready",
                    reason = "holder_open_existing_one_shot_decode_may_run_once",
                    holderId = "native-holder-1",
                    diagnostics = npuPersistentHolderNativeStubDiagnostics(
                        nativeRunCalled = true,
                        holderId = "native-holder-1",
                        holderOpenBeforeRun = true,
                        runOnceRequested = true,
                        runOnceSupported = true,
                        status = "run_ready",
                        reason = "holder_open_existing_one_shot_decode_may_run_once",
                    ),
                ),
                decodeResult = NpuPersistentHolderRunOnceDecodeResult(
                    status = "success",
                    reason = "success",
                    runDecodeReached = "true",
                    rawOutput = "raw",
                    sanitizedOutput = "sanitized",
                    qualityClassification = "natural_japanese",
                    backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                    fallbackUsed = "false",
                    timeout = "false",
                    freshCrash = "false",
                ),
            ),
        )

        assertTrue(text.contains("[DEV診断: NPU persistent holder run once full dump]"))
        assertTrue(text.contains("test_name=NPU Persistent Holder Run Once Probe"))
        assertTrue(text.contains("run_once_requested=true"))
        assertTrue(text.contains("run_once_called=true"))
        assertTrue(text.contains("run_once_supported=true"))
        assertTrue(text.contains("run_once_succeeded=true"))
        assertTrue(text.contains("run_decode_reached=true"))
        assertTrue(text.contains("backend_evidence=QNN_HTP_V79_FastRPC_native_diag"))
        assertTrue(text.contains("fallback_used=false"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
    }

    @Test
    fun `buildInferenceStatsFullCopyText includes holder two turn dump in developer copy`() {
        val text = buildInferenceStatsFullCopyText(
            stats = InferenceStats(modelName = "local-dev"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            sections = emptyList(),
            detailSections = emptyList(),
            npuPersistentHolderTwoTurnState = NpuPersistentHolderTwoTurnProbeState(
                status = "completed",
                reason = "success",
                turns = listOf(
                    NpuPersistentHolderTwoTurnRecord(
                        turnIndex = 1,
                        prompt = "こんにちは",
                        runResult = NpuPersistentHolderApiResult(
                            status = "run_ready",
                            reason = "holder_open_existing_one_shot_decode_may_run_once",
                            holderId = "native-holder-1",
                            diagnostics = npuPersistentHolderNativeStubDiagnostics(
                                nativeRunCalled = true,
                                runOnceRequested = true,
                                runOnceSupported = true,
                            ),
                        ),
                        decodeResult = NpuPersistentHolderRunOnceDecodeResult(
                            status = "success",
                            reason = "success",
                            runDecodeReached = "true",
                            qualityClassification = "natural_japanese",
                            backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                            fallbackUsed = "false",
                            timeout = "false",
                            freshCrash = "false",
                        ),
                    ),
                    NpuPersistentHolderTwoTurnRecord(
                        turnIndex = 2,
                        prompt = "あなたは誰ですか",
                        runResult = NpuPersistentHolderApiResult(
                            status = "run_ready",
                            reason = "holder_open_existing_one_shot_decode_may_run_once",
                            holderId = "native-holder-1",
                            diagnostics = npuPersistentHolderNativeStubDiagnostics(
                                nativeRunCalled = true,
                                runOnceRequested = true,
                                runOnceSupported = true,
                            ),
                        ),
                        decodeResult = NpuPersistentHolderRunOnceDecodeResult(
                            status = "success",
                            reason = "success",
                            runDecodeReached = "true",
                            qualityClassification = "natural_japanese",
                            backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                            fallbackUsed = "false",
                            timeout = "false",
                            freshCrash = "false",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(text.contains("[DEV診断: NPU persistent holder two turn full dump]"))
        assertTrue(text.contains("test_name=NPU Persistent Holder Two Turn Probe"))
        assertTrue(text.contains("run_count_requested=2"))
        assertTrue(text.contains("turn1_run_decode_reached=true"))
        assertTrue(text.contains("turn2_run_decode_reached=true"))
        assertTrue(text.contains("run_decode_reached_count=2"))
        assertTrue(text.contains("fallback_used_count=0"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
    }

    @Test
    fun `buildInferenceStatsFullCopyText includes holder five turn dump in developer copy`() {
        val text = buildInferenceStatsFullCopyText(
            stats = InferenceStats(modelName = "local-dev"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            sections = emptyList(),
            detailSections = emptyList(),
            npuPersistentHolderFiveTurnState = NpuPersistentHolderFiveTurnProbeState(
                status = "completed",
                reason = "success",
                turns = (1..5).map { index ->
                    NpuPersistentHolderTwoTurnRecord(
                        turnIndex = index,
                        prompt = "prompt$index",
                        runResult = NpuPersistentHolderApiResult(
                            status = "run_ready",
                            reason = "holder_open_existing_one_shot_decode_may_run_once",
                            holderId = "native-holder-1",
                            diagnostics = npuPersistentHolderNativeStubDiagnostics(
                                nativeRunCalled = true,
                                runOnceRequested = true,
                                runOnceSupported = true,
                            ),
                        ),
                        decodeResult = NpuPersistentHolderRunOnceDecodeResult(
                            status = "success",
                            reason = "success",
                            runDecodeReached = "true",
                            qualityClassification = "natural_japanese",
                            backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                            fallbackUsed = "false",
                            timeout = "false",
                            freshCrash = "false",
                        ),
                    )
                },
            ),
        )

        assertTrue(text.contains("[DEV診断: NPU persistent holder five turn full dump]"))
        assertTrue(text.contains("test_name=NPU Persistent Holder Five Turn Probe"))
        assertTrue(text.contains("run_count_requested=5"))
        assertTrue(text.contains("turn1_run_decode_reached=true"))
        assertTrue(text.contains("turn5_run_decode_reached=true"))
        assertTrue(text.contains("run_decode_reached_count=5"))
        assertTrue(text.contains("backend_evidence_summary=QNN_HTP_V79_FastRPC_native_diag:5"))
        assertTrue(text.contains("fallback_used_count=0"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
    }

    @Test
    fun `buildInferenceStatsFullCopyText includes holder ten turn dump in developer copy`() {
        val text = buildInferenceStatsFullCopyText(
            stats = InferenceStats(modelName = "local-dev"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            sections = emptyList(),
            detailSections = emptyList(),
            npuPersistentHolderTenTurnState = NpuPersistentHolderTenTurnProbeState(
                status = "completed",
                reason = "success",
                turns = (1..10).map { index ->
                    NpuPersistentHolderTwoTurnRecord(
                        turnIndex = index,
                        prompt = "prompt$index",
                        runResult = NpuPersistentHolderApiResult(
                            status = "run_ready",
                            reason = "holder_open_existing_one_shot_decode_may_run_once",
                            holderId = "native-holder-1",
                            diagnostics = npuPersistentHolderNativeStubDiagnostics(
                                nativeRunCalled = true,
                                runOnceRequested = true,
                                runOnceSupported = true,
                            ),
                        ),
                        decodeResult = NpuPersistentHolderRunOnceDecodeResult(
                            status = "success",
                            reason = "success",
                            runDecodeReached = "true",
                            qualityClassification = "natural_japanese",
                            backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                            fallbackUsed = "false",
                            timeout = "false",
                            freshCrash = "false",
                        ),
                    )
                },
            ),
        )

        assertTrue(text.contains("[DEV診断: NPU persistent holder ten turn full dump]"))
        assertTrue(text.contains("test_name=NPU Persistent Holder Ten Turn Probe"))
        assertTrue(text.contains("run_count_requested=10"))
        assertTrue(text.contains("turn1_run_decode_reached=true"))
        assertTrue(text.contains("turn10_run_decode_reached=true"))
        assertTrue(text.contains("run_decode_reached_count=10"))
        assertTrue(text.contains("success_count=10"))
        assertTrue(text.contains("success_rate=1.00"))
        assertTrue(text.contains("backend_evidence_summary=QNN_HTP_V79_FastRPC_native_diag:10"))
        assertTrue(text.contains("fallback_rate=0.00"))
        assertTrue(text.contains("true_engine_persistent_reuse=false"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
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

    @Test
    fun `buildGpuDiagnosticKeysCopyText includes executor internal surface and quality keys`() {
        val traceText = """
            LOCAL_ROUTE_DIAG preferred_backend=GPU selected_model_name=gemma selected_model_file=gemma.litertlm gpu_output_quality_matrix_mode=edge_gallery_executor_probe edge_gallery_executor_probe_result=same_sampler_different_executor edge_gallery_executor_difference_summary=same_sampler_lami_runtime_decode_fragmentation_executor_selection_suspected edge_gallery_generate_api_candidate=generateResponse executor_selection_fingerprint=exec runtime_backend_fingerprint=backend runtime_executor_fingerprint=runtime runtime_dispatch_fingerprint=dispatch runtime_compiled_model_fingerprint=compiled engine_config_fingerprint=engine conversation_config_fingerprint=conversation sampler_config_fingerprint=sampler gpu_internal_surface_probe_enabled=true gpu_internal_surface_probe_result=completed gpu_internal_surface_probe_disabled_reason=none gpu_output_quality_candidate_result=quality_candidate_fail gpu_output_quality_gate_status=fail gpu_output_quality_promotion_blocker=true gpu_output_quality_summary=runtime_callback_source_corruption_suspected gpu_sampler_root_cause_candidate=runtime_decode_fragmentation gpu_output_source_corruption_stage=raw_callback callback_corruption_earliest_stage=raw_callback callback_quality_classification=severe_fragmentation gpu_fragmentation_score=0.816 gpu_output_suspicious_fragment_detected=true gpu_output_suspicious_fragment_reason=many_tiny_fragments gpu_callback_invoked_count=323 gpu_callback_empty_text_count=13 gpu_callback_non_empty_text_count=310 gpu_output_callback_chunk_count=323 gpu_output_raw_callback_text_head=head gpu_output_raw_callback_text_tail=tail gpu_output_final_assistant_text_head=final_head gpu_output_final_assistant_text_tail=final_tail gpu_perf_engine_acquire_elapsed_ms=10 gpu_perf_engine_create_or_reuse=reuse gpu_perf_generate_to_first_token_ms=300 gpu_perf_callback_total_elapsed_ms=1200 gpu_perf_slow_path_detected=false gpu_perf_slow_path_reason=none
        """.trimIndent()
        val text = buildGpuDiagnosticKeysCopyText(
            stats = InferenceStats(localSourceSummary = "source_summary=$traceText"),
        )

        assertTrue(text.startsWith("[GPU diagnostic keys]"))
        assertTrue(text.contains("selected_backend=GPU"))
        assertTrue(text.contains("requested_backend=GPU"))
        assertTrue(text.contains("effective_backend=GPU"))
        assertTrue(text.contains("route_family=local_gpu"))
        assertTrue(text.contains("backend_evidence=gpu_route"))
        assertTrue(text.contains("edge_gallery_executor_probe_result=same_sampler_different_executor"))
        assertTrue(text.contains("gpu_internal_surface_probe_enabled=true"))
        assertTrue(text.contains("gpu_internal_surface_probe_result=completed"))
        assertTrue(text.contains("gpu_output_quality_promotion_blocker=true"))
        assertTrue(text.contains("gpu_sampler_root_cause_candidate=runtime_decode_fragmentation"))
        assertTrue(text.contains("gpu_output_raw_callback_text_tail=tail"))
    }

    @Test
    fun `buildGpuDiagnosticKeysCopyText uses unavailable for missing keys and is CPU safe`() {
        val text = buildGpuDiagnosticKeysCopyText(
            stats = InferenceStats(
                localSourceSummary = """
                    selected_backend=CPU
                    requested_backend=CPU
                    effective_backend=CPU
                    route_family=local_cpu
                    backend_evidence=cpu_route
                """.trimIndent(),
            ),
        )

        assertTrue(text.contains("selected_backend=CPU"))
        assertTrue(text.contains("route_family=local_cpu"))
        assertTrue(text.contains("edge_gallery_executor_probe_result=unavailable"))
        assertTrue(text.contains("gpu_internal_surface_probe_enabled=unavailable"))
        assertTrue(text.contains("gpu_output_quality_promotion_blocker=unavailable"))
    }

    @Test
    fun `buildGpuInternalSurfaceKeysCopyText only includes internal surface keys`() {
        val text = buildGpuInternalSurfaceKeysCopyText(
            stats = InferenceStats(
                localSourceSummary = """
                    gpu_internal_surface_probe_enabled=true
                    gpu_internal_surface_probe_result=completed_with_missing_symbols
                    gpu_internal_surface_probe_disabled_reason=none
                    gpu_internal_runtime_config_class_present=false
                    gpu_internal_backend_constraint_class_present=true
                    gpu_internal_preferred_engine_type_class_present=false
                    gpu_internal_gpu_options_class_present=true
                    gpu_internal_artisan_class_present=false
                    gpu_internal_llm_gpu_artisan_executor_symbol_present=true
                    gpu_internal_kv_cache_symbol_present=true
                    gpu_internal_runtime_config_methods=builder,setBackend
                    gpu_internal_backend_constraint_methods=matchGpu
                    gpu_internal_gpu_options_methods=createFromToml
                    gpu_internal_probe_exception_class=none
                    gpu_internal_probe_exception_message=none
                    edge_gallery_executor_probe_result=same_sampler_different_executor
                """.trimIndent(),
            ),
        )

        assertTrue(text.startsWith("[GPU internal surface keys]"))
        assertTrue(text.contains("gpu_internal_surface_probe_enabled=true"))
        assertTrue(text.contains("gpu_internal_surface_probe_result=completed_with_missing_symbols"))
        assertTrue(text.contains("gpu_internal_surface_probe_disabled_reason=none"))
        assertTrue(text.contains("gpu_internal_runtime_config_class_present=false"))
        assertTrue(text.contains("gpu_internal_backend_constraint_class_present=true"))
        assertTrue(text.contains("gpu_internal_preferred_engine_type_class_present=false"))
        assertTrue(text.contains("gpu_internal_gpu_options_class_present=true"))
        assertTrue(text.contains("gpu_internal_artisan_class_present=false"))
        assertTrue(text.contains("gpu_internal_llm_gpu_artisan_executor_symbol_present=true"))
        assertTrue(text.contains("gpu_internal_kv_cache_symbol_present=true"))
        assertTrue(text.contains("gpu_internal_runtime_config_methods=builder,setBackend"))
        assertTrue(text.contains("gpu_internal_backend_constraint_methods=matchGpu"))
        assertTrue(text.contains("gpu_internal_gpu_options_methods=createFromToml"))
        assertTrue(text.contains("gpu_internal_probe_exception_class=none"))
        assertTrue(text.contains("gpu_internal_probe_exception_message=none"))
        assertFalse(text.contains("edge_gallery_executor_probe_result="))
    }

    @Test
    fun `buildGpuInternalSurfaceKeysCopyText keeps stable unavailable shape for missing keys`() {
        val text = buildGpuInternalSurfaceKeysCopyText(
            stats = InferenceStats(
                localSourceSummary = """
                    selected_backend=CPU
                    route_family=local_cpu
                """.trimIndent(),
            ),
        )

        assertTrue(text.startsWith("[GPU internal surface keys]"))
        assertTrue(text.contains("gpu_internal_surface_probe_enabled=unavailable"))
        assertTrue(text.contains("gpu_internal_surface_probe_result=unavailable"))
        assertTrue(text.contains("gpu_internal_surface_probe_disabled_reason=unavailable"))
        assertTrue(text.contains("gpu_internal_runtime_config_class_present=unavailable"))
        assertTrue(text.contains("gpu_internal_llm_gpu_artisan_executor_symbol_present=unavailable"))
        assertTrue(text.contains("gpu_internal_probe_exception_message=unavailable"))
    }

    @Test
    fun `buildNpuDiagnosticKeysCopyText includes NPU promotion gate keys`() {
        val traceText = """
            status=success reason=success selected_backend=NPU requested_backend=NPU effective_backend=NPU route_family=standard_chat_screen_s5_npu_tts backend_evidence=QNN_HTP_V79_FastRPC npu_backend_evidence=QNN_HTP_V79_FastRPC fallback_used=false fresh_crash=false timeout=false selected_path_npu_saved=true normal_ui_route_connected=true standard_route_connected=true npu_standard_route_dev_gate_enabled=true npu_standard_route_phase=1 npu_standard_route_phase_name=1_route_entry_diagnostic npu_standard_route_connected=true npu_standard_route_quality_gate_passed=unavailable npu_standard_route_output_suppressed=false npu_standard_route_suppression_reason=none npu_standard_route_generate_diagnostic_only=false npu_standard_route_output_delivery_allowed=false npu_standard_route_candidate_text_present=true npu_standard_route_ui_append_allowed=false npu_standard_route_ui_append_source=not_allowed_before_phase4 npu_standard_route_ui_appended_text_length=0 npu_standard_route_ui_append_block_reason=phase_not_ui_append npu_standard_route_ui_append_executed=false npu_standard_route_ui_append_visible_candidate=false npu_standard_route_ui_append_target=none npu_standard_route_ui_append_failure_reason=phase_not_ui_append npu_standard_route_tts_allowed=false npu_standard_route_tts_source=not_allowed_before_phase5 npu_standard_route_tts_text_length=0 npu_standard_route_tts_block_reason=phase_not_tts npu_standard_route_tts_requested=false npu_standard_route_tts_started=false npu_standard_route_tts_execution_block_reason=phase_not_tts npu_standard_route_db_save_allowed=false npu_standard_route_markdown_allowed=false npu_standard_route_streaming_allowed=false npu_standard_route_output_delivery_executed=false npu_standard_route_delivery_path=phase_gate_suppressed npu_standard_route_rollback_required=false npu_standard_route_rollback_reason=none conversation_created=false generate_response=false quality_classification=natural_japanese db=true tts=true markdown=true streaming=true cleanup_status=success engine_close_evidence=present fresh_tombstone_status=none
        """.trimIndent()
        val text = buildNpuDiagnosticKeysCopyText(
            stats = InferenceStats(localSourceSummary = "source_summary=$traceText"),
        )

        assertTrue(text.startsWith("[NPU diagnostic keys]"))
        assertTrue(text.contains("status=success"))
        assertTrue(text.contains("reason=success"))
        assertTrue(text.contains("selected_backend=NPU"))
        assertTrue(text.contains("route_family=standard_chat_screen_s5_npu_tts"))
        assertTrue(text.contains("backend_evidence=QNN_HTP_V79_FastRPC"))
        assertTrue(text.contains("npu_backend_evidence=QNN_HTP_V79_FastRPC"))
        assertTrue(text.contains("fallback_used=false"))
        assertTrue(text.contains("fresh_crash=false"))
        assertTrue(text.contains("timeout=false"))
        assertTrue(text.contains("standard_route_connected=true"))
        assertTrue(text.contains("npu_standard_route_dev_gate_enabled=true"))
        assertTrue(text.contains("npu_standard_route_phase=1"))
        assertTrue(text.contains("npu_standard_route_phase_name=1_route_entry_diagnostic"))
        assertTrue(text.contains("npu_standard_route_connected=true"))
        assertTrue(text.contains("npu_standard_route_output_suppressed=false"))
        assertTrue(text.contains("npu_standard_route_generate_diagnostic_only=false"))
        assertTrue(text.contains("npu_standard_route_output_delivery_allowed=false"))
        assertTrue(text.contains("npu_standard_route_candidate_text_present=true"))
        assertTrue(text.contains("npu_standard_route_ui_append_allowed=false"))
        assertTrue(text.contains("npu_standard_route_ui_append_source=not_allowed_before_phase4"))
        assertTrue(text.contains("npu_standard_route_ui_appended_text_length=0"))
        assertTrue(text.contains("npu_standard_route_ui_append_block_reason=phase_not_ui_append"))
        assertTrue(text.contains("npu_standard_route_ui_append_executed=false"))
        assertTrue(text.contains("npu_standard_route_ui_append_visible_candidate=false"))
        assertTrue(text.contains("npu_standard_route_ui_append_target=none"))
        assertTrue(text.contains("npu_standard_route_ui_append_failure_reason=phase_not_ui_append"))
        assertTrue(text.contains("npu_standard_route_tts_allowed=false"))
        assertTrue(text.contains("npu_standard_route_tts_source=not_allowed_before_phase5"))
        assertTrue(text.contains("npu_standard_route_tts_text_length=0"))
        assertTrue(text.contains("npu_standard_route_tts_block_reason=phase_not_tts"))
        assertTrue(text.contains("npu_standard_route_tts_requested=false"))
        assertTrue(text.contains("npu_standard_route_tts_started=false"))
        assertTrue(text.contains("npu_standard_route_tts_execution_block_reason=phase_not_tts"))
        assertTrue(text.contains("npu_standard_route_db_save_allowed=false"))
        assertTrue(text.contains("npu_standard_route_db_save_executed=unavailable"))
        assertTrue(text.contains("npu_standard_route_db_save_target=unavailable"))
        assertTrue(text.contains("npu_standard_route_db_saved_text_length=unavailable"))
        assertTrue(text.contains("npu_standard_route_db_assistant_id_present=unavailable"))
        assertTrue(text.contains("npu_standard_route_db_save_block_reason=unavailable"))
        assertTrue(text.contains("npu_standard_route_db_message_replaced_transient=unavailable"))
        assertTrue(text.contains("npu_standard_route_db_conversation_id_present=unavailable"))
        assertTrue(text.contains("npu_standard_route_markdown_allowed=false"))
        assertTrue(text.contains("npu_standard_route_markdown_executed=unavailable"))
        assertTrue(text.contains("npu_standard_route_markdown_mode=unavailable"))
        assertTrue(text.contains("npu_standard_route_markdown_block_reason=unavailable"))
        assertTrue(text.contains("npu_standard_route_streaming_allowed=false"))
        assertTrue(text.contains("npu_standard_route_streaming_executed=unavailable"))
        assertTrue(text.contains("npu_standard_route_streaming_mode=unavailable"))
        assertTrue(text.contains("npu_standard_route_streaming_source=unavailable"))
        assertTrue(text.contains("npu_standard_route_streaming_chunk_count=unavailable"))
        assertTrue(text.contains("npu_standard_route_streaming_final_text_length=unavailable"))
        assertTrue(text.contains("npu_standard_route_streaming_block_reason=unavailable"))
        assertTrue(text.contains("npu_standard_route_native_streaming_used=unavailable"))
        assertTrue(text.contains("npu_standard_route_streaming_text_matches_db=unavailable"))
        assertTrue(text.contains("npu_standard_route_streaming_text_matches_markdown=unavailable"))
        assertTrue(text.contains("npu_standard_route_output_delivery_executed=false"))
        assertTrue(text.contains("npu_standard_route_delivery_path=phase_gate_suppressed"))
        assertTrue(text.contains("npu_standard_route_rollback_required=false"))
        assertTrue(text.contains("quality_classification=natural_japanese"))
        assertTrue(text.contains("cleanup_status=success"))
        assertTrue(text.contains("engine_close_evidence=present"))
    }

    @Test
    fun `buildNpuDiagnosticKeysCopyText uses unavailable for missing keys and is GPU safe`() {
        val text = buildNpuDiagnosticKeysCopyText(
            stats = InferenceStats(
                localSourceSummary = """
                    selected_backend=GPU
                    requested_backend=GPU
                    effective_backend=GPU
                    route_family=local_gpu
                    backend_evidence=gpu_route
                """.trimIndent(),
            ),
        )

        assertTrue(text.startsWith("[NPU diagnostic keys]"))
        assertTrue(text.contains("selected_backend=GPU"))
        assertTrue(text.contains("fallback_used=unavailable"))
        assertTrue(text.contains("standard_route_connected=unavailable"))
        assertTrue(text.contains("npu_standard_route_ui_append_source=unavailable"))
        assertTrue(text.contains("npu_standard_route_ui_append_block_reason=unavailable"))
        assertTrue(text.contains("npu_standard_route_ui_append_executed=unavailable"))
        assertTrue(text.contains("npu_standard_route_ui_append_visible_candidate=unavailable"))
        assertTrue(text.contains("npu_standard_route_ui_append_target=unavailable"))
        assertTrue(text.contains("npu_standard_route_ui_append_failure_reason=unavailable"))
        assertTrue(text.contains("npu_standard_route_tts_source=unavailable"))
        assertTrue(text.contains("npu_standard_route_tts_block_reason=unavailable"))
        assertTrue(text.contains("npu_standard_route_tts_started=unavailable"))
        assertTrue(text.contains("npu_standard_route_output_delivery_executed=unavailable"))
        assertTrue(text.contains("quality_classification=unavailable"))
        assertTrue(text.contains("fresh_tombstone_status=unavailable"))
    }

    @Test
    fun `route diagnostic copy button labels remain distinct`() {
        assertEquals("GPU診断キーをコピー", GPU_DIAGNOSTIC_COPY_BUTTON_LABEL)
        assertEquals("GPU内部surfaceキーをコピー", GPU_INTERNAL_SURFACE_COPY_BUTTON_LABEL)
        assertEquals("NPU診断キーをコピー", NPU_DIAGNOSTIC_COPY_BUTTON_LABEL)
        assertNotEquals(GPU_DIAGNOSTIC_COPY_BUTTON_LABEL, GPU_INTERNAL_SURFACE_COPY_BUTTON_LABEL)
        assertNotEquals(GPU_DIAGNOSTIC_COPY_BUTTON_LABEL, NPU_DIAGNOSTIC_COPY_BUTTON_LABEL)
        assertNotEquals(GPU_INTERNAL_SURFACE_COPY_BUTTON_LABEL, NPU_DIAGNOSTIC_COPY_BUTTON_LABEL)
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
