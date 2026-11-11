package io.github.ninbyo02.lami.tts

/** Bounds the UTF-16 input length required by Android TTS without losing text or splitting a code point. */
internal fun splitAndroidTtsText(text: String, maximumLength: Int): List<String> {
    require(maximumLength >= 2) { "Android TTS chunks must fit a surrogate pair" }
    if (text.isEmpty()) return emptyList()
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = start + minOf(maximumLength, text.length - start)
        if (end < text.length) {
            val minimumBoundary = start + maximumLength / 2
            // Prefer a nearby sentence boundary, then whitespace. Keep the separator
            // in the chunk so joining all chunks exactly reconstructs the input.
            val sentenceEnd = (end - 1 downTo minimumBoundary).firstOrNull {
                text[it] in "。！？.!?\n"
            }
            val whitespaceEnd = if (sentenceEnd == null) (end - 1 downTo minimumBoundary).firstOrNull {
                text[it].isWhitespace()
            } else null
            end = sentenceEnd?.plus(1) ?: whitespaceEnd?.plus(1) ?: end
            if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
        }
        chunks += text.substring(start, end)
        start = end
    }
    return chunks
}
