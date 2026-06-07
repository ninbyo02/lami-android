package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.Qairt244NpuOutputSanitizer
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
        assertEquals("こんにちは。", summary.mostCommonOutput)
        assertTrue(summary.allOutputsSame)
    }

    @Test
    fun `copy formatter includes repeated run summary details memory and unavailable finish reasons`() {
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
        assertTrue(text.contains("[DEV診断: NPU S1 repeated run details]"))
        assertTrue(text.contains("repeated_run_status=completed"))
        assertTrue(text.contains("repeated_run_mode=reuse"))
        assertTrue(text.indexOf("run_index=1") < text.lastIndexOf("repeated_run_mode=reuse"))
        assertTrue(text.contains("recreate_api_note=s1_direct_runner_engine_session_dispose_not_exposed_uses_safe_holder_recreate_api"))
        assertTrue(text.contains("run_index=1"))
        assertTrue(text.contains("finish_reason=unavailable"))
        assertTrue(text.contains("stop_reason=unavailable"))
        assertTrue(text.contains("memory_recovery_5s_total_pss_mb=250"))
        assertTrue(text.contains("memory_recovery_5s_native_heap_pss_mb=70"))
        assertTrue(text.contains("memory_recovery_5s_native_heap_alloc_mb=24"))
        assertTrue(text.contains("memory_recovery_5s_system_available_memory_mb=1000"))
        assertTrue(text.contains("memory_before_low_memory=false"))
        assertTrue(text.contains("memory_after_low_memory=false"))
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
        assertTrue(text.contains("run_started_at_wall_time_ms=unavailable"))
        assertTrue(text.contains("run_finished_at_wall_time_ms=unavailable"))
        assertTrue(text.contains("engine_request_count_at_run=1"))
        assertTrue(text.contains("adapter_call_count_at_run=1"))
        assertTrue(text.contains("decode_success_count_at_run=1"))
        assertTrue(text.contains("failure_counter_snapshot=unavailable"))
        assertTrue(text.contains("recreate_requested_after_run=false"))
        assertTrue(text.contains("recreate_result_after_run=not_requested"))
        assertTrue(text.contains("recreate_delay_after_run_ms=0"))
        assertTrue(text.contains("output_token_count_source=estimated_code_points_not_tokenizer"))
        assertTrue(text.contains("prompt_token_count_source=code_points"))
        assertFalse(text.contains("NPU memory"))
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
        assertTrue(copy.contains("[DEV診断: NPU S1 repeated run details]"))
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
        assertEquals("recreate", NpuS1RepeatedRunMode.RECREATE.wireValue)
        assertEquals("recreate_3s", NpuS1RepeatedRunMode.RECREATE_3S.wireValue)
        assertFalse(npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.REUSE).recreateAfterRun)
        assertEquals(0L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.REUSE).postRecreateDelayMs)
        assertTrue(npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.RECREATE).recreateAfterRun)
        assertEquals(0L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.RECREATE).postRecreateDelayMs)
        assertTrue(npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.RECREATE_3S).recreateAfterRun)
        assertEquals(3_000L, npuS1RepeatedRunLifecyclePlan(NpuS1RepeatedRunMode.RECREATE_3S).postRecreateDelayMs)
    }

    @Test
    fun `repeated run changes do not modify npu prompt sanitizer token and fallback contracts`() {
        val sanitized = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = "こんにちは！<end_of_turn>",
            prompt = "こんにちは",
        )

        assertEquals("LamiNpuS1", NPU_S1_LOGCAT_TAG)
        assertEquals(32, NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS)
        assertEquals("raw_dialog_tail_variant_c", NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT)
        assertEquals("safe_greeting_fallback", NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING)
        assertEquals(128, NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_MAX_LENGTH)
        assertEquals("short_prompt_guard", NpuDiagnosticPromptValidator.DEFAULT_INPUT_LIMIT_MODE)
        assertEquals(20, NPU_S1_REPEATED_RUN_DEFAULT_COUNT)
        assertEquals("こんにちは", NPU_S1_REPEATED_RUN_DEFAULT_PROMPT)
        assertEquals("こんにちは！", sanitized.sanitizedOutput)
        assertTrue(sanitized.sanitizerApplied)
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
    ): NpuS1RepeatedRunRecord = NpuS1RepeatedRunRecord(
        runIndex = runIndex,
        runCount = 20,
        repeatedRunMode = repeatedRunMode,
        prompt = "こんにちは",
        requestedMaxOutputTokens = 32,
        effectiveMaxOutputTokens = 32,
        status = status,
        reason = reason,
        finishReason = "unavailable",
        stopReason = "unavailable",
        eosDetected = "unavailable",
        rawOutput = " こんにちは。",
        sanitizedOutput = "こんにちは。",
        qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
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
        finalInputLengthChars = 5,
        finalInputTailPreview = "こんにちは",
        outputTokenCountSource = "estimated_code_points_not_tokenizer",
        promptTokenCountSource = "code_points",
        maxOutputTokensReached = false,
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
