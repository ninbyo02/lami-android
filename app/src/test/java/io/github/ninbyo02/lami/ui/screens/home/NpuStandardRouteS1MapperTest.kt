package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1MapperTest {
    @Test
    fun `natural Japanese dev-only result maps to successful S1 result`() {
        val result = NpuStandardRouteS1Mapper.map(successRaw())

        assertTrue(result.successCriteriaMet)
        assertEquals("こんにちは。", result.displayText)
        assertEquals("こんにちは。", result.sanitizedOutput)
        assertEquals("natural_japanese", result.qualityClassification)
        assertEquals("QNN_HTP_V79_FastRPC_native_diag", result.npuBackendEvidence)
        assertEquals(32, result.selection.requestedMaxOutputTokens)
        assertEquals(32, result.selection.effectiveMaxOutputTokens)
        assertFalse(result.selection.sideEffects.db)
        assertFalse(result.selection.sideEffects.tts)
        assertFalse(result.selection.sideEffects.markdown)
        assertFalse(result.selection.sideEffects.streaming)
        assertFalse(result.selection.sideEffects.backendNpuPersisted)
        assertFalse(result.selection.sideEffects.conversationHistorySaved)
    }

    @Test
    fun `result success string is accepted as success equivalent`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(status = "", result = "success", success = null),
        )

        assertTrue(result.successCriteriaMet)
    }

    @Test
    fun `success boolean is accepted as success equivalent`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(status = "", result = "", success = true),
        )

        assertTrue(result.successCriteriaMet)
    }

    @Test
    fun `FastRPC evidence alone is normalized to S1 NPU evidence`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(npuBackendEvidence = "FastRPC native diag"),
        )

        assertTrue(result.successCriteriaMet)
        assertEquals("QNN_HTP_V79_FastRPC_native_diag", result.npuBackendEvidence)
    }

    @Test
    fun `failure conditions do not meet S1 success criteria`() {
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(fallbackUsed = true)).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(timeout = true)).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(freshCrash = true)).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(runDecodeReached = false)).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(npuBackendEvidence = "")).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(sanitizedOutput = "")).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(qualityClassification = "mixed_language")).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(status = "failure", result = "failure", success = false)).successCriteriaMet)
    }

    @Test
    fun `template artifact raw output can pass when sanitized output is a quality candidate`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">こんにちは！何かお手伝いできることはありますか？<end_of_turn>",
                sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
            ),
        )

        assertTrue(result.successCriteriaMet)
        assertEquals("こんにちは！何かお手伝いできることはありますか？", result.displayText)
        assertEquals("quality_candidate_pass", result.outputQualityCandidateStatus)
        assertTrue(result.outputQualityCandidateReason.contains("natural_japanese_after_safe_leading_gt"))
    }

    @Test
    fun `template artifact raw output still fails when sanitized output is empty`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">こんにちは！<end_of_turn>",
                sanitizedOutput = "",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
            ),
        )

        assertFalse(result.successCriteriaMet)
        assertEquals("quality_candidate_fail", result.outputQualityCandidateStatus)
    }

    @Test
    fun `arithmetic answer passes but prompt repetition fails quality candidate`() {
        val answered = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = "１＋１は２です",
                sanitizedOutput = "１＋１は２です",
                inputPrompt = "１＋１は？",
            ),
        )
        val echoed = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = "１＋１は？",
                sanitizedOutput = "１＋１は？",
                inputPrompt = "１＋１は？",
            ),
        )

        assertTrue(answered.successCriteriaMet)
        assertEquals("quality_candidate_pass", answered.outputQualityCandidateStatus)
        assertFalse(echoed.successCriteriaMet)
        assertEquals("quality_candidate_fail", echoed.outputQualityCandidateStatus)
        assertTrue(echoed.outputQualityCandidateReason.contains("prompt_repetition_only"))
        assertTrue(echoed.outputQualityCandidateReason.contains("arithmetic_answer_missing"))
    }

    @Test
    fun `raw role contamination is classified as failure even with natural sanitized output`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = "どうしましたか。\nユーザー: ああああ\nアシスタント: 何か困っていますか。",
                sanitizedOutput = "どうしましたか。",
                qualityClassification = "natural_japanese",
            ),
        )

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("raw_role_contamination", result.reason)
        assertEquals("role_contamination", result.qualityClassification)
        assertEquals("どうしましたか。", result.sanitizedOutput)
    }

    @Test
    fun `mapper keeps S1 side effects disconnected and max output fixed`() {
        val result = NpuStandardRouteS1Mapper.map(successRaw())
        val text = result.displayText

        assertEquals(32, result.selection.requestedMaxOutputTokens)
        assertEquals(32, result.selection.effectiveMaxOutputTokens)
        assertTrue(result.selection.sideEffects.allDisconnected)
        assertEquals("こんにちは。", text)
        assertFalse(result.selection.sideEffects.db)
        assertFalse(result.selection.sideEffects.tts)
        assertFalse(result.selection.sideEffects.markdown)
        assertFalse(result.selection.sideEffects.streaming)
    }

    private fun successRaw(
        status: String = "success",
        result: String = "",
        success: Boolean? = null,
        rawOutput: String = "こんにちは。",
        sanitizedOutput: String = "こんにちは。",
        qualityClassification: String = "natural_japanese",
        runDecodeReached: Boolean = true,
        npuBackendEvidence: String = "QNN_HTP_V79_FastRPC_native_diag",
        fallbackUsed: Boolean = false,
        timeout: Boolean = false,
        freshCrash: Boolean = false,
        inputPrompt: String = "",
    ): NpuStandardRouteS1RawResult = NpuStandardRouteS1RawResult(
        status = status,
        result = result,
        success = success,
        reason = "success",
        rawOutput = rawOutput,
        sanitizedOutput = sanitizedOutput,
        qualityClassification = qualityClassification,
        runDecodeReached = runDecodeReached,
        npuBackendEvidence = npuBackendEvidence,
        fallbackUsed = fallbackUsed,
        timeout = timeout,
        freshCrash = freshCrash,
        requestedMaxOutputTokens = 32,
        effectiveMaxOutputTokens = 32,
        inputPrompt = inputPrompt,
    )
}
