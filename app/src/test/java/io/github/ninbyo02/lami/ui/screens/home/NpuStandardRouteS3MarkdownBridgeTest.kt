package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS3MarkdownBridgeTest {
    @Test
    fun `bridge returns markdown candidate for successful S1 result`() {
        val mapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
            s1Result = successResult(),
        )
        val candidate = requireNotNull(mapping.markdownCandidate)

        assertTrue(mapping.hasMarkdownCandidate)
        assertNull(mapping.failureReason)
        assertEquals("こんにちは。", candidate.sanitizedInputText)
        assertEquals("こんにちは。", candidate.sourceDisplayText)
        assertEquals("こんにちは。", candidate.finalizedText)
        assertFalse(candidate.repairApplied)
    }

    @Test
    fun `bridge returns no markdown candidate when S1 success criteria fail`() {
        val mapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
            s1Result = successResult(fallbackUsed = true),
        )

        assertFalse(mapping.hasMarkdownCandidate)
        assertNull(mapping.markdownCandidate)
        assertEquals(NpuStandardRouteS3MarkdownContract.FAILURE_S1_NOT_SUCCESS, mapping.failureReason)
    }

    @Test
    fun `bridge returns no markdown candidate for empty finalized text`() {
        val mapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
            s1Result = successResult(),
            finalizeMarkdown = { "" },
        )

        assertFalse(mapping.hasMarkdownCandidate)
        assertNull(mapping.markdownCandidate)
        assertEquals(NpuStandardRouteS3MarkdownContract.FAILURE_EMPTY_TEXT, mapping.failureReason)
    }

    @Test
    fun `bridge applies injected markdown finalizer`() {
        val mapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
            s1Result = successResult(sanitizedOutput = "line\\nnext"),
            finalizeMarkdown = { it.replace("\\n", "\n") },
        )
        val candidate = requireNotNull(mapping.markdownCandidate)

        assertEquals("line\\nnext", candidate.sanitizedInputText)
        assertEquals("line\nnext", candidate.finalizedText)
        assertTrue(candidate.repairApplied)
    }

    @Test
    fun `bridge keeps downstream side effects disconnected`() {
        val candidate = requireNotNull(
            NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
                s1Result = successResult(),
            ).markdownCandidate,
        )

        assertFalse(candidate.sideEffects.streaming)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
        assertTrue(candidate.sideEffects.downstreamDisconnected)
        assertTrue(candidate.readyToRender)
    }

    @Test
    fun `gate off returns fallback text without finalizing markdown`() {
        var finalizerCalled = false
        val text = NpuStandardRouteS3MarkdownBridge().resolveFinalizedText(
            enabled = false,
            s1Result = successResult(sanitizedOutput = "line\\nnext"),
            fallbackText = "line\\nnext",
            finalizeMarkdown = {
                finalizerCalled = true
                it.replace("\\n", "\n")
            },
        )

        assertEquals("line\\nnext", text)
        assertFalse(finalizerCalled)
    }

    @Test
    fun `gate on returns markdown candidate finalized text`() {
        val text = NpuStandardRouteS3MarkdownBridge().resolveFinalizedText(
            enabled = true,
            s1Result = successResult(sanitizedOutput = "line\\nnext"),
            fallbackText = "line\\nnext",
            finalizeMarkdown = { it.replace("\\n", "\n") },
        )

        assertEquals("line\nnext", text)
    }

    @Test
    fun `gate on with failed S1 returns fallback without finalizing markdown`() {
        var finalizerCalled = false
        val text = NpuStandardRouteS3MarkdownBridge().resolveFinalizedText(
            enabled = true,
            s1Result = successResult(fallbackUsed = true),
            fallbackText = "fallback",
            finalizeMarkdown = {
                finalizerCalled = true
                it.uppercase()
            },
        )

        assertEquals("fallback", text)
        assertFalse(finalizerCalled)
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
