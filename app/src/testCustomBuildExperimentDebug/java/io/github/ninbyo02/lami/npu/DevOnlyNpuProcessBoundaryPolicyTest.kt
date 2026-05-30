package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuProcessBoundaryPolicyTest {
    @Test
    fun `process present before dispatch can dispatch`() {
        val decision = DevOnlyNpuProcessBoundaryPolicy.evaluate(snapshot(pid = "4758"))

        assertEquals(DevOnlyNpuProcessBoundaryClassification.PROCESS_PRESENT, decision.classification)
        assertTrue(decision.canDispatch)
        assertTrue(decision.reuseAllowed)
        assertFalse(decision.processDisappearedSuspect)
        assertFalse(decision.hiddenPerRunIsolatedRequired)
    }

    @Test
    fun `process absent before dispatch stops before prompt dispatch`() {
        val decision = DevOnlyNpuProcessBoundaryPolicy.evaluate(snapshot(pid = null))

        assertEquals(
            DevOnlyNpuProcessBoundaryClassification.PROCESS_ABSENT_BEFORE_DISPATCH,
            decision.classification,
        )
        assertFalse(decision.canDispatch)
        assertFalse(decision.reuseAllowed)
        assertTrue(decision.processDisappearedSuspect)
        assertTrue(decision.hiddenPerRunIsolatedRequired)
    }

    @Test
    fun `process disappeared after cleanup is suspect and forbids reuse`() {
        val decision = DevOnlyNpuProcessBoundaryPolicy.evaluate(
            snapshot(point = DevOnlyNpuProcessBoundaryPoint.AFTER_CLEANUP, pid = null),
        )

        assertEquals(
            DevOnlyNpuProcessBoundaryClassification.PROCESS_DISAPPEARED_AFTER_CLEANUP,
            decision.classification,
        )
        assertFalse(decision.reuseAllowed)
        assertTrue(decision.processDisappearedSuspect)
        assertTrue(decision.hiddenPerRunIsolatedRequired)
    }

    @Test
    fun `unknown snapshot blocks dispatch but is not treated as disappeared`() {
        val decision = DevOnlyNpuProcessBoundaryPolicy.evaluate(
            snapshot(pid = null, snapshotCaptured = false),
        )

        assertEquals(DevOnlyNpuProcessBoundaryClassification.PROCESS_STATE_UNKNOWN, decision.classification)
        assertFalse(decision.canDispatch)
        assertFalse(decision.reuseAllowed)
        assertFalse(decision.processDisappearedSuspect)
    }

    @Test
    fun `H1 256 512 and 1024 policy remains unchanged`() {
        assertEquals(128, DevOnlyNpuRouteAdapter.QAIRT244_H1_PINNED_MAX_OUTPUT_TOKENS)
        assertEquals(256, DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_EXPERIMENTAL_BASELINE_CANDIDATE_TOKENS)
        assertEquals(512, DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS)

        val isolated512 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate())
        val blocked1024 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(maxOutputTokens = 1024))

        assertTrue(isolated512.allowed)
        assertFalse(blocked1024.allowed)
        assertEquals(
            DevOnlyNpuHiddenExperimentalModeGateReason.MAX_OUTPUT_TOKENS_ABOVE_512_BLOCKED,
            blocked1024.reason,
        )
    }

    private fun snapshot(
        point: DevOnlyNpuProcessBoundaryPoint = DevOnlyNpuProcessBoundaryPoint.BEFORE_DISPATCH,
        pid: String?,
        snapshotCaptured: Boolean = true,
    ) = DevOnlyNpuProcessBoundarySnapshot(
        point = point,
        pid = pid,
        psOutputPresent = pid != null,
        snapshotCaptured = snapshotCaptured,
    )

    private fun validModeGate(
        maxOutputTokens: Int = DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS,
    ) = DevOnlyNpuHiddenExperimentalModeGateInput(
        mode = DevOnlyNpuHiddenExperimentalMode.HIDDEN_PER_RUN_ISOLATED_512,
        executionIsolation = DevOnlyNpuExecutionIsolation.PER_RUN_FORCE_STOP,
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
