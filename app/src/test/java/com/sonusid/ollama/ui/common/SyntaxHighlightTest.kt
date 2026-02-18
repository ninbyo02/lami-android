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
    fun unsupportedLanguage_returnsPlainAnnotatedString() {
        val code = "SELECT * FROM demo"

        val result = buildHighlightedCodeAnnotatedString(
            code = code,
            language = "sql",
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
