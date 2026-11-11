package io.github.ninbyo02.lami.tts

import java.text.BreakIterator
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Text and audio tokens share Qwen's fixed main cache. Never spend it all on prefill. */
internal object LamiQwenTextChunks {
    const val MAX_CODE_POINTS = 32
    private const val MINIMUM_AUDIO_FRAMES = 64
    private const val AUDIO_FRAMES_PER_CODE_POINT = 4
    private const val EOS_MARGIN_FRAMES = 16

    fun audioReserve(text: String): Int = maxOf(MINIMUM_AUDIO_FRAMES,
        text.codePointCount(0, text.length) * AUDIO_FRAMES_PER_CODE_POINT + EOS_MARGIN_FRAMES)

    /**
     * Only short candidates reach the real tokenizer, whose own input limit is
     * 4096 UTF-16 units. Each successful piece is measured independently: BPE
     * token counts need not be monotonic as a candidate becomes longer.
     * Whitespace remains in the plan so concatenation exactly restores input.
     */
    suspend fun split(text: String, capacity: Int, prefillSize: (String) -> Int): List<String> {
        require(capacity >= MINIMUM_AUDIO_FRAMES + 16) { "Qwen cache is too small for speech" }
        require(!text.contains("<|")) { "Special-token syntax is not spoken text" }
        if (text.isEmpty()) return emptyList()
        val boundaries = characterBoundaries(text)
        val chunks = mutableListOf<String>()
        var startIndex = 0
        while (startIndex < boundaries.lastIndex) {
            currentCoroutineContext().ensureActive()
            val start = boundaries[startIndex]
            val fitting = mutableListOf<Int>()
            var next = startIndex + 1
            while (next <= boundaries.lastIndex &&
                text.codePointCount(start, boundaries[next]) <= MAX_CODE_POINTS) {
                currentCoroutineContext().ensureActive()
                val candidate = text.substring(start, boundaries[next])
                if (candidate.isBlank() || prefillSize(candidate) + audioReserve(candidate) <= capacity) fitting += next
                next++
            }
            require(fitting.isNotEmpty()) { "A Qwen speech character does not fit the text/audio cache budget" }
            val furthest = fitting.last()
            val endIndex = if (boundaries[furthest] == text.length) furthest else {
                fitting.lastOrNull { boundaryPriority(text, start, boundaries[it]) == 2 }
                    ?: fitting.lastOrNull { boundaryPriority(text, start, boundaries[it]) == 1 }
                    ?: furthest
            }
            chunks += text.substring(start, boundaries[endIndex])
            startIndex = endIndex
        }
        return chunks
    }

    private fun boundaryPriority(text: String, start: Int, end: Int): Int {
        var cursor = end
        val whitespaceBoundary = Character.isWhitespace(text.codePointBefore(end))
        while (cursor > start) {
            val point = text.codePointBefore(cursor)
            if (point == '\n'.code || point == '\r'.code) return 2
            if (Character.isWhitespace(point) || point in listOf('」'.code, '』'.code, '）'.code, ')'.code, '”'.code, '"'.code, '】'.code)) {
                cursor -= Character.charCount(point)
                continue
            }
            return when (point) {
                '。'.code, '！'.code, '？'.code, '.'.code, '!'.code, '?'.code, '\n'.code -> 2
                '、'.code, '，'.code, ','.code, '；'.code, ';'.code, '：'.code, ':'.code -> 1
                else -> if (whitespaceBoundary) 1 else 0
            }
        }
        return if (whitespaceBoundary) 1 else 0
    }

    private fun characterBoundaries(text: String): List<Int> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val result = mutableListOf(0)
        var boundary = iterator.next()
        while (boundary != BreakIterator.DONE) {
            if (boundary == text.length || safeBoundary(text, boundary)) result += boundary
            boundary = iterator.next()
        }
        return result
    }

    /** Join emoji/combining sequences even on JVMs with an older BreakIterator. */
    private fun safeBoundary(text: String, boundary: Int): Boolean {
        if (Character.isHighSurrogate(text[boundary - 1]) && Character.isLowSurrogate(text[boundary])) return false
        val previous = text.codePointBefore(boundary)
        val next = text.codePointAt(boundary)
        if (previous == 0x200D || next == 0x200D || previous == '\r'.code && next == '\n'.code) return false
        if (next in 0xFE00..0xFE0F || next in 0xE0100..0xE01EF || next in 0x1F3FB..0x1F3FF) return false
        if (Character.getType(next) in setOf(Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt())) return false
        if (previous in 0x1F1E6..0x1F1FF && next in 0x1F1E6..0x1F1FF) {
            var regionalIndicators = 0
            var cursor = boundary
            while (cursor > 0 && text.codePointBefore(cursor) in 0x1F1E6..0x1F1FF) {
                cursor -= Character.charCount(text.codePointBefore(cursor))
                regionalIndicators++
            }
            if (regionalIndicators % 2 != 0) return false
        }
        return true
    }
}
