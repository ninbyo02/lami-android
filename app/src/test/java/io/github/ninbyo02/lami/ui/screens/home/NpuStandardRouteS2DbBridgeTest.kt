package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS2DbBridgeTest {
    @Test
    fun `bridge returns save candidate for successful S1 result`() {
        val mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = " こんにちは ",
            s1Result = successResult(),
        )
        val candidate = requireNotNull(mapping.saveCandidate)

        assertTrue(mapping.hasSaveCandidate)
        assertNull(mapping.failureReason)
        assertEquals("こんにちは", candidate.userMessage.text)
        assertTrue(candidate.userMessage.isSendByMe)
        assertEquals("こんにちは。", candidate.assistantMessage.text)
        assertEquals("こんにちは。", candidate.assistantMessage.sourceDisplayText)
        assertFalse(candidate.assistantMessage.isSendByMe)
    }

    @Test
    fun `bridge returns no save candidate when S1 success criteria fail`() {
        val mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "こんにちは",
            s1Result = successResult(fallbackUsed = true),
        )

        assertFalse(mapping.hasSaveCandidate)
        assertNull(mapping.saveCandidate)
        assertEquals(NpuStandardRouteS2DbContract.FAILURE_S1_NOT_SUCCESS, mapping.failureReason)
    }

    @Test
    fun `bridge returns no save candidate for blank prompt`() {
        val mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "   ",
            s1Result = successResult(),
        )

        assertFalse(mapping.hasSaveCandidate)
        assertNull(mapping.saveCandidate)
        assertEquals(NpuStandardRouteS2DbContract.FAILURE_BLANK_USER_MESSAGE, mapping.failureReason)
    }

    @Test
    fun `bridge connects only DB on S2 candidate`() {
        val candidate = requireNotNull(
            NpuStandardRouteS2DbBridge().prepareSaveCandidate(
                userPrompt = "こんにちは",
                s1Result = successResult(),
            ).saveCandidate,
        )

        assertTrue(candidate.sideEffects.dbConnected)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.markdown)
        assertFalse(candidate.sideEffects.streaming)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
        assertTrue(candidate.sideEffects.onlyDbConnected)
        assertTrue(candidate.readyToPersist)
    }

    private fun successResult(
        fallbackUsed: Boolean = false,
    ): NpuStandardRouteS1Result = NpuStandardRouteS1Result(
        selection = NpuStandardRouteS1Selection(enabled = true),
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput = "こんにちは。",
        sanitizedOutput = "こんにちは。",
        qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        runDecodeReached = true,
        npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed = fallbackUsed,
        timeout = false,
        freshCrash = false,
        displayText = "こんにちは。",
    )
}
