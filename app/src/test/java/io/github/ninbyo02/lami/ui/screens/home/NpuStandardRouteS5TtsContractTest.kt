package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS5TtsContractTest {
    @Test
    fun `side effects default to disconnected`() {
        val sideEffects = NpuStandardRouteS5TtsSideEffects()

        assertFalse(sideEffects.ttsInvoked)
        assertFalse(sideEffects.streaming)
        assertFalse(sideEffects.backendNpuPersisted)
        assertTrue(sideEffects.disconnected)
    }

    @Test
    fun `candidate is ready only without connected side effects`() {
        val candidate = NpuStandardRouteS5TtsCandidate(
            finalAssistantText = "こんにちは。",
            speakText = "こんにちは。",
        )

        assertTrue(candidate.readyToSpeak)

        val connected = candidate.copy(
            sideEffects = NpuStandardRouteS5TtsSideEffects(ttsInvoked = true),
        )
        assertFalse(connected.readyToSpeak)
    }

    @Test
    fun `mapping reports candidate availability`() {
        val mapping = NpuStandardRouteS5TtsMapping(
            ttsCandidate = NpuStandardRouteS5TtsCandidate(
                finalAssistantText = "こんにちは。",
                speakText = "こんにちは。",
            ),
        )

        assertTrue(mapping.hasTtsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.ROUTE_TYPE, "standard_chat_screen_s5_npu_tts")
    }

    @Test
    fun `mapping without candidate is unavailable`() {
        val mapping = NpuStandardRouteS5TtsMapping(
            ttsCandidate = null,
            failureReason = NpuStandardRouteS5TtsContract.FAILURE_EMPTY_TEXT,
        )

        assertFalse(mapping.hasTtsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_EMPTY_TEXT, mapping.failureReason)
    }
}
