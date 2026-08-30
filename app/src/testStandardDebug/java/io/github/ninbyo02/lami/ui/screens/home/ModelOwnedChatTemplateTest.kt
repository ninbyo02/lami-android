package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelOwnedChatTemplateTest {
    @Test
    fun `known profile renders model metadata role framing in order`() {
        val rendered = ModelOwnedChatTemplate.renderKnownProfile(
            contextText = "ユーザー: 私の名前は佐藤です。\nアシスタント: 佐藤さんですね。",
            userPrompt = "私の名前は分かりますか。",
            systemInstruction = "日本語で回答してください。",
        )

        assertEquals(
            "<bos><|turn>system\n日本語で回答してください。<turn|>\n" +
                "<|turn>user\n私の名前は佐藤です。<turn|>\n" +
                "<|turn>model\n佐藤さんですね。<turn|>\n" +
                "<|turn>user\n私の名前は分かりますか。<turn|>\n" +
                "<|turn>model\n",
            rendered,
        )
    }
    @Test
    fun `unknown model metadata template is rejected before native decode`() {
        val model = File.createTempFile("unsupported-template", ".litertlm")
        try {
            model.writeText("not a LiteRT model chat template")

            val verification = ModelOwnedChatTemplate.verify(model.absolutePath)

            assertFalse(verification.supported)
            assertEquals("embedded_chat_template_not_found", verification.reason)
        } finally {
            model.delete()
        }
    }

    @Test
    fun `native adapter delegates BOS to tokenizer and keeps model turn framing`() {
        val rendered = ModelOwnedChatTemplate.renderForNativeAdapter(
            contextText = "",
            userPrompt = "こんにちは",
        )

        assertFalse(rendered.contains("<bos>"))
        assertTrue(rendered.startsWith("<|turn>system\n"))
        assertTrue(rendered.contains("<|turn>user\nこんにちは<turn|>"))
        assertTrue(rendered.endsWith("<|turn>model\n"))
    }

    @Test
    fun `hidden template validation accepts paired supplementary Unicode`() {
        val result = NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(
            "<|turn>model\nこんにちは 😊<turn|>",
        )

        assertTrue(result.isValid)
        assertEquals("ok", result.reasonCode)
    }

    @Test
    fun `shared sanitizer removes prompt echo and template tokens`() {
        val result = LocalInferenceResponseSanitizer.sanitize(
            rawOutput = "質問です。<turn|>\n回答です。<end_of_turn>",
            prompt = "質問です。",
        )

        assertEquals("回答です。", result.sanitizedOutput)
        assertTrue(result.sanitizerApplied)
        assertTrue(result.removedPromptEcho)
        assertTrue(result.removedTemplateTokenCount > 0)
    }

    @Test
    fun `shared sanitizer removes inline foreign script contamination from Japanese output`() {
        val result = LocalInferenceResponseSanitizer.sanitize(
            rawOutput = "はい、先ほど教えていただいた「佐藤さん」 ваш名前です。",
            prompt = "私の名前は分かりますか。",
        )

        assertEquals("はい、先ほど教えていただいた「佐藤さん」 名前です。", result.sanitizedOutput)
        assertTrue(result.sanitizerApplied)
    }
}
