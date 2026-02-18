package com.sonusid.ollama.ui.common

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString

private const val TOKEN_NONE = 0
private const val TOKEN_COMMENT = 1
private const val TOKEN_STRING = 2
private const val TOKEN_KEYWORD = 3
private const val TOKEN_NUMBER = 4
private const val TOKEN_FUNCTION = 5

private enum class SupportedLanguage {
    PYTHON,
    KOTLIN,
    BASH,
}

private data class TokenRange(
    val start: Int,
    val end: Int,
    val type: Int,
)

private data class HighlightPalette(
    val comment: Color,
    val string: Color,
    val keyword: Color,
    val number: Color,
    val function: Color,
)

private val pythonKeywords = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
    "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import",
    "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return",
    "True", "try", "while", "with", "yield"
)

private val kotlinKeywords = setOf(
    "as", "break", "class", "continue", "data", "do", "else", "false", "for", "fun", "if",
    "in", "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
    "true", "try", "typealias", "val", "var", "when", "while"
)

private val bashKeywords = setOf(
    "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "case", "esac",
    "function", "select", "until", "time", "coproc", "local", "readonly", "export", "return"
)

fun buildHighlightedCodeAnnotatedString(
    code: String,
    language: String?,
    colors: ColorScheme,
): AnnotatedString {
    val supportedLanguage = normalizeLanguage(language) ?: return AnnotatedString(code)
    if (code.isEmpty()) return AnnotatedString(code)

    val marked = IntArray(code.length)
    val tokens = mutableListOf<TokenRange>()

    collectCommentAndStringTokens(code, supportedLanguage, marked, tokens)

    val simplify = code.length > 10_000
    if (!simplify) {
        collectKeywordNumberAndFunctionTokens(code, supportedLanguage, marked, tokens)
    } else {
        collectKeywordTokensOnly(code, supportedLanguage, marked, tokens)
    }

    val palette = HighlightPalette(
        comment = colors.onSurfaceVariant,
        string = colors.tertiary,
        keyword = colors.primary,
        number = colors.secondary,
        function = colors.secondary,
    )

    return buildAnnotatedString {
        append(code)
        tokens.forEach { token ->
            val style = when (token.type) {
                TOKEN_COMMENT -> SpanStyle(
                    color = palette.comment,
                    fontStyle = FontStyle.Italic,
                )
                TOKEN_STRING -> SpanStyle(color = palette.string)
                TOKEN_KEYWORD -> SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)
                TOKEN_NUMBER -> SpanStyle(color = palette.number)
                TOKEN_FUNCTION -> SpanStyle(color = palette.function, fontWeight = FontWeight.Medium)
                else -> null
            }
            if (style != null && token.start < token.end) {
                addStyle(style = style, start = token.start, end = token.end)
            }
        }
    }
}

private fun normalizeLanguage(language: String?): SupportedLanguage? {
    return when (language?.trim()?.lowercase()) {
        "python", "py" -> SupportedLanguage.PYTHON
        "kotlin", "kt", "kts" -> SupportedLanguage.KOTLIN
        "bash", "sh", "zsh", "shell" -> SupportedLanguage.BASH
        else -> null
    }
}

private fun collectCommentAndStringTokens(
    code: String,
    language: SupportedLanguage,
    marked: IntArray,
    tokens: MutableList<TokenRange>,
) {
    var i = 0
    while (i < code.length) {
        when {
            language == SupportedLanguage.KOTLIN && code.startsWith("//", i) -> {
                val end = findLineEnd(code, i + 2)
                addTokenIfFree(i, end, TOKEN_COMMENT, marked, tokens)
                i = end
            }

            language == SupportedLanguage.KOTLIN && code.startsWith("/*", i) -> {
                val end = findBlockCommentEnd(code, i + 2)
                addTokenIfFree(i, end, TOKEN_COMMENT, marked, tokens)
                i = end
            }

            (language == SupportedLanguage.PYTHON || language == SupportedLanguage.BASH) && code[i] == '#' -> {
                val end = findLineEnd(code, i + 1)
                addTokenIfFree(i, end, TOKEN_COMMENT, marked, tokens)
                i = end
            }

            code.startsWith("\"\"\"", i) -> {
                val end = findTripleQuotedEnd(code, i, "\"\"\"")
                addTokenIfFree(i, end, TOKEN_STRING, marked, tokens)
                i = end
            }

            language == SupportedLanguage.PYTHON && code.startsWith("'''", i) -> {
                val end = findTripleQuotedEnd(code, i, "'''")
                addTokenIfFree(i, end, TOKEN_STRING, marked, tokens)
                i = end
            }

            code[i] == '"' || code[i] == '\'' -> {
                val end = findQuotedEnd(code, i, code[i])
                addTokenIfFree(i, end, TOKEN_STRING, marked, tokens)
                i = end
            }

            else -> i++
        }
    }
}

