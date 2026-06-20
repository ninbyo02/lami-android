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
            NPU_S1_PERSISTENT_ENGINE_STATUS_BLOCKED,
        ).forEach { status ->
            val text = formatNpuS1PersistentEngineDiagnosticsForDev(
                NpuS1PersistentEngineProbeState(persistentProbeStatus = status),
            )

            assertTrue(text.contains("persistent_probe_status=$status"))
        }
    }

    @Test
    fun `multi turn summary exposes persistent engine test keys without inferring reuse`() {
        val state = NpuS1PersistentEngineProbeState(
            persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_COMPLETED,
            runCountRequested = 10,
            waitMs = 500L,
            engineInitializeCount = 1,
            engineInitializeFinishedAtElapsedRealtimeMs = 1_100L,
            backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
            records = listOf(
                NpuS1PersistentEngineRunRecord(
                    runIndex = 1,
                    status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                    reason = "success",
                    prompt = "こんにちは",
                    sessionCreated = "true",
                    sessionClosed = "true",
                    decodeStarted = "true",
                    decodeFinished = "true",
                    rawOutput = "こんにちは。",
                    sanitizedOutput = "こんにちは。",
                    qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                    fallbackUsed = "false",
                    timeout = "false",
                    freshCrash = "false",
                    totalMs = 120L,
                    decodeMs = 100L,
                    backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                ),
                NpuS1PersistentEngineRunRecord(
                    runIndex = 2,
                    status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                    reason = "success",
                    prompt = "こんにちは",
                    sessionCreated = "true",
                    sessionClosed = "true",
                    decodeStarted = "true",
                    decodeFinished = "true",
                    rawOutput = "こんにちは。",
                    sanitizedOutput = "こんにちは。",
                    qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                    fallbackUsed = "false",
                    timeout = "false",
                    freshCrash = "false",
                    totalMs = 180L,
                    decodeMs = 140L,
                    backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                ),
            ),
        )
        val text = formatNpuS1PersistentEngineDiagnosticsForDev(state)

        assertTrue(text.contains("test_name=NPU Persistent Engine Multi-turn Test"))
        assertTrue(text.contains("persistent_engine_requested=true"))
        assertTrue(text.contains("persistent_engine_available=true"))
        assertTrue(text.contains("engine_reuse_observed=unavailable"))
        assertFalse(text.contains("engine_reuse_observed=true"))
        assertTrue(text.contains("engine_holder_id=not_exposed"))
        assertTrue(text.contains("provider_instance_id=not_exposed"))
        assertTrue(text.contains("adapter_instance_id=not_exposed"))
        assertTrue(text.contains("session_id=not_exposed"))
        assertTrue(text.contains("run_count_requested=10"))
        assertTrue(text.contains("run_count_completed=2"))
        assertTrue(text.contains("success_count=2"))
        assertTrue(text.contains("failure_count=0"))
        assertTrue(text.contains("success_rate=1.00"))
        assertTrue(text.contains("fallback_used_count=0"))
        assertTrue(text.contains("timeout_count=0"))
        assertTrue(text.contains("fresh_crash_count=0"))
        assertTrue(text.contains("engine_create_failed_count=0"))
        assertTrue(text.contains("run_decode_reached_count=2"))
        assertTrue(text.contains("run_decode_reached_rate=1.00"))
        assertTrue(text.contains("average_total_ms=150.00"))
        assertTrue(text.contains("average_decode_ms=120.00"))
        assertTrue(text.contains("average_tokens_per_second=unavailable"))
        assertTrue(text.contains("backend_evidence_summary=QNN_HTP_V79_FastRPC_native_diag:2"))
        assertTrue(text.contains("quality_classification_summary=natural_japanese:2"))
        assertTrue(text.contains("restart_app_recommended=false"))
        assertTrue(text.contains("prompt=こんにちは"))
        assertTrue(text.contains("run_decode_reached=true"))
        assertTrue(text.contains("fallback_used=false"))
        assertTrue(text.contains("holder_identity=not_exposed"))
        assertTrue(text.contains("native_stage_history=unavailable"))
    }

    @Test
    fun `engine create failure recommends app restart and exposes native diag tail`() {
        val state = NpuS1PersistentEngineProbeState(
            persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED,
            runCountRequested = 10,
            engineInitializeCount = 1,
            engineInitializeFinishedAtElapsedRealtimeMs = 1_100L,
            firstFailureRunIndex = 7,
            firstFailureReason = "adapter_failure:LiteRtLmJniException",
            records = (1..6).map { index ->
                NpuS1PersistentEngineRunRecord(
                    runIndex = index,
                    status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                    reason = "success",
                    decodeStarted = "true",
                    decodeFinished = "true",
                    fallbackUsed = "false",
                    timeout = "false",
                    backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                    qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                )
            } + NpuS1PersistentEngineRunRecord(
                runIndex = 7,
                status = FailureNpuStandardRouteS1Provider.STATUS_FAILURE,
                reason = "adapter_failure:LiteRtLmJniException",
                decodeStarted = "false",
                decodeFinished = "false",
                fallbackUsed = "false",
                timeout = "false",
                failureExceptionClass = "LiteRtLmJniException",
                failureExceptionMessage = "engine-create-failed:INTERNAL",
                nativeOrEngineDiagTail = "before EngineFactory::CreateDefault engine-create-failed:INTERNAL runtime/executor/llm_litert_npu_compiled_model_executor.cc:2725",
                backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
            ),
        )
        val text = formatNpuS1PersistentEngineDiagnosticsForDev(state)

        assertTrue(text.contains("engine_create_failed_count=1"))
        assertTrue(text.contains("restart_app_recommended=true"))
        assertTrue(text.contains("guard_recommendation=disable_npu_until_app_restart_or_cooldown"))
        assertTrue(text.contains("first_failure_run_index=7"))
        assertTrue(text.contains("first_failure_reason=adapter_failure:LiteRtLmJniException"))
        assertTrue(text.contains("first_failure_native_diag_tail=before EngineFactory::CreateDefault engine-create-failed:INTERNAL"))
        assertTrue(text.contains("run_index=7"))
        assertTrue(text.contains("native_or_engine_diag_tail=before EngineFactory::CreateDefault engine-create-failed:INTERNAL"))
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
    fun `npu session api can be blocked before repeating logits unsupported failure`() {
        val state = NpuS1PersistentEngineProbeState(
            persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_BLOCKED,
            firstFailureStage = "api_mode_selection",
            firstFailureReason = NPU_S1_PERSISTENT_ENGINE_SESSION_API_NPU_BLOCK_REASON,
            blockedReason = NPU_S1_PERSISTENT_ENGINE_SESSION_API_NPU_BLOCK_REASON,
            persistentEngineHypothesisResult = "blocked_session_api_logits_output_not_supported_on_npu_backend",
            persistentEngineApiMode = NPU_S1_PERSISTENT_ENGINE_API_MODE_STANDARD_ROUTE_ADAPTER,
            attemptedApiModes = "standard_route_adapter,session",
            selectedApiMode = "unavailable",
            apiModeSelectionReason = "session_api_blocked_for_npu_standard_route_adapter_not_exposed",
            logitsOutputRequired = "true",
            logitsOutputBackendSupported = "false",
            logitsFailureDetected = "false",
            sessionApiAvailable = "true",
            sessionApiUsed = "false",
            sessionApiBlockedForNpu = "true",
            sessionApiBlockReason = "logits_output_not_supported_on_npu_backend",
            standardRouteAdapterAvailable = "false",
            standardRouteAdapterUsed = "false",
            standardRouteAdapterReason = "needs_native_adapter_work",
            persistentStandardRouteAvailable = "false",
            persistentStandardRouteReason = "needs_native_adapter_work",
        )
        val text = formatNpuS1PersistentEngineDiagnosticsForDev(state)

        assertTrue(text.contains("persistent_probe_status=blocked"))
        assertTrue(text.contains("persistent_engine_available=false"))
        assertTrue(text.contains("blocked_reason=session_api_logits_output_not_supported_on_npu_backend"))
        assertTrue(text.contains("persistent_engine_api_mode=standard_route_adapter"))
        assertTrue(text.contains("attempted_api_modes=standard_route_adapter,session"))
        assertTrue(text.contains("selected_api_mode=unavailable"))
        assertTrue(text.contains("api_mode_selection_reason=session_api_blocked_for_npu_standard_route_adapter_not_exposed"))
        assertTrue(text.contains("session_api_available=true"))
        assertTrue(text.contains("session_api_used=false"))
        assertTrue(text.contains("session_api_blocked_for_npu=true"))
        assertTrue(text.contains("session_api_block_reason=logits_output_not_supported_on_npu_backend"))
        assertTrue(text.contains("standard_route_adapter_available=false"))
        assertTrue(text.contains("standard_route_adapter_used=false"))
        assertTrue(text.contains("standard_route_adapter_reason=needs_native_adapter_work"))
        assertTrue(text.contains("persistent_standard_route_available=false"))
        assertTrue(text.contains("persistent_standard_route_reason=needs_native_adapter_work"))
        assertTrue(text.contains("logits_output_required=true"))
        assertTrue(text.contains("logits_output_backend_supported=false"))
        assertTrue(text.contains("restart_app_recommended=false"))
        assertTrue(text.contains("engine_reuse_observed=unavailable"))
        assertFalse(text.contains("engine_reuse_observed=true"))
    }

    @Test
    fun `Copy Persistent Summary excludes details while Full Dump includes them`() {
        val state = NpuS1PersistentEngineProbeState(
            persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_BLOCKED,
            blockedReason = NPU_S1_PERSISTENT_ENGINE_SESSION_API_NPU_BLOCK_REASON,
            records = listOf(
                NpuS1PersistentEngineRunRecord(
                    runIndex = 1,
                    status = FailureNpuStandardRouteS1Provider.STATUS_FAILURE,
                    reason = "blocked",
                    nativeOrEngineDiagTail = "blocked_before_session_generate_content",
                ),
            ),
        )

        val summary = buildNpuPersistentEngineSummaryCopyText(state)
        val fullDump = buildNpuPersistentEngineFullDumpCopyText(state)

        assertTrue(summary.contains("[DEV診断: NPU S1 persistent engine summary]"))
        assertTrue(summary.contains("blocked_reason=session_api_logits_output_not_supported_on_npu_backend"))
        assertFalse(summary.contains("[DEV診断: NPU S1 persistent engine details]"))
        assertFalse(summary.contains("\nrun_index=1"))
        assertTrue(fullDump.contains("[DEV診断: NPU S1 persistent engine summary]"))
        assertTrue(fullDump.contains("[DEV診断: NPU S1 persistent engine details]"))
        assertTrue(fullDump.contains("run_index=1"))
        assertTrue(fullDump.contains("native_or_engine_diag_tail=blocked_before_session_generate_content"))
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
