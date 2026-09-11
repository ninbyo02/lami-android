package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode
import io.github.ninbyo02.lami.ui.text.processEdgeGalleryCompatibleMarkdown
import java.util.Locale

internal fun shouldPreserveWhitespaceChunk(text: String): Boolean =
    text.isNotEmpty() && text.all { it.isWhitespace() }

internal fun isViableStreamingChunk(text: String): Boolean =
    text.isNotEmpty() && (text.isNotBlank() || shouldPreserveWhitespaceChunk(text))

internal fun shouldInsertMinimalJoinBetween(
    previous: String,
    next: String,
): Boolean {
    // LiteRT-LM callback chunks preserve their tokenizer-decoded whitespace.
    // Guessing a word boundary from adjacent ASCII chunks corrupts subword
    // sequences such as "RE" + "COVER" + "ED".
    return false
}

internal fun appendMarkdownStreamingChunk(
    builder: StringBuilder,
    extractedRaw: String,
    context: StreamingAppendContext? = null,
    markdownStreamingMode: MarkdownStreamingMode = MarkdownStreamingMode.DEFAULT,
    appendTrace: ((String) -> Unit)? = null,
): String {
    return when (markdownStreamingMode) {
        // Streaming Markdown Recovery Engine v1: legacy safe markdown recovery path.
        MarkdownStreamingMode.LAMI_RECOVERY_V1 -> appendStreamingChunk(
            builder = builder,
            extractedRaw = extractedRaw,
            context = context,
            appendTrace = appendTrace,
        )
        MarkdownStreamingMode.EDGE_GALLERY_COMPAT -> {
            builder.append(processEdgeGalleryCompatibleMarkdown(extractedRaw))
            appendTrace?.let { trace ->
                safeAppendTrace(trace, "UPSTREAM append-chunk mode=edge-gallery-compatible join=${summarizeWhitespaceForUi("")}")
            }
            ""
        }
    }
}

internal fun appendStreamingChunk(
    builder: StringBuilder,
    extractedRaw: String,
    context: StreamingAppendContext? = null,
    appendTrace: ((String) -> Unit)? = null,
): String {
    if (extractedRaw.isEmpty()) return ""
    var previousText = builder.toString()
    var forcedJoin: String? = null
    if (context?.lane == StreamingLane.PROSE &&
        isStandaloneCodeLanguageTag(extractedRaw) &&
        context.pendingCodeLanguageTag == null
    ) {
        context.pendingCodeLanguageTag = extractedRaw.trim()
        appendTrace?.let { trace ->
            safeAppendTrace(trace, "[code.pendingLanguageTag.prose]=${summarizeWhitespaceForUi(context.pendingCodeLanguageTag)}")
            safeAppendTrace(trace, "UPSTREAM append-chunk lane=${StreamingLane.PROSE.label}")
            safeAppendTrace(trace, "UPSTREAM append-chunk join=${summarizeWhitespaceForUi("")}")
        }
        return ""
    }
    val lane = context?.lane ?: StreamingLane.PROSE
    if (lane == StreamingLane.PROSE && shouldEnterCodeLane(extractedRaw, context)) {
        context?.lane = StreamingLane.CODE
        appendTrace?.let { trace ->
            safeAppendTrace(trace, "[lane.switch]=prose->code reason=${codeLaneReason(extractedRaw, context)}")
        }
    }
    if (context?.lane == StreamingLane.PROSE && context.pendingCodeLanguageTag != null && !isStrongCodeLikeChunk(extractedRaw)) {
        flushPendingCodeLanguageTagAsProse(builder, context, appendTrace)
        previousText = builder.toString()
    }
    if (context?.lane == StreamingLane.CODE) {
        if (shouldLeaveCodeLane(extractedRaw, context)) {
            commitPendingCodeLine(builder, context, appendTrace)
            flushPendingCodeLanguageTagAsProse(builder, context, appendTrace)
            context.lane = StreamingLane.PROSE
            previousText = builder.toString()
            forcedJoin = if (previousText.startsWithStandaloneStreamingLanguageTagLine()) "\n" else " "
            appendTrace?.let { trace ->
                safeAppendTrace(trace, "[lane.switch]=code->prose reason=prose_like_chunk")
            }
        } else {
            return appendStreamingChunkForCode(
                builder = builder,
                extractedRaw = extractedRaw,
                context = context,
                appendTrace = appendTrace,
            )
        }
    }
    val join = forcedJoin?.takeIf {
        previousText.isNotEmpty() &&
            previousText.lastOrNull()?.isWhitespace() != true &&
            extractedRaw.isNotBlank() &&
            !extractedRaw.first().isWhitespace()
    } ?: if (shouldInsertMinimalJoinBetween(previousText, extractedRaw)) {
        " "
    } else if (
        previousText.endsWith("```") &&
        extractedRaw.isNotBlank() &&
        !extractedRaw.first().isWhitespace()
    ) {
        "\n"
    } else {
        ""
    }
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "UPSTREAM append-chunk previousTail=${summarizeWhitespaceForUi(previousText.takeLast(64))}")
        safeAppendTrace(trace, "UPSTREAM append-chunk extracted=${summarizeWhitespaceForUi(extractedRaw.take(64))}")
        safeAppendTrace(trace, "UPSTREAM append-chunk lane=${StreamingLane.PROSE.label}")
        safeAppendTrace(trace, "UPSTREAM append-chunk join=${summarizeWhitespaceForUi(join)}")
    }
    if (join.isNotEmpty()) builder.append(join)
    builder.append(extractedRaw)
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "UPSTREAM append-chunk afterTail=${summarizeWhitespaceForUi(builder.toString().takeLast(64))}")
    }
    return join
}

