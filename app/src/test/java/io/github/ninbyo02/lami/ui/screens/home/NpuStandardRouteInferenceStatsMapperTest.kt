package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NpuStandardRouteInferenceStatsMapperTest {
    @Test
    fun `maps standard NPU timing to shared inference stats`() {
        val result = successfulResult(
            timing = NpuStandardRouteS1Timing(
                totalMs = 2_680,
                decodeMs = 2_627,
                ttftMs = 85,
                outputTokens = 24,
                tokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS,
                tokensPerSecond = 9.1,
            ),
        )

        val stats = result.toSharedInferenceStats("こんにちは！")

        assertEquals("gemma-4-E2B-it_qualcomm_sm8750.litertlm", stats.modelName)
        assertEquals(24, stats.outputTokens)
        assertEquals(24, stats.completionTokens)
        assertEquals(24, stats.totalTokens)
        assertEquals(9.1, stats.tokensPerSecond!!, 0.001)
        assertEquals("estimated_code_points", stats.tokenCountMode)
        assertEquals(2_627L, stats.decodeDurationMs)
        assertEquals(2_680L, stats.totalDurationMs)
        assertEquals(85L, stats.timeToFirstTokenMs)
        assertEquals(6, stats.responseCharCount)
        assertEquals("success", stats.finishReason)
        assertEquals(true, stats.notes?.contains("backend=NPU"))
        assertEquals(
            "route_family=npu_standard; backend=NPU; evidence=QNN_HTP_V79_FastRPC_native_diag",
            stats.localSourceSummary,
        )
        assertEquals(false, stats.localSourceSummary?.contains("こんにちは！"))
    }

    @Test
    fun `keeps unavailable timing nullable instead of inventing GPU comparable values`() {
        val stats = successfulResult(
            timing = NpuStandardRouteS1Timing(),
        ).toSharedInferenceStats("回答")

        assertNull(stats.outputTokens)
        assertNull(stats.tokensPerSecond)
        assertNull(stats.tokenCountMode)
        assertNull(stats.decodeDurationMs)
        assertNull(stats.totalDurationMs)
    }

    private fun successfulResult(
        timing: NpuStandardRouteS1Timing,
    ) = NpuStandardRouteS1Result(
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput = "こんにちは！",
        sanitizedOutput = "こんにちは！",
        qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        runDecodeReached = true,
        npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed = false,
        timeout = false,
        freshCrash = false,
        selectedModelName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
        timing = timing,
        inputPrompt = "こんにちは",
    )
}
