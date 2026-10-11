package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.LocalInferenceEngineState
import org.junit.Assert.*
import org.junit.Test

class LocalInferenceRunDiagnosticsTest {
    @Test
    fun `timeout normalization preserves failure and partial response`() {
        val result = LocalInferenceRunResult(
            state = LocalInferenceEngineState.ERROR,
            response = "途中の応答",
            trace = LocalInferenceTrace(
                selectedAssistantResponseSource = "held-official-flow",
                officialFlowFallbackReason = "stale fallback",
                preferredBackendApplyResult = GPU_TIMEOUT_PARTIAL_PRESERVED_APPLY_RESULT,
                sessionResponseTokens = 7,
            ),
        )
        val normalized = requireNotNull(normalizeLocalInferenceRunResult(result))
        assertEquals(LocalInferenceEngineState.ERROR, normalized.state)
        assertEquals(result.response, normalized.response)
        assertTrue(shouldInsertLocalFailureAssistantMessage(normalized))
        assertTrue(normalized.trace.officialFlowUsed)
        assertNull(normalized.trace.officialFlowFallbackReason)
        assertEquals(LocalStatsAvailability.DERIVABLE_NOW, normalized.trace.outputTokenProbe.availability)
        assertEquals(LocalStatsAvailability.API_CANDIDATE_ONLY, normalized.trace.evalTimeProbe.availability)
    }

    @Test
    fun `known source takes precedence over close path without losing explicit stats`() {
        val probe = LocalStatsCandidateProbe(LocalStatsAvailability.AVAILABLE_NOW, valueSummary = "7")
        val result = LocalInferenceRunResult(
            state = LocalInferenceEngineState.READY,
            response = "answer",
            trace = LocalInferenceTrace(
                selectedAssistantResponseSource = "one-shot",
                officialFlowUsed = true,
                officialFlowFallbackReason = "flow unavailable",
                officialConversationApiAvailable = false,
                outputTokenProbe = probe,
            ),
            closeLifecycleSummary = RunCloseLifecycleSummary("held-official-flow", true),
        )
        val normalized = requireNotNull(normalizeLocalInferenceRunResult(result))
        assertFalse(normalized.trace.officialFlowUsed)
        assertFalse(normalized.trace.officialFlowAttempted)
        assertEquals(false, normalized.trace.officialConversationApiAvailable)
        assertEquals("flow unavailable", normalized.trace.officialFlowFallbackReason)
        assertEquals(probe, normalized.trace.outputTokenProbe)
        assertEquals(result.closeLifecycleSummary, normalized.closeLifecycleSummary)
        assertFalse(shouldInsertLocalFailureAssistantMessage(normalized))
    }

    @Test
    fun `close path restores source and derivable timing while missing result stays null`() {
        assertNull(normalizeLocalInferenceRunResult(null))
        val result = LocalInferenceRunResult(
            state = LocalInferenceEngineState.READY,
            trace = LocalInferenceTrace(
                localTraceStartElapsedRealtimeMs = 100,
                localTraceFirstResponseElapsedRealtimeMs = 120,
                localTraceCompletedElapsedRealtimeMs = 150,
            ),
            closeLifecycleSummary = RunCloseLifecycleSummary("chat-held-official-blocking-success", true),
        )
        val trace = requireNotNull(normalizeLocalInferenceRunResult(result)).trace
        assertEquals("held-official-blocking", trace.selectedAssistantResponseSource)
        assertEquals(true, trace.officialConversationApiAvailable)
        assertEquals(LocalStatsAvailability.DERIVABLE_NOW, trace.firstTokenProbe.availability)
        assertEquals(LocalStatsAvailability.DERIVABLE_NOW, trace.evalTimeProbe.availability)
    }

    @Test
    fun `failure insertion requires error state and a recognized response or partial marker`() {
        assertFalse(shouldInsertLocalFailureAssistantMessage(null))
        for (message in listOf(GPU_EXPERIMENTAL_TIMEOUT_MESSAGE, GPU_PREFILL_PROBE_DIAGNOSTIC_MESSAGE,
            GPU_RAW_CALLBACK_PROBE_DIAGNOSTIC_MESSAGE, GPU_MEMORY_PREFLIGHT_BLOCKED_MESSAGE)) {
            assertTrue(shouldInsertLocalFailureAssistantMessage(LocalInferenceRunResult(LocalInferenceEngineState.ERROR, message)))
            assertFalse(shouldInsertLocalFailureAssistantMessage(LocalInferenceRunResult(LocalInferenceEngineState.READY, message)))
        }
        assertFalse(shouldInsertLocalFailureAssistantMessage(LocalInferenceRunResult(LocalInferenceEngineState.ERROR, "unmarked partial")))
        assertFalse(shouldInsertLocalFailureAssistantMessage(LocalInferenceRunResult(
            LocalInferenceEngineState.ERROR, " ",
            LocalInferenceTrace(preferredBackendApplyResult = GPU_TIMEOUT_PARTIAL_PRESERVED_APPLY_RESULT),
        )))
    }

    @Test
    fun `close diagnostics retain failure details and missing target text`() {
        assertNull(buildCloseLifecycleText(null))
        val summary = RunCloseLifecycleSummary(
            path = "held-official-flow", successReturned = false,
            conversationOutcome = RunCloseTargetOutcome("conversation", "Conversation", "close", "failed", "IllegalStateException", "busy"),
            notes = "late callback rejected",
        )
        assertEquals(
            listOf("CLOSE LIFECYCLE", "path=held-official-flow", "successReturned=false",
                "conversation=status=failed strategy=close class=Conversation error=IllegalStateException message=busy",
                "engine=status=none", "session=status=none", "inference=status=none", "notes=late callback rejected").joinToString("\n"),
            buildCloseLifecycleText(summary),
        )
    }
}
