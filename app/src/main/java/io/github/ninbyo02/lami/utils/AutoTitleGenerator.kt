package io.github.ninbyo02.lami.utils

import java.time.LocalDate

private const val MAX_TITLE_CODE_POINTS = 24
private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+")
private val DIGIT_SEQUENCE_REGEX = Regex("\\d{3,}")

object AutoTitleGenerator {
    fun generateTitle(sourceMessage: String?, today: LocalDate = LocalDate.now()): String {
        val normalized = sourceMessage
            .orEmpty()
            .trimStart { it.isWhitespace() || it == '>' || it == '"' || it == '\'' }
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim()

        val masked = maskSensitiveText(normalized)
        if (masked.isBlank() || isMeaningless(masked)) {
            return today.toString()
        }
        return truncateByCodePoint(masked, MAX_TITLE_CODE_POINTS)
    }

    private fun maskSensitiveText(value: String): String {
        return value
            .replace(EMAIL_REGEX, "***@***")
            .replace(DIGIT_SEQUENCE_REGEX, "***")
    }

    private fun isMeaningless(value: String): Boolean {
        val compact = value.filterNot(Char::isWhitespace)
        if (compact.isEmpty()) return true
        return compact.toSet().size == 1
    }

    private fun truncateByCodePoint(value: String, maxCodePoints: Int): String {
        val codePointCount = value.codePointCount(0, value.length)
        if (codePointCount <= maxCodePoints) {
            return value
        }
        val endIndex = value.offsetByCodePoints(0, maxCodePoints)
        return value.substring(0, endIndex).trimEnd() + "…"
    }
}
