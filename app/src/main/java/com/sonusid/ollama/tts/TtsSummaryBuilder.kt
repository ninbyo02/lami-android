package com.sonusid.ollama.tts

object TtsSummaryBuilder {
    private const val DEFAULT_INTRO = "結論からお伝えしますね。"
    private const val LIST_INTRO = "順番に説明しますね。"
    private const val CODE_INTRO = "コード例があります。ポイントをお話ししますね。"

    private const val LONG_FORM_MIN_SENTENCE_COUNT = 3
    private const val LONG_FORM_MIN_LENGTH = 70

    private val sentenceDelimiterRegex = Regex("[。！？!?]+")
    private val fencedCodeRegex = Regex("```[\\s\\S]*?```")
    private val listLineRegex = Regex("(?m)^\\s*(?:[-*•]|\\d+\\.)\\s+")

    fun build(rawDisplayText: String, speechText: String, isError: Boolean = false): String {
        val normalizedSpeechText = speechText.trim()
        if (normalizedSpeechText.isEmpty()) {
            return ""
        }

        if (containsFencedCode(rawDisplayText)) {
            return "$CODE_INTRO $normalizedSpeechText"
        }

        if (looksLikeList(rawDisplayText)) {
            return "$LIST_INTRO $normalizedSpeechText"
        }

        if (shouldAddLongFormIntro(normalizedSpeechText)) {
            return "$DEFAULT_INTRO $normalizedSpeechText"
        }

        return normalizedSpeechText
    }

    private fun shouldAddLongFormIntro(speechText: String): Boolean {
        return sentenceCount(speechText) >= LONG_FORM_MIN_SENTENCE_COUNT ||
            speechText.length >= LONG_FORM_MIN_LENGTH
    }

    private fun sentenceCount(speechText: String): Int {
        return sentenceDelimiterRegex
            .split(speechText)
            .map { it.trim() }
            .count { it.isNotEmpty() }
    }

    private fun containsFencedCode(rawDisplayText: String): Boolean {
        return fencedCodeRegex.containsMatchIn(rawDisplayText)
    }

    private fun looksLikeList(rawDisplayText: String): Boolean {
        return listLineRegex.containsMatchIn(rawDisplayText)
    }
}
