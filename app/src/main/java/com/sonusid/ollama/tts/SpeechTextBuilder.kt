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
        private val repeatedSymbolRegex = Regex("([=\\-*#])\\1{2,}")
        private val multiBlankLineRegex = Regex("\\n{3,}")

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

            val symbolReduced = urlConverted
                .replace(markdownDecorationLineRegex, "")
                .replace(repeatedSymbolRegex) { symbolMatch ->
                    symbolMatch.groupValues[1].repeat(2)
                }

            val normalized = symbolReduced
                .replace("\r\n", "\n")
                .replace(multiBlankLineRegex, "\n\n")
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
    }
}
