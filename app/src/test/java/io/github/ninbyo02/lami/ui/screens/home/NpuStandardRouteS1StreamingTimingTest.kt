package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class NpuStandardRouteS1StreamingTimingTest {
    @Test
    fun `mapper preserves first native streaming chunk as TTFT`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                success = true,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                npuS1DecodeMs = 420L,
                npuS1NativeTtftMs = 55L,
                npuS1TtftMs = 85L,
                npuS1OutputTokens = 6,
                npuS1TokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS,
                inputPrompt = "こんにちは",
            ),
        )

        assertEquals(55L, result.timing.nativeTtftMs)
        assertEquals(85L, result.timing.ttftMs)
        assertEquals(420L, result.timing.decodeMs)

        val uiTiming = buildNpuStandardRouteS1UiTiming(
            result = result,
            decodeStartedAtMs = 1_000L,
            uiDisplayedAtMs = 1_500L,
        )
        assertEquals(55L, uiTiming.nativeTtftMs)
        assertEquals(85L, uiTiming.ttftMs)
        assertEquals(500L, uiTiming.totalMs)
    }
}
