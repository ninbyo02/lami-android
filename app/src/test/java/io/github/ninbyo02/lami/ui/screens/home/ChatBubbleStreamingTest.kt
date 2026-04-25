package io.github.ninbyo02.lami.ui.screens.home

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
    fun buildAssistantDisplayText_tailLimit_appliesNoticeAndTail() {
        val original = "0123456789"
        val display = buildAssistantDisplayText(
            originalMessage = original,
            tailLimitChars = 4,
        )

        assertTrue(display.isTrimmedForRender)
        assertTrue(display.text.startsWith("...(前半省略 / 表示負荷軽減中)...\n"))
        assertTrue(display.text.endsWith("6789"))
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
}
