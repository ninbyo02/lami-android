package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qairt244NpuOutputSanitizerEscapedNewlineTest {
    @Test
    fun `removes prompt echo when native result contains multiply escaped newlines`() {
        val prompt = "今日やることを3つ、短い箇条書きで教えて"
        val result = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = ">\\\\n" +
                prompt + "\\\\n" +
                "- 朝のTODO: 〇〇を確認する\\\\n" +
                "- 昼のTODO: 〇〇を進める\\\\n" +
                "- 夜のTODO: 〇〇を振り返る\\\\n" +
                "</start_of_turn>",
            prompt = prompt,
        )

        assertEquals(
            """
            - 朝のTODO: 〇〇を確認する
            - 昼のTODO: 〇〇を進める
            - 夜のTODO: 〇〇を振り返る
            """.trimIndent(),
            result.sanitizedOutput,
        )
        assertTrue(result.removedPromptEcho)
        assertTrue(result.removedTemplateTokenCount > 0)
        assertFalse(result.sanitizedOutput.contains(prompt))
        assertFalse(result.sanitizedOutput.contains("\\n"))
    }
}
