package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuRuntimeReusePolicyTest {
    @Test
    fun `success clean is the only lifecycle result that opens the next prompt gate`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(validEvidence())
        val policy = runtimePolicyFor(decision)

        assertEquals(DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN, decision.classification)
        assertTrue(decision.acceptsCurrentRun)
        assertTrue(policy.runtimeReuseAllowed)
        assertTrue(policy.nextPromptAllowed)
        assertEquals("true", policy.asKeyValues()["next_prompt_allowed"])
        assertFalse(decision.perRunIsolatedRequired)
        assertFalse(policy.hiddenPerRunIsolatedRequired)
    }

    @Test
    fun `timeout cleanup stale and mismatch close the next prompt gate`() {
        val cases = listOf(
            DevOnlyNpuLifecycleClassification.TIMEOUT_SUSPECT to validEvidence(timeout = true),
            DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT to validEvidence(cleanupElapsedMs = null),
            DevOnlyNpuLifecycleClassification.STALE_RESULT_REJECTED to validEvidence(
                runStartedAtMs = 2_000L,
                resultWrittenAtMs = 1_000L,
            ),
            DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED to validEvidence(
                resultRunId = "previous-run",
            ),
        )

        cases.forEach { (classification, evidence) ->
            val decision = DevOnlyNpuLifecycleWrapper.evaluate(evidence)

            assertEquals(classification.name, classification, decision.classification)
            assertFalse(classification.name, decision.acceptsCurrentRun)
            val policy = runtimePolicyFor(decision)
            assertFalse(classification.name, policy.runtimeReuseAllowed)
            assertFalse(classification.name, policy.nextPromptAllowed)
            assertEquals(classification.name, "false", policy.asKeyValues()["next_prompt_allowed"])
            assertEquals(classification.name, classification.name, policy.reason.name)
            assertTrue(classification.name, decision.perRunIsolatedRequired)
            assertTrue(classification.name, policy.hiddenPerRunIsolatedRequired)
        }
    }

    @Test
    fun `failure clean does not open sequential next prompt gate`() {
        val decision = DevOnlyNpuLifecycleWrapper.evaluate(validEvidence(resultSuccess = false))
        val policy = runtimePolicyFor(decision)

        assertEquals(DevOnlyNpuLifecycleClassification.FAILURE_CLEAN, decision.classification)
        assertFalse(policy.runtimeReuseAllowed)
        assertFalse(policy.nextPromptAllowed)
        assertEquals(DevOnlyNpuRuntimeReusePolicyReason.NON_SUCCESS_CLEAN_CLASSIFICATION, policy.reason)
    }

    @Test
    fun `hidden 512 remains per-run isolated only`() {
        val sequential512 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(
            executionIsolation = DevOnlyNpuExecutionIsolation.SEQUENTIAL,
        ))
        val activityRestart512 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(
            executionIsolation = DevOnlyNpuExecutionIsolation.ACTIVITY_RESTART_ONLY,
        ))
        val missingForceStop512 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(
            forceStopBeforeEachPrompt = false,
        ))
        val isolated512 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate())

        assertFalse(sequential512.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.SEQUENTIAL_512_ROLLBACK, sequential512.reason)
        assertFalse(activityRestart512.allowed)
        assertEquals(
            DevOnlyNpuHiddenExperimentalModeGateReason.ACTIVITY_RESTART_ONLY_512_ROLLBACK,
            activityRestart512.reason,
        )
        assertFalse(missingForceStop512.allowed)
        assertEquals(
            DevOnlyNpuHiddenExperimentalModeGateReason.FORCE_STOP_BEFORE_AFTER_REQUIRED,
            missingForceStop512.reason,
        )
        assertTrue(isolated512.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.OK, isolated512.reason)
    }

    @Test
    fun `H1 256 512 and 1024 runtime policy remains pinned`() {
        assertEquals(128, DevOnlyNpuRouteAdapter.QAIRT244_H1_PINNED_MAX_OUTPUT_TOKENS)
        assertEquals(256, DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_EXPERIMENTAL_BASELINE_CANDIDATE_TOKENS)
        assertEquals(512, DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS)
        assertEquals(512, DevOnlyNpuRouteAdapter.QAIRT244_MAX_OUTPUT_TOKENS_COMPARE_LIMIT)

        val hidden256 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(
            DevOnlyNpuHiddenExperimentalModeGateInput(
                mode = DevOnlyNpuHiddenExperimentalMode.HIDDEN_EXPERIMENTAL_256,
                executionIsolation = DevOnlyNpuExecutionIsolation.SEQUENTIAL,
                maxOutputTokens = DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_EXPERIMENTAL_BASELINE_CANDIDATE_TOKENS,
            ),
        )
        val hidden512 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate())
        val blocked1024 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(maxOutputTokens = 1024))

        assertTrue(hidden256.allowed)
        assertTrue(hidden512.allowed)
        assertFalse(blocked1024.allowed)
        assertEquals(
            DevOnlyNpuHiddenExperimentalModeGateReason.MAX_OUTPUT_TOKENS_ABOVE_512_BLOCKED,
            blocked1024.reason,
        )
    }

    private fun runtimePolicyFor(decision: DevOnlyNpuLifecycleDecision): DevOnlyNpuRuntimeReusePolicyResult =
        DevOnlyNpuRuntimeReusePolicy.from(
            DevOnlyNpuLifecycleSummary(
                lifecycleClassification = decision.classification,
                acceptsCurrentRun = decision.acceptsCurrentRun,
                reuseAllowed = decision.sessionReuseAllowed,
                suspectSession = decision.classification == DevOnlyNpuLifecycleClassification.TIMEOUT_SUSPECT ||
                    decision.classification == DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT,
                perRunIsolatedRequired = decision.perRunIsolatedRequired,
                expectedRunId = RUN_ID,
                observedRunId = RUN_ID,
                cleanupElapsedMs = "42",
                engineCloseEvidence = true,
                staleResultRejected = decision.classification ==
                    DevOnlyNpuLifecycleClassification.STALE_RESULT_REJECTED,
                runIdMismatchRejected = decision.classification ==
                    DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED,
                sideEffectsClear = decision.sideEffectsClear,
            ),
        )

    private fun validEvidence(
        runStartedAtMs: Long = 1_000L,
        resultWrittenAtMs: Long? = 1_001L,
        resultRunId: String? = RUN_ID,
        timeout: Boolean = false,
        cleanupElapsedMs: Long? = 42L,
        resultSuccess: Boolean = true,
    ) = DevOnlyNpuLifecycleEvidence(
        runId = RUN_ID,
        files = DevOnlyNpuLifecycleWrapper.buildRunFiles(RUN_ID),
        callbackRunId = RUN_ID,
        stateRunId = RUN_ID,
        resultRunId = resultRunId,
        nativeDiagRunId = RUN_ID,
        cleanupRunId = RUN_ID,
        runStartedAtMs = runStartedAtMs,
        resultWrittenAtMs = resultWrittenAtMs,
        resultSuccess = resultSuccess,
        timeout = timeout,
        cleanupElapsedMs = cleanupElapsedMs,
        engineCloseUniquePtrCleanup = true,
    )

    private fun validModeGate(
        executionIsolation: DevOnlyNpuExecutionIsolation = DevOnlyNpuExecutionIsolation.PER_RUN_FORCE_STOP,
        maxOutputTokens: Int = DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS,
        forceStopBeforeEachPrompt: Boolean = true,
    ) = DevOnlyNpuHiddenExperimentalModeGateInput(
        mode = DevOnlyNpuHiddenExperimentalMode.HIDDEN_PER_RUN_ISOLATED_512,
        executionIsolation = executionIsolation,
        maxOutputTokens = maxOutputTokens,
        forceStopBeforeEachPrompt = forceStopBeforeEachPrompt,
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
        const val RUN_ID = "run-20260528-runtime-reuse-policy"
    }
}
