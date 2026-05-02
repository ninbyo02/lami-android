package io.github.ninbyo02.lami.tts

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
    fun headingCheckEmojiWithVariationSelector_isRemoved() {
        val actual = SpeechTextBuilder.build("✅ 機能説明:")

        assertEquals("機能説明:", actual)
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
        val actual = SpeechTextBuilder.build("📌 注意点:")

        assertEquals("注意点:", actual)
    }

    @Test
    fun headingBrainEmoji_isRemoved() {
        val actual = SpeechTextBuilder.build("🧠 今後追加できる機能:")

        assertEquals("今後追加できる機能:", actual)
    }

    @Test
    fun headingDiceEmoji_isRemoved() {
        val actual = SpeechTextBuilder.build("🎲 HTML + JavaScriptで作成した双六ゲーム")

        assertEquals("HTML + JavaScriptで作成した双六ゲーム", actual)
    }

    @Test
    fun headingGamepadEmoji_isRemoved() {
        val actual = SpeechTextBuilder.build("🎮 操作方法:")

        assertEquals("操作方法:", actual)
    }

    @Test
    fun headingGamepadEmojiWithVariationSelector_isRemoved() {
        val actual = SpeechTextBuilder.build("🎮️ 操作方法:")

        assertEquals("操作方法:", actual)
    }

    @Test
    fun headingFlagEmoji_isRemoved() {
        val actual = SpeechTextBuilder.build("🏁 レースゲームのコード")

        assertEquals("レースゲームのコード", actual)
    }

    @Test
    fun headingFlagEmojiWithVariationSelector_isRemoved() {
        val actual = SpeechTextBuilder.build("🏁️ レースゲームのコード")

        assertEquals("レースゲームのコード", actual)
    }

    @Test
    fun headingSmileEmoji_isRemoved() {
        val actual = SpeechTextBuilder.build("😊 ありがとうございます")

        assertEquals("ありがとうございます", actual)
    }

    @Test
    fun headingSmileEmojiWithVariationSelector_isRemoved() {
        val actual = SpeechTextBuilder.build("😊️ ありがとうございます")

        assertEquals("ありがとうございます", actual)
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
    fun asteriskList_isNormalizedForSpeech() {
        val input = "* 項目A\n* 項目B"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("項目A\n項目B", actual)
    }

    @Test
    fun trailingEmoji_isPreserved() {
        val actual = SpeechTextBuilder.build("成功しました ✅")

        assertEquals("成功しました ✅", actual)
    }

    @Test
    fun trailingSmileEmoji_isRemoved() {
        val actual = SpeechTextBuilder.build("追加できますよ 😊")

        assertEquals("追加できますよ", actual)
    }

    @Test
    fun trailingSmileEmojiWithVariationSelector_isRemoved() {
        val actual = SpeechTextBuilder.build("追加できますよ 😊️")

        assertEquals("追加できますよ", actual)
    }

    @Test
    fun trailingSmileEmojiAfterPeriod_isRemoved() {
        val actual = SpeechTextBuilder.build("追加できますよ。😊")

        assertEquals("追加できますよ。", actual)
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
    fun atxHeadingWithPinEmoji_isFullyNormalized() {
        val actual = SpeechTextBuilder.build("## 📌 使い方")

        assertEquals("使い方", actual)
    }

    @Test
    fun atxHeadingWithBrainEmoji_isFullyNormalized() {
        val actual = SpeechTextBuilder.build("## 🧠 次の改善案（任意）")

        assertEquals("次の改善案（任意）", actual)
    }

    @Test
    fun atxHeadingWithCheckEmoji_isFullyNormalized() {
        val actual = SpeechTextBuilder.build("### ✅ 機能説明")

        assertEquals("機能説明", actual)
    }

    @Test
    fun plainHeadingEmojiStillRemoved() {
        val actual = SpeechTextBuilder.build("📌 注意点:")

        assertEquals("注意点:", actual)
    }

    @Test
    fun atxHeadingWithoutEmoji_isStillStripped() {
        val actual = SpeechTextBuilder.build("## 注意事項")

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
    fun bulletList_isNormalizedForSpeech() {
        val input = "• 項目A\n• 項目B\n• 項目C"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("項目A\n項目B\n項目C", actual)
    }

    @Test
    fun dashList_isNormalizedForSpeech() {
        val input = "- 手順A\n- 手順B"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("手順A\n手順B", actual)
    }

    @Test
    fun numberedList_isNormalizedForSpeech() {
        val input = "1. 手順A\n2. 手順B\n3. 手順C"

        val actual = SpeechTextBuilder.build(input)

        assertEquals("手順A\n手順B\n手順C", actual)
    }

    @Test
    fun mixedListMarkers_areNormalizedForSpeech() {
        val input = """
            • 項目A
            - 項目B
            * 項目C

            1. 手順A
            2. 手順B
        """.trimIndent()

        val actual = SpeechTextBuilder.build(input)

        assertEquals("項目A\n項目B\n項目C\n\n手順A\n手順B", actual)
    }

    @Test
    fun inlineNumericText_isPreserved() {
        val actual = SpeechTextBuilder.build("version 1.2 を確認")

        assertEquals("version 1.2 を確認", actual)
    }

    @Test
    fun inlineHashText_isPreserved() {
        val actual = SpeechTextBuilder.build("C# は別物です")

        assertEquals("C# は別物です", actual)
    }

    @Test
    fun htmlCodeBlock_isReplacedWithGuideOnly() {
        val input = """
            以下はサンプルです。

            ```html
            <!DOCTYPE html>
            <html lang="ja">
            <body>
              <h1>タイトル</h1>
            </body>
            </html>
            ```

            実行してください。
        """.trimIndent()

        val actual = SpeechTextBuilder.build(input)

        assertContainsAll(actual, "以下はサンプルです。", "実行してください。")
        assertContainsCodeGuide(actual)
        assertContainsNone(actual, "<html", "<body", "<h1>", "<!DOCTYPE html>")
    }

    @Test
    fun codeBlockEmoji_isNotIncludedInSpeechText() {
        val input = """
            ゲーム例です。

            ```html
            <h1>🎯 ホッケーゲーム 🎯</h1>
            const symbols = ['🍎', '🍌', '🍇'];
            ```

            以上です。
        """.trimIndent()

        val actual = SpeechTextBuilder.build(input)

        assertContainsAll(actual, "ゲーム例です。", "以上です。")
        assertContainsCodeGuide(actual)
        assertContainsNone(actual, "🎯", "🍎", "🍌", "🍇")
    }

    @Test
    fun codeBlockSymbolsAndComments_areNotIncludedInSpeechText() {
        val input = """
            説明です。

            ```js
            // ゲームの初期化
            const score = 0;
            ====
            ****
            ```
        """.trimIndent()

        val actual = SpeechTextBuilder.build(input)

        assertTrue(actual.contains("説明です。"))
        assertContainsCodeGuide(actual)
        assertFalse(actual.contains("//"))
        assertFalse(actual.contains("const score"))
        assertFalse(actual.contains("===="))
        assertFalse(actual.contains("****"))
    }

    @Test
    fun surroundingText_isPreservedWhenCodeBlockExists() {
        val input = """
            これは双六ゲームの例です。

            ```html
            <div>🎲 game</div>
            ```

            ブラウザで実行してください。
        """.trimIndent()

        val actual = SpeechTextBuilder.build(input)

        assertContainsAll(actual, "これは双六ゲームの例です。", "ブラウザで実行してください。")
        assertContainsCodeGuide(actual)
        assertContainsNone(actual, "🎲", "<div>")
    }


    @Test
    fun pythonFencedCode_isReplacedWithCodeGuideWithoutReadingBody() {
        val input = """
            Pythonの例です。

            ```python
            def hello(name: str) -> None:
                print(f"Hello, {name}")
            ```

            `print()` を実行します。
        """.trimIndent()

        val actual = SpeechTextBuilder.build(input)

        assertContainsAll(actual, "Pythonの例です。", "print 関数呼び出し を実行します。")
        assertTrue(actual.contains("コード例があります"))
        assertContainsNone(actual, "def hello", "Hello, {name}")
    }
    private fun assertContainsCodeGuide(actual: String) {
        assertTrue(
            actual.contains("コード例があります") ||
                actual.contains("詳細なコード例があります")
        )
    }

    private fun assertContainsAll(actual: String, vararg expectedFragments: String) {
        expectedFragments.forEach { fragment ->
            assertTrue(actual.contains(fragment))
        }
    }

    private fun assertContainsNone(actual: String, vararg unexpectedFragments: String) {
        unexpectedFragments.forEach { fragment ->
            assertFalse(actual.contains(fragment))
        }
    }
}
