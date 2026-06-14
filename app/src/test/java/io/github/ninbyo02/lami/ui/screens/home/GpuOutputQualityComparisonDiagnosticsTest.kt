package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuOutputQualityComparisonDiagnosticsTest {
    @Test
    fun `GPU callback quality comparison diagnostics classify both corrupt`() {
        val compact = compactFromFlags(
            LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                gpuCallbackStreamingPathSelected = true,
                gpuOutputCallbackChunkCount = 20,
                gpuOutputSuspiciousFragmentDetected = true,
                gpuOutputQualityCandidateResult = "quality_candidate_fail",
                cpuCompareRequested = true,
                cpuCompareEnabled = true,
                cpuCompareStarted = true,
                cpuCompareFinished = true,
                cpuCompareSkippedReason = "none",
                cpuCompareExceptionClass = "none",
                cpuCompareFailureStage = "none",
                cpuCompareCallbackInvokedCount = 10,
                cpuOutputSuspiciousFragmentDetected = true,
                cpuOutputSuspiciousFragmentReason = "many_tiny_fragments",
                cpuOutputSourceCorruptionStage = "raw_callback",
            ),
        )

        assertTrue(compact.contains("callback_quality_compare_result=cpu_and_gpu_corrupt"))
        assertTrue(compact.contains("cpu_output_suspicious_fragment_detected=true"))
        assertTrue(compact.contains("cpu_output_source_corruption_stage=raw_callback"))
    }

    @Test
    fun `GPU callback quality comparison diagnostics include skip reason`() {
        val compact = compactFromFlags(
            LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                gpuCallbackStreamingPathSelected = true,
                gpuOutputCallbackChunkCount = 20,
                gpuOutputSuspiciousFragmentDetected = true,
                gpuOutputQualityCandidateResult = "quality_candidate_fail",
                cpuCompareRequested = true,
                cpuCompareEnabled = false,
                cpuCompareStarted = false,
                cpuCompareFinished = false,
                cpuCompareSkippedReason = "not_standard_gpu_minimal_runtime_candidate_flavor",
            ),
        )

        assertTrue(compact.contains("cpu_compare_requested=true"))
        assertTrue(compact.contains("cpu_compare_enabled=false"))
        assertTrue(compact.contains("cpu_compare_skipped_reason=not_standard_gpu_minimal_runtime_candidate_flavor"))
        assertTrue(compact.contains("callback_quality_compare_result=gpu_corrupt_cpu_unavailable"))
        assertTrue(
            compact.contains(
                "callback_quality_compare_reason=cpu_compare_skipped:not_standard_gpu_minimal_runtime_candidate_flavor",
            ),
        )
    }

    @Test
    fun `GPU corrupt with CPU compare exception is not classified as pass`() {
        val compact = compactFromFlags(
            LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                gpuCallbackStreamingPathSelected = true,
                gpuOutputCallbackChunkCount = 20,
                gpuOutputSuspiciousFragmentDetected = true,
                gpuOutputQualityCandidateResult = "quality_candidate_fail",
                cpuCompareRequested = true,
                cpuCompareEnabled = true,
                cpuCompareStarted = true,
                cpuCompareFinished = true,
                cpuCompareSkippedReason = "none",
                cpuCompareFailureStage = "generate_collect",
                cpuCompareExceptionClass = "com.google.ai.edge.litertlm.LiteRtLmJniException",
                cpuCompareExceptionMessage = "Status Code: 13",
                cpuCompareCallbackInvokedCount = 0,
            ),
        )

        assertTrue(compact.contains("callback_quality_compare_result=gpu_corrupt_cpu_unavailable"))
        assertTrue(compact.contains("cpu_compare_exception_class=com.google.ai.edge.litertlm.LiteRtLmJniException"))
    }

    @Test
    fun `healthy GPU with CPU compare timeout is comparison unavailable`() {
        val compact = compactFromFlags(
            LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                gpuCallbackStreamingPathSelected = true,
                gpuOutputCallbackChunkCount = 20,
                gpuOutputSuspiciousFragmentDetected = false,
                gpuOutputQualityCandidateResult = "quality_candidate_pass",
                cpuCompareRequested = true,
                cpuCompareEnabled = true,
                cpuCompareStarted = true,
                cpuCompareFinished = true,
                cpuCompareSkippedReason = "none",
                cpuCompareFailureStage = "timeout",
                cpuCompareExceptionClass = "Timeout",
                cpuCompareExceptionMessage = "cpu_gpu_callback_compare_timeout",
                cpuCompareCallbackInvokedCount = 0,
            ),
        )

        assertTrue(compact.contains("callback_quality_compare_result=comparison_unavailable"))
        assertTrue(compact.contains("cpu_compare_failure_stage=timeout"))
    }

    @Test
    fun `GPU callback raw artifact diagnostics are copied to compact`() {
        val compact = compactFromFlags(
            LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                gpuCallbackStreamingPathSelected = true,
                gpuOutputCallbackChunkCount = 3,
                gpuOutputSuspiciousFragmentDetected = true,
                gpuOutputQualityCandidateResult = "quality_candidate_fail",
                gpuPrefillProbeDiagnostics = mapOf(
                    "gpu_callback_raw_artifact_enabled" to "true",
                    "gpu_callback_raw_stream_artifact_dir" to
                        "/sdcard/Android/data/io.github.ninbyo02.lami.gpustandardminimal/files/artifacts/gpu_callback_raw_stream",
                    "gpu_callback_raw_full_artifact_path" to
                        "/sdcard/Android/data/io.github.ninbyo02.lami.gpustandardminimal/files/artifacts/gpu_callback_raw_stream/gpu_callback_raw_full.txt",
                    "gpu_callback_accumulated_final_artifact_path" to
                        "/sdcard/Android/data/io.github.ninbyo02.lami.gpustandardminimal/files/artifacts/gpu_callback_raw_stream/gpu_callback_accumulated_final.txt",
                    "gpu_callback_raw_artifact_write_result" to "ok",
                    "gpu_callback_raw_passthrough" to "true",
                    "gpu_callback_raw_sha256" to "raw-sha",
                    "gpu_ui_text_sha256" to "ui-sha",
                    "gpu_callback_ui_identical" to "false",
                ),
            ),
        )

        assertTrue(compact.contains("gpu_callback_raw_artifact_enabled=true"))
        assertTrue(compact.contains("gpu_callback_raw_stream_artifact_dir="))
        assertTrue(compact.contains("gpu_callback_raw_full_artifact_path="))
        assertTrue(compact.contains("gpu_callback_accumulated_final_artifact_path="))
        assertTrue(compact.contains("gpu_callback_raw_passthrough=true"))
        assertTrue(compact.contains("gpu_callback_raw_sha256=raw-sha"))
        assertTrue(compact.contains("gpu_ui_text_sha256=ui-sha"))
        assertTrue(compact.contains("gpu_callback_ui_identical=false"))
    }

    private fun compactFromFlags(flags: LocalRouteDiagnosticFlags): String {
        val context = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery",
            selectedModelFile = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
            selectedModelPath = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = context,
            flags = flags,
            elapsedMs = 2_000L,
        )
        return buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "カレーの材料をお願いします。",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(localFailureDiagnosticsText = routeDiagnostics),
                status = "success",
                reason = "gpu_callback_streaming_success",
                routeContext = context,
            ),
        )
    }
}
