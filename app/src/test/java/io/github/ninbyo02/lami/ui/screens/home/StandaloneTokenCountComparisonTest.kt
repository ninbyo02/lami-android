package io.github.ninbyo02.lami.ui.screens.home

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class StandaloneTokenCountComparisonTest {
    @Test fun disabledOrMissingBaselineDoesNoWork() {
        assertEquals("disabled", compareStandaloneTokenCounts(false, 1, 2) { error("called") }.status)
        assertEquals("baseline-unavailable", compareStandaloneTokenCounts(true, null, 2) { error("called") }.status)
    }
    @Test fun comparesBothCountsWithoutMutatingAuthoritativeSnapshot() {
        val snapshot = LocalInferenceMeasuredTokenSnapshot(inputTokens = 1, outputTokens = 2)
        val before = snapshot.copy()
        assertEquals("match", compareStandaloneTokenCounts(true, snapshot.inputTokens, snapshot.outputTokens) { StandaloneTokenCounts(1, 2) }.status)
        assertEquals("mismatch", compareStandaloneTokenCounts(true, snapshot.inputTokens, snapshot.outputTokens) { StandaloneTokenCounts(1, 3) }.status)
        assertEquals(before, snapshot)
    }
    @Test fun unavailableLibraryAndInvalidCountsAreDiagnosticFailures() {
        assertEquals("native-unavailable", compareStandaloneTokenCounts(true, 1, 2) { throw UnsatisfiedLinkError() }.status)
        assertEquals("failed", compareStandaloneTokenCounts(true, 1, 2) { throw IllegalArgumentException("bad model") }.status)
        assertEquals("failed", compareStandaloneTokenCounts(true, 1, 2) { StandaloneTokenCounts(-1, 2) }.status)
    }
    @Test fun callerCancellationPropagates() {
        try { compareStandaloneTokenCounts(true, 1, 2) { throw CancellationException() }; fail("cancel swallowed") }
        catch (_: CancellationException) { }
    }
}
