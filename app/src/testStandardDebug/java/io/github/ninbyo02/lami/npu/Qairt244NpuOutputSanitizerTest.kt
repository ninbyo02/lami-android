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
    fun `collapses repeated short answer when prompt explicitly requests one answer`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = "青葉 青葉",
            prompt = "前に伝えた私の名前を一度だけ答えてください。",
        )

        assertEquals("青葉", result.sanitizedOutput)
        assertTrue(result.sanitizerApplied)
    }

    @Test
    fun `keeps repeated words when prompt does not constrain a short answer`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = "とても とても",
            prompt = "感想を自然に答えてください。",
        )

        assertEquals("とてもとても", result.sanitizedOutput)
    }

    @Test
    fun `removes spaced end of turn variant after answer`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(">\n2\n< end_of_turn>", "１＋１は？")

        assertEquals("2", result.sanitizedOutput)
        assertTrue(result.sanitizerApplied)
        assertTrue(result.removedTemplateTokenCount >= 1)
    }

    @Test
    fun `removes closing end of turn variant after answer`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(">答え:2</end_of_turn>", "１＋１は")

        assertEquals("答え:2", result.sanitizedOutput)
        assertTrue(result.sanitizerApplied)
        assertTrue(result.removedTemplateTokenCount >= 1)
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
    fun `removes quoted prompt echo and duplicate empty turn continuations`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            """
            >はじめまして
            <end_of_turn>
            <start_of_turn>user>はじめまして
            <end_of_turn>
            <start_of_turn>model>はじめまして
            <end_of_turn>
            <start_of_turn>user>
            <end_of_turn>
            <start_of_turn>model>何かご用でしょうか？
            <end_of_turn>
            <start_of_turn>user>
            <end_of_turn>
            <start_of_turn>model>何かご用でしょうか？
            <end
            """.trimIndent(),
            "はじめまして",
        )

        assertEquals("何かご用でしょうか？", result.sanitizedOutput)
        assertTrue(result.removedPromptEcho)
    }

    @Test
    fun `removes leading multilingual drift for Japanese hidden prompt`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            """
            कैसा है?

            >元気です。お疲れ様です。

            >お疲れ様です。今日も一日頑張りましょう。
            """.trimIndent(),
            "こんばんは",
        )

        assertEquals(
            "元気です。お疲れ様です。\n\nお疲れ様です。今日も一日頑張りましょう。",
            result.sanitizedOutput,
        )
        assertTrue(result.sanitizerApplied)
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

    @Test
    fun `removes half width spaces between Japanese characters`() {
        val cases = listOf(
            "承 知いたしました。" to "承知いたしました。",
            "短くまとめ る" to "短くまとめる",
            "日 本 語" to "日本語",
            "これは テストです" to "これはテストです",
        )

        cases.forEach { (rawOutput, expected) ->
            val result = Qairt244NpuOutputSanitizer.sanitize(rawOutput, "箇条書きで3つ教えて")

            assertEquals(expected, result.sanitizedOutput)
            assertEquals(rawOutput, result.rawOutput)
            assertTrue(result.sanitizerApplied)
        }
    }

    @Test
    fun `keeps latin term spaces unchanged while normalizing Japanese internal spaces`() {
        val cases = listOf(
            "Google AI" to "Google AI",
            "NPU backend" to "NPU backend",
            "Python は便利です" to "Python は便利です",
            "1. 箇条書きの作成" to "1. 箇条書きの作成",
            "3つの項目" to "3つの項目",
        )

        cases.forEach { (rawOutput, expected) ->
            val result = Qairt244NpuOutputSanitizer.sanitize(rawOutput, "箇条書きで3つ教えて")

            assertEquals(expected, result.sanitizedOutput)
            assertEquals(rawOutput, result.rawOutput)
        }
    }

    @Test
    fun `keeps list newlines while removing Japanese internal spaces`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            "承 知いたしました。\n1. 箇条書きの作成\n3. 短くまとめ る",
            "箇条書きで3つ教えて",
        )

        assertEquals(
            "承知いたしました。\n1. 箇条書きの作成\n3. 短くまとめる",
            result.sanitizedOutput,
        )
        assertTrue(result.sanitizerApplied)
    }

    @Test
    fun `keeps standalone greeting responses even when they match the prompt`() {
        listOf("こんばんは", "こんにちは", "おはよう", "ありがとう").forEach { greeting ->
            val result = Qairt244NpuOutputSanitizer.sanitize(greeting, greeting)

            assertEquals(greeting, result.sanitizedOutput)
            assertFalse(result.removedPromptEcho)
        }
    }

    @Test
    fun `keeps observed short prompt comparison responses non empty`() {
        val cases = listOf(
            "q" to "どうしますか？",
            "え" to "どうしますか？",
            "明日の天気は" to "明日の天気は晴れです。",
        )

        cases.forEach { (prompt, rawOutput) ->
            val result = Qairt244NpuOutputSanitizer.sanitize(rawOutput, prompt)

            assertEquals(rawOutput, result.sanitizedOutput)
            assertFalse(result.removedPromptEcho)
        }
    }

    @Test
    fun `still removes exact non greeting prompt echo`() {
        val result = Qairt244NpuOutputSanitizer.sanitize("明日の天気は", "明日の天気は")

        assertEquals("", result.sanitizedOutput)
        assertTrue(result.removedPromptEcho)
    }
}
