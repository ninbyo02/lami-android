package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS3MarkdownMapperTest {
    @Test
    fun `successful S1 result creates markdown candidate`() {
        val mapping = NpuStandardRouteS3MarkdownMapper.map(successResult())
        val candidate = requireNotNull(mapping.markdownCandidate)

        assertTrue(mapping.hasMarkdownCandidate)
        assertNull(mapping.failureReason)
        assertEquals("こんにちは。", candidate.sanitizedInputText)
        assertEquals("こんにちは。", candidate.sourceDisplayText)
        assertEquals("こんにちは。", candidate.finalizedText)
        assertFalse(candidate.repairApplied)
        assertFalse(candidate.sideEffects.streaming)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
    }

    @Test
    fun `mapper applies injected finalizer to sanitized text`() {
        val mapping = NpuStandardRouteS3MarkdownMapper.map(
            s1Result = successResult(sanitizedOutput = "line\\nnext"),
            finalizeMarkdown = { it.replace("\\n", "\n") },
        )
        val candidate = requireNotNull(mapping.markdownCandidate)

        assertEquals("line\\nnext", candidate.sanitizedInputText)
        assertEquals("line\nnext", candidate.finalizedText)
        assertTrue(candidate.repairApplied)
    }

    @Test
    fun `failed S1 result creates no markdown candidate`() {
        val mapping = NpuStandardRouteS3MarkdownMapper.map(
            successResult(fallbackUsed = true),
        )

        assertFalse(mapping.hasMarkdownCandidate)
        assertNull(mapping.markdownCandidate)
        assertEquals(NpuStandardRouteS3MarkdownContract.FAILURE_S1_NOT_SUCCESS, mapping.failureReason)
    }

    @Test
    fun `empty finalized text creates no markdown candidate`() {
        val mapping = NpuStandardRouteS3MarkdownMapper.map(
            s1Result = successResult(),
            finalizeMarkdown = { "" },
        )

        assertFalse(mapping.hasMarkdownCandidate)
        assertNull(mapping.markdownCandidate)
        assertEquals(NpuStandardRouteS3MarkdownContract.FAILURE_EMPTY_TEXT, mapping.failureReason)
    }

    @Test
    fun `mapper does not depend on S2 DB connection`() {
        val mapping = NpuStandardRouteS3MarkdownMapper.map(successResult())
        val candidate = requireNotNull(mapping.markdownCandidate)

        assertTrue(candidate.readyToRender)
        assertFalse(candidate.sideEffects.streaming)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
    }

    @Test
    fun `S3 saved result marks DB and markdown connected only`() {
        val result = buildNpuStandardRouteS3MarkdownSavedResult(
            s1Result = successResult(sanitizedOutput = "line\\nnext"),
            finalizedText = "line\nnext",
        )

        assertEquals(NpuStandardRouteS1Contract.ROUTE_TYPE_S3_MARKDOWN, result.selection.routeType)
        assertEquals("line\nnext", result.sanitizedOutput)
        assertTrue(result.displayText.contains("route_type=standard_chat_screen_s3_markdown"))
        assertTrue(result.displayText.contains("db=true"))
        assertTrue(result.displayText.contains("conversation_history_saved=true"))
        assertTrue(result.displayText.contains("markdown=true"))
        assertTrue(result.displayText.contains("streaming=false"))
        assertTrue(result.displayText.contains("tts=false"))
        assertTrue(result.displayText.contains("fallback_used=false"))
        assertTrue(result.displayText.contains("fresh_crash=false"))
        assertTrue(result.displayText.contains("timeout=false"))
    }

    private fun successResult(
        sanitizedOutput: String = "こんにちは。",
        displayText: String = sanitizedOutput,
        fallbackUsed: Boolean = false,
    ): NpuStandardRouteS1Result = NpuStandardRouteS1Result(
        selection = NpuStandardRouteS1Selection(enabled = true),
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput = sanitizedOutput,
        sanitizedOutput = sanitizedOutput,
        qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        runDecodeReached = true,
        npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed = fallbackUsed,
        timeout = false,
        freshCrash = false,
        displayText = displayText,
    )
}