private fun appendStreamingChunkForCode(
    builder: StringBuilder,
    extractedRaw: String,
    context: StreamingAppendContext,
    appendTrace: ((String) -> Unit)? = null,
): String {
    val wasInFencedCodeBlock = context.inFencedCodeBlock
    val isFenceBoundaryChunk = isFenceBoundaryChunk(extractedRaw)
    if (isFenceBoundaryChunk) {
        if (wasInFencedCodeBlock) {
            commitPendingCodeLine(builder, context, appendTrace, force = true)
            appendFenceChunk(builder, extractedRaw, appendTrailingNewline = false)
            context.lane = StreamingLane.PROSE
            clearCodeLanePendingState(context)
            context.inFencedCodeBlock = updateFencedCodeState(wasInFencedCodeBlock, extractedRaw)
            context.fencedCodeLanguageTag = null
            appendTrace?.let { trace ->
                safeAppendTrace(trace, "[lane.switch]=code->prose reason=fence_close")
            }
            return ""
        }
        commitPendingCodeLine(builder, context, appendTrace)
        val hadPendingLanguageTag = context.pendingCodeLanguageTag != null
        flushPendingCodeLanguageTagAsCodeLine(builder, context, appendTrace)
        appendFenceChunk(builder, extractedRaw, appendTrailingNewline = !hadPendingLanguageTag)
        clearCodeLanePendingState(context)
        context.inFencedCodeBlock = updateFencedCodeState(wasInFencedCodeBlock, extractedRaw)
        context.fencedCodeLanguageTag = extractFencedCodeLanguageTag(extractedRaw)
        appendTrace?.let { trace ->
            safeAppendTrace(trace, "[lane.switch]=code->code reason=fence_open")
        }
        return ""
    }
    context.inFencedCodeBlock = updateFencedCodeState(wasInFencedCodeBlock, extractedRaw)

    if (isStandaloneCodeLanguageTag(extractedRaw)) {
        commitPendingCodeLine(builder, context, appendTrace)
        context.pendingCodeLanguageTag = extractedRaw.trim()
        appendTrace?.let { trace ->
            safeAppendTrace(trace, "UPSTREAM [code.pendingLanguageTag]=${summarizeWhitespaceForUi(context.pendingCodeLanguageTag)}")
            safeAppendTrace(trace, "UPSTREAM [code.insertedNewline]=false")
            safeAppendTrace(trace, "UPSTREAM append-chunk lane=${StreamingLane.CODE.label}")
            safeAppendTrace(trace, "UPSTREAM append-chunk join=${summarizeWhitespaceForUi("")}")
            safeAppendTrace(trace, "UPSTREAM append-chunk afterTail=${summarizeWhitespaceForUi(builder.toString().takeLast(64))}")
        }
        return ""
    }

    val pendingTag = context.pendingCodeLanguageTag
    var insertedNewline = false
    if (pendingTag != null) {
        if (builder.isNotEmpty() && !builder.last().isWhitespace()) {
            builder.append('\n')
            insertedNewline = true
        }
        builder.append(pendingTag)
        context.pendingCodeLanguageTag = null
        if (builder.lastOrNull() != '\n') builder.append('\n')
        appendTrace?.let { trace ->
            safeAppendTrace(trace, "[code.flushLanguageTag]")
            safeAppendTrace(trace, "UPSTREAM [code.pendingLanguageTag]=${summarizeWhitespaceForUi(pendingTag)}")
        }
    }

    preSplitFencedPythonChunk(context, extractedRaw).forEach { chunkPart ->
        appendStreamingCodeChunkBody(
            builder = builder,
            extractedRaw = chunkPart,
            context = context,
            appendTrace = appendTrace,
        )
    }

    appendTrace?.let { trace ->
        safeAppendTrace(trace, "UPSTREAM append-chunk previousTail=${summarizeWhitespaceForUi(builder.toString().takeLast(64))}")
        safeAppendTrace(trace, "UPSTREAM append-chunk extracted=${summarizeWhitespaceForUi(extractedRaw.take(64))}")
        safeAppendTrace(trace, "UPSTREAM append-chunk lane=${StreamingLane.CODE.label}")
        safeAppendTrace(trace, "UPSTREAM append-chunk join=${summarizeWhitespaceForUi("")}")
        safeAppendTrace(trace, "UPSTREAM [code.pendingLanguageTag]=${summarizeWhitespaceForUi(context.pendingCodeLanguageTag)}")
        safeAppendTrace(trace, "[code.pending.after]=${summarizeWhitespaceForUi(context.pendingCodeLineBuffer?.toString())}")
        safeAppendTrace(trace, "UPSTREAM [code.insertedNewline]=$insertedNewline")
    }
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "UPSTREAM append-chunk afterTail=${summarizeWhitespaceForUi(builder.toString().takeLast(64))}")
    }
    return ""
}

private fun appendStreamingCodeChunkBody(
    builder: StringBuilder,
    extractedRaw: String,
    context: StreamingAppendContext,
    appendTrace: ((String) -> Unit)? = null,
) {
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "[code.pending.before]=${summarizeWhitespaceForUi(context.pendingCodeLineBuffer?.toString())}")
    }

    context.pendingCodeLineBuffer ?: StringBuilder().also {
        context.pendingCodeLineBuffer = it
    }
    if (
        context.pendingCodeLineBuffer?.isNotEmpty() == true &&
            shouldStartNewFencedPythonLogicalLine(context, context.pendingCodeLineBuffer.toString(), extractedRaw)
    ) {
        commitPendingCodeLine(builder, context, appendTrace)
    } else if (
        context.pendingCodeLineBuffer?.isNotEmpty() == true &&
            shouldCommitPendingCodeLine(context, context.pendingCodeLineBuffer.toString(), extractedRaw)
    ) {
        commitPendingCodeLine(builder, context, appendTrace)
    }
    val appendBuffer = context.pendingCodeLineBuffer ?: StringBuilder().also {
        context.pendingCodeLineBuffer = it
    }
    appendBuffer.append(extractedRaw)
    if (shouldCommitPendingCodeLine(context, context.pendingCodeLineBuffer?.toString().orEmpty(), null)) {
        commitPendingCodeLine(builder, context, appendTrace)
    }
}

