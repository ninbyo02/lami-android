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

    @Test
    fun headingEmoji_isRemovedAtLineStart() {
        val input = "✅ 機能:\n🧠 動作方法:"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("機能:\n動作方法:", actual)
    }

    @Test
    fun headingCheckEmoji_isRemoved() {
        val actual = SpeechTextBuilder.build("✅ 機能:")

        assertEquals("機能:", actual)
    }

    @Test
    fun headingPageEmoji_isRemoved() {
        val actual = SpeechTextBuilder.build("📄 HTMLコード:")

        assertEquals("HTMLコード:", actual)
    }

    @Test
    fun headingPinEmoji_isRemoved() {
        val actual = SpeechTextBuilder.build("📌 注意:")

        assertEquals("注意:", actual)
    }

    @Test
    fun headingBrainEmoji_isRemoved() {
        val actual = SpeechTextBuilder.build("🧠 今後追加できる機能:")

        assertEquals("今後追加できる機能:", actual)
    }

    @Test
    fun headingPuzzleEmoji_isRemoved() {
        val actual = SpeechTextBuilder.build("🧩 拡張例:")

        assertEquals("拡張例:", actual)
    }

    @Test
    fun boldMarkdown_isStrippedButContentRemains() {
        val actual = SpeechTextBuilder.build("**Minimaxアルゴリズム**")

        assertEquals("Minimaxアルゴリズム", actual)
    }

    @Test
    fun underscoreBoldMarkdown_isStrippedButContentRemains() {
        val actual = SpeechTextBuilder.build("__Alpha-Beta剪定__")

        assertEquals("Alpha-Beta剪定", actual)
    }

    @Test
    fun italicMarkdown_isStrippedButContentRemains() {
        val actual = SpeechTextBuilder.build("*強調*")

        assertEquals("強調", actual)
    }

    @Test
    fun bulletStar_isPreserved() {
        val actual = SpeechTextBuilder.build("* 項目A")

        assertEquals("* 項目A", actual)
    }

    @Test
    fun trailingEmoji_isPreserved() {
        val actual = SpeechTextBuilder.build("成功しました ✅")

        assertEquals("成功しました ✅", actual)
    }


    @Test
    fun atxHeading_level1_isStripped() {
        val actual = SpeechTextBuilder.build("# 概要")

        assertEquals("概要", actual)
    }

    @Test
    fun atxHeading_level2_isStripped() {
        val actual = SpeechTextBuilder.build("## チェック")

        assertEquals("チェック", actual)
    }

    @Test
    fun atxHeading_level3_isStripped() {
        val actual = SpeechTextBuilder.build("### 注意事項")

        assertEquals("注意事項", actual)
    }

    @Test
    fun atxHeading_afterEmojiRemoval_isStripped() {
        val actual = SpeechTextBuilder.build("🤖 ## AIの改善案")

        assertEquals("AIの改善案", actual)
    }

    @Test
    fun inlineHash_isPreserved() {
        val actual = SpeechTextBuilder.build("C# は別物です")

        assertEquals("C# は別物です", actual)
    }

    @Test
    fun hashtag_isPreserved() {
        val actual = SpeechTextBuilder.build("今日は #hashtag を使う")

        assertEquals("今日は #hashtag を使う", actual)
    }

    @Test
    fun nonHeadingDoubleHashWithoutSpace_isPreserved_orHandledSafely() {
        val actual = SpeechTextBuilder.build("##チェック")

        assertEquals("##チェック", actual)
    }

    @Test
    fun mixedHeadingAndBold_areNormalized() {
        val actual = SpeechTextBuilder.build("🤖 **AIの改善案**")

        assertEquals("AIの改善案", actual)
    }

    @Test
    fun inlineOrTrailingEmoji_isPreserved() {
        val input = "成功しました ✅"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("成功しました ✅", actual)
    }

    @Test
    fun bulletMarkers_arePreserved() {
        val input = "• 項目A\n- 項目B"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("• 項目A\n- 項目B", actual)
    }

    @Test
    fun bulletMarkers_areStillPreserved() {
        val input = "• 項目A\n- 項目B"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("• 項目A\n- 項目B", actual)
    }
}
