package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qairt244NpuOutputSanitizerTest {
    @Test
    fun `removes end of turn token`() {
        val result = Qairt244NpuOutputSanitizer.sanitize("こんにちは！<end_of_turn>", "こんにちは")

        assertEquals("こんにちは！", result.sanitizedOutput)
        assertTrue(result.sanitizerApplied)
        assertEquals(1, result.removedTemplateTokenCount)
    }

    @Test
    fun `removes start of turn user token`() {
        val result = Qairt244NpuOutputSanitizer.sanitize("<start_of_turn>user\nこんにちは！", "こんにちは")

        assertEquals("こんにちは！", result.sanitizedOutput)
        assertEquals(1, result.removedTemplateTokenCount)
    }

    @Test
    fun `removes start of turn model token`() {
        val result = Qairt244NpuOutputSanitizer.sanitize("<start_of_turn>model\nこんにちは！", "こんにちは")

        assertEquals("こんにちは！", result.sanitizedOutput)
        assertEquals(1, result.removedTemplateTokenCount)
    }

    @Test
    fun `removes leading prompt echo`() {
        val result = Qairt244NpuOutputSanitizer.sanitize("こんにちは\nこんにちは！", "こんにちは")

        assertEquals("こんにちは！", result.sanitizedOutput)
        assertTrue(result.removedPromptEcho)
    }

    @Test
    fun `removes prompt echo before natural assistant text`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            "こんにちは\n<end_of_turn>\nこんにちは！何かお手伝いできることはありますか？",
            "こんにちは",
        )

        assertEquals("こんにちは！何かお手伝いできることはありますか？", result.sanitizedOutput)
        assertTrue(result.removedPromptEcho)
        assertEquals(1, result.removedTemplateTokenCount)
    }

    @Test
    fun `removes repeated turn artifact after first assistant response`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            """
            アシスタント: こんにちは！
            ユーザー: こんにちは
            アシスタント: 繰り返し
            """.trimIndent(),
            "こんにちは",
        )

        assertEquals("こんにちは！", result.sanitizedOutput)
    }

    @Test
    fun `returns empty after sanitize when only artifacts remain`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            "<start_of_turn>user\nこんにちは\n<end_of_turn>\n<start_of_turn>model\n<end_of_turn>",
            "こんにちは",
        )

        assertEquals("", result.sanitizedOutput)
        assertTrue(result.sanitizerApplied)
        assertTrue(result.removedPromptEcho)
    }

    @Test
    fun `keeps normal Japanese text unchanged`() {
        val result = Qairt244NpuOutputSanitizer.sanitize("こんにちは！何かお手伝いできますか？", "こんにちは")

        assertEquals("こんにちは！何かお手伝いできますか？", result.sanitizedOutput)
        assertFalse(result.sanitizerApplied)
        assertFalse(result.removedPromptEcho)
        assertEquals(0, result.removedTemplateTokenCount)
    }
}
