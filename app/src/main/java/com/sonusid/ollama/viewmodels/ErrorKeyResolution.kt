package com.sonusid.ollama.viewmodels

import com.sonusid.ollama.ui.screens.settings.ErrorCause

private fun normalizeErrorKey(key: String?): String =
    when (key) {
        "ErrorHeavy" -> "ErrorHeavy"
        else -> "ErrorLight"
    }

internal fun recommendedErrorKey(cause: ErrorCause?): String =
    when (cause) {
        ErrorCause.HEAVY -> "ErrorHeavy"
        else -> "ErrorLight"
    }

internal fun resolveErrorKey(storedSelectedKey: String?, cause: ErrorCause?): String {
    val normalizedStored = storedSelectedKey
        ?.takeIf { it.isNotBlank() }
        ?.let { normalizeErrorKey(it) }
    val resolved = normalizedStored ?: recommendedErrorKey(cause)
    return resolved.ifBlank { "ErrorLight" }
}
