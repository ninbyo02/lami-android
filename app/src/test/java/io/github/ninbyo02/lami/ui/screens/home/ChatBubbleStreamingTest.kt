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
}
