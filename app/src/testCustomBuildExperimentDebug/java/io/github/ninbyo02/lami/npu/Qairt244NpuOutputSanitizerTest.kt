package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qairt244NpuOutputSanitizerCodeAwareTest {
    @Test
    fun `preserves indentation inside fenced python code`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = """
                ```python
                def add(a, b):
                    return a + b

                if __name__ == "__main__":
                    print(add(1, 2))
                ```
            """.trimIndent(),
            prompt = "Pythonで簡単な電卓コードを書いて",
        )

        assertEquals(
            """
            ```python
            def add(a, b):
                return a + b

            if __name__ == "__main__":
                print(add(1, 2))
            ```
            """.trimIndent(),
            result.sanitizedOutput,
        )
        assertTrue(result.codeBlockDetected)
        assertFalse(result.codeFenceCompleted)
    }

    @Test
    fun `completes unclosed code fence after token limit truncation`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = """
                はい、例です。

                ```python
                def add(a, b):
                    return a + b
            """.trimIndent(),
            prompt = "Pythonで簡単な電卓コードを書いて",
        )

        assertEquals(
            """
            はい、例です。

            ```python
            def add(a, b):
                return a + b
            ```
            """.trimIndent(),
            result.sanitizedOutput,
        )
        assertTrue(result.codeBlockDetected)
        assertTrue(result.codeFenceCompleted)
    }

    @Test
    fun `keeps non code sanitizer behavior for echo trim and template tokens`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = """
                <start_of_turn>user
                こんにちは
                <end_of_turn>
                <start_of_turn>model
                > こんにちは！何かお手伝いできますか？
                <end_of_turn>
            """.trimIndent(),
            prompt = "こんにちは",
        )

        assertEquals("こんにちは！何かお手伝いできますか？", result.sanitizedOutput)
        assertTrue(result.removedPromptEcho)
        assertTrue(result.removedTemplateTokenCount > 0)
        assertFalse(result.codeBlockDetected)
        assertFalse(result.sanitizedOutput.contains("<start_of_turn>"))
        assertFalse(result.sanitizedOutput.contains("<end_of_turn>"))
    }

    @Test
    fun `preserves greater than prefix inside code block only`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = """
                > 説明です。

                ```python
                def quote():
                    return "> keep this"
                ```
            """.trimIndent(),
            prompt = "Pythonで簡単な電卓コードを書いて",
        )

        assertEquals(
            """
            説明です。

            ```python
            def quote():
                return "> keep this"
            ```
            """.trimIndent(),
            result.sanitizedOutput,
        )
    }

    @Test
    fun `suppresses repeated non code completion while preserving repeated code lines`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = """
                了解しました。
                了解しました。

                ```python
                print("same")
                print("same")
                ```
            """.trimIndent(),
            prompt = "Pythonで簡単な電卓コードを書いて",
        )

        assertEquals(
            """
            了解しました。

            ```python
            print("same")
            print("same")
            ```
            """.trimIndent(),
            result.sanitizedOutput,
        )
    }

    @Test
    fun `keeps leading non japanese drift suppression before natural answer`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = """
                Sure, here is the answer.
                こんにちは、回答です。
            """.trimIndent(),
            prompt = "こんにちは",
        )

        assertEquals("こんにちは、回答です。", result.sanitizedOutput)
        assertTrue(result.removedTemplateTokenCount > 0)
    }

    @Test
    fun `snapshot for bounded retry style code output`() {
        val result = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = """
                >
                はい、承知いたしました。

                ```python
                def add(x, y):
                    # 2つの数を加算する
                    return x + y

                def divide(x, y):
                    if y == 0:
                        return "エラー"
                    return x / y
                elif choice == '
            """.trimIndent(),
            prompt = "Pythonで簡単な電卓コードを書いて",
        )

        assertEquals(
            """
            はい、承知いたしました。

            ```python
            def add(x, y):
                # 2つの数を加算する
                return x + y

            def divide(x, y):
                if y == 0:
                    return "エラー"
                return x / y
            elif choice == '
            ```
            """.trimIndent(),
            result.sanitizedOutput,
        )
        assertTrue(result.sanitizerApplied)
        assertTrue(result.codeFenceCompleted)
        assertFalse(result.sanitizedOutput.contains("<start_of_turn>"))
        assertFalse(result.sanitizedOutput.contains("<end_of_turn>"))
    }
}
