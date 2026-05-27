package io.github.ninbyo02.lami.npu

data class DevOnlyNpuLifecycleSummary(
    val lifecycleClassification: DevOnlyNpuLifecycleClassification,
    val acceptsCurrentRun: Boolean,
    val reuseAllowed: Boolean,
    val suspectSession: Boolean,
    val perRunIsolatedRequired: Boolean,
    val expectedRunId: String,
    val observedRunId: String,
    val cleanupElapsedMs: String,
    val engineCloseEvidence: Boolean,
    val staleResultRejected: Boolean,
    val runIdMismatchRejected: Boolean,
    val sideEffectsClear: Boolean,
) {
    fun asKeyValues(): Map<String, String> = linkedMapOf(
        "lifecycle_classification" to lifecycleClassification.name,
        "accepts_current_run" to acceptsCurrentRun.toString(),
        "reuse_allowed" to reuseAllowed.toString(),
        "suspect_session" to suspectSession.toString(),
        "per_run_isolated_required" to perRunIsolatedRequired.toString(),
        "hidden_per_run_isolated_required" to perRunIsolatedRequired.toString(),
        "expected_run_id" to expectedRunId,
        "observed_run_id" to observedRunId,
        "cleanup_elapsed_ms" to cleanupElapsedMs,
        "engine_close_evidence" to engineCloseEvidence.toString(),
        "stale_result_rejected" to staleResultRejected.toString(),
        "run_id_mismatch_rejected" to runIdMismatchRejected.toString(),
        "side_effects_clear" to sideEffectsClear.toString(),
    )
}

object DevOnlyNpuLifecycleSummaryBuilder {
    fun fromParserResult(result: DevOnlyNpuLifecycleArtifactParserResult): DevOnlyNpuLifecycleSummary {
        val evidence = result.evidence
        val classification = result.classification
        return DevOnlyNpuLifecycleSummary(
            lifecycleClassification = classification,
            acceptsCurrentRun = result.decision.acceptsCurrentRun,
            reuseAllowed = result.decision.sessionReuseAllowed,
            suspectSession = classification == DevOnlyNpuLifecycleClassification.TIMEOUT_SUSPECT ||
                classification == DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT,
            perRunIsolatedRequired = result.decision.perRunIsolatedRequired,
            expectedRunId = evidence.runId,
            observedRunId = observedRunIds(evidence),
            cleanupElapsedMs = evidence.cleanupElapsedMs?.toString() ?: "missing",
            engineCloseEvidence = evidence.engineCloseUniquePtrCleanup,
            staleResultRejected = classification == DevOnlyNpuLifecycleClassification.STALE_RESULT_REJECTED,
            runIdMismatchRejected = classification == DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED,
            sideEffectsClear = result.decision.sideEffectsClear,
        )
    }

    private fun observedRunIds(evidence: DevOnlyNpuLifecycleEvidence): String =
        listOfNotNull(
            evidence.callbackRunId,
            evidence.stateRunId,
            evidence.resultRunId,
            evidence.nativeDiagRunId,
            evidence.cleanupRunId,
        ).distinct().joinToString(",").ifBlank { "unavailable" }
}
