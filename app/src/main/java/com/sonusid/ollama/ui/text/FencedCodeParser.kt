package com.sonusid.ollama.ui.text

sealed class Segment {
    data class Text(val text: String) : Segment()
    data class Code(val lang: String?, val code: String) : Segment()
}

fun parseFencedCodeSegments(input: String): List<Segment> {
    if (input.isEmpty()) {
        return listOf(Segment.Text(input))
    }

    val lines = input.split("\n")
    val segments = mutableListOf<Segment>()
    val textBuffer = StringBuilder()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val startFence = parseOpeningFence(line)

        if (startFence == null) {
            appendLine(textBuffer, line)
            index++
            continue
        }

        var closingIndex = -1
        var searchIndex = index + 1
        while (searchIndex < lines.size) {
            if (isClosingFence(lines[searchIndex])) {
                closingIndex = searchIndex
                break
            }
            searchIndex++
        }

        if (closingIndex == -1) {
            appendLine(textBuffer, line)
            index++
            continue
        }

        if (textBuffer.isNotEmpty()) {
            segments.add(Segment.Text(textBuffer.toString()))
            textBuffer.clear()
        }

        val code = lines.subList(index + 1, closingIndex).joinToString("\n")
        segments.add(Segment.Code(startFence, code))
        index = closingIndex + 1
    }

    if (textBuffer.isNotEmpty() || segments.isEmpty()) {
        segments.add(Segment.Text(textBuffer.toString()))
    }

    return segments
}

private fun parseOpeningFence(line: String): String? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("```")) {
        return null
    }
    val suffix = trimmed.removePrefix("```")
    if (suffix.contains("`") || suffix.contains(' ')) {
        return null
    }
    return suffix.ifBlank { null }
}

private fun isClosingFence(line: String): Boolean = line.trim() == "```"

private fun appendLine(buffer: StringBuilder, line: String) {
    if (buffer.isNotEmpty()) {
        buffer.append("\n")
    }
    buffer.append(line)
}
