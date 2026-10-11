package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.tts.SpeechTextBuilder

private val protectedAssistantCode = Regex(
    "```[\\s\\S]*?(?:```|\\z)|~~~[\\s\\S]*?(?:~~~|\\z)|`[^`\\r\\n]*(?:`|\\z)",
)

internal fun normalizeAssistantCounterNumbers(text: String): String = buildString {
    var cursor = 0
    // Keep code examples intact, including incomplete fences during streaming.
    protectedAssistantCode.findAll(text).forEach { code ->
        append(SpeechTextBuilder.normalizeCounterNumbers(text.substring(cursor, code.range.first)))
        append(code.value)
        cursor = code.range.last + 1
    }
    append(SpeechTextBuilder.normalizeCounterNumbers(text.substring(cursor)))
}
