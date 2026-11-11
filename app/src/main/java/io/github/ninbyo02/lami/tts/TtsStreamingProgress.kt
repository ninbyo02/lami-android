package io.github.ninbyo02.lami.tts

/** Monotonic source timestamps allow generation/playback overlap to be verified. */
enum class TtsStreamEvent {
    GENERATION_STARTED,
    PCM_CHUNK,
    GENERATION_COMPLETED,
    PLAYBACK_STARTED,
    PLAYBACK_COMPLETED,
}

data class TtsStreamingProgress(
    val event: TtsStreamEvent,
    val sampleCount: Int = 0,
    val timestampNs: Long = 0,
    val underruns: Int = 0,
)
