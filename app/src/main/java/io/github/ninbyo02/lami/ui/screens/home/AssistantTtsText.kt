package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.tts.JapaneseCodeSpeechText
import io.github.ninbyo02.lami.tts.SpeechTextBuilder

private val standaloneTtsCall = Regex("[A-Za-z_][A-Za-z0-9_.]*\\([^\\n]*\\)\\s*;?")
private val standaloneTtsAssignment = Regex("(?:(?:val|var|let|const)\\s+)?[A-Za-z_][A-Za-z0-9_]*\\s*=(?!=)[^\\n]+")

/**
 * Normalizes assistant text for Text-to-Speech only.
 *
 * Display text intentionally keeps emoji/decorative symbols, but TTS should not read
 * sparkle/emoji artifacts such as "キラキラ" or emoji descriptions aloud.
 */
internal fun sanitizeAssistantTextForTts(text: String): String {
    val normalized = text
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .filterNot { it.isTtsDecorativeSymbolOrEmoji() }
        .replace("*", "")
        .let { SpeechTextBuilder.prepareHeadingsForSpeech(it) }
        .lineSequence()
        .joinToString("\n") { line ->
            line.replace(Regex("[ \\t]+"), " ").trimEnd()
        }
        .replace(Regex("[ \\t]+([。、！？,.!?])"), "\$1")
        .replace(Regex("([。、！？,.!?])\\s+"), "\$1")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    if (normalized.all { !it.isLetterOrDigit() }) return ""
    return JapaneseCodeSpeechText.normalize(normalized)
}

/** Skip standalone calls/assignments while retaining explanations containing inline calls. */
internal fun sanitizeAssistantStreamingTextForTts(text: String): String {
    var insideFencedCodeBlock = false
    val filtered = text.lineSequence().map { line ->
        SpeechTextBuilder.prepareHeadingsForSpeech(line.filterNot { it.isTtsDecorativeSymbolOrEmoji() }.replace("*", "").trim())
    }.filterNot { line ->
        if (line.contains("```")) {
            insideFencedCodeBlock = !insideFencedCodeBlock
            true
        } else {
            insideFencedCodeBlock || line.isBlank() || line == "コード生成中…" ||
                line.matches(Regex("^[`*_#>\\-\\s]+$")) ||
                line.matches(Regex("^[\\p{Punct}\\s]+$")) ||
                standaloneTtsCall.matches(line) || standaloneTtsAssignment.matches(line)
        }
    }.joinToString(" ")
    return sanitizeAssistantTextForTts(filtered)
}

private fun Char.isTtsDecorativeSymbolOrEmoji(): Boolean {
    if (this == '°' || this == '℃') return false
    if (this == '☺' || this == '☻' || this == '✨' || this == '⭐' || this == '★' || this == '☆') {
        return true
    }
    val type = Character.getType(this)
    return type == Character.SURROGATE.toInt() ||
        type == Character.OTHER_SYMBOL.toInt() ||
        type == Character.NON_SPACING_MARK.toInt()
}
