package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealNpuStandardRouteS1ProviderTest {
    @Test
    fun `real provider implements S1 provider contract`() {
        val provider: NpuStandardRouteS1Provider = RealNpuStandardRouteS1Provider(
            displayRunner = { successDisplay() },
        )

        assertEquals("success", provider.invoke().reason)
    }

    @Test
    fun `real provider maps dev only success display to S1 raw result`() {
        val raw = RealNpuStandardRouteS1Provider(
            displayRunner = { successDisplay() },
        ).invoke()

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
            displayRunner = { failureDisplay(reason = "npu_evidence_missing") },
        ).invoke()

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
        val raw = RealNpuStandardRouteS1Provider().invoke()

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
                displayRunner = { failureDisplay(reason = "timeout") },
            ).invoke(),
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
    fun `custom build experiment default provider selects real provider and reports unavailable in unit test`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider().invoke()
        val result = NpuStandardRouteS1Mapper.map(raw)

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", raw.status)
        assertEquals("dev_only_entry_unavailable", raw.reason)
        assertEquals("dev_only_entry_unavailable", result.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `custom build experiment invoker default propagates real provider unavailable failure in unit test`() {
        val result = NpuStandardRouteS1Mapper.map(NpuStandardRouteS1Invoker().invoke())

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("dev_only_entry_unavailable", result.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
    }

    private fun successDisplay(): DevOnlyNpuOneTurnConversationDisplay =
        DevOnlyNpuOneTurnConversationDisplay(
            text = "DEV ONLY NPU ONE TURN",
            output = "こんにちは。",
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
            rawLen = 6,
            sanitizedLen = 6,
            quality = "natural_japanese",
            controlCharSummary = "none",
            rawOutputFirst200Chars = "こんにちは。",
            rawOutputLast200Chars = "こんにちは。",
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
