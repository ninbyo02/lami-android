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

    @Test
    fun `stop suppressed assistant blocks S5 TTS replay`() {
        val mapping = readyMapping()

        assertFalse(
            shouldSpeakNpuStandardRouteS5Tts(
                enabled = true,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = true,
                inCooldown = false,
            ),
        )

        val reason = classifyNpuStandardRouteS5TtsSkipReason(
            enabled = true,
            mapping = mapping,
            ttsEnabled = true,
            streamingActive = false,
            assistantId = 42,
            suppressedForAssistant = true,
            inCooldown = false,
        )
        val trace = buildNpuStandardRouteS5TtsSkipTrace(
            reason = reason,
            assistantId = 42,
        )

        assertEquals(NPU_STANDARD_ROUTE_S5_TTS_SKIP_STOP_SUPPRESSED, reason)
        assertTrue(trace.contains("tts_speak_invoked=false"))
        assertTrue(trace.contains("tts_skipped_reason=stop_suppressed"))
        assertTrue(trace.contains("assistant_id=42"))
        assertTrue(trace.contains("backend_npu_persisted=true"))
    }

    private fun readyMapping(): NpuStandardRouteS5TtsMapping =
        NpuStandardRouteS5TtsMapping(
            ttsCandidate = NpuStandardRouteS5TtsCandidate(
                finalAssistantText = "こんにちは。",
                speakText = "こんにちは。",
            ),
        )
}