private fun isFenceBoundaryChunk(chunk: String): Boolean {
    val trimmed = chunk.trim()
    return trimmed.startsWith("```")
}

private fun appendFenceChunk(
    builder: StringBuilder,
    fenceChunk: String,
    appendTrailingNewline: Boolean = true,
) {
    if (builder.isNotEmpty() && builder.last() != '\n') {
        builder.append('\n')
    }
    builder.append(fenceChunk.trimEnd())
    if (appendTrailingNewline && builder.lastOrNull() != '\n') {
        builder.append('\n')
    }
}

private fun clearCodeLanePendingState(context: StreamingAppendContext) {
    context.pendingCodeLanguageTag = null
    context.pendingCodeLineBuffer = null
    context.lastCommittedCodeLine = null
    context.lastCodeChunkEndedWithNewline = false
}

internal enum class StreamingLane(val label: String) {
    PROSE("prose"),
    CODE("code"),
}

internal data class StreamingAppendContext(
    var lane: StreamingLane = StreamingLane.PROSE,
    var pendingCodeLanguageTag: String? = null,
    var pendingCodeLineBuffer: StringBuilder? = null,
    var lastCodeChunkEndedWithNewline: Boolean = false,
    var inFencedCodeBlock: Boolean = false,
    var fencedCodeLanguageTag: String? = null,
    var lastCommittedCodeLine: String? = null,
)


private fun updateFencedCodeState(current: Boolean, chunk: String): Boolean {
    val fenceCount = FENCED_MARKER_REGEX.findAll(chunk).count()
    if (fenceCount == 0) return current
    return if (fenceCount % 2 == 0) current else !current
}

private fun shouldEnterCodeLane(next: String, context: StreamingAppendContext?): Boolean {
    if (next.isEmpty()) return false
    if (context?.inFencedCodeBlock == true) return true
    val nextTrimmedStart = next.trimStart()
    if (nextTrimmedStart.startsWith("```")) return true
    if (context?.pendingCodeLanguageTag != null && isLikelyCodeAfterLanguageTag(next)) return true
    return isStrongCodeLikeChunk(next)
}

private fun codeLaneReason(next: String, context: StreamingAppendContext?): String = when {
    context?.inFencedCodeBlock == true -> "fenced_block"
    next.trimStart().startsWith("```") -> "fenced_chunk"
    context?.pendingCodeLanguageTag != null && isLikelyCodeAfterLanguageTag(next) -> "language_tag_and_strong_code"
    isStrongCodeLikeChunk(next) -> "strong_code_chunk"
    else -> "unknown"
}

private fun shouldLeaveCodeLane(next: String, context: StreamingAppendContext): Boolean {
    if (context.inFencedCodeBlock) return false
    return isProseLikeChunk(next)
}


private fun isProseLikeChunk(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.startsWith("```")) return false
    if (isStandaloneCodeLanguageTag(trimmed)) return false
    if (isStrongCodeLikeChunk(trimmed)) return false
    if (isCommandLikeCodeChunk(trimmed)) return false
    if (isCodeArtifactLikeChunk(trimmed)) return false

    val hasJapanese = JAPANESE_TEXT_REGEX.containsMatchIn(trimmed)
    val hasSentencePunctuation = JAPANESE_SENTENCE_PUNCTUATION.any { punctuation ->
        trimmed.contains(punctuation)
    }
    val isQuotedNaturalText = trimmed.length >= 3 &&
        ((trimmed.startsWith('"') && trimmed.endsWith('"')) ||
            (trimmed.startsWith('“') && trimmed.endsWith('”'))) &&
        trimmed.any { it.isLetter() }

    return hasJapanese || hasSentencePunctuation || isQuotedNaturalText
}

private fun isCommandLikeCodeChunk(text: String): Boolean =
    CODE_COMMAND_CHUNK_REGEX.containsMatchIn(text)

private fun isCodeArtifactLikeChunk(text: String): Boolean =
    CODE_ARTIFACT_CHUNK_REGEX.containsMatchIn(text)

private fun flushPendingCodeLanguageTagAsProse(
    builder: StringBuilder,
    context: StreamingAppendContext,
    appendTrace: ((String) -> Unit)? = null,
) {
    val pendingTag = context.pendingCodeLanguageTag ?: return
    val join = if (shouldInsertMinimalJoinBetween(builder.toString(), pendingTag)) " " else ""
    if (join.isNotEmpty()) builder.append(join)
    builder.append(pendingTag)
    context.pendingCodeLanguageTag = null
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "[code.flushLanguageTag.asProse]")
    }
}

private fun flushPendingCodeLanguageTagAsCodeLine(
    builder: StringBuilder,
    context: StreamingAppendContext,
    appendTrace: ((String) -> Unit)? = null,
) {
    val pendingTag = context.pendingCodeLanguageTag ?: return
    if (builder.isNotEmpty() && builder.last() != '\n') {
        builder.append('\n')
    }
    builder.append(pendingTag)
    if (builder.lastOrNull() != '\n') {
        builder.append('\n')
    }
    context.pendingCodeLanguageTag = null
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "[code.flushLanguageTag.asCodeLine]")
    }
}

private val STREAMING_CODE_LANGUAGE_TAGS = setOf("python", "kotlin", "bash", "json")
private val FENCED_MARKER_REGEX = Regex("```")
private val FENCED_PYTHON_ASSIGNMENT_STARTER_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*\\s*(?:[+\\-*/%:]?=)")
private val FENCED_PYTHON_STRONG_STARTERS = listOf(
    "import ",
    "from ",
    "class ",
    "def ",
    "if ",
    "elif ",
    "else:",
    "for ",
    "while ",
    "try:",
    "except",
    "finally:",
    "with ",
    "return ",
    "raise ",
    "yield ",
    "async def ",
    "async for ",
    "async with ",
)

