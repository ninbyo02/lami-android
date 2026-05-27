package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuSequentialSoftResetGateTest {
    @Test
    fun `all success clean summaries allow sequence to continue`() {
        val result = DevOnlyNpuSequentialSoftResetGate.evaluate(
            listOf(
                step(1, "konnichiwa", cleanSummary("run-1")),
                step(2, "python_calculator", cleanSummary("run-2")),
                step(3, "lami_npu_short", cleanSummary("run-3")),
            ),
        )

        assertTrue(result.sequenceCanContinue)
        assertEquals(null, result.stopPromptIndex)
        assertEquals(DevOnlyNpuSequentialSoftResetGateReason.OK, result.reason)
    }

    @Test
    fun `timeout suspect stops the sequence`() {
        val result = evaluateWithSecond(timeoutSummary("run-2"))

        assertFalse(result.sequenceCanContinue)
        assertEquals(2, result.stopPromptIndex)
        assertEquals(DevOnlyNpuSequentialSoftResetGateReason.TIMEOUT_SUSPECT, result.reason)
    }

    @Test
    fun `cleanup missing suspect stops the sequence`() {
        val result = evaluateWithSecond(cleanupMissingSummary("run-2"))

        assertFalse(result.sequenceCanContinue)
        assertEquals(2, result.stopPromptIndex)
        assertEquals(DevOnlyNpuSequentialSoftResetGateReason.CLEANUP_MISSING_SUSPECT, result.reason)
    }

    @Test
    fun `run id mismatch rejected stops the sequence`() {
        val result = evaluateWithSecond(
            cleanSummary(
                runId = "run-2",
                classification = DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED,
                acceptsCurrentRun = false,
                reuseAllowed = false,
                perRunIsolatedRequired = true,
                runIdMismatchRejected = true,
            ),
        )

        assertFalse(result.sequenceCanContinue)
        assertEquals(2, result.stopPromptIndex)
        assertEquals(DevOnlyNpuSequentialSoftResetGateReason.RUN_ID_MISMATCH_REJECTED, result.reason)
    }

    @Test
    fun `stale result rejected stops the sequence`() {
        val result = evaluateWithSecond(
            cleanSummary(
                runId = "run-2",
                classification = DevOnlyNpuLifecycleClassification.STALE_RESULT_REJECTED,
                acceptsCurrentRun = false,
                reuseAllowed = false,
                perRunIsolatedRequired = true,
                staleResultRejected = true,
            ),
        )

        assertFalse(result.sequenceCanContinue)
        assertEquals(2, result.stopPromptIndex)
        assertEquals(DevOnlyNpuSequentialSoftResetGateReason.STALE_RESULT_REJECTED, result.reason)
    }

    @Test
    fun `reuse not allowed stops the sequence`() {
        val result = evaluateWithSecond(cleanSummary("run-2", reuseAllowed = false))

        assertFalse(result.sequenceCanContinue)
        assertEquals(2, result.stopPromptIndex)
        assertEquals(DevOnlyNpuSequentialSoftResetGateReason.REUSE_NOT_ALLOWED, result.reason)
    }

    @Test
    fun `hidden per-run isolated requirement stops the sequence`() {
        val result = evaluateWithSecond(cleanSummary("run-2", perRunIsolatedRequired = true))

        assertFalse(result.sequenceCanContinue)
        assertEquals(2, result.stopPromptIndex)
        assertEquals(DevOnlyNpuSequentialSoftResetGateReason.PER_RUN_ISOLATED_REQUIRED, result.reason)
    }

    @Test
    fun `duplicate run id stops the sequence`() {
        val result = DevOnlyNpuSequentialSoftResetGate.evaluate(
            listOf(
                step(1, "konnichiwa", cleanSummary("run-1")),
                step(2, "python_calculator", cleanSummary("run-1")),
            ),
        )

        assertFalse(result.sequenceCanContinue)
        assertEquals(2, result.stopPromptIndex)
        assertEquals(DevOnlyNpuSequentialSoftResetGateReason.RUN_ID_NOT_UNIQUE, result.reason)
    }

    @Test
    fun `policy remains pinned while soft reset is preflight only`() {
        assertEquals(128, DevOnlyNpuRouteAdapter.QAIRT244_H1_PINNED_MAX_OUTPUT_TOKENS)
        assertEquals(256, DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_EXPERIMENTAL_BASELINE_CANDIDATE_TOKENS)

        val sequential512 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(
            executionIsolation = DevOnlyNpuExecutionIsolation.SEQUENTIAL,
        ))
        val blocked1024 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(maxOutputTokens = 1024))

        assertFalse(sequential512.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.SEQUENTIAL_512_ROLLBACK, sequential512.reason)
        assertFalse(blocked1024.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.MAX_OUTPUT_TOKENS_ABOVE_512_BLOCKED, blocked1024.reason)
    }

    private fun evaluateWithSecond(summary: DevOnlyNpuLifecycleSummary): DevOnlyNpuSequentialSoftResetGateResult =
        DevOnlyNpuSequentialSoftResetGate.evaluate(
            listOf(
                step(1, "konnichiwa", cleanSummary("run-1")),
                step(2, "python_calculator", summary),
                step(3, "lami_npu_short", cleanSummary("run-3")),
            ),
        )

    private fun step(
        index: Int,
        prompt: String,
        summary: DevOnlyNpuLifecycleSummary,
    ): DevOnlyNpuSequentialSoftResetStep =
        DevOnlyNpuSequentialSoftResetStep(
            promptIndex = index,
            promptLabel = prompt,
            summary = summary,
        )

    private fun timeoutSummary(runId: String): DevOnlyNpuLifecycleSummary =
        cleanSummary(
            runId = runId,
            classification = DevOnlyNpuLifecycleClassification.TIMEOUT_SUSPECT,
            acceptsCurrentRun = false,
            reuseAllowed = false,
            suspectSession = true,
            perRunIsolatedRequired = true,
        )

    private fun cleanupMissingSummary(runId: String): DevOnlyNpuLifecycleSummary =
        cleanSummary(
            runId = runId,
            classification = DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT,
            acceptsCurrentRun = false,
            reuseAllowed = false,
            suspectSession = true,
            perRunIsolatedRequired = true,
            cleanupElapsedMs = "missing",
            engineCloseEvidence = false,
        )

    private fun cleanSummary(
        runId: String,
        classification: DevOnlyNpuLifecycleClassification = DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN,
        acceptsCurrentRun: Boolean = true,
        reuseAllowed: Boolean = true,
        suspectSession: Boolean = false,
        perRunIsolatedRequired: Boolean = false,
        staleResultRejected: Boolean = false,
        runIdMismatchRejected: Boolean = false,
        sideEffectsClear: Boolean = true,
        cleanupElapsedMs: String = "42",
        engineCloseEvidence: Boolean = true,
    ): DevOnlyNpuLifecycleSummary =
        DevOnlyNpuLifecycleSummary(
            lifecycleClassification = classification,
            acceptsCurrentRun = acceptsCurrentRun,
            reuseAllowed = reuseAllowed,
            suspectSession = suspectSession,
            perRunIsolatedRequired = perRunIsolatedRequired,
            expectedRunId = runId,
            observedRunId = runId,
            cleanupElapsedMs = cleanupElapsedMs,
            engineCloseEvidence = engineCloseEvidence,
            staleResultRejected = staleResultRejected,
            runIdMismatchRejected = runIdMismatchRejected,
            sideEffectsClear = sideEffectsClear,
        )

    private fun validModeGate(
        executionIsolation: DevOnlyNpuExecutionIsolation = DevOnlyNpuExecutionIsolation.PER_RUN_FORCE_STOP,
        maxOutputTokens: Int = DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS,
    ) = DevOnlyNpuHiddenExperimentalModeGateInput(
        mode = DevOnlyNpuHiddenExperimentalMode.HIDDEN_PER_RUN_ISOLATED_512,
        executionIsolation = executionIsolation,
        maxOutputTokens = maxOutputTokens,
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
}
