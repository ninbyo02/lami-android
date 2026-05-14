package io.github.ninbyo02.lami.ui.text

sealed class Segment {
    data class Text(val text: String) : Segment()
    data class Code(
        val lang: String?,
        val code: String,
        val isClosed: Boolean = true,
    ) : Segment()
}

private data class OpeningFence(val lang: String?)

fun parseFencedCodeSegments(input: String): List<Segment> {
    if (input.isEmpty()) {
        return listOf(Segment.Text(input))
    }

    val lines = input.split("\n")
    val segments = mutableListOf<Segment>()
    val textBuffer = StringBuilder()
    var index = 0
    var foundExplicitFence = false

    while (index < lines.size) {
        val line = lines[index]
        val openingFence = parseOpeningFence(line)

        if (openingFence == null) {
            appendLine(textBuffer, line)
            index++
            continue
        }
        foundExplicitFence = true

        var closingIndex = -1
        var searchIndex = index + 1
        while (searchIndex < lines.size) {
            if (isClosingFence(lines[searchIndex])) {
                closingIndex = searchIndex
                break
            }
            searchIndex++
        }

        if (textBuffer.isNotEmpty()) {
            segments.add(Segment.Text(textBuffer.toString()))
            textBuffer.clear()
        }

        if (closingIndex == -1) {
            val code = lines.subList(index + 1, lines.size).joinToString("\n")
            segments.add(Segment.Code(openingFence.lang, code, isClosed = false))
            index = lines.size
        } else {
            val code = lines.subList(index + 1, closingIndex).joinToString("\n")
            segments.add(Segment.Code(openingFence.lang, code))
            index = closingIndex + 1
        }
    }

    if (textBuffer.isNotEmpty()) {
        segments.add(Segment.Text(textBuffer.toString()))
    }

    return if (foundExplicitFence) {
        segments
    } else {
        parseImplicitCodeSegments(input)
    }
}

private fun parseOpeningFence(line: String): OpeningFence? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("```")) {
        return null
    }
    val suffix = trimmed.removePrefix("```").trim()
    if (suffix.contains("`") || suffix.contains(' ')) {
        return null
    }
    return OpeningFence(lang = suffix.ifBlank { null })
}

private fun isClosingFence(line: String): Boolean = line.trim() == "```"

private fun appendLine(buffer: StringBuilder, line: String) {
    if (buffer.isNotEmpty()) {
        buffer.append("\n")
    }
    buffer.append(line)
}

private fun parseImplicitCodeSegments(input: String): List<Segment> {
    val lines = input.split("\n")
    val segments = mutableListOf<Segment>()
    val textBuffer = StringBuilder()
    val codeBuffer = StringBuilder()
    var inCodeBlock = false
    var codeLikeLineCount = 0

    fun flushCodeBuffer() {
        if (codeBuffer.isEmpty()) {
            inCodeBlock = false
            codeLikeLineCount = 0
            return
        }
        val bufferedCode = codeBuffer.toString()
        if (codeLikeLineCount >= 2) {
            segments.add(Segment.Code(lang = null, code = bufferedCode))
        } else {
            appendLine(textBuffer, bufferedCode)
        }
        codeBuffer.clear()
        inCodeBlock = false
        codeLikeLineCount = 0
    }

    lines.forEachIndexed { index, line ->
        val isCodeLike = isImplicitCodeLikeLine(line)
        if (inCodeBlock) {
            if (isCodeLike || line.isBlank()) {
                appendLine(codeBuffer, line)
                if (isCodeLike) codeLikeLineCount += 1
            } else {
                flushCodeBuffer()
                appendLine(textBuffer, line)
            }
            return@forEachIndexed
        }

        val shouldStartCodeBlock = isCodeLike && hasNearbyImplicitCodeLine(lines, index)
        if (shouldStartCodeBlock) {
            if (textBuffer.isNotEmpty()) {
                segments.add(Segment.Text(textBuffer.toString()))
                textBuffer.clear()
            }
            inCodeBlock = true
            codeLikeLineCount = 1
            appendLine(codeBuffer, line)
        } else {
            appendLine(textBuffer, line)
        }
    }

    if (inCodeBlock) {
        flushCodeBuffer()
    }
    if (textBuffer.isNotEmpty()) {
        segments.add(Segment.Text(textBuffer.toString()))
    }
    return if (segments.isEmpty()) listOf(Segment.Text(input)) else segments
}

private fun hasNearbyImplicitCodeLine(lines: List<String>, index: Int): Boolean {
    if (index + 1 < lines.size && isImplicitCodeLikeLine(lines[index + 1])) return true
    if (index + 2 < lines.size && lines[index + 1].isBlank() && isImplicitCodeLikeLine(lines[index + 2])) return true
    return false
}

private fun isImplicitCodeLikeLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isBlank()) return false
    if (line.startsWith("    ") || line.startsWith("\t")) return true

    val keywordPrefixes = listOf(
        "fun ", "class ", "object ", "interface ",
        "val ", "var ", "override ",
        "import ", "package ",
        "def ", "from ", "return ",
        "if (", "if ", "for ", "while ",
    )
    if (keywordPrefixes.any { trimmed.startsWith(it) }) return true

    if (trimmed == "{" || trimmed == "}" || trimmed.endsWith("{")) return true

    val looksLikeJson = (
        (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
            (trimmed.startsWith("\"") && trimmed.contains("\":"))
        )
    if (looksLikeJson) return true

    val looksLikeXml = (
        (trimmed.startsWith("<") && trimmed.endsWith(">") && trimmed.contains("</")) ||
            trimmed.startsWith("</")
        )
    return looksLikeXml
}
