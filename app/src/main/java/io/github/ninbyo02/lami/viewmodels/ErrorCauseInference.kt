package io.github.ninbyo02.lami.viewmodels

import io.github.ninbyo02.lami.ui.screens.settings.ErrorCause

internal fun inferErrorCause(errorMessage: String?): ErrorCause? {
    if (errorMessage.isNullOrBlank()) {
        return ErrorCause.UNKNOWN
    }
    val normalizedMessage = errorMessage.lowercase()
    val heavyKeywords = listOf(
        "timeout",
        "timed out",
        "refused",
        "unreachable",
        "offline",
        "reset",
        "unresolved",
        "unknownhost",
        "connectexception",
        "socket",
    )
    return if (heavyKeywords.any { normalizedMessage.contains(it) }) {
        ErrorCause.HEAVY
    } else {
        ErrorCause.LIGHT
    }
}
