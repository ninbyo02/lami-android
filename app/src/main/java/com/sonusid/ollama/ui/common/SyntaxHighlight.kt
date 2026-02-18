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
    JSON,
    YAML,
    HTML,
    CSS,
    JAVASCRIPT,
    TYPESCRIPT,
    SQL,
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
    "true", "try", "typealias", "val", "var", "when", "while",
)

private val kotlinGradleDslKeywords = setOf(
    // Gradle Kotlin DSLの頻出語だけを最小追加して、build.gradle.kts/settings.gradle.ktsの可読性を改善する。
    "plugins", "repositories", "dependencies", "application",
    "pluginManagement", "dependencyResolutionManagement",
    "implementation", "api", "compileOnly", "runtimeOnly",
    "testImplementation", "androidTestImplementation", "testRuntimeOnly",
    "id", "version", "kotlin", "jvm",
    "mavenCentral", "google",
    "mainClass", "set"
)

private val gradleDslDetectionMarkers = setOf(
    "plugins",
    "dependencies",
    "repositories",
    "buildscript",
    "android",
    "kotlin",
    "subprojects",
    "allprojects",
    "pluginManagement",
    "dependencyResolutionManagement",
)

private val bashKeywords = setOf(
    "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "case", "esac",
    "function", "select", "until", "time", "coproc", "local", "readonly", "export", "return"
)

private val jsonKeywords = setOf("true", "false", "null")

private val yamlKeywords = setOf("true", "false", "null", "yes", "no", "on", "off")

private val javascriptKeywords = setOf(
    "function", "return", "const", "let", "var", "if", "else", "for", "while", "switch",
    "case", "break", "continue", "class", "extends", "import", "export", "from", "try", "catch",
    "finally", "throw", "new", "this", "super", "async", "await", "typeof", "instanceof", "in",
    "of", "void", "yield"
)

private val typescriptKeywords = javascriptKeywords + setOf(
    "type", "interface", "implements", "enum", "namespace", "readonly", "public", "private", "protected"
)

