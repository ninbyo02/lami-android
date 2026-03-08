package com.sonusid.ollama.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextBuilderTest {
    @Test
    fun kotlinCodeBlock_isConvertedToCodeGuide() {
        val input = """
            説明です。
            ```kotlin
            val message = \"hello\"
            ```
        """.trimIndent()

        val actual = SpeechTextBuilder.build(input)

        assertTrue(actual.contains("コード例があります"))
        assertFalse(actual.contains("val message"))
    }

    @Test
    fun bashCodeBlock_isConvertedToCommandGuide() {
        val input = """
            ```bash
            ./gradlew test
            ```
        """.trimIndent()

        val actual = SpeechTextBuilder.build(input)

        assertEquals("実行コマンド例があります", actual)
    }

    @Test
    fun jsonCodeBlock_isConvertedToConfigGuide() {
        val input = """
            ```json
            {\"name\":\"lami\"}
            ```
        """.trimIndent()

        val actual = SpeechTextBuilder.build(input)

        assertEquals("設定例があります", actual)
    }

    @Test
    fun unknownCodeBlock_isConvertedToDetailedCodeGuide() {
        val input = """
            ```
            custom value
            ```
        """.trimIndent()

        val actual = SpeechTextBuilder.build(input)

        assertEquals("詳細なコード例があります", actual)
    }

    @Test
    fun rawUrl_isConvertedToLinkGuide() {
        val input = "詳細は https://example.com/docs を参照してください"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("詳細は リンクがあります を参照してください", actual)
    }

    @Test
    fun emptyText_returnsFallbackMessage() {
        val actual = SpeechTextBuilder.build("   ")

        assertEquals("詳しい内容は画面をご確認下さい", actual)
    }

    @Test
    fun codeBlockOnly_returnsGuideOnly() {
        val input = """
            ```python
            print('hello')
            ```
        """.trimIndent()

        val actual = SpeechTextBuilder.build(input)

        assertEquals("コード例があります", actual)
    }

    @Test
    fun inlineCode_handlesRepresentativeCases() {
        val input = "`https://example.com` と `runTask()` と `git status` を確認"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("リンク と 関数呼び出し と git status コマンド を確認", actual)
    }

    @Test
    fun repeatedSymbols_areReduced() {
        val input = "見出し==== と 区切り---- と 強調****"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("見出し== と 区切り-- と 強調**", actual)
    }

    @Test
    fun consecutiveBlankLines_areCollapsed() {
        val input = "1行目\n\n\n\n2行目"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("1行目\n\n2行目", actual)
    }
}
