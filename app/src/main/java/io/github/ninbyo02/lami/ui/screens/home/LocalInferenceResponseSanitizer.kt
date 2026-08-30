package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.Qairt244NpuOutputSanitizer

internal object LocalInferenceResponseSanitizer {
    private val unexpectedJapaneseInlineScripts = setOf(
        Character.UnicodeScript.ARABIC,
        Character.UnicodeScript.CYRILLIC,
        Character.UnicodeScript.HANGUL,
    )

    fun sanitize(
        rawOutput: String,
        prompt: String,
    ): Qairt244NpuOutputSanitizer.Result {
        val base = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = rawOutput,
            prompt = prompt,
        )
        val withoutInlineScriptContamination = stripUnexpectedJapaneseInlineScripts(
            value = base.sanitizedOutput,
            prompt = prompt,
        )
        val sanitizedOutput = stripLeadingRoleDelimiter(withoutInlineScriptContamination)
        return base.copy(
            sanitizedOutput = sanitizedOutput,
            sanitizerApplied = base.sanitizerApplied ||
                sanitizedOutput != base.sanitizedOutput,
        )
    }

    fun normalizeJapaneseInternalSpaces(value: String): String =
        Qairt244NpuOutputSanitizer.normalizeJapaneseInternalSpaces(value)

    private fun stripLeadingRoleDelimiter(value: String): String =
        value.replace(Regex("^\\s*[:：]+\\s*(?=[\\p{L}\\p{N}])"), "")

    private fun stripUnexpectedJapaneseInlineScripts(
        value: String,
        prompt: String,
    ): String {
        if (!containsJapanese(prompt) || !containsJapanese(value)) return value
        val sanitized = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val runStart = index
            val codePoint = value.codePointAt(index)
            if (Character.UnicodeScript.of(codePoint) !in unexpectedJapaneseInlineScripts) {
                sanitized.appendCodePoint(codePoint)
                index += Character.charCount(codePoint)
                continue
            }
            while (index < value.length) {
                val candidate = value.codePointAt(index)
                if (Character.UnicodeScript.of(candidate) !in unexpectedJapaneseInlineScripts) break
                index += Character.charCount(candidate)
            }
            val previous = previousNonWhitespaceCodePoint(value, runStart)
            val next = nextNonWhitespaceCodePoint(value, index)
            if (previous?.let(::isJapanese) != true && next?.let(::isJapanese) != true) {
                sanitized.append(value, runStart, index)
            }
        }
        return Qairt244NpuOutputSanitizer
            .normalizeJapaneseInternalSpaces(sanitized.toString())
            .trim()
    }

    private fun containsJapanese(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            if (isJapanese(codePoint)) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    private fun isJapanese(codePoint: Int): Boolean =
        when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            -> true
            else -> false
        }

    private fun previousNonWhitespaceCodePoint(value: String, fromIndex: Int): Int? {
        var index = fromIndex
        while (index > 0) {
            val codePoint = value.codePointBefore(index)
            if (!Character.isWhitespace(codePoint)) return codePoint
            index -= Character.charCount(codePoint)
        }
        return null
    }

    private fun nextNonWhitespaceCodePoint(value: String, fromIndex: Int): Int? {
        var index = fromIndex
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            if (!Character.isWhitespace(codePoint)) return codePoint
            index += Character.charCount(codePoint)
        }
        return null
    }
}
