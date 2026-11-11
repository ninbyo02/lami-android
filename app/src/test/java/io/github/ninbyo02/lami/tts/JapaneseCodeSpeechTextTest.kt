package io.github.ninbyo02.lami.tts

import io.github.ninbyo02.lami.ui.screens.home.ResponseSpeechSession
import io.github.ninbyo02.lami.ui.screens.home.sanitizeAssistantStreamingTextForTts
import io.github.ninbyo02.lami.ui.screens.home.sanitizeAssistantTextForTts
import org.junit.Assert.*
import org.junit.Test

class JapaneseCodeSpeechTextTest {
    @Test fun keywordsUseJapaneseReadings() {
        assertEquals("プリントとリターン、パイソン、コトリン、ジェイソン、パイゲームです。",
            sanitizeAssistantTextForTts("printとreturn、Python、Kotlin、JSON、Pygameです。"))
    }

    @Test fun exceptionKeywordsAndCelsiusAreHandledInTheSameExplanation() {
        val text = "26°Cを超えたら、tryとcatchで処理します。Cでも確認します。"
        val expected = "26°Cを超えたら、トライとキャッチで処理します。シーでも確認します。"
        assertEquals(expected, sanitizeAssistantTextForTts(text))
        assertEquals(expected, sanitizeAssistantStreamingTextForTts(text))
        assertEquals("気温は26℃です。", sanitizeAssistantStreamingTextForTts("気温は26℃です。"))
    }

    @Test fun identifiersSplitCamelCaseSnakeCaseAndAcronyms() {
        assertEquals("ゲット ユーザー ネーム、ユーザー アンダースコア ネーム、エイチティーティーピー レスポンス 2",
            JapaneseCodeSpeechText.normalize("getUserName、user_name、HTTPResponse2"))
        assertEquals("エーピーアイとジーピーユーとシーピーユー", JapaneseCodeSpeechText.normalize("APIとGPUとCPU"))
    }

    @Test fun dottedIdentifiersAndUnknownInlineNamesAreReadableWithoutEnglishPhonemes() {
        assertEquals("システム ドット アウト ドット プリントライン(`エックスワイゼット`)",
            JapaneseCodeSpeechText.normalize("System.out.println(`xyz`)"))
    }

    @Test fun japaneseAndOrdinaryProseRemainIntactAndConversionIsIdempotent() {
        val text = "通常の説明です。unknown proseを残してprintを確認します。"
        val expected = "通常の説明です。unknown proseを残してプリントを確認します。"
        assertEquals(expected, JapaneseCodeSpeechText.normalize(text))
        assertEquals(expected, JapaneseCodeSpeechText.normalize(expected))
        assertEquals("Hello sunshine", JapaneseCodeSpeechText.normalize("Hello sunshine"))
    }

    @Test fun urlsAreNotMistakenForPackageIdentifiers() {
        val text = "https://example.com/API/getUserName と WWW.example.com、HTTPS://EXAMPLE.COM/API、vscode://file/home/user/app.kt を確認します。"
        assertEquals(text, JapaneseCodeSpeechText.normalize(text))
        assertEquals("https://example.com、プリントを確認します。",
            JapaneseCodeSpeechText.normalize("https://example.com、printを確認します。"))
    }

    @Test fun mixedEnglishAndTechnicalNamesHaveStableReadingsAfterOnePass() {
        for (text in listOf("Hello API", "getUserName print", "Read Python", "Read this file")) {
            val once = JapaneseCodeSpeechText.normalize(text)
            assertEquals(text, once, JapaneseCodeSpeechText.normalize(once))
        }
        assertEquals("ハロー エーピーアイ", JapaneseCodeSpeechText.normalize("Hello API"))
        assertEquals("ゲット ユーザー ネーム プリント", JapaneseCodeSpeechText.normalize("getUserName print"))
    }

    @Test fun japaneseExplanationContainingInlineCallIsNotSilentlyDiscarded() {
        assertEquals("`プリント()`で表示し、`リターン`で値を返します。",
            sanitizeAssistantStreamingTextForTts("`print()`で表示し、`return`で値を返します。"))
        assertEquals("説明です。続きです。", sanitizeAssistantStreamingTextForTts(
            "説明です。\n```python\nprint('hello')\n```\n続きです。"))
        assertEquals("", sanitizeAssistantStreamingTextForTts("print('hello');"))
        assertEquals("This is an example (optional).",
            sanitizeAssistantStreamingTextForTts("This is an example (optional)."))
        assertEquals("", sanitizeAssistantStreamingTextForTts("val count = 3"))
    }

    @Test fun expandedReadingsDoNotAlterTheRawStreamingCursorOrDisplayedText() {
        val session = ResponseSpeechSession().apply { begin() }
        val first = "printで表示します。"
        val complete = first + "returnで値を返します。"
        assertEquals("プリントで表示します。", sanitizeAssistantTextForTts(checkNotNull(session.take(first, false))))
        assertEquals("リターンで値を返します。", sanitizeAssistantTextForTts(checkNotNull(session.take(complete, true))))
        assertEquals("printで表示します。returnで値を返します。", complete)
        assertNull(session.take(complete, true))
    }

    @Test fun headingMarkersAreRemovedBeforeStreamingLinesAreJoined() {
        assertEquals("基本的なコードの骨組み、概念。",
            sanitizeAssistantTextForTts("### 基本的なコードの骨組み（概念）"))
        assertEquals("説明です。基本的なコードの骨組み、概念。続きです。",
            sanitizeAssistantStreamingTextForTts("説明です。\n### 基本的なコードの骨組み（概念）\n続きです。"))
        assertEquals("C#とF#の説明です。",
            SpeechTextBuilder.stripAtxHeadings("C#とF#の説明です。"))
    }

    @Test fun headingAsideCorrectionKeepsNormalParenthesesAndSourceTextUntouched() {
        val source = "### 基本的なコードの骨組み（概念）"
        val prepared = SpeechTextBuilder.prepareHeadingsForSpeech(source)
        assertEquals("基本的なコードの骨組み、概念。", prepared)
        assertEquals(prepared, SpeechTextBuilder.prepareHeadingsForSpeech(prepared))
        assertEquals("### 基本的なコードの骨組み（概念）", source)
        assertEquals("本文（補足）は保持します。\nprint(value)\nC#",
            SpeechTextBuilder.prepareHeadingsForSpeech("本文（補足）は保持します。\nprint(value)\nC#"))
        assertEquals("見出し、補足。", SpeechTextBuilder.prepareHeadingsForSpeech("## 見出し（補足）。"))
        assertEquals("見出し、補足。", sanitizeAssistantTextForTts("🧠 ### 見出し（補足）"))
        assertEquals("見出し、補足。", sanitizeAssistantStreamingTextForTts("🧠 ### 見出し（補足）"))
    }
}
