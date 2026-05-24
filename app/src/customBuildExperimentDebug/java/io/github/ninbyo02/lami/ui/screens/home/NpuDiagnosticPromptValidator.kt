package io.github.ninbyo02.lami.ui.screens.home

object NpuDiagnosticPromptValidator {
    const val MAX_LENGTH = 32
    const val ASCII_DIAGNOSTIC_MODE = "ascii_diagnostic"
    const val UTF8_INTERNAL_INTENT_MODE = "utf8_internal_intent"

    private val allowedPunctuation = setOf('.', ',', '?', '!', '\'', '-', '_')

    data class Result(
        val isValid: Boolean,
        val normalizedPrompt: String,
        val reasonCode: String,
        val message: String,
        val promptValidationMode: String,
    )

    fun validate(input: String): Result = validateAsciiDiagnostic(input)

    fun validateAsciiDiagnostic(input: String): Result {
        val normalized = input.trim()
        validateCommon(normalized, ASCII_DIAGNOSTIC_MODE)?.let { return it }
        if (normalized.any { it.code > 0x7F }) {
            return invalid(normalized, "contains_non_ascii", "Prompt must contain ASCII characters only.", ASCII_DIAGNOSTIC_MODE)
        }
        if (normalized.any(::isDisallowedAscii)) {
            return invalid(
                normalized,
                "contains_disallowed_char",
                "Prompt contains a character outside the diagnostic allowlist.",
                ASCII_DIAGNOSTIC_MODE,
            )
        }

        return valid(normalized, ASCII_DIAGNOSTIC_MODE)
    }

    fun validateUtf8InternalIntent(input: String): Result {
        val normalized = input.trim()
        validateCommon(normalized, UTF8_INTERNAL_INTENT_MODE)?.let { return it }
        if (normalized.any(Char::isSurrogate)) {
            return invalid(normalized, "invalid_utf8", "Prompt must be valid UTF-8 text.", UTF8_INTERNAL_INTENT_MODE)
        }
        return valid(normalized, UTF8_INTERNAL_INTENT_MODE)
    }

    private fun validateCommon(normalized: String, mode: String): Result? = when {
        normalized.isEmpty() -> invalid(normalized, "empty", "Prompt must contain at least one non-whitespace character.", mode)
        normalized.codePointCount(0, normalized.length) > MAX_LENGTH -> invalid(normalized, "too_long", "Prompt must be $MAX_LENGTH characters or fewer.", mode)
        normalized.any { it == '\n' || it == '\r' } -> invalid(normalized, "contains_newline", "Prompt must not contain newlines.", mode)
        normalized.any { it == '\t' } -> invalid(normalized, "contains_tab", "Prompt must not contain tabs.", mode)
        normalized.any { it == '\u0000' } -> invalid(normalized, "contains_nul", "Prompt must not contain NUL characters.", mode)
        normalized.any(Char::isISOControl) -> invalid(normalized, "contains_control_char", "Prompt must not contain control characters.", mode)
        else -> null
    }

    private fun valid(normalizedPrompt: String, mode: String): Result =
        Result(
            isValid = true,
            normalizedPrompt = normalizedPrompt,
            reasonCode = "ok",
            message = "OK",
            promptValidationMode = mode,
        )

    private fun invalid(
        normalizedPrompt: String,
        reasonCode: String,
        message: String,
        mode: String,
    ): Result =
        Result(
            isValid = false,
            normalizedPrompt = normalizedPrompt,
            reasonCode = reasonCode,
            message = message,
            promptValidationMode = mode,
        )

    private fun isDisallowedAscii(char: Char): Boolean =
        !(char.isLetterOrDigit() || char == ' ' || char in allowedPunctuation)
}
