package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal const val VERIFIED_GPU_PREWARM_MODEL_BYTES = 2_583_085_056L

internal data class GpuIdlePrewarmEligibilityInput(
    val debugBuild: Boolean,
    val featureEnabled: Boolean,
    val localTargetSelected: Boolean,
    val preferredBackend: PreferredBackendDryRunSetting,
    val inferenceActive: Boolean,
    val appInForeground: Boolean,
    val verifiedGpuRuntime: Boolean,
    val modelExists: Boolean,
    val modelSizeBytes: Long,
)

internal data class GpuIdlePrewarmEligibility(
    val eligible: Boolean,
    val reason: String,
)

internal fun resolveGpuIdlePrewarmEligibility(
    input: GpuIdlePrewarmEligibilityInput,
): GpuIdlePrewarmEligibility = when {
    !input.debugBuild -> GpuIdlePrewarmEligibility(false, "non_debug_build")
    !input.featureEnabled -> GpuIdlePrewarmEligibility(false, "diagnostic_disabled")
    !input.localTargetSelected -> GpuIdlePrewarmEligibility(false, "non_local_target")
    input.preferredBackend != PreferredBackendDryRunSetting.GPU ->
        GpuIdlePrewarmEligibility(false, "gpu_not_explicitly_selected")
    input.inferenceActive -> GpuIdlePrewarmEligibility(false, "inference_active")
    !input.appInForeground -> GpuIdlePrewarmEligibility(false, "app_not_foreground")
    !input.verifiedGpuRuntime -> GpuIdlePrewarmEligibility(false, "gpu_runtime_not_verified")
    !input.modelExists -> GpuIdlePrewarmEligibility(false, "model_missing")
    input.modelSizeBytes != VERIFIED_GPU_PREWARM_MODEL_BYTES ->
        GpuIdlePrewarmEligibility(false, "model_identity_not_validated")
    else -> GpuIdlePrewarmEligibility(true, "eligible")
}

internal data class GpuIdlePrewarmRequest(
    val modelPath: String,
    val cacheDirPath: String,
    val preferredBackend: PreferredBackendDryRunSetting,
    val localTargetSelected: Boolean,
    val inferenceActive: Boolean,
)

internal object GpuIdlePrewarmCancellationSignal {
    private val generation = AtomicLong(0L)
    private val lastReason = AtomicReference("none")

    fun snapshot(): Long = generation.get()
    fun cancel(reason: String): Long {
        lastReason.set(reason)
        return generation.incrementAndGet()
    }

    fun isCurrent(snapshot: Long): Boolean = generation.get() == snapshot

    fun reason(): String = lastReason.get()
}

internal data class GpuIdlePrewarmAcquireResult(
    val status: String,
    val engineCreateMs: Long? = null,
    val failureStage: String? = null,
    val failureClassName: String? = null,
    val failureMessage: String? = null,
)
