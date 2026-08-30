package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.NpuStandardRouteNativeContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelOwnedChatTemplateNativeSystemTest {
    @Test
    fun `native renderer prioritizes model conversation turns when context exists`() {
        val rendered = ModelOwnedChatTemplate.renderForNativeAdapter(
            contextText = "ユーザー: 好きな色は赤です。\nアシスタント: 赤",
            userPrompt = "好きな色を青に訂正します。",
        )

        assertFalse(rendered.startsWith("<bos>"))
        assertFalse(rendered.contains(ModelOwnedChatTemplate.NATIVE_SYSTEM_INSTRUCTION))
        assertTrue(rendered.startsWith("<|turn>user\n好きな色は赤です。<turn|>"))
        assertTrue(rendered.endsWith("<|turn>model\n"))
    }
    @Test
    fun `native request bounds context within reduced history budget`() {
        val request = RealNpuStandardRouteS1Provider.request(
            userPrompt = "好きな色を青に訂正します。青の一文字だけ答えてください。",
            contextText =
                "ユーザー: 好きな色は赤です。色だけ答えてください。\n" +
                    "アシスタント: 赤",
        )
        val rendered = NpuStandardRouteNativeContract.buildPrompt(
            contextText = request.contextText,
            userPrompt = request.userPrompt,
            promptTailVariant = request.promptTailVariant,
        )

        assertTrue(
            rendered.codePointCount(0, rendered.length) <=
                RealNpuStandardRouteS1Provider.NATIVE_MAX_INPUT_CODE_POINTS,
        )
        assertFalse(rendered.contains(ModelOwnedChatTemplate.NATIVE_SYSTEM_INSTRUCTION))
    }
}
