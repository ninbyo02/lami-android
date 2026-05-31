package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS3MarkdownContractTest {
    @Test
    fun `S3 side effects keep streaming TTS and backend persistence disconnected`() {
        val sideEffects = NpuStandardRouteS3MarkdownSideEffects()

        assertFalse(sideEffects.streaming)
        assertFalse(sideEffects.tts)
        assertFalse(sideEffects.backendNpuPersisted)
        assertTrue(sideEffects.downstreamDisconnected)
    }

    @Test
    fun `markdown candidate carries sanitized display and finalized text`() {
        val candidate = NpuStandardRouteS3MarkdownCandidate(
            sanitizedInputText = "こんにちは。",
            sourceDisplayText = "NPU STANDARD ROUTE S1",
            finalizedText = "こんにちは。",
        )

        assertTrue(candidate.readyToRender)
        assertEquals("こんにちは。", candidate.sanitizedInputText)
        assertEquals("NPU STANDARD ROUTE S1", candidate.sourceDisplayText)
        assertEquals("こんにちは。", candidate.finalizedText)
        assertFalse(candidate.repairApplied)
        assertFalse(candidate.sideEffects.streaming)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
    }

    @Test
    fun `markdown candidate records repair when finalized text differs`() {
        val candidate = NpuStandardRouteS3MarkdownCandidate(
            sanitizedInputText = "line\\nnext",
            sourceDisplayText = "line\\nnext",
            finalizedText = "line\nnext",
        )

        assertTrue(candidate.readyToRender)
        assertTrue(candidate.repairApplied)
    }

    @Test
    fun `mapping without candidate has no markdown candidate`() {
        val mapping = NpuStandardRouteS3MarkdownMapping(
            markdownCandidate = null,
            failureReason = NpuStandardRouteS3MarkdownContract.FAILURE_S1_NOT_SUCCESS,
        )

        assertFalse(mapping.hasMarkdownCandidate)
        assertNull(mapping.markdownCandidate)
        assertEquals("s1_success_criteria_not_met", mapping.failureReason)
    }

    @Test
    fun `S3 route type is standard chat screen markdown`() {
        assertEquals("standard_chat_screen_s3_markdown", NpuStandardRouteS3MarkdownContract.ROUTE_TYPE)
        assertEquals(
            NpuStandardRouteS1Contract.ROUTE_TYPE_S3_MARKDOWN,
            NpuStandardRouteS3MarkdownContract.ROUTE_TYPE,
        )
    }
}
