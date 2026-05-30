package io.github.ninbyo02.lami.ui.screens.home

internal object NpuStandardRouteS5TtsMapper {
    fun map(
        s1Result: NpuStandardRouteS1Result,
        finalAssistantText: String,
        ttsEnabled: Boolean,
        streamingActive: Boolean = false,
        sanitizeForTts: (String) -> String = { it.trim() },
    ): NpuStandardRouteS5TtsMapping {
        if (!s1Result.successCriteriaMet) {
            return NpuStandardRouteS5TtsMapping(
                ttsCandidate = null,
                failureReason = NpuStandardRouteS5TtsContract.FAILURE_S1_NOT_SUCCESS,
            )
        }
        if (!ttsEnabled) {
            return NpuStandardRouteS5TtsMapping(
                ttsCandidate = null,
                failureReason = NpuStandardRouteS5TtsContract.FAILURE_TTS_DISABLED,
            )
        }
        if (streamingActive) {
            return NpuStandardRouteS5TtsMapping(
                ttsCandidate = null,
                failureReason = NpuStandardRouteS5TtsContract.FAILURE_STREAMING_ACTIVE,
            )
        }

        val normalizedFinalText = finalAssistantText.trim()
        if (normalizedFinalText.isBlank()) {
            return NpuStandardRouteS5TtsMapping(
                ttsCandidate = null,
                failureReason = NpuStandardRouteS5TtsContract.FAILURE_EMPTY_TEXT,
            )
        }

        val speakText = sanitizeForTts(normalizedFinalText).trim()
        if (speakText.isBlank()) {
            return NpuStandardRouteS5TtsMapping(
                ttsCandidate = null,
                failureReason = NpuStandardRouteS5TtsContract.FAILURE_EMPTY_SPEAK_TEXT,
            )
        }

        return NpuStandardRouteS5TtsMapping(
            ttsCandidate = NpuStandardRouteS5TtsCandidate(
                finalAssistantText = normalizedFinalText,
                speakText = speakText,
            ),
        )
    }
}
