package io.github.ninbyo02.lami.npu

enum class DevOnlyNpuSequentialSoftResetGateReason {
    OK,
    RUN_ID_MISSING,
    RUN_ID_NOT_UNIQUE,
    CURRENT_RUN_REJECTED,
    TIMEOUT_SUSPECT,
    CLEANUP_MISSING_SUSPECT,
    NON_SUCCESS_CLEAN_CLASSIFICATION,
    SUSPECT_SESSION,
    REUSE_NOT_ALLOWED,
    PER_RUN_ISOLATED_REQUIRED,
    STALE_RESULT_REJECTED,
    RUN_ID_MISMATCH_REJECTED,
    SIDE_EFFECTS_NOT_CLEAR,
}

data class DevOnlyNpuSequentialSoftResetStep(
    val promptIndex: Int,
    val promptLabel: String,
    val summary: DevOnlyNpuLifecycleSummary,
)

data class DevOnlyNpuSequentialSoftResetGateResult(
    val sequenceCanContinue: Boolean,
    val stopPromptIndex: Int?,
    val reason: DevOnlyNpuSequentialSoftResetGateReason,
    val lifecycleClassifications: List<DevOnlyNpuLifecycleClassification>,
)

object DevOnlyNpuSequentialSoftResetGate {
    fun evaluate(steps: List<DevOnlyNpuSequentialSoftResetStep>): DevOnlyNpuSequentialSoftResetGateResult {
        val seenRunIds = mutableSetOf<String>()
        val classifications = steps.map { it.summary.lifecycleClassification }
        for (step in steps) {
            val summary = step.summary
            val reason = firstStopReason(summary, seenRunIds)
            if (reason != DevOnlyNpuSequentialSoftResetGateReason.OK) {
                return DevOnlyNpuSequentialSoftResetGateResult(
                    sequenceCanContinue = false,
                    stopPromptIndex = step.promptIndex,
                    reason = reason,
                    lifecycleClassifications = classifications,
                )
            }
            seenRunIds += summary.expectedRunId
        }
        return DevOnlyNpuSequentialSoftResetGateResult(
            sequenceCanContinue = true,
            stopPromptIndex = null,
            reason = DevOnlyNpuSequentialSoftResetGateReason.OK,
            lifecycleClassifications = classifications,
        )
    }

    private fun firstStopReason(
        summary: DevOnlyNpuLifecycleSummary,
        seenRunIds: Set<String>,
    ): DevOnlyNpuSequentialSoftResetGateReason {
        if (summary.expectedRunId.isBlank() || summary.expectedRunId == "unavailable") {
            return DevOnlyNpuSequentialSoftResetGateReason.RUN_ID_MISSING
        }
        if (summary.expectedRunId in seenRunIds) {
            return DevOnlyNpuSequentialSoftResetGateReason.RUN_ID_NOT_UNIQUE
        }
        if (summary.staleResultRejected) {
            return DevOnlyNpuSequentialSoftResetGateReason.STALE_RESULT_REJECTED
        }
        if (summary.runIdMismatchRejected) {
            return DevOnlyNpuSequentialSoftResetGateReason.RUN_ID_MISMATCH_REJECTED
        }
        if (summary.lifecycleClassification == DevOnlyNpuLifecycleClassification.TIMEOUT_SUSPECT) {
            return DevOnlyNpuSequentialSoftResetGateReason.TIMEOUT_SUSPECT
        }
        if (summary.lifecycleClassification == DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT) {
            return DevOnlyNpuSequentialSoftResetGateReason.CLEANUP_MISSING_SUSPECT
        }
        if (summary.lifecycleClassification != DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN) {
            return DevOnlyNpuSequentialSoftResetGateReason.NON_SUCCESS_CLEAN_CLASSIFICATION
        }
        if (summary.suspectSession) {
            return DevOnlyNpuSequentialSoftResetGateReason.SUSPECT_SESSION
        }
        if (!summary.acceptsCurrentRun) {
            return DevOnlyNpuSequentialSoftResetGateReason.CURRENT_RUN_REJECTED
        }
        if (!summary.reuseAllowed) {
            return DevOnlyNpuSequentialSoftResetGateReason.REUSE_NOT_ALLOWED
        }
        if (summary.perRunIsolatedRequired) {
            return DevOnlyNpuSequentialSoftResetGateReason.PER_RUN_ISOLATED_REQUIRED
        }
        if (!summary.sideEffectsClear) {
            return DevOnlyNpuSequentialSoftResetGateReason.SIDE_EFFECTS_NOT_CLEAR
        }
        return DevOnlyNpuSequentialSoftResetGateReason.OK
    }
}
