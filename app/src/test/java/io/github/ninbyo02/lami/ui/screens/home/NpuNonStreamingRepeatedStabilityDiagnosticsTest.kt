package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NonStreaming {
    @Test
    fun `summary exposes non streaming repeat safety keys`() {
        val text = buildNpuNonStreamingRepeatedStabilitySummaryCopyText(
            NpuNonStreamingRepeatedStabilityState(
                status = NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_COMPLETED,
                reason = "ok",
                runCountRequested = 10,
                records = (1..10).map { index ->
                    record(
                        runIndex = index,
                        prompt = NPU_NON_STREAMING_REPEATED_STABILITY_PROMPTS[index - 1],
                    )
                },
            ),
        )

        assertTrue(text.contains("test_name=NPU Non-Streaming Repeated Stability Test"))
        assertTrue(text.contains("route_type=dev_only_one_turn_conversation_non_streaming_repeat"))
        assertTrue(text.contains("streaming=false"))
        assertTrue(text.contains("pseudo_streaming=false"))
        assertTrue(text.contains("tts=false"))
        assertTrue(text.contains("db=false"))
        assertTrue(text.contains("markdown=false"))
        assertTrue(text.contains("fallback_allowed=false"))
        assertTrue(text.contains("run_count_requested=10"))
        assertTrue(text.contains("run_count_completed=10"))
        assertTrue(text.contains("success_count=10"))
        assertTrue(text.contains("failure_count=0"))
        assertTrue(text.contains("success_rate=1.00"))
        assertTrue(text.contains("run_decode_reached_count=10"))
        assertTrue(text.contains("run_decode_reached_rate=1.00"))
        assertTrue(text.contains("backend_evidence_summary=QNN_HTP_V79_FastRPC_native_diag:10"))
        assertTrue(text.contains("quality_classification_summary=natural_japanese:10"))
        assertTrue(text.contains("fallback_used_count=0"))
        assertTrue(text.contains("fallback_rate=0.00"))
        assertTrue(text.contains("timeout_count=0"))
        assertTrue(text.contains("timeout_rate=0.00"))
        assertTrue(text.contains("fresh_crash_count=0"))
        assertTrue(text.contains("fresh_crash_rate=0.00"))
        assertTrue(text.contains("average_total_ms=105"))
        assertTrue(text.contains("average_decode_ms=55"))
        assertTrue(text.contains("restart_app_recommended=false"))
        assertTrue(text.contains("guard_recommendation=none"))
        assertTrue(text.contains("true_engine_probe_status=disabled_or_blocked"))
        assertTrue(text.contains("true_engine_persistent_reuse=false"))
        assertTrue(text.contains("engine_reuse_observed=unavailable"))
        assertFalse(text.contains("[DEV診断: NPU Non-Streaming Repeated Stability detail]"))
    }

    @Test
    fun `full dump includes per run details and first failure`() {
        val text = buildNpuNonStreamingRepeatedStabilityFullDumpCopyText(
            NpuNonStreamingRepeatedStabilityState(
                status = NPU_NON_STREAMING_REPEATED_STABILITY_STATUS_STOPPED,
                reason = "timeout",
                runCountRequested = 10,
                records = listOf(
                    record(runIndex = 1),
                    record(
                        runIndex = 2,
                        status = "failure",
                        reason = "timeout",
                        runDecodeReached = false,
                        timeout = true,
                        nativeStage = "native_call",
                        nativeErrorStage = "decode",
                        nativeErrorClass = "TimeoutCancellationException",
                        nativeDiagTail = "decode timeout",
                    ),
                ),
                stopped = true,
                stopReason = "timeout",
            ),
        )

        assertTrue(text.contains("run_count_completed=2"))
        assertTrue(text.contains("success_count=1"))
        assertTrue(text.contains("failure_count=1"))
        assertTrue(text.contains("first_failure_run_index=2"))
        assertTrue(text.contains("first_failure_stage=decode"))
        assertTrue(text.contains("first_failure_reason=timeout"))
        assertTrue(text.contains("first_failure_exception_class=TimeoutCancellationException"))
        assertTrue(text.contains("first_failure_native_diag_tail=decode timeout"))
        assertTrue(text.contains("[DEV診断: NPU Non-Streaming Repeated Stability detail]"))
        assertTrue(text.contains("run_index=2"))
        assertTrue(text.contains("prompt=こんにちは"))
        assertTrue(text.contains("raw_output_first_200_chars=raw"))
        assertTrue(text.contains("sanitized_output=こんにちは。"))
        assertTrue(text.contains("native_stage_history=adapter_start>native_call"))
        assertTrue(text.contains("native_error_stage=decode"))
        assertTrue(text.contains("native_error_class=TimeoutCancellationException"))
    }

    @Test
    fun `empty state keeps rates unavailable`() {
        val text = buildNpuNonStreamingRepeatedStabilitySummaryCopyText(
            NpuNonStreamingRepeatedStabilityState(),
        )

        assertTrue(text.contains("run_count_completed=0"))
        assertTrue(text.contains("success_rate=unavailable"))
        assertTrue(text.contains("run_decode_reached_rate=unavailable"))
        assertTrue(text.contains("fallback_rate=unavailable"))
        assertTrue(text.contains("timeout_rate=unavailable"))
        assertTrue(text.contains("fresh_crash_rate=unavailable"))
        assertTrue(text.contains("average_total_ms=unavailable"))
        assertTrue(text.contains("average_decode_ms=unavailable"))
        assertTrue(text.contains("backend_evidence_summary=unavailable"))
        assertTrue(text.contains("quality_classification_summary=unavailable"))
    }

    private fun record(
        runIndex: Int,
        prompt: String = "こんにちは",
        status: String = "success",
        reason: String = "ok",
        runDecodeReached: Boolean = true,
        fallbackUsed: Boolean = false,
        timeout: Boolean = false,
        freshCrash: Boolean = false,
        nativeStage: String = "decode_finished",
        nativeErrorStage: String = "unavailable",
        nativeErrorClass: String = "unavailable",
        nativeDiagTail: String = "unavailable",
    ): NpuNonStreamingRepeatedStabilityRecord =
        NpuNonStreamingRepeatedStabilityRecord(
            runIndex = runIndex,
            prompt = prompt,
            status = status,
            reason = reason,
            runDecodeReached = runDecodeReached,
            backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
            qualityClassification = "natural_japanese",
            fallbackUsed = fallbackUsed,
            timeout = timeout,
            freshCrash = freshCrash,
            totalMs = 100L + runIndex,
            decodeMs = 50L + runIndex,
            rawOutput = "raw",
            sanitizedOutput = "こんにちは。",
            nativeStage = nativeStage,
            nativeStageHistory = "adapter_start>native_call",
            nativeErrorStage = nativeErrorStage,
            nativeErrorClass = nativeErrorClass,
            nativeDiagTail = nativeDiagTail,
        )
}
