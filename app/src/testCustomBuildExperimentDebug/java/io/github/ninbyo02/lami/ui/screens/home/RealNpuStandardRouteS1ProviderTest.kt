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

        assertEquals(
            "success",
            provider.invoke(
                userPrompt = userPrompt,
                maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
                trace = {},
            ).reason,
        )
    }

    @Test
    fun `real provider maps dev only success display to S1 raw result`() {
        val raw = RealNpuStandardRouteS1Provider(
            requestRunner = { successDisplay() },
        ).invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )

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
        assertEquals(128, raw.requestedMaxOutputTokens)
        assertEquals(128, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `real provider maps dev only failure reason to S1 raw result`() {
        val raw = RealNpuStandardRouteS1Provider(
            requestRunner = { failureDisplay(reason = "npu_evidence_missing") },
        ).invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )

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
        assertEquals(128, raw.requestedMaxOutputTokens)
        assertEquals(128, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `real provider normalizes prompt 8 Japanese internal spaces in final sanitized output`() {
        val raw = RealNpuStandardRouteS1Provider(
            requestRunner = {
                successDisplay(
                    output = "承 知いたしました。\n1. 箇条書きの作成\n2. 3つの項目を提示\n3. 短くまとめ る",
                )
            },
        ).invoke(
            userPrompt = "箇条書きで3つ教えて",
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )
        val result = NpuStandardRouteS1Mapper.map(raw)
        val s2Mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "箇条書きで3つ教えて",
            s1Result = result,
        )

        val expected = "承知いたしました。\n1. 箇条書きの作成\n2. 3つの項目を提示\n3. 短くまとめる"
        assertEquals(expected, raw.sanitizedOutput)
        assertEquals(expected, result.sanitizedOutput)
        assertEquals(expected, result.displayText)
        assertEquals(expected, requireNotNull(s2Mapping.saveCandidate).assistantMessage.text)
        assertEquals(
            "承 知いたしました。\n1. 箇条書きの作成\n2. 3つの項目を提示\n3. 短くまとめ る",
            raw.rawOutput,
        )
    }

    @Test
    fun `real provider returns explicit failure when dev only entry is unavailable in unit test`() {
        val raw = RealNpuStandardRouteS1Provider().invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )

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
        assertEquals(128, raw.requestedMaxOutputTokens)
        assertEquals(128, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `real provider failure maps to failed S1 result without side effects`() {
        val result = NpuStandardRouteS1Mapper.map(
            RealNpuStandardRouteS1Provider(
                requestRunner = { failureDisplay(reason = "timeout") },
            ).invoke(
                userPrompt = userPrompt,
                maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
                trace = {},
            ),
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
        ).invoke(
            userPrompt = "明日の天気は",
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = traces::add,
        )
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
    fun `real provider classifies standalone assistant marker as failure`() {
        listOf(
            "アシスタント。",
            "アシスタント:",
            "Assistant.",
            "Assistant:",
        ).forEach { output ->
            val raw = RealNpuStandardRouteS1Provider(
                requestRunner = { successDisplay(output = output) },
            ).invoke(
                userPrompt = userPrompt,
                maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
                trace = {},
            )
            val result = NpuStandardRouteS1Mapper.map(raw)

            assertEquals("failure", raw.status)
            assertEquals("failure", raw.result)
            assertEquals(false, raw.success)
            assertEquals("assistant_stub", raw.reason)
            assertEquals(output, raw.sanitizedOutput)
            assertEquals("assistant_stub", raw.qualityClassification)
            assertFalse(result.successCriteriaMet)
            assertEquals("assistant_stub", result.reason)
            assertEquals("assistant_stub", result.qualityClassification)
        }
    }

    @Test
    fun `real provider classifies raw role contamination as failure`() {
        val raw = RealNpuStandardRouteS1Provider(
            requestRunner = {
                successDisplay(
                    output = "どうしましたか。",
                    rawOutput = "どうしましたか。\nユーザー: ああああ\nアシスタント: 何か困っていますか。",
                )
            },
        ).invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )

        assertEquals("failure", raw.status)
        assertEquals("failure", raw.result)
        assertEquals(false, raw.success)
        assertEquals("raw_role_contamination", raw.reason)
        assertEquals("どうしましたか。", raw.sanitizedOutput)
        assertEquals("role_contamination", raw.qualityClassification)
        assertTrue(raw.rawOutput.contains("ユーザー:"))
        assertTrue(raw.rawOutput.contains("アシスタント:"))
    }

    @Test
    fun `custom build experiment default provider selects real provider and reports unavailable in unit test`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider().invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )
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
            .invoke(
                userPrompt = userPrompt,
                maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
                trace = {},
            )
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
                successDisplay(maxOutputTokens = request.maxOutputTokens)
            },
        ).invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = traces::add,
        )

        val request = requireNotNull(capturedRequest)
        assertEquals("success", raw.status)
        assertEquals(userPrompt, request.userPrompt)
        assertEquals("", request.contextText)
        assertTrue(request.unsafeDevBypassPromptLengthGate)
        assertEquals(128, request.maxOutputTokens)
        assertEquals(128, raw.requestedMaxOutputTokens)
        assertEquals(128, raw.effectiveMaxOutputTokens)
        assertEquals("gemma_it_user_model", request.promptTailVariant)
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

    @Test
    fun `real provider clamps explicit max output tokens to native experiment limit`() {
        var capturedRequest: DevOnlyNpuOneTurnConversationRequest? = null

        val raw = RealNpuStandardRouteS1Provider(
            requestRunner = { request ->
                capturedRequest = request
                successDisplay(maxOutputTokens = request.maxOutputTokens)
            },
        ).invoke(
            userPrompt = userPrompt,
            maxOutputTokens = 1024,
            trace = {},
        )

        val request = requireNotNull(capturedRequest)
        assertEquals(512, request.maxOutputTokens)
        assertEquals(1024, raw.requestedMaxOutputTokens)
        assertEquals(512, raw.effectiveMaxOutputTokens)
    }

    private fun successDisplay(
        output: String = "こんにちは。",
        rawOutput: String = output,
        maxOutputTokens: Int = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
    ): DevOnlyNpuOneTurnConversationDisplay =
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
            requestedMaxOutputTokens = maxOutputTokens,
            effectiveMaxOutputTokens = maxOutputTokens,
            nativeMaxOutputTokensLimit = "512",
            rawLen = rawOutput.length,
            sanitizedLen = output.length,
            quality = "natural_japanese",
            controlCharSummary = "none",
            rawOutputFirst200Chars = rawOutput.take(200),
            rawOutputLast200Chars = rawOutput.takeLast(200),
            rawUnicodeSummary = "control_chars=none",
            sanitizerApplied = "true",
            removedTemplateTokenCount = "0",
            removedPromptEcho = "false",
            replacementCharCount = "0",
            outputContainsControlChars = "false",
            rawOutput = rawOutput,
        )

    private fun failureDisplay(
        reason: String,
        maxOutputTokens: Int = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
    ): DevOnlyNpuOneTurnConversationDisplay =
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
            requestedMaxOutputTokens = maxOutputTokens,
            effectiveMaxOutputTokens = maxOutputTokens,
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
