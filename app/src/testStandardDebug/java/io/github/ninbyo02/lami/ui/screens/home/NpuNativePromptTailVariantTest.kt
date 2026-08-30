package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.NpuStandardRouteNativeDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuNativePromptTailVariantTest {
    @Test
    fun `real provider uses stable raw dialog tail for native request`() {
        var capturedPromptTailVariant: String? = null
        val provider = RealNpuStandardRouteS1Provider { request ->
            capturedPromptTailVariant = request.promptTailVariant
            successDisplay(request.maxOutputTokens)
        }

        val result = provider.invoke(
            userPrompt = "こんにちは",
            maxOutputTokens = 32,
            trace = {},
        )

        assertEquals("model_metadata_gemma4_turn_v1", capturedPromptTailVariant)
        assertEquals(true, result.success)
        val diagnostics = NpuStandardRouteS1Contract.displayText(
            selection = NpuStandardRouteS1Selection(enabled = true),
            status = result.status,
            reason = result.reason,
            rawOutput = result.rawOutput,
            sanitizedOutput = result.sanitizedOutput,
            qualityClassification = result.qualityClassification,
            runDecodeReached = result.runDecodeReached,
            npuBackendEvidence = result.npuBackendEvidence,
            fallbackUsed = result.fallbackUsed,
            timeout = result.timeout,
            freshCrash = result.freshCrash,
        )
        assertTrue(diagnostics.contains("prompt_template_owner=model_metadata"))
        assertTrue(diagnostics.contains("prompt_template_evaluator=lami_verified_model_template_renderer"))
        assertTrue(diagnostics.contains("conversation_api_used=false"))
        assertTrue(diagnostics.contains("app_template_used=false"))
        assertTrue(diagnostics.contains("template_ownership_unified=true"))
    }

    private fun successDisplay(maxOutputTokens: Int) = NpuStandardRouteNativeDisplay(
        text = "test",
        output = "こんにちは。",
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        nativeReached = true,
        decodeReached = true,
        npuEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallback = false,
        freshCrash = false,
        timeout = false,
        requestedMaxOutputTokens = maxOutputTokens,
        effectiveMaxOutputTokens = maxOutputTokens,
        nativeMaxOutputTokensLimit = maxOutputTokens.toString(),
        rawLen = 6,
        sanitizedLen = 6,
        quality = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        controlCharSummary = "none",
        rawOutputFirst200Chars = "こんにちは。",
        rawOutputLast200Chars = "こんにちは。",
        rawUnicodeSummary = "unavailable",
        sanitizerApplied = "false",
        removedTemplateTokenCount = "0",
        removedPromptEcho = "false",
        replacementCharCount = "0",
        outputContainsControlChars = "false",
        rawOutput = "こんにちは。",
    )
}
