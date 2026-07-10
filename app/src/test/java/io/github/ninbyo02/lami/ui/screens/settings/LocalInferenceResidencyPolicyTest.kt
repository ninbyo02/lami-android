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
}
