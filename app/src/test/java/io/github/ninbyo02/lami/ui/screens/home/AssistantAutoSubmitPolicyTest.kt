package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantAutoSubmitPolicyTest {
    @Test
    fun rejectsBlankAndSingleCharacterTranscripts() {
        assertFalse(shouldAutoSubmitAssistantTranscript(""))
        assertFalse(shouldAutoSubmitAssistantTranscript("   "))
        assertFalse(shouldAutoSubmitAssistantTranscript("あ"))
    }

    @Test
    fun acceptsShortJapaneseCommandsAndTrimsWhitespace() {
        assertTrue(shouldAutoSubmitAssistantTranscript("天気"))
        assertTrue(shouldAutoSubmitAssistantTranscript("  今日の天気を教えて  "))
    }

    @Test fun successfulAutomaticSubmissionDoesNotShowSnackbar() {
        assertNull(assistantTranscriptSnackbarMessage(autoSubmit = true, willSubmit = true))
    }

    @Test fun rejectedShortTranscriptStillHasFeedback() {
        assertEquals("認識結果が短いため送信しませんでした",
            assistantTranscriptSnackbarMessage(autoSubmit = true, willSubmit = false))
    }

    @Test fun manualVoiceInputStillHasFeedback() {
        assertEquals("音声を入力しました",
            assistantTranscriptSnackbarMessage(autoSubmit = false, willSubmit = false))
    }
}
