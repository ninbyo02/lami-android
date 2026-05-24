package io.github.ninbyo02.lami.ui.screens.home

object NpuDiagnosticPromptValidator {
    const val MAX_LENGTH = 32
    const val HIDDEN_TEMPLATE_MAX_LENGTH = 128
    const val ASCII_DIAGNOSTIC_MODE = "ascii_diagnostic"
    const val UTF8_INTERNAL_INTENT_MODE = "utf8_internal_intent"
    const val UTF8_HIDDEN_EXPERIMENTAL_MODE = "utf8_hidden_experimental"
    const val UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE = "utf8_hidden_template_experiment"
    const val DEFAULT_INPUT_LIMIT_MODE = "short_prompt_guard"
    const val HIDDEN_TEMPLATE_INPUT_LIMIT_MODE = "hidden_template_experiment"

    private val allowedPunctuation = setOf('.', ',', '?', '!', '\'', '-', '_')

    data class Result(
        val isValid: Boolean,
        val normalizedPrompt: String,
        val reasonCode: String,
        val message: String,
        val promptValidationMode: String,
        val promptInputCodePoints: Int,
        val promptInputCodePointLimit: Int,
        val promptInputLimitMode: String,
    )

    fun validate(input: String): Result = validateAsciiDiagnostic(input)

    fun validateAsciiDiagnostic(input: String): Result {
        val normalized = input.trim()
        validateCommon(
            normalized = normalized,
            mode = ASCII_DIAGNOSTIC_MODE,
            maxCodePoints = MAX_LENGTH,
            inputLimitMode = DEFAULT_INPUT_LIMIT_MODE,
            allowLineFeed = false,
        )?.let { return it }
        if (normalized.any { it.code > 0x7F }) {
            return invalid(
                normalized,
                "contains_non_ascii",
                "Prompt must contain ASCII characters only.",
                ASCII_DIAGNOSTIC_MODE,
                MAX_LENGTH,
                DEFAULT_INPUT_LIMIT_MODE,
            )
        }
        if (normalized.any(::isDisallowedAscii)) {
            return invalid(
                normalized,
                "contains_disallowed_char",
                "Prompt contains a character outside the diagnostic allowlist.",
                ASCII_DIAGNOSTIC_MODE,
                MAX_LENGTH,
                DEFAULT_INPUT_LIMIT_MODE,
            )
        }

        return valid(normalized, ASCII_DIAGNOSTIC_MODE, MAX_LENGTH, DEFAULT_INPUT_LIMIT_MODE)
    }

    fun validateUtf8InternalIntent(input: String): Result {
        val normalized = input.trim()
        validateCommon(
            normalized = normalized,
            mode = UTF8_INTERNAL_INTENT_MODE,
            maxCodePoints = MAX_LENGTH,
            inputLimitMode = DEFAULT_INPUT_LIMIT_MODE,
            allowLineFeed = false,
        )?.let { return it }
        if (normalized.any(Char::isSurrogate)) {
            return invalid(
                normalized,
                "invalid_utf8",
                "Prompt must be valid UTF-8 text.",
                UTF8_INTERNAL_INTENT_MODE,
                MAX_LENGTH,
                DEFAULT_INPUT_LIMIT_MODE,
            )
        }
        return valid(normalized, UTF8_INTERNAL_INTENT_MODE, MAX_LENGTH, DEFAULT_INPUT_LIMIT_MODE)
    }

    fun validateUtf8HiddenExperimental(input: String): Result {
        val normalized = input.trim()
        validateCommon(
            normalized = normalized,
            mode = UTF8_HIDDEN_EXPERIMENTAL_MODE,
            maxCodePoints = MAX_LENGTH,
            inputLimitMode = DEFAULT_INPUT_LIMIT_MODE,
            allowLineFeed = false,
        )?.let { return it }
        if (normalized.any(Char::isSurrogate)) {
            return invalid(
                normalized,
                "invalid_utf8",
                "Prompt must be valid UTF-8 text.",
                UTF8_HIDDEN_EXPERIMENTAL_MODE,
                MAX_LENGTH,
                DEFAULT_INPUT_LIMIT_MODE,
            )
        }
        return valid(normalized, UTF8_HIDDEN_EXPERIMENTAL_MODE, MAX_LENGTH, DEFAULT_INPUT_LIMIT_MODE)
    }

