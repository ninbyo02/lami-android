package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS3MarkdownFixedResponseTest {
    @Test
    fun `mapper keeps fixed markdown response as finalized text`() {
        val mapping = NpuStandardRouteS3MarkdownMapper.map(
            s1Result = successResult(sanitizedOutput = FIXED_MARKDOWN_RESPONSE),
        )
        val candidate = requireNotNull(mapping.markdownCandidate)

        assertTrue(mapping.hasMarkdownCandidate)
        assertNull(mapping.failureReason)
        assertEquals(FIXED_MARKDOWN_RESPONSE, candidate.sanitizedInputText)
        assertEquals(FIXED_MARKDOWN_RESPONSE, candidate.finalizedText)
        assertEquals(FIXED_MARKDOWN_RESPONSE, candidate.sourceDisplayText)
        assertFalse(candidate.repairApplied)
    }

    @Test
    fun `bridge keeps fixed markdown response and code fence unchanged`() {
        val mapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
            s1Result = successResult(sanitizedOutput = FIXED_MARKDOWN_RESPONSE),
        )
        val candidate = requireNotNull(mapping.markdownCandidate)

        assertEquals(FIXED_MARKDOWN_RESPONSE, candidate.finalizedText)
        assertTrue(candidate.finalizedText.contains("# 見出し"))
        assertTrue(candidate.finalizedText.contains("- 項目1"))
        assertTrue(candidate.finalizedText.contains("- 項目2"))
        assertTrue(candidate.finalizedText.contains("```kotlin"))
        assertTrue(candidate.finalizedText.contains("println(\"hello\")"))
        assertEquals(1, candidate.finalizedText.windowed(3).count { it == "```" })
    }

    @Test
    fun `fixed markdown failure creates no markdown candidate`() {
        val mapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
            s1Result = successResult(
                sanitizedOutput = FIXED_MARKDOWN_RESPONSE,
                fallbackUsed = true,
            ),
        )

        assertFalse(mapping.hasMarkdownCandidate)
        assertNull(mapping.markdownCandidate)
        assertEquals(NpuStandardRouteS3MarkdownContract.FAILURE_S1_NOT_SUCCESS, mapping.failureReason)
    }

    @Test
    fun `fixed markdown empty finalizer creates no markdown candidate`() {
        val mapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
            s1Result = successResult(sanitizedOutput = FIXED_MARKDOWN_RESPONSE),
            finalizeMarkdown = { "" },
        )

        assertFalse(mapping.hasMarkdownCandidate)
        assertNull(mapping.markdownCandidate)
        assertEquals(NpuStandardRouteS3MarkdownContract.FAILURE_EMPTY_TEXT, mapping.failureReason)
    }

    @Test
    fun `fixed markdown candidate keeps downstream side effects disconnected`() {
        val candidate = requireNotNull(
            NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
                s1Result = successResult(sanitizedOutput = FIXED_MARKDOWN_RESPONSE),
            ).markdownCandidate,
        )

        assertFalse(candidate.sideEffects.streaming)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
        assertTrue(candidate.sideEffects.downstreamDisconnected)
    }

    private fun successResult(
        sanitizedOutput: String,
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
        displayText = sanitizedOutput,
    )

    private companion object {
        val FIXED_MARKDOWN_RESPONSE = """
            # 見出し

            - 項目1
            - 項目2

            ```kotlin
            println("hello")
        """.trimIndent()
    }
}
