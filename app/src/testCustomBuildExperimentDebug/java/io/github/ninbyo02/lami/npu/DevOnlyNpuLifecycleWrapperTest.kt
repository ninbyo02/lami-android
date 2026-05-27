package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuLifecycleWrapperTest {
    @Test
    fun `run id scoped files include current run id`() {
        val files = DevOnlyNpuLifecycleWrapper.buildRunFiles(RUN_ID)

        assertTrue(files.stateFileName.contains(RUN_ID))
        assertTrue(files.resultFileName.contains(RUN_ID))
        assertTrue(files.nativeDiagFileName.contains(RUN_ID))
        assertTrue(files.cleanupFileName.contains(RUN_ID))
    }

    @Test
    fun `run id match accepts only current callback state result native diag and cleanup`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(validEvidence())

        assertEquals(DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN, decision.classification)
        assertTrue(decision.acceptsCurrentRun)
        assertTrue(decision.sessionReuseAllowed)
        assertFalse(decision.perRunIsolatedRequired)
    }

    @Test
    fun `stale result is rejected`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(
            validEvidence(runStartedAtMs = 2_000L, resultWrittenAtMs = 1_000L),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.STALE_RESULT_REJECTED, decision.classification)
        assertFalse(decision.acceptsCurrentRun)
        assertFalse(decision.sessionReuseAllowed)
        assertTrue(decision.perRunIsolatedRequired)
    }

    @Test
    fun `native diag run id mismatch is rejected`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(
            validEvidence(nativeDiagRunId = "previous-run"),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED, decision.classification)
        assertFalse(decision.acceptsCurrentRun)
    }

    @Test
    fun `callback run id mismatch is rejected`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(
            validEvidence(callbackRunId = "previous-run"),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED, decision.classification)
        assertFalse(decision.acceptsCurrentRun)
    }

    @Test
    fun `result run id mismatch is rejected`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(
            validEvidence(resultRunId = "previous-run"),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED, decision.classification)
        assertFalse(decision.acceptsCurrentRun)
    }

    @Test
    fun `unscoped file names are rejected`() {
        val files = DevOnlyNpuLifecycleFiles(
            stateFileName = "qairt244_hidden_state.txt",
            resultFileName = "qairt244_hidden_result.txt",
            nativeDiagFileName = "qairt244_hidden_native_diag.txt",
            cleanupFileName = "qairt244_hidden_cleanup.txt",
        )
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(validEvidence().copy(files = files))

        assertEquals(DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED, decision.classification)
        assertFalse(decision.acceptsCurrentRun)
    }

    @Test
    fun `cleanup evidence classifies success as clean`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(
            validEvidence(cleanupElapsedMs = 42L, engineCloseUniquePtrCleanup = true),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN, decision.classification)
        assertTrue(decision.acceptsCurrentRun)
    }

    @Test
    fun `failure with cleanup evidence is clean but not success`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(
            validEvidence(resultSuccess = false),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.FAILURE_CLEAN, decision.classification)
        assertTrue(decision.acceptsCurrentRun)
        assertTrue(decision.sessionReuseAllowed)
    }

    @Test
    fun `cleanup missing is suspect`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(
            validEvidence(cleanupElapsedMs = null),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT, decision.classification)
        assertFalse(decision.acceptsCurrentRun)
        assertFalse(decision.sessionReuseAllowed)
        assertTrue(decision.perRunIsolatedRequired)
    }

    @Test
    fun `Engine close missing is suspect`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(
            validEvidence(engineCloseUniquePtrCleanup = false),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT, decision.classification)
        assertFalse(decision.sessionReuseAllowed)
    }

    @Test
    fun `timeout is suspect and forbids session reuse`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(
            validEvidence(timeout = true),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.TIMEOUT_SUSPECT, decision.classification)
        assertFalse(decision.acceptsCurrentRun)
        assertFalse(decision.sessionReuseAllowed)
        assertTrue(decision.perRunIsolatedRequired)
    }

    @Test
    fun `side-effect flags must remain false`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(
            validEvidence(sideEffects = DevOnlyNpuLifecycleSideEffects(db = true)),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN, decision.classification)
        assertFalse(decision.sideEffectsClear)
        assertFalse(decision.acceptsCurrentRun)
        assertFalse(decision.sessionReuseAllowed)
    }

    @Test
    fun `H1 and max output policy remain pinned`() {
        assertEquals(128, DevOnlyNpuRouteAdapter.QAIRT244_H1_PINNED_MAX_OUTPUT_TOKENS)
        assertEquals(256, DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_EXPERIMENTAL_BASELINE_CANDIDATE_TOKENS)
        assertEquals(512, DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS)
        assertEquals(512, DevOnlyNpuRouteAdapter.QAIRT244_MAX_OUTPUT_TOKENS_COMPARE_LIMIT)
    }

    @Test
    fun `512 sequential remains non baseline and per-run isolated remains candidate`() {
        val sequential = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(
            executionIsolation = DevOnlyNpuExecutionIsolation.SEQUENTIAL,
        ))
        val isolated = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate())

        assertFalse(sequential.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.SEQUENTIAL_512_ROLLBACK, sequential.reason)
        assertTrue(isolated.allowed)
    }

    private fun validEvidence(
        runStartedAtMs: Long = 1_000L,
        resultWrittenAtMs: Long? = 1_001L,
        callbackRunId: String? = RUN_ID,
        stateRunId: String? = RUN_ID,
        resultRunId: String? = RUN_ID,
        nativeDiagRunId: String? = RUN_ID,
        cleanupRunId: String? = RUN_ID,
        resultSuccess: Boolean = true,
        timeout: Boolean = false,
        cleanupElapsedMs: Long? = 1L,
        engineCloseUniquePtrCleanup: Boolean = true,
        sideEffects: DevOnlyNpuLifecycleSideEffects = DevOnlyNpuLifecycleSideEffects(),
    ) = DevOnlyNpuLifecycleEvidence(
        runId = RUN_ID,
        files = DevOnlyNpuLifecycleWrapper.buildRunFiles(RUN_ID),
        callbackRunId = callbackRunId,
        stateRunId = stateRunId,
        resultRunId = resultRunId,
        nativeDiagRunId = nativeDiagRunId,
        cleanupRunId = cleanupRunId,
        runStartedAtMs = runStartedAtMs,
        resultWrittenAtMs = resultWrittenAtMs,
        resultSuccess = resultSuccess,
        timeout = timeout,
        cleanupElapsedMs = cleanupElapsedMs,
        engineCloseUniquePtrCleanup = engineCloseUniquePtrCleanup,
        sideEffects = sideEffects,
    )

    private fun validModeGate(
        executionIsolation: DevOnlyNpuExecutionIsolation = DevOnlyNpuExecutionIsolation.PER_RUN_FORCE_STOP,
    ) = DevOnlyNpuHiddenExperimentalModeGateInput(
        mode = DevOnlyNpuHiddenExperimentalMode.HIDDEN_PER_RUN_ISOLATED_512,
        executionIsolation = executionIsolation,
        maxOutputTokens = DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS,
        forceStopBeforeEachPrompt = true,
        forceStopAfterEachPrompt = true,
        runDecodeReached = true,
        setMaxOutputTokens512Evidence = true,
        qnnHtpFastRpcEvidence = true,
        engineCloseUniquePtrCleanup = true,
        cleanupEvidence = true,
        codeAwareSanitizer = true,
        codeIndentationPreserved = true,
        codeFenceCompleted = true,
    )

    private companion object {
        const val RUN_ID = "run-20260527-hidden-wrapper"
    }
}
