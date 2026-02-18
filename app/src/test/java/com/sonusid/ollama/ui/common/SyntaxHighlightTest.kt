package com.sonusid.ollama.ui.common

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlightTest {

    @Test
    fun kotlinCode_addsStylesForKeywordCommentAndString() {
        val code = """
            fun greet(name: String) {
                // hello comment
                val text = "hello"
                println(text)
            }
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "kotlin",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "fun"))
        assertTrue(containsStyledFragment(result, "// hello comment"))
        assertTrue(containsStyledFragment(result, "\"hello\""))
    }

    @Test
    fun pythonCode_addsStylesForKeywordCommentAndNumber() {
        val code = """
            def add(a, b):
                # sample comment
                return a + b + 10
        """.trimIndent()

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "py",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "def"))
        assertTrue(containsStyledFragment(result, "# sample comment"))
        assertTrue(containsStyledFragment(result, "10"))
    }

    @Test
    fun javascriptCode_addsStylesForKeywordAndComment() {
        val code = "function x(){ return 1 } // c"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "js",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "function"))
        assertTrue(containsStyledFragment(result, "return"))
        assertTrue(containsStyledFragment(result, "// c"))
    }

    @Test
    fun typescriptCode_addsStylesForKeyword() {
        val code = "type A = string"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "ts",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "type"))
    }

    @Test
    fun sqlCode_addsStylesForKeywordAndComment() {
        val code = "select * from t -- c"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "sql",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "select"))
        assertTrue(containsStyledFragment(result, "from"))
        assertTrue(containsStyledFragment(result, "-- c"))
    }

    @Test
    fun htmlCode_addsStylesForCommentAndString() {
        val code = "<!-- c --> <div class='x'>hi</div>"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "html",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "<!-- c -->"))
        assertTrue(containsStyledFragment(result, "'x'"))
    }

    @Test
    fun jsonCode_addsStylesForStringAndLiteralKeyword() {
        val code = "{ \"a\": true, \"b\": null }"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "json",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "\"a\""))
        assertTrue(containsStyledFragment(result, "true"))
        assertTrue(containsStyledFragment(result, "null"))
    }

    @Test
    fun yamlCode_addsStylesForComment() {
        val code = "a: 1 # c"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "yaml",
            colors = lightColorScheme(),
        )

        assertFalse(result.spanStyles.isEmpty())
        assertTrue(containsStyledFragment(result, "# c"))
    }

    @Test
    fun unsupportedLanguage_returnsPlainAnnotatedString() {
        val code = "plain text"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "unknown",
            colors = lightColorScheme(),
        )

        assertTrue(result.spanStyles.isEmpty())
    }

    private fun containsStyledFragment(
        annotatedString: AnnotatedString,
        fragment: String,
    ): Boolean {
        return annotatedString.spanStyles.any { range ->
            val start = range.start
            val end = range.end
            start in 0 until end && end <= annotatedString.length &&
                annotatedString.text.substring(start, end).contains(fragment)
        }
    }
}
