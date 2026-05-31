package io.github.ninbyo02.lami.npu

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuOneTurnConversationEntryTest {
    @Test
    fun `raw dialog tail variants are fixed for one turn`() {
        val variantA = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
            contextText = "これは中立文脈です。",
            userPrompt = "こんにちは。",
            promptTailVariant = DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_A,
        )
        val variantB = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
            contextText = "これは中立文脈です。",
            userPrompt = "こんにちは。",
            promptTailVariant = DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
        )
        val variantC = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
            contextText = "これは中立文脈です。",
            userPrompt = "明日の天気は",
            promptTailVariant = DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_C,
        )

        assertTrue(variantA.contains("必ず日本語だけで短く返答してください。"))
        assertTrue(variantA.contains("ユーザー: こんにちは。"))
        assertTrue(variantA.endsWith("アシスタント:"))
        assertTrue(variantB.contains("必ず日本語だけで短く返答してください。"))
        assertTrue(variantB.contains("ユーザー: こんにちは。"))
        assertTrue(variantB.endsWith("アシスタント: はい、"))
        assertTrue(variantB.contains("\n\n必ず日本語だけで短く返答してください。\nユーザー:"))
        assertTrue(variantC.contains("あなたは日本語だけで短く答えるアシスタントです。"))
        assertTrue(variantC.contains("ユーザーの文を繰り返さず、答えだけを1文で書いてください。"))
        assertTrue(variantC.contains("ユーザー: 明日の天気は"))
        assertTrue(variantC.endsWith("アシスタント:"))
        assertFalse(variantC.endsWith("アシスタント: はい、"))
    }

    @Test
    fun `default contract keeps standard route and side effects disconnected`() {
        val safety = DevOnlyNpuOneTurnConversationContract.safety()

        assertFalse(safety.standardRouteConnected)
        assertFalse(safety.backendNpuPersisted)
        assertFalse(safety.db)
        assertFalse(safety.tts)
        assertFalse(safety.markdown)
        assertFalse(safety.streaming)
        assertFalse(safety.selectedPathNpuSaved)
        assertEquals("raw", safety.appTemplateMode)
        assertEquals("raw_dialog_tail", safety.template)
        assertEquals("base64", safety.promptTransport)
        assertEquals(
            "必ず日本語だけで短く返答してください。",
            DevOnlyNpuOneTurnConversationContract.JAPANESE_ONLY_TAIL_INSTRUCTION,
        )
        assertEquals("はい、", DevOnlyNpuOneTurnConversationContract.JAPANESE_ASSISTANT_PREFIX_VARIANT_B)
        assertEquals(
            "raw_dialog_tail_variant_b",
            DevOnlyNpuOneTurnConversationContract.DEFAULT_PROMPT_TAIL_VARIANT,
        )
        assertTrue(DevOnlyNpuOneTurnConversationContract.safetyLines().contains("route_type=dev_only_one_turn_conversation"))
        assertTrue(
            DevOnlyNpuOneTurnConversationContract.safetyLines().contains(
                "prompt_tail_variant=raw_dialog_tail_variant_b",
            ),
        )
    }

    @Test
    fun `initial display is idle until manual trigger`() {
        val text = DevOnlyNpuOneTurnConversationContract.INITIAL_DISPLAY_TEXT

        assertTrue(text.contains("status=idle"))
        assertTrue(text.contains("adapter_execution=manual_trigger_only"))
        assertFalse(text.contains("status=starting"))
    }

    @Test
    fun `one turn request uses fixed max output tokens contract`() {
        val request = DevOnlyNpuOneTurnConversationRequest(userPrompt = "こんにちは。")

        assertEquals(16, DevOnlyNpuOneTurnConversationContract.MAX_OUTPUT_TOKENS)
        assertEquals("こんにちは。", request.userPrompt)
    }

    @Test
    fun `activity auto run is opt in and builds fixed max output request`() {
        val request = DevOnlyNpuOneTurnConversationContract.activityRequest(
            userPrompt = "",
            contextText = "",
            unsafeDevBypassPromptLengthGate = true,
        )

        assertEquals("auto_run", DevOnlyNpuOneTurnConversationContract.EXTRA_AUTO_RUN)
        assertEquals("こんにちは", request.userPrompt)
        assertEquals("", request.contextText)
        assertTrue(request.unsafeDevBypassPromptLengthGate)
        assertEquals(16, DevOnlyNpuOneTurnConversationContract.MAX_OUTPUT_TOKENS)
        assertEquals(16, request.maxOutputTokens)
        assertEquals(
            DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
            request.promptTailVariant,
        )
        assertTrue(DevOnlyNpuOneTurnConversationContract.INITIAL_DISPLAY_TEXT.contains("status=idle"))
        assertTrue(
            DevOnlyNpuOneTurnConversationContract.INITIAL_DISPLAY_TEXT.contains(
                "adapter_execution=manual_trigger_only",
            ),
        )
    }

    @Test
    fun `activity max output tokens compare option allows only 16 or 32`() {
        val request32 = DevOnlyNpuOneTurnConversationContract.activityRequest(
            userPrompt = "こんにちは",
            contextText = "",
            unsafeDevBypassPromptLengthGate = true,
            requestedMaxOutputTokens = 32,
        )
        val requestInvalid = DevOnlyNpuOneTurnConversationContract.activityRequest(
            userPrompt = "こんにちは",
            contextText = "",
            unsafeDevBypassPromptLengthGate = true,
            requestedMaxOutputTokens = 128,
        )

        assertEquals("max_output_tokens", DevOnlyNpuOneTurnConversationContract.EXTRA_MAX_OUTPUT_TOKENS)
        assertEquals(16, DevOnlyNpuOneTurnConversationContract.DEFAULT_MAX_OUTPUT_TOKENS)
        assertEquals(32, DevOnlyNpuOneTurnConversationContract.COMPARE_MAX_OUTPUT_TOKENS)
        assertEquals(16, DevOnlyNpuOneTurnConversationContract.sanitizeMaxOutputTokens(16))
        assertEquals(32, DevOnlyNpuOneTurnConversationContract.sanitizeMaxOutputTokens(32))
        assertEquals(16, DevOnlyNpuOneTurnConversationContract.sanitizeMaxOutputTokens(0))
        assertEquals(16, DevOnlyNpuOneTurnConversationContract.sanitizeMaxOutputTokens(128))
        assertEquals(32, request32.maxOutputTokens)
        assertEquals(16, requestInvalid.maxOutputTokens)
    }

    @Test
    fun `activity prompt tail variant option allows only raw dialog tail variants`() {
        val requestA = DevOnlyNpuOneTurnConversationContract.activityRequest(
            userPrompt = "こんにちは",
            contextText = "",
            unsafeDevBypassPromptLengthGate = true,
            requestedPromptTailVariant = DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_A,
        )
        val requestB = DevOnlyNpuOneTurnConversationContract.activityRequest(
            userPrompt = "こんにちは",
            contextText = "",
            unsafeDevBypassPromptLengthGate = true,
            requestedPromptTailVariant = DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
        )
        val requestC = DevOnlyNpuOneTurnConversationContract.activityRequest(
            userPrompt = "こんにちは",
            contextText = "",
            unsafeDevBypassPromptLengthGate = true,
            requestedPromptTailVariant = DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_C,
        )
        val requestInvalid = DevOnlyNpuOneTurnConversationContract.activityRequest(
            userPrompt = "こんにちは",
            contextText = "",
            unsafeDevBypassPromptLengthGate = true,
            requestedPromptTailVariant = "raw_dialog_tail_variant_d",
        )

        assertEquals("prompt_tail_variant", DevOnlyNpuOneTurnConversationContract.EXTRA_PROMPT_TAIL_VARIANT)
        assertEquals(
            DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_A,
            DevOnlyNpuOneTurnConversationContract.sanitizePromptTailVariant(
                DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_A,
            ),
        )
        assertEquals(
            DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
            DevOnlyNpuOneTurnConversationContract.sanitizePromptTailVariant(
                DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
            ),
        )
        assertEquals(
            DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_C,
            DevOnlyNpuOneTurnConversationContract.sanitizePromptTailVariant(
                DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_C,
            ),
        )
        assertEquals(
            DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
            DevOnlyNpuOneTurnConversationContract.sanitizePromptTailVariant(null),
        )
        assertEquals(
            DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
            DevOnlyNpuOneTurnConversationContract.sanitizePromptTailVariant("raw_dialog_tail_variant_d"),
        )
        assertEquals(DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_A, requestA.promptTailVariant)
        assertEquals(DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B, requestB.promptTailVariant)
        assertEquals(DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_C, requestC.promptTailVariant)
        assertEquals(DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B, requestInvalid.promptTailVariant)
    }

    @Test
    fun `receiver contract is debug-only named and writes dedicated result file`() {
        assertEquals(
            "io.github.ninbyo02.lami.action.DEV_ONLY_NPU_ONE_TURN_CONVERSATION",
            DevOnlyNpuOneTurnConversationContract.RECEIVER_ACTION,
        )
        assertEquals(
            "dev_only_npu_one_turn_conversation_result.txt",
            DevOnlyNpuOneTurnConversationContract.RECEIVER_RESULT_FILE_NAME,
        )
        assertEquals("user_prompt", DevOnlyNpuOneTurnConversationContract.EXTRA_USER_PROMPT)
        assertEquals("prompt_tail_variant", DevOnlyNpuOneTurnConversationContract.EXTRA_PROMPT_TAIL_VARIANT)
        assertEquals("context", DevOnlyNpuOneTurnConversationContract.EXTRA_CONTEXT)
        assertEquals(
            "unsafe_dev_bypass_prompt_length_gate",
            DevOnlyNpuOneTurnConversationContract.EXTRA_UNSAFE_DEV_BYPASS_PROMPT_LENGTH_GATE,
        )
        assertTrue(DevOnlyNpuOneTurnConversationContract.RECEIVER_ACTION.contains("DEV_ONLY"))
        assertEquals("こんにちは", DevOnlyNpuOneTurnConversationContract.DEFAULT_USER_PROMPT)
        assertEquals(244, DevOnlyNpuOneTurnConversationContract.RECEIVER_RESULT_CODE_RECEIVED)
        assertEquals("auto_run_matrix", DevOnlyNpuOneTurnConversationContract.EXTRA_AUTO_RUN_MATRIX)
        assertEquals(
            "auto_run_prompt_template_matrix",
            DevOnlyNpuOneTurnConversationContract.EXTRA_AUTO_RUN_PROMPT_TEMPLATE_MATRIX,
        )
        assertEquals(
            "dev_only_npu_one_turn_conversation_matrix_result.txt",
            DevOnlyNpuOneTurnConversationContract.MATRIX_RESULT_FILE_NAME,
        )
    }

    @Test
    fun `dev-only conversation does not use shared standard route once guard`() {
        assertFalse(
            Qairt244DevOnlyNpuRouteAdapter.usesSharedOnceGuard(
                Qairt244DevOnlyNpuRouteAdapter.PROMPT_SOURCE_DEV_ONLY_CONVERSATION,
            ),
        )
        assertTrue(
            Qairt244DevOnlyNpuRouteAdapter.usesSharedOnceGuard(
                Qairt244DevOnlyNpuRouteAdapter.PROMPT_SOURCE_CHAT_SCREEN,
            ),
        )
    }

    @Test
    fun `display includes only dev-only side effect flags`() {
        val display = DevOnlyNpuOneTurnConversationContract.display(
            result = DevOnlyNpuRouteResult(
                success = true,
                output = "こんにちは。",
                reasonCode = "success",
                elapsedMs = 10,
                decodeElapsedMs = 5,
                prompt = "ユーザー: こんにちは。\nアシスタント:",
                maxOutputTokens = DevOnlyNpuOneTurnConversationContract.MAX_OUTPUT_TOKENS,
                backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                artifactPath = null,
                freshCrash = false,
                timeout = false,
            ),
            values = mapOf(
                "sanitized_output" to "こんにちは。",
                "sanitized_output_length" to "6",
                "raw_native_output" to " こんにちは。",
                "raw_native_output_length" to "7",
                "output_first_200_chars" to "こんにちは。",
                "max_output_tokens" to "16",
                "native_max_output_tokens_limit" to "512",
                "quality_classification" to "natural_japanese",
                "output_unicode_summary" to "control_chars=none;replacement_char_count=0",
                "sanitizer_applied" to "false",
                "removed_template_token_count" to "0",
                "removed_prompt_echo" to "false",
                "replacement_char_count" to "0",
                "output_contains_control_chars" to "false",
            ),
        )

        assertEquals(16, display.requestedMaxOutputTokens)
        assertEquals(16, display.effectiveMaxOutputTokens)
        assertEquals("512", display.nativeMaxOutputTokensLimit)
        assertTrue(display.text.contains("requested_max_output_tokens=16"))
        assertTrue(display.text.contains("effective_max_output_tokens=16"))
        assertTrue(display.text.contains("max_output_tokens=16"))
        assertTrue(display.text.contains("native_max_output_tokens_limit=512"))
        assertTrue(display.text.contains("run_decode_reached=true"))
        assertTrue(display.text.contains("npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag"))
        assertTrue(display.text.contains("fallback_used=false"))
        assertTrue(display.text.contains("timeout=false"))
        assertTrue(display.text.contains("fresh_crash=false"))
        assertTrue(display.text.contains("raw_output_first_200_chars=こんにちは。"))
        assertTrue(display.text.contains("raw_unicode_summary=control_chars=none;replacement_char_count=0"))
        assertTrue(display.text.contains("sanitizer_applied=false"))
        assertTrue(display.text.contains("removed_template_token_count=0"))
        assertTrue(display.text.contains("removed_prompt_echo=false"))
        assertTrue(display.text.contains("replacement_char_count=0"))
        assertTrue(display.text.contains("output_contains_control_chars=false"))
        assertTrue(display.text.contains("standard_route_connected=false"))
        assertTrue(display.text.contains("route_type=dev_only_one_turn_conversation"))
        assertTrue(display.text.contains("backend_npu_persisted=false"))
        assertTrue(display.text.contains("db=false"))
        assertTrue(display.text.contains("tts=false"))
        assertTrue(display.text.contains("markdown=false"))
        assertTrue(display.text.contains("streaming=false"))
        assertTrue(display.text.contains("app_template_mode=raw"))
        assertTrue(display.text.contains("template=raw_dialog_tail"))
        assertTrue(display.text.contains("prompt_tail_variant=raw_dialog_tail_variant_b"))
        assertTrue(display.text.contains("prompt_transport=base64"))
    }

    @Test
    fun `display and result contract preserve 32 token compare request`() {
        val display = DevOnlyNpuOneTurnConversationContract.display(
            result = DevOnlyNpuRouteResult(
                success = true,
                output = "こんにちは。短い応答です。",
                reasonCode = "success",
                elapsedMs = 10,
                decodeElapsedMs = 5,
                prompt = "ユーザー: こんにちは\nアシスタント: こんにちは",
                maxOutputTokens = DevOnlyNpuOneTurnConversationContract.COMPARE_MAX_OUTPUT_TOKENS,
                backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                artifactPath = null,
                freshCrash = false,
                timeout = false,
            ),
            values = mapOf(
                "sanitized_output" to "こんにちは。短い応答です。",
                "sanitized_output_length" to "12",
                "raw_native_output" to "こんにちは。短い応答です。",
                "raw_native_output_length" to "12",
                "max_output_tokens" to "32",
                "native_max_output_tokens_limit" to "512",
                "quality_classification" to "natural_japanese",
                "output_unicode_summary" to "control_chars=none;replacement_char_count=0",
                "sanitizer_applied" to "false",
                "removed_template_token_count" to "0",
                "removed_prompt_echo" to "false",
                "replacement_char_count" to "0",
                "output_contains_control_chars" to "false",
            ),
        )

        val resultText = DevOnlyNpuOneTurnConversationContract.receiverResultText(
            display = display,
            timestampMs = 1234L,
        )
        val progressText = DevOnlyNpuOneTurnConversationContract.receiverProgressText(
            status = "running",
            timestampMs = 1234L,
            maxOutputTokens = DevOnlyNpuOneTurnConversationContract.COMPARE_MAX_OUTPUT_TOKENS,
        )
        val failureText = DevOnlyNpuOneTurnConversationContract.receiverFailureText(
            reason = "activity_failure:IllegalStateException",
            message = "failed",
            timestampMs = 1234L,
            maxOutputTokens = DevOnlyNpuOneTurnConversationContract.COMPARE_MAX_OUTPUT_TOKENS,
        )

        assertEquals(32, display.requestedMaxOutputTokens)
        assertEquals(32, display.effectiveMaxOutputTokens)
        assertTrue(display.text.contains("requested_max_output_tokens=32"))
        assertTrue(display.text.contains("effective_max_output_tokens=32"))
        assertTrue(display.text.contains("max_output_tokens=32"))
        assertTrue(resultText.contains("requested_max_output_tokens=32"))
        assertTrue(resultText.contains("effective_max_output_tokens=32"))
        assertTrue(resultText.contains("max_output_tokens=32"))
        assertTrue(progressText.contains("requested_max_output_tokens=32"))
        assertTrue(progressText.contains("effective_max_output_tokens=32"))
        assertTrue(progressText.contains("max_output_tokens=32"))
        assertTrue(failureText.contains("requested_max_output_tokens=32"))
        assertTrue(failureText.contains("effective_max_output_tokens=32"))
        assertTrue(failureText.contains("max_output_tokens=32"))
    }

    @Test
    fun `receiver result file includes token evidence and disconnected side effects`() {
        val display = DevOnlyNpuOneTurnConversationContract.display(
            result = DevOnlyNpuRouteResult(
                success = true,
                output = "こんにちは。\n短い応答です。",
                reasonCode = "success",
                elapsedMs = 10,
                decodeElapsedMs = 5,
                prompt = "ユーザー: こんにちは\nアシスタント:",
                maxOutputTokens = DevOnlyNpuOneTurnConversationContract.MAX_OUTPUT_TOKENS,
                backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                artifactPath = null,
                freshCrash = false,
                timeout = false,
            ),
            values = mapOf(
                "sanitized_output" to "こんにちは。\n短い応答です。",
                "sanitized_output_length" to "12",
                "raw_native_output" to "こんにちは。\n短い応答です。",
                "raw_native_output_length" to "12",
                "max_output_tokens" to "16",
                "native_max_output_tokens_limit" to "512",
                "quality_classification" to "natural_japanese",
                "output_unicode_summary" to "control_chars=U+000A x1;replacement_char_count=0",
                "output_first_200_chars" to "こんにちは。\n短い応答です。",
                "output_last_200_chars" to "こんにちは。\n短い応答です。",
                "sanitizer_applied" to "false",
                "removed_template_token_count" to "0",
                "removed_prompt_echo" to "false",
                "replacement_char_count" to "0",
                "output_contains_control_chars" to "true",
            ),
        )

        val text = DevOnlyNpuOneTurnConversationContract.receiverResultText(
            display = display,
            timestampMs = 1234L,
        )

        assertTrue(text.contains("timestamp=1234"))
        assertTrue(text.contains("status=success"))
        assertTrue(text.contains("result=success"))
        assertTrue(text.contains("success=true"))
        assertTrue(text.contains("requested_max_output_tokens=16"))
        assertTrue(text.contains("effective_max_output_tokens=16"))
        assertTrue(text.contains("max_output_tokens=16"))
        assertTrue(text.contains("native_max_output_tokens_limit=512"))
        assertTrue(text.contains("run_decode_reached=true"))
        assertTrue(text.contains("npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag"))
        assertTrue(text.contains("fallback_used=false"))
        assertTrue(text.contains("timeout=false"))
        assertTrue(text.contains("fresh_crash=false"))
        assertTrue(text.contains("raw_len=12"))
        assertTrue(text.contains("sanitized_len=12"))
        assertTrue(text.contains("raw_output_first_200_chars=こんにちは。\\n短い応答です。"))
        assertTrue(text.contains("raw_output_last_200_chars=こんにちは。\\n短い応答です。"))
        assertTrue(text.contains("raw_unicode_summary=control_chars=U+000A x1;replacement_char_count=0"))
        assertTrue(text.contains("sanitizer_applied=false"))
        assertTrue(text.contains("removed_template_token_count=0"))
        assertTrue(text.contains("removed_prompt_echo=false"))
        assertTrue(text.contains("replacement_char_count=0"))
        assertTrue(text.contains("output_contains_control_chars=true"))
        assertTrue(text.contains("standard_route_connected=false"))
        assertTrue(text.contains("backend_npu_persisted=false"))
        assertTrue(text.contains("db=false"))
        assertTrue(text.contains("tts=false"))
        assertTrue(text.contains("markdown=false"))
        assertTrue(text.contains("streaming=false"))
        assertTrue(text.contains("route_type=dev_only_one_turn_conversation"))
        assertTrue(text.contains("sanitized_output=こんにちは。\\n短い応答です。"))
        assertTrue(text.contains("quality_classification=natural_japanese"))
        assertTrue(text.contains("output_first_200_chars=こんにちは。\\n短い応答です。"))
    }

    @Test
    fun `receiver progress file is written before entry finishes`() {
        val text = DevOnlyNpuOneTurnConversationContract.receiverProgressText(
            status = "received",
            action = DevOnlyNpuOneTurnConversationContract.RECEIVER_ACTION,
            packageName = "io.github.ninbyo02.lami.debug",
            className = "io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationReceiver",
            userPromptPresent = true,
            timestampMs = 1234L,
        )

        assertTrue(text.contains("timestamp=1234"))
        assertTrue(text.contains("status=received"))
        assertTrue(text.contains("result_code=244"))
        assertTrue(text.contains("action=io.github.ninbyo02.lami.action.DEV_ONLY_NPU_ONE_TURN_CONVERSATION"))
        assertTrue(text.contains("package_name=io.github.ninbyo02.lami.debug"))
        assertTrue(text.contains("class_name=io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationReceiver"))
        assertTrue(text.contains("user_prompt_present=true"))
        assertTrue(text.contains("result=pending"))
        assertTrue(text.contains("requested_max_output_tokens=16"))
        assertTrue(text.contains("effective_max_output_tokens=16"))
        assertTrue(text.contains("max_output_tokens=16"))
        assertTrue(text.contains("run_decode_reached=false"))
        assertTrue(text.contains("fallback_used=false"))
        assertTrue(text.contains("timeout=false"))
        assertTrue(text.contains("fresh_crash=false"))
        assertTrue(text.contains("standard_route_connected=false"))
        assertTrue(text.contains("backend_npu_persisted=false"))
        assertTrue(text.contains("db=false"))
        assertTrue(text.contains("tts=false"))
        assertTrue(text.contains("markdown=false"))
        assertTrue(text.contains("streaming=false"))
        assertTrue(text.contains("route_type=dev_only_one_turn_conversation"))
    }

    @Test
    fun `matrix diagnostic covers greeting and short prompt comparison cases`() {
        assertEquals(
            listOf(
                "こんばんは",
                "こんばんは。",
                "こんばんわ",
                "こんばんは！",
                "こんばんは？",
                "こんにちは",
                "おはよう",
                "ありがとう",
                "ハロー",
                "あ",
                "q",
                "え",
            ),
            DevOnlyNpuOneTurnConversationMatrix.prompts,
        )
    }

    @Test
    fun `matrix diagnostic row exposes safe summaries without full prompt or output`() {
        val longPrompt = "こんばんは。NPUの実機診断で全文を出さずに比較するための長い入力です。"
        val longRawOutput = "こんばんは。raw outputの全文を出さずにhashとpreviewだけを記録するための長い出力です。"
        val longSanitizedOutput = "こんばんは。sanitized outputの全文を出さずにhashとpreviewだけを記録するための長い出力です。"
        val request = DevOnlyNpuOneTurnConversationRequest(
            userPrompt = longPrompt,
            maxOutputTokens = DevOnlyNpuOneTurnConversationContract.COMPARE_MAX_OUTPUT_TOKENS,
        )
        val display = DevOnlyNpuOneTurnConversationContract.display(
            result = DevOnlyNpuRouteResult(
                success = true,
                output = longSanitizedOutput,
                reasonCode = "success",
                elapsedMs = 10,
                decodeElapsedMs = 5,
                prompt = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
                    contextText = "",
                    userPrompt = longPrompt,
                ),
                maxOutputTokens = request.maxOutputTokens,
                backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                artifactPath = null,
                freshCrash = false,
                timeout = false,
            ),
            values = mapOf(
                "sanitized_output" to longSanitizedOutput,
                "sanitized_output_length" to longSanitizedOutput.length.toString(),
                "raw_native_output" to longRawOutput,
                "raw_native_output_length" to longRawOutput.length.toString(),
                "output_first_200_chars" to longRawOutput.take(200),
                "max_output_tokens" to "32",
                "native_max_output_tokens_limit" to "512",
                "quality_classification" to "natural_japanese",
                "output_unicode_summary" to "control_chars=none;replacement_char_count=0",
                "sanitizer_applied" to "false",
                "removed_template_token_count" to "0",
                "removed_prompt_echo" to "false",
                "replacement_char_count" to "0",
                "output_contains_control_chars" to "false",
                "stop_reason" to "eos",
                "finish_reason" to "stop",
                "eos_detected" to "true",
                "output_token_count" to "7",
                "prompt_token_count" to "12",
            ),
        )

        val row = DevOnlyNpuOneTurnConversationMatrix.buildRow(
            index = 1,
            request = request,
            display = display,
        ).joinToString("\n")

        assertTrue(row.contains("case_index=1"))
        assertTrue(row.contains("input_hash="))
        assertTrue(row.contains("input_length=${longPrompt.length}"))
        assertTrue(row.contains("input_code_points=${longPrompt.codePointCount(0, longPrompt.length)}"))
        assertTrue(row.contains("request_prompt_hash="))
        assertTrue(row.contains("request_prompt_length="))
        assertTrue(row.contains("request_prompt_code_points="))
        assertTrue(row.contains("raw_output_hash="))
        assertTrue(row.contains("raw_output_length=${longRawOutput.length}"))
        assertTrue(row.contains("raw_output_code_points=${longRawOutput.codePointCount(0, longRawOutput.length)}"))
        assertTrue(row.contains("sanitized_output_hash="))
        assertTrue(row.contains("sanitized_output_length=${longSanitizedOutput.length}"))
        assertTrue(row.contains("quality_classification=natural_japanese"))
        assertTrue(row.contains("reason=success"))
        assertTrue(row.contains("run_decode_reached=true"))
        assertTrue(row.contains("timeout=false"))
        assertTrue(row.contains("fallback=false"))
        assertTrue(row.contains("fresh_crash=false"))
        assertTrue(row.contains("stop_reason=eos"))
        assertTrue(row.contains("finish_reason=stop"))
        assertTrue(row.contains("eos_detected=true"))
        assertTrue(row.contains("output_token_count=7"))
        assertTrue(row.contains("prompt_token_count=12"))
        assertFalse(row.contains(longPrompt))
        assertFalse(row.contains(longRawOutput))
        assertFalse(row.contains(longSanitizedOutput))
    }

    @Test
    fun `prompt template matrix covers template and prompt comparison cases`() {
        assertEquals(
            listOf(
                DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
                DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_C,
                "simple_ja_chat",
                "gemma_it_like",
            ),
            DevOnlyNpuPromptTemplateMatrix.templates.map { it.name },
        )
        assertEquals(
            listOf(
                "こんにちは",
                "おはよう",
                "こんばんは",
                "明日の天気は",
                "あなたは誰ですか",
            ),
            DevOnlyNpuPromptTemplateMatrix.prompts,
        )
        assertEquals(10, DevOnlyNpuPromptTemplateMatrix.cases().size)
        assertEquals(
            20,
            DevOnlyNpuPromptTemplateMatrix.cases(
                templateFilter = DevOnlyNpuPromptTemplateMatrix.TEMPLATE_FILTER_ALL,
            ).size,
        )
        assertEquals(
            DevOnlyNpuPromptTemplateMatrix.TEMPLATE_FILTER_RAW_ONLY,
            DevOnlyNpuPromptTemplateMatrix.DEFAULT_TEMPLATE_FILTER,
        )
        assertEquals(
            "dev_only_npu_prompt_template_matrix_result.txt",
            DevOnlyNpuPromptTemplateMatrix.RESULT_FILE_NAME,
        )
        assertTrue(
            DevOnlyNpuOneTurnConversationContract.EXTRA_AUTO_RUN_PROMPT_TEMPLATE_MATRIX.contains(
                "prompt_template_matrix",
            ),
        )
    }

    @Test
    fun `prompt template matrix raw filter excludes unsafe templates`() {
        val rawCases = DevOnlyNpuPromptTemplateMatrix.cases(
            templateFilter = DevOnlyNpuPromptTemplateMatrix.TEMPLATE_FILTER_RAW_ONLY,
        )
        val safeCases = DevOnlyNpuPromptTemplateMatrix.cases(
            templateFilter = DevOnlyNpuPromptTemplateMatrix.TEMPLATE_FILTER_SAFE_ONLY,
        )
        val header = DevOnlyNpuPromptTemplateMatrix.buildHeader(
            status = "running",
            templateFilter = DevOnlyNpuPromptTemplateMatrix.TEMPLATE_FILTER_RAW_ONLY,
        ).joinToString("\n")

        assertEquals(10, rawCases.size)
        assertEquals(
            setOf(
                DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B,
                DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_C,
            ),
            rawCases.map { it.template.name }.toSet(),
        )
        assertEquals(rawCases.map { it.template.name }, safeCases.map { it.template.name })
        assertFalse(rawCases.any { it.template.name == "simple_ja_chat" })
        assertFalse(rawCases.any { it.template.name == "gemma_it_like" })
        assertEquals(
            DevOnlyNpuPromptTemplateMatrix.TEMPLATE_FILTER_RAW_ONLY,
            DevOnlyNpuPromptTemplateMatrix.sanitizeTemplateFilter("unknown_filter"),
        )
        assertTrue(header.contains("template_filter=raw_only"))
        assertTrue(header.contains("template_total_count=4"))
        assertTrue(header.contains("template_count=2"))
        assertTrue(header.contains("case_total_count=20"))
        assertTrue(header.contains("case_count=10"))
        assertTrue(header.contains("evaluation_metrics=natural_japanese,mixed_language,question_echo,empty_after_sanitize"))
    }

    @Test
    fun `prompt template matrix filtered templates are recorded as skipped`() {
        val skippedCase = DevOnlyNpuPromptTemplateMatrix.indexedCases().first {
            it.case.template.name == "simple_ja_chat"
        }
        val skipped = DevOnlyNpuPromptTemplateMatrix.CaseResult.skipped(
            reason = DevOnlyNpuPromptTemplateMatrix.REASON_TEMPLATE_FILTER,
        )
        val skippedLines = DevOnlyNpuPromptTemplateMatrix.buildTemplateSkipped(
            index = skippedCase.index,
            case = skippedCase.case,
            reason = skipped.reason,
        ).joinToString("\n")
        val start = DevOnlyNpuPromptTemplateMatrix.buildMatrixStart(
            templateFilter = DevOnlyNpuPromptTemplateMatrix.TEMPLATE_FILTER_SAFE_ONLY,
        ).joinToString("\n")

        assertEquals("skipped", skipped.status)
        assertEquals("template_filter", skipped.reason)
        assertTrue(start.contains("template_filter=safe_only"))
        assertTrue(skippedLines.contains("template_skipped=true"))
        assertTrue(skippedLines.contains("template_name=simple_ja_chat"))
        assertTrue(skippedLines.contains("reason=template_filter"))
    }

    @Test
    fun `prompt template matrix raw case keeps standard route template unchanged`() {
        val rawCase = DevOnlyNpuPromptTemplateMatrix.cases().first {
            it.template.name == DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B &&
                it.inputPrompt == "こんにちは"
        }

        assertTrue(rawCase.requestPrompt.contains("必ず日本語だけで短く返答してください。"))
        assertTrue(rawCase.requestPrompt.contains("ユーザー: こんにちは"))
        assertTrue(rawCase.requestPrompt.endsWith("アシスタント: はい、"))
    }

    @Test
    fun `prompt template matrix row exposes safe summaries and elapsed time`() {
        val case = DevOnlyNpuPromptTemplateMatrix.cases().first()
        val longRawOutput = "こんにちは。raw outputの全文を出さずにhashとpreviewだけを記録するための長い出力です。"
        val longSanitizedOutput = "こんにちは。sanitized outputの全文を出さずにhashとpreviewだけを記録するための長い出力です。"
        val result = DevOnlyNpuPromptTemplateMatrix.CaseResult(
            status = "success",
            reason = "success",
            runDecodeReached = true,
            fallbackUsed = false,
            timeout = false,
            freshCrash = false,
            rawOutput = longRawOutput,
            rawOutputLength = longRawOutput.length,
            sanitizedOutput = longSanitizedOutput,
            sanitizedOutputLength = longSanitizedOutput.length,
            qualityClassification = "natural_japanese",
            elapsedMs = 123,
        )

        val row = DevOnlyNpuPromptTemplateMatrix.buildRow(
            index = 1,
            case = case,
            result = result,
        ).joinToString("\n")

        assertTrue(row.contains("case_index=1"))
        assertTrue(row.contains("template_name=${case.template.name}"))
        assertTrue(row.contains("input_prompt_hash="))
        assertTrue(row.contains("input_prompt_length=${case.inputPrompt.length}"))
        assertTrue(row.contains("request_prompt_hash="))
        assertTrue(row.contains("status=success"))
        assertTrue(row.contains("reason=success"))
        assertTrue(row.contains("run_decode_reached=true"))
        assertTrue(row.contains("fallback_used=false"))
        assertTrue(row.contains("timeout=false"))
        assertTrue(row.contains("fresh_crash=false"))
        assertTrue(row.contains("raw_output_hash="))
        assertTrue(row.contains("raw_output_length=${longRawOutput.length}"))
        assertTrue(row.contains("sanitized_output_hash="))
        assertTrue(row.contains("sanitized_output_length=${longSanitizedOutput.length}"))
        assertTrue(row.contains("quality_classification=natural_japanese"))
        assertTrue(row.contains("elapsed_ms=123"))
        assertFalse(row.contains(longRawOutput))
        assertFalse(row.contains(longSanitizedOutput))
    }

    @Test
    fun `prompt template matrix records rejected template case without aborting report`() = runBlocking {
        val text = DevOnlyNpuPromptTemplateMatrix.run(
            templateFilter = DevOnlyNpuPromptTemplateMatrix.TEMPLATE_FILTER_ALL,
        ) { case ->
            if (case.template.name == "simple_ja_chat") {
                DevOnlyNpuPromptTemplateMatrix.CaseResult.failure(
                    reason = "invalid_prompt:too_long",
                    message = "editable prompt rejected before native execution",
                )
            } else {
                DevOnlyNpuPromptTemplateMatrix.CaseResult(
                    status = "success",
                    reason = "success",
                    runDecodeReached = true,
                    fallbackUsed = false,
                    timeout = false,
                    freshCrash = false,
                    rawOutput = "こんにちは。",
                    rawOutputLength = 6,
                    sanitizedOutput = "こんにちは。",
                    sanitizedOutputLength = 6,
                    qualityClassification = "natural_japanese",
                    elapsedMs = 10,
                )
            }
        }

        assertTrue(text.contains("DEV ONLY NPU PROMPT TEMPLATE MATRIX"))
        assertTrue(text.contains("case_count=20"))
        assertTrue(text.contains("template_name=simple_ja_chat"))
        assertTrue(text.contains("reason=invalid_prompt:too_long"))
        assertTrue(text.contains("template_name=gemma_it_like"))
        assertTrue(text.contains("standard_route_template_unchanged=raw_dialog_tail_variant_b"))
        assertTrue(text.contains("prompt_and_output_policy=hash_length_code_points_preview_only"))
        assertTrue(text.contains("template_failure_threshold=2"))
        assertTrue(text.contains("matrix_timeout_ms=600000"))
        assertFalse(text.contains("editable prompt rejected before native execution"))
    }

    @Test
    fun `prompt template matrix progress lines expose case lifecycle without full values`() {
        val case = DevOnlyNpuPromptTemplateMatrix.cases().first {
            it.template.name == DevOnlyNpuOneTurnConversationContract.RAW_DIALOG_TAIL_VARIANT_B
        }
        val result = DevOnlyNpuPromptTemplateMatrix.CaseResult(
            status = "failure",
            reason = "empty_after_sanitize",
            runDecodeReached = true,
            fallbackUsed = false,
            timeout = false,
            freshCrash = false,
            rawOutput = "",
            rawOutputLength = 0,
            sanitizedOutput = "",
            sanitizedOutputLength = 0,
            qualityClassification = "mixed_language",
            elapsedMs = 456,
        )

        val start = DevOnlyNpuPromptTemplateMatrix.buildMatrixStart().joinToString("\n")
        val caseStart = DevOnlyNpuPromptTemplateMatrix.buildCaseStart(
            index = 1,
            case = case,
        ).joinToString("\n")
        val caseDone = DevOnlyNpuPromptTemplateMatrix.buildCaseDone(
            index = 1,
            case = case,
            result = result,
        ).joinToString("\n")

        assertTrue(start.contains("matrix_start=true"))
        assertTrue(start.contains("status=running"))
        assertTrue(caseStart.contains("case_start=true"))
        assertTrue(caseStart.contains("case_index=1"))
        assertTrue(caseStart.contains("template_name=${case.template.name}"))
        assertTrue(caseStart.contains("prompt_hash="))
        assertTrue(caseStart.contains("prompt_length=${case.inputPrompt.length}"))
        assertTrue(caseStart.contains("prompt_code_points=${case.inputPrompt.codePointCount(0, case.inputPrompt.length)}"))
        assertTrue(caseStart.contains("prompt_preview=${case.inputPrompt}"))
        assertTrue(caseDone.contains("case_done=true"))
        assertTrue(caseDone.contains("status=failure"))
        assertTrue(caseDone.contains("reason=empty_after_sanitize"))
        assertTrue(caseDone.contains("elapsed_ms=456"))
    }

    @Test
    fun `prompt template matrix failure progress records exception safely`() {
        val case = DevOnlyNpuPromptTemplateMatrix.cases().first()
        val message = "editable prompt rejected before native execution because the prompt is too long"

        val failure = DevOnlyNpuPromptTemplateMatrix.buildCaseFailed(
            index = 2,
            case = case,
            throwable = IllegalStateException(message),
            elapsedMs = 789,
        ).joinToString("\n")

        assertTrue(failure.contains("case_failed=true"))
        assertTrue(failure.contains("case_index=2"))
        assertTrue(failure.contains("template_name=${case.template.name}"))
        assertTrue(failure.contains("exception_class=IllegalStateException"))
        assertTrue(failure.contains("message_hash="))
        assertTrue(failure.contains("message_preview=editable prompt rejected"))
        assertTrue(failure.contains("elapsed_ms=789"))
        assertFalse(failure.contains(message))
    }

    @Test
    fun `prompt template matrix timeout failure remains a case result`() {
        val result = DevOnlyNpuPromptTemplateMatrix.CaseResult.failure(
            reason = "case_timeout",
            message = "Timed out waiting for template matrix case",
            elapsedMs = 60_000,
            timeout = true,
        )

        assertEquals("failure", result.status)
        assertEquals("case_timeout", result.reason)
        assertFalse(result.runDecodeReached)
        assertTrue(result.timeout)
        assertEquals(60_000L, result.elapsedMs)
    }

    @Test
    fun `prompt template matrix skipped case records threshold reason`() {
        val case = DevOnlyNpuPromptTemplateMatrix.cases(
            templateFilter = DevOnlyNpuPromptTemplateMatrix.TEMPLATE_FILTER_ALL,
        ).first {
            it.template.name == "simple_ja_chat"
        }
        val skipped = DevOnlyNpuPromptTemplateMatrix.CaseResult.skipped()
        val skippedLines = DevOnlyNpuPromptTemplateMatrix.buildTemplateSkipped(
            index = 9,
            case = case,
        ).joinToString("\n")
        val row = DevOnlyNpuPromptTemplateMatrix.buildRow(
            index = 9,
            case = case,
            result = skipped,
        ).joinToString("\n")

        assertEquals("skipped", skipped.status)
        assertEquals("template_failure_threshold", skipped.reason)
        assertFalse(skipped.runDecodeReached)
        assertFalse(skipped.timeout)
        assertEquals("skipped", skipped.qualityClassification)
        assertTrue(skippedLines.contains("template_skipped=true"))
        assertTrue(skippedLines.contains("template_name=simple_ja_chat"))
        assertTrue(skippedLines.contains("case_index=9"))
        assertTrue(skippedLines.contains("reason=template_failure_threshold"))
        assertTrue(row.contains("status=skipped"))
        assertTrue(row.contains("reason=template_failure_threshold"))
        assertTrue(row.contains("run_decode_reached=false"))
        assertTrue(row.contains("quality_classification=skipped"))
    }
}
