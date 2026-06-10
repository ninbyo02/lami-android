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
            finalAssistantText = "# 見出し\n\n```kotlin\nprintln(\"hello\")\n```",
            ttsEnabled = true,
            sanitizeForTts = { "見出し。コード例があります。" },
        )
        val candidate = requireNotNull(mapping.ttsCandidate)

        assertEquals("# 見出し\n\n```kotlin\nprintln(\"hello\")\n```", candidate.finalAssistantText)
        assertEquals("見出し。コード例があります。", candidate.speakText)
    }

    @Test
    fun `bridge uses prepared display output for TTS when sanitized output contains tail leak`() {
        val s1Result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                result = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                success = true,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = ">2</start_of_turn>\n<end_of_turn>\n<start_of_turn>user>次の計算に日本語で",
                sanitizedOutput = "2</start_of_turn>\n\n次の計算に日本語で",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                inputPrompt = "1+1は？",
            ),
        )

        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1Result,
            finalAssistantText = s1Result.actualDisplayText,
            ttsEnabled = true,
        )
        val candidate = requireNotNull(mapping.ttsCandidate)

        assertEquals("2", candidate.finalAssistantText)
        assertEquals("2", candidate.speakText)
        assertFalse(candidate.speakText.contains("<start_of_turn>"))
        assertFalse(candidate.speakText.contains("次の計算"))
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
            finalAssistantText = " ",
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
