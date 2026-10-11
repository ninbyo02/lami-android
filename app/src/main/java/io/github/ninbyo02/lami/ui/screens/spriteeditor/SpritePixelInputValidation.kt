package io.github.ninbyo02.lami.ui.screens.spriteeditor

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

private fun digitsOnly(input: String): String = input.filter { ch -> ch.isDigit() }

@VisibleForTesting
internal fun clampPxFieldValue(prev: TextFieldValue, next: TextFieldValue, max: Int): TextFieldValue {
    val maxDigits = max.coerceAtLeast(1).toString().length
    val sanitized = digitsOnly(next.text)
    val parsed = sanitized.toLongOrNull()
    val exceedsMax = parsed != null && parsed > max
    val exceedsDigits = sanitized.length > maxDigits
    val clamped = clampPxInput(next.text, max)
    val prevText = prev.text
    if ((exceedsDigits || exceedsMax) && clamped == prevText) {
        return TextFieldValue(
            text = prevText,
            selection = TextRange(prevText.length),
            composition = null,
        )
    }
    return TextFieldValue(
        text = clamped,
        selection = TextRange(clamped.length),
        composition = null,
    )
}

@VisibleForTesting
internal fun clampPxInput(raw: String, max: Int): String {
    val sanitized = digitsOnly(raw)
    if (sanitized.isEmpty()) {
        return ""
    }
    val maxDigits = max.coerceAtLeast(1).toString().length
    val parsed = sanitized.toLongOrNull()
    if (parsed == null) {
        return sanitized.take(maxDigits)
    }
    val clamped = parsed.coerceIn(1L, max.toLong()).toString()
    return if (clamped.length > maxDigits) clamped.take(maxDigits) else clamped
}

@VisibleForTesting
internal fun rejectPxFieldValueOverMaxDigits(
    prev: TextFieldValue,
    nextRaw: String,
    maxDigits: Int = 4,
): TextFieldValue {
    val sanitized = digitsOnly(nextRaw)
    if (sanitized.isEmpty()) {
        return TextFieldValue(
            text = "",
            selection = TextRange(0),
            composition = null,
        )
    }
    if (sanitized.length > maxDigits) {
        return prev
    }
    return TextFieldValue(
        text = sanitized,
        selection = TextRange(sanitized.length),
        composition = null,
    )
}
