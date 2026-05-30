package io.github.ninbyo02.lami.ui.screens.home

internal class NpuStandardRouteS5TtsBridge(
    private val mapper: NpuStandardRouteS5TtsMapper = NpuStandardRouteS5TtsMapper,
) {
    fun prepareTtsCandidate(
        s1Result: NpuStandardRouteS1Result,
        finalAssistantText: String,
        ttsEnabled: Boolean,
        streamingActive: Boolean = false,
        sanitizeForTts: (String) -> String = { it.trim() },
    ): NpuStandardRouteS5TtsMapping =
        mapper.map(
            s1Result = s1Result,
            finalAssistantText = finalAssistantText,
            ttsEnabled = ttsEnabled,
            streamingActive = streamingActive,
            sanitizeForTts = sanitizeForTts,
        )
}