private val sqlKeywords = setOf(
    "select", "from", "where", "join", "left", "right", "inner", "outer", "on", "group", "by",
    "order", "having", "limit", "offset", "insert", "into", "values", "update", "set", "delete",
    "create", "table", "alter", "drop", "distinct", "as", "and", "or", "not", "null", "is", "in",
    "like", "between", "union", "all"
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
        function = colors.tertiary,
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
                TOKEN_NUMBER -> SpanStyle(color = palette.number, fontWeight = FontWeight.Medium)
                TOKEN_FUNCTION -> SpanStyle(color = palette.function, fontWeight = FontWeight.SemiBold)
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
        "json" -> SupportedLanguage.JSON
        "yaml", "yml" -> SupportedLanguage.YAML
        "html", "htm" -> SupportedLanguage.HTML
        "css" -> SupportedLanguage.CSS
        "javascript", "js" -> SupportedLanguage.JAVASCRIPT
        "typescript", "ts" -> SupportedLanguage.TYPESCRIPT
        "sql" -> SupportedLanguage.SQL
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

            (language == SupportedLanguage.JAVASCRIPT || language == SupportedLanguage.TYPESCRIPT) && code.startsWith("//", i) -> {
                val end = findLineEnd(code, i + 2)
                addTokenIfFree(i, end, TOKEN_COMMENT, marked, tokens)
                i = end
            }

            (language == SupportedLanguage.JAVASCRIPT || language == SupportedLanguage.TYPESCRIPT || language == SupportedLanguage.CSS) && code.startsWith("/*", i) -> {
                val end = findBlockCommentEnd(code, i + 2)
                addTokenIfFree(i, end, TOKEN_COMMENT, marked, tokens)
                i = end
            }

            language == SupportedLanguage.SQL && code.startsWith("--", i) -> {
                val end = findLineEnd(code, i + 2)
                addTokenIfFree(i, end, TOKEN_COMMENT, marked, tokens)
                i = end
            }

            language == SupportedLanguage.SQL && code.startsWith("/*", i) -> {
                val end = findBlockCommentEnd(code, i + 2)
                addTokenIfFree(i, end, TOKEN_COMMENT, marked, tokens)
                i = end
            }

            language == SupportedLanguage.HTML && code.startsWith("<!--", i) -> {
                val end = findDelimitedEnd(code, i + 4, "-->")
                addTokenIfFree(i, end, TOKEN_COMMENT, marked, tokens)
                i = end
            }

            (language == SupportedLanguage.PYTHON || language == SupportedLanguage.BASH || language == SupportedLanguage.YAML) && code[i] == '#' -> {
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

            (language == SupportedLanguage.JAVASCRIPT || language == SupportedLanguage.TYPESCRIPT) && code[i] == '`' -> {
                val end = findQuotedEnd(code, i, '`')
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
    val keywords = keywordsOf(language, code, marked)
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
                    if (supportsFunctionToken(language) && openParen in code.indices && code[openParen] == '(') {
                        addTokenIfFree(start, i, TOKEN_FUNCTION, marked, tokens)
                    }
                }
            }

            language == SupportedLanguage.KOTLIN && char.isDigit() -> {
                val start = i
                val end = findKotlinNumberLiteralEnd(code, start)
                if (end > start) {
                    addTokenIfFree(start, end, TOKEN_NUMBER, marked, tokens)
                    i = end
                } else {
                    i++
                }
            }

            char.isDigit() ||
                (char == '.' && i + 1 < code.length && code[i + 1].isDigit()) ||
                (
                    char == '-' &&
                        (
                            (i + 1 < code.length && code[i + 1].isDigit()) ||
                                (i + 2 < code.length && code[i + 1] == '.' && code[i + 2].isDigit())
                            ) &&
                        (
                            i == 0 ||
                                code[i - 1].isWhitespace() ||
                                code[i - 1] == '(' ||
                                code[i - 1] == '=' ||
                                code[i - 1] == ':' ||
                                code[i - 1] == ',' ||
                                code[i - 1] == '{' ||
                                code[i - 1] == '['
                            )
                    ) -> {
                val start = i
                if (code[i] == '-') {
                    i++
                }
                if (code[i] == '.') {
                    i++
                }
                while (i < code.length && code[i].isDigit()) i++
                if (start == i) {
                    i++
                    continue
                }
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

private fun findKotlinNumberLiteralEnd(code: String, start: Int): Int {
    if (start !in code.indices || !code[start].isDigit()) return start

    var i = start
    var isPrefixedBase = false
    var isHex = false

    fun readDigitsWithUnderscore(baseDigit: (Char) -> Boolean): Int {
        var index = i
        var digitCount = 0
        while (index < code.length) {
            val current = code[index]
            when {
                baseDigit(current) -> {
                    digitCount++
                    index++
                }

                current == '_' -> {
                    val prevIsDigit = index > i && baseDigit(code[index - 1])
                    val nextIsDigit = index + 1 < code.length && baseDigit(code[index + 1])
                    if (prevIsDigit && nextIsDigit) {
                        index++
                    } else {
                        break
                    }
                }

                else -> break
            }
        }
        return if (digitCount > 0) index else i
    }

    if (i + 1 < code.length && code[i] == '0') {
        when (code[i + 1]) {
            'x', 'X' -> {
                val hasHexDigit = i + 2 < code.length && (code[i + 2].isDigit() || code[i + 2] in 'a'..'f' || code[i + 2] in 'A'..'F')
                if (hasHexDigit) {
                    i += 2
                    isPrefixedBase = true
                    isHex = true
                    val end = readDigitsWithUnderscore { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                    if (end == i) return start
                    i = end
                }
            }

            'b', 'B' -> {
                val hasBinaryDigit = i + 2 < code.length && (code[i + 2] == '0' || code[i + 2] == '1')
                if (hasBinaryDigit) {
                    i += 2
                    isPrefixedBase = true
                    val end = readDigitsWithUnderscore { it == '0' || it == '1' }
                    if (end == i) return start
                    i = end
                }
            }

            'o', 'O' -> {
                val hasOctalDigit = i + 2 < code.length && code[i + 2] in '0'..'7'
                if (hasOctalDigit) {
                    i += 2
                    isPrefixedBase = true
                    val end = readDigitsWithUnderscore { it in '0'..'7' }
                    if (end == i) return start
                    i = end
                }
            }
        }
    }

    if (!isPrefixedBase) {
        i = readDigitsWithUnderscore { it.isDigit() }
        if (i == start) return start

        if (i < code.length && code[i] == '.' && i + 1 < code.length && code[i + 1].isDigit()) {
            i++
            val decimalEnd = readDigitsWithUnderscore { it.isDigit() }
            if (decimalEnd > i) {
                i = decimalEnd
            }
        }

        if (i < code.length && (code[i] == 'e' || code[i] == 'E')) {
            val expStart = i
            var j = i + 1
            if (j < code.length && (code[j] == '+' || code[j] == '-')) j++
            val digitStart = j
            i = j
            val expEnd = readDigitsWithUnderscore { it.isDigit() }
            i = if (expEnd > digitStart) expEnd else expStart
        }
    } else if (isHex) {
        if (i < code.length && code[i] == '.' && i + 1 < code.length && (code[i + 1].isDigit() || code[i + 1] in 'a'..'f' || code[i + 1] in 'A'..'F')) {
            i++
            val fractionalEnd = readDigitsWithUnderscore { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            if (fractionalEnd > i) {
                i = fractionalEnd
            }
        }

        if (i < code.length && (code[i] == 'p' || code[i] == 'P')) {
            val expStart = i
            var j = i + 1
            if (j < code.length && (code[j] == '+' || code[j] == '-')) j++
            val digitStart = j
            i = j
            val expEnd = readDigitsWithUnderscore { it.isDigit() }
            i = if (expEnd > digitStart) expEnd else expStart
        }
    }

    if (i < code.length && (code[i] == 'u' || code[i] == 'U')) {
        i++
    }
    if (i < code.length && (code[i] == 'l' || code[i] == 'L' || code[i] == 'f' || code[i] == 'F' || code[i] == 'd' || code[i] == 'D')) {
        i++
    }

    return i
}

private fun collectKeywordTokensOnly(
    code: String,
    language: SupportedLanguage,
    marked: IntArray,
    tokens: MutableList<TokenRange>,
) {
    val keywords = keywordsOf(language, code, marked)
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

private fun findDelimitedEnd(code: String, start: Int, delimiter: String): Int {
    val foundIndex = code.indexOf(delimiter, startIndex = start)
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

private fun keywordsOf(language: SupportedLanguage, code: String, marked: IntArray): Set<String> {
    return when (language) {
        SupportedLanguage.PYTHON -> pythonKeywords
        SupportedLanguage.KOTLIN -> {
            if (isGradleDslLikeKotlinCode(code, marked)) kotlinKeywords + kotlinGradleDslKeywords else kotlinKeywords
        }
        SupportedLanguage.BASH -> bashKeywords
        SupportedLanguage.JSON -> jsonKeywords
        SupportedLanguage.YAML -> yamlKeywords
        SupportedLanguage.HTML -> emptySet()
        SupportedLanguage.CSS -> emptySet()
        SupportedLanguage.JAVASCRIPT -> javascriptKeywords
        SupportedLanguage.TYPESCRIPT -> typescriptKeywords
        SupportedLanguage.SQL -> sqlKeywords
    }
}

private fun isGradleDslLikeKotlinCode(code: String, marked: IntArray): Boolean {
    val matchedMarkers = mutableSetOf<String>()
    // Kotlin通常コード内の誤検出を避けるため、トップレベル(深さ0)でのみ marker 判定する。
    // 文字列/コメント(marked)は brace 深さ更新・marker 判定のどちらからも除外する。
    var braceDepth = 0

    var i = 0
    while (i < code.length) {
        if (marked[i] == TOKEN_NONE) {
            when (code[i]) {
                '{' -> braceDepth++
                '}' -> braceDepth = (braceDepth - 1).coerceAtLeast(0)
            }
        }

        if (braceDepth == 0 && (i == 0 || code[i - 1] == '\n')) {
            var lineStart = i
            while (lineStart < code.length && (code[lineStart] == ' ' || code[lineStart] == '\t')) {
                lineStart++
            }

            // 文字列/コメント除外は collectCommentAndStringTokens が事前に marked へ反映済み。
            if (lineStart in marked.indices && marked[lineStart] == TOKEN_NONE) {
                for (marker in gradleDslDetectionMarkers) {
                    val markerEnd = lineStart + marker.length
                    if (markerEnd > code.length || !code.startsWith(marker, lineStart)) continue

                    var cursor = markerEnd
                    while (cursor < code.length && (code[cursor] == ' ' || code[cursor] == '\t')) {
                        cursor++
                    }

                    if (cursor < code.length && code[cursor] == '{') {
                        matchedMarkers += marker
                    }
                }

                if (matchedMarkers.size >= 2) {
                    return true
                }
            }
        }
        i++
    }

    if ("plugins" in matchedMarkers && containsUnmarkedGradlePluginCall(code, marked)) {
        return true
    }

    return false
}

private fun containsUnmarkedGradlePluginCall(code: String, marked: IntArray): Boolean {
    var i = 0
    while (i < code.length) {
        if (marked[i] != TOKEN_NONE) {
            i++
            continue
        }

        if (code.startsWith("id(", i) || code.startsWith("kotlin(", i)) {
            return true
        }
        i++
    }
    return false
}

private fun supportsFunctionToken(language: SupportedLanguage): Boolean {
    return when (language) {
        SupportedLanguage.PYTHON,
        SupportedLanguage.KOTLIN,
        SupportedLanguage.BASH,
        SupportedLanguage.JAVASCRIPT,
        SupportedLanguage.TYPESCRIPT,
        SupportedLanguage.SQL,
        -> true

        SupportedLanguage.JSON,
        SupportedLanguage.YAML,
        SupportedLanguage.HTML,
        SupportedLanguage.CSS,
        -> false
    }
}

private fun isIdentifierStart(char: Char): Boolean = char == '_' || char.isLetter()

private fun isIdentifierPart(char: Char): Boolean = char == '_' || char.isLetterOrDigit()
