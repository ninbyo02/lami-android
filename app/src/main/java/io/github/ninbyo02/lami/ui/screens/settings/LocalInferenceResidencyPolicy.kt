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


data class LocalInferenceRoutingDryRunInput(
    val promptTokenEstimate: Int? = null,
    val requestedOutputTokens: Int? = null,
    val longContextTokenThreshold: Int = 2048,
) {
    // requestedOutputTokens is a capacity ceiling, not observed context usage.
    // Including it makes a default ceiling such as 4096 route even tiny prompts to GPU.
    val estimatedTotalTokens: Int?
        get() = promptTokenEstimate
}

data class LocalInferenceRoutingDryRunDecision(
    val selectedBackend: ResidentInferenceBackend,
    val reason: String,
    val estimatedTotalTokens: Int?,
    val longContextThreshold: Int,
    val fallbackBackends: List<ResidentInferenceBackend>,
) {
    val diagnosticLines: List<String>
        get() = listOf(
            "dry_run_selected_backend=${selectedBackend.name}",
            "dry_run_reason=$reason",
            "dry_run_estimated_total_tokens=${estimatedTotalTokens ?: "unknown"}",
            "dry_run_long_context_threshold=$longContextThreshold",
            "dry_run_fallback_backends=${fallbackBackends.joinToString(",") { it.name }.ifBlank { "none" }}",
        )

    val diagnosticText: String get() = diagnosticLines.joinToString("\n")
}

fun LocalInferenceResidencyPolicy.dryRunRoutingDecision(
    input: LocalInferenceRoutingDryRunInput,
): LocalInferenceRoutingDryRunDecision {
    val estimatedTotal = input.estimatedTotalTokens
    val longContext = estimatedTotal != null && estimatedTotal >= input.longContextTokenThreshold
    val selected = if (longContext) longContextBackend else residentBackend
    val reason = if (longContext) {
        "long_context_use_${longContextBackend.name.lowercase()}"
    } else {
        "interactive_use_${residentBackend.name.lowercase()}"
    }
    return LocalInferenceRoutingDryRunDecision(
        selectedBackend = selected,
        reason = reason,
        estimatedTotalTokens = estimatedTotal,
        longContextThreshold = input.longContextTokenThreshold,
        fallbackBackends = fallbackBackends,
    )
}


data class LocalInferenceRoutingHookInput(
    val currentBackend: PreferredBackendDryRunSetting,
    val dryRunInput: LocalInferenceRoutingDryRunInput,
    val enabled: Boolean = false,
)

data class LocalInferenceRoutingHookDecision(
    val selectedBackend: PreferredBackendDryRunSetting,
    val dryRunDecision: LocalInferenceRoutingDryRunDecision,
    val enabled: Boolean,
    val applied: Boolean,
    val reason: String,
) {
    val diagnosticLines: List<String>
        get() = listOf(
            "resident_router_hook_enabled=$enabled",
            "resident_router_hook_applied=$applied",
            "resident_router_hook_selected_backend=${selectedBackend.name}",
            "resident_router_hook_reason=$reason",
        ) + dryRunDecision.diagnosticLines
}

fun LocalInferenceResidencyPolicy.resolveResidentRouterHookDecision(
    input: LocalInferenceRoutingHookInput,
): LocalInferenceRoutingHookDecision {
    val dryRunDecision = dryRunRoutingDecision(input.dryRunInput)
    if (!input.enabled) {
        return LocalInferenceRoutingHookDecision(
            selectedBackend = input.currentBackend,
            dryRunDecision = dryRunDecision,
            enabled = false,
            applied = false,
            reason = "disabled_keep_current_backend",
        )
    }
    val routedBackend = dryRunDecision.selectedBackend.toPreferredBackendDryRunSettingOrNull()
    if (routedBackend == null) {
        return LocalInferenceRoutingHookDecision(
            selectedBackend = input.currentBackend,
            dryRunDecision = dryRunDecision,
            enabled = true,
            applied = false,
            reason = "dry_run_selected_non_local_backend_keep_current",
        )
    }
    return LocalInferenceRoutingHookDecision(
        selectedBackend = routedBackend,
        dryRunDecision = dryRunDecision,
        enabled = true,
        applied = routedBackend != input.currentBackend,
        reason = dryRunDecision.reason,
    )
}

private fun ResidentInferenceBackend.toPreferredBackendDryRunSettingOrNull(): PreferredBackendDryRunSetting? =
    when (this) {
        ResidentInferenceBackend.NPU -> PreferredBackendDryRunSetting.NPU
        ResidentInferenceBackend.GPU -> PreferredBackendDryRunSetting.GPU
        ResidentInferenceBackend.CPU -> PreferredBackendDryRunSetting.CPU
        ResidentInferenceBackend.SERVER -> null
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