private fun preSplitFencedPythonChunk(context: StreamingAppendContext, raw: String): List<String> {
    if (!isFencedPythonCodeContext(context)) return listOf(raw)
    if (raw.isEmpty() || raw.contains('\n')) return listOf(raw)
    if (isFenceBoundaryChunk(raw)) return listOf(raw)
    val pendingLine = context.pendingCodeLineBuffer?.toString().orEmpty()
    val normalizedRaw = if (
        raw.firstOrNull()?.isWhitespace() == true &&
        (pendingLine.trimStart().startsWith("import ") || context.lastCommittedCodeLine?.trimStart()?.startsWith("import ") == true)
    ) {
        raw.trimStart()
    } else {
        raw
    }
    if (pendingLine.isNotEmpty() && (isQuoteOrBracketCarryOverLine(pendingLine) || isFencedPythonCommentCarryOverLine(context, pendingLine))) {
        return listOf(normalizedRaw)
    }
    return splitFencedPythonChunkSequentially(normalizedRaw)
}

private fun splitFencedPythonChunkSequentially(raw: String): List<String> {
    val chunks = mutableListOf<String>()
    var remainder = raw
    while (remainder.isNotEmpty()) {
        val splitIndex = findNextFencedPythonSplitIndex(remainder)
        if (splitIndex == null) {
            chunks += remainder
            break
        }
        if (splitIndex !in 1 until remainder.length) {
            chunks += remainder
            break
        }
        chunks += remainder.substring(0, splitIndex)
        remainder = remainder.substring(splitIndex)
    }
    return chunks.filter { it.isNotEmpty() }
}

private fun findNextFencedPythonSplitIndex(text: String): Int? {
    for (index in 1 until text.length) {
        if (shouldSplitAtFencedPythonIndex(text, index)) return index
    }
    return null
}

private fun shouldSplitAtFencedPythonIndex(raw: String, index: Int): Boolean {
    if (index !in 1 until raw.length) return false
    if (isInsideQuotedString(raw, index)) return false
    if (isInsidePythonComment(raw, index)) return isFencedPythonCommentTailBoundaryAt(raw, index)
    if (hasUnclosedBrackets(raw.substring(0, index))) return false
    if (isFencedPythonClosingBracketTailBoundaryAt(raw, index)) return true
    if (isFencedPythonImportTailBoundaryAt(raw, index)) return true
    if (isFencedPythonTailToStrongStarterBoundaryAt(raw, index)) return true
    if (isFencedPythonIdentifierToStrongStarterBoundaryAt(raw, index)) return true
    if (isFencedPythonBooleanLiteralToStrongStarterBoundaryAt(raw, index)) return true
    if (isFencedPythonBooleanLiteralToAssignmentBoundaryAt(raw, index)) return true
    if (isFencedPythonNumericLiteralTailBoundaryAt(raw, index)) return true
    return isFencedPythonLiteralToAssignmentBoundaryAt(raw, index)
}

private fun isFencedPythonTailToStrongStarterBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isFencedPythonStrongStarterAt(text, index)) return false
    val before = text[index - 1]
    if (before == '\n' || before.isWhitespace() || before == '#') return false
    return true
}

private fun isFencedPythonIdentifierToStrongStarterBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    val before = text[index - 1]
    if (!isIdentifierPart(before)) return false
    if (!isIdentifierStart(text[index])) return false
    if (before == '_') return false
    if (before.isUpperCase() && text[index].isUpperCase()) return false
    if (isFencedPythonStrongStarterAt(text, index)) return true
    if (isUpperSnakeAssignmentListStarterAt(text, index)) return true
    return isFencedPythonAssignmentTargetListStarterAt(text, index)
}

private fun isFencedPythonLiteralToAssignmentBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isFencedPythonAssignmentTargetListStarterAt(text, index)) return false
    val before = text[index - 1]
    return before.isDigit() || before in listOf(']', ')', '}', '"', '\'')
}

private fun isFencedPythonBooleanLiteralToAssignmentBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isLooseFencedPythonAssignmentStarterAt(text, index)) return false
    val prefix = text.substring(0, index)
    return prefix.endsWith("True") || prefix.endsWith("False")
}

private fun isFencedPythonBooleanLiteralToStrongStarterBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!matchesFencedPythonStrongStarterAt(text, index, requireBoundary = false)) return false
    val prefix = text.substring(0, index)
    return prefix.endsWith("True") || prefix.endsWith("False")
}

private fun isFencedPythonImportTailBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("import ") && !trimmed.startsWith("from ")) return false

    if (text.regionMatches(index, "import ", 0, "import ".length) ||
        text.regionMatches(index, "from ", 0, "from ".length)
    ) {
        val before = text[index - 1]
        if (!isAsciiIdentifierPart(before) && before != ')' && before != ']' && before != '}') return false
        val previousWord = text.substring(0, index).trimEnd().takeLastWhile { isAsciiIdentifierPart(it) }
        if (previousWord == "as") return false
        return true
    }

    val before = text[index - 1]
    if (!before.isWhitespace()) return false
    if (!isAsciiIdentifierStart(text[index]) && !isFencedPythonStrongStarterAt(text, index)) return false

    if (trimmed.startsWith("import ")) {
        val importTokenEnd = text.indexOf("import ") + "import ".length
        if (index <= importTokenEnd) return false
        val previousWord = text.substring(0, index).trimEnd().takeLastWhile { isAsciiIdentifierPart(it) }
        if (previousWord == "as") return false
        return true
    }

    val importIndex = text.indexOf(" import ")
    if (importIndex < 0 || index <= importIndex + " import ".length) return false
    val previousWord = text.substring(0, index).trimEnd().takeLastWhile { isAsciiIdentifierPart(it) }
    if (previousWord == "as") return false
    return isAsciiIdentifierStart(text[index]) || isFencedPythonStrongStarterAt(text, index)
}

private fun isFencedPythonNumericLiteralTailBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isIdentifierStart(text[index])) return false
    val before = text[index - 1]
    if (!before.isDigit() && before !in listOf(']', ')', '}')) return false
    return true
}

