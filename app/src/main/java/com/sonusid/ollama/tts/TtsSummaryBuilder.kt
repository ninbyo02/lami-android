package com.sonusid.ollama.tts

object TtsSummaryBuilder {
    private const val DEFAULT_INTRO = "結論からお伝えしますね。"
    private const val LIST_INTRO = "順番に説明しますね。"
    private const val CODE_INTRO = "コード例があります。ポイントをお話ししますね。"
    private const val ERROR_INTRO = "状況を確認しますね。"

    private const val SHORT_TEXT_MAX_LENGTH = 30
    private val sentenceDelimiterRegex = Regex("[。！？!?]+")
    private val fencedCodeRegex = Regex("```[\\s\\S]*?```")
    private val listLineRegex = Regex("(?m)^\\s*(?:[-*•]|\\d+\\.)\\s+")

    fun build(rawDisplayText: String, speechText: String, isError: Boolean = false): String {
        val normalizedSpeechText = speechText.trim()
        if (normalizedSpeechText.isEmpty()) {
            return ""
        }

        if (shouldKeepAsIs(normalizedSpeechText)) {
            return normalizedSpeechText
        }

        val intro = when {
            isError -> ERROR_INTRO
            containsFencedCode(rawDisplayText) -> CODE_INTRO
            looksLikeList(rawDisplayText) -> LIST_INTRO
            else -> DEFAULT_INTRO
        }

        return "$intro $normalizedSpeechText"
    }

    private fun shouldKeepAsIs(speechText: String): Boolean {
        if (speechText.length > SHORT_TEXT_MAX_LENGTH) {
            return false
        }

        val sentenceCount = sentenceDelimiterRegex
            .split(speechText)
            .map { it.trim() }
            .count { it.isNotEmpty() }

        return sentenceCount <= 1
    }

    private fun containsFencedCode(rawDisplayText: String): Boolean {
        return fencedCodeRegex.containsMatchIn(rawDisplayText)
    }

    private fun looksLikeList(rawDisplayText: String): Boolean {
        return listLineRegex.containsMatchIn(rawDisplayText)
    }
}
