package io.github.ninbyo02.lami.ui.screens.home

internal fun sanitizeLocalResponseText(raw: String): String {
    val normalized = raw
        .replace("<end_of_turn>", "")
        .replace("\r\n", "\n")
    val compactBlankLines = normalized.replace(Regex("\n{3,}"), "\n\n")
    val cleanedLines = buildList {
        var previous: String? = null
        compactBlankLines.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed == previous && trimmed.isNotEmpty()) return@forEach
            add(trimmed)
            previous = trimmed
        }
    }
    val sanitized = cleanedLines.joinToString("\n").trim()
    return sanitized.ifEmpty { raw.trim() }
}

internal fun sanitizeDebugTraceHead(raw: String?): String? {
    if (raw == null) return null
    val sanitized = sanitizeLocalResponseText(raw)
    val base = if (sanitized.isNotEmpty()) sanitized else raw.trim()
    return base.take(80)
}

internal fun sanitizeOneShotShortAnswerResponse(prompt: String, raw: String): String {
    val shortAnswerKeywords = listOf(
        "短く", "短文", "一言", "最短", "簡潔", "短く答えて", "短く回答",
        "答えだけ", "回答だけ", "一語", "一行", "すぐ答えて", "端的に",
        "簡単に", "シンプルに", "手短に",
    )
    if (!shortAnswerKeywords.any { prompt.contains(it) }) return raw

    return runCatching {
        val normalized = sanitizeLocalResponseText(raw)
        val segments = normalized
            .split("。", "!", "！", "?", "？", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (segments.isEmpty()) return@runCatching raw

        val shortDirectAnswer = segments.firstOrNull { candidate ->
            Regex("^\\d+(です)?。?$").matches(candidate)
        }
        if (shortDirectAnswer != null) return@runCatching shortDirectAnswer

        val emojiRegex = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]")
        val sanitized = segments.firstOrNull { candidate ->
            candidate.length <= 20 &&
                !emojiRegex.containsMatchIn(candidate) &&
                !candidate.contains("ありがとうございます") &&
                !candidate.contains("かしこまり") &&
                !candidate.contains("承知") &&
                !candidate.contains("算数") &&
                !candidate.contains("問題") &&
                !candidate.contains("ですね")
        }
        sanitized ?: raw
    }.getOrDefault(raw)
}