private fun isFencedPythonStrongStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    val before = text[index - 1]
    val prevPrev = text.getOrNull(index - 2)
    val hasWordBoundary = before == '\n' || (!isAsciiIdentifierPart(before)) || (before.isLowerCase() && text[index].isUpperCase())
    if (!hasWordBoundary) return false
    if (prevPrev == '#' || before == '#') return false
    return matchesFencedPythonStrongStarterAt(text, index)
}

private fun isFencedPythonAssignmentStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    return isFencedPythonAssignmentTargetListStarterAt(text, index)
}

private fun isFencedPythonAssignmentTargetListStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isIdentifierStart(text[index])) return false
    val before = text[index - 1]
    if (before == '#') return false
    val boundary = before == '\n' || !isIdentifierPart(before) || (text[index].isUpperCase() && (before.isLowerCase() || before.isDigit()))
    if (!boundary) return false
    var cursor = index
    while (true) {
        if (cursor >= text.length || !isIdentifierStart(text[cursor])) return false
        cursor += 1
        while (cursor < text.length && isIdentifierPart(text[cursor])) cursor += 1
        while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
        if (cursor < text.length && text[cursor] == ',') {
            cursor += 1
            while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
            continue
        }
        break
    }
    while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
    if (cursor >= text.length) return false
    if (text[cursor] == '=') return text.getOrNull(cursor + 1) != '='
    if (cursor + 1 >= text.length) return false
    val op = text[cursor]
    val eq = text[cursor + 1]
    return op in charArrayOf('+', '-', '*', '/', '%', ':') && eq == '='
}

private fun isFencedPythonCommentTailBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    val before = text[index - 1]
    if (before == '\n') return false
    if (!isInsidePythonComment(text, index)) return false
    if (text[index] == '#') return true
    if (isAsciiAssignmentStarterAt(text, index)) return true
    return isCommentStrongStarterAt(text, index)
}

private fun isFencedPythonClosingBracketTailBoundaryAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    val before = text[index - 1]
    if (before !in listOf(')', ']', '}')) return false
    if (isCommentStarterAt(text, index) && before == ')') {
        return !pythonCommentTailHasCodeBoundary(text, index)
    }
    if (isCommentStarterAt(text, index)) return true
    if (isFencedPythonStrongStarterAt(text, index)) return true
    return isAsciiAssignmentStarterAt(text, index)
}

private fun pythonCommentTailHasCodeBoundary(text: String, hashIndex: Int): Boolean {
    for (index in (hashIndex + 1) until text.length) {
        if (isFencedPythonCommentTailBoundaryAt(text, index)) return true
    }
    return false
}

private fun isFencedPythonClassOrDefStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    return text.regionMatches(index, "class ", 0, "class ".length) ||
        text.regionMatches(index, "def ", 0, "def ".length)
}

private fun isInsideQuotedString(text: String, targetIndex: Int): Boolean {
    if (targetIndex <= 0 || targetIndex >= text.length) return false
    return hasUnclosedQuotedString(text.substring(0, targetIndex))
}

private fun isInsidePythonComment(text: String, targetIndex: Int): Boolean {
    if (targetIndex <= 0 || targetIndex > text.length) return false
    var inSingleQuote = false
    var inDoubleQuote = false
    var escaped = false
    var inComment = false
    for (i in 0 until targetIndex) {
        val ch = text[i]
        if (inComment) {
            if (ch == '\n') inComment = false
            continue
        }
        if (escaped) {
            escaped = false
            continue
        }
        when (ch) {
            '\\' -> if (inSingleQuote || inDoubleQuote) escaped = true
            '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
            '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
            '#' -> if (!inSingleQuote && !inDoubleQuote) inComment = true
        }
    }
    return inComment
}

private fun matchesFencedPythonStrongStarterAt(
    text: String,
    index: Int,
    requireBoundary: Boolean = true,
): Boolean {
    return FENCED_PYTHON_STRONG_STARTERS.any { keyword ->
        if (!text.regionMatches(index, keyword, 0, keyword.length)) return@any false
        if (!requireBoundary) return@any true
        val before = text.getOrNull(index - 1) ?: return@any true
        before == '\n' || !isAsciiIdentifierPart(before) || (before.isLowerCase() && text[index].isUpperCase())
    }
}

private fun isIdentifierStart(ch: Char): Boolean = ch == '_' || ch.isLetter()

private fun isIdentifierPart(ch: Char): Boolean = isIdentifierStart(ch) || ch.isDigit()

private fun isAsciiIdentifierPart(ch: Char): Boolean = ch == '_' || ch.isDigit() || ch in 'a'..'z' || ch in 'A'..'Z'

private fun isAsciiIdentifierStart(ch: Char): Boolean = ch == '_' || ch in 'a'..'z' || ch in 'A'..'Z'

private fun isCommentStrongStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    return text.regionMatches(index, "class ", 0, "class ".length) ||
        text.regionMatches(index, "def ", 0, "def ".length) ||
        text.regionMatches(index, "import ", 0, "import ".length) ||
        text.regionMatches(index, "from ", 0, "from ".length)
}

private fun isCommentStarterAt(text: String, index: Int): Boolean =
    index in 1 until text.length && text[index] == '#'

private fun isUpperSnakeAssignmentStarterAt(text: String, index: Int): Boolean {
    return upperSnakeAssignmentPrefixEnd(text, index) != null
}

private fun isUpperSnakeAssignmentListStarterAt(text: String, index: Int): Boolean {
    val end = upperSnakeAssignmentPrefixEnd(text, index) ?: return false
    return text.substring(index, end).contains(',')
}