private fun collectKeywordNumberAndFunctionTokens(
    code: String,
    language: SupportedLanguage,
    marked: IntArray,
    tokens: MutableList<TokenRange>,
) {
    val keywords = keywordsOf(language)
    var i = 0

    while (i < code.length) {
        if (marked[i] != TOKEN_NONE) {
            i++
            continue
        }

        val char = code[i]
        when {
            isIdentifierStart(char) -> {
                val start = i
                i++
                while (i < code.length && isIdentifierPart(code[i])) i++
                val word = code.substring(start, i)
                if (word in keywords) {
                    addTokenIfFree(start, i, TOKEN_KEYWORD, marked, tokens)
                } else {
                    val openParen = findNextNonWhitespace(code, i)
                    if (openParen in code.indices && code[openParen] == '(') {
                        addTokenIfFree(start, i, TOKEN_FUNCTION, marked, tokens)
                    }
                }
            }

            char.isDigit() -> {
                val start = i
                i++
                while (i < code.length && code[i].isDigit()) i++
                if (i < code.length && code[i] == '.' && i + 1 < code.length && code[i + 1].isDigit()) {
                    i++
                    while (i < code.length && code[i].isDigit()) i++
                }
                addTokenIfFree(start, i, TOKEN_NUMBER, marked, tokens)
            }

            else -> i++
        }
    }
}

private fun collectKeywordTokensOnly(
    code: String,
    language: SupportedLanguage,
    marked: IntArray,
    tokens: MutableList<TokenRange>,
) {
    val keywords = keywordsOf(language)
    var i = 0
    while (i < code.length) {
        if (marked[i] != TOKEN_NONE) {
            i++
            continue
        }

        if (!isIdentifierStart(code[i])) {
            i++
            continue
        }

        val start = i
        i++
        while (i < code.length && isIdentifierPart(code[i])) i++
        val word = code.substring(start, i)
        if (word in keywords) {
            addTokenIfFree(start, i, TOKEN_KEYWORD, marked, tokens)
        }
    }
}

private fun addTokenIfFree(
    start: Int,
    end: Int,
    type: Int,
    marked: IntArray,
    tokens: MutableList<TokenRange>,
) {
    if (start >= end) return
    for (index in start until end) {
        if (index !in marked.indices || marked[index] != TOKEN_NONE) {
            return
        }
    }
    for (index in start until end) {
        marked[index] = type
    }
    tokens += TokenRange(start = start, end = end, type = type)
}

private fun findLineEnd(code: String, start: Int): Int {
    var i = start
    while (i < code.length && code[i] != '\n') i++
    return i
}

private fun findBlockCommentEnd(code: String, start: Int): Int {
    val foundIndex = code.indexOf("*/", startIndex = start)
    return if (foundIndex == -1) code.length else foundIndex + 2
}

private fun findTripleQuotedEnd(code: String, start: Int, delimiter: String): Int {
    val from = start + delimiter.length
    val foundIndex = code.indexOf(delimiter, startIndex = from)
    return if (foundIndex == -1) code.length else foundIndex + delimiter.length
}

private fun findQuotedEnd(code: String, start: Int, quote: Char): Int {
    var i = start + 1
    while (i < code.length) {
        if (code[i] == '\\') {
            i += 2
            continue
        }
        if (code[i] == quote) return i + 1
        i++
    }
    return code.length
}

private fun findNextNonWhitespace(code: String, start: Int): Int {
    var i = start
    while (i < code.length && code[i].isWhitespace()) i++
    return i
}

private fun keywordsOf(language: SupportedLanguage): Set<String> {
    return when (language) {
        SupportedLanguage.PYTHON -> pythonKeywords
        SupportedLanguage.KOTLIN -> kotlinKeywords
        SupportedLanguage.BASH -> bashKeywords
    }
}

private fun isIdentifierStart(char: Char): Boolean = char == '_' || char.isLetter()

private fun isIdentifierPart(char: Char): Boolean = char == '_' || char.isLetterOrDigit()
