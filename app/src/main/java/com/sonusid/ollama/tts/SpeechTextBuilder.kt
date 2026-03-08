package com.sonusid.ollama.tts

import java.util.Locale

class SpeechTextBuilder private constructor() {
    companion object {
        private const val EMPTY_FALLBACK = "詳しい内容は画面をご確認下さい"

        private val commandLanguages = setOf("bash", "sh", "shell", "zsh")
        private val configLanguages = setOf("json", "yaml", "yml", "toml", "xml")
        private val codeLanguages = setOf(
            "kotlin", "java", "python", "js", "javascript", "ts", "typescript",
            "c", "cpp", "csharp", "go", "rust", "swift", "php"
        )

        private val fencedCodeRegex = Regex("```\\s*([^\\n`]*)\\n?[\\s\\S]*?```")
        private val inlineCodeRegex = Regex("`([^`]+)`")
        private val rawUrlRegex = Regex("(https?://\\S+|www\\.\\S+)")
        private val markdownDecorationLineRegex = Regex("(?m)^\\s*([#=\\-*`_])\\1{2,}\\s*$")
        private val atxHeadingRegex = Regex("^(\\s{0,3})#{1,6}\\s+(.+?)\\s*$")
        private val repeatedSymbolRegex = Regex("([=\\-*#])\\1{2,}")
        private val multiBlankLineRegex = Regex("\\n{3,}")
        private val unorderedListMarkerRegex = Regex("^(\\s*)[•\\-*]\\s+")
        private val orderedListMarkerRegex = Regex("^(\\s*)\\d+\\.\\s+")
        private val boldAsteriskRegex = Regex("(?<!\\*)\\*\\*([^*\\n]+)\\*\\*(?!\\*)")
        private val boldUnderscoreRegex = Regex("(?<!\\w)__((?=[^\\n]*[^A-Za-z0-9_])[^_\\n]+)__(?!\\w)")
        private val italicAsteriskRegex = Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)")
        private val italicUnderscoreRegex = Regex("(?<!\\w)_((?=[^\\n]*[^A-Za-z0-9_])[^_\\n]+)_(?!\\w)")
        private val leadingDecorativeEmojis = listOf(
            "☑️", "✔️", "✅", "☑", "✔",
            "🧠", "💡", "📌", "📍",
            "🔹", "🔸", "⭐", "🌟",
            "🚀", "🎯", "📝", "📖",
            "🤖", "📣", "🎉", "⚠️", "⚠", "❗", "❓", "🔧", "🛠️", "🛠",
            "🧩", "📄", "📘", "📚", "🗂️", "🗂", "🧪", "🎲"
        )

        fun build(displayText: String): String {
            if (displayText.isBlank()) {
                return EMPTY_FALLBACK
            }

            val fencedConverted = fencedCodeRegex.replace(displayText) { match ->
                classifyFencedCodeLanguage(match.groupValues[1])
            }

            val inlineConverted = inlineCodeRegex.replace(fencedConverted) { match ->
                convertInlineCode(match.groupValues[1])
            }

            val urlConverted = rawUrlRegex.replace(inlineConverted, "リンクがあります")
            val lineBreakNormalized = urlConverted.replace("\r\n", "\n")
            val headingEmojiNormalized = removeLeadingDecorativeEmoji(lineBreakNormalized)
            val atxHeadingStripped = stripAtxHeadings(headingEmojiNormalized)
            val emphasisStripped = stripMarkdownEmphasis(atxHeadingStripped)

            val symbolReduced = emphasisStripped
                .replace(markdownDecorationLineRegex, "")
                .replace(repeatedSymbolRegex) { symbolMatch ->
                    symbolMatch.groupValues[1].repeat(2)
                }

            val blankCollapsed = symbolReduced
                .replace(multiBlankLineRegex, "\n\n")
            val listMarkerNormalized = normalizeListMarkers(blankCollapsed)
            val normalized = listMarkerNormalized
                .trim()

            return normalized.ifEmpty { EMPTY_FALLBACK }
        }

        private fun classifyFencedCodeLanguage(rawLanguage: String): String {
            val language = rawLanguage
                .trim()
                .lowercase(Locale.ROOT)
                .substringBefore(' ')

            return when {
                language in commandLanguages -> "実行コマンド例があります"
                language in configLanguages -> "設定例があります"
                language in codeLanguages -> "コード例があります"
                language.isBlank() -> "詳細なコード例があります"
                else -> "詳細なコード例があります"
            }
        }

        private fun convertInlineCode(rawInlineCode: String): String {
            val inlineCode = rawInlineCode.trim()
            if (inlineCode.isEmpty()) {
                return ""
            }
            if (inlineCode.contains('\n')) {
                return "コード"
            }
            if (rawUrlRegex.matches(inlineCode)) {
                return "リンク"
            }
            if (Regex("^[A-Za-z_][\\w.]*\\(\\)$").matches(inlineCode)) {
                return "関数呼び出し"
            }
            if (Regex("^[a-zA-Z]{2,}(?:\\s+[\\w./:-]+){1,3}$").matches(inlineCode)) {
                return "$inlineCode コマンド"
            }
            return if (inlineCode.length <= 20) inlineCode else "コード"
        }

        private fun removeLeadingDecorativeEmoji(text: String): String {
            return text
                .lineSequence()
                .joinToString("\n") { line ->
                    normalizeLeadingDecorativeEmoji(line)
                }
        }

        private fun stripMarkdownEmphasis(text: String): String {
            return text
                .lineSequence()
                .joinToString("\n") { line ->
                    line
                        .replace(boldAsteriskRegex, "$1")
                        .replace(boldUnderscoreRegex, "$1")
                        .replace(italicAsteriskRegex, "$1")
                        .replace(italicUnderscoreRegex, "$1")
                }
        }

        private fun stripAtxHeadings(text: String): String {
            return text
                .lineSequence()
                .joinToString("\n") { line ->
                    val match = atxHeadingRegex.matchEntire(line)
                    if (match != null) {
                        match.groupValues[1] + match.groupValues[2]
                    } else {
                        line
                    }
                }
        }


        private fun normalizeListMarkers(text: String): String {
            return text
                .lineSequence()
                .joinToString("\n") { line ->
                    line
                        .replace(unorderedListMarkerRegex, "$1")
                        .replace(orderedListMarkerRegex, "$1")
                }
        }

        private fun normalizeLeadingDecorativeEmoji(line: String): String {
            val leadingWhitespace = line.takeWhile { it == ' ' || it == '\t' }
            val content = line.removePrefix(leadingWhitespace)

            if (content.isEmpty()) {
                return line
            }

            var normalizedContent = content
            var decorativeEmojiRemoved = false

            while (true) {
                val matchedEmoji = leadingDecorativeEmojis.firstOrNull { emoji ->
                    normalizedContent.startsWith(emoji)
                } ?: break

                decorativeEmojiRemoved = true
                normalizedContent = normalizedContent
                    .removePrefix(matchedEmoji)
                    .dropWhile {
                        it == ' ' ||
                            it == '\t' ||
                            it == '\uFE0E' ||
                            it == '\uFE0F' ||
                            it == '\u200D'
                    }
            }

            if (!decorativeEmojiRemoved) {
                return line
            }

            return leadingWhitespace + normalizedContent
        }
    }
}
