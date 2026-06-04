package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS5TtsBridgeTest {
    @Test
    fun `bridge returns TTS candidate for successful S1 result`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = successResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )
        val candidate = requireNotNull(mapping.ttsCandidate)

        assertTrue(mapping.hasTtsCandidate)
        assertNull(mapping.failureReason)
        assertEquals("こんにちは。", candidate.finalAssistantText)
        assertEquals("こんにちは。", candidate.speakText)
        assertFalse(candidate.sideEffects.ttsInvoked)
        assertFalse(candidate.sideEffects.streaming)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
    }

    @Test
    fun `bridge applies injected TTS sanitizer`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = successResult(sanitizedOutput = "# 見出し\n\n```kotlin\nprintln(\"hello\")\n```"),
            finalAssistantText = "raw output must not be used",
            ttsEnabled = true,
            sanitizeForTts = { "見出し。コード例があります。" },
        )
        val candidate = requireNotNull(mapping.ttsCandidate)

        assertEquals("# 見出し\n\n```kotlin\nprintln(\"hello\")\n```", candidate.finalAssistantText)
        assertEquals("見出し。コード例があります。", candidate.speakText)
    }

    @Test
    fun `bridge returns no candidate when S1 success criteria fail`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = successResult(fallbackUsed = true),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_S1_NOT_SUCCESS, mapping.failureReason)
    }

    @Test
    fun `bridge returns no candidate when TTS is disabled`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = successResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = false,
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_TTS_DISABLED, mapping.failureReason)
    }

    @Test
    fun `bridge returns no candidate while streaming is active`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = successResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
            streamingActive = true,
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_STREAMING_ACTIVE, mapping.failureReason)
    }

    @Test
    fun `bridge returns no candidate for empty sanitized output`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = successResult(sanitizedOutput = " "),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_EMPTY_TEXT, mapping.failureReason)
    }

    @Test
    fun `bridge returns no candidate when sanitizer removes all text`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = successResult(),
            finalAssistantText = "。",
            ttsEnabled = true,
            sanitizeForTts = { "" },
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_EMPTY_SPEAK_TEXT, mapping.failureReason)
    }

    private fun successResult(
        sanitizedOutput: String = "こんにちは。",
        fallbackUsed: Boolean = false,
    ): NpuStandardRouteS1Result = NpuStandardRouteS1Result(
        selection = NpuStandardRouteS1Selection(enabled = true),
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput = "こんにちは。",
        sanitizedOutput = sanitizedOutput,
        qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        runDecodeReached = true,
        npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed = fallbackUsed,
        timeout = false,
        freshCrash = false,
        displayText = "こんにちは。",
    )
}
