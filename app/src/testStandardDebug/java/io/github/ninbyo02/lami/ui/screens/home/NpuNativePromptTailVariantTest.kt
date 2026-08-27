package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.NpuStandardRouteNativeDisplay
import org.junit.Assert.assertEquals
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

        assertEquals("raw_dialog_tail_variant_a", capturedPromptTailVariant)
        assertEquals(true, result.success)
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
