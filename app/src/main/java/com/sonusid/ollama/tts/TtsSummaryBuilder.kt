package com.sonusid.ollama.tts

object TtsSummaryBuilder {
    private const val LIST_INTRO = "順番に説明しますね。"

    private val listLineRegex = Regex("(?m)^\\s*(?:[-*•]|\\d+\\.)\\s+")

    fun build(rawDisplayText: String, speechText: String, isError: Boolean = false): String {
        val normalizedSpeechText = speechText.trim()
        if (normalizedSpeechText.isEmpty()) {
            return ""
        }

        if (looksLikeList(rawDisplayText)) {
            return "$LIST_INTRO $normalizedSpeechText"
        }

        return normalizedSpeechText
    }

    private fun looksLikeList(rawDisplayText: String): Boolean {
        return listLineRegex.containsMatchIn(rawDisplayText)
    }
}
