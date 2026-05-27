package io.github.ninbyo02.lami.npu

data class DevOnlyNpuLifecycleFiles(
    val stateFileName: String,
    val resultFileName: String,
    val nativeDiagFileName: String,
    val cleanupFileName: String,
)

data class DevOnlyNpuLifecycleSideEffects(
    val assistantMessageListInserted: Boolean = false,
    val selectedPathSaved: Boolean = false,
    val db: Boolean = false,
    val tts: Boolean = false,
    val markdown: Boolean = false,
    val streaming: Boolean = false,
) {
    val clear: Boolean
        get() = !assistantMessageListInserted &&
            !selectedPathSaved &&
            !db &&
            !tts &&
            !markdown &&
            !streaming
}

data class DevOnlyNpuLifecycleEvidence(
    val runId: String,
    val files: DevOnlyNpuLifecycleFiles,
    val callbackRunId: String? = runId,
    val stateRunId: String? = runId,
    val resultRunId: String? = runId,
    val nativeDiagRunId: String? = runId,
    val cleanupRunId: String? = runId,
    val runStartedAtMs: Long = 0L,
    val resultWrittenAtMs: Long? = runStartedAtMs,
    val startedMarker: Boolean = true,
    val terminalMarker: Boolean = true,
    val cleanupMarker: Boolean = true,
    val resultSuccess: Boolean = true,
    val timeout: Boolean = false,
    val cleanupElapsedMs: Long? = 1L,
    val engineCloseUniquePtrCleanup: Boolean = true,
    val staleResultDetected: Boolean = false,
    val sideEffects: DevOnlyNpuLifecycleSideEffects = DevOnlyNpuLifecycleSideEffects(),
    val maxOutputTokens: Int = DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS,
    val mode: DevOnlyNpuHiddenExperimentalMode = DevOnlyNpuHiddenExperimentalMode.HIDDEN_PER_RUN_ISOLATED_512,
    val executionIsolation: DevOnlyNpuExecutionIsolation = DevOnlyNpuExecutionIsolation.PER_RUN_FORCE_STOP,
)

enum class DevOnlyNpuLifecycleClassification {
    SUCCESS_CLEAN,
    FAILURE_CLEAN,
    TIMEOUT_SUSPECT,
    CLEANUP_MISSING_SUSPECT,
    STALE_RESULT_REJECTED,
    RUN_ID_MISMATCH_REJECTED,
}

data class DevOnlyNpuLifecycleDecision(
    val classification: DevOnlyNpuLifecycleClassification,
    val acceptsCurrentRun: Boolean,
    val sessionReuseAllowed: Boolean,
    val perRunIsolatedRequired: Boolean,
    val sideEffectsClear: Boolean,
)

object DevOnlyNpuLifecycleWrapper {
    fun buildRunFiles(runId: String): DevOnlyNpuLifecycleFiles {
        require(runId.isNotBlank()) { "runId is required" }
        return DevOnlyNpuLifecycleFiles(
            stateFileName = "qairt244_hidden_npu_${runId}_state.txt",
            resultFileName = "qairt244_hidden_npu_${runId}_result.txt",
            nativeDiagFileName = "qairt244_hidden_npu_${runId}_native_diag.txt",
            cleanupFileName = "qairt244_hidden_npu_${runId}_cleanup.txt",
        )
    }

    fun evaluate(evidence: DevOnlyNpuLifecycleEvidence): DevOnlyNpuLifecycleDecision {
        val classification = when {
            !runIdsMatch(evidence) -> DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED
            !filesAreRunIdScoped(evidence.runId, evidence.files) ->
                DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED
            evidence.staleResultDetected || resultPredatesRun(evidence) ->
                DevOnlyNpuLifecycleClassification.STALE_RESULT_REJECTED
            evidence.timeout -> DevOnlyNpuLifecycleClassification.TIMEOUT_SUSPECT
            cleanupMissing(evidence) -> DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT
            evidence.resultSuccess -> DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN
            else -> DevOnlyNpuLifecycleClassification.FAILURE_CLEAN
        }
        val clean = classification == DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN ||
            classification == DevOnlyNpuLifecycleClassification.FAILURE_CLEAN
        val accepted = clean && evidence.sideEffects.clear
        return DevOnlyNpuLifecycleDecision(
            classification = classification,
            acceptsCurrentRun = accepted,
            sessionReuseAllowed = accepted,
            perRunIsolatedRequired = !accepted,
            sideEffectsClear = evidence.sideEffects.clear,
        )
    }

    private fun runIdsMatch(evidence: DevOnlyNpuLifecycleEvidence): Boolean {
        if (evidence.runId.isBlank()) return false
        val observedRunIds = listOf(
            evidence.callbackRunId,
            evidence.stateRunId,
            evidence.resultRunId,
            evidence.nativeDiagRunId,
            evidence.cleanupRunId,
        )
        return observedRunIds.all { observed -> observed == null || observed == evidence.runId }
    }

    private fun filesAreRunIdScoped(runId: String, files: DevOnlyNpuLifecycleFiles): Boolean =
        files.stateFileName.contains(runId) &&
            files.resultFileName.contains(runId) &&
            files.nativeDiagFileName.contains(runId) &&
            files.cleanupFileName.contains(runId)

    private fun resultPredatesRun(evidence: DevOnlyNpuLifecycleEvidence): Boolean {
        val resultWrittenAtMs = evidence.resultWrittenAtMs ?: return false
        return resultWrittenAtMs < evidence.runStartedAtMs
    }

    private fun cleanupMissing(evidence: DevOnlyNpuLifecycleEvidence): Boolean =
        !evidence.startedMarker ||
            !evidence.terminalMarker ||
            !evidence.cleanupMarker ||
            evidence.cleanupElapsedMs == null ||
            evidence.cleanupElapsedMs < 0L ||
            !evidence.engineCloseUniquePtrCleanup
}
