package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS2DbMapperTest {
    @Test
    fun `successful S1 result creates DB save candidate`() {
        val mapping = NpuStandardRouteS2DbMapper.map(
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
        assertTrue(candidate.sideEffects.dbConnected)
        assertTrue(candidate.sideEffects.conversationHistorySaved)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.markdown)
        assertFalse(candidate.sideEffects.streaming)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
        assertTrue(candidate.readyToPersist)
    }

    @Test
    fun `assistant candidate uses sanitized output and source display text only`() {
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "こんにちは",
            s1Result = successResult(
                rawOutput = "raw diagnostic text",
                sanitizedOutput = "保存する応答。",
                displayText = "保存する応答。",
            ),
        )
        val assistant = requireNotNull(mapping.saveCandidate).assistantMessage

        assertEquals("保存する応答。", assistant.text)
        assertEquals("保存する応答。", assistant.sourceDisplayText)
        assertFalse(assistant.text.contains("raw diagnostic text"))
        assertFalse(assistant.sourceDisplayText.contains("raw diagnostic text"))
    }

    @Test
    fun `failed S1 result creates no DB save candidate`() {
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "こんにちは",
            s1Result = successResult(fallbackUsed = true),
        )

        assertFalse(mapping.hasSaveCandidate)
        assertNull(mapping.saveCandidate)
        assertEquals(NpuStandardRouteS2DbContract.FAILURE_S1_NOT_SUCCESS, mapping.failureReason)
    }

    @Test
    fun `blank user prompt creates no DB save candidate`() {
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "   ",
            s1Result = successResult(),
        )

        assertFalse(mapping.hasSaveCandidate)
        assertNull(mapping.saveCandidate)
        assertEquals(NpuStandardRouteS2DbContract.FAILURE_BLANK_USER_MESSAGE, mapping.failureReason)
    }

    @Test
    fun `S1 contract remains disconnected while S2 candidate connects DB only`() {
        val s1Result = successResult()
        val candidate = requireNotNull(
            NpuStandardRouteS2DbMapper.map(
                userPrompt = "こんにちは",
                s1Result = s1Result,
            ).saveCandidate,
        )

        assertFalse(s1Result.selection.sideEffects.db)
        assertFalse(s1Result.selection.sideEffects.tts)
        assertFalse(s1Result.selection.sideEffects.markdown)
        assertFalse(s1Result.selection.sideEffects.streaming)
        assertTrue(candidate.sideEffects.dbConnected)
        assertTrue(candidate.sideEffects.conversationHistorySaved)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.markdown)
        assertFalse(candidate.sideEffects.streaming)
    }

    private fun successResult(
        rawOutput: String = "こんにちは。",
        sanitizedOutput: String = "こんにちは。",
        displayText: String = sanitizedOutput,
        qualityClassification: String = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        fallbackUsed: Boolean = false,
    ): NpuStandardRouteS1Result = NpuStandardRouteS1Result(
        selection = NpuStandardRouteS1Selection(enabled = true),
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput = rawOutput,
        sanitizedOutput = sanitizedOutput,
        qualityClassification = qualityClassification,
        runDecodeReached = true,
        npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed = fallbackUsed,
        timeout = false,
        freshCrash = false,
        displayText = displayText,
    )
}
