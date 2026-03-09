package com.sonusid.ollama.tts

object TtsSummaryBuilder {
    fun build(rawDisplayText: String, speechText: String, isError: Boolean = false): String {
        val normalizedSpeechText = speechText.trim()
        if (normalizedSpeechText.isEmpty()) {
            return ""
        }
        return normalizedSpeechText
    }
}
