package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuOneTurnConversationEntryTest {
    @Test
    fun `raw dialog tail formatting is fixed for one turn`() {
        val formatted = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
            contextText = "これは中立文脈です。",
            userPrompt = "こんにちは。",
        )

        assertTrue(formatted.contains("必ず日本語だけで短く返答してください。"))
        assertTrue(formatted.contains("ユーザー: こんにちは。"))
        assertTrue(formatted.endsWith("アシスタント: こんにちは"))
        assertTrue(formatted.contains("\n\n必ず日本語だけで短く返答してください。\nユーザー:"))
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
        assertEquals("こんにちは", DevOnlyNpuOneTurnConversationContract.JAPANESE_ASSISTANT_PREFIX)
        assertTrue(DevOnlyNpuOneTurnConversationContract.safetyLines().contains("route_type=dev_only_one_turn_conversation"))
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
        assertEquals("context", DevOnlyNpuOneTurnConversationContract.EXTRA_CONTEXT)
        assertEquals(
            "unsafe_dev_bypass_prompt_length_gate",
            DevOnlyNpuOneTurnConversationContract.EXTRA_UNSAFE_DEV_BYPASS_PROMPT_LENGTH_GATE,
        )
        assertTrue(DevOnlyNpuOneTurnConversationContract.RECEIVER_ACTION.contains("DEV_ONLY"))
        assertEquals("こんにちは", DevOnlyNpuOneTurnConversationContract.DEFAULT_USER_PROMPT)
        assertEquals(244, DevOnlyNpuOneTurnConversationContract.RECEIVER_RESULT_CODE_RECEIVED)
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
}
