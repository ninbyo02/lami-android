package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuLifecycleArtifactParserTest {
    @Test
    fun `expected run id with completed cleanup is success clean`() {
        val parsed = DevOnlyNpuLifecycleArtifactParser.parse(validInput())

        assertEquals(DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN, parsed.classification)
        assertTrue(parsed.decision.acceptsCurrentRun)
        assertTrue(parsed.decision.sessionReuseAllowed)
    }

    @Test
    fun `stale result is rejected`() {
        val parsed = DevOnlyNpuLifecycleArtifactParser.parse(
            validInput(
                resultText = resultText(
                    runId = RUN_ID,
                    resultWrittenAtMs = 500L,
                ),
                artifactTimestampMs = 1_000L,
            ),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.STALE_RESULT_REJECTED, parsed.classification)
        assertFalse(parsed.decision.acceptsCurrentRun)
    }

    @Test
    fun `state run id mismatch is rejected`() {
        assertParserClassification(
            validInput(stateText = stateText(runId = "previous-run")),
            DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED,
        )
    }

    @Test
    fun `result run id mismatch is rejected`() {
        assertParserClassification(
            validInput(resultText = resultText(runId = "previous-run")),
            DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED,
        )
    }

    @Test
    fun `native diag run id mismatch is rejected`() {
        assertParserClassification(
            validInput(nativeDiagText = nativeDiagText(runId = "previous-run")),
            DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED,
        )
    }

    @Test
    fun `cleanup run id mismatch is rejected`() {
        assertParserClassification(
            validInput(cleanupText = cleanupText(runId = "previous-run")),
            DevOnlyNpuLifecycleClassification.RUN_ID_MISMATCH_REJECTED,
        )
    }

    @Test
    fun `timeout is suspect`() {
        val parsed = DevOnlyNpuLifecycleArtifactParser.parse(
            validInput(
                stateText = "runId=$RUN_ID state=timeout timeout=true",
                resultText = "runId=$RUN_ID state=timeout timeout=true",
            ),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.TIMEOUT_SUSPECT, parsed.classification)
        assertFalse(parsed.decision.sessionReuseAllowed)
        assertTrue(parsed.decision.perRunIsolatedRequired)
    }

    @Test
    fun `missing cleanup is suspect`() {
        val parsed = DevOnlyNpuLifecycleArtifactParser.parse(
            validInput(
                resultText = resultText(runId = RUN_ID, cleanupElapsedMs = null),
                nativeDiagText = nativeDiagText(runId = RUN_ID, includeCleanup = false),
                cleanupText = "runId=$RUN_ID",
            ),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT, parsed.classification)
        assertFalse(parsed.decision.sessionReuseAllowed)
    }

    @Test
    fun `missing success callback is suspect`() {
        val parsed = DevOnlyNpuLifecycleArtifactParser.parse(
            validInput(resultText = "runId=$RUN_ID state=started timeout=false"),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT, parsed.classification)
        assertFalse(parsed.decision.acceptsCurrentRun)
    }

    @Test
    fun `missing native completed evidence is suspect`() {
        val parsed = DevOnlyNpuLifecycleArtifactParser.parse(
            validInput(nativeDiagText = "runId=$RUN_ID before RunDecode SetMaxOutputTokens(512)"),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.CLEANUP_MISSING_SUSPECT, parsed.classification)
        assertFalse(parsed.decision.sessionReuseAllowed)
    }

    @Test
    fun `side-effect flags false are required`() {
        val parsed = DevOnlyNpuLifecycleArtifactParser.parse(
            validInput(resultText = resultText(runId = RUN_ID, db = true)),
        )

        assertEquals(DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN, parsed.classification)
        assertFalse(parsed.decision.sideEffectsClear)
        assertFalse(parsed.decision.acceptsCurrentRun)
    }

    @Test
    fun `512 sequential is rejected and per-run isolated is accepted only with clean evidence`() {
        val lifecycle = DevOnlyNpuLifecycleArtifactParser.parse(validInput())
        val sequential = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(
            executionIsolation = DevOnlyNpuExecutionIsolation.SEQUENTIAL,
        ))
        val isolated = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate())

        assertEquals(DevOnlyNpuLifecycleClassification.SUCCESS_CLEAN, lifecycle.classification)
        assertFalse(sequential.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.SEQUENTIAL_512_ROLLBACK, sequential.reason)
        assertTrue(isolated.allowed)
    }

    @Test
    fun `H1 remains pinned and 1024 remains blocked`() {
        assertEquals(128, DevOnlyNpuRouteAdapter.QAIRT244_H1_PINNED_MAX_OUTPUT_TOKENS)
        assertEquals(256, DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_EXPERIMENTAL_BASELINE_CANDIDATE_TOKENS)

        val blocked = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validModeGate(maxOutputTokens = 1024))

        assertFalse(blocked.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.MAX_OUTPUT_TOKENS_ABOVE_512_BLOCKED, blocked.reason)
    }

    private fun assertParserClassification(
        input: DevOnlyNpuLifecycleArtifactParserInput,
        classification: DevOnlyNpuLifecycleClassification,
    ) {
        val parsed = DevOnlyNpuLifecycleArtifactParser.parse(input)

        assertEquals(classification, parsed.classification)
        assertFalse(parsed.decision.acceptsCurrentRun)
    }

    private fun validInput(
        runId: String = RUN_ID,
        stateText: String = stateText(runId),
        resultText: String = resultText(runId),
        nativeDiagText: String = nativeDiagText(runId),
        cleanupText: String = cleanupText(runId),
        artifactTimestampMs: Long = 1_000L,
    ) = DevOnlyNpuLifecycleArtifactParserInput(
        runId = runId,
        stateText = stateText,
        resultText = resultText,
        nativeDiagText = nativeDiagText,
        cleanupText = cleanupText,
        artifactTimestampMs = artifactTimestampMs,
    )

    private fun stateText(runId: String): String =
        "runId=$runId state=started timeout=false db=false tts=false markdown=false streaming=false"

    private fun resultText(
        runId: String,
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

    private fun nativeDiagText(
        runId: String,
        includeCleanup: Boolean = true,
    ): String = buildString {
        append("runId=$runId qairt244_native_file_v1 qairt244_editable_prompt_smoke_v1 success ")
        append("npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag ")
        if (includeCleanup) {
            append("cleanup_elapsed_ms=42 Engine.close=unique_ptr_cleanup")
        }
    }

    private fun cleanupText(runId: String): String =
        "runId=$runId cleanup_elapsed_ms=42 Engine.close=unique_ptr_cleanup"

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
        const val RUN_ID = "run-20260527-parser"
    }
}
