package io.github.ninbyo02.lami.ui.screens.settings

/**
 * Describes which local inference backend should stay warm on this device.
 *
 * Design intent:
 * - On NPU-capable/healthy devices, keep NPU resident for short, interactive chat.
 * - Use GPU for long-context/high-token requests and as the first local fallback.
 * - Use CPU only as the last local fallback, and keep it short-lived.
 * - On devices without usable NPU, keep GPU resident instead.
 */
enum class ResidentInferenceBackend {
    NPU,
    GPU,
    CPU,
    SERVER,
}

data class LocalBackendCapability(
    val npuSupported: Boolean = false,
    val npuHealthy: Boolean = false,
    val gpuSupported: Boolean = false,
    val gpuHealthy: Boolean = false,
    val cpuSupported: Boolean = true,
    val cpuHealthy: Boolean = true,
) {
    val npuUsable: Boolean get() = npuSupported && npuHealthy
    val gpuUsable: Boolean get() = gpuSupported && gpuHealthy
    val cpuUsable: Boolean get() = cpuSupported && cpuHealthy
}

data class LocalInferenceResidencyPolicy(
    val residentBackend: ResidentInferenceBackend,
    val longContextBackend: ResidentInferenceBackend,
    val fallbackBackends: List<ResidentInferenceBackend>,
    val npuOutOfProcessRecommended: Boolean,
    val gpuIdleUnloadSeconds: Int,
    val cpuIdleUnloadSeconds: Int,
    val reason: String,
) {
    val keepsNpuResident: Boolean get() = residentBackend == ResidentInferenceBackend.NPU
    val keepsGpuResident: Boolean get() = residentBackend == ResidentInferenceBackend.GPU
}


data class LocalInferenceResidencyPolicySummary(
    val oneLine: String,
    val diagnosticLines: List<String>,
) {
    val diagnosticText: String get() = diagnosticLines.joinToString("\n")
}

fun LocalInferenceResidencyPolicy.toSummary(): LocalInferenceResidencyPolicySummary {
    val fallbackText = fallbackBackends.joinToString(",") { it.name }.ifBlank { "none" }
    val oneLine = "常駐: ${residentBackend.name} / 長文: ${longContextBackend.name} / fallback: $fallbackText"
    return LocalInferenceResidencyPolicySummary(
        oneLine = oneLine,
        diagnosticLines = listOf(
            "resident_backend=${residentBackend.name}",
            "long_context_backend=${longContextBackend.name}",
            "fallback_backends=$fallbackText",
            "npu_out_of_process_recommended=$npuOutOfProcessRecommended",
            "gpu_idle_unload_seconds=$gpuIdleUnloadSeconds",
            "cpu_idle_unload_seconds=$cpuIdleUnloadSeconds",
            "reason=$reason",
        ),
    )
}

object LocalInferenceResidencyPolicyResolver {
    private const val DEFAULT_GPU_IDLE_UNLOAD_SECONDS = 60
    private const val DEFAULT_CPU_IDLE_UNLOAD_SECONDS = 30

    fun resolve(capability: LocalBackendCapability): LocalInferenceResidencyPolicy {
        val fallbackBackends = buildList {
            if (capability.gpuUsable) add(ResidentInferenceBackend.GPU)
            if (capability.cpuUsable) add(ResidentInferenceBackend.CPU)
            add(ResidentInferenceBackend.SERVER)
        }.distinct()

        if (capability.npuUsable) {
            return LocalInferenceResidencyPolicy(
                residentBackend = ResidentInferenceBackend.NPU,
                longContextBackend = if (capability.gpuUsable) {
                    ResidentInferenceBackend.GPU
                } else if (capability.cpuUsable) {
                    ResidentInferenceBackend.CPU
                } else {
                    ResidentInferenceBackend.SERVER
                },
                fallbackBackends = fallbackBackends.filterNot { it == ResidentInferenceBackend.NPU },
                npuOutOfProcessRecommended = true,
                gpuIdleUnloadSeconds = DEFAULT_GPU_IDLE_UNLOAD_SECONDS,
                cpuIdleUnloadSeconds = DEFAULT_CPU_IDLE_UNLOAD_SECONDS,
                reason = "npu_supported_and_healthy_keep_npu_resident",
            )
        }

        if (capability.gpuUsable) {
            return LocalInferenceResidencyPolicy(
                residentBackend = ResidentInferenceBackend.GPU,
                longContextBackend = ResidentInferenceBackend.GPU,
                fallbackBackends = buildList {
                    if (capability.cpuUsable) add(ResidentInferenceBackend.CPU)
                    add(ResidentInferenceBackend.SERVER)
                },
                npuOutOfProcessRecommended = false,
                gpuIdleUnloadSeconds = 0,
                cpuIdleUnloadSeconds = DEFAULT_CPU_IDLE_UNLOAD_SECONDS,
                reason = if (capability.npuSupported) {
                    "npu_supported_but_unhealthy_keep_gpu_resident"
                } else {
                    "npu_unavailable_keep_gpu_resident"
                },
            )
        }

        if (capability.cpuUsable) {
            return LocalInferenceResidencyPolicy(
                residentBackend = ResidentInferenceBackend.CPU,
                longContextBackend = ResidentInferenceBackend.SERVER,
                fallbackBackends = listOf(ResidentInferenceBackend.SERVER),
                npuOutOfProcessRecommended = false,
                gpuIdleUnloadSeconds = DEFAULT_GPU_IDLE_UNLOAD_SECONDS,
                cpuIdleUnloadSeconds = 0,
                reason = "only_cpu_usable_keep_cpu_resident",
            )
        }

        return LocalInferenceResidencyPolicy(
            residentBackend = ResidentInferenceBackend.SERVER,
            longContextBackend = ResidentInferenceBackend.SERVER,
            fallbackBackends = emptyList(),
            npuOutOfProcessRecommended = false,
            gpuIdleUnloadSeconds = DEFAULT_GPU_IDLE_UNLOAD_SECONDS,
            cpuIdleUnloadSeconds = DEFAULT_CPU_IDLE_UNLOAD_SECONDS,
            reason = "no_local_backend_usable_use_server",
        )
    }
}

internal fun localBackendCapabilityForUserFacingSelection(
    selection: InferenceBackendSelection,
): LocalBackendCapability =
    when (selection) {
        InferenceBackendSelection.NPU -> LocalBackendCapability(
            npuSupported = true,
            npuHealthy = true,
            gpuSupported = true,
            gpuHealthy = true,
            cpuSupported = true,
            cpuHealthy = true,
        )
        InferenceBackendSelection.GPU,
        InferenceBackendSelection.AUTOMATIC -> LocalBackendCapability(
            npuSupported = false,
            npuHealthy = false,
            gpuSupported = true,
            gpuHealthy = true,
            cpuSupported = true,
            cpuHealthy = true,
        )
        InferenceBackendSelection.CPU -> LocalBackendCapability(
            npuSupported = false,
            npuHealthy = false,
            gpuSupported = false,
            gpuHealthy = false,
            cpuSupported = true,
            cpuHealthy = true,
        )
        else -> LocalBackendCapability(
            npuSupported = false,
            npuHealthy = false,
            gpuSupported = true,
            gpuHealthy = true,
            cpuSupported = true,
            cpuHealthy = true,
        )
    }

internal fun localInferenceResidencyPolicyForUserFacingSelection(
    selection: InferenceBackendSelection,
): LocalInferenceResidencyPolicy =
    LocalInferenceResidencyPolicyResolver.resolve(
        localBackendCapabilityForUserFacingSelection(selection),
    )

