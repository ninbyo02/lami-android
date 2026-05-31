package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS2DbContractTest {
    @Test
    fun `S2 side effects connect only DB`() {
        val sideEffects = NpuStandardRouteS2DbSideEffects()

        assertTrue(sideEffects.dbConnected)
        assertTrue(sideEffects.conversationHistorySaved)
        assertFalse(sideEffects.tts)
        assertFalse(sideEffects.markdown)
        assertFalse(sideEffects.streaming)
        assertFalse(sideEffects.backendNpuPersisted)
        assertTrue(sideEffects.onlyDbConnected)
    }

    @Test
    fun `save candidate contains user and assistant candidates only`() {
        val candidate = NpuStandardRouteS2DbSaveCandidate(
            userMessage = NpuStandardRouteS2DbUserMessageCandidate(text = "こんにちは"),
            assistantMessage = NpuStandardRouteS2DbAssistantMessageCandidate(
                text = "こんにちは。",
                sourceDisplayText = "こんにちは。",
            ),
        )

        assertTrue(candidate.readyToPersist)
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
    }

    @Test
    fun `mapping without candidate has no persistence candidate`() {
        val mapping = NpuStandardRouteS2DbMapping(
            saveCandidate = null,
            failureReason = NpuStandardRouteS2DbContract.FAILURE_S1_NOT_SUCCESS,
        )

        assertFalse(mapping.hasSaveCandidate)
        assertNull(mapping.saveCandidate)
        assertEquals("s1_success_criteria_not_met", mapping.failureReason)
    }
}
