package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.NpuStandardRouteSelectionSource
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1ProviderTest {
    private val userPrompt = "好きな色を一つだけ答えてください"

    @Test
    fun `fixed provider returns default S1 success raw result`() {
        val raw = FixedNpuStandardRouteS1Provider().invoke(
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
    fun `failure provider returns mapper compatible failure raw result`() {
        val raw = FailureNpuStandardRouteS1Provider(reason = "test_failure").invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        assertEquals("failure", raw.status)
        assertEquals("failure", raw.result)
        assertEquals(false, raw.success)
        assertEquals("test_failure", raw.reason)
        assertEquals("", raw.sanitizedOutput)
        assertFalse(raw.runDecodeReached)
        assertEquals("", raw.npuBackendEvidence)
        assertEquals(128, raw.requestedMaxOutputTokens)
        assertEquals(128, raw.effectiveMaxOutputTokens)
        assertFalse(mapped.successCriteriaMet)
        assertEquals("failure", mapped.status)
        assertEquals("test_failure", mapped.reason)
    }

    @Test
    fun `invoker default provider follows build variant provider selection`() {
        val raw = NpuStandardRouteS1Invoker().invoke(userPrompt)
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            assertFalse(mapped.successCriteriaMet)
            assertEquals(RealNpuStandardRouteS1Provider.REASON_DEV_ONLY_ENTRY_UNAVAILABLE, mapped.reason)
        } else {
            assertTrue(mapped.successCriteriaMet)
            assertEquals("こんにちは。", mapped.displayText)
        }
        assertTrue(mapped.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `default provider follows build variant provider selection`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider().invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            assertFalse(mapped.successCriteriaMet)
            assertEquals("failure", raw.status)
            assertEquals(RealNpuStandardRouteS1Provider.REASON_DEV_ONLY_ENTRY_UNAVAILABLE, raw.reason)
        } else {
            assertTrue(mapped.successCriteriaMet)
            assertEquals("success", raw.status)
            assertEquals("こんにちは。", raw.sanitizedOutput)
            assertEquals("QNN_HTP_V79_FastRPC_native_diag", raw.npuBackendEvidence)
        }
    }

    @Test
    fun `provider selector uses fixed provider when S1 gate is disabled`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider(s1GateEnabled = false).invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        assertTrue(mapped.successCriteriaMet)
        assertEquals("success", raw.status)
        assertEquals("こんにちは。", raw.sanitizedOutput)
    }

    @Test
    fun `provider selector unblocks native route for normal chat when S1 gate is enabled`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider(s1GateEnabled = true).invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        assertFalse(mapped.successCriteriaMet)
        assertEquals("failure", raw.status)
        assertEquals(RealNpuStandardRouteS1Provider.REASON_DEV_ONLY_ENTRY_UNAVAILABLE, raw.reason)
        assertTrue(
            mapped.withTiming(NpuStandardRouteS1Timing(totalMs = 0L))
                .displayText
                .contains("normal_chat_native_route_blocked=false"),
        )
    }

    @Test
    fun `provider selector allows normal chat native route when promotion gate passes`() {
        val promotionGate = NpuS1PromotionGateResult(
            status = NPU_S1_PROMOTION_GATE_STATUS_PASS,
            reason = NPU_S1_PROMOTION_GATE_REASON_READY_BUT_NORMAL_CHAT_BLOCKED,
            normalChatUnblockAllowed = true,
        )
        val raw = NpuStandardRouteS1ProviderSelector.defaultProviderWithPromotionGate(
            s1GateEnabled = true,
            promotionGate = promotionGate,
        ).invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )

        assertTrue(NpuStandardRouteS1ProviderSelector.normalChatNativeRouteUnblockAllowed(promotionGate))
        assertEquals("failure", raw.status)
        assertEquals(RealNpuStandardRouteS1Provider.REASON_DEV_ONLY_ENTRY_UNAVAILABLE, raw.reason)
    }

    @Test
    fun `dev diagnostic provider keeps real native provider path available`() {
        val raw = NpuStandardRouteS1ProviderSelector.devDiagnosticProviderForMode(NpuStandardRouteMode.S1_ONLY).invoke(
            userPrompt = userPrompt,
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            trace = {},
        )

        assertEquals("failure", raw.status)
        assertEquals("dev_only_entry_unavailable", raw.reason)
    }

    @Test
    fun `fixed provider uses explicit max output token setting`() {
        val raw = FixedNpuStandardRouteS1Provider().invoke(
            userPrompt = userPrompt,
            maxOutputTokens = 512,
            trace = {},
        )

        assertEquals(512, raw.requestedMaxOutputTokens)
        assertEquals(512, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `provider selector for Settings mode keeps standard OFF fixed and S1 real while preserving custom compatibility`() {
        val offRaw = NpuStandardRouteS1ProviderSelector.defaultProviderForMode(NpuStandardRouteMode.OFF)
            .invoke(
                userPrompt = userPrompt,
                maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
                trace = {},
            )
        val s1Raw = NpuStandardRouteS1ProviderSelector.defaultProviderForMode(NpuStandardRouteMode.S1_ONLY)
            .invoke(
                userPrompt = userPrompt,
                maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
                trace = {},
            )

        if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            assertEquals("failure", offRaw.status)
            assertEquals(RealNpuStandardRouteS1Provider.REASON_DEV_ONLY_ENTRY_UNAVAILABLE, offRaw.reason)
        } else {
            assertEquals("success", offRaw.status)
            assertEquals("こんにちは。", offRaw.sanitizedOutput)
        }
        assertEquals("failure", s1Raw.status)
        assertEquals(RealNpuStandardRouteS1Provider.REASON_DEV_ONLY_ENTRY_UNAVAILABLE, s1Raw.reason)
    }

    @Test
    fun `real provider class is resolvable from debug source set`() {
        val providerClass = Class.forName(NpuStandardRouteS1ProviderSelector.REAL_PROVIDER_CLASS_NAME)

        assertTrue(NpuStandardRouteS1Provider::class.java.isAssignableFrom(providerClass))
    }

    @Test
    fun `raw dialog tail variant B remains available for matrix comparison`() {
        val contractClass = Class.forName("io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract")
        val contract = contractClass.getField("INSTANCE").get(null)
        val variantB = contractClass.getField("RAW_DIALOG_TAIL_VARIANT_B").get(null) as String
        val prompt = contractClass
            .getMethod(
                "buildRawDialogTailPrompt",
                String::class.java,
                String::class.java,
                String::class.java,
            )
            .invoke(contract, "", "こんにちは", variantB) as String

        assertTrue(prompt.contains("最終回答だけ"))
        assertTrue(prompt.contains("「ユーザー:」「アシスタント:」"))
        assertTrue(prompt.contains("会話の続きを書かない"))
        assertTrue(prompt.endsWith("アシスタント: はい、"))
    }

    @Test
    fun `standard route uses Gemma IT user model wrapper`() {
        val contractClass = Class.forName("io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract")
        val contract = contractClass.getField("INSTANCE").get(null)
        val gemmaVariant = contractClass.getField("GEMMA_IT_USER_MODEL_VARIANT").get(null) as String
        assertEquals("gemma_it_user_model", NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT)
        listOf(
            "こんにちは",
            "あなたは誰ですか？",
            "カレーの材料を箇条書きで教えて",
        ).forEach { userPrompt ->
            val prompt = contractClass
                .getMethod(
                    "buildRawDialogTailPrompt",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                )
                .invoke(contract, "", userPrompt, gemmaVariant) as String
            val request = RealNpuStandardRouteS1Provider.request(
                userPrompt = userPrompt,
                maxOutputTokens = 32,
            )

            assertEquals("gemma_it_user_model", request.promptTailVariant)
            assertTrue(
                RealNpuStandardRouteS1Provider.buildNpuRealPromptRequestTrace(request)
                    .contains("prompt_wrapper_used=gemma_it_user_model"),
            )
            assertEquals(
                "<start_of_turn>user\n$userPrompt<end_of_turn>\n<start_of_turn>model",
                prompt,
            )
        }
    }

    @Test
    fun `standard route rewrites short arithmetic prompts inside Gemma IT wrapper`() {
        val contractClass = Class.forName("io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationContract")
        val contract = contractClass.getField("INSTANCE").get(null)
        val gemmaVariant = contractClass.getField("GEMMA_IT_USER_MODEL_VARIANT").get(null) as String
        val prompt = contractClass
            .getMethod(
                "buildRawDialogTailPrompt",
                String::class.java,
                String::class.java,
                String::class.java,
            )
            .invoke(contract, "", "１＋１は？", gemmaVariant) as String
        val request = RealNpuStandardRouteS1Provider.request(
            userPrompt = "１＋１は？",
            maxOutputTokens = 32,
        )
        val trace = RealNpuStandardRouteS1Provider.buildNpuRealPromptRequestTrace(request)

        assertEquals(
            "<start_of_turn>user\n" +
                "次の計算に日本語で答えてください。答えだけ簡潔に書いてください。\n" +
                "問題: １＋１は？\n" +
                "答え:<end_of_turn>\n" +
                "<start_of_turn>model",
            prompt,
        )
        assertTrue(trace.contains("arithmetic_prompt_detected=true"))
        assertTrue(trace.contains("short_prompt_rewrite_applied=true"))
    }

    @Test
    fun `invoker accepts provider interface without ChatScreen dependency`() {
        val invoker = NpuStandardRouteS1Invoker(
            provider = FailureNpuStandardRouteS1Provider(
                reason = "provider_injected_failure",
                fallbackUsed = true,
            ),
        )
        val mapped = NpuStandardRouteS1Mapper.map(invoker.invoke(userPrompt))

        assertFalse(mapped.successCriteriaMet)
        assertEquals("provider_injected_failure", mapped.reason)
        assertTrue(mapped.fallbackUsed)
        assertTrue(mapped.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `real prompt trace uses hash and preview without full prompt`() {
        val trace = buildNpuRealPromptHandoffTrace(stage = "chat", userPrompt = userPrompt)

        assertTrue(trace.contains("NPU_REAL_PROMPT chat_prompt_hash="))
        assertTrue(trace.contains("chat_prompt_length=${userPrompt.length}"))
        assertTrue(trace.contains("chat_prompt_code_points=${userPrompt.codePointCount(0, userPrompt.length)}"))
        assertTrue(trace.contains("chat_prompt_preview="))
        assertFalse(trace.contains(userPrompt))
    }

    @Test
    fun `S1 dev trace summarizes input and outputs without full long text`() {
        val longPrompt = "こんばんは。NPU標準ルートのデバッグ表示で全文が出ないことを確認します。"
        val longRawOutput = "こんばんは。これはraw outputの長い確認文です。全文ではなくpreviewだけを表示します。"
        val longSanitizedOutput = "こんばんは。これはsanitized outputの長い確認文です。全文ではなくpreviewだけを表示します。"
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = longRawOutput,
                sanitizedOutput = longSanitizedOutput,
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )

        val trace = buildNpuStandardRouteS1DevTraceText(
            input = longPrompt,
            result = result,
            maxOutputTokens = 512,
        )

        assertTrue(trace.contains("max_output_tokens=512"))
        assertTrue(trace.contains("input_hash="))
        assertTrue(trace.contains("input_prompt="))
        assertTrue(trace.contains("input_preview="))
        assertTrue(trace.contains("..."))
        assertTrue(trace.contains("input_length=${longPrompt.length}"))
        assertTrue(trace.contains("input_code_points=${longPrompt.codePointCount(0, longPrompt.length)}"))
        assertTrue(trace.contains("raw_output_hash="))
        assertTrue(trace.contains("raw_output_length=${longRawOutput.length}"))
        assertTrue(trace.contains("sanitized_output_hash="))
        assertTrue(trace.contains("sanitized_output_length=${longSanitizedOutput.length}"))
        assertTrue(trace.contains("status=success"))
        assertTrue(trace.contains("reason=success"))
        assertTrue(trace.contains("quality_classification=natural_japanese"))
        assertTrue(trace.contains("run_decode_reached=true"))
        assertTrue(trace.contains("timeout=false"))
        assertTrue(trace.contains("fallback=false"))
        assertTrue(trace.contains("fresh_crash=false"))
        assertFalse(trace.contains(longPrompt))
        assertFalse(trace.contains(longRawOutput))
        assertFalse(trace.contains(longSanitizedOutput))
    }

    @Test
    fun `S1 diagnostic copy uses compact section for successful results`() {
        val input = "こんばんは"
        val rawOutput = "raw\noutput"
        val sanitizedOutput = "こんばんは。"
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = rawOutput,
                sanitizedOutput = sanitizedOutput,
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 256,
                effectiveMaxOutputTokens = 256,
            ),
        )

        val copyText = buildNpuStandardRouteS1DiagnosticCopyText(
            input = input,
            result = result,
            maxOutputTokens = 256,
        )

        assertTrue(copyText.contains("[DEV診断: NPU S1 compact]"))
        assertFalse(copyText.contains("[DEV診断: NPU S1 failure details]"))
        assertFalse(copyText.contains("[DEV診断: NPU S1 full dump]"))
        assertFalse(copyText.contains("[DEV診断: NPU S1 repeated run summary]"))
        assertFalse(copyText.contains("[DEV診断: NPU S1 persistent Engine summary]"))
        assertFalse(copyText.contains("[DEV診断: NPU S1 persistent custom JNI summary]"))
        assertTrue(copyText.contains("input_prompt=こんばんは"))
        assertTrue(copyText.contains("arithmetic_prompt_detected=false"))
        assertTrue(copyText.contains("short_prompt_rewrite_applied=false"))
        assertTrue(copyText.contains("arithmetic_tail_leak_detected=false"))
        assertTrue(copyText.contains("arithmetic_tail_leak_ignored_for_display=false"))
        assertTrue(copyText.contains("actual_display_text=こんばんは。"))
        assertTrue(copyText.contains("tts_text=こんばんは。"))
        assertFalse(copyText.contains("max_output_tokens=256"))
        assertFalse(copyText.contains("selected_model_name=unknown"))
        assertFalse(copyText.contains("finish_reason="))
        assertFalse(copyText.contains("tokenizer_output_tokens="))
        assertTrue(copyText.contains("raw_output=raw\\noutput"))
        assertTrue(copyText.contains("sanitized_output=こんばんは。"))
        assertTrue(copyText.contains("status=success"))
        assertTrue(copyText.contains("reason=success"))
        assertTrue(copyText.contains("quality_classification=natural_japanese"))
        assertTrue(copyText.contains("run_decode_reached=true"))
        assertTrue(copyText.contains("timeout=false"))
        assertTrue(copyText.contains("fallback=false"))
        assertTrue(copyText.contains("fresh_crash=false"))
    }

    @Test
    fun `S1 compact diagnostic records arithmetic tail leak ignored for display`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = ">2</start_of_turn>\n<end_of_turn>\n<start_of_turn>user>次の計算に日本語で",
                sanitizedOutput = "2</start_of_turn>\n\n次の計算に日本語で",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                inputPrompt = "1+1は？",
            ),
        )

        val copyText = buildNpuStandardRouteS1DiagnosticCopyText(
            input = "1+1は？",
            result = result,
        )

        assertTrue(result.successCriteriaMet)
        assertTrue(copyText.contains("[DEV診断: NPU S1 compact]"))
        assertFalse(copyText.contains("[DEV診断: NPU S1 failure details]"))
        assertTrue(copyText.contains("output_quality_candidate_status=quality_candidate_pass"))
        assertTrue(copyText.contains("output_quality_candidate_prepared_output=2"))
        assertTrue(copyText.contains("arithmetic_tail_leak_detected=true"))
        assertTrue(copyText.contains("arithmetic_tail_leak_ignored_for_display=true"))
        assertTrue(copyText.contains("actual_display_text=2"))
        assertTrue(copyText.contains("tts_text=2です。"))
        assertTrue(
            copyText.contains(
                "output_quality_candidate_reason=" +
                    "natural_japanese_after_arithmetic_answer_extraction_with_tail_leak_cleanup",
            ),
        )
    }

    @Test
    fun `S1 full dump keeps verbose values outside compact copy`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "raw",
                sanitizedOutput = "こんばんは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 256,
                effectiveMaxOutputTokens = 256,
            ),
        )

        val dump = buildNpuStandardRouteS1FullDumpDiagnosticCopyText(
            input = "こんばんは",
            result = result,
            maxOutputTokens = 256,
        )

        assertTrue(dump.contains("[DEV診断: NPU S1 full dump]"))
        assertTrue(dump.contains("max_output_tokens=256"))
        assertTrue(dump.contains("selected_model_name=unknown"))
        assertTrue(dump.contains("[DEV診断: NPU S1 short output telemetry]"))
        assertTrue(dump.contains("finish_reason=unavailable"))
        assertTrue(dump.contains("tokenizer_output_tokens=unavailable"))
    }

    @Test
    fun `S1 compact and full dump include phase1 diagnostics when dev gate is enabled`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = { "true" },
        )
        val fullDump = buildNpuStandardRouteS1FullDumpDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = { "true" },
        )

        listOf(compact, fullDump).forEach { text ->
            assertTrue(text.contains("npu_standard_route_dev_gate_enabled=true"))
            assertTrue(text.contains("npu_standard_route_phase=1"))
            assertTrue(text.contains("npu_standard_route_phase_name=1_route_entry_diagnostic"))
            assertTrue(text.contains("npu_standard_route_connected=true"))
            assertTrue(text.contains("conversation_created=false"))
            assertTrue(text.contains("generate_response=false"))
            assertTrue(text.contains("npu_standard_route_quality_gate_passed=true"))
            assertTrue(text.contains("npu_standard_route_output_suppressed=false"))
            assertTrue(text.contains("npu_standard_route_suppression_reason=none"))
            assertTrue(text.contains("npu_standard_route_ui_append_allowed=false"))
            assertTrue(text.contains("npu_standard_route_tts_allowed=false"))
            assertTrue(text.contains("npu_standard_route_db_save_allowed=false"))
            assertTrue(text.contains("npu_standard_route_markdown_allowed=false"))
            assertTrue(text.contains("npu_standard_route_streaming_allowed=false"))
        }
    }

    @Test
    fun `S1 dev trace and NPU diagnostic copy include phase1 diagnostics when dev gate is enabled`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )

        val trace = buildNpuStandardRouteS1DevTraceText(
            input = "こんにちは",
            result = result,
            npuStandardRouteDevGatePropertyReader = { "true" },
        )
        val copyText = buildNpuDiagnosticKeysCopyText(
            stats = InferenceStats(localSourceSummary = trace),
        )

        assertTrue(trace.contains("npu_standard_route_dev_gate_enabled=true"))
        assertTrue(trace.contains("npu_standard_route_phase=1"))
        assertTrue(trace.contains("npu_standard_route_phase_name=1_route_entry_diagnostic"))
        assertTrue(trace.contains("npu_standard_route_connected=true"))
        assertTrue(trace.contains("conversation_created=false"))
        assertTrue(trace.contains("generate_response=false"))
        assertTrue(copyText.contains("npu_standard_route_dev_gate_enabled=true"))
        assertTrue(copyText.contains("npu_standard_route_phase=1"))
        assertTrue(copyText.contains("npu_standard_route_phase_name=1_route_entry_diagnostic"))
        assertTrue(copyText.contains("npu_standard_route_connected=true"))
        assertTrue(copyText.contains("conversation_created=false"))
        assertTrue(copyText.contains("generate_response=false"))
        assertTrue(copyText.contains("npu_standard_route_ui_append_allowed=false"))
        assertTrue(copyText.contains("npu_standard_route_tts_allowed=false"))
        assertTrue(copyText.contains("npu_standard_route_db_save_allowed=false"))
        assertTrue(copyText.contains("npu_standard_route_markdown_allowed=false"))
        assertTrue(copyText.contains("npu_standard_route_streaming_allowed=false"))
    }

    @Test
    fun `S1 compact full dump trace and diagnostic copy include completed route rollout diagnostics`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "0"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            npuStandardRouteSelectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            npuStandardRouteDevGatePropertyReader = reader,
        )
        val fullDump = buildNpuStandardRouteS1FullDumpDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            npuStandardRouteSelectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            npuStandardRouteDevGatePropertyReader = reader,
        )
        val trace = buildNpuStandardRouteS1DevTraceText(
            input = "こんにちは",
            result = result,
            preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            npuStandardRouteSelectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            npuStandardRouteDevGatePropertyReader = reader,
        )
        val copyText = buildNpuDiagnosticKeysCopyText(
            stats = InferenceStats(localSourceSummary = trace),
        )

        listOf(compact, fullDump, trace, copyText).forEach { text ->
            assertTrue(text.contains("npu_standard_route_rollout_gate_enabled=true"))
            assertTrue(text.contains("npu_standard_route_selection_mode=user_facing_npu_experimental"))
            assertTrue(text.contains("npu_standard_route_user_facing_backend=NPU Experimental"))
            assertTrue(text.contains("npu_standard_route_completed_phase_default=8"))
            assertTrue(text.contains("npu_standard_route_completed_route_selected=true"))
            assertTrue(text.contains("npu_standard_route_developer_phase_override=false"))
            assertTrue(text.contains("npu_standard_route_completed_route_block_reason=none"))
            assertTrue(text.contains("npu_standard_route_effective_phase_source=completed_route_default"))
            assertTrue(text.contains("npu_standard_route_effective_phase=8"))
            assertTrue(text.contains("npu_standard_route_user_facing_selected_backend=NPU Experimental"))
            assertTrue(text.contains("npu_standard_route_completed_route_family=npu_standard_route_completed"))
            assertTrue(text.contains("npu_standard_route_internal_legacy_backend=NPU_S5"))
            assertTrue(text.contains("npu_standard_route_internal_legacy_route_family=npu_s5"))
            assertTrue(text.contains("npu_standard_route_phase=8"))
            assertTrue(text.contains("npu_standard_route_phase_name=7b_pseudo_streaming_gate"))
        }
        assertTrue(compact.contains("selected_backend=NPU_S5"))
        assertTrue(compact.contains("route_family=npu_s5"))
    }

    @Test
    fun `S1 completed route diagnostics infer user facing NPU when selection source is legacy unspecified and phase is zero`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "0"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            npuStandardRouteSelectionSource = NpuStandardRouteSelectionSource.LEGACY_UNSPECIFIED,
            npuStandardRouteDevGatePropertyReader = reader,
        )

        assertTrue(compact.contains("npu_standard_route_selection_mode=user_facing_npu_experimental"))
        assertTrue(compact.contains("npu_standard_route_completed_route_selected=true"))
        assertTrue(compact.contains("npu_standard_route_effective_phase_source=completed_route_default"))
        assertTrue(compact.contains("npu_standard_route_effective_phase=8"))
        assertTrue(compact.contains("npu_standard_route_completed_route_family=npu_standard_route_completed"))
        assertTrue(compact.contains("npu_standard_route_phase=8"))
        assertTrue(compact.contains("npu_standard_route_phase_name=7b_pseudo_streaming_gate"))
        assertTrue(compact.contains("npu_standard_route_ui_append_allowed=true"))
        assertTrue(compact.contains("npu_standard_route_tts_allowed=true"))
        assertTrue(compact.contains("npu_standard_route_db_save_allowed=true"))
        assertTrue(compact.contains("npu_standard_route_markdown_allowed=true"))
        assertTrue(compact.contains("npu_standard_route_streaming_allowed=true"))
        assertTrue(compact.contains("selected_backend=NPU_S5"))
        assertTrue(compact.contains("route_family=npu_s5"))
    }

    @Test
    fun `S1 completed route diagnostics allow phase8 delivery without dev gate for NPU Experimental`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "0"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            npuStandardRouteSelectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            npuStandardRouteDevGatePropertyReader = reader,
        )

        assertTrue(compact.contains("npu_standard_route_dev_gate_enabled=false"))
        assertTrue(compact.contains("npu_standard_route_rollout_gate_enabled=true"))
        assertTrue(compact.contains("npu_standard_route_dev_gate_required=false"))
        assertTrue(compact.contains("npu_standard_route_completed_route_selected=true"))
        assertTrue(compact.contains("npu_standard_route_completed_route_block_reason=none"))
        assertTrue(compact.contains("npu_standard_route_completed_route_disabled_by_property=false"))
        assertTrue(compact.contains("npu_standard_route_completed_route_rollout_state=enabled"))
        assertTrue(compact.contains("npu_standard_route_phase=8"))
        assertTrue(compact.contains("npu_standard_route_phase_name=7b_pseudo_streaming_gate"))
        assertTrue(compact.contains("npu_standard_route_ui_append_allowed=true"))
        assertTrue(compact.contains("npu_standard_route_tts_allowed=true"))
        assertTrue(compact.contains("npu_standard_route_db_save_allowed=true"))
        assertTrue(compact.contains("npu_standard_route_markdown_allowed=true"))
        assertTrue(compact.contains("npu_standard_route_streaming_allowed=true"))
    }

    @Test
    fun `S1 completed route suppresses quality candidate fail without dev gate`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "_turn>\n<end_of_turn>\n<start_of_turn>model",
                sanitizedOutput = "_turn>",
                qualityClassification = "template_artifact",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "0"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "template cleanup が出やすい短文",
            result = result,
            preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            npuStandardRouteSelectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            npuStandardRouteDevGatePropertyReader = reader,
        )

        assertTrue(compact.contains("npu_standard_route_dev_gate_enabled=false"))
        assertTrue(compact.contains("npu_standard_route_phase=8"))
        assertTrue(compact.contains("output_quality_candidate_status=quality_candidate_fail"))
        assertTrue(compact.contains("npu_standard_route_output_suppressed=true"))
        assertTrue(compact.contains("npu_standard_route_output_delivery_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_ui_append_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_tts_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_db_save_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_markdown_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_streaming_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_rollback_required=true"))
    }

    @Test
    fun `S1 completed route kill switch emits NPU safe block diagnostics without generation`() {
        val result = buildNpuStandardRouteKillSwitchBlockedResult(
            maxOutputTokens = NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS,
            selectedModelName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
            selectedModelFile = "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
            npuModelEligible = true,
            inputPrompt = "こんにちは",
        )
        val reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "0"
                NPU_STANDARD_ROUTE_COMPLETED_ROUTE_DISABLED_PROPERTY -> "true"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.FULL,
            npuStandardRouteSelectionSource = NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            npuStandardRouteDevGatePropertyReader = reader,
        )

        assertTrue(compact.contains("status=blocked"))
        assertTrue(compact.contains("reason=kill_switch_disabled"))
        assertTrue(compact.contains("selected_backend=NPU_S5"))
        assertTrue(compact.contains("requested_backend=NPU"))
        assertTrue(compact.contains("effective_backend=NPU"))
        assertTrue(compact.contains("backend_evidence=NPU_completed_route_kill_switch_blocked"))
        assertTrue(compact.contains("route_family=npu_s5"))
        assertTrue(compact.contains("fallback=false"))
        assertTrue(compact.contains("timeout=false"))
        assertTrue(compact.contains("fresh_crash=false"))
        assertTrue(compact.contains("npu_standard_route_dev_gate_enabled=false"))
        assertTrue(compact.contains("npu_standard_route_rollout_gate_enabled=true"))
        assertTrue(compact.contains("npu_standard_route_dev_gate_required=false"))
        assertTrue(compact.contains("npu_standard_route_selection_mode=user_facing_npu_experimental"))
        assertTrue(compact.contains("npu_standard_route_completed_route_selected=false"))
        assertTrue(compact.contains("npu_standard_route_completed_route_block_reason=kill_switch_disabled"))
        assertTrue(compact.contains("npu_standard_route_completed_route_kill_switch_enabled=true"))
        assertTrue(compact.contains("npu_standard_route_completed_route_disabled_by_property=true"))
        assertTrue(compact.contains("npu_standard_route_completed_route_rollout_state=disabled_by_kill_switch"))
        assertTrue(compact.contains("npu_standard_route_effective_phase_source=completed_route_default"))
        assertTrue(compact.contains("npu_standard_route_effective_phase=8"))
        assertTrue(compact.contains("npu_standard_route_completed_route_family=npu_standard_route_completed"))
        assertTrue(compact.contains("npu_standard_route_phase=8"))
        assertTrue(compact.contains("npu_standard_route_phase_name=7b_pseudo_streaming_gate"))
        assertTrue(compact.contains("conversation_created=false"))
        assertTrue(compact.contains("generate_response=false"))
        assertTrue(compact.contains("npu_standard_route_output_delivery_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_ui_append_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_ui_append_executed=false"))
        assertTrue(compact.contains("npu_standard_route_tts_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_tts_requested=false"))
        assertTrue(compact.contains("npu_standard_route_tts_started=false"))
        assertTrue(compact.contains("npu_standard_route_db_save_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_db_save_executed=false"))
        assertTrue(compact.contains("npu_standard_route_markdown_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_markdown_executed=false"))
        assertTrue(compact.contains("npu_standard_route_streaming_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_streaming_executed=false"))
        assertTrue(compact.contains("npu_standard_route_native_streaming_used=false"))
        assertTrue(compact.contains("npu_standard_route_rollback_required=true"))
        assertTrue(compact.contains("npu_standard_route_rollback_reason=kill_switch_disabled_before_generation"))
        assertTrue(compact.contains("run_decode_reached=false"))
        assertTrue(compact.contains("native_call_reached=false"))
        assertTrue(compact.contains("native_call_returned=false"))
        assertTrue(compact.contains("native_decode_started=false"))
        assertTrue(compact.contains("native_decode_finished=false"))
    }

    @Test
    fun `NPU diagnostic copy includes completed route kill switch safe block keys`() {
        val trace = """
            status=blocked reason=kill_switch_disabled selected_backend=NPU_S5 requested_backend=NPU effective_backend=NPU route_family=npu_s5 backend_evidence=NPU_completed_route_kill_switch_blocked fallback=false timeout=false fresh_crash=false
            npu_standard_route_selection_mode=user_facing_npu_experimental npu_standard_route_completed_route_selected=false npu_standard_route_completed_route_block_reason=kill_switch_disabled npu_standard_route_completed_route_disabled_by_property=true npu_standard_route_completed_route_rollout_state=disabled_by_kill_switch npu_standard_route_effective_phase=8
            npu_standard_route_output_delivery_allowed=false npu_standard_route_ui_append_executed=false npu_standard_route_tts_started=false npu_standard_route_db_save_executed=false npu_standard_route_markdown_executed=false npu_standard_route_streaming_executed=false
        """.trimIndent()

        val copyText = buildNpuDiagnosticKeysCopyText(
            stats = InferenceStats(localSourceSummary = "source_summary=$trace"),
        )

        assertTrue(copyText.contains("status=blocked"))
        assertTrue(copyText.contains("reason=kill_switch_disabled"))
        assertTrue(copyText.contains("selected_backend=NPU_S5"))
        assertTrue(copyText.contains("effective_backend=NPU"))
        assertTrue(copyText.contains("npu_standard_route_completed_route_selected=false"))
        assertTrue(copyText.contains("npu_standard_route_completed_route_block_reason=kill_switch_disabled"))
        assertTrue(copyText.contains("npu_standard_route_completed_route_disabled_by_property=true"))
        assertTrue(copyText.contains("npu_standard_route_completed_route_rollout_state=disabled_by_kill_switch"))
        assertTrue(copyText.contains("npu_standard_route_output_delivery_allowed=false"))
        assertTrue(copyText.contains("npu_standard_route_ui_append_executed=false"))
        assertTrue(copyText.contains("npu_standard_route_db_save_executed=false"))
        assertTrue(copyText.contains("npu_standard_route_markdown_executed=false"))
        assertTrue(copyText.contains("npu_standard_route_streaming_executed=false"))
    }

    @Test
    fun `S1 compact full dump and diagnostic copy include phase2 conversation-created diagnostics`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val phase2Reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "2"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = phase2Reader,
        )
        val fullDump = buildNpuStandardRouteS1FullDumpDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = phase2Reader,
        )
        val trace = buildNpuStandardRouteS1DevTraceText(
            input = "こんにちは",
            result = result,
            npuStandardRouteDevGatePropertyReader = phase2Reader,
        )
        val copyText = buildNpuDiagnosticKeysCopyText(
            stats = InferenceStats(localSourceSummary = trace),
        )

        listOf(compact, fullDump, trace, copyText).forEach { text ->
            assertTrue(text.contains("npu_standard_route_dev_gate_enabled=true"))
            assertTrue(text.contains("npu_standard_route_phase=2"))
            assertTrue(text.contains("npu_standard_route_phase_name=2_conversation_created_diagnostic"))
            assertTrue(text.contains("npu_standard_route_connected=true"))
            assertTrue(text.contains("conversation_created=true"))
            assertTrue(text.contains("generate_response=false"))
            assertTrue(text.contains("npu_standard_route_ui_append_allowed=false"))
            assertTrue(text.contains("npu_standard_route_tts_allowed=false"))
            assertTrue(text.contains("npu_standard_route_db_save_allowed=false"))
            assertTrue(text.contains("npu_standard_route_markdown_allowed=false"))
            assertTrue(text.contains("npu_standard_route_streaming_allowed=false"))
        }
    }

    @Test
    fun `S1 compact full dump and diagnostic copy include phase3 generate diagnostics without delivery`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val phase3Reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "3"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = phase3Reader,
        )
        val fullDump = buildNpuStandardRouteS1FullDumpDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = phase3Reader,
        )
        val trace = buildNpuStandardRouteS1DevTraceText(
            input = "こんにちは",
            result = result,
            npuStandardRouteDevGatePropertyReader = phase3Reader,
        )
        val copyText = buildNpuDiagnosticKeysCopyText(
            stats = InferenceStats(localSourceSummary = trace),
        )

        listOf(compact, fullDump, trace, copyText).forEach { text ->
            assertTrue(text.contains("npu_standard_route_phase=3"))
            assertTrue(text.contains("npu_standard_route_phase_name=3_generate_response_diagnostic"))
            assertTrue(text.contains("conversation_created=true"))
            assertTrue(text.contains("generate_response=true"))
            assertTrue(text.contains("npu_standard_route_generate_diagnostic_only=true"))
            assertTrue(text.contains("npu_standard_route_quality_gate_passed=true"))
            assertTrue(text.contains("npu_standard_route_output_suppressed=false"))
            assertTrue(text.contains("npu_standard_route_output_delivery_allowed=false"))
            assertTrue(text.contains("npu_standard_route_candidate_text_present=true"))
            assertTrue(text.contains("npu_standard_route_ui_append_allowed=false"))
            assertTrue(text.contains("npu_standard_route_tts_allowed=false"))
            assertTrue(text.contains("npu_standard_route_db_save_allowed=false"))
            assertTrue(text.contains("npu_standard_route_markdown_allowed=false"))
            assertTrue(text.contains("npu_standard_route_streaming_allowed=false"))
            assertTrue(text.contains("npu_standard_route_rollback_required=false"))
        }
    }

    @Test
    fun `S1 compact full dump and diagnostic copy include phase4 UI append gate for pass`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val phase4Reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "4"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = phase4Reader,
        )
        val fullDump = buildNpuStandardRouteS1FullDumpDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = phase4Reader,
        )
        val trace = buildNpuStandardRouteS1DevTraceText(
            input = "こんにちは",
            result = result,
            npuStandardRouteDevGatePropertyReader = phase4Reader,
        )
        val copyText = buildNpuDiagnosticKeysCopyText(
            stats = InferenceStats(localSourceSummary = trace),
        )

        listOf(compact, fullDump, trace, copyText).forEach { text ->
            assertTrue(text.contains("npu_standard_route_phase=4"))
            assertTrue(text.contains("npu_standard_route_phase_name=4_ui_append_gate"))
            assertTrue(text.contains("conversation_created=true"))
            assertTrue(text.contains("generate_response=true"))
            assertTrue(text.contains("npu_standard_route_generate_diagnostic_only=false"))
            assertTrue(text.contains("npu_standard_route_quality_gate_passed=true"))
            assertTrue(text.contains("npu_standard_route_output_suppressed=false"))
            assertTrue(text.contains("npu_standard_route_output_delivery_allowed=true"))
            assertTrue(text.contains("npu_standard_route_candidate_text_present=true"))
            assertTrue(text.contains("npu_standard_route_ui_append_allowed=true"))
            assertTrue(text.contains("npu_standard_route_ui_append_source=actual_display_text"))
            assertTrue(text.contains("npu_standard_route_ui_append_block_reason=none"))
            assertTrue(text.contains("npu_standard_route_tts_allowed=false"))
            assertTrue(text.contains("npu_standard_route_db_save_allowed=false"))
            assertTrue(text.contains("npu_standard_route_markdown_allowed=false"))
            assertTrue(text.contains("npu_standard_route_streaming_allowed=false"))
            assertTrue(text.contains("npu_standard_route_rollback_required=false"))
        }
    }

    @Test
    fun `S1 phase3 dangerous quality fail output is suppressed before delivery`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = ">こんにちは</start_of_turn>\n<start_of_turn>user>こんにちは",
                sanitizedOutput = "こんにちは</start_of_turn>\nこんにちは",
                qualityClassification = "template_artifact",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val phase3Reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "3"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = phase3Reader,
        )

        assertTrue(compact.contains("output_quality_candidate_status=quality_candidate_fail"))
        assertTrue(compact.contains("output_quality_candidate_reason=special_token_leak"))
        assertTrue(compact.contains("npu_standard_route_phase=3"))
        assertTrue(compact.contains("conversation_created=true"))
        assertTrue(compact.contains("generate_response=true"))
        assertTrue(compact.contains("npu_standard_route_quality_gate_passed=false"))
        assertTrue(compact.contains("npu_standard_route_output_suppressed=true"))
        assertTrue(compact.contains("npu_standard_route_suppression_reason=special_token_leak"))
        assertTrue(compact.contains("npu_standard_route_output_delivery_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_ui_append_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_tts_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_db_save_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_markdown_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_streaming_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_rollback_required=true"))
        assertTrue(
            compact.contains(
                "npu_standard_route_rollback_reason=" +
                    "quality_candidate_fail_output_suppressed_before_ui_tts_db",
            ),
        )
    }

    @Test
    fun `S1 phase4 dangerous quality fail output is suppressed before UI append`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "_turn>\n<end_of_turn>\n<start_of_turn>model",
                sanitizedOutput = "_turn>",
                qualityClassification = "template_artifact",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val phase4Reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "4"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "template cleanup が出やすい短文",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = phase4Reader,
        )

        assertTrue(compact.contains("output_quality_candidate_status=quality_candidate_fail"))
        assertTrue(compact.contains("output_quality_candidate_reason=raw_unexpected_start_turn"))
        assertTrue(compact.contains("npu_standard_route_phase=4"))
        assertTrue(compact.contains("npu_standard_route_phase_name=4_ui_append_gate"))
        assertTrue(compact.contains("conversation_created=true"))
        assertTrue(compact.contains("generate_response=true"))
        assertTrue(compact.contains("npu_standard_route_quality_gate_passed=false"))
        assertTrue(compact.contains("npu_standard_route_output_suppressed=true"))
        assertTrue(compact.contains("npu_standard_route_suppression_reason=raw_unexpected_start_turn"))
        assertTrue(compact.contains("npu_standard_route_output_delivery_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_ui_append_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_ui_append_source=blocked_quality_candidate_fail"))
        assertTrue(compact.contains("npu_standard_route_ui_append_block_reason=quality_candidate_fail"))
        assertTrue(compact.contains("npu_standard_route_tts_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_db_save_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_markdown_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_streaming_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_rollback_required=true"))
        assertTrue(
            compact.contains(
                "npu_standard_route_rollback_reason=" +
                    "quality_candidate_fail_output_suppressed_before_ui_tts_db",
            ),
        )
    }

    @Test
    fun `S1 compact full dump and diagnostic copy include phase5 TTS gate for pass`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val phase5Reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "5"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = phase5Reader,
        )
        val fullDump = buildNpuStandardRouteS1FullDumpDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = phase5Reader,
        )
        val trace = buildNpuStandardRouteS1DevTraceText(
            input = "こんにちは",
            result = result,
            npuStandardRouteDevGatePropertyReader = phase5Reader,
        )
        val copyText = buildNpuDiagnosticKeysCopyText(
            stats = InferenceStats(localSourceSummary = trace),
        )

        listOf(compact, fullDump, trace, copyText).forEach { text ->
            assertTrue(text.contains("npu_standard_route_phase=5"))
            assertTrue(text.contains("npu_standard_route_phase_name=5_tts_gate"))
            assertTrue(text.contains("conversation_created=true"))
            assertTrue(text.contains("generate_response=true"))
            assertTrue(text.contains("npu_standard_route_generate_diagnostic_only=false"))
            assertTrue(text.contains("npu_standard_route_quality_gate_passed=true"))
            assertTrue(text.contains("npu_standard_route_output_suppressed=false"))
            assertTrue(text.contains("npu_standard_route_output_delivery_allowed=true"))
            assertTrue(text.contains("npu_standard_route_ui_append_allowed=true"))
            assertTrue(text.contains("npu_standard_route_tts_allowed=true"))
            assertTrue(text.contains("npu_standard_route_tts_source=tts_text"))
            assertTrue(text.contains("npu_standard_route_tts_text_length=${result.ttsText.length}"))
            assertTrue(text.contains("npu_standard_route_tts_block_reason=none"))
            assertTrue(text.contains("npu_standard_route_db_save_allowed=false"))
            assertTrue(text.contains("npu_standard_route_markdown_allowed=false"))
            assertTrue(text.contains("npu_standard_route_streaming_allowed=false"))
            assertTrue(text.contains("npu_standard_route_rollback_required=false"))
        }
    }

    @Test
    fun `S1 phase5 dangerous quality fail output is suppressed before TTS`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "_turn>\n<end_of_turn>\n<start_of_turn>model",
                sanitizedOutput = "_turn>",
                qualityClassification = "template_artifact",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val phase5Reader: (String) -> String? = { key ->
            when (key) {
                NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "5"
                else -> null
            }
        }

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "template cleanup が出やすい短文",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = phase5Reader,
        )

        assertTrue(compact.contains("output_quality_candidate_status=quality_candidate_fail"))
        assertTrue(compact.contains("output_quality_candidate_reason=raw_unexpected_start_turn"))
        assertTrue(compact.contains("npu_standard_route_phase=5"))
        assertTrue(compact.contains("npu_standard_route_phase_name=5_tts_gate"))
        assertTrue(compact.contains("npu_standard_route_quality_gate_passed=false"))
        assertTrue(compact.contains("npu_standard_route_output_suppressed=true"))
        assertTrue(compact.contains("npu_standard_route_suppression_reason=raw_unexpected_start_turn"))
        assertTrue(compact.contains("npu_standard_route_output_delivery_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_ui_append_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_tts_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_tts_source=blocked_quality_candidate_fail"))
        assertTrue(compact.contains("npu_standard_route_tts_text_length=0"))
        assertTrue(compact.contains("npu_standard_route_tts_block_reason=quality_candidate_fail"))
        assertTrue(compact.contains("npu_standard_route_db_save_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_markdown_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_streaming_allowed=false"))
        assertTrue(compact.contains("npu_standard_route_rollback_required=true"))
        assertTrue(
            compact.contains(
                "npu_standard_route_rollback_reason=" +
                    "quality_candidate_fail_output_suppressed_before_ui_tts_db",
            ),
        )
    }

    @Test
    fun `S1 compact includes phase1 suppression diagnostics for quality candidate fail`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = ">こんにちは</start_of_turn>\n<start_of_turn>user>こんにちは",
                sanitizedOutput = "こんにちは</start_of_turn>\nこんにちは",
                qualityClassification = "template_artifact",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )

        val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            npuStandardRouteDevGatePropertyReader = { "true" },
        )

        assertTrue(compact.contains("output_quality_candidate_status=quality_candidate_fail"))
        assertTrue(compact.contains("npu_standard_route_quality_gate_passed=false"))
        assertTrue(compact.contains("npu_standard_route_output_suppressed=true"))
        assertTrue(compact.contains("npu_standard_route_suppression_reason=quality_candidate_fail"))
        assertTrue(compact.contains("npu_standard_route_rollback_required=true"))
        assertTrue(
            compact.contains(
                "npu_standard_route_rollback_reason=quality_gate_output_must_not_reach_ui_tts_db",
            ),
        )
        assertTrue(compact.contains("conversation_created=false"))
        assertTrue(compact.contains("generate_response=false"))
    }

    @Test
    fun `S1 compact does not include phase1 diagnostics for CPU or GPU selections`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )

        listOf(PreferredBackendDryRunSetting.CPU, PreferredBackendDryRunSetting.GPU).forEach { backend ->
            val compact = buildNpuStandardRouteS1CompactDiagnosticCopyText(
                input = "こんにちは",
                result = result,
                preferredBackendSetting = backend,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
                npuStandardRouteDevGatePropertyReader = { "true" },
            )

            assertFalse(compact.contains("npu_standard_route_dev_gate_enabled="))
            assertFalse(compact.contains("npu_standard_route_phase="))
            assertFalse(compact.contains("npu_standard_route_connected="))
            assertFalse(compact.contains("npu_standard_route_ui_append_allowed="))
        }
    }

    @Test
    fun `Copy Compact uses compact formatter explicitly`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "raw",
                sanitizedOutput = "こんばんは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )

        val copy = buildNpuStandardRouteS1CompactExplicitCopyText(
            input = "こんばんは",
            result = result,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
        )

        assertEquals(
            buildNpuStandardRouteS1DiagnosticCopyText(
                input = "こんばんは",
                result = result,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            ),
            copy,
        )
        assertTrue(copy.contains("[DEV診断: NPU S1 compact]"))
        assertTrue(copy.contains("selected_backend=NPU_S1"))
        assertTrue(copy.contains("requested_backend=NPU"))
        assertTrue(copy.contains("effective_backend=NPU"))
        assertTrue(copy.contains("backend_evidence=QNN_HTP_V79_FastRPC_native_diag"))
        assertTrue(copy.contains("route_family=npu_s1"))
        assertTrue(copy.contains("blocked_reason=none"))
        assertTrue(copy.contains("guard_recommendation=unavailable"))
        assertFalse(copy.contains("[DEV診断: NPU S1 repeated run summary]"))
        assertFalse(copy.contains("[DEV診断: NPU S1 full dump]"))
    }

    @Test
    fun `Copy Full Dump uses full dump formatter explicitly and keeps full dump summary sections`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "raw",
                sanitizedOutput = "こんばんは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 256,
                effectiveMaxOutputTokens = 256,
            ),
        )

        val copy = buildNpuStandardRouteS1FullDumpExplicitCopyText(
            input = "こんばんは",
            result = result,
            maxOutputTokens = 256,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
        )

        assertEquals(
            buildNpuStandardRouteS1FullDumpDiagnosticCopyText(
                input = "こんばんは",
                result = result,
                maxOutputTokens = 256,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            ),
            copy,
        )
        assertTrue(copy.contains("[DEV診断: NPU S1 full dump]"))
        assertTrue(copy.contains("[DEV診断: NPU S1 short output telemetry]"))
        assertTrue(copy.contains("status=success"))
        assertTrue(copy.contains("reason=success"))
        assertTrue(copy.contains("selected_backend=NPU_S1"))
        assertTrue(copy.contains("requested_backend=NPU"))
        assertTrue(copy.contains("effective_backend=NPU"))
        assertTrue(copy.contains("backend_evidence=QNN_HTP_V79_FastRPC_native_diag"))
        assertTrue(copy.contains("route_family=npu_s1"))
        assertTrue(copy.contains("blocked_reason=none"))
        assertTrue(copy.contains("guard_recommendation=unavailable"))
        assertFalse(copy.contains("[DEV診断: NPU S1 repeated run summary]"))
    }

    @Test
    fun `compact backend diagnostics do not report CPU or GPU as effective NPU`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "raw",
                sanitizedOutput = "こんばんは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )

        val cpuCopy = buildNpuStandardRouteS1CompactExplicitCopyText(
            input = "こんばんは",
            result = result,
            preferredBackendSetting = PreferredBackendDryRunSetting.CPU,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
        )
        val gpuCopy = buildNpuStandardRouteS1CompactExplicitCopyText(
            input = "こんばんは",
            result = result,
            preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
        )

        assertTrue(cpuCopy.contains("selected_backend=CPU"))
        assertTrue(cpuCopy.contains("requested_backend=CPU"))
        assertTrue(cpuCopy.contains("effective_backend=CPU"))
        assertFalse(cpuCopy.contains("selected_backend=CPU\nrequested_backend=NPU\neffective_backend=NPU"))
        assertTrue(gpuCopy.contains("selected_backend=GPU"))
        assertTrue(gpuCopy.contains("requested_backend=GPU"))
        assertTrue(gpuCopy.contains("effective_backend=GPU"))
        assertFalse(gpuCopy.contains("selected_backend=GPU\nrequested_backend=NPU\neffective_backend=NPU"))
    }

    @Test
    fun `S1 dev trace records original failure when safe greeting fallback is applied`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "failure",
                result = "failure",
                success = false,
                reason = NpuStandardRouteS1Contract.REASON_EMPTY_AFTER_SANITIZE,
                rawOutput = "૩です|",
                sanitizedOutput = "",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_MIXED_LANGUAGE,
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )

        val trace = buildNpuStandardRouteS1DevTraceText(
            input = "こんばんは",
            result = result,
            transientFallback = NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING,
        )
        val copyText = buildNpuStandardRouteS1DiagnosticCopyText(
            input = "こんばんは",
            result = result,
            transientFallback = NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING,
        )

        assertTrue(trace.contains("original_status=failure"))
        assertTrue(trace.contains("original_reason=empty_after_sanitize"))
        assertTrue(trace.contains("original_quality_classification=mixed_language"))
        assertTrue(trace.contains("fallback=safe_greeting_fallback"))
        assertTrue(copyText.contains("[DEV診断: NPU S1 compact]"))
        assertTrue(copyText.contains("[DEV診断: NPU S1 failure details]"))
        assertTrue(copyText.contains("fallback=safe_greeting_fallback"))
    }

    @Test
    fun `S1 diagnostic copy includes LiteRtLmJniException failure keys`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "failure",
                result = "failure",
                success = false,
                reason = "adapter_failure:LiteRtLmJniException",
                rawOutput = "",
                sanitizedOutput = "",
                qualityClassification = "unknown",
                runDecodeReached = false,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
                nativeDiagnostics = NpuS1NativeStageDiagnostics(
                    nativeStage = NPU_S1_NATIVE_STAGE_NATIVE_CALL,
                    nativeStageHistory = "provider_start>adapter_start>before_native_call>native_call>adapter_failure",
                    nativeCallReached = "true",
                    nativeCallReturned = "false",
                    nativeDecodeStarted = "false",
                    nativeDecodeFinished = "false",
                    nativeErrorClass = "LiteRtLmJniException",
                    nativeErrorMessage = "engine-create-failed:INTERNAL",
                    nativeErrorStage = NPU_S1_NATIVE_STAGE_NATIVE_CALL,
                    nativeErrorSource = "throwable",
                ),
                inputPrompt = "あなたは誰ですか",
            ),
        )

        val copyText = buildNpuStandardRouteS1DiagnosticCopyText(
            input = "あなたは誰ですか",
            result = result,
            maxOutputTokens = 32,
            appHistoryText = listOf(
                "[DEV診断: NPU S1 normal chat app history]",
                "last_npu_s1_request_started_at_elapsed_realtime_ms=123",
                "last_npu_s1_request_finished_at_elapsed_realtime_ms=456",
                "last_npu_s1_prompt=あなたは誰ですか",
                "last_npu_s1_final_prompt_tail=<start_of_turn>model",
                "last_npu_s1_prompt_profile=gemma_it_user_model",
                "last_npu_s1_model_path=hidden_from_failure_details",
                "last_npu_s1_status=failure",
                "last_npu_s1_reason=adapter_failure:LiteRtLmJniException",
                "last_npu_s1_exception_class=LiteRtLmJniException",
                "last_npu_s1_exception_message=engine-create-failed:INTERNAL",
                "last_npu_s1_native_stage=native_call",
                "last_npu_s1_native_stage_history=provider_start>native_call",
                "last_successful_npu_s1_prompt=こんにちは",
                "last_failed_npu_s1_prompt=あなたは誰ですか",
                "successful_npu_s1_request_count=3",
                "engine_create_failure_count=1",
                "last_engine_create_failure_at_elapsed_realtime_ms=789",
                "failure_after_successful_npu_s1_request_count=3",
                "failure_after_last_success_elapsed_ms=333",
                "last_failure_was_engine_create_failed=true",
                "native_crash_risk_hint=engine_create_failed_near_litert_compiled_model_dispatch_delegate_check_tombstone_dropbox",
            ).joinToString("\n"),
        )

        assertTrue(copyText.contains("[DEV診断: NPU S1 compact]"))
        assertTrue(copyText.contains("[DEV診断: NPU S1 failure details]"))
        assertTrue(copyText.contains("input_prompt=あなたは誰ですか"))
        assertTrue(copyText.contains("final_prompt_text=<start_of_turn>user\\nあなたは誰ですか<end_of_turn>\\n<start_of_turn>model"))
        assertTrue(copyText.contains("selected_prompt_profile=gemma_it_user_model"))
        assertTrue(copyText.contains("failure_exception_class=LiteRtLmJniException"))
        assertTrue(copyText.contains("failure_exception_message=engine-create-failed:INTERNAL"))
        assertTrue(copyText.contains("npu_s1_failure_kind=engine_create_failed"))
        assertTrue(copyText.contains("npu_s1_failure_layer=litert_npu_compiled_model_executor"))
        assertTrue(copyText.contains("npu_s1_failure_recovery_hint=recreate_app_or_wait_before_retry"))
        assertTrue(copyText.contains("failure_stage=native_call"))
        assertTrue(copyText.contains("native_stage=native_call"))
        assertTrue(copyText.contains("native_call_reached=true"))
        assertTrue(copyText.contains("native_call_returned=false"))
        assertTrue(copyText.contains("native_decode_started=false"))
        assertTrue(copyText.contains("native_decode_finished=false"))
        assertTrue(copyText.contains("engine_create_failure_count=1"))
        assertTrue(copyText.contains("failure_after_successful_npu_s1_request_count=3"))
        assertTrue(
            copyText.contains(
                "native_crash_risk_hint=" +
                    "engine_create_failed_near_litert_compiled_model_dispatch_delegate_check_tombstone_dropbox",
            ),
        )
        assertTrue(copyText.contains("last_npu_s1_request_started_at_elapsed_realtime_ms=123"))
        assertTrue(copyText.contains("last_npu_s1_prompt=あなたは誰ですか"))
        assertTrue(copyText.contains("last_npu_s1_native_stage_history=provider_start>native_call"))
        assertTrue(copyText.contains("successful_npu_s1_request_count=3"))
        assertTrue(copyText.contains("last_engine_create_failure_at_elapsed_realtime_ms=789"))
        assertFalse(copyText.contains("last_npu_s1_model_path=hidden_from_failure_details"))
    }

    @Test
    fun `S1 quality failure diagnostics do not classify as engine create failed`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                reason = "success",
                rawOutput = "<start_of_turn>user\n1+1は？",
                sanitizedOutput = "<start_of_turn>user\n1+1は？",
                qualityClassification = "role_contamination",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                inputPrompt = "1+1は？",
            ),
        )

        val copyText = buildNpuStandardRouteS1DiagnosticCopyText(
            input = "1+1は？",
            result = result,
            appHistoryText = listOf(
                "[DEV診断: NPU S1 normal chat app history]",
                "engine_create_failure_count=0",
                "failure_after_successful_npu_s1_request_count=4",
                "native_crash_risk_hint=unavailable",
            ).joinToString("\n"),
        )

        assertTrue(copyText.contains("output_quality_candidate_status=quality_candidate_fail"))
        assertTrue(copyText.contains("npu_s1_failure_kind=unavailable"))
        assertTrue(copyText.contains("engine_create_failure_count=0"))
        assertTrue(copyText.contains("failure_after_successful_npu_s1_request_count=4"))
        assertFalse(copyText.contains("npu_s1_failure_kind=engine_create_failed"))
    }
}
