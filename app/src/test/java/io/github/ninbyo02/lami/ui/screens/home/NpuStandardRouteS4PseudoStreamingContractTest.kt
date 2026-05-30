package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS4PseudoStreamingContractTest {
    @Test
    fun `side effects mark pseudo streaming as not real token streaming`() {
        val sideEffects = NpuStandardRouteS4PseudoStreamingSideEffects()

        assertFalse(sideEffects.realTokenStreaming)
        assertFalse(sideEffects.tts)
        assertFalse(sideEffects.backendNpuPersisted)
        assertTrue(sideEffects.disconnected)
    }

    @Test
    fun `candidate persists final full text only`() {
        val candidate = NpuStandardRouteS4PseudoStreamingCandidate(
            finalText = "final text",
            chunks = listOf("final", "final text"),
            sourceDisplayText = "source",
        )

        assertTrue(candidate.readyToDisplay)
        assertEquals("final text", candidate.finalText)
        assertEquals("final text", candidate.dbPersistedText)
        assertEquals("final text", candidate.chunks.last())
        assertFalse(candidate.sideEffects.realTokenStreaming)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
    }

    @Test
    fun `mapping without candidate has no pseudo streaming candidate`() {
        val mapping = NpuStandardRouteS4PseudoStreamingMapping(
            pseudoStreamingCandidate = null,
            failureReason = NpuStandardRouteS4PseudoStreamingContract.FAILURE_EMPTY_TEXT,
        )

        assertFalse(mapping.hasPseudoStreamingCandidate)
        assertNull(mapping.pseudoStreamingCandidate)
        assertEquals("empty_text", mapping.failureReason)
    }

    @Test
    fun `contract labels route as pseudo streaming full text chunking`() {
        assertEquals("standard_chat_screen_s4a_npu_pseudo_streaming", NpuStandardRouteS4PseudoStreamingContract.ROUTE_TYPE)
        assertEquals("pseudo_streaming_full_text_chunking", NpuStandardRouteS4PseudoStreamingContract.STREAMING_TYPE)
    }
}
