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
            s1Result = successResult(sanitizedOutput = "# 見出し\n\n- 項目1\n- 項目2"),
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
    fun `empty final assistant text creates no candidate`() {
        val mapping = NpuStandardRouteS5TtsMapper.map(
            s1Result = successResult(sanitizedOutput = "   "),
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
            s1Result = successResult(sanitizedOutput = "。"),
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
            s1Result = successResult(sanitizedOutput = "```kotlin\nprintln(\"hello\")\n```"),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
            sanitizeForTts = { "" },
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_EMPTY_SPEAK_TEXT, mapping.failureReason)
    }

    @Test
    fun `role contamination creates no TTS candidate`() {
        val mapping = NpuStandardRouteS5TtsMapper.map(
            s1Result = successResult(rawOutput = "こんにちは。\nユーザー: お願い"),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_ROLE_CONTAMINATION, mapping.failureReason)
    }

    @Test
    fun `saved S5 success diagnostics use final assistant text`() {
        val result = buildNpuStandardRouteS5TtsSavedResult(
            s1Result = successResult(sanitizedOutput = "こんにちは。"),
            finalAssistantText = "こんにちは。",
        )

        assertTrue(result.displayText.contains("route_type=standard_chat_screen_s5_npu_tts"))
        assertTrue(result.displayText.contains("status=success"))
        assertTrue(result.displayText.contains("reason=success"))
        assertTrue(result.displayText.contains("tts=true"))
        assertTrue(result.displayText.contains("s5_tts_reason=success"))
        assertTrue(result.displayText.contains("tts_requested=true"))
        assertTrue(result.displayText.contains("tts_started=true"))
        assertTrue(result.displayText.contains("tts_completed=true"))
        assertTrue(result.displayText.contains("tts_skipped=false"))
        assertTrue(result.displayText.contains("tts_exception_class=none"))
        assertTrue(result.displayText.contains("tts_exception_message=none"))
        assertTrue(result.displayText.contains("tts_text_length=6"))
        assertTrue(result.displayText.contains("tts_input_source=sanitized_output"))
        assertTrue(result.displayText.contains("sanitized_output=こんにちは。"))
    }

    @Test
    fun `saved S5 TTS exception diagnostics keep NPU success`() {
        val result = buildNpuStandardRouteS5TtsSavedResult(
            s1Result = successResult(sanitizedOutput = "こんにちは。"),
            finalAssistantText = "raw output must not be used",
            ttsDiagnostics = NpuStandardRouteS5TtsContract.exceptionDiagnostics(
                sanitizedOutput = "こんにちは。",
                throwable = IllegalStateException("tts engine failed"),
            ),
        )

        assertTrue(result.displayText.contains("status=success"))
        assertTrue(result.displayText.contains("reason=success"))
        assertTrue(result.displayText.contains("db=true"))
        assertTrue(result.displayText.contains("markdown=true"))
        assertTrue(result.displayText.contains("streaming=true"))
        assertTrue(result.displayText.contains("conversation_history_saved=true"))
        assertTrue(result.displayText.contains("tts=false"))
        assertTrue(result.displayText.contains("s5_tts_reason=tts_exception"))
        assertTrue(result.displayText.contains("tts_requested=true"))
        assertTrue(result.displayText.contains("tts_started=true"))
        assertTrue(result.displayText.contains("tts_completed=false"))
        assertTrue(result.displayText.contains("tts_exception_class=IllegalStateException"))
        assertTrue(result.displayText.contains("tts_exception_message=tts engine failed"))
        assertTrue(result.displayText.contains("tts_input_source=sanitized_output"))
    }

    private fun successResult(
        rawOutput: String = "こんにちは。",
        sanitizedOutput: String = "こんにちは。",
        fallbackUsed: Boolean = false,
    ): NpuStandardRouteS1Result = NpuStandardRouteS1Result(
        selection = NpuStandardRouteS1Selection(enabled = true),
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput = rawOutput,
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
