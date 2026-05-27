package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuLifecycleSummaryIntegrationTest {
    @Test
    fun `summary shows success clean and allows reuse`() {
        val summary = summaryFor(validInput())

        assertEquals("SUCCESS_CLEAN", summary["lifecycle_classification"])
        assertEquals("true", summary["accepts_current_run"])
        assertEquals("true", summary["reuse_allowed"])
        assertEquals("true", summary["next_prompt_allowed"])
        assertEquals("reuse_allowed", summary["runtime_reuse_policy"])
        assertEquals("false", summary["suspect_session"])
        assertEquals("false", summary["per_run_isolated_required"])
        assertEquals("42", summary["cleanup_elapsed_ms"])
        assertEquals("true", summary["engine_close_evidence"])
    }

    @Test
    fun `timeout summary marks suspect and forbids reuse`() {
        val summary = summaryFor(validInput(
            stateText = "runId=$RUN_ID state=timeout timeout=true",
            resultText = "runId=$RUN_ID state=timeout timeout=true",
        ))

        assertEquals("TIMEOUT_SUSPECT", summary["lifecycle_classification"])
        assertEquals("true", summary["suspect_session"])
        assertEquals("false", summary["reuse_allowed"])
        assertEquals("false", summary["next_prompt_allowed"])
        assertEquals("per_run_isolated_required", summary["runtime_reuse_policy"])
        assertEquals("true", summary["hidden_per_run_isolated_required"])
    }

    @Test
    fun `cleanup missing summary marks suspect and requires isolated mode`() {
        val summary = summaryFor(validInput(
            resultText = resultText(cleanupElapsedMs = null),
            nativeDiagText = "runId=$RUN_ID before RunDecode SetMaxOutputTokens(512)",
            cleanupText = "runId=$RUN_ID",
        ))

        assertEquals("CLEANUP_MISSING_SUSPECT", summary["lifecycle_classification"])
        assertEquals("true", summary["suspect_session"])
        assertEquals("false", summary["reuse_allowed"])
        assertEquals("false", summary["next_prompt_allowed"])
        assertEquals("true", summary["per_run_isolated_required"])
    }

    @Test
    fun `stale result summary is rejected`() {
        val summary = summaryFor(validInput(
            resultText = resultText(resultWrittenAtMs = 500L),
            artifactTimestampMs = 1_000L,
        ))

        assertEquals("STALE_RESULT_REJECTED", summary["lifecycle_classification"])
        assertEquals("true", summary["stale_result_rejected"])
        assertEquals("false", summary["accepts_current_run"])
    }

    @Test
    fun `run id mismatch summary is rejected`() {
        val summary = summaryFor(validInput(nativeDiagText = nativeDiagText(runId = "previous-run")))

        assertEquals("RUN_ID_MISMATCH_REJECTED", summary["lifecycle_classification"])
        assertEquals("true", summary["run_id_mismatch_rejected"])
        assertEquals("false", summary["accepts_current_run"])
    }

    @Test
    fun `side effects keep clean classification but reject current run`() {
        val summary = summaryFor(validInput(resultText = resultText(db = true)))

        assertEquals("SUCCESS_CLEAN", summary["lifecycle_classification"])
        assertEquals("false", summary["side_effects_clear"])
        assertEquals("false", summary["accepts_current_run"])
        assertEquals("false", summary["reuse_allowed"])
        assertEquals("false", summary["next_prompt_allowed"])
    }

    @Test
    fun `hidden mode policy remains pinned`() {
        assertEquals(128, DevOnlyNpuRouteAdapter.QAIRT244_H1_PINNED_MAX_OUTPUT_TOKENS)
        assertEquals(256, DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_EXPERIMENTAL_BASELINE_CANDIDATE_TOKENS)
        assertEquals(512, DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS)

        val sequential512 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(
            executionIsolation = DevOnlyNpuExecutionIsolation.SEQUENTIAL,
        ))
        val isolated512 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate())
        val blocked1024 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(maxOutputTokens = 1024))

        assertFalse(sequential512.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.SEQUENTIAL_512_ROLLBACK, sequential512.reason)
        assertTrue(isolated512.allowed)
        assertFalse(blocked1024.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.MAX_OUTPUT_TOKENS_ABOVE_512_BLOCKED, blocked1024.reason)
    }

    private fun summaryFor(input: DevOnlyNpuLifecycleArtifactParserInput): Map<String, String> =
        DevOnlyNpuLifecycleSummaryBuilder.fromParserResult(
            DevOnlyNpuLifecycleArtifactParser.parse(input),
        ).asKeyValues()

    private fun validInput(
        stateText: String = "runId=$RUN_ID state=started timeout=false",
        resultText: String = resultText(),
        nativeDiagText: String = nativeDiagText(),
        cleanupText: String = "runId=$RUN_ID cleanup_elapsed_ms=42 Engine.close=unique_ptr_cleanup",
        artifactTimestampMs: Long = 1_000L,
    ) = DevOnlyNpuLifecycleArtifactParserInput(
        runId = RUN_ID,
        stateText = stateText,
        resultText = resultText,
        nativeDiagText = nativeDiagText,
        cleanupText = cleanupText,
        artifactTimestampMs = artifactTimestampMs,
    )

    private fun resultText(
        runId: String = RUN_ID,
        resultWrittenAtMs: Long = 1_001L,
        cleanupElapsedMs: Long? = 42L,
        db: Boolean = false,
    ): String = buildString {
        appendLine("runId=$runId state=started")
        appendLine("runId=$runId state=success result=success result_written_at_ms=$resultWrittenAtMs")
        cleanupElapsedMs?.let { appendLine("cleanup_elapsed_ms=$it") }
        appendLine("Engine.close=unique_ptr_cleanup")
        appendLine("assistant_message_list_inserted=false")
        appendLine("selected_path_npu_saved=false")
        appendLine("db=$db")
        appendLine("tts=false")
        appendLine("markdown=false")
        appendLine("streaming=false")
    }

    private fun nativeDiagText(runId: String = RUN_ID): String =
        "runId=$runId qairt244_native_file_v1 qairt244_editable_prompt_smoke_v1 success " +
            "npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag " +
            "cleanup_elapsed_ms=42 Engine.close=unique_ptr_cleanup"

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

    private companion object {
        const val RUN_ID = "run-20260528-summary"
    }
}
