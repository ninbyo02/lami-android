package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS4PseudoStreamingBridgeTest {
    @Test
    fun `bridge returns pseudo streaming candidate for successful S1 result`() {
        val mapping = NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
            s1Result = successResult(),
            finalText = LONG_TEXT,
        )
        val candidate = requireNotNull(mapping.pseudoStreamingCandidate)

        assertTrue(mapping.hasPseudoStreamingCandidate)
        assertNull(mapping.failureReason)
        assertEquals(LONG_TEXT, candidate.finalText)
        assertEquals(LONG_TEXT, candidate.dbPersistedText)
        assertEquals(LONG_TEXT, candidate.chunks.last())
        assertTrue(candidate.chunks.size in 3..5)
    }

    @Test
    fun `bridge returns no candidate when S1 success criteria fail`() {
        val mapping = NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
            s1Result = successResult(fallbackUsed = true),
            finalText = LONG_TEXT,
        )

        assertFalse(mapping.hasPseudoStreamingCandidate)
        assertNull(mapping.pseudoStreamingCandidate)
        assertEquals(NpuStandardRouteS4PseudoStreamingContract.FAILURE_S1_NOT_SUCCESS, mapping.failureReason)
    }

    @Test
    fun `bridge returns no candidate for empty text`() {
        val mapping = NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
            s1Result = successResult(),
            finalText = "  ",
        )

        assertFalse(mapping.hasPseudoStreamingCandidate)
        assertNull(mapping.pseudoStreamingCandidate)
        assertEquals(NpuStandardRouteS4PseudoStreamingContract.FAILURE_EMPTY_TEXT, mapping.failureReason)
    }

    @Test
    fun `bridge does not depend on markdown finalization`() {
        val markdownText = """
            # 見出し

            - 項目1
            - 項目2
        """.trimIndent()

        val candidate = requireNotNull(
            NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
                s1Result = successResult(sanitizedOutput = markdownText),
                finalText = markdownText,
            ).pseudoStreamingCandidate,
        )

        assertEquals(markdownText, candidate.finalText)
        assertEquals(markdownText, candidate.dbPersistedText)
        assertEquals(markdownText, candidate.chunks.last())
    }

    @Test
    fun `bridge keeps streaming and downstream side effects disconnected`() {
        val candidate = requireNotNull(
            NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
                s1Result = successResult(),
                finalText = LONG_TEXT,
            ).pseudoStreamingCandidate,
        )

        assertFalse(candidate.sideEffects.realTokenStreaming)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
        assertTrue(candidate.sideEffects.disconnected)
        assertTrue(candidate.readyToDisplay)
    }

    @Test
    fun `bridge forwards custom chunk bounds`() {
        val candidate = requireNotNull(
            NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
                s1Result = successResult(),
                finalText = LONG_TEXT,
                minChunks = 4,
                maxChunks = 4,
            ).pseudoStreamingCandidate,
        )

        assertEquals(4, candidate.chunks.size)
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

    private companion object {
        const val LONG_TEXT = "こんにちは。今日はNPU応答を段階表示します。最終的な保存本文は全文だけです。"
    }
}