private fun upperSnakeAssignmentPrefixEnd(text: String, index: Int): Int? {
    if (index !in text.indices) return null
    if (!text[index].isUpperCase()) return null
    var cursor = index
    while (cursor < text.length && (text[cursor].isUpperCase() || text[cursor].isDigit() || text[cursor] == '_')) {
        cursor += 1
    }
    if (cursor == index) return null
    while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
    while (cursor < text.length && text[cursor] == ',') {
        cursor += 1
        while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
        if (cursor >= text.length || !text[cursor].isUpperCase()) return null
        while (cursor < text.length && (text[cursor].isUpperCase() || text[cursor].isDigit() || text[cursor] == '_')) {
            cursor += 1
        }
        while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
    }
    if (cursor >= text.length) return null
    if (text[cursor] == '=') return if (text.getOrNull(cursor + 1) != '=') cursor else null
    if (cursor + 1 >= text.length) return null
    val op = text[cursor]
    val eq = text[cursor + 1]
    return if (op in charArrayOf('+', '-', '*', '/', '%', ':') && eq == '=') cursor else null
}

private fun isAsciiAssignmentStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isAsciiIdentifierStart(text[index])) return false
    var cursor = index + 1
    while (cursor < text.length && isAsciiIdentifierPart(text[cursor])) cursor += 1
    while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
    if (cursor >= text.length) return false
    if (text[cursor] == '=') return text.getOrNull(cursor + 1) != '='
    if (cursor + 1 >= text.length) return false
    val op = text[cursor]
    val eq = text[cursor + 1]
    return op in charArrayOf('+', '-', '*', '/', '%', ':') && eq == '='
}

private fun isLooseFencedPythonAssignmentStarterAt(text: String, index: Int): Boolean {
    if (index !in 1 until text.length) return false
    if (!isIdentifierStart(text[index])) return false
    var cursor = index
    while (true) {
        if (cursor >= text.length || !isIdentifierStart(text[cursor])) return false
        cursor += 1
        while (cursor < text.length && isIdentifierPart(text[cursor])) cursor += 1
        while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
        if (cursor < text.length && text[cursor] == ',') {
            cursor += 1
            while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
            continue
        }
        break
    }
    while (cursor < text.length && text[cursor].isWhitespace()) cursor += 1
    if (cursor >= text.length) return false
    if (text[cursor] == '=') return text.getOrNull(cursor + 1) != '='
    if (cursor + 1 >= text.length) return false
    val op = text[cursor]
    val eq = text[cursor + 1]
    return op in charArrayOf('+', '-', '*', '/', '%', ':') && eq == '='
}

private fun isStandaloneLanguageTag(text: String): Boolean {
    val normalized = text.trim()
    return normalized in STREAMING_CODE_LANGUAGE_TAGS
}

private fun isStandaloneCodeLanguageTag(text: String): Boolean = isStandaloneLanguageTag(text)

private fun String.startsWithStandaloneStreamingLanguageTagLine(): Boolean {
    val firstLine = lineSequence().firstOrNull()?.trim().orEmpty()
    return firstLine in STREAMING_CODE_LANGUAGE_TAGS
}

private fun isLikelyCodeAfterLanguageTag(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    return isStrongCodeLikeChunk(trimmed) ||
        trimmed.matches(Regex("[A-Za-z_][A-Za-z0-9_.]*"))
}

private fun isStrongCodeLineStart(text: String): Boolean {
    val trimmedStart = text.trimStart()
    if (trimmedStart.isEmpty()) return false
    val lower = trimmedStart.lowercase(Locale.ROOT)
    val keywords = listOf(
        "import ",
        "from ",
        "def ",
        "class ",
        "for ",
        "while ",
        "return ",
        "print(",
        "if ",
        "elif ",
        "else",
        "try",
        "except",
    )
    if (keywords.any { lower.startsWith(it) }) return true
    val assignmentPattern = Regex("^[A-Za-z_][A-Za-z0-9_\\.\\[\\]]*\\s*=.+")
    return assignmentPattern.containsMatchIn(trimmedStart)
}

private fun isStrongCodeLikeChunk(text: String): Boolean =
    isStrongCodeLineStart(text) || text.startsWith("    ")

private fun shouldCommitPendingCodeLine(
    context: StreamingAppendContext,
    pendingLine: String,
    nextChunk: String?,
): Boolean {
    if (pendingLine.isEmpty()) return false
    if (pendingLine.contains('\n')) return true
    if (nextChunk == null) {
        return !shouldHoldPendingCodeLine(pendingLine) &&
            !isFencedPythonCommentCarryOverLine(context, pendingLine)
    }
    if (nextChunk.startsWith("```")) return true
    if (shouldCommitAfterFencedPythonComment(context, pendingLine, nextChunk)) return true
    if (shouldKeepPythonCommentOnSameLogicalLine(context, pendingLine, nextChunk)) return false
    if (shouldAppendToCurrentCodeLine(pendingLine, nextChunk)) return false
    if (!isStrongCodeLikeChunk(nextChunk)) return false
    if (pendingLine.endsWith(" ") || pendingLine.endsWith("(") || pendingLine.endsWith("=")) return false
    return pendingLine.trimStart().startsWith("{") ||
        pendingLine.trimStart().startsWith("}") ||
        pendingLine.trimEnd().endsWith(":") ||
        isStrongCodeLikeChunk(pendingLine)
}

private fun shouldKeepPythonCommentOnSameLogicalLine(
    context: StreamingAppendContext,
    pendingLine: String,
    nextChunk: String,
): Boolean {
    if (!isFencedPythonCommentCarryOverLine(context, pendingLine)) return false
    if (nextChunk.isEmpty() || nextChunk.contains('\n')) return false
    if (nextChunk.trimStart().startsWith("```")) return false
    if (nextChunk.trimStart().startsWith("#")) return false
    return !isFencedPythonLogicalLineStarter(nextChunk)
}

private fun isFencedPythonCommentCarryOverLine(
    context: StreamingAppendContext,
    pendingLine: String,
): Boolean {
    if (!isFencedPythonCodeContext(context)) return false
    if (pendingLine.contains('\n')) return false
    if (isQuoteOrBracketCarryOverLine(pendingLine)) return false
    return findPythonCommentHashIndex(pendingLine) >= 0
}

