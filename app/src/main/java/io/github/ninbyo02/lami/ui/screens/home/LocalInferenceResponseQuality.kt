package io.github.ninbyo02.lami.ui.screens.home

import java.util.Locale

private enum class LocalUnexpectedScript(
    val unicodeScript: Character.UnicodeScript,
) {
    ARABIC(Character.UnicodeScript.ARABIC),
    HANGUL(Character.UnicodeScript.HANGUL),
}

private data class MarkdownFence(
    val marker: Char,
    val length: Int,
)

private const val LOCAL_DEGENERATE_REPETITION_MIN_SPAN_CODE_POINTS = 12
private const val LOCAL_DEGENERATE_REPETITION_MIN_COUNT = 4
private const val LOCAL_DEGENERATE_REPETITION_MAX_UNIT_CODE_POINTS = 12

internal fun localInferenceResponseRejectionReason(
    userPrompt: String,
    response: String?,
): String? {
    val finalResponse = response?.trim().orEmpty()
    if (finalResponse.isBlank()) return "blank_response"

    val prose = proseOutsideClosedMarkdownCode(finalResponse)
    if (hasDegenerateConsecutiveRepetition(prose)) return "degenerate_repetition"
    if (!containsCjkLetter(userPrompt) && !containsCjkLetter(prose)) return null

    val unexpected = LocalUnexpectedScript.entries.filter { target ->
        hasUnexpectedScriptJoinedToCjk(prose, target.unicodeScript)
    }
    if (unexpected.isEmpty()) return null
    return "unexpected_script:${unexpected.joinToString(",") { it.name }}"
}

internal fun acceptedLocalInferenceResponse(
    userPrompt: String,
    successfulBackend: String?,
    response: String?,
): String? {
    successfulBackend
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { it == "GPU" || it == "CPU" }
        ?: return null
    val finalResponse = response?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return finalResponse.takeIf {
        localInferenceResponseRejectionReason(userPrompt, finalResponse) == null
    }
}

private fun hasDegenerateConsecutiveRepetition(text: String): Boolean {
    val points = codePoints(text).toList()
    if (points.size < LOCAL_DEGENERATE_REPETITION_MIN_SPAN_CODE_POINTS) return false

    for (start in points.indices) {
        val remaining = points.size - start
        val maxUnitLength = minOf(
            LOCAL_DEGENERATE_REPETITION_MAX_UNIT_CODE_POINTS,
            remaining / LOCAL_DEGENERATE_REPETITION_MIN_COUNT,
        )
        for (unitLength in 1..maxUnitLength) {
            if (!containsLetter(points, start, unitLength)) continue
            val minimumRepeatCount = maxOf(
                LOCAL_DEGENERATE_REPETITION_MIN_COUNT,
                (LOCAL_DEGENERATE_REPETITION_MIN_SPAN_CODE_POINTS + unitLength - 1) / unitLength,
            )
            var repeatCount = 1
            while (
                start + (repeatCount + 1) * unitLength <= points.size &&
                codePointRegionsEqual(
                    points = points,
                    firstStart = start,
                    secondStart = start + repeatCount * unitLength,
                    length = unitLength,
                )
            ) {
                repeatCount += 1
            }
            if (repeatCount >= minimumRepeatCount) return true
        }
    }
    return false
}

private fun containsLetter(
    points: List<Int>,
    start: Int,
    length: Int,
): Boolean = (start until start + length).any { index ->
    Character.isLetter(points[index])
}

private fun codePointRegionsEqual(
    points: List<Int>,
    firstStart: Int,
    secondStart: Int,
    length: Int,
): Boolean = (0 until length).all { offset ->
    points[firstStart + offset] == points[secondStart + offset]
}

private fun containsCjkLetter(text: String): Boolean =
    codePoints(text).any(::isCjkLetter)

private fun isCjkLetter(codePoint: Int): Boolean =
    when (Character.UnicodeScript.of(codePoint)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        -> true
        else -> false
    }

private fun hasUnexpectedScriptJoinedToCjk(
    text: String,
    unexpectedScript: Character.UnicodeScript,
): Boolean {
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val codePointLength = Character.charCount(codePoint)
        if (Character.UnicodeScript.of(codePoint) == unexpectedScript) {
            val previousIsCjk = index > 0 && isCjkLetter(text.codePointBefore(index))
            val nextIndex = index + codePointLength
            val nextIsCjk = nextIndex < text.length && isCjkLetter(text.codePointAt(nextIndex))
            if (previousIsCjk || nextIsCjk) return true
        }
        index += codePointLength
    }
    return false
}

