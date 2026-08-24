package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceStatsFactoryTest {
    @Test
    fun `local trace produces one canonical stats object`() {
        val trace = LocalInferenceTrace(
            localModelDisplayName = "local-model.litertlm",
            sessionPromptTokens = 7,
            sessionResponseTokens = 9,
            sessionTotalTokens = 16,
            measuredTokenSnapshot = LocalInferenceMeasuredTokenSnapshot(
                inputTokens = 7,
                outputTokens = 9,
                totalTokens = 16,
                tokenCountMode = "tokenizer_recount",
                decodeDurationMs = 450L,
                totalDurationMs = 700L,
                ttftMs = 120L,
            ),
        )

        val stats = InferenceStatsFactory.fromLocalTrace(
            trace = trace,
            generationTimeMs = 450L,
            responseCharCount = 18,
            responseText = "local response",
        )

        assertNotNull(stats)
        assertEquals("local-model.litertlm", stats?.modelName)
        assertEquals(7, stats?.inputTokens)
        assertEquals(9, stats?.outputTokens)
        assertEquals(16, stats?.totalTokens)
        assertEquals(20.0, stats?.tokensPerSecond ?: 0.0, 0.001)
        assertEquals("tokenizer_recount", stats?.tokenCountMode)
        assertEquals("stop", stats?.finishReason)
        assertEquals(120L, stats?.timeToFirstTokenMs)
        assertEquals(18, stats?.responseCharCount)
    }

    @Test
    fun `empty local trace does not fabricate inference stats`() {
        val stats = InferenceStatsFactory.fromLocalTrace(
            trace = LocalInferenceTrace(),
            generationTimeMs = 500L,
            responseCharCount = 0,
            responseText = null,
        )

        assertNull(stats)
    }

    @Test
    fun `NPU route uses native timing and canonical source summary`() {
        val result = successfulNpuResult(
            timing = NpuStandardRouteS1Timing(
                totalMs = 900L,
                decodeMs = 400L,
                ttftMs = 200L,
                outputTokens = 8,
                tokenCountMode = "native",
                tokensPerSecond = 20.0,
            ),
        )

        val stats = InferenceStatsFactory.fromNpuStandardRoute(
            result = result,
            localSourceSummary = "route_family=npu_standard",
            assistantText = "こんにちは。",
        )

        assertEquals("npu-model", stats.modelName)
        assertEquals(8, stats.outputTokens)
        assertEquals(20.0, stats.tokensPerSecond ?: 0.0, 0.0)
        assertEquals(400L, stats.generationTimeMs)
        assertEquals(400L, stats.decodeDurationMs)
        assertEquals(900L, stats.totalDurationMs)
        assertEquals("native", stats.tokenCountMode)
        assertEquals("route_family=npu_standard", stats.localSourceSummary)
    }

    @Test
    fun `safe greeting factory preserves rejected attempt provenance`() {
        val result = successfulNpuResult(
            status = FailureNpuStandardRouteS1Provider.STATUS_FAILURE,
            reason = NpuStandardRouteS1Contract.REASON_MIXED_LANGUAGE,
            rawOutput = "안녕하세요.",
            sanitizedOutput = "",
            timing = NpuStandardRouteS1Timing(totalMs = 1_200L, decodeMs = 100L),
        )
        val summary = InferenceStatsFactory.safeGreetingSourceSummary(
            result = result,
            existingSummary = "route_family=npu_standard",
        )
        val stats = InferenceStatsFactory.safeGreetingFallback(
            result = result,
            localSourceSummary = summary,
            assistantText = "こんにちは。",
        )

        assertTrue(summary.contains("fallback=${NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING}"))
        assertTrue(summary.contains("inference_metrics_source=rejected_npu_attempt"))
        assertEquals(NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING, stats.finishReason)
        assertTrue(stats.notes.orEmpty().contains("display_source=deterministic_safe_greeting"))
        assertEquals("こんにちは。".length, stats.responseCharCount)
    }

    private fun successfulNpuResult(
        status: String = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason: String = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput: String = "こんにちは。",
        sanitizedOutput: String = rawOutput,
        timing: NpuStandardRouteS1Timing = NpuStandardRouteS1Timing(),
    ): NpuStandardRouteS1Result = NpuStandardRouteS1Result(
        status = status,
        reason = reason,
        rawOutput = rawOutput,
        sanitizedOutput = sanitizedOutput,
        qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        runDecodeReached = true,
        npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed = false,
        timeout = false,
        freshCrash = false,
        selectedModelName = "npu-model",
        timing = timing,
        inputPrompt = "こんにちは",
    )
}