private fun findPythonCommentHashIndex(line: String): Int {
    var inSingleQuote = false
    var inDoubleQuote = false
    var escaped = false
    line.forEachIndexed { index, ch ->
        if (escaped) {
            escaped = false
            return@forEachIndexed
        }
        when (ch) {
            '\\' -> if (inSingleQuote || inDoubleQuote) escaped = true
            '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
            '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
            '#' -> if (!inSingleQuote && !inDoubleQuote) return index
        }
    }
    return -1
}

private fun shouldStartNewFencedPythonLogicalLine(
    context: StreamingAppendContext,
    pendingLine: String,
    nextChunk: String,
): Boolean {
    if (!isFencedPythonCodeContext(context)) return false
    if (pendingLine.isEmpty() || nextChunk.isEmpty()) return false
    if (nextChunk.trimStart().startsWith("```")) return false
    if (shouldCommitAfterFencedPythonComment(context, pendingLine, nextChunk)) return true
    if (shouldKeepPythonCommentOnSameLogicalLine(context, pendingLine, nextChunk)) return false
    if (!isFencedPythonLogicalLineStarter(nextChunk)) return false
    if (shouldAppendToCurrentCodeLine(pendingLine, nextChunk)) return false
    return !isQuoteOrBracketCarryOverLine(pendingLine)
}

private fun shouldCommitAfterFencedPythonComment(
    context: StreamingAppendContext,
    pendingLine: String,
    nextChunk: String,
): Boolean {
    if (!isFencedPythonCodeContext(context)) return false
    if (!isFencedPythonCommentCarryOverLine(context, pendingLine)) return false
    if (pendingLine.contains('\n')) return false
    if (nextChunk.isEmpty() || nextChunk.contains('\n')) return false
    if (nextChunk.trimStart().startsWith("```")) return false
    if (isQuoteOrBracketCarryOverLine(pendingLine)) return false
    if (shouldAppendToCurrentCodeLine(pendingLine, nextChunk)) return false
    return nextChunk.trimStart().startsWith("#") ||
        isFencedPythonAssignmentStarter(nextChunk) ||
        isFencedPythonClassOrDefStarter(nextChunk)
}

private fun isFencedPythonCommentLine(line: String): Boolean = line.trimStart().startsWith("#")

private fun isFencedPythonAssignmentStarter(chunk: String): Boolean {
    val trimmedStart = chunk.trimStart()
    return FENCED_PYTHON_ASSIGNMENT_STARTER_REGEX.containsMatchIn(trimmedStart)
}

private fun isFencedPythonClassOrDefStarter(chunk: String): Boolean {
    val trimmedStart = chunk.trimStart()
    return trimmedStart.startsWith("class ") || trimmedStart.startsWith("def ")
}

private fun isFencedPythonCodeContext(context: StreamingAppendContext): Boolean {
    if (!context.inFencedCodeBlock) return false
    val normalized = context.fencedCodeLanguageTag?.trim()?.lowercase(Locale.ROOT) ?: return false
    return normalized == "python" || normalized == "py"
}

private fun isStrongFencedPythonLogicalLineStarter(chunk: String): Boolean {
    val trimmed = chunk.trimStart()
    if (trimmed.isEmpty()) return false
    val strongKeywords = listOf(
        "import",
        "from",
        "class",
        "def",
        "if",
        "elif",
        "else",
        "for",
        "while",
        "try",
        "except",
        "finally",
        "with",
        "return",
        "raise",
        "yield",
    )
    return strongKeywords.any { matchesFencedPythonStarterKeyword(trimmed, it) }
}

private fun isFencedPythonLogicalLineStarter(chunk: String): Boolean =
    isStrongFencedPythonLogicalLineStarter(chunk) || isFencedPythonUpperSnakeAssignmentListStarter(chunk)

private fun isFencedPythonUpperSnakeAssignmentListStarter(chunk: String): Boolean {
    val trimmed = chunk.trimStart()
    val end = upperSnakeAssignmentPrefixEnd(trimmed, 0) ?: return false
    return trimmed.substring(0, end).contains(',')
}

private fun matchesFencedPythonStarterKeyword(text: String, keyword: String): Boolean {
    if (!text.startsWith(keyword)) return false
    if (text.length == keyword.length) return true
    val next = text[keyword.length]
    return next.isWhitespace() || next == ':'
}

private fun isQuoteOrBracketCarryOverLine(pendingLine: String): Boolean {
    val trimmedEnd = pendingLine.trimEnd()
    if (trimmedEnd.isEmpty()) return false
    if (hasUnclosedQuotedString(trimmedEnd)) return true
    if (hasUnclosedBrackets(trimmedEnd)) return true
    return trimmedEnd.endsWith(",") ||
        trimmedEnd.endsWith("(") ||
        trimmedEnd.endsWith("[") ||
        trimmedEnd.endsWith("{")
}

private fun extractFencedCodeLanguageTag(chunk: String): String? {
    val trimmed = chunk.trim()
    if (!trimmed.startsWith("```")) return null
    val markerTail = trimmed.removePrefix("```").trim()
    if (markerTail.isEmpty()) return null
    return markerTail.lineSequence().first().trim().ifEmpty { null }
}

private fun shouldHoldPendingCodeLine(pendingLine: String): Boolean {
    val trimmedEnd = pendingLine.trimEnd()
    if (trimmedEnd.isEmpty()) return false
    if (isQuoteOrBracketContinuationLine(pendingLine)) return true
    return trimmedEnd.endsWith("(") ||
        trimmedEnd.endsWith("=") ||
        trimmedEnd.matches(Regex("[A-Za-z_][A-Za-z0-9_.]*"))
}

