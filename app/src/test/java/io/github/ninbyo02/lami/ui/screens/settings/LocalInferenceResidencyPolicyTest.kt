package io.github.ninbyo02.lami.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInferenceResidencyPolicyTest {
    @Test
    fun keepsNpuResidentWhenNpuIsSupportedAndHealthy() {
        val policy = LocalInferenceResidencyPolicyResolver.resolve(
            LocalBackendCapability(
                npuSupported = true,
                npuHealthy = true,
                gpuSupported = true,
                gpuHealthy = true,
                cpuSupported = true,
                cpuHealthy = true,
            ),
        )

        assertEquals(ResidentInferenceBackend.NPU, policy.residentBackend)
        assertEquals(ResidentInferenceBackend.GPU, policy.longContextBackend)
        assertEquals(
            listOf(ResidentInferenceBackend.GPU, ResidentInferenceBackend.CPU, ResidentInferenceBackend.SERVER),
            policy.fallbackBackends,
        )
        assertTrue(policy.npuOutOfProcessRecommended)
        assertEquals(60, policy.gpuIdleUnloadSeconds)
        assertEquals(30, policy.cpuIdleUnloadSeconds)
        assertEquals("npu_supported_and_healthy_keep_npu_resident", policy.reason)
    }

    @Test
    fun keepsGpuResidentWhenNpuIsUnavailable() {
        val policy = LocalInferenceResidencyPolicyResolver.resolve(
            LocalBackendCapability(
                npuSupported = false,
                npuHealthy = false,
                gpuSupported = true,
                gpuHealthy = true,
                cpuSupported = true,
                cpuHealthy = true,
            ),
        )

        assertEquals(ResidentInferenceBackend.GPU, policy.residentBackend)
        assertEquals(ResidentInferenceBackend.GPU, policy.longContextBackend)
        assertEquals(
            listOf(ResidentInferenceBackend.CPU, ResidentInferenceBackend.SERVER),
            policy.fallbackBackends,
        )
        assertFalse(policy.npuOutOfProcessRecommended)
        assertEquals(0, policy.gpuIdleUnloadSeconds)
        assertEquals("npu_unavailable_keep_gpu_resident", policy.reason)
    }

    @Test
    fun keepsGpuResidentWhenNpuIsSupportedButUnhealthy() {
        val policy = LocalInferenceResidencyPolicyResolver.resolve(
            LocalBackendCapability(
                npuSupported = true,
                npuHealthy = false,
                gpuSupported = true,
                gpuHealthy = true,
                cpuSupported = true,
                cpuHealthy = true,
            ),
        )

        assertEquals(ResidentInferenceBackend.GPU, policy.residentBackend)
        assertEquals("npu_supported_but_unhealthy_keep_gpu_resident", policy.reason)
    }

    @Test
    fun fallsBackToCpuResidentWhenOnlyCpuIsUsable() {
        val policy = LocalInferenceResidencyPolicyResolver.resolve(
            LocalBackendCapability(
                npuSupported = false,
                npuHealthy = false,
                gpuSupported = false,
                gpuHealthy = false,
                cpuSupported = true,
                cpuHealthy = true,
            ),
        )

        assertEquals(ResidentInferenceBackend.CPU, policy.residentBackend)
        assertEquals(ResidentInferenceBackend.SERVER, policy.longContextBackend)
        assertEquals(listOf(ResidentInferenceBackend.SERVER), policy.fallbackBackends)
        assertEquals("only_cpu_usable_keep_cpu_resident", policy.reason)
    }

    @Test
    fun usesServerWhenNoLocalBackendIsUsable() {
        val policy = LocalInferenceResidencyPolicyResolver.resolve(
            LocalBackendCapability(
                npuSupported = false,
                npuHealthy = false,
                gpuSupported = false,
                gpuHealthy = false,
                cpuSupported = false,
                cpuHealthy = false,
            ),
        )

        assertEquals(ResidentInferenceBackend.SERVER, policy.residentBackend)
        assertEquals(ResidentInferenceBackend.SERVER, policy.longContextBackend)
        assertTrue(policy.fallbackBackends.isEmpty())
        assertEquals("no_local_backend_usable_use_server", policy.reason)
    }


    @Test
    fun buildsHumanReadableSummaryForSettingsDiagnostics() {
        val policy = LocalInferenceResidencyPolicyResolver.resolve(
            LocalBackendCapability(
                npuSupported = true,
                npuHealthy = true,
                gpuSupported = true,
                gpuHealthy = true,
            ),
        )

        val summary = policy.toSummary()

        assertEquals("常駐: NPU / 長文: GPU / fallback: GPU,CPU,SERVER", summary.oneLine)
        assertTrue(summary.diagnosticLines.contains("resident_backend=NPU"))
        assertTrue(summary.diagnosticLines.contains("long_context_backend=GPU"))
        assertTrue(summary.diagnosticLines.contains("fallback_backends=GPU,CPU,SERVER"))
        assertTrue(summary.diagnosticLines.contains("npu_out_of_process_recommended=true"))
        assertTrue(summary.diagnosticText.contains("reason=npu_supported_and_healthy_keep_npu_resident"))
    }


    @Test
    fun resolvesSettingsSelectionToDisplayOnlyResidencyPolicy() {
        assertEquals(
            ResidentInferenceBackend.NPU,
            localInferenceResidencyPolicyForUserFacingSelection(
                InferenceBackendSelection.NPU,
            ).residentBackend,
        )
        assertEquals(
            ResidentInferenceBackend.GPU,
            localInferenceResidencyPolicyForUserFacingSelection(
                InferenceBackendSelection.AUTOMATIC,
            ).residentBackend,
        )
        assertEquals(
            ResidentInferenceBackend.GPU,
            localInferenceResidencyPolicyForUserFacingSelection(
                InferenceBackendSelection.GPU,
            ).residentBackend,
        )
        assertEquals(
            ResidentInferenceBackend.CPU,
            localInferenceResidencyPolicyForUserFacingSelection(
                InferenceBackendSelection.CPU,
            ).residentBackend,
        )
    }


    @Test
    fun dryRunKeepsInteractiveRequestsOnResidentBackend() {
        val policy = LocalInferenceResidencyPolicyResolver.resolve(
            LocalBackendCapability(
                npuSupported = true,
                npuHealthy = true,
                gpuSupported = true,
                gpuHealthy = true,
            ),
        )

        val decision = policy.dryRunRoutingDecision(
            LocalInferenceRoutingDryRunInput(
                promptTokenEstimate = 128,
                requestedOutputTokens = 256,
                longContextTokenThreshold = 2048,
            ),
        )

        assertEquals(ResidentInferenceBackend.NPU, decision.selectedBackend)
        assertEquals("interactive_use_npu", decision.reason)
        assertEquals(128, decision.estimatedTotalTokens)
        assertTrue(decision.diagnosticLines.contains("dry_run_selected_backend=NPU"))
    }

    @Test
    fun dryRunRoutesLongContextRequestsToLongContextBackend() {
        val policy = LocalInferenceResidencyPolicyResolver.resolve(
            LocalBackendCapability(
                npuSupported = true,
                npuHealthy = true,
                gpuSupported = true,
                gpuHealthy = true,
            ),
        )

        val decision = policy.dryRunRoutingDecision(
            LocalInferenceRoutingDryRunInput(
                promptTokenEstimate = 2200,
                requestedOutputTokens = 512,
                longContextTokenThreshold = 2048,
            ),
        )

        assertEquals(ResidentInferenceBackend.GPU, decision.selectedBackend)
        assertEquals("long_context_use_gpu", decision.reason)
        assertEquals(2200, decision.estimatedTotalTokens)
        assertTrue(decision.diagnosticText.contains("dry_run_fallback_backends=GPU,CPU,SERVER"))
    }

    @Test
    fun dryRunDoesNotTreatLargeOutputCeilingAsLongContext() {
        val policy = LocalInferenceResidencyPolicyResolver.resolve(
            LocalBackendCapability(
                npuSupported = true,
                npuHealthy = true,
                gpuSupported = true,
                gpuHealthy = true,
            ),
        )

        val decision = policy.dryRunRoutingDecision(
            LocalInferenceRoutingDryRunInput(
                promptTokenEstimate = 5,
                requestedOutputTokens = 4096,
                longContextTokenThreshold = 2048,
            ),
        )

        assertEquals(ResidentInferenceBackend.NPU, decision.selectedBackend)
        assertEquals("interactive_use_npu", decision.reason)
        assertEquals(5, decision.estimatedTotalTokens)
    }


    @Test
    fun residentRouterHookKeepsCurrentBackendWhenDisabled() {
        val policy = LocalInferenceResidencyPolicyResolver.resolve(
            LocalBackendCapability(
                npuSupported = true,
                npuHealthy = true,
                gpuSupported = true,
                gpuHealthy = true,
            ),
        )

        val decision = policy.resolveResidentRouterHookDecision(
            LocalInferenceRoutingHookInput(
                currentBackend = PreferredBackendDryRunSetting.GPU,
                dryRunInput = LocalInferenceRoutingDryRunInput(
                    promptTokenEstimate = 32,
                    requestedOutputTokens = 128,
                ),
                enabled = false,
            ),
        )

        assertEquals(PreferredBackendDryRunSetting.GPU, decision.selectedBackend)
        assertFalse(decision.applied)
        assertEquals("disabled_keep_current_backend", decision.reason)
        assertTrue(decision.diagnosticLines.contains("resident_router_hook_enabled=false"))
        assertTrue(decision.diagnosticLines.contains("dry_run_selected_backend=NPU"))
    }

    @Test
    fun residentRouterHookAppliesDryRunBackendOnlyWhenEnabled() {
        val policy = LocalInferenceResidencyPolicyResolver.resolve(
            LocalBackendCapability(
                npuSupported = true,
                npuHealthy = true,
                gpuSupported = true,
                gpuHealthy = true,
            ),
        )

        val decision = policy.resolveResidentRouterHookDecision(
            LocalInferenceRoutingHookInput(
                currentBackend = PreferredBackendDryRunSetting.NPU,
                dryRunInput = LocalInferenceRoutingDryRunInput(
                    promptTokenEstimate = 2200,
                    requestedOutputTokens = 512,
                    longContextTokenThreshold = 2048,
                ),
                enabled = true,
            ),
        )

        assertEquals(PreferredBackendDryRunSetting.GPU, decision.selectedBackend)
        assertTrue(decision.applied)
        assertEquals("long_context_use_gpu", decision.reason)
        assertTrue(decision.diagnosticLines.contains("resident_router_hook_selected_backend=GPU"))
    }

}
