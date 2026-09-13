package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuIdlePrewarmContractTest {
    @Test
    fun `validated explicit GPU debug route is eligible`() {
        val decision = resolveGpuIdlePrewarmEligibility(eligibleInput())

        assertTrue(decision.eligible)
        assertEquals("eligible", decision.reason)
    }

    @Test
    fun `diagnostic is opt in and debug only`() {
        val disabled = resolveGpuIdlePrewarmEligibility(
            eligibleInput().copy(featureEnabled = false),
        )
        val release = resolveGpuIdlePrewarmEligibility(
            eligibleInput().copy(debugBuild = false),
        )

        assertFalse(disabled.eligible)
        assertEquals("diagnostic_disabled", disabled.reason)
        assertFalse(release.eligible)
        assertEquals("non_debug_build", release.reason)
    }
    @Test
    fun `non GPU active or background routes are blocked`() {
        val cpu = resolveGpuIdlePrewarmEligibility(
            eligibleInput().copy(preferredBackend = PreferredBackendDryRunSetting.CPU),
        )
        val active = resolveGpuIdlePrewarmEligibility(
            eligibleInput().copy(inferenceActive = true),
        )
        val background = resolveGpuIdlePrewarmEligibility(
            eligibleInput().copy(appInForeground = false),
        )

        assertEquals("gpu_not_explicitly_selected", cpu.reason)
        assertEquals("inference_active", active.reason)
        assertEquals("app_not_foreground", background.reason)
    }

    @Test
    fun `unverified runtime or model identity is blocked`() {
        val runtime = resolveGpuIdlePrewarmEligibility(
            eligibleInput().copy(verifiedGpuRuntime = false),
        )
        val model = resolveGpuIdlePrewarmEligibility(
            eligibleInput().copy(modelSizeBytes = VERIFIED_GPU_PREWARM_MODEL_BYTES - 1L),
        )

        assertEquals("gpu_runtime_not_verified", runtime.reason)
        assertEquals("model_identity_not_validated", model.reason)
    }
    @Test
    fun `cancellation generation invalidates active request`() {
        val before = GpuIdlePrewarmCancellationSignal.snapshot()

        GpuIdlePrewarmCancellationSignal.cancel("navigation")

        assertFalse(GpuIdlePrewarmCancellationSignal.isCurrent(before))
        assertEquals("navigation", GpuIdlePrewarmCancellationSignal.reason())
    }

    private fun eligibleInput() = GpuIdlePrewarmEligibilityInput(
        debugBuild = true,
        featureEnabled = true,
        localTargetSelected = true,
        preferredBackend = PreferredBackendDryRunSetting.GPU,
        inferenceActive = false,
        appInForeground = true,
        verifiedGpuRuntime = true,
        modelExists = true,
        modelSizeBytes = VERIFIED_GPU_PREWARM_MODEL_BYTES,
    )
}
