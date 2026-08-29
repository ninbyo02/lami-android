package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.Qairt244NpuOutputSanitizer
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuS1RepeatedRunDiagnosticsTest {
    @Test
    fun `repeated run state can represent all statuses`() {
        listOf(
            NPU_S1_REPEATED_RUN_STATUS_IDLE,
            NPU_S1_REPEATED_RUN_STATUS_RUNNING,
            NPU_S1_REPEATED_RUN_STATUS_COMPLETED,
            NPU_S1_REPEATED_RUN_STATUS_CANCELLED,
            NPU_S1_REPEATED_RUN_STATUS_STOPPED,
        ).forEach { status ->
            val text = formatNpuS1RepeatedRunDiagnosticsForDev(
                NpuS1RepeatedRunState(status = status),
            )

            assertTrue(text.contains("repeated_run_status=$status"))
        }
    }

    @Test
    fun `twenty successful records build repeated run summary`() {
        val records = (1..20).map { index ->
            record(
                runIndex = index,
                totalMs = 100L + index,
                tokensPerSecond = 2.0 + index,
                memoryRecovery5sTotalPssMb = 240L + index,
                memoryRecovery5sNativeHeapPssMb = 60L + index,
                memoryRecovery5sSystemAvailableMemoryMb = 1000L - index,
            )
        }
        val summary = buildNpuS1RepeatedRunSummary(
            NpuS1RepeatedRunState(
                status = NPU_S1_REPEATED_RUN_STATUS_COMPLETED,
                repeatedRunMode = NpuS1RepeatedRunMode.RECREATE,
                records = records,
            ),
        )

        assertEquals(NpuS1RepeatedRunMode.RECREATE, summary.repeatedRunMode)
        assertEquals(20, summary.runCountCompleted)
        assertEquals(20, summary.successCount)
        assertEquals(101L, summary.minTotalMs)
        assertEquals(120L, summary.maxTotalMs)
        assertEquals(110L, summary.avgTotalMs)
        assertEquals(3.0, summary.minTokensPerSecond!!, 0.001)
        assertEquals(22.0, summary.maxTokensPerSecond!!, 0.001)
        assertEquals(12.5, summary.avgTokensPerSecond!!, 0.001)
        assertEquals(241L, summary.first5sTotalPssMb)
        assertEquals(260L, summary.last5sTotalPssMb)
        assertEquals(260L, summary.peak5sTotalPssMb)
        assertEquals(61L, summary.first5sNativeHeapPssMb)
        assertEquals(80L, summary.last5sNativeHeapPssMb)
        assertEquals(80L, summary.peak5sNativeHeapPssMb)
        assertEquals(999L, summary.first5sSystemAvailableMemoryMb)
        assertEquals(980L, summary.last5sSystemAvailableMemoryMb)
        assertFalse(summary.memoryGrowthSuspected)
    }

    @Test
    fun `fallback low memory and danger conditions produce safety stop reasons`() {
        assertEquals("fallback_detected", repeatedRunSafetyStopReason(record(fallbackUsed = true)))
        assertEquals("low_memory", repeatedRunSafetyStopReason(record(memoryRecovery5sLowMemory = true)))
        assertEquals("low_memory_before", repeatedRunSafetyStopReason(record(memoryBeforeLowMemory = true)))
        assertEquals("low_memory_after", repeatedRunSafetyStopReason(record(memoryAfterLowMemory = true)))
        assertEquals("fresh_crash_detected", repeatedRunSafetyStopReason(record(freshCrash = true)))
        assertEquals("timeout", repeatedRunSafetyStopReason(record(timeout = true)))
        assertEquals("safety_guard_triggered", repeatedRunSafetyStopReason(record(safetyGuardTriggered = true)))
        assertEquals(
            "adapter_failure",
            repeatedRunSafetyStopReason(
                record(
                    status = "failure",
                    reason = "adapter_failure:LiteRtLmJniException",
                    runDecodeReached = false,
                ),
            ),
        )
        assertEquals(
            "engine_recreate_failure",
            repeatedRunSafetyStopReason(record(recreateResultAfterRun = "failed")),
        )
        assertEquals("run_decode_reached_false", repeatedRunSafetyStopReason(record(runDecodeReached = false)))
        assertEquals("status_failure", repeatedRunSafetyStopReason(record(status = "failure")))
        assertEquals("run_too_long", repeatedRunSafetyStopReason(record(totalMs = 30_001L)))
    }

    @Test
    fun `all identical outputs are summarized as same output`() {
        val summary = buildNpuS1RepeatedRunSummary(
            NpuS1RepeatedRunState(records = (1..20).map { record(runIndex = it) }),
        )

        assertEquals(1, summary.uniqueOutputsCount)
        assertEquals("こんにちは。", summary.mostCommonActualDisplayText)
        assertTrue(summary.allOutputsSame)
    }

    @Test
    fun `copy formatter keeps successful repeated run compact without per-run details`() {
        val state = NpuS1RepeatedRunState(
            status = NPU_S1_REPEATED_RUN_STATUS_COMPLETED,
            startedAtMs = 1_000L,
            startedAtElapsedRealtimeMs = 10_000L,
            finishedAtMs = 2_000L,
            finishedAtElapsedRealtimeMs = 11_000L,
            records = listOf(record(runIndex = 1)),
        )
        val text = appendNpuS1RepeatedRunDiagnosticsForDev(
            text = "base=true",
            state = state,
        )

        assertTrue(text.contains("base=true"))
        assertTrue(text.contains("[DEV診断: NPU S1 repeated run summary]"))
        assertFalse(text.contains("[DEV診断: NPU S1 repeated run details]"))
        assertTrue(text.contains("repeated_run_status=completed"))
        assertTrue(text.contains("repeated_run_mode=reuse"))
        assertTrue(text.contains("repeated_run_wait_ms=0"))
        assertTrue(text.contains("total_wait_time_ms=0"))
        assertTrue(text.contains("recreate_api_note=s1_direct_runner_engine_session_dispose_not_exposed_uses_safe_holder_recreate_api"))
        assertFalse(text.contains("run_index=1"))
        assertTrue(text.contains("peak_5s_total_pss_mb=250"))
        assertTrue(text.contains("peak_5s_native_heap_pss_mb=70"))
        assertTrue(text.contains("process_pid="))
        assertTrue(text.contains("repeated_run_started_at_wall_time_ms=1000"))
        assertTrue(text.contains("repeated_run_started_at_elapsed_realtime_ms=10000"))
        assertTrue(text.contains("repeated_run_finished_at_wall_time_ms=2000"))
        assertTrue(text.contains("repeated_run_finished_at_elapsed_realtime_ms=11000"))
        assertTrue(text.contains("first_failure_run_index=unavailable"))
        assertTrue(text.contains("tombstone_compare_hint=compare_first_failure_wall_time_ms_with_adb_shell_ls_lt_data_tombstones_and_dumpsys_dropbox"))
        assertTrue(text.contains("engine_request_count=1"))
        assertTrue(text.contains("engine_request_success_count=1"))
        assertTrue(text.contains("engine_request_failure_count=0"))
        assertTrue(text.contains("engine_create_attempt_count=unavailable"))
        assertTrue(text.contains("engine_create_visibility=not_exposed"))
        assertTrue(text.contains("engine_create_source=not_exposed"))
        assertTrue(text.contains("adapter_call_count=1"))
        assertTrue(text.contains("adapter_success_count=1"))
        assertTrue(text.contains("adapter_failure_count=0"))
        assertTrue(text.contains("decode_attempt_count=1"))
        assertTrue(text.contains("decode_success_count=1"))
        assertTrue(text.contains("decode_failure_count=0"))
        assertTrue(text.contains("first_failure_counter_snapshot=unavailable"))
        assertTrue(text.contains("counter_note=counters_are_app_layer_attempts_engine_create_may_be_unavailable_if_not_exposed"))
        assertTrue(text.contains("failure_count=0"))
        assertTrue(text.contains("engine_create_failed_count=0"))
        assertTrue(text.contains("quality_fail_count=0"))
        assertTrue(text.contains("most_common_actual_display_text=こんにちは。"))
        assertTrue(text.contains("most_common_tts_text=こんにちは。"))
        assertFalse(text.contains("NPU memory"))
    }

    @Test
    fun `stability summary exposes NPU Beta aggregate keys`() {
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(
            NpuS1RepeatedRunState(
                status = NPU_S1_REPEATED_RUN_STATUS_STOPPED,
                requestedRunCount = 10,
                repeatedRunMode = NpuS1RepeatedRunMode.RECREATE,
                records = listOf(
                    record(
                        runIndex = 1,
                        totalMs = 100L,
                        tokensPerSecond = 2.0,
                        repeatedRunMode = NpuS1RepeatedRunMode.RECREATE,
                        runCount = 10,
                    ),
                    record(
                        runIndex = 2,
                        totalMs = 200L,
                        tokensPerSecond = 4.0,
                        status = "failure",
                        reason = "timeout",
                        fallbackUsed = true,
                        timeout = true,
                        freshCrash = true,
                        runDecodeReached = false,
                        repeatedRunMode = NpuS1RepeatedRunMode.RECREATE,
                        runCount = 10,
                        qualityClassification = "template_artifact",
                    ),
                ),
            ),
        )

        assertTrue(text.contains("test_name=NPU Beta Stability Test"))
        assertTrue(text.contains("mode=safe_recreate"))
        assertTrue(text.contains("requested_runs=10"))
        assertTrue(text.contains("completed_runs=2"))
        assertTrue(text.contains("success_count=1"))
        assertTrue(text.contains("failed_count=1"))
        assertTrue(text.contains("success_rate=0.50"))
        assertTrue(text.contains("fallback_used_count=1"))
        assertTrue(text.contains("fallback_rate=0.50"))
        assertTrue(text.contains("timeout_count=1"))
        assertTrue(text.contains("timeout_rate=0.50"))
        assertTrue(text.contains("fresh_crash_count=1"))
        assertTrue(text.contains("fresh_crash_rate=0.50"))
        assertTrue(text.contains("run_decode_reached_count=1"))
        assertTrue(text.contains("run_decode_reached_rate=0.50"))
        assertTrue(text.contains("average_total_ms=150"))
        assertTrue(text.contains("average_decode_ms=149"))
        assertTrue(text.contains("average_tokens_per_second=3.00"))
        assertTrue(text.contains("first_failure_reason=timeout"))
        assertTrue(text.contains("backend_evidence_summary=QNN_HTP_V79_FastRPC_native_diag:2"))
        assertTrue(text.contains("quality_classification_summary=natural_japanese:1,template_artifact:1"))
    }

    @Test
    fun `stability summary keeps unavailable values unavailable when records are missing`() {
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(
            NpuS1RepeatedRunState(
                requestedRunCount = 10,
                repeatedRunMode = NpuS1RepeatedRunMode.RECREATE,
            ),
        )

        assertTrue(text.contains("test_name=NPU Beta Stability Test"))
        assertTrue(text.contains("requested_runs=10"))
        assertTrue(text.contains("completed_runs=0"))
        assertTrue(text.contains("success_rate=unavailable"))
        assertTrue(text.contains("fallback_rate=unavailable"))
        assertTrue(text.contains("timeout_rate=unavailable"))
        assertTrue(text.contains("fresh_crash_rate=unavailable"))
        assertTrue(text.contains("run_decode_reached_count=0"))
        assertTrue(text.contains("run_decode_reached_rate=unavailable"))
        assertTrue(text.contains("average_total_ms=unavailable"))
        assertTrue(text.contains("average_decode_ms=unavailable"))
        assertTrue(text.contains("average_tokens_per_second=unavailable"))
        assertTrue(text.contains("backend_evidence_summary=unavailable"))
        assertTrue(text.contains("quality_classification_summary=unavailable"))
    }

    @Test
    fun `copy formatter includes first failure time stage and inferred adapter exception`() {
        val failureRecord = record(
            runIndex = 7,
            status = "failure",
            reason = "adapter_failure:LiteRtLmJniException",
            runDecodeReached = false,
        ).copy(
            processPid = 12345,
            processName = "io.github.ninbyo02.lami",
            threadName = "main",
            runStartedAtWallTimeMs = 100_000L,
            runStartedAtElapsedRealtimeMs = 200_000L,
            runFinishedAtWallTimeMs = 101_000L,
            runFinishedAtElapsedRealtimeMs = 201_000L,
            runDurationWallMs = 1_000L,
            engineRequestStartedAtElapsedRealtimeMs = 200_010L,
            failureDetectedAtWallTimeMs = 100_500L,
            failureDetectedAtElapsedRealtimeMs = 200_500L,
            failureExceptionClass = inferNpuS1FailureExceptionClass("adapter_failure:LiteRtLmJniException"),
            failureExceptionMessage = "adapter_failure:LiteRtLmJniException",
            failureExceptionSource = npuS1FailureExceptionSource(
                reason = "adapter_failure:LiteRtLmJniException",
                exceptionClass = "LiteRtLmJniException",
            ),
            failureStage = inferNpuS1FailureStage(
                status = "failure",
                reason = "adapter_failure:LiteRtLmJniException",
                runDecodeReached = false,
                timeout = false,
            ),
        )
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(
            NpuS1RepeatedRunState(
                status = NPU_S1_REPEATED_RUN_STATUS_STOPPED,
                startedAtMs = 90_000L,
                startedAtElapsedRealtimeMs = 190_000L,
                finishedAtMs = 102_000L,
                finishedAtElapsedRealtimeMs = 202_000L,
                records = (1..6).map { record(runIndex = it) } + failureRecord,
                stopped = true,
                stopReason = "adapter_failure",
            ),
        )

        assertTrue(text.contains("first_failure_run_index=7"))
        assertTrue(text.contains("engine_request_count=7"))
        assertTrue(text.contains("engine_request_success_count=6"))
        assertTrue(text.contains("engine_request_failure_count=1"))
        assertTrue(text.contains("adapter_call_count=7"))
        assertTrue(text.contains("adapter_success_count=6"))
        assertTrue(text.contains("adapter_failure_count=1"))
        assertTrue(text.contains("decode_attempt_count=6"))
        assertTrue(text.contains("decode_success_count=6"))
        assertTrue(text.contains("decode_failure_count=0"))
        assertTrue(text.contains("first_failure_counter_snapshot=engine_request=7,adapter_call=7,decode_attempt=6,adapter_failure=1,decode_success=6"))
        assertTrue(text.contains("first_failure_wall_time_ms=100500"))
        assertTrue(text.contains("first_failure_elapsed_realtime_ms=200500"))
        assertTrue(text.contains("first_failure_stage=adapter"))
        assertTrue(text.contains("first_failure_reason=adapter_failure:LiteRtLmJniException"))
        assertTrue(text.contains("first_failure_exception_class=LiteRtLmJniException"))
        assertTrue(text.contains("failure_after_n_successes=6"))
        assertTrue(text.contains("failure_after_n_adapter_calls=7"))
        assertTrue(text.contains("failure_after_n_decode_successes=6"))
        assertTrue(text.contains("failure_after_total_wait_ms=0"))
        assertTrue(text.contains("failure_pattern_hint=adapter_failure_after_6_successful_decodes"))
        assertTrue(text.contains("process_pid=12345"))
        assertTrue(text.contains("process_name=io.github.ninbyo02.lami"))
        assertTrue(text.contains("thread_name=main"))
        assertTrue(text.contains("failure_detected_at_wall_time_ms=100500"))
        assertTrue(text.contains("failure_detected_at_elapsed_realtime_ms=200500"))
        assertTrue(text.contains("failure_exception_class=LiteRtLmJniException"))
        assertTrue(text.contains("failure_exception_source=reason_string_inferred"))
        assertTrue(text.contains("failure_stage=adapter"))
        assertTrue(text.contains("engine_request_count_at_run=7"))
        assertTrue(text.contains("engine_request_success_count_at_run=6"))
        assertTrue(text.contains("engine_request_failure_count_at_run=1"))
        assertTrue(text.contains("engine_create_attempt_count_at_run=unavailable"))
        assertTrue(text.contains("adapter_call_count_at_run=7"))
        assertTrue(text.contains("adapter_failure_count_at_run=1"))
        assertTrue(text.contains("decode_attempt_count_at_run=6"))
        assertTrue(text.contains("decode_success_count_at_run=6"))
        assertTrue(text.contains("decode_failure_count_at_run=0"))
        assertTrue(text.contains("failure_counter_snapshot=engine_request=7,adapter_call=7,decode_attempt=6,adapter_failure=1,decode_success=6"))
        assertTrue(text.contains("failure_after_engine_request_count=7"))
        assertTrue(text.contains("failure_after_adapter_call_count=7"))
        assertTrue(text.contains("failure_after_decode_attempt_count=6"))
    }

    @Test
    fun `reuse wait mode reports completed wait before first failure`() {
        val successfulRecords = (1..6).map { index ->
            record(
                runIndex = index,
                repeatedRunMode = NpuS1RepeatedRunMode.REUSE_10S,
                waitAfterRunMs = 10_000L,
                waitStartedAtElapsedRealtimeMs = 300_000L + index,
                waitFinishedAtElapsedRealtimeMs = 310_000L + index,
            )
        }
        val failureRecord = record(
            runIndex = 7,
            repeatedRunMode = NpuS1RepeatedRunMode.REUSE_10S,
            status = "failure",
            reason = "adapter_failure:LiteRtLmJniException",
            runDecodeReached = false,
            waitAfterRunMs = 0L,
        )
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(
            NpuS1RepeatedRunState(
                status = NPU_S1_REPEATED_RUN_STATUS_STOPPED,
                repeatedRunMode = NpuS1RepeatedRunMode.REUSE_10S,
                records = successfulRecords + failureRecord,
                stopped = true,
                stopReason = "adapter_failure",
            ),
        )

        assertTrue(text.contains("repeated_run_mode=reuse_10s"))
        assertTrue(text.contains("repeated_run_wait_ms=10000"))
        assertTrue(text.contains("total_wait_time_ms=60000"))
        assertTrue(text.contains("failure_after_total_wait_ms=60000"))
        assertTrue(text.contains("wait_after_run_ms=0"))
        assertFalse(text.contains("wait_started_at_elapsed_realtime_ms=300001"))
        assertFalse(text.contains("wait_finished_at_elapsed_realtime_ms=310001"))
    }

    @Test
    fun `copy formatter includes native stage diagnostics and inferred native adapter exception`() {
        val failureRecord = record(
            runIndex = 7,
            status = "failure",
            reason = "adapter_failure:LiteRtLmJniException",
            runDecodeReached = false,
            nativeDiagnostics = NpuS1NativeStageDiagnostics(
                nativeRunId = "chat-real-123",
                nativeStage = NPU_S1_NATIVE_STAGE_ADAPTER_FAILURE,
                nativeStageHistory = "provider_start>adapter_start>before_native_call>native_call>adapter_failure>provider_failure",
                nativeCallStartedAtElapsedRealtimeMs = "5000",
                nativeCallFinishedAtElapsedRealtimeMs = "5100",
                nativeCallDurationMs = "100",
                nativeCallReached = "true",
                nativeCallReturned = "false",
                nativeDecodeStarted = "unavailable",
                nativeDecodeFinished = "unavailable",
                nativeCleanupReached = "unavailable",
                nativeSessionDestroyReached = "unavailable",
                nativeResultAvailable = "true",
                nativeResultTail = "result=failure\\nreasonCode=adapter_failure",
                nativeDiagAvailable = "true",
                nativeDiagTail = "QNN failure tail",
                nativeErrorClass = "LiteRtLmJniException",
                nativeErrorMessage = "native create failed",
                nativeErrorStage = NPU_S1_NATIVE_STAGE_NATIVE_CALL,
                nativeErrorSource = "throwable",
            ),
        )
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(
            NpuS1RepeatedRunState(
                status = NPU_S1_REPEATED_RUN_STATUS_STOPPED,
                records = (1..6).map { record(runIndex = it) } + failureRecord,
                stopped = true,
                stopReason = "adapter_failure",
            ),
        )

        assertTrue(text.contains("first_failure_native_stage=adapter_failure"))
        assertTrue(text.contains("first_failure_native_error_stage=native_call"))
        assertTrue(text.contains("first_failure_native_error_class=LiteRtLmJniException"))
        assertTrue(text.contains("first_failure_native_error_source=throwable"))
        assertTrue(text.contains("first_failure_native_stage_history=provider_start>adapter_start>before_native_call>native_call>adapter_failure>provider_failure"))
        assertTrue(text.contains("first_failure_native_diag_tail=QNN failure tail"))
        assertTrue(text.contains("native_run_id=chat-real-123"))
        assertTrue(text.contains("native_stage_history=provider_start>adapter_start>before_native_call>native_call>adapter_failure>provider_failure"))
        assertTrue(text.contains("native_call_reached=true"))
        assertTrue(text.contains("native_call_returned=false"))
        assertTrue(text.contains("native_cleanup_reached=unavailable"))
        assertTrue(text.contains("native_session_destroy_reached=unavailable"))
        assertTrue(text.contains("native_error_class=LiteRtLmJniException"))
        assertTrue(text.contains("native_error_stage=native_call"))
    }

    @Test
    fun `short output telemetry states tokenizer counts are unavailable when not exposed`() {
        val telemetry = buildNpuS1ShortOutputTelemetry(
            input = "こんにちは",
            result = result(outputTokens = 6),
        )
        val text = formatNpuS1ShortOutputTelemetryForDev(telemetry)

        assertTrue(text.contains("[DEV診断: NPU S1 short output telemetry]"))
        assertTrue(text.contains("finish_reason=unavailable"))
        assertTrue(text.contains("stop_reason=unavailable"))
        assertTrue(text.contains("eos_detected=unavailable"))
        assertTrue(text.contains("tokenizer_output_tokens=unavailable"))
        assertTrue(text.contains("tokenizer_input_tokens=unavailable"))
        assertTrue(text.contains("output_token_count_source=estimated_code_points_not_tokenizer"))
        assertTrue(text.contains("prompt_token_count_source=code_points"))
        assertTrue(text.contains("final_input_tail_preview=こんにちは"))
        assertTrue(text.contains("max_output_tokens_reached=false"))
        assertFalse(text.contains("NPU memory"))
    }

    @Test
    fun `repeated run copy can be included in inference stats developer copy`() {
        val copy = buildInferenceStatsFullCopyText(
            stats = io.github.ninbyo02.lami.ui.model.InferenceStats(modelName = "local-dev"),
            displayMode = io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode.DEVELOPER,
            sections = emptyList(),
            detailSections = emptyList(),
            npuS1RepeatedRunState = NpuS1RepeatedRunState(records = listOf(record())),
        )

        assertTrue(copy.contains("[DEV診断: NPU S1 repeated run summary]"))
        assertFalse(copy.contains("[DEV診断: NPU S1 repeated run details]"))
    }

    @Test
    fun `memory threshold proximity stops repeated run`() {
        assertEquals(
            "system_memory_threshold_near",
            repeatedRunMemoryThresholdStopReason(snapshot(availableSystemMemoryMb = 200, systemMemoryThresholdMb = 100)),
        )
        assertEquals(
            null,
            repeatedRunMemoryThresholdStopReason(snapshot(availableSystemMemoryMb = 250, systemMemoryThresholdMb = 100)),
        )
    }

    @Test
    fun `repeated run modes keep lifecycle plans`() {
        assertEquals("reuse", NpuS1RepeatedRunMode.REUSE.wireValue)
        assertEquals("reuse_10s", NpuS1RepeatedRunMode.REUSE_10S.wireValue)
        assertEquals("reuse_30s", NpuS1RepeatedRunMode.REUSE_30S.wireValue)
        assertEquals("recreate", NpuS1RepeatedRunMode.RECREATE.wireValue)
        assertEquals("recreate_3s", NpuS1RepeatedRunMode.RECREATE_3S.wireValue)
        assertFalse(npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.REUSE).recreateAfterRun)
        assertEquals(0L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.REUSE).postRecreateDelayMs)
        assertEquals(0L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.REUSE).waitAfterRunMs)
        assertFalse(npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.REUSE_10S).recreateAfterRun)
        assertEquals(0L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.REUSE_10S).postRecreateDelayMs)
        assertEquals(10_000L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.REUSE_10S).waitAfterRunMs)
        assertFalse(npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.REUSE_30S).recreateAfterRun)
        assertEquals(0L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.REUSE_30S).postRecreateDelayMs)
        assertEquals(30_000L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.REUSE_30S).waitAfterRunMs)
        assertTrue(npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.RECREATE).recreateAfterRun)
        assertEquals(0L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.RECREATE).postRecreateDelayMs)
        assertEquals(0L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.RECREATE).waitAfterRunMs)
        assertTrue(npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.RECREATE_3S).recreateAfterRun)
        assertEquals(3_000L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.RECREATE_3S).postRecreateDelayMs)
        assertEquals(0L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.RECREATE_3S).waitAfterRunMs)
    }

    @Test
    fun `repeated run changes do not modify npu prompt sanitizer token and fallback contracts`() {
        val sanitized = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = "こんにちは！<end_of_turn>",
            prompt = "こんにちは",
        )

        assertEquals("LamiNpuS1", NPU_S1_LOGCAT_TAG)
        assertEquals(32, NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS)
        assertEquals("raw_dialog_tail_variant_a", NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT)
        assertEquals("safe_greeting_fallback", NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING)
        assertEquals(128, NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_MAX_LENGTH)
        assertEquals("short_prompt_guard", NpuDiagnosticPromptValidator.DEFAULT_INPUT_LIMIT_MODE)
        assertEquals(10, NPU_S1_REPEATED_RUN_DEFAULT_COUNT)
        assertEquals("こんにちは", NPU_S1_REPEATED_RUN_DEFAULT_PROMPT)
        assertEquals("こんにちは！", sanitized.sanitizedOutput)
        assertTrue(sanitized.sanitizerApplied)
    }

    @Test
    fun `repeated summary counts engine create failed and records first index after successes`() {
        val failure = record(
            runIndex = 3,
            status = "failure",
            reason = "adapter_failure:LiteRtLmJniException: engine-create-failed:INTERNAL",
            runDecodeReached = false,
            npuS1FailureKind = NPU_STANDARD_ROUTE_S1_FAILURE_KIND_ENGINE_CREATE_FAILED,
            nativeCrashRiskHint = "engine_create_failed_near_litert_compiled_model_dispatch_delegate_check_tombstone_dropbox",
            failureExceptionClass = "LiteRtLmJniException",
            failureExceptionMessage = "engine-create-failed:INTERNAL",
            failureDetectedAtElapsedRealtimeMs = 3_500L,
            nativeDiagnostics = NpuS1NativeStageDiagnostics(
                nativeStageHistory = "provider_start>adapter_start>before_native_call>native_call>adapter_failure>provider_failure",
            ),
        )
        val state = NpuS1RepeatedRunState(
            requestedRunCount = 20,
            records = listOf(
                record(runIndex = 1, runFinishedAtElapsedRealtimeMs = 1_000L),
                record(runIndex = 2, runFinishedAtElapsedRealtimeMs = 2_000L),
                failure,
            ),
        )
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(state)

        assertTrue(text.contains("engine_create_failed_count=1"))
        assertTrue(text.contains("first_failure_run_index=3"))
        assertTrue(text.contains("first_engine_create_failure_run_index=3"))
        assertTrue(text.contains("failure_after_n_successes=2"))
        assertTrue(text.contains("failure_after_last_success_elapsed_ms=1500"))
        assertTrue(text.contains("npu_s1_failure_kind=engine_create_failed"))
        assertTrue(text.contains("first_failure_native_stage_history=provider_start>adapter_start>before_native_call>native_call>adapter_failure>provider_failure"))
    }

    @Test
    fun `repeated summary supports requested counts and configured wait ms`() {
        listOf(20, 50, 100).forEach { count ->
            val text = formatNpuS1RepeatedRunDiagnosticsForDev(
                NpuS1RepeatedRunState(
                    requestedRunCount = count,
                    repeatedRunWaitMs = 500L,
                    records = listOf(record(runCount = count, waitAfterRunMs = 500L)),
                ),
            )

            assertTrue(text.contains("run_count_requested=$count"))
            assertTrue(text.contains("repeated_run_wait_ms=500"))
        }
    }

    @Test
    fun `arithmetic prompt records normal chat display and tts candidates in compact summary`() {
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(
            NpuS1RepeatedRunState(
                prompt = "1+1は？",
                records = listOf(
                    record(
                        prompt = "1+1は？",
                        outputQualityCandidatePreparedOutput = "2",
                        arithmeticTailLeakDetected = true,
                        arithmeticTailLeakIgnoredForDisplay = true,
                        actualDisplayText = "2",
                        ttsText = "2です。",
                    ),
                ),
            ),
        )

        assertTrue(text.contains("most_common_actual_display_text=2"))
        assertTrue(text.contains("most_common_tts_text=2です。"))
        assertTrue(text.contains("arithmetic_tail_leak_count=1"))
    }

    @Test
    fun `compact summary omits full repeated records and persistent details`() {
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(
            NpuS1RepeatedRunState(records = (1..20).map { record(runIndex = it) }),
        )

        assertFalse(text.contains("[DEV診断: NPU S1 repeated run details]"))
        assertFalse(text.contains("run_index=20"))
        assertFalse(text.contains("persistent"))
        assertFalse(text.contains("full dump"))
    }

    @Test
    fun `quality fail and engine create failed are counted separately`() {
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(
            NpuS1RepeatedRunState(
                records = listOf(
                    record(
                        runIndex = 1,
                        status = "failure",
                        reason = "quality_candidate_fail",
                        outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL,
                    ),
                    record(
                        runIndex = 2,
                        status = "failure",
                        reason = "adapter_failure:LiteRtLmJniException: engine-create-failed",
                        runDecodeReached = false,
                        npuS1FailureKind = NPU_STANDARD_ROUTE_S1_FAILURE_KIND_ENGINE_CREATE_FAILED,
                        failureExceptionClass = "LiteRtLmJniException",
                        failureExceptionMessage = "engine-create-failed",
                    ),
                ),
            ),
        )

        assertTrue(text.contains("quality_fail_count=1"))
        assertTrue(text.contains("engine_create_failed_count=1"))
        assertTrue(text.contains("first_engine_create_failure_run_index=2"))
    }

    @Test
    fun `Copy Stability Summary uses repeated summary formatter without detail body`() {
        val state = NpuS1RepeatedRunState(
            requestedRunCount = 50,
            selectedBackend = NPU_S1_BACKEND_NPU_S1,
            requestedBackend = NPU_S1_BACKEND_NPU,
            effectiveBackend = NPU_S1_BACKEND_NPU,
            backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
            routeFamily = NPU_S1_ROUTE_FAMILY_NPU_S1,
            repeatedRunMode = NpuS1RepeatedRunMode.RECREATE,
            records = listOf(
                record(runIndex = 1),
                record(
                    runIndex = 2,
                    status = "failure",
                    reason = "quality_candidate_fail",
                    outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL,
                ),
                record(
                    runIndex = 3,
                    status = "failure",
                    reason = "adapter_failure:LiteRtLmJniException: engine-create-failed",
                    runDecodeReached = false,
                    npuS1FailureKind = NPU_STANDARD_ROUTE_S1_FAILURE_KIND_ENGINE_CREATE_FAILED,
                    failureExceptionClass = "LiteRtLmJniException",
                    failureExceptionMessage = "engine-create-failed",
                ),
            ),
            stopped = true,
            stopReason = "adapter_failure",
        )

        val copy = buildNpuBetaStabilitySummaryCopyText(state)

        assertTrue(copy.contains("[DEV診断: NPU S1 repeated run summary]"))
        assertTrue(copy.contains("test_name=NPU Beta Stability Test"))
        assertTrue(copy.contains("mode=safe_recreate"))
        assertTrue(copy.contains("run_count_requested=50"))
        assertTrue(copy.contains("run_count_completed=3"))
        assertTrue(copy.contains("success_count=1"))
        assertTrue(copy.contains("failure_count=2"))
        assertTrue(copy.contains("engine_create_failed_count=1"))
        assertTrue(copy.contains("quality_fail_count=1"))
        assertTrue(copy.contains("selected_backend=NPU_S1"))
        assertTrue(copy.contains("requested_backend=NPU"))
        assertTrue(copy.contains("effective_backend=NPU"))
        assertTrue(copy.contains("backend_evidence=QNN_HTP_V79_FastRPC_native_diag"))
        assertTrue(copy.contains("route_family=npu_s1"))
        assertTrue(copy.contains("first_failure_run_index=2"))
        assertTrue(copy.contains("last_failure_run_index=3"))
        assertTrue(copy.contains("stop_reason=adapter_failure"))
        assertFalse(copy.contains("[DEV診断: NPU S1 repeated run details]"))
        assertFalse(copy.contains("\nrun_index=2\n"))
        assertFalse(copy.contains("[DEV診断: NPU S1 compact]"))
        assertFalse(copy.contains("[DEV診断: NPU S1 full dump]"))
    }

    @Test
    fun `Copy Stability Full Dump uses currently displayed diagnostics text`() {
        val state = NpuS1RepeatedRunState(
            requestedRunCount = 10,
            selectedBackend = NPU_S1_BACKEND_NPU_S1,
            requestedBackend = NPU_S1_BACKEND_NPU,
            effectiveBackend = NPU_S1_BACKEND_NPU,
            backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
            routeFamily = NPU_S1_ROUTE_FAMILY_NPU_S1,
            records = listOf(
                record(runIndex = 1),
                record(
                    runIndex = 2,
                    status = "failure",
                    reason = "quality_candidate_fail",
                    outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL,
                ),
            ),
        )

        val copy = buildNpuBetaStabilityFullDumpCopyText(state)

        assertEquals(formatNpuS1RepeatedRunDiagnosticsForDev(state), copy)
        assertTrue(copy.contains("[DEV診断: NPU S1 repeated run summary]"))
        assertTrue(copy.contains("[DEV診断: NPU S1 repeated run details]"))
        assertTrue(copy.contains("run_index=2"))
        assertTrue(copy.contains("reason=quality_candidate_fail"))
    }

    @Test
    fun `repeated run start gate allows NPU beta standard route recreate and reuse ten runs with wait`() {
        listOf(
            NpuStandardRouteMode.S1_ONLY,
            NpuStandardRouteMode.S2_DB,
            NpuStandardRouteMode.S3_MARKDOWN,
            NpuStandardRouteMode.S4A_PSEUDO_STREAMING,
            NpuStandardRouteMode.FULL,
        ).forEach { mode ->
            assertTrue(
                npuS1RepeatedRunStartGate(
                    preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
                    npuStandardRouteMode = mode,
                    mode = NpuS1RepeatedRunMode.RECREATE,
                    runCount = 10,
                    waitMs = 500L,
                ).allowed,
            )
            assertTrue(
                npuS1RepeatedRunStartGate(
                    preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
                    npuStandardRouteMode = mode,
                    mode = NpuS1RepeatedRunMode.REUSE,
                    runCount = 10,
                    waitMs = 500L,
                ).allowed,
            )
        }
        assertEquals(
            NPU_S1_REPEATED_RUN_BLOCKED_SELECTED_BACKEND_NOT_NPU,
            npuS1RepeatedRunStartGate(
                preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
                mode = NpuS1RepeatedRunMode.RECREATE,
                runCount = 10,
                waitMs = 500L,
            ).blockedReason,
        )
        assertEquals(
            NPU_S1_REPEATED_RUN_BLOCKED_SELECTED_BACKEND_NOT_NPU,
            npuS1RepeatedRunStartGate(
                preferredBackendSetting = PreferredBackendDryRunSetting.CPU,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
                mode = NpuS1RepeatedRunMode.RECREATE,
                runCount = 10,
                waitMs = 500L,
            ).blockedReason,
        )
        assertEquals(
            NPU_S1_REPEATED_RUN_BLOCKED_SELECTED_BACKEND_NOT_NPU,
            npuS1RepeatedRunStartGate(
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
                mode = NpuS1RepeatedRunMode.RECREATE,
                runCount = 10,
                waitMs = 500L,
            ).blockedReason,
        )
        assertEquals(
            NPU_S1_REPEATED_RUN_BLOCKED_SELECTED_BACKEND_NOT_NPU,
            npuS1RepeatedRunStartGate(
                preferredBackendSetting = PreferredBackendDryRunSetting.CPU,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
                mode = NpuS1RepeatedRunMode.REUSE,
                runCount = 10,
                waitMs = 500L,
            ).blockedReason,
        )
        assertEquals(
            NPU_S1_REPEATED_RUN_BLOCKED_UNSAFE_MODE,
            npuS1RepeatedRunStartGate(
                preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
                npuStandardRouteMode = NpuStandardRouteMode.FULL,
                mode = NpuS1RepeatedRunMode.REUSE_10S,
                runCount = 10,
                waitMs = 500L,
            ).blockedReason,
        )
        assertEquals(
            NPU_S1_REPEATED_RUN_BLOCKED_UNSAFE_RUN_COUNT,
            npuS1RepeatedRunStartGate(
                preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
                npuStandardRouteMode = NpuStandardRouteMode.FULL,
                mode = NpuS1RepeatedRunMode.REUSE,
                runCount = 50,
                waitMs = 500L,
            ).blockedReason,
        )
        assertEquals(
            NPU_S1_REPEATED_RUN_BLOCKED_UNSAFE_RUN_COUNT,
            npuS1RepeatedRunStartGate(
                preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
                npuStandardRouteMode = NpuStandardRouteMode.FULL,
                mode = NpuS1RepeatedRunMode.RECREATE,
                runCount = 100,
                waitMs = 500L,
            ).blockedReason,
        )
        assertEquals(
            NPU_S1_REPEATED_RUN_BLOCKED_UNSAFE_WAIT_MS,
            npuS1RepeatedRunStartGate(
                preferredBackendSetting = PreferredBackendDryRunSetting.DEFAULT,
                npuStandardRouteMode = NpuStandardRouteMode.FULL,
                mode = NpuS1RepeatedRunMode.RECREATE,
                runCount = 10,
                waitMs = 0L,
            ).blockedReason,
        )
    }

    @Test
    fun `blocked repeated run summary records selected backend not npu`() {
        val state = NpuS1RepeatedRunState(
            status = NPU_S1_REPEATED_RUN_STATUS_STOPPED,
            selectedBackend = NPU_S1_BACKEND_GPU,
            requestedBackend = NPU_S1_BACKEND_NPU,
            effectiveBackend = NPU_S1_BACKEND_GPU,
            backendEvidence = NPU_S1_BACKEND_EVIDENCE_GPU_ROUTE,
            routeFamily = NPU_S1_ROUTE_FAMILY_LOCAL_GPU,
            blockedReason = NPU_S1_REPEATED_RUN_BLOCKED_SELECTED_BACKEND_NOT_NPU,
            stopped = true,
            stopReason = "blocked",
        )
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(state)

        assertTrue(text.contains("selected_backend=GPU"))
        assertTrue(text.contains("requested_backend=NPU"))
        assertTrue(text.contains("effective_backend=GPU"))
        assertTrue(text.contains("backend_evidence=gpu_route"))
        assertTrue(text.contains("route_family=local_gpu"))
        assertTrue(text.contains("blocked_reason=selected_backend_not_npu"))
        assertTrue(text.contains("stability_test_gate_allowed=false"))
        assertTrue(text.contains("stability_test_gate_reason=selected_backend_not_npu"))
    }

    @Test
    fun `NPU S5 repeated run summary records gate allowed and standard route backend`() {
        val state = NpuS1RepeatedRunState(
            status = NPU_S1_REPEATED_RUN_STATUS_RUNNING,
            selectedBackend = NPU_S1_BACKEND_NPU_S5,
            requestedBackend = NPU_S1_BACKEND_NPU,
            effectiveBackend = NPU_S1_BACKEND_NPU,
            backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
            routeFamily = NPU_S1_ROUTE_FAMILY_NPU_S5,
            blockedReason = "none",
            maxOutputTokens = 4096,
            repeatedRunMode = NpuS1RepeatedRunMode.REUSE,
            repeatedRunWaitMs = 500L,
        )
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(state)

        assertTrue(text.contains("stability_test_gate_allowed=true"))
        assertTrue(text.contains("stability_test_gate_reason=none"))
        assertTrue(text.contains("reuse_enabled=true"))
        assertTrue(text.contains("reuse_gate_allowed=true"))
        assertTrue(text.contains("reuse_gate_reason=none"))
        assertTrue(text.contains("engine_reuse_requested=true"))
        assertTrue(text.contains("engine_reused=unavailable"))
        assertTrue(text.contains("selected_backend=NPU_S5"))
        assertTrue(text.contains("requested_backend=NPU"))
        assertTrue(text.contains("effective_backend=NPU"))
        assertTrue(text.contains("route_family=npu_s5"))
        assertTrue(text.contains("npu_standard_route_connected=true"))
        assertTrue(text.contains("requested_max_output_tokens=4096"))
        assertTrue(text.contains("effective_max_output_tokens=4096"))
        assertTrue(text.contains("max_output_tokens_clamped=false"))
        assertTrue(text.contains("run_mode=reuse"))
        assertTrue(text.contains("run_count=10"))
        assertTrue(text.contains("wait_ms=500"))
    }

    @Test
    fun `engine create failed summary records safety policy and guard recommendation`() {
        val text = formatNpuS1RepeatedRunDiagnosticsForDev(
            NpuS1RepeatedRunState(
                records = listOf(
                    record(
                        status = "failure",
                        reason = "adapter_failure:LiteRtLmJniException: engine-create-failed",
                        runDecodeReached = false,
                        npuS1FailureKind = NPU_STANDARD_ROUTE_S1_FAILURE_KIND_ENGINE_CREATE_FAILED,
                        failureExceptionClass = "LiteRtLmJniException",
                        failureExceptionMessage = "engine-create-failed",
                    ),
                ),
            ),
        )

        assertTrue(text.contains("repeated_run_safety_policy=stop_on_first_engine_create_failed"))
        assertTrue(text.contains("guard_recommendation=disable_npu_until_app_restart_or_cooldown"))
    }

    @Test
    fun `backend diagnostics keep local backend effective unless NPU S1 is selected`() {
        val automatic = npuS1BackendDiagnosticsForPreferredSetting(
            setting = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.OFF,
        )
        assertEquals("Automatic", automatic.selectedBackend)
        assertEquals("Automatic", automatic.requestedBackend)
        assertEquals("Automatic", automatic.effectiveBackend)
        assertEquals("local_default", automatic.routeFamily)

        val cpuWithNpuEvidence = npuS1BackendDiagnosticsForPreferredSetting(
            setting = PreferredBackendDryRunSetting.CPU,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        )
        assertEquals("CPU", cpuWithNpuEvidence.selectedBackend)
        assertEquals("CPU", cpuWithNpuEvidence.requestedBackend)
        assertEquals("CPU", cpuWithNpuEvidence.effectiveBackend)
        assertEquals("cpu_route", cpuWithNpuEvidence.backendEvidence)
        assertEquals("local_cpu", cpuWithNpuEvidence.routeFamily)

        val gpuWithNpuEvidence = npuS1BackendDiagnosticsForPreferredSetting(
            setting = PreferredBackendDryRunSetting.GPU,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        )
        assertEquals("GPU", gpuWithNpuEvidence.selectedBackend)
        assertEquals("GPU", gpuWithNpuEvidence.requestedBackend)
        assertEquals("GPU", gpuWithNpuEvidence.effectiveBackend)
        assertEquals("gpu_route", gpuWithNpuEvidence.backendEvidence)
        assertEquals("local_gpu", gpuWithNpuEvidence.routeFamily)

        val npuS1 = npuS1BackendDiagnosticsForPreferredSetting(
            setting = PreferredBackendDryRunSetting.DEFAULT,
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        )
        assertEquals("NPU_S1", npuS1.selectedBackend)
        assertEquals("NPU", npuS1.requestedBackend)
        assertEquals("NPU", npuS1.effectiveBackend)
        assertEquals("npu_s1", npuS1.routeFamily)
    }

    private fun record(
        runIndex: Int = 1,
        totalMs: Long? = 100L,
        tokensPerSecond: Double? = 2.5,
        status: String = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        fallbackUsed: Boolean = false,
        timeout: Boolean = false,
        freshCrash: Boolean = false,
        safetyGuardTriggered: Boolean = false,
        runDecodeReached: Boolean = true,
        reason: String = if (status == NpuStandardRouteS1Contract.STATUS_SUCCESS) "success" else status,
        memoryRecovery5sTotalPssMb: Long? = 250L,
        memoryRecovery5sNativeHeapPssMb: Long? = 70L,
        memoryRecovery5sSystemAvailableMemoryMb: Long? = 1000L,
        memoryBeforeLowMemory: Boolean? = false,
        memoryAfterLowMemory: Boolean? = false,
        memoryRecovery5sLowMemory: Boolean? = false,
        repeatedRunMode: NpuS1RepeatedRunMode = NpuS1RepeatedRunMode.REUSE,
        recreateResultAfterRun: String = "not_requested",
        nativeDiagnostics: NpuS1NativeStageDiagnostics = NpuS1NativeStageDiagnostics(),
        waitAfterRunMs: Long = 0L,
        waitStartedAtElapsedRealtimeMs: Long? = null,
        waitFinishedAtElapsedRealtimeMs: Long? = null,
        runCount: Int = 10,
        prompt: String = "こんにちは",
        qualityClassification: String = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        outputQualityCandidateStatus: String = NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS,
        outputQualityCandidateReason: String = "natural_japanese",
        outputQualityCandidatePreparedOutput: String = "こんにちは。",
        arithmeticTailLeakDetected: Boolean = false,
        arithmeticTailLeakIgnoredForDisplay: Boolean = false,
        actualDisplayText: String = "こんにちは。",
        ttsText: String = actualDisplayText,
        npuS1FailureKind: String = "unavailable",
        nativeCrashRiskHint: String = "unavailable",
        selectedBackend: String = NPU_S1_BACKEND_NPU_S1,
        requestedBackend: String = NPU_S1_BACKEND_NPU,
        effectiveBackend: String = NPU_S1_BACKEND_NPU,
        backendEvidence: String = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        routeFamily: String = NPU_S1_ROUTE_FAMILY_NPU_S1,
        failureExceptionClass: String = "unavailable",
        failureExceptionMessage: String = "unavailable",
        failureDetectedAtElapsedRealtimeMs: Long? = null,
        runFinishedAtElapsedRealtimeMs: Long? = null,
    ): NpuS1RepeatedRunRecord = NpuS1RepeatedRunRecord(
        runIndex = runIndex,
        runCount = runCount,
        repeatedRunMode = repeatedRunMode,
        prompt = prompt,
        requestedMaxOutputTokens = 32,
        effectiveMaxOutputTokens = 32,
        status = status,
        reason = reason,
        finishReason = "unavailable",
        stopReason = "unavailable",
        eosDetected = "unavailable",
        rawOutput = " こんにちは。",
        sanitizedOutput = "こんにちは。",
        qualityClassification = qualityClassification,
        outputQualityCandidateStatus = outputQualityCandidateStatus,
        outputQualityCandidateReason = outputQualityCandidateReason,
        outputQualityCandidatePreparedOutput = outputQualityCandidatePreparedOutput,
        arithmeticTailLeakDetected = arithmeticTailLeakDetected,
        arithmeticTailLeakIgnoredForDisplay = arithmeticTailLeakIgnoredForDisplay,
        actualDisplayText = actualDisplayText,
        ttsText = ttsText,
        npuS1FailureKind = npuS1FailureKind,
        nativeCrashRiskHint = nativeCrashRiskHint,
        selectedBackend = selectedBackend,
        requestedBackend = requestedBackend,
        effectiveBackend = effectiveBackend,
        backendEvidence = backendEvidence,
        routeFamily = routeFamily,
        totalMs = totalMs,
        decodeMs = totalMs?.minus(1L),
        outputTokens = 6,
        tokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS,
        tokensPerSecond = tokensPerSecond,
        runDecodeReached = runDecodeReached,
        fallbackUsed = fallbackUsed,
        timeout = timeout,
        freshCrash = freshCrash,
        safetyGuardTriggered = safetyGuardTriggered,
        memoryBeforeTotalPssMb = 240L,
        memoryBeforeNativeHeapPssMb = 60L,
        memoryBeforeLowMemory = memoryBeforeLowMemory,
        memoryAfterTotalPssMb = 255L,
        memoryAfterNativeHeapPssMb = 80L,
        memoryAfterLowMemory = memoryAfterLowMemory,
        memoryRecovery5sTotalPssMb = memoryRecovery5sTotalPssMb,
        memoryRecovery5sNativeHeapPssMb = memoryRecovery5sNativeHeapPssMb,
        memoryRecovery5sNativeHeapAllocMb = 24L,
        memoryRecovery5sSystemAvailableMemoryMb = memoryRecovery5sSystemAvailableMemoryMb,
        memoryRecovery5sLowMemory = memoryRecovery5sLowMemory,
        recreateRequestedAfterRun = repeatedRunMode.recreateAfterRun,
        recreateResultAfterRun = recreateResultAfterRun,
        recreateDelayAfterRunMs = repeatedRunMode.postRecreateDelayMs,
        waitAfterRunMs = waitAfterRunMs,
        waitStartedAtElapsedRealtimeMs = waitStartedAtElapsedRealtimeMs,
        waitFinishedAtElapsedRealtimeMs = waitFinishedAtElapsedRealtimeMs,
        finalInputLengthChars = 5,
        finalInputTailPreview = "こんにちは",
        outputTokenCountSource = "estimated_code_points_not_tokenizer",
        promptTokenCountSource = "code_points",
        maxOutputTokensReached = false,
        runFinishedAtElapsedRealtimeMs = runFinishedAtElapsedRealtimeMs,
        failureDetectedAtElapsedRealtimeMs = failureDetectedAtElapsedRealtimeMs,
        failureExceptionClass = failureExceptionClass,
        failureExceptionMessage = failureExceptionMessage,
        nativeDiagnostics = nativeDiagnostics,
    )

    private fun result(outputTokens: Int?): NpuStandardRouteS1Result = NpuStandardRouteS1Result(
        selection = NpuStandardRouteS1Selection(
            enabled = true,
            requestedMaxOutputTokens = 32,
            effectiveMaxOutputTokens = 32,
        ),
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput = " こんにちは。",
        sanitizedOutput = "こんにちは。",
        qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        runDecodeReached = true,
        npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed = false,
        timeout = false,
        freshCrash = false,
        timing = NpuStandardRouteS1Timing(
            totalMs = 100L,
            decodeMs = 99L,
            outputTokens = outputTokens,
            tokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS,
            tokensPerSecond = 2.5,
        ),
    )

    private fun snapshot(
        availableSystemMemoryMb: Long?,
        systemMemoryThresholdMb: Long?,
    ): MemorySnapshot = MemorySnapshot(
        timestampMs = 1L,
        stage = "test",
        javaHeapUsedMb = 1L,
        javaHeapMaxMb = 2L,
        nativeHeapAllocatedMb = 3L,
        nativeHeapSizeMb = 4L,
        totalPssMb = 5L,
        privateDirtyMb = 6L,
        privateCleanMb = 7L,
        availableSystemMemoryMb = availableSystemMemoryMb,
        systemMemoryThresholdMb = systemMemoryThresholdMb,
        lowMemory = false,
        threadName = "test",
    )
}
