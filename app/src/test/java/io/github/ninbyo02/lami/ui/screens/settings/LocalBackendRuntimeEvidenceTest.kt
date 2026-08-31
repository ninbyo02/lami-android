package io.github.ninbyo02.lami.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalBackendRuntimeEvidenceTest {
    @Test
    fun npuSelectionWithoutRuntimeEvidenceFailsClosedToGpu() {
        val policy = localInferenceResidencyPolicyForUserFacingSelection(
            selection = InferenceBackendSelection.NPU,
            runtimeEvidence = LocalBackendRuntimeEvidence(),
        )

        assertEquals(ResidentInferenceBackend.GPU, policy.residentBackend)
        assertEquals("npu_unavailable_keep_gpu_resident", policy.reason)
    }

    @Test
    fun observedHealthyNpuRoutesShortContextToNpuAndLongContextToGpu() {
        val policy = localInferenceResidencyPolicyForUserFacingSelection(
            selection = InferenceBackendSelection.NPU,
            runtimeEvidence = LocalBackendRuntimeEvidence(
                npuSupported = true,
                npuHealthy = true,
            ),
        )

        val short = policy.dryRunRoutingDecision(
            LocalInferenceRoutingDryRunInput(
                promptTokenEstimate = 32,
                requestedOutputTokens = 4096,
            ),
        )
        val long = policy.dryRunRoutingDecision(
            LocalInferenceRoutingDryRunInput(
                promptTokenEstimate = 2200,
                requestedOutputTokens = 512,
            ),
        )

        assertEquals(ResidentInferenceBackend.NPU, short.selectedBackend)
        assertEquals("interactive_use_npu", short.reason)
        assertEquals(ResidentInferenceBackend.GPU, long.selectedBackend)
        assertEquals("long_context_use_gpu", long.reason)
    }

    @Test
    fun supportedButUnhealthyNpuFallsBackToGpuThenCpu() {
        val policy = localInferenceResidencyPolicyForUserFacingSelection(
            selection = InferenceBackendSelection.NPU,
            runtimeEvidence = LocalBackendRuntimeEvidence(
                npuSupported = true,
                npuHealthy = false,
            ),
        )

        assertEquals(ResidentInferenceBackend.GPU, policy.residentBackend)
        assertEquals(
            listOf(ResidentInferenceBackend.CPU, ResidentInferenceBackend.SERVER),
            policy.fallbackBackends,
        )
    }

    @Test
    fun completeLatestNpuSuccessForCurrentModelProducesHealthyEvidence() {
        val evidence = LocalNpuRuntimeHistorySnapshot(
            latestModelPath = "/models/npu.litertlm",
            latestStatus = "success",
            latestReason = "success",
            latestStage = "none",
            latestBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
            latestRunDecodeReached = "true",
            latestNativeCallReturned = "true",
            latestNativeDecodeFinished = "true",
            latestSuccessCriteriaMet = "true",
            successfulRequestCount = 3,
        ).toLocalBackendRuntimeEvidence(
            currentNpuModelPath = "/models/npu.litertlm",
        )

        assertEquals(true, evidence.npuSupported)
        assertEquals(true, evidence.npuHealthy)
    }

    @Test
    fun latestRunningFailureOrExceptionOverridesEarlierSuccessCount() {
        listOf("running", "failure", "exception").forEach { latestStatus ->
            val evidence = LocalNpuRuntimeHistorySnapshot(
                latestModelPath = "/models/npu.litertlm",
                latestStatus = latestStatus,
                latestReason = if (latestStatus == "running") "running" else "failed",
                latestStage = if (latestStatus == "running") "request_started" else "adapter_failure",
                latestBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                latestRunDecodeReached = "true",
                latestNativeCallReturned = "true",
                latestNativeDecodeFinished = "true",
                latestSuccessCriteriaMet = "true",
                successfulRequestCount = 8,
            ).toLocalBackendRuntimeEvidence(
                currentNpuModelPath = "/models/npu.litertlm",
            )

            assertEquals("latestStatus=$latestStatus", false, evidence.npuHealthy)
        }
    }

    @Test
    fun incompleteNativeOrBackendEvidenceFailsClosed() {
        val incompleteSnapshots = listOf(
            LocalNpuRuntimeHistorySnapshot(
                latestModelPath = "/models/npu.litertlm",
                latestStatus = "success",
                latestReason = "success",
                latestStage = "none",
                latestBackendEvidence = "unavailable",
                latestRunDecodeReached = "true",
                latestNativeCallReturned = "true",
                latestNativeDecodeFinished = "true",
                latestSuccessCriteriaMet = "true",
                successfulRequestCount = 1,
            ),
            LocalNpuRuntimeHistorySnapshot(
                latestModelPath = "/models/npu.litertlm",
                latestStatus = "success",
                latestReason = "success",
                latestStage = "none",
                latestBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                latestRunDecodeReached = "false",
                latestNativeCallReturned = "true",
                latestNativeDecodeFinished = "true",
                latestSuccessCriteriaMet = "true",
                successfulRequestCount = 1,
            ),
            LocalNpuRuntimeHistorySnapshot(
                latestModelPath = "/models/npu.litertlm",
                latestStatus = "success",
                latestReason = "success",
                latestStage = "none",
                latestBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                latestRunDecodeReached = "true",
                latestNativeCallReturned = "true",
                latestNativeDecodeFinished = "false",
                latestSuccessCriteriaMet = "true",
                successfulRequestCount = 1,
            ),
        )

        incompleteSnapshots.forEach { snapshot ->
            val evidence = snapshot.toLocalBackendRuntimeEvidence("/models/npu.litertlm")
            assertEquals(false, evidence.npuHealthy)
        }
    }

    @Test
    fun modelMismatchFailsClosed() {
        val evidence = LocalNpuRuntimeHistorySnapshot(
            latestModelPath = "/models/old.litertlm",
            latestStatus = "success",
            latestReason = "success",
            latestStage = "none",
            latestBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
            latestRunDecodeReached = "true",
            latestNativeCallReturned = "true",
            latestNativeDecodeFinished = "true",
            latestSuccessCriteriaMet = "true",
            successfulRequestCount = 4,
        ).toLocalBackendRuntimeEvidence("/models/current.litertlm")

        assertEquals(false, evidence.npuHealthy)
    }
}
