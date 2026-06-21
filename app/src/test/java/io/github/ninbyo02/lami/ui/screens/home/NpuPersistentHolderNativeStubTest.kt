package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuPersistentHolderNativeStubTest {
    @Test
    fun `true engine holder copy text reports no result before run`() {
        val state = NpuTrueEngineHolderCreateCloseProbeState()

        val summary = formatNpuTrueEngineHolderCreateCloseSummaryForCopy(state)
        val fullDump = formatNpuTrueEngineHolderCreateCloseFullDumpForCopy(state)

        assertTrue(summary.contains("no true engine holder create/close probe result available"))
        assertTrue(summary.contains("test_name=NPU True Engine Holder Create Close Probe"))
        assertTrue(summary.contains("true_engine_create_close_probe_startup_safe=true"))
        assertTrue(summary.contains("native_call_deferred_until_button_click=true"))
        assertTrue(summary.contains("startup_native_call_blocked=true"))
        assertTrue(summary.contains("probe_execution_available=false"))
        assertTrue(summary.contains("probe_execution_block_reason=temporarily_blocked_to_restore_startup"))
        assertTrue(fullDump.contains("no true engine holder create/close probe result available"))
        assertTrue(fullDump.contains("probe_status=idle"))
        assertTrue(fullDump.contains("probe_reason=not_run"))
        assertTrue(fullDump.contains("startup_native_call_blocked=true"))
    }

    @Test
    fun `true engine holder blocked summary is startup safe and does not enter native run`() {
        val state = NpuTrueEngineHolderCreateCloseProbeState(
            status = "blocked",
            reason = NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_BLOCK_REASON,
            modelPathOrReason = "not_resolved_startup_safe_block",
            holderId = "true-engine-holder-create-close-dev",
            nativeResult = blockedNpuTrueEngineHolderCreateCloseNativeResult(),
        )

        val summary = formatNpuTrueEngineHolderCreateCloseSummaryForCopy(state)
        val fullDump = formatNpuTrueEngineHolderCreateCloseFullDumpForCopy(state)

        assertTrue(summary.contains("probe_status=blocked"))
        assertTrue(summary.contains("probe_reason=temporarily_blocked_to_restore_startup"))
        assertTrue(summary.contains("selected_native_probe_mode=true_engine_create_close_only"))
        assertTrue(summary.contains("true_engine_create_close_probe_startup_safe=true"))
        assertTrue(summary.contains("native_call_deferred_until_button_click=true"))
        assertTrue(summary.contains("startup_native_call_blocked=true"))
        assertTrue(summary.contains("probe_execution_available=false"))
        assertTrue(summary.contains("probe_execution_block_reason=temporarily_blocked_to_restore_startup"))
        assertTrue(summary.contains("argument_validation_passed=false"))
        assertTrue(summary.contains("model_assets_create_called=false"))
        assertTrue(summary.contains("engine_settings_create_called=false"))
        assertTrue(summary.contains("engine_create_called=false"))
        assertTrue(summary.contains("engine_close_succeeded=false"))
        assertTrue(summary.contains("session_create_count=0"))
        assertTrue(summary.contains("decode_count=0"))
        assertTrue(summary.contains("generate_count=0"))
        assertTrue(summary.contains("npu_decode_called=false"))
        assertTrue(summary.contains("qnn_decode_called=false"))
        assertTrue(summary.contains("true_engine_persistent_reuse=false"))
        assertTrue(summary.contains("engine_reuse_observed=unavailable"))
        assertTrue(
            summary.contains(
                "recommended_next_step=restore_startup_then_rebuild_native_create_close_mode_in_isolated_flavor",
            ),
        )
        assertTrue(fullDump.contains("native_return=blocked"))
        assertTrue(fullDump.contains("model_path_or_reason=not_resolved_startup_safe_block"))
        assertFalse(summary.contains("true_engine_persistent_reuse=true"))
        assertFalse(summary.contains("engine_reuse_observed=true"))
    }

    @Test
    fun `true engine holder summary reports create close only without session decode or generate`() {
        val state = NpuTrueEngineHolderCreateCloseProbeState(
            status = "completed",
            reason = "engine_create_once_zero_runs_success",
            holderId = "true-engine-holder-create-close-dev",
            nativeResult = NpuTrueEngineHolderNativeResult(
                nativeReturn = "persistent_custom_jni_status=completed",
                resultText = """
                    selected_native_probe_mode=true_engine_create_close_only
                    argument_validation_passed=true
                    run_count_validation_skipped_for_create_close_only=true
                    persistent_custom_jni_status=completed
                    model_assets_create_reached=true
                    model_assets_create_returned=true
                    engine_settings_create_reached=true
                    engine_settings_create_returned=true
                    engine_create_reached=true
                    engine_create_returned=true
                    engine_create_count=1
                    engine_close_reached=true
                    engine_close_success=true
                    session_create_reached=false
                    decode_reached=false
                    decode_attempt_count=0
                    persistent_custom_jni_hypothesis_result=engine_create_once_zero_runs_success
                """.trimIndent(),
            ),
        )

        val summary = formatNpuTrueEngineHolderCreateCloseSummaryForCopy(state)
        val fullDump = formatNpuTrueEngineHolderCreateCloseFullDumpForCopy(state)

        assertTrue(summary.contains("test_name=NPU True Engine Holder Create Close Probe"))
        assertTrue(summary.contains("selected_native_probe_mode=true_engine_create_close_only"))
        assertTrue(summary.contains("argument_validation_passed=true"))
        assertTrue(summary.contains("run_count_validation_skipped_for_create_close_only=true"))
        assertTrue(summary.contains("holder_create_called=true"))
        assertTrue(summary.contains("holder_create_succeeded=true"))
        assertTrue(summary.contains("model_assets_create_called=true"))
        assertTrue(summary.contains("model_assets_create_succeeded=true"))
        assertTrue(summary.contains("engine_settings_create_called=true"))
        assertTrue(summary.contains("engine_settings_create_succeeded=true"))
        assertTrue(summary.contains("engine_factory_create_called=true"))
        assertTrue(summary.contains("engine_create_called=true"))
        assertTrue(summary.contains("engine_create_succeeded=true"))
        assertTrue(summary.contains("engine_holder_open=false"))
        assertTrue(summary.contains("engine_holder_id=true-engine-holder-create-close-dev"))
        assertTrue(summary.contains("engine_create_count=1"))
        assertTrue(summary.contains("engine_close_count=1"))
        assertTrue(summary.contains("engine_close_succeeded=true"))
        assertTrue(summary.contains("engine_double_close_safe=true"))
        assertTrue(summary.contains("engine_fatal_latch=false"))
        assertTrue(summary.contains("session_create_count=0"))
        assertTrue(summary.contains("decode_count=0"))
        assertTrue(summary.contains("generate_count=0"))
        assertTrue(summary.contains("npu_decode_called=false"))
        assertTrue(summary.contains("qnn_decode_called=false"))
        assertTrue(summary.contains("true_engine_persistent_reuse_possible=true"))
        assertTrue(summary.contains("true_engine_persistent_reuse=false"))
        assertTrue(summary.contains("engine_reuse_observed=unavailable"))
        assertTrue(summary.contains("restart_app_recommended=false"))
        assertTrue(fullDump.contains("native_result_begin"))
        assertTrue(fullDump.contains("persistent_custom_jni_hypothesis_result=engine_create_once_zero_runs_success"))
        assertFalse(summary.contains("true_engine_persistent_reuse=true"))
        assertFalse(summary.contains("engine_reuse_observed=true"))
    }

    @Test
    fun `true engine holder failure recommends restart and does not infer reuse`() {
        val state = NpuTrueEngineHolderCreateCloseProbeState(
            status = "failed",
            reason = "engine_create_failed",
            nativeResult = NpuTrueEngineHolderNativeResult(
                throwableClass = "java.lang.IllegalStateException",
                throwableMessage = "engine_create_failed",
                resultText = """
                    selected_native_probe_mode=true_engine_create_close_only
                    argument_validation_passed=true
                    run_count_validation_skipped_for_create_close_only=true
                    persistent_custom_jni_status=stopped
                    model_assets_create_reached=true
                    model_assets_create_returned=false
                    engine_settings_create_reached=false
                    engine_create_reached=false
                    engine_close_reached=false
                    session_create_reached=false
                    decode_reached=false
                    decode_attempt_count=0
                """.trimIndent(),
            ),
        )

        val summary = formatNpuTrueEngineHolderCreateCloseSummaryForCopy(state)

        assertTrue(summary.contains("engine_fatal_latch=true"))
        assertTrue(summary.contains("engine_fatal_reason=engine_create_failed"))
        assertTrue(summary.contains("restart_app_recommended=true"))
        assertTrue(summary.contains("decode_count=0"))
        assertTrue(summary.contains("session_create_count=0"))
        assertTrue(summary.contains("generate_count=0"))
        assertTrue(summary.contains("true_engine_persistent_reuse_possible=false"))
        assertTrue(summary.contains("true_engine_persistent_reuse=false"))
        assertFalse(summary.contains("true_engine_persistent_reuse=true"))
    }

    @Test
    fun `ten turn copy text reports no result before run`() {
        val state = NpuPersistentHolderTenTurnProbeState()

        val summary = formatNpuPersistentHolderTenTurnSummaryForCopy(state)
        val fullDump = formatNpuPersistentHolderTenTurnFullDumpForCopy(state)

        assertTrue(summary.contains("no holder ten-turn probe result available"))
        assertTrue(summary.contains("test_name=NPU Persistent Holder Ten Turn Probe"))
        assertTrue(fullDump.contains("no holder ten-turn probe result available"))
        assertTrue(fullDump.contains("probe_status=idle"))
        assertTrue(fullDump.contains("probe_reason=not_run"))
    }

    @Test
    fun `ten turn summary includes rates and keeps true persistence unproven`() {
        val createDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCreateCalled = true,
            holderCreateSucceeded = true,
            holderId = "native-holder-1",
            holderOpen = true,
            status = "created",
            reason = "app_jni_holder_lifecycle_created_without_engine_create",
        )
        val runDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeRunCalled = true,
            holderId = "native-holder-1",
            holderOpenBeforeRun = true,
            runOnceRequested = true,
            runOnceSupported = true,
            status = "run_ready",
            reason = "holder_open_existing_one_shot_decode_may_run_once",
        )
        val closeDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCloseCalled = true,
            holderCloseRequested = true,
            holderCloseSucceeded = true,
            status = "closed",
            reason = "holder_closed_without_decode",
        )
        val prompts = listOf(
            NPU_PERSISTENT_HOLDER_TEN_TURN_PROMPT_1,
            NPU_PERSISTENT_HOLDER_TEN_TURN_PROMPT_2,
            NPU_PERSISTENT_HOLDER_TEN_TURN_PROMPT_3,
            NPU_PERSISTENT_HOLDER_TEN_TURN_PROMPT_4,
            NPU_PERSISTENT_HOLDER_TEN_TURN_PROMPT_5,
            NPU_PERSISTENT_HOLDER_TEN_TURN_PROMPT_6,
            NPU_PERSISTENT_HOLDER_TEN_TURN_PROMPT_7,
            NPU_PERSISTENT_HOLDER_TEN_TURN_PROMPT_8,
            NPU_PERSISTENT_HOLDER_TEN_TURN_PROMPT_9,
            NPU_PERSISTENT_HOLDER_TEN_TURN_PROMPT_10,
        )
        val state = NpuPersistentHolderTenTurnProbeState(
            status = "completed",
            reason = "success",
            createResult = NpuPersistentHolderApiResult(
                status = "created",
                reason = "app_jni_holder_lifecycle_created_without_engine_create",
                holderId = "native-holder-1",
                diagnostics = createDiagnostics,
            ),
            diagnosticsAfterCreate = createDiagnostics,
            turns = prompts.mapIndexed { index, prompt ->
                NpuPersistentHolderTwoTurnRecord(
                    turnIndex = index + 1,
                    prompt = prompt,
                    runResult = NpuPersistentHolderApiResult(
                        status = "run_ready",
                        reason = "holder_open_existing_one_shot_decode_may_run_once",
                        holderId = "native-holder-1",
                        diagnostics = runDiagnostics,
                    ),
                    decodeResult = NpuPersistentHolderRunOnceDecodeResult(
                        status = "success",
                        reason = "success",
                        runDecodeReached = "true",
                        rawOutput = "raw${index + 1}",
                        sanitizedOutput = "sanitized${index + 1}",
                        qualityClassification = "natural_japanese",
                        backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                        fallbackUsed = "false",
                        timeout = "false",
                        freshCrash = "false",
                    ),
                )
            },
            closeResult = NpuPersistentHolderApiResult(
                status = "closed",
                reason = "holder_closed_without_decode",
                holderId = "native-holder-1",
                diagnostics = closeDiagnostics,
            ),
            diagnosticsAfterClose = closeDiagnostics,
        )

        val summary = formatNpuPersistentHolderTenTurnSummaryForCopy(state)
        val fullDump = formatNpuPersistentHolderTenTurnFullDumpForCopy(state)

        assertTrue(summary.contains("test_name=NPU Persistent Holder Ten Turn Probe"))
        assertTrue(summary.contains("run_count_requested=10"))
        assertTrue(summary.contains("run_count_completed=10"))
        (1..10).forEach { index ->
            assertTrue(summary.contains("turn${index}_run_decode_reached=true"))
            assertTrue(fullDump.contains("turn_index=$index"))
        }
        assertTrue(summary.contains("run_decode_reached_count=10"))
        assertTrue(summary.contains("success_count=10"))
        assertTrue(summary.contains("failure_count=0"))
        assertTrue(summary.contains("success_rate=1.00"))
        assertTrue(summary.contains("backend_evidence_summary=QNN_HTP_V79_FastRPC_native_diag:10"))
        assertTrue(summary.contains("quality_classification_summary=natural_japanese:10"))
        assertTrue(summary.contains("fallback_used_count=0"))
        assertTrue(summary.contains("fallback_rate=0.00"))
        assertTrue(summary.contains("timeout_count=0"))
        assertTrue(summary.contains("timeout_rate=0.00"))
        assertTrue(summary.contains("fresh_crash_count=0"))
        assertTrue(summary.contains("fresh_crash_rate=0.00"))
        assertTrue(summary.contains("holder_close_succeeded=true"))
        assertTrue(summary.contains("engine_reuse_observed=unavailable"))
        assertTrue(summary.contains("true_engine_persistent_reuse=false"))
        assertTrue(summary.contains("persistent_multi_turn_possible=false"))
        assertFalse(summary.contains("engine_reuse_observed=true"))
        assertFalse(summary.contains("true_engine_persistent_reuse=true"))
        assertFalse(summary.contains("persistent_multi_turn_possible=true"))
    }

    @Test
    fun `five turn copy text reports no result before run`() {
        val state = NpuPersistentHolderFiveTurnProbeState()

        val summary = formatNpuPersistentHolderFiveTurnSummaryForCopy(state)
        val fullDump = formatNpuPersistentHolderFiveTurnFullDumpForCopy(state)

        assertTrue(summary.contains("no holder five-turn probe result available"))
        assertTrue(summary.contains("test_name=NPU Persistent Holder Five Turn Probe"))
        assertTrue(fullDump.contains("no holder five-turn probe result available"))
        assertTrue(fullDump.contains("probe_status=idle"))
        assertTrue(fullDump.contains("probe_reason=not_run"))
    }

    @Test
    fun `five turn summary includes five decode attempts and keeps persistence unproven`() {
        val createDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCreateCalled = true,
            holderCreateSucceeded = true,
            holderId = "native-holder-1",
            holderOpen = true,
            status = "created",
            reason = "app_jni_holder_lifecycle_created_without_engine_create",
        )
        val runDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeRunCalled = true,
            holderId = "native-holder-1",
            holderOpenBeforeRun = true,
            runOnceRequested = true,
            runOnceSupported = true,
            status = "run_ready",
            reason = "holder_open_existing_one_shot_decode_may_run_once",
        )
        val closeDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCloseCalled = true,
            holderCloseRequested = true,
            holderCloseSucceeded = true,
            status = "closed",
            reason = "holder_closed_without_decode",
        )
        val prompts = listOf(
            NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_1,
            NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_2,
            NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_3,
            NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_4,
            NPU_PERSISTENT_HOLDER_FIVE_TURN_PROMPT_5,
        )
        val state = NpuPersistentHolderFiveTurnProbeState(
            status = "completed",
            reason = "success",
            createResult = NpuPersistentHolderApiResult(
                status = "created",
                reason = "app_jni_holder_lifecycle_created_without_engine_create",
                holderId = "native-holder-1",
                diagnostics = createDiagnostics,
            ),
            diagnosticsAfterCreate = createDiagnostics,
            turns = prompts.mapIndexed { index, prompt ->
                NpuPersistentHolderTwoTurnRecord(
                    turnIndex = index + 1,
                    prompt = prompt,
                    runResult = NpuPersistentHolderApiResult(
                        status = "run_ready",
                        reason = "holder_open_existing_one_shot_decode_may_run_once",
                        holderId = "native-holder-1",
                        diagnostics = runDiagnostics,
                    ),
                    decodeResult = NpuPersistentHolderRunOnceDecodeResult(
                        status = "success",
                        reason = "success",
                        runDecodeReached = "true",
                        rawOutput = "raw${index + 1}",
                        sanitizedOutput = "sanitized${index + 1}",
                        qualityClassification = "natural_japanese",
                        backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                        fallbackUsed = "false",
                        timeout = "false",
                        freshCrash = "false",
                    ),
                )
            },
            closeResult = NpuPersistentHolderApiResult(
                status = "closed",
                reason = "holder_closed_without_decode",
                holderId = "native-holder-1",
                diagnostics = closeDiagnostics,
            ),
            diagnosticsAfterClose = closeDiagnostics,
        )

        val summary = formatNpuPersistentHolderFiveTurnSummaryForCopy(state)
        val fullDump = formatNpuPersistentHolderFiveTurnFullDumpForCopy(state)

        assertTrue(summary.contains("test_name=NPU Persistent Holder Five Turn Probe"))
        assertTrue(summary.contains("run_count_requested=5"))
        assertTrue(summary.contains("run_count_completed=5"))
        (1..5).forEach { index ->
            assertTrue(summary.contains("turn${index}_run_decode_reached=true"))
            assertTrue(fullDump.contains("turn_index=$index"))
        }
        assertTrue(summary.contains("run_decode_reached_count=5"))
        assertTrue(summary.contains("backend_evidence_summary=QNN_HTP_V79_FastRPC_native_diag:5"))
        assertTrue(summary.contains("quality_classification_summary=natural_japanese:5"))
        assertTrue(summary.contains("fallback_used_count=0"))
        assertTrue(summary.contains("timeout_count=0"))
        assertTrue(summary.contains("fresh_crash_count=0"))
        assertTrue(summary.contains("holder_close_succeeded=true"))
        assertTrue(summary.contains("engine_reuse_observed=unavailable"))
        assertTrue(summary.contains("persistent_multi_turn_possible=false"))
        assertFalse(summary.contains("persistent_multi_turn_possible=true"))
        assertFalse(summary.contains("engine_reuse_observed=true"))
    }

    @Test
    fun `two turn copy text reports no result before run`() {
        val state = NpuPersistentHolderTwoTurnProbeState()

        val summary = formatNpuPersistentHolderTwoTurnSummaryForCopy(state)
        val fullDump = formatNpuPersistentHolderTwoTurnFullDumpForCopy(state)

        assertTrue(summary.contains("no holder two-turn probe result available"))
        assertTrue(summary.contains("test_name=NPU Persistent Holder Two Turn Probe"))
        assertTrue(fullDump.contains("no holder two-turn probe result available"))
        assertTrue(fullDump.contains("probe_status=idle"))
        assertTrue(fullDump.contains("probe_reason=not_run"))
    }

    @Test
    fun `two turn summary includes both turns and keeps persistence unproven`() {
        val createDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCreateCalled = true,
            holderCreateSucceeded = true,
            holderId = "native-holder-1",
            holderOpen = true,
            status = "created",
            reason = "app_jni_holder_lifecycle_created_without_engine_create",
        )
        val runDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeRunCalled = true,
            holderId = "native-holder-1",
            holderOpenBeforeRun = true,
            runOnceRequested = true,
            runOnceSupported = true,
            status = "run_ready",
            reason = "holder_open_existing_one_shot_decode_may_run_once",
        )
        val closeDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCloseCalled = true,
            holderCloseRequested = true,
            holderCloseSucceeded = true,
            status = "closed",
            reason = "holder_closed_without_decode",
        )
        val state = NpuPersistentHolderTwoTurnProbeState(
            status = "completed",
            reason = "success",
            createResult = NpuPersistentHolderApiResult(
                status = "created",
                reason = "app_jni_holder_lifecycle_created_without_engine_create",
                holderId = "native-holder-1",
                diagnostics = createDiagnostics,
            ),
            diagnosticsAfterCreate = createDiagnostics,
            turns = listOf(
                NpuPersistentHolderTwoTurnRecord(
                    turnIndex = 1,
                    prompt = "こんにちは",
                    runResult = NpuPersistentHolderApiResult(
                        status = "run_ready",
                        reason = "holder_open_existing_one_shot_decode_may_run_once",
                        holderId = "native-holder-1",
                        diagnostics = runDiagnostics,
                    ),
                    decodeResult = NpuPersistentHolderRunOnceDecodeResult(
                        status = "success",
                        reason = "success",
                        runDecodeReached = "true",
                        rawOutput = "raw1",
                        sanitizedOutput = "こんにちは。",
                        qualityClassification = "natural_japanese",
                        backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                        fallbackUsed = "false",
                        timeout = "false",
                        freshCrash = "false",
                    ),
                ),
                NpuPersistentHolderTwoTurnRecord(
                    turnIndex = 2,
                    prompt = "あなたは誰ですか",
                    runResult = NpuPersistentHolderApiResult(
                        status = "run_ready",
                        reason = "holder_open_existing_one_shot_decode_may_run_once",
                        holderId = "native-holder-1",
                        diagnostics = runDiagnostics,
                    ),
                    decodeResult = NpuPersistentHolderRunOnceDecodeResult(
                        status = "success",
                        reason = "success",
                        runDecodeReached = "true",
                        rawOutput = "raw2",
                        sanitizedOutput = "アシスタントです。",
                        qualityClassification = "natural_japanese",
                        backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                        fallbackUsed = "false",
                        timeout = "false",
                        freshCrash = "false",
                    ),
                ),
            ),
            closeResult = NpuPersistentHolderApiResult(
                status = "closed",
                reason = "holder_closed_without_decode",
                holderId = "native-holder-1",
                diagnostics = closeDiagnostics,
            ),
            diagnosticsAfterClose = closeDiagnostics,
        )

        val summary = formatNpuPersistentHolderTwoTurnSummaryForCopy(state)
        val fullDump = formatNpuPersistentHolderTwoTurnFullDumpForCopy(state)

        assertTrue(summary.contains("test_name=NPU Persistent Holder Two Turn Probe"))
        assertTrue(summary.contains("run_count_requested=2"))
        assertTrue(summary.contains("run_count_completed=2"))
        assertTrue(summary.contains("turn1_run_called=true"))
        assertTrue(summary.contains("turn1_run_succeeded=true"))
        assertTrue(summary.contains("turn1_run_decode_reached=true"))
        assertTrue(summary.contains("turn2_run_called=true"))
        assertTrue(summary.contains("turn2_run_succeeded=true"))
        assertTrue(summary.contains("turn2_run_decode_reached=true"))
        assertTrue(summary.contains("run_decode_reached_count=2"))
        assertTrue(summary.contains("fallback_used_count=0"))
        assertTrue(summary.contains("timeout_count=0"))
        assertTrue(summary.contains("fresh_crash_count=0"))
        assertTrue(summary.contains("holder_close_succeeded=true"))
        assertTrue(summary.contains("engine_reuse_observed=unavailable"))
        assertTrue(summary.contains("persistent_multi_turn_possible=false"))
        assertTrue(fullDump.contains("turn_index=1"))
        assertTrue(fullDump.contains("turn_index=2"))
        assertTrue(fullDump.contains("prompt=こんにちは"))
        assertTrue(fullDump.contains("prompt=あなたは誰ですか"))
        assertFalse(summary.contains("persistent_multi_turn_possible=true"))
        assertFalse(summary.contains("engine_reuse_observed=true"))
    }

    @Test
    fun `run once copy text reports no result before run`() {
        val state = NpuPersistentHolderRunOnceProbeState()

        val summary = formatNpuPersistentHolderRunOnceSummaryForCopy(state)
        val fullDump = formatNpuPersistentHolderRunOnceFullDumpForCopy(state)

        assertTrue(summary.contains("no holder run once probe result available"))
        assertTrue(summary.contains("test_name=NPU Persistent Holder Run Once Probe"))
        assertTrue(fullDump.contains("no holder run once probe result available"))
        assertTrue(fullDump.contains("probe_status=idle"))
        assertTrue(fullDump.contains("probe_reason=not_run"))
    }

    @Test
    fun `run once summary includes decode keys and keeps multi turn unavailable`() {
        val createDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCreateCalled = true,
            holderCreateSucceeded = true,
            holderId = "native-holder-1",
            holderOpen = true,
            status = "created",
            reason = "app_jni_holder_lifecycle_created_without_engine_create",
        )
        val runDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeRunCalled = true,
            holderId = "native-holder-1",
            holderOpen = true,
            holderOpenBeforeRun = true,
            runOnceRequested = true,
            runOnceSupported = true,
            runOnceReason = "holder_open_existing_one_shot_decode_may_run_once",
            status = "run_ready",
            reason = "holder_open_existing_one_shot_decode_may_run_once",
        )
        val closeDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCloseCalled = true,
            holderId = "native-holder-1",
            holderCloseRequested = true,
            holderCloseSucceeded = true,
            status = "closed",
            reason = "holder_closed_without_decode",
        )
        val state = NpuPersistentHolderRunOnceProbeState(
            status = "completed",
            reason = "success",
            createResult = NpuPersistentHolderApiResult(
                status = "created",
                reason = "app_jni_holder_lifecycle_created_without_engine_create",
                holderId = "native-holder-1",
                diagnostics = createDiagnostics,
            ),
            diagnosticsAfterCreate = createDiagnostics,
            runResult = NpuPersistentHolderApiResult(
                status = "run_ready",
                reason = "holder_open_existing_one_shot_decode_may_run_once",
                holderId = "native-holder-1",
                diagnostics = runDiagnostics,
            ),
            decodeResult = NpuPersistentHolderRunOnceDecodeResult(
                status = "success",
                reason = "success",
                runDecodeReached = "true",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = "false",
                timeout = "false",
                freshCrash = "false",
                totalMs = "1200",
                decodeMs = "800",
                outputTokens = "4",
                tokensPerSecond = "5.0",
                finishReason = "stop",
                stopReason = "eos",
                eosDetected = "true",
            ),
            closeResult = NpuPersistentHolderApiResult(
                status = "closed",
                reason = "holder_closed_without_decode",
                holderId = "native-holder-1",
                diagnostics = closeDiagnostics,
            ),
            diagnosticsAfterClose = closeDiagnostics,
        )

        val text = formatNpuPersistentHolderRunOnceSummaryForCopy(state)

        assertTrue(text.contains("test_name=NPU Persistent Holder Run Once Probe"))
        assertTrue(text.contains("holder_create_succeeded=true"))
        assertTrue(text.contains("holder_open_before_run=true"))
        assertTrue(text.contains("run_once_requested=true"))
        assertTrue(text.contains("run_once_called=true"))
        assertTrue(text.contains("run_once_supported=true"))
        assertTrue(text.contains("run_once_succeeded=true"))
        assertTrue(text.contains("run_decode_reached=true"))
        assertTrue(text.contains("quality_classification=natural_japanese"))
        assertTrue(text.contains("backend_evidence=QNN_HTP_V79_FastRPC_native_diag"))
        assertTrue(text.contains("fallback_used=false"))
        assertTrue(text.contains("timeout=false"))
        assertTrue(text.contains("fresh_crash=false"))
        assertTrue(text.contains("holder_close_succeeded=true"))
        assertTrue(text.contains("engine_reuse_observed=unavailable"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
        assertFalse(text.contains("persistent_multi_turn_possible=true"))
        assertFalse(text.contains("engine_reuse_observed=true"))
    }

    @Test
    fun `run once unsupported summary remains blocked`() {
        val runDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeRunCalled = true,
            holderId = "native-holder-1",
            runOnceRequested = true,
            runOnceSupported = false,
            runOnceReason = "needs_native_adapter_work",
            status = "blocked",
            reason = "needs_native_adapter_work",
        )
        val state = NpuPersistentHolderRunOnceProbeState(
            status = "completed",
            reason = "needs_native_adapter_work",
            runResult = NpuPersistentHolderApiResult(
                status = "blocked",
                reason = "needs_native_adapter_work",
                holderId = "native-holder-1",
                diagnostics = runDiagnostics,
            ),
            closeResult = NpuPersistentHolderApiResult(
                status = "closed_noop",
                reason = "holder_already_closed",
                holderId = "native-holder-1",
                diagnostics = npuPersistentHolderNativeStubDiagnostics(nativeCloseCalled = true),
            ),
        )

        val text = formatNpuPersistentHolderRunOnceSummaryForCopy(state)

        assertTrue(text.contains("run_once_requested=true"))
        assertTrue(text.contains("run_once_called=true"))
        assertTrue(text.contains("run_once_supported=false"))
        assertTrue(text.contains("run_once_succeeded=false"))
        assertTrue(text.contains("run_once_reason=needs_native_adapter_work"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
    }

    @Test
    fun `create close copy text reports no result before run`() {
        val state = NpuPersistentHolderCreateCloseProbeState()

        val summary = formatNpuPersistentHolderCreateCloseSummaryForCopy(state)
        val fullDump = formatNpuPersistentHolderCreateCloseFullDumpForCopy(state)

        assertTrue(summary.contains("no holder create/close probe result available"))
        assertTrue(summary.contains("test_name=NPU Persistent Holder Create Close Probe"))
        assertTrue(fullDump.contains("no holder create/close probe result available"))
        assertTrue(fullDump.contains("probe_status=idle"))
        assertTrue(fullDump.contains("probe_reason=not_run"))
    }

    @Test
    fun `create close full dump includes lifecycle results without run once`() {
        val createDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCreateCalled = true,
            holderCreateSucceeded = true,
            holderId = "native-holder-1",
            holderOpen = true,
            status = "created",
            reason = "app_jni_holder_lifecycle_created_without_engine_create",
        )
        val closeDiagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCreateCalled = true,
            nativeCloseCalled = true,
            nativeDiagnosticsCalled = true,
            holderCreateSucceeded = true,
            holderId = "native-holder-1",
            holderOpen = false,
            holderCloseRequested = true,
            holderCloseSucceeded = true,
            holderDoubleCloseSafe = true,
            status = "closed",
            reason = "holder_closed_without_decode",
        )
        val state = NpuPersistentHolderCreateCloseProbeState(
            status = "completed",
            reason = "holder_closed_without_decode",
            modelPathOrReason = "/models/gemma.task",
            createResult = NpuPersistentHolderApiResult(
                status = "created",
                reason = "app_jni_holder_lifecycle_created_without_engine_create",
                holderId = "native-holder-1",
                diagnostics = createDiagnostics,
                nativeSummary = formatNpuPersistentHolderNativeStubProbeSummary(createDiagnostics),
            ),
            diagnosticsAfterCreate = createDiagnostics,
            closeResult = NpuPersistentHolderApiResult(
                status = "closed",
                reason = "holder_closed_without_decode",
                holderId = "native-holder-1",
                diagnostics = closeDiagnostics,
                nativeSummary = formatNpuPersistentHolderNativeStubProbeSummary(closeDiagnostics),
            ),
            diagnosticsAfterClose = closeDiagnostics,
            secondCloseResult = NpuPersistentHolderApiResult(
                status = "closed",
                reason = "holder_already_closed_double_close_safe",
                holderId = "native-holder-1",
                diagnostics = closeDiagnostics,
                nativeSummary = formatNpuPersistentHolderNativeStubProbeSummary(closeDiagnostics),
            ),
            diagnosticsAfterSecondClose = closeDiagnostics,
        )

        val text = formatNpuPersistentHolderCreateCloseFullDumpForCopy(state)

        assertTrue(text.contains("[create_result]"))
        assertTrue(text.contains("[close_result]"))
        assertTrue(text.contains("[second_close_result]"))
        assertTrue(text.contains("holder_create_called=true"))
        assertTrue(text.contains("holder_close_called=true"))
        assertTrue(text.contains("holder_double_close_safe=true"))
        assertTrue(text.contains("native_run_called=false"))
        assertTrue(text.contains("npu_decode_called=false"))
        assertTrue(text.contains("generate_called=false"))
        assertTrue(text.contains("qnn_decode_called=false"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
        assertFalse(text.contains("persistent_multi_turn_possible=true"))
    }

    @Test
    fun `native holder create close summary reports lifecycle without decode`() {
        val diagnostics = npuPersistentHolderNativeStubDiagnostics(
            nativeCreateCalled = true,
            nativeCloseCalled = true,
            nativeDiagnosticsCalled = true,
            holderCreateRequested = true,
            holderCreateSucceeded = true,
            holderId = "native-holder-1",
            holderOpen = false,
            holderCloseRequested = true,
            holderCloseSucceeded = true,
            holderDoubleCloseSafe = true,
            status = "closed",
            reason = "holder_closed_without_decode",
        )
        val text = formatNpuPersistentHolderNativeStubProbeSummary(diagnostics)

        assertTrue(text.contains("test_name=NPU Persistent Holder Create Close Probe"))
        assertTrue(text.contains("holder_api_available=true"))
        assertTrue(text.contains("native_holder_stub_available=true"))
        assertTrue(text.contains("native_holder_create_close_available=true"))
        assertTrue(text.contains("native_holder_stub_version=dev_only_standard_route_adapter_holder_create_close_v1"))
        assertTrue(text.contains("native_create_declared=true"))
        assertTrue(text.contains("native_run_declared=true"))
        assertTrue(text.contains("native_close_declared=true"))
        assertTrue(text.contains("native_diagnostics_declared=true"))
        assertTrue(text.contains("native_create_called=true"))
        assertTrue(text.contains("native_run_called=false"))
        assertTrue(text.contains("native_close_called=true"))
        assertTrue(text.contains("native_diagnostics_called=true"))
        assertTrue(text.contains("holder_create_requested=true"))
        assertTrue(text.contains("holder_create_called=true"))
        assertTrue(text.contains("holder_create_succeeded=true"))
        assertTrue(text.contains("holder_id=native-holder-1"))
        assertTrue(text.contains("holder_open=false"))
        assertTrue(text.contains("holder_close_requested=true"))
        assertTrue(text.contains("holder_close_called=true"))
        assertTrue(text.contains("holder_close_succeeded=true"))
        assertTrue(text.contains("holder_double_close_safe=true"))
        assertTrue(text.contains("holder_fatal_latch=false"))
        assertTrue(text.contains("engine_factory_create_called=false"))
        assertTrue(text.contains("engine_create_called=false"))
        assertTrue(text.contains("model_assets_create_called=false"))
        assertTrue(text.contains("engine_settings_create_called=false"))
        assertTrue(text.contains("npu_decode_called=false"))
        assertTrue(text.contains("generate_called=false"))
        assertTrue(text.contains("qnn_decode_called=false"))
        assertTrue(text.contains("qnn_called=false"))
        assertTrue(text.contains("run_once_supported=false"))
        assertTrue(text.contains("status=closed"))
        assertTrue(text.contains("reason=holder_closed_without_decode"))
        assertTrue(text.contains("persistent_multi_turn_possible=false"))
        assertTrue(text.contains("restart_app_recommended=false"))
        assertTrue(text.contains("recommended_next_step=review_create_close_device_result_then_implement_run_once_without_multi_turn"))
        assertFalse(text.contains("persistent_multi_turn_possible=true"))
        assertFalse(text.contains("engine_reuse_observed=true"))
    }

    @Test
    fun `native holder create parser keeps engine and qnn calls false`() {
        val result = parseNpuPersistentHolderNativeStubResult(
            nativeSummary = """
                test_name=NPU Persistent Holder Create Close Probe
                holder_api_available=true
                native_holder_stub_available=true
                native_holder_create_close_available=true
                native_holder_stub_version=dev_only_standard_route_adapter_holder_create_close_v1
                native_create_declared=true
                native_run_declared=true
                native_close_declared=true
                native_diagnostics_declared=true
                holder_create_requested=true
                holder_create_called=true
                holder_create_succeeded=true
                holder_id=native-holder-1
                holder_open=true
                holder_close_requested=false
                holder_close_called=false
                holder_close_succeeded=false
                holder_double_close_safe=true
                holder_fatal_latch=false
                holder_fatal_reason=none
                native_create_called=true
                native_run_called=false
                native_close_called=false
                native_diagnostics_called=false
                engine_factory_create_called=false
                engine_create_called=false
                model_assets_create_called=false
                engine_settings_create_called=false
                npu_decode_called=false
                generate_called=false
                qnn_decode_called=false
                qnn_called=false
                run_once_supported=false
                status=created
                reason=app_jni_holder_lifecycle_created_without_engine_create
                persistent_multi_turn_possible=false
                restart_app_recommended=false
                recommended_next_step=review_create_close_device_result_then_implement_run_once_without_multi_turn
            """.trimIndent(),
        )

        assertEquals("created", result.status)
        assertEquals("app_jni_holder_lifecycle_created_without_engine_create", result.reason)
        assertEquals("native-holder-1", result.holderId)
        assertTrue(result.diagnostics.nativeHolderStubAvailable)
        assertTrue(result.diagnostics.nativeHolderCreateCloseAvailable)
        assertTrue(result.diagnostics.nativeCreateDeclared)
        assertTrue(result.diagnostics.nativeRunDeclared)
        assertTrue(result.diagnostics.nativeCloseDeclared)
        assertTrue(result.diagnostics.nativeDiagnosticsDeclared)
        assertTrue(result.diagnostics.nativeCreateCalled)
        assertTrue(result.diagnostics.holderCreateRequested)
        assertTrue(result.diagnostics.holderCreateCalled)
        assertTrue(result.diagnostics.holderCreateSucceeded)
        assertTrue(result.diagnostics.holderOpen)
        assertTrue(result.diagnostics.holderDoubleCloseSafe)
        assertFalse(result.diagnostics.nativeRunCalled)
        assertFalse(result.diagnostics.nativeCloseCalled)
        assertFalse(result.diagnostics.nativeDiagnosticsCalled)
        assertFalse(result.diagnostics.engineFactoryCreateCalled)
        assertFalse(result.diagnostics.engineCreateCalled)
        assertFalse(result.diagnostics.modelAssetsCreateCalled)
        assertFalse(result.diagnostics.engineSettingsCreateCalled)
        assertFalse(result.diagnostics.npuDecodeCalled)
        assertFalse(result.diagnostics.generateCalled)
        assertFalse(result.diagnostics.qnnDecodeCalled)
        assertFalse(result.diagnostics.qnnCalled)
        assertFalse(result.diagnostics.runOnceSupported)
        assertFalse(result.diagnostics.persistentMultiTurnPossible)
    }

    @Test
    fun `native holder run once gate is supported without native decode`() {
        val result = parseNpuPersistentHolderNativeStubResult(
            nativeSummary = """
                native_holder_create_close_available=true
                native_run_called=true
                holder_id=native-holder-1
                holder_open_before_run=true
                run_once_requested=true
                npu_decode_called=false
                generate_called=false
                qnn_decode_called=false
                run_once_supported=true
                status=run_ready
                reason=holder_open_existing_one_shot_decode_may_run_once
                persistent_multi_turn_possible=false
            """.trimIndent(),
        )

        assertEquals("run_ready", result.status)
        assertEquals("holder_open_existing_one_shot_decode_may_run_once", result.reason)
        assertTrue(result.diagnostics.nativeRunCalled)
        assertTrue(result.diagnostics.holderOpenBeforeRun)
        assertTrue(result.diagnostics.runOnceRequested)
        assertTrue(result.diagnostics.runOnceSupported)
        assertFalse(result.diagnostics.npuDecodeCalled)
        assertFalse(result.diagnostics.generateCalled)
        assertFalse(result.diagnostics.qnnDecodeCalled)
        assertFalse(result.diagnostics.persistentMultiTurnPossible)
    }

    @Test
    fun `native holder fatal latch recommends app restart`() {
        val result = parseNpuPersistentHolderNativeStubResult(
            nativeSummary = """
                holder_fatal_latch=true
                holder_fatal_reason=invalid_model_path
                restart_app_recommended=true
                status=failed
                reason=invalid_model_path
                npu_decode_called=false
                generate_called=false
                qnn_decode_called=false
                persistent_multi_turn_possible=false
            """.trimIndent(),
        )

        assertEquals("failed", result.status)
        assertTrue(result.diagnostics.holderFatalLatch)
        assertEquals("invalid_model_path", result.diagnostics.holderFatalReason)
        assertTrue(result.diagnostics.restartAppRecommended)
        assertFalse(result.diagnostics.persistentMultiTurnPossible)
    }

    @Test
    fun `native holder create close diagnostics merge called flags without enabling persistence`() {
        val diagnostics = mergeNpuPersistentHolderNativeStubDiagnostics(
            listOf(
                npuPersistentHolderNativeStubDiagnostics(
                    nativeCreateCalled = true,
                    holderCreateSucceeded = true,
                    holderId = "native-holder-1",
                    holderOpen = true,
                ),
                npuPersistentHolderNativeStubDiagnostics(nativeRunCalled = true),
                npuPersistentHolderNativeStubDiagnostics(
                    nativeCloseCalled = true,
                    holderCloseSucceeded = true,
                    holderOpen = false,
                ),
                npuPersistentHolderNativeStubDiagnostics(nativeDiagnosticsCalled = true),
            ),
        )

        assertTrue(diagnostics.nativeCreateCalled)
        assertTrue(diagnostics.nativeRunCalled)
        assertTrue(diagnostics.nativeCloseCalled)
        assertTrue(diagnostics.nativeDiagnosticsCalled)
        assertTrue(diagnostics.holderCreateSucceeded)
        assertTrue(diagnostics.holderCloseSucceeded)
        assertTrue(diagnostics.holderDoubleCloseSafe)
        assertFalse(diagnostics.holderOpen)
        assertFalse(diagnostics.engineFactoryCreateCalled)
        assertFalse(diagnostics.engineCreateCalled)
        assertFalse(diagnostics.npuDecodeCalled)
        assertFalse(diagnostics.generateCalled)
        assertFalse(diagnostics.qnnDecodeCalled)
        assertFalse(diagnostics.qnnCalled)
        assertEquals("not_implemented", diagnostics.status)
        assertFalse(diagnostics.persistentMultiTurnPossible)
    }
}