private fun shouldAppendToCurrentCodeLine(
    pendingLine: String,
    nextChunk: String,
): Boolean {
    if (nextChunk.isEmpty()) return true
    if (nextChunk.contains('\n')) return false
    if (nextChunk.trimStart().startsWith("```")) return false

    val previous = pendingLine.trimEnd()
    if (previous.isEmpty()) return true
    val nextTrimmedStart = nextChunk.trimStart()
    if (nextTrimmedStart.isEmpty()) return true

    if (nextChunk.first().isWhitespace()) {
        return isQuoteOrBracketContinuationLine(pendingLine)
    }

    val previousLast = previous.last()
    val nextFirst = nextTrimmedStart.first()

    val inlinePair = (previousLast.isLetterOrDigit() || previousLast == '_') &&
        (nextFirst == '(' || nextFirst == ',' || nextFirst == ')' || nextFirst == '!' || nextFirst == ']' || nextFirst == '}')
    if (inlinePair) return true

    if ((previousLast == '(' || previousLast == '[' || previousLast == '{') &&
        (nextFirst.isLetterOrDigit() || nextFirst == '"' || nextFirst == '\'')
    ) return true

    if ((previousLast == '"' || previousLast == '\'') && (nextFirst == '"' || nextFirst == '\'' || nextFirst.isLetterOrDigit())) {
        return true
    }

    if (previousLast == ',' && nextChunk.first().isWhitespace()) return true

    return false
}

private fun isQuoteOrBracketContinuationLine(pendingLine: String): Boolean {
    val trimmedEnd = pendingLine.trimEnd()
    if (trimmedEnd.isEmpty()) return false
    if (hasUnclosedQuotedString(trimmedEnd)) return true
    if (hasUnclosedBrackets(trimmedEnd)) return true
    if (trimmedEnd.endsWith(",")) return true
    if (trimmedEnd.endsWith("(") || trimmedEnd.endsWith("[") || trimmedEnd.endsWith("{")) return true
    return trimmedEnd.endsWith(".") ||
        trimmedEnd.endsWith("=") ||
        trimmedEnd.matches(Regex(".*[+\\-*/%&|^<>!]$"))
}

private fun hasUnclosedQuotedString(text: String): Boolean {
    var index = 0
    var single = false
    var double = false
    var tripleSingle = false
    var tripleDouble = false
    while (index < text.length) {
        if (tripleSingle) {
            if (text.startsWith("'''", index)) {
                tripleSingle = false
                index += 3
                continue
            }
            index += 1
            continue
        }
        if (tripleDouble) {
            if (text.startsWith("\"\"\"", index)) {
                tripleDouble = false
                index += 3
                continue
            }
            index += 1
            continue
        }
        val ch = text[index]
        val escaped = index > 0 && text[index - 1] == '\\' && (index < 2 || text[index - 2] != '\\')
        if (ch == '\'' && !double && !escaped) {
            if (!single && text.startsWith("'''", index)) {
                tripleSingle = true
                index += 3
                continue
            }
            single = !single
            index += 1
            continue
        }
        if (ch == '"' && !single && !escaped) {
            if (!double && text.startsWith("\"\"\"", index)) {
                tripleDouble = true
                index += 3
                continue
            }
            double = !double
            index += 1
            continue
        }
        index += 1
    }
    return single || double || tripleSingle || tripleDouble
}

private fun hasUnclosedBrackets(text: String): Boolean {
    var round = 0
    var square = 0
    var curly = 0
    var index = 0
    var single = false
    var double = false
    var tripleSingle = false
    var tripleDouble = false
    while (index < text.length) {
        if (tripleSingle) {
            if (text.startsWith("'''", index)) {
                tripleSingle = false
                index += 3
                continue
            }
            index += 1
            continue
        }
        if (tripleDouble) {
            if (text.startsWith("\"\"\"", index)) {
                tripleDouble = false
                index += 3
                continue
            }
            index += 1
            continue
        }
        val ch = text[index]
        val escaped = index > 0 && text[index - 1] == '\\' && (index < 2 || text[index - 2] != '\\')
        if (ch == '\'' && !double && !escaped) {
            if (!single && text.startsWith("'''", index)) {
                tripleSingle = true
                index += 3
                continue
            }
            single = !single
            index += 1
            continue
        }
        if (ch == '"' && !single && !escaped) {
            if (!double && text.startsWith("\"\"\"", index)) {
                tripleDouble = true
                index += 3
                continue
            }
            double = !double
            index += 1
            continue
        }
        if (single || double) {
            index += 1
            continue
        }
        when (ch) {
            '(' -> round += 1
            ')' -> round = (round - 1).coerceAtLeast(0)
            '[' -> square += 1
            ']' -> square = (square - 1).coerceAtLeast(0)
            '{' -> curly += 1
            '}' -> curly = (curly - 1).coerceAtLeast(0)
        }
        index += 1
    }
    return round > 0 || square > 0 || curly > 0
}

private fun commitPendingCodeLine(
    builder: StringBuilder,
    context: StreamingAppendContext,
    appendTrace: ((String) -> Unit)? = null,
    force: Boolean = false,
) {
    val pending = context.pendingCodeLineBuffer?.toString().orEmpty().trimEnd()
    if (pending.isEmpty()) return
    if (builder.isNotEmpty() && builder.last() != '\n' && !pending.startsWith("\n")) {
        builder.append('\n')
    }
    builder.append(pending)
    context.lastCommittedCodeLine = pending
    context.lastCodeChunkEndedWithNewline = pending.endsWith('\n')
    context.pendingCodeLineBuffer = null
    appendTrace?.let { trace ->
        safeAppendTrace(trace, "[code.commit]=${summarizeWhitespaceForUi(pending)}")
    }
}


private val JAPANESE_TEXT_REGEX = Regex("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]")
private val JAPANESE_SENTENCE_PUNCTUATION = listOf("。", "、", "！", "？")
private val CODE_COMMAND_CHUNK_REGEX = Regex("^(python|python3|bash|sh|node|ruby|java|kotlinc)\\b", RegexOption.IGNORE_CASE)
private val CODE_ARTIFACT_CHUNK_REGEX = Regex("^[A-Za-z0-9_./-]+\\.(py|kt|kts|sh|json|yaml|yml|xml|txt|md)$", RegexOption.IGNORE_CASE)

private val STREAMING_STRONG_CODE_SIGNALS = listOf(
    "import ",
    "from ",
    "def ",
    "class ",
    "return",
    "print(",
    "=",
    "self.",
)
