package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GpuBlockingRaceContractTest {
    private val gpuContext = LocalRouteDiagnosticContext(
        selectedModelName = "generic",
        selectedModelFile = "model.litertlm",
        preferredBackend = "GPU",
        npuStandardRouteMode = "OFF",
        effectiveNpuStandardRouteMode = "OFF",
        shouldEnterNpuS1 = false,
        localRouteEntered = true,
        normalChatNativeRouteBlocked = false,
        blockedReason = "none",
        modelKind = "generic_model",
        baselineRole = LITERT_LM_BASELINE_GPU_EXPERIMENTAL,
        genericModelCpuBaseline = false,
    )

    @Test
    fun `standard normal GPU held blocking never uses watchdog or detached timeout`() {
        val policy = resolveGpuExperimentalTimeoutPolicy(
            context = gpuContext,
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
            currentFlavor = "standard",
        )

        assertFalse(policy.useFirstTokenWatchdog)
        assertFalse(policy.useDetachedOperationTimeout)
    }

    @Test
    fun `callback and Flow diagnostic probes retain experimental timeout`() {
        listOf("raw_callback_only", GPU_GENERATE_PROBE_MODE_MAX_TOKENS_32).forEach { probeMode ->
            val policy = resolveGpuExperimentalTimeoutPolicy(
                context = gpuContext,
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                gpuGenerateProbeMode = probeMode,
                currentFlavor = "standard",
            )
            assertTrue(policy.useFirstTokenWatchdog)
            assertTrue(policy.useDetachedOperationTimeout)
        }
    }

    @Test
    fun `debug callback property cannot move standard normal GPU off held blocking`() {
        assertTrue(
            shouldUseHeldOfficialBlockingFastPath(
                currentFlavor = "standard",
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                callbackStreamingDebugPropertyEnabled = true,
            ),
        )
    }

    @Test
    fun `explicit callback probes stay off held blocking`() {
        listOf(
            GPU_GENERATE_PROBE_MODE_RAW_CALLBACK_ONLY,
            GPU_GENERATE_PROBE_MODE_NORMAL_CALLBACK_STREAMING,
        ).forEach { probeMode ->
            assertFalse(
                shouldUseHeldOfficialBlockingFastPath(
                    currentFlavor = "standard",
                    preferredBackend = PreferredBackendDryRunSetting.GPU,
                    gpuGenerateProbeMode = probeMode,
                    callbackStreamingDebugPropertyEnabled = true,
                ),
            )
        }
    }

    @Test
    fun `non standard or non GPU routes stay off held GPU blocking`() {
        assertFalse(
            shouldUseHeldOfficialBlockingFastPath(
                currentFlavor = "standardGpuNoConstraintProvider",
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                callbackStreamingDebugPropertyEnabled = false,
            ),
        )
        assertFalse(
            shouldUseHeldOfficialBlockingFastPath(
                currentFlavor = "standard",
                preferredBackend = PreferredBackendDryRunSetting.CPU,
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                callbackStreamingDebugPropertyEnabled = false,
            ),
        )
    }

    @Test
    fun `active standard GPU generate defers onStop independently of transient debug gate`() {
        assertEquals(
            GpuOnStopLifecycleAction.DEFER_ACTIVE_GENERATE,
            resolveGpuOnStopLifecycleAction(
                gpuGenerateActive = true,
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                transientProtectionEnabled = false,
                recentSuccess = false,
            ),
        )
    }

    @Test
    fun `foreground return reuses deferred engine and real background releases only after generate finishes`() {
        assertEquals(
            listOf(
                GpuOnStopLifecycleAction.DEFER_ACTIVE_GENERATE,
                GpuOnStopLifecycleAction.KEEP_FOREGROUND_REUSE,
                GpuOnStopLifecycleAction.RELEASE_CONFIRMED_BACKGROUND,
            ),
            resolveGpuLifecycleRaceSequenceForTest(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
            ),
        )
    }

    @Test
    fun `held GPU cancellation clears active generation before rethrow`() {
        val source = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt",
        ).readText()
        val heldRunFailureBlock = source
            .substringAfter("val response = runCatching {")
            .substringBefore("if (response == null)")
            .substringAfterLast("}.getOrElse { throwable ->")
            .substringBefore("failureDiagnosticsText")
        val nonCancellableIndex = heldRunFailureBlock.indexOf("withContext(NonCancellable)")
        val finishIndex = heldRunFailureBlock.indexOf(
            "engineHolder.recordGpuGenerationFinishedForDiagnostics(success = false)",
        )
        val rethrowIndex = heldRunFailureBlock.indexOf("throw throwable")

        assertTrue("GPU cancellation cleanup must be non-cancellable", nonCancellableIndex >= 0)
        assertTrue("GPU cancellation must clear active generation", finishIndex >= 0)
        assertTrue("NonCancellable scope must wrap cleanup", nonCancellableIndex < finishIndex)
        assertTrue("GPU cancellation cleanup must precede rethrow", finishIndex < rethrowIndex)

        val heldRun = source
            .substringAfter("internal suspend fun runWithHeldEngine(")
            .substringBefore("internal data class LocalOfficialConversationApiProbeResult(")
        val finishCallCount = Regex(
            """recordGpuGenerationFinishedForDiagnostics\(success = (?:true|false)\)""",
        ).findAll(heldRun).count()
        val protectedFinishCallCount = Regex(
            """withContext\(NonCancellable\)\s*\{\s*engineHolder\.recordGpuGenerationFinishedForDiagnostics\(success = (?:true|false)\)""",
        ).findAll(heldRun).count()
        val successCleanupIndex = heldRun.indexOf(
            "engineHolder.recordGpuGenerationFinishedForDiagnostics(success = true)",
        )
        val closeSummaryIndex = heldRun.indexOf("emitCloseSummaryTrace(")

        assertEquals("Every GPU finish path must be present", 3, finishCallCount)
        assertEquals("Every GPU finish path must be NonCancellable", finishCallCount, protectedFinishCallCount)
        assertTrue("Success cleanup must precede post-response close summaries", successCleanupIndex < closeSummaryIndex)
    }

    @Test
    fun `NPU success does not start GPU or CPU`() = runBlocking {
        val calls = mutableListOf<String>()

        val result = runInferenceBackendWithFallback(
            primaryBackend = { calls += "NPU"; "npu response" },
            shouldFallback = { it.isBlank() },
            fallbackBackend = {
                runInferenceBackendWithFallback(
                    primaryBackend = { calls += "GPU"; "gpu response" },
                    shouldFallback = { it.isBlank() },
                    fallbackBackend = { calls += "CPU"; "cpu response" },
                )
            },
        )

        assertEquals("npu response", result)
        assertEquals(listOf("NPU"), calls)
    }

    @Test
    fun `NPU quality rejection starts GPU and GPU success skips CPU`() = runBlocking {
        val calls = mutableListOf<String>()

        val result = runInferenceBackendWithFallback(
            primaryBackend = { calls += "NPU"; "" },
            shouldFallback = { it.isBlank() },
            fallbackBackend = {
                runInferenceBackendWithFallback(
                    primaryBackend = { calls += "GPU"; "gpu response" },
                    shouldFallback = { it.isBlank() },
                    fallbackBackend = { calls += "CPU"; "cpu response" },
                )
            },
        )

        assertEquals("gpu response", result)
        assertEquals(listOf("NPU", "GPU"), calls)
    }

    @Test
    fun `GPU rejected result and non cancellation exception each start CPU`() = runBlocking {
        val rejectedCalls = mutableListOf<String>()
        val rejectedResult = runInferenceBackendWithFallback(
            primaryBackend = { rejectedCalls += "GPU"; "" },
            shouldFallback = { it.isBlank() },
            fallbackBackend = { rejectedCalls += "CPU"; "cpu after rejected result" },
        )
        assertEquals("cpu after rejected result", rejectedResult)
        assertEquals(listOf("GPU", "CPU"), rejectedCalls)

        val exceptionCalls = mutableListOf<String>()
        val exceptionResult = runInferenceBackendWithFallback(
            primaryBackend = {
                exceptionCalls += "GPU"
                throw IllegalStateException("gpu exploded")
            },
            shouldFallback = { false },
            fallbackBackend = { exceptionCalls += "CPU"; "cpu after exception" },
        )
        assertEquals("cpu after exception", exceptionResult)
        assertEquals(listOf("GPU", "CPU"), exceptionCalls)
    }

    @Test
    fun `NPU stop cancellation starts neither GPU nor CPU`() = runBlocking {
        val calls = mutableListOf<String>()
        try {
            runInferenceBackendWithFallback(
                primaryBackend = {
                    calls += "NPU"
                    throw CancellationException("stop")
                },
                shouldFallback = { false },
                fallbackBackend = {
                    calls += "GPU"
                    runInferenceBackendWithFallback(
                        primaryBackend = { "gpu response" },
                        shouldFallback = { false },
                        fallbackBackend = { calls += "CPU"; "cpu response" },
                    )
                },
            )
            fail("CancellationException must be rethrown")
        } catch (_: CancellationException) {
            // expected
        }
        assertEquals(listOf("NPU"), calls)
    }

    @Test
    fun `all backend failures retain the original NPU reason and final attempted path`() {
        val source = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val resultFailureBlock = source
            .substringAfter("shouldFallbackNpuStandardRouteFailureToLocal(s1Result)")
            .substringBefore("var npuStandardRouteUiAppendExecuted")

        assertTrue(resultFailureBlock.contains("runInferenceBackendChain("))
        assertTrue(resultFailureBlock.contains("NPU,GPU,CPU"))
        assertTrue(resultFailureBlock.contains("s1Result.reason"))
        assertTrue(resultFailureBlock.contains("failureDiagnostics"))
    }

    @Test
    fun `automatic cancellation never invokes fallback backend`() = runBlocking {
        var primaryInvocationCount = 0
        var fallbackInvocationCount = 0

        try {
            runInferenceBackendWithFallback(
                primaryBackend = {
                    primaryInvocationCount += 1
                    throw CancellationException("stop")
                },
                shouldFallback = { false },
                fallbackBackend = {
                    fallbackInvocationCount += 1
                    "fallback"
                },
            )
            fail("CancellationException must be rethrown")
        } catch (_: CancellationException) {
            // Contract: cancellation is control flow, never an ordinary backend failure.
        }

        assertEquals(1, primaryInvocationCount)
        assertEquals(0, fallbackInvocationCount)
    }

    @Test
    fun `automatic generic route starts with explicit GPU and falls back to explicit CPU`() {
        val source = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val automaticBlock = source
            .substringAfter("if (preferredBackendDryRunSetting != PreferredBackendDryRunSetting.DEFAULT)")
            .substringBefore("val savedChatLamiAvatarSizeDp")

        assertTrue(
            "Automatic generic inference must not enter LiteRT Backend.DEFAULT/local_default.",
            automaticBlock.contains(
                "primaryBackend = { runWithBackend(PreferredBackendDryRunSetting.GPU) }",
            ),
        )
        assertTrue(
            "Automatic generic inference must retain CPU as the fallback after GPU failure.",
            automaticBlock.contains(
                "fallbackBackend = { runWithBackend(PreferredBackendDryRunSetting.CPU) }",
            ),
        )
        assertFalse(
            "Automatic generic inference must never execute Backend.DEFAULT.",
            automaticBlock.contains(
                "primaryBackend = { runWithBackend(PreferredBackendDryRunSetting.DEFAULT) }",
            ),
        )
    }

    @Test
    fun `cold Automatic uses configured eligible NPU model before runtime history exists`() {
        val source = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val automaticPlanBlock = source
            .substringAfter("val automaticNpuModelEligibility")
            .substringBefore("val selectedLocalModelSlot")

        assertTrue(
            "Automatic must derive cold-start NPU usability from the configured model's static eligibility.",
            automaticPlanBlock.contains("npuSupported = automaticNpuModelEligibility.npuModelEligible"),
        )
        assertTrue(
            "Automatic must not require a previous successful NPU history entry before trying an eligible model.",
            automaticPlanBlock.contains("npuHealthy = automaticNpuModelEligibility.npuModelEligible"),
        )
        assertTrue(
            "The NPU plan entry must require the same eligibility used by the real S1 route.",
            automaticPlanBlock.contains("npuModelAvailable = automaticNpuModelEligibility.npuModelEligible"),
        )
    }

    @Test
    fun `blocking timing bars hide inseparable generation and preserve total remainder`() {
        val breakdown = requireNotNull(
            buildInferenceTimeBreakdown(
                InferenceStats(
                    evalDurationNs = 2_200_000_000L,
                    totalDurationMs = 2_200L,
                    modelLoadDurationNs = 400_000_000L,
                    generationDurationNs = 1_700_000_000L,
                    localSourceSummary = "source=held-official-blocking",
                ),
            ),
        )

        assertEquals(listOf("ロード", "未計上"), breakdown.segments.map { it.label })
        assertEquals(listOf(18, 82), breakdown.segments.map { it.percent })
        assertEquals(0.4 / 2.2, breakdown.segments[0].ratio, 0.000_001)
        assertEquals(1.8 / 2.2, breakdown.segments[1].ratio, 0.000_001)
    }

    @Test
    fun `blocking timing bars use displayed total denominator when eval duration is unavailable`() {
        val breakdown = requireNotNull(
            buildInferenceTimeBreakdown(
                InferenceStats(
                    evalDurationNs = null,
                    totalDurationMs = 2_200L,
                    modelLoadDurationNs = 400_000_000L,
                    generationDurationNs = 1_700_000_000L,
                    localSourceSummary = "source=held-official-blocking",
                ),
            ),
        )

        assertEquals(listOf("ロード", "未計上"), breakdown.segments.map { it.label })
        assertEquals(listOf(18, 82), breakdown.segments.map { it.percent })
        assertEquals(0.4 / 2.2, breakdown.segments[0].ratio, 0.000_001)
        assertEquals(1.8 / 2.2, breakdown.segments[1].ratio, 0.000_001)
    }

    @Test
    fun `automatic GPU failure invokes CPU exactly once`() = runBlocking {
        val calls = mutableListOf<String>()

        val result = runInferenceBackendWithFallback(
            primaryBackend = {
                calls += "GPU"
                throw IllegalStateException("gpu failed")
            },
            shouldFallback = { false },
            fallbackBackend = {
                calls += "CPU"
                "cpu response"
            },
        )

        assertEquals("cpu response", result)
        assertEquals(listOf("GPU", "CPU"), calls)
    }

    @Test
    fun `NPU thrown failure enters explicit GPU then CPU fallback without catching cancellation`() {
        val source = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val cancellationCatchBlock = source
            .substringBefore("Log.e(\"ChatScreen\", \"NPU standard route execution failed\", exception)")
            .substringAfterLast("} catch (exception: CancellationException) {")
        val npuExceptionBlock = source
            .substringAfter("Log.e(\"ChatScreen\", \"NPU standard route execution failed\", exception)")
            .substringBefore("} finally {")

        assertTrue(
            "A thrown NPU execution failure must enter the shared cancellation-safe fallback helper.",
            npuExceptionBlock.contains("runInferenceBackendChain("),
        )
        assertTrue(
            "NPU exception fallback must start the official generic route on explicit GPU.",
            npuExceptionBlock.contains("PreferredBackendDryRunSetting.GPU"),
        )
        assertTrue(
            "NPU exception fallback must use explicit CPU only after GPU failure.",
            npuExceptionBlock.contains("PreferredBackendDryRunSetting.CPU"),
        )
        assertTrue(
            "NPU exception fallback must preserve GPU then CPU attempt order.",
            npuExceptionBlock.indexOf("PreferredBackendDryRunSetting.GPU") <
                npuExceptionBlock.indexOf("PreferredBackendDryRunSetting.CPU"),
        )
        assertTrue(
            "NPU exception fallback stats must identify the backend that actually succeeded.",
            npuExceptionBlock.contains("exceptionFallbackChain.successfulBackend"),
        )
        assertTrue(
            "NPU exception fallback evidence must persist the actual fallback backend.",
            npuExceptionBlock.contains("npu_standard_route_fallback_backend="),
        )
        assertTrue(
            "Cancellation must be rethrown by the preceding catch before ordinary failure fallback.",
            cancellationCatchBlock.contains("throw exception"),
        )
    }

    @Test
    fun `cancellation is rethrown before held and official outer failure handling`() {
        val source = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt",
        ).readText()

        assertCancellationRethrowPrecedesFailureHandling(
            source = source,
            searchAfter = "internal suspend fun runWithHeldEngine(",
            failureAnchor = "failureDiagnosticsText = mediaPipeProbeContext?.let",
        )
        assertCancellationRethrowPrecedesFailureHandling(
            source = source,
            searchAfter = "private suspend fun runOfficialLiteRtLmDirect(",
            failureAnchor = "val npuPreferredBackendApplyResult = preferredBackendApplyResult",
        )
        assertCancellationRethrowPrecedesFailureHandling(
            source = source,
            searchAfter = "private fun runOfficialLiteRtLmBlocking(",
            failureAnchor = "val npuPreferredBackendApplyResult = preferredBackendApplyResult",
        )
        assertCancellationRethrowPrecedesFailureHandling(
            source = source,
            searchAfter = "internal suspend fun tryRunOfficialLiteRtFlowStreaming(",
            outerCatchAnchor = "}.onFailure { throwable ->",
            failureAnchor = "val reasonCode = (throwable as? OfficialFlowFallbackException)",
        )
        assertCancellationRethrowPrecedesFailureHandling(
            source = source,
            searchAfter = "internal fun tryRunOfficialLiteRtBlockingConversation(",
            outerCatchAnchor = "}.onFailure { throwable ->",
            failureAnchor = "val reasonCode = (throwable as? OfficialFlowFallbackException)",
        )
    }

    @Test
    fun `normal chat repetitive placeholder rejection reaches GPU and only final success is delivered`() = runBlocking {
        val prompt = "今日やることを3つ、短い箇条書きで教えて"
        val brokenNpuOutput = """
            - 〇〇の資料をレビューする
            - 〇〇のタスクを完了させる
            - 〇〇について学ぶ
        """.trimIndent()
        val npuResult = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = brokenNpuOutput,
                sanitizedOutput = brokenNpuOutput,
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                inputPrompt = prompt,
            ),
        )
        assertFalse(npuResult.successCriteriaMet)
        assertTrue(npuResult.outputQualityCandidateReason.contains("repetitive_placeholder_output"))

        val backendCalls = mutableListOf("NPU")
        var userRows = 1
        var uiDeliveries = 0
        var dbDeliveries = 0
        var ttsDeliveries = 0
        var technicalErrorDeliveries = 0

        if (shouldFallbackNpuStandardRouteFailureToLocal(npuResult)) {
            val fallback = runInferenceBackendChain(
                attempts = listOf(
                    InferenceBackendChainAttempt("GPU") {
                        backendCalls += "GPU"
                        "GPUで生成した具体的な回答"
                    },
                    InferenceBackendChainAttempt("CPU") {
                        backendCalls += "CPU"
                        "CPUで生成した回答"
                    },
                ),
                shouldFallback = { result -> result.isBlank() },
            )
            val finalResponse = fallback.result.orEmpty()
            if (fallback.successfulBackend != null && finalResponse.isNotBlank()) {
                uiDeliveries += 1
                dbDeliveries += 1
                ttsDeliveries += 1
            }
        } else {
            technicalErrorDeliveries += 1
        }

        assertEquals(listOf("NPU", "GPU"), backendCalls)
        assertEquals(1, userRows)
        assertEquals(1, uiDeliveries)
        assertEquals(1, dbDeliveries)
        assertEquals(1, ttsDeliveries)
        assertEquals(0, technicalErrorDeliveries)
    }

    @Test
    fun `valid unspecified three item bullet list remains a quality candidate`() {
        val output = "- 猫\n- 犬\n- 鳥"
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = output,
                sanitizedOutput = output,
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                inputPrompt = "動物を箇条書きで教えて",
            ),
        )

        assertTrue(result.outputQualityCandidateReason, result.successCriteriaMet)
    }

    @Test
    fun `valid explicit three item single character bullet list remains a quality candidate`() {
        val output = "1. 猫\n2. 犬\n3. 鳥"
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = output,
                sanitizedOutput = output,
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                inputPrompt = "動物を3つ箇条書きで教えて",
            ),
        )

        assertTrue(result.outputQualityCandidateReason, result.successCriteriaMet)
    }

    @Test
    fun `explicit one item bullet requirement is matched exactly`() {
        val output = "- 猫"
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = output,
                sanitizedOutput = output,
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                inputPrompt = "動物を1つ箇条書きで教えて",
            ),
        )

        assertTrue(result.outputQualityCandidateReason, result.successCriteriaMet)
    }

    @Test
    fun `explicit twenty one item bullet requirement rejects shortage and accepts exact count`() {
        fun evaluate(itemCount: Int): NpuStandardRouteS1Result {
            val output = (1..itemCount).joinToString("\n") { index -> "$index. 項目$index" }
            return NpuStandardRouteS1Mapper.map(
                NpuStandardRouteS1RawResult(
                    status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                    reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                    rawOutput = output,
                    sanitizedOutput = output,
                    qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                    runDecodeReached = true,
                    npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                    inputPrompt = "確認項目を21項目の箇条書きで教えて",
                ),
            )
        }

        assertFalse(evaluate(itemCount = 20).successCriteriaMet)
        assertTrue(evaluate(itemCount = 21).successCriteriaMet)
    }

    @Test
    fun `separator only output is rejected as a template leak`() {
        val output = "---"
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = output,
                sanitizedOutput = output,
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                inputPrompt = "短く答えて",
            ),
        )

        assertFalse(result.successCriteriaMet)
        assertTrue(result.outputQualityCandidateReason.contains("self_intro_template_leak"))
    }

    @Test
    fun `single unresolved circle placeholder bullet is rejected`() {
        val output = "- 〇〇の資料を確認する"
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = output,
                sanitizedOutput = output,
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                inputPrompt = "やることを1つ箇条書きで教えて",
            ),
        )

        assertFalse(result.successCriteriaMet)
        assertTrue(result.outputQualityCandidateReason.contains("repetitive_placeholder_output"))
    }

    @Test
    fun `meaningful circle company name in a bullet remains valid`() {
        val output = "- 〇〇株式会社へ連絡する"
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = output,
                sanitizedOutput = output,
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                inputPrompt = "やることを1つ箇条書きで教えて",
            ),
        )

        assertTrue(result.outputQualityCandidateReason, result.successCriteriaMet)
    }

    @Test
    fun `NPU fallback validates final responses before display persistence and TTS`() {
        val source = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val qualityFailureBlock = source
            .substringAfter("if (shouldFallbackNpuFailure)")
            .substringBefore("var npuStandardRouteUiAppendExecuted")
        val exceptionFailureBlock = source
            .substringAfter("Log.e(\"ChatScreen\", \"NPU standard route execution failed\", exception)")
            .substringBefore("} finally {")

        listOf(qualityFailureBlock, exceptionFailureBlock).forEach { fallbackBlock ->
            assertTrue(
                "Each fallback chain must route candidate validation through the unified output policy.",
                fallbackBlock.contains("LocalInferenceOutputPolicy.evaluateLocalCandidate("),
            )
            assertTrue(
                "Each fallback chain must use the policy decision to continue to the next backend.",
                fallbackBlock.contains(".shouldFallbackToNextBackend"),
            )
            assertFalse(
                "ChatScreen must not bypass the unified output policy with a direct quality call.",
                fallbackBlock.contains("localInferenceResponseRejectionReason(requestPrompt, result.response)"),
            )
            assertTrue(
                "A nonblank rejected result must not be accepted without a successful backend.",
                fallbackBlock.contains("acceptedLocalInferenceResponse("),
            )
            assertFalse(
                "Unvalidated fallback partials must never reach the normal UI.",
                fallbackBlock.contains("localStreamingResponseText = partial"),
            )
            assertFalse(
                "Unvalidated fallback partials must never reach the render buffer.",
                fallbackBlock.contains("streamingResponseTextForRender = partial"),
            )
            assertTrue(
                "Product fallback must stay on Conversation API paths instead of backend-unknown legacy one-shot.",
                fallbackBlock.contains("allowLegacyReflectionFallback = false"),
            )
            assertTrue(
                "Streaming fallback partials must pass the unified output policy before UI display.",
                fallbackBlock.contains("provisionalDecision = LocalInferenceOutputPolicy.evaluateLocalCandidate("),
            )
            assertTrue(
                "Only the policy-approved partial may reach the UI.",
                fallbackBlock.contains("localStreamingResponseText = safePartial"),
            )
            assertTrue(
                "Streaming fallback partials must update the owned assistant placeholder so visible text matches TTS.",
                fallbackBlock.contains("upsertStreamingAssistantPlaceholderSerialized("),
            )
        }
    }

    @Test
    fun `product local inference stops before blocking or legacy fallback after flow failure`() {
        val source = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val strictBlock = source
            .substringAfter("if (!allowLegacyReflectionFallback)")
            .substringBefore("UPSTREAM official-blocking attempt")

        assertTrue(strictBlock.contains("strict-conversation-path stop-after-flow-failure"))
        assertTrue(strictBlock.contains("state = LocalInferenceEngineState.ERROR"))
        assertFalse(strictBlock.contains("generateLiteRtResponseViaReflection("))
    }

    @Test
    fun `quality rejected NPU fallback persists final local stats on the same assistant row`() {
        val source = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val fallbackSuccessBlock = source
            .substringAfter("if (finalFallbackResponse.isNotBlank())")
            .substringBefore("val fallbackFailureMessage")

        assertTrue(
            "The successful fallback result must be mapped through the shared local stats builder.",
            fallbackSuccessBlock.contains("buildSuccessfulNpuFallbackInferencePersistence("),
        )
        assertTrue(
            "The final GPU or CPU InferenceStats must be persisted with the answer row.",
            fallbackSuccessBlock.contains("latestInferenceStats = fallbackPersistence.inferenceStats"),
        )
        assertTrue(
            "The answer row must carry compact final-backend provenance rather than the NPU DEV dump.",
            fallbackSuccessBlock.contains("localSourceSummary = fallbackPersistence.localSourceSummary"),
        )
        assertFalse(fallbackSuccessBlock.contains("localSourceSummary = fallbackDiagnostics"))
    }

    @Test
    fun `GPU fallback persistence uses the final GPU trace stats and compact provenance`() {
        val answer = "GPUで生成した実回答"
        val trace = successfulFallbackTrace(backend = "GPU", model = "gpu-model.litertlm")
        val persistence = requireNotNull(
            buildSuccessfulNpuFallbackInferencePersistence(
                successfulBackend = "GPU",
                response = answer,
                trace = trace,
            ),
        )

        assertEquals("gpu-model.litertlm", persistence.inferenceStats.modelName)
        assertEquals(11, persistence.inferenceStats.inputTokens)
        assertEquals(20, persistence.inferenceStats.outputTokens)
        assertEquals(31, persistence.inferenceStats.totalTokens)
        assertEquals(2_000L, persistence.inferenceStats.generationTimeMs)
        assertEquals(answer.length, persistence.inferenceStats.responseCharCount)
        assertTrue(requireNotNull(persistence.inferenceStats.tokensPerSecond) > 0.0)
        assertEquals("session-token-count", persistence.inferenceStats.tokenCountMode)
        assertTrue(persistence.localSourceSummary.contains("effective_backend=GPU"))
        assertTrue(persistence.localSourceSummary.contains("fallback_path=NPU,GPU"))
        assertTrue(persistence.localSourceSummary.contains("backend_evidence=GPU"))
        assertTrue(persistence.localSourceSummary.contains("model=gpu-model.litertlm"))
        assertFalse(persistence.localSourceSummary.contains(answer))
        assertFalse(persistence.localSourceSummary.contains("DEV"))
        assertTrue(persistence.localSourceSummary.length < 500)

        val summaryItems = buildInferenceSummarySections(
            stats = persistence.inferenceStats,
            displayMode = InferenceStatsDisplayMode.SIMPLE,
        ).single { it.title == "概要" }.items
        assertEquals("GPU", summaryItems.single { it.label == "実行バックエンド" }.value)
        assertEquals("NPU → GPU", summaryItems.single { it.label == "フォールバック" }.value)
        assertFalse(summaryItems.any { it.value.contains("NPU（GPU）") })
    }

    @Test
    fun `GPU rejection then CPU success persists only the final CPU trace stats`() = runBlocking {
        val calls = mutableListOf<String>()
        val cpuTrace = successfulFallbackTrace(backend = "CPU", model = "cpu-model.litertlm")
        val chain = runInferenceBackendChain(
            attempts = listOf(
                InferenceBackendChainAttempt("GPU") {
                    calls += "GPU"
                    ""
                },
                InferenceBackendChainAttempt("CPU") {
                    calls += "CPU"
                    "CPUで生成した実回答"
                },
            ),
            shouldFallback = String::isBlank,
        )
        val persistence = requireNotNull(
            buildSuccessfulNpuFallbackInferencePersistence(
                successfulBackend = chain.successfulBackend,
                response = chain.result.orEmpty(),
                trace = cpuTrace,
            ),
        )

        assertEquals(listOf("GPU", "CPU"), calls)
        assertTrue(persistence.localSourceSummary.contains("effective_backend=CPU"))
        assertTrue(persistence.localSourceSummary.contains("fallback_path=NPU,GPU,CPU"))
        assertTrue(persistence.localSourceSummary.contains("backend_evidence=CPU"))
        assertEquals("cpu-model.litertlm", persistence.inferenceStats.modelName)
        assertEquals(11, persistence.inferenceStats.inputTokens)
        assertEquals(20, persistence.inferenceStats.outputTokens)
        assertEquals(31, persistence.inferenceStats.totalTokens)
        assertTrue(requireNotNull(persistence.inferenceStats.tokensPerSecond) > 0.0)
        assertEquals("session-token-count", persistence.inferenceStats.tokenCountMode)
        assertFalse(persistence.localSourceSummary.contains("effective_backend=GPU"))

        val summaryItems = buildInferenceSummarySections(
            stats = persistence.inferenceStats,
            displayMode = InferenceStatsDisplayMode.SIMPLE,
        ).single { it.title == "概要" }.items
        assertEquals("CPU", summaryItems.single { it.label == "実行バックエンド" }.value)
        assertEquals("NPU → GPU → CPU", summaryItems.single { it.label == "フォールバック" }.value)
    }

    @Test
    fun `fallback persistence retains measured token provenance and timing from final backend`() {
        val trace = successfulFallbackTrace(backend = "GPU", model = "gpu-model.litertlm").copy(
            sessionPromptTokens = 11,
            sessionResponseTokens = 20,
            sessionTotalTokens = 31,
            firstTokenProbe = LocalStatsCandidateProbe(
                availability = LocalStatsAvailability.AVAILABLE_NOW,
                valueSummary = "400",
            ),
        )
        val stats = requireNotNull(
            buildSuccessfulNpuFallbackInferencePersistence(
                successfulBackend = "GPU",
                response = "GPUで生成した実回答",
                trace = trace,
            ),
        ).inferenceStats

        assertEquals(11, stats.inputTokens)
        assertEquals(20, stats.outputTokens)
        assertEquals(31, stats.totalTokens)
        assertEquals(400L, stats.timeToFirstTokenMs)
        assertEquals(2_000L, stats.totalDurationMs)
        assertEquals(2_000L, stats.generationTimeMs)
        assertTrue(requireNotNull(stats.tokensPerSecond) > 0.0)
        assertFalse(stats.tokenCountMode.isNullOrBlank())
    }

    @Test
    fun `fallback persistence explains unavailable token measurements without fabricating counts`() {
        val stats = requireNotNull(
            buildSuccessfulNpuFallbackInferencePersistence(
                successfulBackend = "GPU",
                response = "GPUで生成した実回答",
                trace = LocalInferenceTrace(
                    localModelDisplayName = "gpu-model.litertlm",
                    localTraceStartElapsedRealtimeMs = 10_000L,
                    localTraceCompletedElapsedRealtimeMs = 12_000L,
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                ),
            ),
        ).inferenceStats

        assertNull(stats.inputTokens)
        assertNull(stats.outputTokens)
        assertNull(stats.totalTokens)
        assertNull(stats.tokensPerSecond)
        assertEquals("unavailable", stats.tokenCountMode)
        assertTrue(requireNotNull(stats.notes).contains("tokenizer", ignoreCase = true))
        assertTrue(requireNotNull(stats.notes).contains("取得できません", ignoreCase = true))
    }

    @Test
    fun `all failure and stopped delivery never fabricate successful fallback stats`() {
        val trace = successfulFallbackTrace(backend = "GPU", model = "gpu-model.litertlm")

        assertNull(
            buildSuccessfulNpuFallbackInferencePersistence(
                successfulBackend = null,
                response = "",
                trace = trace,
            ),
        )
        assertNull(
            buildSuccessfulNpuFallbackInferencePersistence(
                successfulBackend = "GPU",
                response = "",
                trace = trace,
            ),
        )
        assertNull(
            buildSuccessfulNpuFallbackInferencePersistence(
                successfulBackend = "NPU",
                response = "壊れたNPU候補",
                trace = trace,
            ),
        )
    }

    @Test
    fun `successful NPU fallback schedules final backend tokenizer recount on its assistant row`() {
        val source = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val fallbackSuccessBlock = source
            .substringAfter("if (finalFallbackResponse.isNotBlank())")
            .substringBefore("val fallbackFailureMessage")

        assertTrue(fallbackSuccessBlock.contains("val fallbackAssistantId ="))
        assertTrue(fallbackSuccessBlock.contains("scheduleNpuFallbackTokenizerStatsUpdate("))
        assertTrue(fallbackSuccessBlock.contains("assistantId = fallbackAssistantId"))
        assertTrue(fallbackSuccessBlock.contains("prompt = requestPrompt"))
        assertTrue(fallbackSuccessBlock.contains("response = finalFallbackResponse"))
        assertTrue(fallbackSuccessBlock.contains("successfulBackend = fallbackChain.successfulBackend"))
    }

    @Test
    fun `NPU exception fallback schedules the same final backend tokenizer recount`() {
        val source = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val exceptionBlock = source
            .substringAfter("Log.e(\"ChatScreen\", \"NPU standard route execution failed\", exception)")
            .substringBefore("} finally {")

        assertTrue(exceptionBlock.contains("val exceptionFallbackAssistantId ="))
        assertTrue(exceptionBlock.contains("scheduleNpuFallbackTokenizerStatsUpdate("))
        assertTrue(exceptionBlock.contains("assistantId = exceptionFallbackAssistantId"))
        assertTrue(exceptionBlock.contains("prompt = requestPrompt"))
        assertTrue(exceptionBlock.contains("response = exceptionFallbackResponse"))
        assertTrue(exceptionBlock.contains("successfulBackend = exceptionFallbackChain.successfulBackend"))
    }

    private fun successfulFallbackTrace(backend: String, model: String): LocalInferenceTrace =
        LocalInferenceTrace(
            localModelDisplayName = model,
            sessionPromptTokens = 11,
            sessionResponseTokens = 20,
            sessionTotalTokens = 31,
            outputTokenProbe = LocalStatsCandidateProbe(
                availability = LocalStatsAvailability.AVAILABLE_NOW,
                valueSummary = "20",
            ),
            wallClockTotalInferenceDurationNs = 2_000_000_000L,
            localTraceStartElapsedRealtimeMs = 10_000L,
            localTraceFirstResponseElapsedRealtimeMs = 10_400L,
            localTraceCompletedElapsedRealtimeMs = 12_000L,
            requestedPreferredBackend = backend,
            appliedPreferredBackend = backend,
            preferredBackendApplyResult = "applied",
            selectedAssistantResponseSource = "held-official-blocking",
        )

    private fun assertCancellationRethrowPrecedesFailureHandling(
        source: String,
        searchAfter: String,
        failureAnchor: String,
        outerCatchAnchor: String = "}.getOrElse { throwable ->",
    ) {
        val functionStart = source.indexOf(searchAfter).also { assertTrue(it >= 0) }
        val catchStart = source.indexOf(outerCatchAnchor, functionStart).also { assertTrue(it >= 0) }
        val failureStart = source.indexOf(failureAnchor, catchStart).also { assertTrue(it >= 0) }
        val catchPrefix = source.substring(catchStart, failureStart)
        assertTrue(catchPrefix.contains("if (throwable is CancellationException) throw throwable"))
    }

    @Test
    fun `tokenizer traversal invokes only allowlisted pure getters`() {
        val fake = SideEffectTraversalFake()
        val traversal = Class.forName(
            "io.github.ninbyo02.lami.ui.screens.home.LocalStreamingRunnerKt",
        ).declaredMethods.single { method ->
            method.name == "tryResolveTokenizerFromConversation"
        }.apply {
            isAccessible = true
        }

        val result = traversal.invoke(null, fake, { _: String -> Unit })

        assertNull(result)
        assertEquals(1, fake.tokenizerInvocationCount)
        assertEquals(1, fake.getTokenizerInvocationCount)
        assertEquals(1, fake.getInputTokenizerInvocationCount)
        assertEquals(1, fake.getOutputTokenizerInvocationCount)
        assertEquals(0, fake.getSessionInvocationCount)
        assertEquals(0, fake.sessionInvocationCount)
        assertEquals(0, fake.currentSessionInvocationCount)
        assertEquals(0, fake.activeSessionInvocationCount)
        assertEquals(0, fake.llmSessionInvocationCount)
        assertEquals(0, fake.closeInvocationCount)
        assertEquals(0, fake.releaseInvocationCount)
        assertEquals(0, fake.shutdownInvocationCount)
        assertEquals(0, fake.destroyInvocationCount)
        assertEquals(0, fake.resetInvocationCount)
        assertEquals(0, fake.clearInvocationCount)
        assertEquals(0, fake.cancelInvocationCount)

        val allowed = listOf("tokenizer", "getTokenizer", "getInputTokenizer", "getOutputTokenizer")
        val forbidden = listOf(
            "getSession", "session", "currentSession", "activeSession", "llmSession",
            "getClass", "toString", "close", "release", "shutdown", "destroy", "reset", "clear", "cancel",
        )
        allowed.forEach { assertTrue(it, isSafeTokenizerTraversalMethod(it)) }
        forbidden.forEach { assertFalse(it, isSafeTokenizerTraversalMethod(it)) }
        val runnerClass = Class.forName(
            "io.github.ninbyo02.lami.ui.screens.home.LocalStreamingRunnerKt",
        )
        val actualAllowlist = runnerClass.declaredFields.single { field ->
            field.name == "SAFE_TOKENIZER_TRAVERSAL_METHODS"
        }.apply {
            isAccessible = true
        }.get(null) as Set<*>
        assertEquals(allowed.toSet(), actualAllowlist)
    }

    private class TraversalLeaf

    private class FakeSession {
        @Suppress("UNUSED_PARAMETER")
        fun sizeInTokens(text: String): Int = 1
    }

    private class SideEffectTraversalFake {
        var tokenizerInvocationCount = 0
        var getTokenizerInvocationCount = 0
        var getInputTokenizerInvocationCount = 0
        var getOutputTokenizerInvocationCount = 0
        var getSessionInvocationCount = 0
        var sessionInvocationCount = 0
        var currentSessionInvocationCount = 0
        var activeSessionInvocationCount = 0
        var llmSessionInvocationCount = 0
        var closeInvocationCount = 0
        var releaseInvocationCount = 0
        var shutdownInvocationCount = 0
        var destroyInvocationCount = 0
        var resetInvocationCount = 0
        var clearInvocationCount = 0
        var cancelInvocationCount = 0
        @Suppress("unused")
        private val hiddenSession = FakeSession()

        fun tokenizer(): TraversalLeaf = TraversalLeaf().also { tokenizerInvocationCount += 1 }
        fun getTokenizer(): TraversalLeaf = TraversalLeaf().also { getTokenizerInvocationCount += 1 }
        fun getInputTokenizer(): TraversalLeaf = TraversalLeaf().also { getInputTokenizerInvocationCount += 1 }
        fun getOutputTokenizer(): TraversalLeaf = TraversalLeaf().also { getOutputTokenizerInvocationCount += 1 }
        fun getSession(): FakeSession = FakeSession().also { getSessionInvocationCount += 1 }
        fun session(): FakeSession = FakeSession().also { sessionInvocationCount += 1 }
        fun currentSession(): FakeSession = FakeSession().also { currentSessionInvocationCount += 1 }
        fun activeSession(): FakeSession = FakeSession().also { activeSessionInvocationCount += 1 }
        fun llmSession(): FakeSession = FakeSession().also { llmSessionInvocationCount += 1 }
        fun close(): SideEffectTraversalFake = apply { closeInvocationCount += 1 }
        fun release(): SideEffectTraversalFake = apply { releaseInvocationCount += 1 }
        fun shutdown(): SideEffectTraversalFake = apply { shutdownInvocationCount += 1 }
        fun destroy(): SideEffectTraversalFake = apply { destroyInvocationCount += 1 }
        fun reset(): SideEffectTraversalFake = apply { resetInvocationCount += 1 }
        fun clear(): SideEffectTraversalFake = apply { clearInvocationCount += 1 }
        fun cancel(): SideEffectTraversalFake = apply { cancelInvocationCount += 1 }
    }

    @Test
    fun `held official blocking does not present inseparable engine time as input or zero generation`() {
        val details = blockingDetails()

        val input = details.single { it.label == "入力評価時間" }
        val generation = details.single { it.label == "生成時間" }
        val inference = details.single { it.label == "推論時間" }

        assertTrue(input.value.contains("未取得"))
        assertFalse(input.value.contains("27.3"))
        assertTrue(generation.value.contains("未取得"))
        assertFalse(generation.value.contains("0.0"))
        assertTrue(inference.value.contains("27.3"))
    }

    @Test
    fun `held official blocking without first token evidence does not present zero TTFT`() {
        val ttftItems = blockingDetails().filter { it.label.contains("TTFT") }

        assertTrue(ttftItems.isNotEmpty())
        assertTrue(ttftItems.all { it.value == "—" })
        assertTrue(ttftItems.none { it.value.contains("0 ms") || it.value.contains("0.0") })
    }

    private fun blockingDetails(): List<InferenceStatItemUi> =
        buildInferenceDetailSections(
            stats = InferenceStats(
                modelName = "gemma-4-E2B-it.litertlm",
                outputTokens = 523,
                tokensPerSecond = 22.1,
                modelLoadDurationNs = 3_600_000_000L,
                promptEvalDurationNs = 27_300_000_000L,
                generationDurationNs = 0L,
                evalDurationNs = 27_300_000_000L,
                totalDurationMs = 30_900L,
                timeToFirstTokenMs = 0L,
                localSourceSummary =
                    "route_family=local_gpu; effective_backend=GPU; source_summary=held-official-blocking",
            ),
            displayMode = InferenceStatsDisplayMode.DETAILED,
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
        ).single { it.title == "詳細" }.items
}
