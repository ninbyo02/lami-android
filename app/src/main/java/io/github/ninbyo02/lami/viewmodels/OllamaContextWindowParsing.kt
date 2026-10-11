package io.github.ninbyo02.lami.viewmodels

import androidx.annotation.VisibleForTesting
import org.json.JSONObject

private val NUM_CTX_PATTERN = Regex("""(^|[\s\\n])num_ctx\s+(\d+)""", RegexOption.MULTILINE)
private val OPTIONS_NUM_CTX_PATTERN = Regex(""""options"\s*:\s*\{[^}]*"num_ctx"\s*:\s*(\d+)""")
private val JSON_PARAMETERS_PATTERN = Regex(""""parameters"\s*:\s*"((?:\\.|[^"\\])*)"""", RegexOption.DOT_MATCHES_ALL)
private val MODEL_INFO_CONTEXT_PATTERN = Regex(""""model_info"\s*:\s*\{[^}]*"[^"]*context_length"\s*:\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)
private val CONTEXT_WINDOW_PATTERN = Regex(""""context_window"\s*:\s*(\d+)""")
private val ROOT_CONTEXT_LENGTH_PATTERN = Regex(""""context_length"\s*:\s*(\d+)""")
private val DETAILS_CONTEXT_LENGTH_PATTERN = Regex(""""details"\s*:\s*\{[^}]*"context_length"\s*:\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)

internal fun JSONObject.optNullableIntCompat(name: String): Int? =
    if (has(name) && !isNull(name)) runCatching { getInt(name) }.getOrNull() else null

@VisibleForTesting
internal fun extractEffectiveContextWindowFromShowResponse(response: String): Int? {
    extractFirstPositiveInt(OPTIONS_NUM_CTX_PATTERN, response)?.let { return it }

    JSON_PARAMETERS_PATTERN.find(response)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::unescapeJsonStringForContextWindow)
        ?.let { parameters ->
            NUM_CTX_PATTERN.find(parameters)
                ?.groupValues
                ?.getOrNull(2)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let { return it }
        }

    extractFirstPositiveInt(MODEL_INFO_CONTEXT_PATTERN, response)?.let { return it }
    extractFirstPositiveInt(CONTEXT_WINDOW_PATTERN, response)?.let { return it }
    extractFirstPositiveInt(ROOT_CONTEXT_LENGTH_PATTERN, response)?.let { return it }
    extractFirstPositiveInt(DETAILS_CONTEXT_LENGTH_PATTERN, response)?.let { return it }

    return null
}

internal fun extractFirstPositiveInt(pattern: Regex, text: String): Int? {
    return pattern.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
}

internal fun unescapeJsonStringForContextWindow(value: String): String {
    return value
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
