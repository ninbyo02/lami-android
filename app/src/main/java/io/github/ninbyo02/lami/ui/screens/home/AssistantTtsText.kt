package io.github.ninbyo02.lami.ui.screens.home

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
        .lineSequence()
        .joinToString("\n") { line ->
            line.replace(Regex("[ \\t]+"), " ").trimEnd()
        }
        .replace(Regex("[ \\t]+([。、！？,.!?])"), "\$1")
        .replace(Regex("([。、！？,.!?])\\s+"), "\$1")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    if (normalized.all { !it.isLetterOrDigit() }) return ""
    return normalized
}

private fun Char.isTtsDecorativeSymbolOrEmoji(): Boolean {
    if (this == '☺' || this == '☻' || this == '✨' || this == '⭐' || this == '★' || this == '☆') {
        return true
    }
    val type = Character.getType(this)
    return type == Character.SURROGATE.toInt() ||
        type == Character.OTHER_SYMBOL.toInt() ||
        type == Character.NON_SPACING_MARK.toInt()
}
