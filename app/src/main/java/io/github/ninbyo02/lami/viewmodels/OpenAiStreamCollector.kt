package io.github.ninbyo02.lami.viewmodels

import java.io.IOException

internal data class OpenAiStreamResult(
    val text: String,
    val model: String?,
    val finishReason: String,
    val timeToFirstTokenMs: Long?,
    val timeToFirstThinkingTokenMs: Long?,
    val thinkingCharacterCount: Int,
    val thinkingChunkCount: Int,
    val assistantUpdateCount: Int,
)

/** Consumes the same SSE lines used by the HTTP path; reasoning never enters content. */
internal fun collectOpenAiStream(
    lines: Sequence<String>,
    elapsedMs: () -> Long,
    ensureActive: () -> Unit,
    onThinking: (Int, Int) -> Unit,
    onContent: (String) -> Unit,
    transformContent: (String) -> String = { it },
): OpenAiStreamResult {
    val text = StringBuilder()
    var model: String? = null
    var reason: String? = null
    var done = false
    var firstContent: Long? = null
    var firstThinking: Long? = null
    var thinkingCharacters = 0
    var thinkingChunks = 0
    var updates = 0
    var lastThinkingAt: Long? = null
    var lastContentAt: Long? = null
    var lastFlushed: String? = null

    fun flush(value: String) {
        if (value.isNotEmpty() && value != lastFlushed) {
            ensureActive()
            onContent(value)
            lastFlushed = value
            updates++
        }
    }

    val iterator = lines.iterator()
    while (true) {
        ensureActive()
        if (!iterator.hasNext()) break
        val line = iterator.next()
        ensureActive()
        val chunk = parseOpenAiCompatibleStreamingLine(line) ?: continue
        val now = elapsedMs().coerceAtLeast(0L)
        model = model ?: chunk.model
        reason = chunk.finishReason ?: reason
        val thinking = chunk.reasoningText
        if (!thinking.isNullOrEmpty()) {
            firstThinking = firstThinking ?: now
            thinkingCharacters += thinking.length
            thinkingChunks++
            if (firstContent == null && (lastThinkingAt == null || now - lastThinkingAt >= 120L)) {
                ensureActive()
                onThinking(thinkingCharacters, thinkingChunks)
                lastThinkingAt = now
            }
        }
        val content = chunk.text
        if (!content.isNullOrEmpty()) {
            firstContent = firstContent ?: now
            text.append(transformContent(content))
            val current = text.toString()
            if (lastContentAt == null || now - lastContentAt >= 80L ||
                content.lastOrNull() in setOf('。', '、', '！', '？', '\n')
            ) {
                flush(current)
                lastContentAt = now
            }
        }
        // Process both payloads before honoring the finish marker.
        if (chunk.done || chunk.finishReason != null) {
            done = true
            break
        }
    }
    ensureActive()
    if (!done) {
        val phase = if (text.isEmpty() && thinkingChunks > 0) " during thinking" else ""
        throw IOException("OpenAI compatible streaming response ended before done$phase")
    }
    val finalText = text.toString().trim()
    flush(finalText)
    // Empty content is returned with its thinking metrics and finish reason so the
    // caller can persist diagnostics before classifying a thinking-only completion.
    return OpenAiStreamResult(
        text = finalText,
        model = model,
        finishReason = reason ?: "stop",
        timeToFirstTokenMs = firstContent,
        timeToFirstThinkingTokenMs = firstThinking,
        thinkingCharacterCount = thinkingCharacters,
        thinkingChunkCount = thinkingChunks,
        assistantUpdateCount = updates,
    )
}
