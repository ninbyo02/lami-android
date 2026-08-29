package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract
import io.github.ninbyo02.lami.npu.NpuStandardRouteNativeDisplay
import io.github.ninbyo02.lami.npu.NpuStandardRouteNativeRequest
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
        assertEquals("native_entry_unavailable", raw.reason)
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
    fun `real provider rejects standalone assistant marker as role contamination`() {
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
            assertTrue(raw.reason in setOf("assistant_stub", "raw_role_contamination"))
            assertEquals(output, raw.sanitizedOutput)
            assertTrue(raw.qualityClassification in setOf("assistant_stub", "role_contamination"))
            assertFalse(result.successCriteriaMet)
            assertTrue(result.reason in setOf("assistant_stub", "raw_role_contamination"))
            assertTrue(result.qualityClassification in setOf("assistant_stub", "role_contamination"))
        }
    }

    @Test
    fun `real provider recovers exact safe prefix before raw user tail`() {
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

        assertEquals("success", raw.status)
        assertEquals("success", raw.result)
        assertEquals(true, raw.success)
        assertEquals("success", raw.reason)
        assertEquals("どうしましたか。", raw.sanitizedOutput)
        assertEquals("natural_japanese", raw.qualityClassification)
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
        assertEquals("native_entry_unavailable", raw.reason)
        assertEquals("native_entry_unavailable", result.reason)
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
        assertEquals("native_entry_unavailable", raw.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `custom build experiment invoker default propagates real provider unavailable failure in unit test`() {
        val result = NpuStandardRouteS1Mapper.map(NpuStandardRouteS1Invoker().invoke(userPrompt))

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("native_entry_unavailable", result.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `real provider passes user prompt into dev only request`() {
        var capturedRequest: NpuStandardRouteNativeRequest? = null
        val traces = mutableListOf<String>()

        val raw = RealNpuStandardRouteS1Provider(
            requestRunner = { request ->
                capturedRequest = request
                successDisplay(maxOutputTokens = request.maxOutputTokens)
            },
        ).invokeWithContext(
            userPrompt = userPrompt,
            contextText = "ユーザー: 直前の質問\nアシスタント: 直前の回答",
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = traces::add,
        )

        val request = requireNotNull(capturedRequest)
        assertEquals("success", raw.status)
        assertEquals(
            "重複・説明・句読点なしで一度だけ答えてください。\n$userPrompt",
            request.userPrompt,
        )
        assertEquals("ユーザー: 直前の質問", request.contextText)
        assertTrue(request.unsafeDevBypassPromptLengthGate)
        assertEquals(128, request.maxOutputTokens)
        assertEquals(128, raw.requestedMaxOutputTokens)
        assertEquals(128, raw.effectiveMaxOutputTokens)
        assertEquals("raw_dialog_tail_variant_a", request.promptTailVariant)
        assertEquals(60_000L, request.timeoutMs)
        assertTrue(traces.any { it.contains("NPU_REAL_PROMPT provider_prompt_hash=") })
        assertTrue(traces.any { it.contains("NPU_REAL_PROMPT request_prompt_hash=") })
        assertTrue(traces.any { it.contains("prompt_source=standard_route_persistent_npu") })
        assertTrue(traces.any { it.contains("context_code_points=") })
        assertTrue(traces.any { it.contains("final_input_tokens=unavailable") })
        assertTrue(traces.any { it.contains("final_input_hash=") })
        assertTrue(traces.any { it.contains("final_input_code_points=") })
        assertTrue(traces.any { it.contains("native_input_code_point_limit=128") })
        assertTrue(traces.any { it.contains("native_input_within_limit=true") })
        assertTrue(traces.any { it.contains("sampler_config_profile=lami_stable_v1") })
        assertTrue(traces.any { it.contains("sampler_top_k=40") })
        assertTrue(traces.any { it.contains("sampler_top_p=0.9") })
        assertTrue(traces.any { it.contains("sampler_temperature=0.3") })
        assertTrue(traces.any { it.contains("sampler_seed=42") })
        assertTrue(traces.any { it.contains("thinking_enabled=false") })
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
    fun `real provider rejects over-limit final input before native decode`() {
        var runnerInvoked = false
        val raw = RealNpuStandardRouteS1Provider(
            requestRunner = {
                runnerInvoked = true
                successDisplay()
            },
        ).invoke(
            userPrompt = "あ".repeat(200),
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )

        assertFalse(runnerInvoked)
        assertEquals("failure", raw.status)
        assertTrue(raw.reason.startsWith(RealNpuStandardRouteS1Provider.REASON_NATIVE_INPUT_TOO_LONG))
        assertFalse(raw.runDecodeReached)
        assertFalse(raw.fallbackUsed)
    }

    @Test
    fun `real provider bounds conversation history to native input limit`() {
        val request = RealNpuStandardRouteS1Provider.request(
            userPrompt = "続きを教えて",
            contextText = (1..20).joinToString("\n") { index ->
                if (index % 2 == 0) {
                    "アシスタント: これは直前の回答${index}です"
                } else {
                    "ユーザー: これは過去の質問${index}です"
                }
            },
        )
        val finalInput = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
            contextText = request.contextText,
            userPrompt = request.userPrompt,
            promptTailVariant = request.promptTailVariant,
        )

        assertTrue(request.contextText.isNotBlank())
        assertTrue(request.contextText.endsWith("アシスタント: これは直前の回答20です"))
        assertFalse(request.contextText.contains("これは過去の質問1です"))
        assertTrue(
            finalInput.codePointCount(0, finalInput.length) <=
                RealNpuStandardRouteS1Provider.NATIVE_MAX_INPUT_CODE_POINTS,
        )
    }

    @Test
    fun `strict compact recall keeps only the latest corrected fact`() {
        val request = RealNpuStandardRouteS1Provider.request(
            userPrompt = NpuStandardRouteS1Contract.rewritePromptForNative(
                "現在の好きな色名を一文字だけ答えてください。",
            ).rewrittenPromptText,
            contextText = "ユーザー: 好きな色は赤です。色だけ答えてください。\n" +
                "アシスタント: 赤\n" +
                "ユーザー: 好きな色を青に訂正します。青の一文字だけ答えてください。\n" +
                "アシスタント: 青",
        )

        assertFalse(request.contextText.contains("好きな色は赤です"))
        assertTrue(request.contextText.contains("好きな色の最新値は青です"))
        assertFalse(request.contextText.contains("アシスタント:"))
    }

    @Test
    fun `real provider keeps user facts but only includes referenced assistant answers`() {
        val context = "ユーザー: 私の名前は青葉です。\n" +
            "アシスタント: 青葉。"
        val factRecall = RealNpuStandardRouteS1Provider.request(
            userPrompt = "前に伝えた私の名前を一度だけ答えてください。",
            contextText = context,
        )
        val answerReference = RealNpuStandardRouteS1Provider.request(
            userPrompt = "前の回答を一語で答えてください。",
            contextText = context,
        )

        assertTrue(factRecall.contextText.contains("ユーザー: 私の名前は青葉です。"))
        assertFalse(factRecall.contextText.contains("アシスタント:"))
        assertTrue(answerReference.contextText.contains("アシスタント: 青葉。"))
    }

    @Test
    fun `contextual self name recall embeds the fact and omits redundant history`() {
        val context = "ユーザー: 私の名前は佐藤です。\n" +
            "アシスタント: 佐藤さんですね。\n" +
            "ユーザー: 私の名前は分かりますか。\n" +
            "アシスタント: 佐藤"
        var capturedRequest: NpuStandardRouteNativeRequest? = null

        RealNpuStandardRouteS1Provider(
            requestRunner = { request ->
                capturedRequest = request
                successDisplay(output = "佐藤", maxOutputTokens = request.maxOutputTokens)
            },
        ).invokeWithContext(
            userPrompt = "何ですか。",
            contextText = context,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )

        val request = requireNotNull(capturedRequest)
        assertEquals("", request.contextText)
        assertTrue(request.userPrompt.contains("ユーザーの名前は佐藤です"))
        assertTrue(request.userPrompt.contains("佐藤だけ答えてください"))
        val finalInput = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
            contextText = request.contextText,
            userPrompt = request.userPrompt,
            promptTailVariant = request.promptTailVariant,
        )
        assertTrue(
            finalInput.codePointCount(0, finalInput.length) <=
                RealNpuStandardRouteS1Provider.NATIVE_MAX_INPUT_CODE_POINTS,
        )
    }

    @Test
    fun `real provider reports short prompt rewrite and stable sampler policy`() {
        val traces = mutableListOf<String>()

        RealNpuStandardRouteS1Provider(
            requestRunner = { request -> successDisplay(maxOutputTokens = request.maxOutputTokens) },
        ).invoke(
            userPrompt = "こんにちは",
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = traces::add,
        )

        assertTrue(traces.any { it.contains("short_prompt_rewrite_applied=true") })
        assertTrue(traces.any { it.contains("sampler_config_profile=lami_stable_v1") })
        assertTrue(traces.any { it.contains("thinking_enabled=false") })
    }

    @Test
    fun `real provider allows explicit max output tokens within native experiment limit`() {
        var capturedRequest: NpuStandardRouteNativeRequest? = null

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
        assertEquals(1024, request.maxOutputTokens)
        assertEquals(1024, raw.requestedMaxOutputTokens)
        assertEquals(1024, raw.effectiveMaxOutputTokens)
    }

    private fun successDisplay(
        output: String = "こんにちは。",
        rawOutput: String = output,
        maxOutputTokens: Int = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
    ): NpuStandardRouteNativeDisplay =
        NpuStandardRouteNativeDisplay(
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
            nativeMaxOutputTokensLimit = "4096",
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
    ): NpuStandardRouteNativeDisplay =
        NpuStandardRouteNativeDisplay(
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
