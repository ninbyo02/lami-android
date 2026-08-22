package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBubbleStreamingTest {

    @Test
    fun splitStreamingText_pythonFusionTail_isSeparated() {
        val split = splitStreamingText("説明です\npythonimport random")

        assertEquals("説明です", split.stable)
        assertEquals("pythonimport random", split.unstable)
    }

    @Test
    fun isPythonFusionStart_detectsFusedPattern() {
        assertTrue(isPythonFusionStart("pythonimport os"))
        assertTrue(isPythonFusionStart("python def main():"))
        assertTrue(isPythonFusionStart("pythonfor i in range(3):"))
        assertTrue(isPythonFusionStart("pythondefcreate_grid("))
        assertFalse(isPythonFusionStart("text pythonimport os"))
    }

    @Test
    fun provisionalCodeDetection_handlesLanguageTagAndDenseCode() {
        assertTrue(shouldTreatAsProvisionalCode("python"))
        assertTrue(shouldTreatAsProvisionalCode("for i in range(3):"))
        assertFalse(shouldTreatAsProvisionalCode("これは通常の文章です"))
    }

    @Test
    fun splitStreamingText_languageTagPlusCode_keepsBothAsUnstable() {
        val split = splitStreamingText("説明です\npython\nimport random")

        assertEquals("説明です", split.stable)
        assertEquals("python\nimport random", split.unstable)
    }

    @Test
    fun splitStreamingText_languageTagPlusAssignment_keepsBothAsUnstable() {
        val split = splitStreamingText("説明です\npython\nGRID_SIZE=8")

        assertEquals("説明です", split.stable)
        assertEquals("python\nGRID_SIZE=8", split.unstable)
    }

    @Test
    fun splitStreamingText_shortGreeting_doesNotSplit() {
        val split = splitStreamingText("こんにちは！")

        assertEquals("こんにちは！", split.stable)
        assertTrue(split.unstable.isEmpty())
    }

    @Test
    fun splitStreamingText_shortConversation_doesNotSplit() {
        val split = splitStreamingText("こんにちは！何かお手伝いできますか？")

        assertEquals("こんにちは！何かお手伝いできますか？", split.stable)
        assertTrue(split.unstable.isEmpty())
    }

    @Test
    fun splitStreamingText_multilineConversationTail_doesNotSplit() {
        val split = splitStreamingText("説明です\nもう少し詳しく教えてください")

        assertEquals("説明です\nもう少し詳しく教えてください", split.stable)
        assertTrue(split.unstable.isEmpty())
    }

    @Test
    fun buildAssistantDisplayText_tailLimit_doesNotTrim() {
        val original = "0123456789"
        val display = buildAssistantDisplayText(
            originalMessage = original,
            tailLimitChars = 4,
        )

        assertFalse(display.isTrimmedForRender)
        assertEquals("0123456789", display.text)
    }

    @Test
    fun sanitizeAssistantMessageForDisplay_removesWholeWsTraceBlock() {
        val raw = """
            === WS TRACE ===
            RAW:
            a␠b
            ----
            NORMALIZED:
            c
            回答本文
        """.trimIndent()

        val sanitized = sanitizeAssistantMessageForDisplay(raw)

        assertEquals("", sanitized)
    }

    @Test
    fun sanitizeAssistantMessageForDisplay_keepsBodyBeforeWsTraceBlock() {
        val raw = """
            回答本文の先頭
            追記です
            === WS TRACE ===
            RAW:
            hidden
        """.trimIndent()

        val sanitized = sanitizeAssistantMessageForDisplay(raw)

        assertEquals("回答本文の先頭\n追記です", sanitized)
    }

    @Test
    fun sanitizeAssistantMessageForDisplay_removesDevStreamBlock() {
        val raw = """
            回答本文の先頭
            === DEV Stream ===
            hidden
        """.trimIndent()

        val sanitized = sanitizeAssistantMessageForDisplay(raw)

        assertEquals("回答本文の先頭", sanitized)
    }

    @Test
    fun shouldUsePlainTextForStreamingCodeFence_streamingWithCodeFence_returnsTrue() {
        val result = shouldUsePlainTextForStreamingCodeFence(
            message = "説明です\n```python\nprint('ok')",
            isStreaming = true,
        )

        assertTrue(result)
    }

    @Test
    fun shouldUsePlainTextForStreamingCodeFence_streamingWithoutCodeFence_returnsFalse() {
        val result = shouldUsePlainTextForStreamingCodeFence(
            message = "説明だけです",
            isStreaming = true,
        )

        assertFalse(result)
    }

    @Test
    fun shouldUsePlainTextForStreamingCodeFence_nonStreamingWithCodeFence_returnsFalse() {
        val result = shouldUsePlainTextForStreamingCodeFence(
            message = "```python\nprint('ok')\n```",
            isStreaming = false,
        )

        assertFalse(result)
    }

    @Test
    fun shouldShowCodeGeneratingState_nonStreamingWithOpenCodeFence_returnsFalse() {
        val result = shouldShowCodeGeneratingState(
            isStreaming = false,
            isSegmentClosed = false,
        )

        assertFalse(result)
    }

    @Test
    fun shouldEnableAssistantTextSelection_streaming_returnsFalse() {
        val result = shouldEnableAssistantTextSelection(
            message = "短い回答です",
            isStreaming = true,
        )

        assertFalse(result)
    }

    @Test
    fun shouldEnableAssistantTextSelection_over3000Chars_returnsFalse() {
        val result = shouldEnableAssistantTextSelection(
            message = "a".repeat(3001),
            isStreaming = false,
        )

        assertFalse(result)
    }

    @Test
    fun shouldEnableAssistantTextSelection_shortMessage_returnsTrue() {
        val result = shouldEnableAssistantTextSelection(
            message = "短い回答です",
            isStreaming = false,
        )

        assertTrue(result)
    }

    @Test
    fun finalStreamingPersistence_onlyAppliesRepairAtFinal() {
        val fused = "```python\nimport pygameimport sys#\n```"

        val partial = normalizeStreamingPartialForRender(fused)
        val final = buildFinalizedStreamingResponseForPersist(fused)

        assertEquals(fused, partial)
        assertEquals("```python\nimport pygame\nimport sys\n#\n```", final)
    }


    @Test
    fun shouldShowCodeLineNumbers_streaming_returnsFalse() {
        val result = shouldShowCodeLineNumbers(isStreaming = true)

        assertFalse(result)
    }

    @Test
    fun shouldShowCodeLineNumbers_nonStreaming_returnsTrue() {
        val result = shouldShowCodeLineNumbers(isStreaming = false)

        assertTrue(result)
    }

    @Test
    fun codeBlockScrollSelectionDisabled() {
        assertTrue(
            shouldDisableCodeBlockBodyInteractions(
                code = "print('x')",
                isStreamingCodeBlock = true,
            ),
        )
        assertTrue(
            shouldDisableCodeBlockBodyInteractions(
                code = "a".repeat(3001),
                isStreamingCodeBlock = false,
            ),
        )
        assertFalse(
            shouldDisableCodeBlockBodyInteractions(
                code = "print('x')",
                isStreamingCodeBlock = false,
            ),
        )
    }
    @Test
    fun calculateCodeLineNumberDigits_singleLine_usesTwoDigits() {
        assertEquals(2, calculateCodeLineNumberDigits(1))
    }

    @Test
    fun calculateCodeLineNumberDigits_hundredLines_usesThreeDigits() {
        assertEquals(3, calculateCodeLineNumberDigits(100))
    }

    @Test
    fun buildCodeLinesForDisplay_trailingEmptyLine_isDropped() {
        val lines = buildCodeLinesForDisplay("print('x')\n")

        assertEquals(listOf("print('x')"), lines)
    }

    @Test
    fun buildCodeLinesForDisplay_intentionalBlankLine_isKept() {
        val lines = buildCodeLinesForDisplay("line1\n\nline3")

        assertEquals(listOf("line1", "", "line3"), lines)
    }


    @Test
    fun assistantLifecycleLabel_onlyShowsNonSuccessfulTerminalStates() {
        assertEquals("Generation failed", assistantLifecycleLabel(MessageStatus.FAILED))
        assertEquals("Generation cancelled", assistantLifecycleLabel(MessageStatus.CANCELLED))
        assertEquals("Generation interrupted", assistantLifecycleLabel(MessageStatus.INTERRUPTED))
        assertEquals(null, assistantLifecycleLabel(MessageStatus.COMPLETED))
        assertEquals(null, assistantLifecycleLabel(MessageStatus.GENERATING))
        assertEquals(null, assistantLifecycleLabel(MessageStatus.PENDING))
    }

}
