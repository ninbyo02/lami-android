package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS5TtsMapperTest {
    @Test
    fun `successful S1 result creates TTS candidate from final assistant text`() {
        val mapping = NpuStandardRouteS5TtsMapper.map(
            s1Result = successResult(),
            finalAssistantText = "  こんにちは。  ",
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
    fun `injected sanitizer creates speak text`() {
        val mapping = NpuStandardRouteS5TtsMapper.map(
            s1Result = successResult(),
            finalAssistantText = "# 見出し\n\n- 項目1\n- 項目2",
            ttsEnabled = true,
            sanitizeForTts = { text ->
                text.lineSequence()
                    .map { it.removePrefix("# ").removePrefix("- ") }
                    .joinToString(" ")
                    .trim()
            },
        )
        val candidate = requireNotNull(mapping.ttsCandidate)

        assertEquals("# 見出し\n\n- 項目1\n- 項目2", candidate.finalAssistantText)
        assertEquals("見出し  項目1 項目2", candidate.speakText)
    }

    @Test
    fun `failure S1 result creates no candidate`() {
        val mapping = NpuStandardRouteS5TtsMapper.map(
            s1Result = successResult(fallbackUsed = true),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_S1_NOT_SUCCESS, mapping.failureReason)
    }

    @Test
    fun `disabled TTS creates no candidate`() {
        val mapping = NpuStandardRouteS5TtsMapper.map(
            s1Result = successResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = false,
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_TTS_DISABLED, mapping.failureReason)
    }

    @Test
    fun `streaming active creates no candidate`() {
        val mapping = NpuStandardRouteS5TtsMapper.map(
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
    fun `empty final text creates no candidate`() {
        val mapping = NpuStandardRouteS5TtsMapper.map(
            s1Result = successResult(),
            finalAssistantText = "   ",
            ttsEnabled = true,
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_EMPTY_TEXT, mapping.failureReason)
    }

    @Test
    fun `punctuation only output creates no candidate after sanitizer`() {
        val mapping = NpuStandardRouteS5TtsMapper.map(
            s1Result = successResult(),
            finalAssistantText = "。",
            ttsEnabled = true,
            sanitizeForTts = { text ->
                if (text.all { !it.isLetterOrDigit() }) "" else text
            },
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_EMPTY_SPEAK_TEXT, mapping.failureReason)
    }

    @Test
    fun `sanitizer empty result creates no candidate`() {
        val mapping = NpuStandardRouteS5TtsMapper.map(
            s1Result = successResult(),
            finalAssistantText = "```kotlin\nprintln(\"hello\")\n```",
            ttsEnabled = true,
            sanitizeForTts = { "" },
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_EMPTY_SPEAK_TEXT, mapping.failureReason)
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
