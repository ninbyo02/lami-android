package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuS1PersistentEngineDiagnosticsTest {
    @Test
    fun `persistent engine state can represent statuses`() {
        listOf(
            NPU_S1_PERSISTENT_ENGINE_STATUS_IDLE,
            NPU_S1_PERSISTENT_ENGINE_STATUS_RUNNING,
            NPU_S1_PERSISTENT_ENGINE_STATUS_COMPLETED,
            NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED,
            NPU_S1_PERSISTENT_ENGINE_STATUS_CANCELLED,
        ).forEach { status ->
            val text = formatNpuS1PersistentEngineDiagnosticsForDev(
                NpuS1PersistentEngineProbeState(persistentProbeStatus = status),
            )

            assertTrue(text.contains("persistent_probe_status=$status"))
        }
    }

    @Test
    fun `summary includes engine initialize count and first failure`() {
        val state = NpuS1PersistentEngineProbeState(
            persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED,
            engineInitializeCount = 1,
            firstFailureRunIndex = 7,
            firstFailureStage = "decode",
            firstFailureReason = "decode_failed:LiteRtLmJniException",
            firstFailureExceptionClass = "LiteRtLmJniException",
            persistentEngineHypothesisResult = "decode_failed",
            records = listOf(
                NpuS1PersistentEngineRunRecord(
                    runIndex = 7,
                    status = FailureNpuStandardRouteS1Provider.STATUS_FAILURE,
                    reason = "decode_failed:LiteRtLmJniException",
                    conversationCreated = "true",
                    decodeStarted = "true",
                    failureStage = "decode",
                    failureExceptionClass = "LiteRtLmJniException",
                ),
            ),
        )
        val text = formatNpuS1PersistentEngineDiagnosticsForDev(state)

        assertTrue(text.contains("engine_initialize_count=1"))
        assertTrue(text.contains("first_failure_run_index=7"))
        assertTrue(text.contains("first_failure_stage=decode"))
        assertTrue(text.contains("first_failure_exception_class=LiteRtLmJniException"))
        assertTrue(text.contains("persistent_engine_hypothesis_result=decode_failed"))
    }

    @Test
    fun `token limit keys are separated from requested output tokens`() {
        val state = NpuS1PersistentEngineProbeState(
            promptTextLengthChars = 78,
            requestedMaxOutputTokens = 32,
            officialTotalTokenLimit = 512,
            officialOutputTokenLimit = "not_exposed",
            tokenLimitSource = "engine_config_max_num_tokens_total_limit",
            records = listOf(
                NpuS1PersistentEngineRunRecord(
                    runIndex = 1,
                    status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                    reason = "success",
                    promptTextLengthChars = 78,
                    requestedMaxOutputTokens = 32,
                    officialTotalTokenLimit = 512,
                    officialOutputTokenLimit = "not_exposed",
                    tokenLimitSource = "engine_config_max_num_tokens_total_limit",
                ),
            ),
        )
        val text = formatNpuS1PersistentEngineDiagnosticsForDev(state)

        assertTrue(text.contains("prompt_text_length_chars=78"))
        assertTrue(text.contains("requested_max_output_tokens=32"))
        assertTrue(text.contains("official_total_token_limit=512"))
        assertTrue(text.contains("official_output_token_limit=not_exposed"))
        assertTrue(text.contains("token_limit_source=engine_config_max_num_tokens_total_limit"))
        assertTrue(text.contains("token_limit_fix_note=official_api_uses_max_num_tokens_as_total_input_context_limit_not_output_only"))
    }

    @Test
    fun `input token limit failure is classified as token limit failed`() {
        val message = "Status Code: 3. Message: Input token ids are too long. " +
            "Exceeding the maximum number of tokens allowed: 78 >= 32"
        val state = NpuS1PersistentEngineProbeState(
            persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED,
            firstFailureRunIndex = 1,
            firstFailureStage = npuS1PersistentFailureStage(
                conversationCreated = true,
                decodeStarted = true,
                message = message,
            ),
            firstFailureReason = "token_limit_failed:LiteRtLmJniException",
            firstFailureExceptionClass = "LiteRtLmJniException",
            firstFailureTokenLimitMessage = message,
            persistentEngineHypothesisResult = npuS1PersistentHypothesisResultForFailureStage("token_limit"),
            records = listOf(
                NpuS1PersistentEngineRunRecord(
                    runIndex = 1,
                    status = FailureNpuStandardRouteS1Provider.STATUS_FAILURE,
                    reason = "token_limit_failed:LiteRtLmJniException",
                    failureStage = "token_limit",
                    failureExceptionClass = "LiteRtLmJniException",
                    failureExceptionMessage = message,
                    tokenLimitFailureDetected = "true",
                    tokenLimitFailureMessage = message,
                ),
            ),
        )
        val text = formatNpuS1PersistentEngineDiagnosticsForDev(state)

        assertTrue(isNpuS1PersistentTokenLimitFailure(message))
        assertTrue(text.contains("first_failure_stage=token_limit"))
        assertTrue(text.contains("persistent_engine_hypothesis_result=token_limit_failed"))
        assertTrue(text.contains("first_failure_token_limit_message=Status Code: 3. Message: Input token ids are too long."))
        assertTrue(text.contains("token_limit_failure_detected=true"))
        assertTrue(text.contains("token_limit_failure_message=Status Code: 3. Message: Input token ids are too long."))
    }

    @Test
    fun `logits failure is classified as npu backend unsupported`() {
        val message = "Status Code: 12. Message: Decode for logits output not implemented for backend: " +
            "LiteRT NPU Compiled Model"
        val state = NpuS1PersistentEngineProbeState(
            persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED,
            persistentEngineApiMode = "auto",
            attemptedApiModes = "session",
            selectedApiMode = "session",
            apiModeSelectionReason = "session_api_available_prefers_generate_content",
            logitsOutputRequired = "true",
            logitsOutputBackendSupported = "false",
            logitsFailureDetected = "true",
            logitsFailureMessage = message,
            sessionApiAvailable = "true",
            sessionApiUsed = "true",
            conversationApiUsed = "false",
            streamingApiUsed = "false",
            firstFailureStage = npuS1PersistentFailureStage(
                conversationCreated = true,
                decodeStarted = true,
                message = message,
            ),
            persistentEngineHypothesisResult = npuS1PersistentHypothesisResultForFailureMessage(
                stage = "decode",
                message = message,
            ),
            records = listOf(
                NpuS1PersistentEngineRunRecord(
                    runIndex = 1,
                    status = FailureNpuStandardRouteS1Provider.STATUS_FAILURE,
                    reason = "logits_output_not_supported_on_npu_backend:LiteRtLmJniException",
                    sessionCreated = "true",
                    sessionClosed = "true",
                    decodeStarted = "true",
                    failureStage = "decode",
                    failureExceptionClass = "LiteRtLmJniException",
                    failureExceptionMessage = message,
                    apiModeUsed = "session",
                    logitsFailureDetected = "true",
                    logitsFailureMessage = message,
                    streamingStarted = "false",
                    streamingFinished = "false",
                ),
            ),
        )
        val text = formatNpuS1PersistentEngineDiagnosticsForDev(state)

        assertTrue(isNpuS1PersistentLogitsFailure(message))
        assertTrue(text.contains("selected_api_mode=session"))
        assertTrue(text.contains("session_api_available=true"))
        assertTrue(text.contains("session_api_used=true"))
        assertTrue(text.contains("conversation_api_used=false"))
        assertTrue(text.contains("streaming_api_used=false"))
        assertTrue(text.contains("logits_failure_detected=true"))
        assertTrue(text.contains("logits_output_backend_supported=false"))
        assertTrue(text.contains("persistent_engine_hypothesis_result=logits_output_not_supported_on_npu_backend"))
        assertTrue(text.contains("api_mode_used=session"))
        assertTrue(text.contains("streaming_started=false"))
        assertTrue(text.contains("streaming_finished=false"))
    }

    @Test
    fun `unavailable session counters are not formatted as false or zero`() {
        val text = formatNpuS1PersistentEngineDiagnosticsForDev(
            NpuS1PersistentEngineProbeState(
                records = listOf(
                    NpuS1PersistentEngineRunRecord(
                        runIndex = 1,
                        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                        reason = "success",
                    ),
                ),
            ),
        )

        assertTrue(text.contains("session_create_count=unavailable"))
        assertTrue(text.contains("session_close_count=unavailable"))
        assertTrue(text.contains("session_created=unavailable"))
        assertTrue(text.contains("session_closed=unavailable"))
        assertFalse(text.contains("session_create_count=0"))
        assertFalse(text.contains("session_created=false"))
    }

    @Test
    fun `append function keeps base text and persistent diagnostics`() {
        val text = appendNpuS1PersistentEngineDiagnosticsForDev(
            text = "base=true",
            state = NpuS1PersistentEngineProbeState(engineInitializeCount = 1),
        )

        assertTrue(text.startsWith("base=true"))
        assertTrue(text.contains("[DEV診断: NPU S1 persistent engine summary]"))
        assertTrue(text.contains("engine_initialize_count=1"))
    }
}
