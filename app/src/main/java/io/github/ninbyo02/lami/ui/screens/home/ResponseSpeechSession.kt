package io.github.ninbyo02.lami.ui.screens.home

/** One automatic speech owner per request, including its final unsaid tail. Main-thread confined. */
internal class ResponseSpeechSession {
    var generation: Long = 0L
        private set
    private var active = false
    private var completed = false
    private var consumed = 0

    fun begin() {
        generation += 1
        active = true
        completed = false
        consumed = 0
    }

    fun stop() {
        generation += 1
        active = false
    }

    fun accepts(token: Long = generation): Boolean = active && !completed && token == generation

    fun take(fullText: String, final: Boolean): String? {
        if (!accepts()) return null
        if (final) completed = true
        // Code can arrive while the LLM is still producing a response. Project
        // cumulative input onto prose without retiring its speech owner: later
        // explanation must remain eligible after the closing fence arrives.
        // Completion paths trim their result; streaming must use the same origin.
        val speechText = responseSpeechProse(fullText.trimStart(), final)
        // A shorter replacement must never rewind and replay already consumed text.
        if (speechText.length <= consumed) return null
        val remaining = speechText.substring(consumed)
        val length = if (final) remaining.length else findStreamingTtsBreakIndex(remaining) + 1
        if (length <= 0) return null
        consumed += length
        return remaining.take(length)
    }
}

/** Holds incomplete fence markers so a marker split across LLM updates is never spoken. */
private fun responseSpeechProse(text: String, final: Boolean): String {
    val prose = StringBuilder()
    var fenceCharacter: Char? = null
    var fenceLength = 0
    var offset = 0
    while (offset < text.length) {
        val newline = text.indexOf('\n', offset)
        val end = if (newline < 0) text.length else newline
        val line = text.substring(offset, end)
        val content = line.trimStart(' ', '\t').trimEnd('\r')
        val marker = content.firstOrNull()?.takeIf { it == '`' || it == '~' }
        val markerLength = if (marker == null) 0 else content.takeWhile { it == marker }.length
        if (fenceCharacter != null) {
            if (marker == fenceCharacter && markerLength >= fenceLength && content.drop(markerLength).isBlank()) {
                fenceCharacter = null
            }
        } else if (markerLength >= 3) {
            fenceCharacter = marker
            fenceLength = markerLength
        } else if (!(newline < 0 && !final && markerLength > 0 && markerLength == content.length)) {
            prose.append(line)
            if (newline >= 0) prose.append('\n')
        }
        if (newline < 0) break
        offset = newline + 1
    }
    return prose.toString()
}
