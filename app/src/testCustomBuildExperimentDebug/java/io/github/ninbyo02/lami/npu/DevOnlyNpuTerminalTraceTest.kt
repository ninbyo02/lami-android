package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuTerminalTraceTest {
    @Test
    fun `clean marker order classifies worker completed clean`() {
        val decision = classify(cleanTrace())

        assertEquals(DevOnlyNpuTerminalTraceClassification.WORKER_COMPLETED_CLEAN, decision.classification)
        assertFalse(decision.suspectSession)
        assertTrue(decision.reuseAllowed)
        assertFalse(decision.hiddenPerRunIsolatedRequired)
    }

    @Test
    fun `throwable marker classifies worker throwable caught`() {
        val decision = classify(
            trace(
                DevOnlyNpuTerminalTraceMarker.RECEIVER_ENTER,
                DevOnlyNpuTerminalTraceMarker.GO_ASYNC_STARTED,
                DevOnlyNpuTerminalTraceMarker.WORKER_THREAD_STARTED,
                DevOnlyNpuTerminalTraceMarker.THROWABLE_CAUGHT,
                DevOnlyNpuTerminalTraceMarker.FINALLY_ENTER,
                DevOnlyNpuTerminalTraceMarker.FINALLY_EXIT,
                DevOnlyNpuTerminalTraceMarker.WORKER_FINISHED,
            ),
        )

        assertEquals(DevOnlyNpuTerminalTraceClassification.WORKER_THROWABLE_CAUGHT, decision.classification)
        assertTrue(decision.suspectSession)
        assertFalse(decision.reuseAllowed)
    }

    @Test
    fun `native enter without after native or finally is native non return or process death`() {
        val decision = classify(
            trace(
                DevOnlyNpuTerminalTraceMarker.RECEIVER_ENTER,
                DevOnlyNpuTerminalTraceMarker.GO_ASYNC_STARTED,
                DevOnlyNpuTerminalTraceMarker.WORKER_THREAD_STARTED,
                DevOnlyNpuTerminalTraceMarker.RUN_FOR_CHATSCREEN_ENTER,
                DevOnlyNpuTerminalTraceMarker.BEFORE_NATIVE_ADAPTER_RUN,
            ),
        )

        assertEquals(
            DevOnlyNpuTerminalTraceClassification.NATIVE_NON_RETURN_OR_PROCESS_DEATH,
            decision.classification,
        )
        assertTrue(decision.hiddenPerRunIsolatedRequired)
    }

    @Test
    fun `after native without terminal write is terminal result missing`() {
        val decision = classify(
            trace(
                DevOnlyNpuTerminalTraceMarker.RECEIVER_ENTER,
                DevOnlyNpuTerminalTraceMarker.GO_ASYNC_STARTED,
                DevOnlyNpuTerminalTraceMarker.WORKER_THREAD_STARTED,
                DevOnlyNpuTerminalTraceMarker.RUN_FOR_CHATSCREEN_ENTER,
                DevOnlyNpuTerminalTraceMarker.BEFORE_NATIVE_ADAPTER_RUN,
                DevOnlyNpuTerminalTraceMarker.AFTER_NATIVE_ADAPTER_RUN,
                DevOnlyNpuTerminalTraceMarker.FINALLY_ENTER,
                DevOnlyNpuTerminalTraceMarker.FINALLY_EXIT,
                DevOnlyNpuTerminalTraceMarker.WORKER_FINISHED,
            ),
        )

        assertEquals(
            DevOnlyNpuTerminalTraceClassification.TERMINAL_RESULT_WRITE_MISSING,
            decision.classification,
        )
    }

    @Test
    fun `terminal result without cleanup is cleanup missing`() {
        val decision = classify(
            trace(
                DevOnlyNpuTerminalTraceMarker.RECEIVER_ENTER,
                DevOnlyNpuTerminalTraceMarker.GO_ASYNC_STARTED,
                DevOnlyNpuTerminalTraceMarker.WORKER_THREAD_STARTED,
                DevOnlyNpuTerminalTraceMarker.RUN_FOR_CHATSCREEN_ENTER,
                DevOnlyNpuTerminalTraceMarker.BEFORE_NATIVE_ADAPTER_RUN,
                DevOnlyNpuTerminalTraceMarker.AFTER_NATIVE_ADAPTER_RUN,
                DevOnlyNpuTerminalTraceMarker.BEFORE_TERMINAL_RESULT_WRITE,
                DevOnlyNpuTerminalTraceMarker.AFTER_TERMINAL_RESULT_WRITE,
                DevOnlyNpuTerminalTraceMarker.FINALLY_ENTER,
                DevOnlyNpuTerminalTraceMarker.FINALLY_EXIT,
                DevOnlyNpuTerminalTraceMarker.WORKER_FINISHED,
            ),
        )

        assertEquals(DevOnlyNpuTerminalTraceClassification.CLEANUP_MISSING, decision.classification)
    }

    @Test
    fun `run id mismatch rejects trace`() {
        val decision = DevOnlyNpuTerminalTrace.classify(
            expectedRunId = RUN_ID,
            traceText = eventLine(DevOnlyNpuTerminalTraceMarker.RECEIVER_ENTER, runId = "other-run"),
            nowMs = NOW_MS,
        )

        assertEquals(DevOnlyNpuTerminalTraceClassification.RUN_ID_MISMATCH_REJECTED, decision.classification)
        assertTrue(decision.runIdMismatchRejected)
        assertFalse(decision.reuseAllowed)
    }

    @Test
    fun `stale trace rejects trace`() {
        val decision = DevOnlyNpuTerminalTrace.classify(
            expectedRunId = RUN_ID,
            traceText = eventLine(
                marker = DevOnlyNpuTerminalTraceMarker.RECEIVER_ENTER,
                timestampMs = NOW_MS - DevOnlyNpuTerminalTrace.DEFAULT_TRACE_FRESHNESS_WINDOW_MS - 1L,
            ),
            nowMs = NOW_MS,
        )

        assertEquals(DevOnlyNpuTerminalTraceClassification.STALE_TRACE_REJECTED, decision.classification)
        assertTrue(decision.staleTraceRejected)
        assertTrue(decision.hiddenPerRunIsolatedRequired)
    }

    @Test
    fun `terminal trace side effect flags remain false`() {
        val decision = classify(cleanTrace())

        assertFalse(decision.assistantMessageListInserted)
        assertFalse(decision.selectedPathSaved)
        assertFalse(decision.db)
        assertFalse(decision.tts)
        assertFalse(decision.markdown)
        assertFalse(decision.streaming)
    }

    @Test
    fun `H1 256 512 and 1024 policy remain unchanged`() {
        assertEquals(128, DevOnlyNpuRouteAdapter.QAIRT244_H1_PINNED_MAX_OUTPUT_TOKENS)

        val hidden256 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(
            DevOnlyNpuHiddenExperimentalModeGateInput(
                mode = DevOnlyNpuHiddenExperimentalMode.HIDDEN_EXPERIMENTAL_256,
                executionIsolation = DevOnlyNpuExecutionIsolation.SEQUENTIAL,
                maxOutputTokens = DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_EXPERIMENTAL_BASELINE_CANDIDATE_TOKENS,
            ),
        )
        assertTrue(hidden256.allowed)

        val sequential512 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validPerRun512(
            executionIsolation = DevOnlyNpuExecutionIsolation.SEQUENTIAL,
        ))
        val perRun512 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validPerRun512())
        val blocked1024 = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validPerRun512(maxOutputTokens = 1024))

        assertFalse(sequential512.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.SEQUENTIAL_512_ROLLBACK, sequential512.reason)
        assertTrue(perRun512.allowed)
        assertFalse(blocked1024.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.MAX_OUTPUT_TOKENS_ABOVE_512_BLOCKED, blocked1024.reason)
    }

    private fun classify(traceText: String): DevOnlyNpuTerminalTraceDecision =
        DevOnlyNpuTerminalTrace.classify(
            expectedRunId = RUN_ID,
            traceText = traceText,
            nowMs = NOW_MS,
        )

    private fun cleanTrace(): String = trace(
        DevOnlyNpuTerminalTraceMarker.RECEIVER_ENTER,
        DevOnlyNpuTerminalTraceMarker.GO_ASYNC_STARTED,
        DevOnlyNpuTerminalTraceMarker.WORKER_THREAD_STARTED,
        DevOnlyNpuTerminalTraceMarker.RUN_FOR_CHATSCREEN_ENTER,
        DevOnlyNpuTerminalTraceMarker.BEFORE_NATIVE_ADAPTER_RUN,
        DevOnlyNpuTerminalTraceMarker.BEFORE_RUN_DECODE_MARKER_SEEN,
        DevOnlyNpuTerminalTraceMarker.AFTER_NATIVE_ADAPTER_RUN,
        DevOnlyNpuTerminalTraceMarker.BEFORE_TERMINAL_RESULT_WRITE,
        DevOnlyNpuTerminalTraceMarker.AFTER_TERMINAL_RESULT_WRITE,
        DevOnlyNpuTerminalTraceMarker.BEFORE_CLEANUP,
        DevOnlyNpuTerminalTraceMarker.AFTER_CLEANUP,
        DevOnlyNpuTerminalTraceMarker.FINALLY_ENTER,
        DevOnlyNpuTerminalTraceMarker.FINALLY_EXIT,
        DevOnlyNpuTerminalTraceMarker.WORKER_FINISHED,
    )

    private fun trace(vararg markers: DevOnlyNpuTerminalTraceMarker): String =
        markers.mapIndexed { index, marker ->
            eventLine(marker = marker, timestampMs = NOW_MS - markers.size + index)
        }.joinToString(separator = "\n", postfix = "\n")

    private fun eventLine(
        marker: DevOnlyNpuTerminalTraceMarker,
        timestampMs: Long = NOW_MS,
        runId: String = RUN_ID,
    ): String =
        "marker=${marker.value} timestamp_ms=$timestampMs runId=$runId thread=test process_id=123"

    private fun validPerRun512(
        executionIsolation: DevOnlyNpuExecutionIsolation = DevOnlyNpuExecutionIsolation.PER_RUN_FORCE_STOP,
        maxOutputTokens: Int = DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS,
    ): DevOnlyNpuHiddenExperimentalModeGateInput = DevOnlyNpuHiddenExperimentalModeGateInput(
        mode = DevOnlyNpuHiddenExperimentalMode.HIDDEN_PER_RUN_ISOLATED_512,
        executionIsolation = executionIsolation,
        maxOutputTokens = maxOutputTokens,
        hiddenOnly = true,
        normalChatScreenPromotion = false,
        forceStopBeforeEachPrompt = true,
        forceStopAfterEachPrompt = true,
        runDecodeReached = true,
        setMaxOutputTokens512Evidence = true,
        timeout = false,
        freshCrash = false,
        fallbackUsed = false,
        qnnHtpFastRpcEvidence = true,
        engineCloseUniquePtrCleanup = true,
        cleanupEvidence = true,
        processPresentAfter10s = false,
        memoryHighRetained = false,
        codeAwareSanitizer = true,
        codeIndentationPreserved = true,
        codeFenceCompleted = true,
        selectedPathSaved = false,
        assistantMessageListInserted = false,
        db = false,
        tts = false,
        markdown = false,
        streaming = false,
    )

    private companion object {
        const val RUN_ID = "terminal-run-1"
        const val NOW_MS = 1_000_000L
    }
}