    fun validateUtf8HiddenTemplateExperiment(input: String): Result {
        val normalized = input.trim()
        validateCommon(
            normalized = normalized,
            mode = UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
            maxCodePoints = HIDDEN_TEMPLATE_MAX_LENGTH,
            inputLimitMode = HIDDEN_TEMPLATE_INPUT_LIMIT_MODE,
            allowLineFeed = true,
        )?.let { return it }
        if (normalized.any(Char::isSurrogate)) {
            return invalid(
                normalized,
                "invalid_utf8",
                "Prompt must be valid UTF-8 text.",
                UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
                HIDDEN_TEMPLATE_MAX_LENGTH,
                HIDDEN_TEMPLATE_INPUT_LIMIT_MODE,
            )
        }
        return valid(
            normalized,
            UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
            HIDDEN_TEMPLATE_MAX_LENGTH,
            HIDDEN_TEMPLATE_INPUT_LIMIT_MODE,
        )
    }

    private fun validateCommon(
        normalized: String,
        mode: String,
        maxCodePoints: Int,
        inputLimitMode: String,
        allowLineFeed: Boolean,
    ): Result? = when {
        normalized.isEmpty() -> invalid(normalized, "empty", "Prompt must contain at least one non-whitespace character.", mode, maxCodePoints, inputLimitMode)
        normalized.codePointCount(0, normalized.length) > maxCodePoints -> invalid(normalized, "too_long", "Prompt must be $maxCodePoints characters or fewer.", mode, maxCodePoints, inputLimitMode)
        normalized.any { it == '\n' && !allowLineFeed } -> invalid(normalized, "contains_newline", "Prompt must not contain newlines.", mode, maxCodePoints, inputLimitMode)
        normalized.any { it == '\r' } -> invalid(normalized, "contains_newline", "Prompt must not contain carriage returns.", mode, maxCodePoints, inputLimitMode)
        normalized.any { it == '\t' } -> invalid(normalized, "contains_tab", "Prompt must not contain tabs.", mode, maxCodePoints, inputLimitMode)
        normalized.any { it == '\u0000' } -> invalid(normalized, "contains_nul", "Prompt must not contain NUL characters.", mode, maxCodePoints, inputLimitMode)
        normalized.any { it.isISOControl() && !(allowLineFeed && it == '\n') } -> invalid(normalized, "contains_control_char", "Prompt must not contain control characters.", mode, maxCodePoints, inputLimitMode)
        else -> null
    }

    private fun valid(
        normalizedPrompt: String,
        mode: String,
        maxCodePoints: Int,
        inputLimitMode: String,
    ): Result =
        Result(
            isValid = true,
            normalizedPrompt = normalizedPrompt,
            reasonCode = "ok",
            message = "OK",
            promptValidationMode = mode,
            promptInputCodePoints = normalizedPrompt.codePointCount(0, normalizedPrompt.length),
            promptInputCodePointLimit = maxCodePoints,
            promptInputLimitMode = inputLimitMode,
        )

    private fun invalid(
        normalizedPrompt: String,
        reasonCode: String,
        message: String,
        mode: String,
        maxCodePoints: Int,
        inputLimitMode: String,
    ): Result =
        Result(
            isValid = false,
            normalizedPrompt = normalizedPrompt,
            reasonCode = reasonCode,
            message = message,
            promptValidationMode = mode,
            promptInputCodePoints = normalizedPrompt.codePointCount(0, normalizedPrompt.length),
            promptInputCodePointLimit = maxCodePoints,
            promptInputLimitMode = inputLimitMode,
        )

    private fun isDisallowedAscii(char: Char): Boolean =
        !(char.isLetterOrDigit() || char == ' ' || char in allowedPunctuation)
}