private fun codePoints(text: String): Sequence<Int> = sequence {
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        yield(codePoint)
        index += Character.charCount(codePoint)
    }
}

private fun proseOutsideClosedMarkdownCode(markdown: String): String {
    val lines = markdown.split('\n')
    val withoutFencedCode = buildString {
        var lineIndex = 0
        while (lineIndex < lines.size) {
            val openingFence = parseOpeningFence(lines[lineIndex])
            val closingLineIndex = openingFence?.let { fence ->
                findClosingFence(lines, lineIndex + 1, fence)
            }
            if (openingFence != null && closingLineIndex != null) {
                append('\n')
                lineIndex = closingLineIndex + 1
            } else {
                append(neutralizeUnmatchedFenceCandidate(lines[lineIndex]))
                append('\n')
                lineIndex += 1
            }
        }
    }
    return stripClosedInlineCode(withoutFencedCode)
}

private fun parseOpeningFence(line: String): MarkdownFence? {
    val content = contentAfterAllowedFenceIndent(line) ?: return null
    val marker = content.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
    val length = content.takeWhile { it == marker }.length
    if (length < 3) return null
    val infoString = content.drop(length)
    if (marker == '`' && infoString.contains('`')) return null
    return MarkdownFence(marker, length)
}

private fun findClosingFence(
    lines: List<String>,
    fromIndex: Int,
    openingFence: MarkdownFence,
): Int? = (fromIndex until lines.size).firstOrNull { index ->
    isMatchingClosingFence(lines[index], openingFence)
}

private fun isMatchingClosingFence(
    line: String,
    openingFence: MarkdownFence,
): Boolean {
    val content = contentAfterAllowedFenceIndent(line) ?: return false
    val markerLength = content.takeWhile { it == openingFence.marker }.length
    return markerLength >= openingFence.length &&
        content.drop(markerLength).isBlank()
}

private fun contentAfterAllowedFenceIndent(line: String): String? {
    val leadingSpaces = line.takeWhile { it == ' ' }.length
    if (leadingSpaces > 3) return null
    return line.drop(leadingSpaces)
}

private fun neutralizeUnmatchedFenceCandidate(line: String): String {
    val markerIndex = line.indexOfFirst { !it.isWhitespace() }
    if (markerIndex < 0) return line
    val marker = line[markerIndex].takeIf { it == '`' || it == '~' } ?: return line
    val markerLength = line.runLengthAt(markerIndex, marker)
    if (markerLength < 3) return line
    return buildString(line.length) {
        append(line, 0, markerIndex)
        repeat(markerLength) { append(' ') }
        append(line, markerIndex + markerLength, line.length)
    }
}

private fun stripClosedInlineCode(text: String): String = buildString {
    var index = 0
    while (index < text.length) {
        if (text[index] != '`' || isEscaped(text, index)) {
            append(text[index])
            index += 1
            continue
        }

        val delimiterLength = text.runLengthAt(index, '`')
        val closingIndex = findInlineCodeClosingDelimiter(
            text = text,
            fromIndex = index + delimiterLength,
            delimiterLength = delimiterLength,
        )
        if (closingIndex == null) {
            append(text, index, index + delimiterLength)
            index += delimiterLength
        } else {
            index = closingIndex + delimiterLength
        }
    }
}

private fun findInlineCodeClosingDelimiter(
    text: String,
    fromIndex: Int,
    delimiterLength: Int,
): Int? {
    var index = fromIndex
    while (index < text.length) {
        if (text[index] == '`') {
            val runLength = text.runLengthAt(index, '`')
            if (runLength == delimiterLength) return index
            index += runLength
        } else {
            index += 1
        }
    }
    return null
}

private fun String.runLengthAt(startIndex: Int, character: Char): Int {
    var endIndex = startIndex
    while (endIndex < length && this[endIndex] == character) {
        endIndex += 1
    }
    return endIndex - startIndex
}

private fun isEscaped(text: String, index: Int): Boolean {
    var slashCount = 0
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] == '\\') {
        slashCount += 1
        cursor -= 1
    }
    return slashCount % 2 == 1
}
