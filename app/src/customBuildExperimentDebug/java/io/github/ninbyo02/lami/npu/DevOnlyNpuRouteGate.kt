package io.github.ninbyo02.lami.npu

data class DevOnlyNpuRouteGateInput(
    val customBuildExperiment: Boolean,
    val allowEditablePromptPreview: Boolean,
    val allowGuardedNpuRun: Boolean,
    val allowEditablePromptExecution: Boolean,
    val devCheckboxChecked: Boolean,
    val validatorValid: Boolean,
    val nativeEditablePromptSupported: Boolean,
    val running: Boolean,
    val maxOutputTokens: Int,
)

enum class DevOnlyNpuRouteGateReason {
    OK,
    NOT_CUSTOM_BUILD_EXPERIMENT,
    EDITABLE_PREVIEW_DISABLED,
    GUARDED_RUN_DISABLED,
    EDITABLE_PROMPT_EXECUTION_DISABLED,
    DEV_CHECKBOX_NOT_CHECKED,
    VALIDATOR_INVALID,
    NATIVE_PROMPT_UNSUPPORTED,
    RUN_ALREADY_IN_PROGRESS,
    INVALID_MAX_OUTPUT_TOKENS,
}

data class DevOnlyNpuRouteGateResult(
    val allowed: Boolean,
    val reason: DevOnlyNpuRouteGateReason,
)

interface DevOnlyNpuRouteGateEvaluator {
    fun evaluate(input: DevOnlyNpuRouteGateInput): DevOnlyNpuRouteGateResult
}

object DevOnlyNpuRouteGate : DevOnlyNpuRouteGateEvaluator {
    override fun evaluate(input: DevOnlyNpuRouteGateInput): DevOnlyNpuRouteGateResult {
        val reason = when {
            !input.customBuildExperiment -> DevOnlyNpuRouteGateReason.NOT_CUSTOM_BUILD_EXPERIMENT
            !input.allowEditablePromptPreview -> DevOnlyNpuRouteGateReason.EDITABLE_PREVIEW_DISABLED
            !input.allowGuardedNpuRun -> DevOnlyNpuRouteGateReason.GUARDED_RUN_DISABLED
            !input.allowEditablePromptExecution -> DevOnlyNpuRouteGateReason.EDITABLE_PROMPT_EXECUTION_DISABLED
            !input.devCheckboxChecked -> DevOnlyNpuRouteGateReason.DEV_CHECKBOX_NOT_CHECKED
            !input.validatorValid -> DevOnlyNpuRouteGateReason.VALIDATOR_INVALID
            !input.nativeEditablePromptSupported -> DevOnlyNpuRouteGateReason.NATIVE_PROMPT_UNSUPPORTED
            input.running -> DevOnlyNpuRouteGateReason.RUN_ALREADY_IN_PROGRESS
            input.maxOutputTokens != DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS ->
                DevOnlyNpuRouteGateReason.INVALID_MAX_OUTPUT_TOKENS
            else -> DevOnlyNpuRouteGateReason.OK
        }
        return DevOnlyNpuRouteGateResult(
            allowed = reason == DevOnlyNpuRouteGateReason.OK,
            reason = reason,
        )
    }
}
