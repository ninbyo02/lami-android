package io.github.ninbyo02.lami.npu

enum class DevOnlyNpuRuntimeReusePolicyReason {
    OK,
    TIMEOUT_SUSPECT,
    CLEANUP_MISSING_SUSPECT,
    STALE_RESULT_REJECTED,
    RUN_ID_MISMATCH_REJECTED,
    NON_SUCCESS_CLEAN_CLASSIFICATION,
    SUSPECT_SESSION,
    REUSE_NOT_ALLOWED,
    PER_RUN_ISOLATED_REQUIRED,
    CURRENT_RUN_REJECTED,
    SIDE_EFFECTS_NOT_CLEAR,
}

data class DevOnlyNpuRuntimeReusePolicyResult(
    val lifecycleClassification: DevOnlyNpuLifecycleClassification,
    val runtimeReuseAllowed: Boolean,
    val nextPromptAllowed: Boolean,
    val hiddenPerRunIsolatedRequired: Boolean,
    val suspectSession: Boolean,
    val reason: DevOnlyNpuRuntimeReusePolicyReason,
) {
    fun asKeyValues(): Map<String, String> = linkedMapOf(
        "lifecycle_classification" to lifecycleClassification.name,
        "runtime_reuse_allowed" to runtimeReuseAllowed.toString(),
        "next_prompt_allowed" to nextPromptAllowed.toString(),
        "hidden_per_run_isolated_required" to hiddenPerRunIsolatedRequired.toString(),
        "suspect_session" to suspectSession.toString(),
        "runtime_reuse_policy_reason" to reason.name,
    )
}

object DevOnlyNpuRuntimeReusePolicy {
    fun from(summary: DevOnlyNpuLifecycleSummary): DevOnlyNpuRuntimeReusePolicyResult {
        val reason = when {
            summary.lifecycleClassification == DevOnlyNpuLifecycleClassification.TIMEOUT_SUSPECT ->
                DevOnlyNpuRuntimeReusePolicyReason.TIMEOUT_SUSPECT
            summary.lifecycleClassification == DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT ->
                DevOnlyNpuRuntimeReusePolicyReason.CLEANUP_MISSING_SUSPECT
            summary.staleResultRejected ||
                summary.lifecycleClassification == DevOnlyNpuLifecycleClassification.STALE_RESULT_REJECTED ->
                DevOnlyNpuRuntimeReusePolicyReason.STALE_RESULT_REJECTED
            summary.runIdMismatchRejected ||
                summary.lifecycleClassification == DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED ->
                DevOnlyNpuRuntimeReusePolicyReason.RUN_ID_MISMATCH_REJECTED
            summary.lifecycleClassification != DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN ->
                DevOnlyNpuRuntimeReusePolicyReason.NON_SUCCESS_CLEAN_CLASSIFICATION
            summary.suspectSession -> DevOnlyNpuRuntimeReusePolicyReason.SUSPECT_SESSION
            !summary.nextPromptAllowed -> DevOnlyNpuRuntimeReusePolicyReason.REUSE_NOT_ALLOWED
            !summary.reuseAllowed -> DevOnlyNpuRuntimeReusePolicyReason.REUSE_NOT_ALLOWED
            summary.perRunIsolatedRequired -> DevOnlyNpuRuntimeReusePolicyReason.PER_RUN_ISOLATED_REQUIRED
            !summary.acceptsCurrentRun -> DevOnlyNpuRuntimeReusePolicyReason.CURRENT_RUN_REJECTED
            !summary.sideEffectsClear -> DevOnlyNpuRuntimeReusePolicyReason.SIDE_EFFECTS_NOT_CLEAR
            else -> DevOnlyNpuRuntimeReusePolicyReason.OK
        }
        val allowed = reason == DevOnlyNpuRuntimeReusePolicyReason.OK
        return DevOnlyNpuRuntimeReusePolicyResult(
            lifecycleClassification = summary.lifecycleClassification,
            runtimeReuseAllowed = allowed,
            nextPromptAllowed = allowed,
            hiddenPerRunIsolatedRequired = summary.perRunIsolatedRequired || !allowed,
            suspectSession = summary.suspectSession ||
                summary.lifecycleClassification == DevOnlyNpuLifecycleClassification.TIMEOUT_SUSPECT ||
                summary.lifecycleClassification == DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT,
            reason = reason,
        )
    }
}
