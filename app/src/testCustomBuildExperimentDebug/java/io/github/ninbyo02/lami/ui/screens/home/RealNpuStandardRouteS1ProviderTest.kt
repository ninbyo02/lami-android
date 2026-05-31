package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationDisplay
import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealNpuStandardRouteS1ProviderTest {
    private val userPrompt = "好きな色を一つだけ答えてください"

    @Test
    fun `real provider implements S1 provider contract`() {
        val provider: NpuStandardRouteS1Provider = RealNpuStandardRouteS1Provider(
            requestRunner = { successDisplay() },
        )

        assertEquals("success", provider.invoke(userPrompt, trace = {}).reason)
    }

    @Test
    fun `real provider maps dev only success display to S1 raw result`() {
        val raw = RealNpuStandardRouteS1Provider(
            requestRunner = { successDisplay() },
        ).invoke(userPrompt, trace = {})

        assertEquals("success", raw.status)
        assertEquals("success", raw.result)
        assertEquals(true, raw.success)
        assertEquals("success", raw.reason)
        assertEquals("こんにちは。", raw.rawOutput)
        assertEquals("こんにちは。", raw.sanitizedOutput)
        assertEquals("natural_japanese", raw.qualityClassification)
        assertTrue(raw.runDecodeReached)
        assertEquals("QNN_HTP_V79_FastRPC_native_diag", raw.npuBackendEvidence)
        assertFalse(raw.fallbackUsed)
        assertFalse(raw.timeout)
        assertFalse(raw.freshCrash)
        assertEquals(32, raw.requestedMaxOutputTokens)
        assertEquals(32, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `real provider maps dev only failure reason to S1 raw result`() {
        val raw = RealNpuStandardRouteS1Provider(
            requestRunner = { failureDisplay(reason = "npu_evidence_missing") },
        ).invoke(userPrompt, trace = {})

        assertEquals("failure", raw.status)
        assertEquals("failure", raw.result)
        assertEquals(false, raw.success)
        assertEquals("npu_evidence_missing", raw.reason)
        assertEquals("", raw.rawOutput)
        assertEquals("", raw.sanitizedOutput)
        assertEquals("unknown", raw.qualityClassification)
        assertFalse(raw.runDecodeReached)
        assertEquals("", raw.npuBackendEvidence)
        assertFalse(raw.fallbackUsed)
        assertFalse(raw.timeout)
        assertFalse(raw.freshCrash)
        assertEquals(32, raw.requestedMaxOutputTokens)
        assertEquals(32, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `real provider returns explicit failure when dev only entry is unavailable in unit test`() {
        val raw = RealNpuStandardRouteS1Provider().invoke(userPrompt, trace = {})

        assertEquals("failure", raw.status)
        assertEquals("failure", raw.result)
        assertEquals(false, raw.success)
        assertEquals("dev_only_entry_unavailable", raw.reason)
        assertEquals("", raw.rawOutput)
        assertEquals("", raw.sanitizedOutput)
        assertEquals("unknown", raw.qualityClassification)
        assertFalse(raw.runDecodeReached)
        assertEquals("", raw.npuBackendEvidence)
        assertFalse(raw.fallbackUsed)
        assertFalse(raw.timeout)
        assertFalse(raw.freshCrash)
        assertEquals(32, raw.requestedMaxOutputTokens)
        assertEquals(32, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `real provider failure maps to failed S1 result without side effects`() {
        val result = NpuStandardRouteS1Mapper.map(
            RealNpuStandardRouteS1Provider(
                requestRunner = { failureDisplay(reason = "timeout") },
            ).invoke(userPrompt, trace = {}),
        )

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("timeout", result.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
        assertFalse(result.selection.sideEffects.db)
        assertFalse(result.selection.sideEffects.tts)
        assertFalse(result.selection.sideEffects.markdown)
        assertFalse(result.selection.sideEffects.streaming)
        assertFalse(result.selection.sideEffects.backendNpuPersisted)
        assertFalse(result.selection.sideEffects.conversationHistorySaved)
    }

    @Test
    fun `real provider classifies sanitized prompt echo as failure`() {
        val traces = mutableListOf<String>()
        val raw = RealNpuStandardRouteS1Provider(
            requestRunner = { successDisplay(output = "明日の天気は") },
        ).invoke("明日の天気は", trace = traces::add)
        val result = NpuStandardRouteS1Mapper.map(raw)

        assertEquals("failure", raw.status)
        assertEquals("failure", raw.result)
        assertEquals(false, raw.success)
        assertEquals("question_echo", raw.reason)
        assertEquals("明日の天気は", raw.sanitizedOutput)
        assertEquals("question_echo", raw.qualityClassification)
        assertTrue(raw.runDecodeReached)
        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("question_echo", result.reason)
        assertEquals("question_echo", result.qualityClassification)
        assertTrue(result.selection.sideEffects.allDisconnected)
        assertTrue(traces.any { it.contains("reason=question_echo") })
        assertTrue(traces.any { it.contains("quality_classification=question_echo") })
    }

    @Test
    fun `custom build experiment default provider selects real provider and reports unavailable in unit test`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider().invoke(userPrompt, trace = {})
        val result = NpuStandardRouteS1Mapper.map(raw)

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", raw.status)
        assertEquals("dev_only_entry_unavailable", raw.reason)
        assertEquals("dev_only_entry_unavailable", result.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `custom build experiment keeps provider selector real compatible for Settings mode OFF`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProviderForMode(NpuStandardRouteMode.OFF)
            .invoke(userPrompt, trace = {})
        val result = NpuStandardRouteS1Mapper.map(raw)

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", raw.status)
        assertEquals("dev_only_entry_unavailable", raw.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `custom build experiment invoker default propagates real provider unavailable failure in unit test`() {
        val result = NpuStandardRouteS1Mapper.map(NpuStandardRouteS1Invoker().invoke(userPrompt))

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("dev_only_entry_unavailable", result.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `real provider passes user prompt into dev only request`() {
        var capturedRequest: DevOnlyNpuOneTurnConversationRequest? = null
        val traces = mutableListOf<String>()

        val raw = RealNpuStandardRouteS1Provider(
            requestRunner = { request ->
                capturedRequest = request
                successDisplay()
            },
        ).invoke(userPrompt, trace = traces::add)

        val request = requireNotNull(capturedRequest)
        assertEquals("success", raw.status)
        assertEquals(userPrompt, request.userPrompt)
        assertEquals("", request.contextText)
        assertTrue(request.unsafeDevBypassPromptLengthGate)
        assertEquals(32, request.maxOutputTokens)
        assertEquals("raw_dialog_tail_variant_b", request.promptTailVariant)
        assertEquals(60_000L, request.timeoutMs)
        assertTrue(traces.any { it.contains("NPU_REAL_PROMPT provider_prompt_hash=") })
        assertTrue(traces.any { it.contains("NPU_REAL_PROMPT request_prompt_hash=") })
        assertTrue(traces.any { it.contains("prompt_source=dev_only_conversation") })
        assertTrue(traces.any { it.contains("final_input_tokens=unavailable") })
        assertTrue(traces.any { it.contains("final_input_code_points=") })
        assertTrue(traces.any { it.contains("status=success") })
        assertTrue(traces.any { it.contains("reason=success") })
        assertTrue(traces.any { it.contains("raw_output_hash=") })
        assertTrue(traces.any { it.contains("sanitized_output_hash=") })
        assertTrue(traces.any { it.contains("quality_classification=natural_japanese") })
        assertTrue(traces.any { it.contains("run_decode_reached=true") })
        assertTrue(traces.any { it.contains("fallback_used=false") })
        assertTrue(traces.any { it.contains("timeout=false") })
        assertTrue(traces.any { it.contains("fresh_crash=false") })
        assertFalse(traces.joinToString("\n").contains(userPrompt))
    }

    private fun successDisplay(output: String = "こんにちは。"): DevOnlyNpuOneTurnConversationDisplay =
        DevOnlyNpuOneTurnConversationDisplay(
            text = "DEV ONLY NPU ONE TURN",
            output = output,
            status = "success",
            reason = "success",
            nativeReached = true,
            decodeReached = true,
            npuEvidence = "QNN_HTP_V79_FastRPC_native_diag",
            fallback = false,
            freshCrash = false,
            timeout = false,
            requestedMaxOutputTokens = 32,
            effectiveMaxOutputTokens = 32,
            nativeMaxOutputTokensLimit = "512",
            rawLen = output.length,
            sanitizedLen = output.length,
            quality = "natural_japanese",
            controlCharSummary = "none",
            rawOutputFirst200Chars = output,
            rawOutputLast200Chars = output,
            rawUnicodeSummary = "control_chars=none",
            sanitizerApplied = "true",
            removedTemplateTokenCount = "0",
            removedPromptEcho = "false",
            replacementCharCount = "0",
            outputContainsControlChars = "false",
        )

    private fun failureDisplay(reason: String): DevOnlyNpuOneTurnConversationDisplay =
        DevOnlyNpuOneTurnConversationDisplay(
            text = "DEV ONLY NPU ONE TURN",
            output = "",
            status = "failure",
            reason = reason,
            nativeReached = false,
            decodeReached = false,
            npuEvidence = "",
            fallback = false,
            freshCrash = false,
            timeout = reason == "timeout",
            requestedMaxOutputTokens = 32,
            effectiveMaxOutputTokens = 32,
            nativeMaxOutputTokensLimit = "-",
            rawLen = 0,
            sanitizedLen = 0,
            quality = "unknown",
            controlCharSummary = "none",
            rawOutputFirst200Chars = "",
            rawOutputLast200Chars = "",
            rawUnicodeSummary = "control_chars=none",
            sanitizerApplied = "unknown",
            removedTemplateTokenCount = "unknown",
            removedPromptEcho = "unknown",
            replacementCharCount = "0",
            outputContainsControlChars = "false",
        )
}
