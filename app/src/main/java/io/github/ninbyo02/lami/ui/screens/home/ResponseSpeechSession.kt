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
        // A shorter replacement must never rewind and replay already consumed text.
        if (fullText.length <= consumed) return null
        val remaining = fullText.substring(consumed)
        val length = if (final) remaining.length else findStreamingTtsBreakIndex(remaining) + 1
        if (length <= 0) return null
        consumed += length
        return remaining.take(length)
    }
}
