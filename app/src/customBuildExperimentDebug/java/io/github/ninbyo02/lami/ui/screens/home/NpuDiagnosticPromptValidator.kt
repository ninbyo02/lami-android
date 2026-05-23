package io.github.ninbyo02.lami.ui.screens.home

object NpuDiagnosticPromptValidator {
    const val MAX_LENGTH = 32

    private val allowedPunctuation = setOf('.', ',', '?', '!', '\'', '-', '_')

    data class Result(
        val isValid: Boolean,
        val normalizedPrompt: String,
        val reasonCode: String,
        val message: String,
    )

    fun validate(input: String): Result {
        val normalized = input.trim()
        if (normalized.isEmpty()) {
            return invalid(normalized, "empty", "Prompt must contain at least one non-whitespace character.")
        }
        if (normalized.codePointCount(0, normalized.length) > MAX_LENGTH) {
            return invalid(normalized, "too_long", "Prompt must be $MAX_LENGTH characters or fewer.")
        }
        if (normalized.any { it == '\n' || it == '\r' }) {
            return invalid(normalized, "contains_newline", "Prompt must not contain newlines.")
        }
        if (normalized.any { it == '\t' }) {
            return invalid(normalized, "contains_tab", "Prompt must not contain tabs.")
        }
        if (normalized.any(Char::isISOControl)) {
            return invalid(normalized, "contains_control_char", "Prompt must not contain control characters.")
        }
        if (normalized.any { it.code > 0x7F }) {
            return invalid(normalized, "contains_non_ascii", "Prompt must contain ASCII characters only.")
        }
        if (normalized.any(::isDisallowedAscii)) {
            return invalid(
                normalized,
                "contains_disallowed_char",
                "Prompt contains a character outside the diagnostic allowlist.",
            )
        }

        return Result(
            isValid = true,
            normalizedPrompt = normalized,
            reasonCode = "ok",
            message = "OK",
        )
    }

    private fun invalid(
        normalizedPrompt: String,
        reasonCode: String,
        message: String,
    ): Result =
        Result(
            isValid = false,
            normalizedPrompt = normalizedPrompt,
            reasonCode = reasonCode,
            message = message,
        )

    private fun isDisallowedAscii(char: Char): Boolean =
        !(char.isLetterOrDigit() || char == ' ' || char in allowedPunctuation)
}
