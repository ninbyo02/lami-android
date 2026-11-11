package io.github.ninbyo02.lami.tts

enum class TtsEngineChoice(val storedValue: String, val label: String, val description: String) {
    ANDROID("android", "Android標準TTS", "端末に設定された音声を使います"),
    LAMI_QWEN3("lami_qwen3", "LAMI 可愛いTTS（Qwen3-TTS）", "Qwen3-TTSベースのLAMI専用音声を使います"),
    SUPERTONIC3("supertonic3", "Supertonic 3 TTS", "端末内のSupertonic 3モデルで読み上げます"),
    KOKORO("kokoro", "Kokoro TTS（試験）", "ゼロから学習した日本語音声です。一般語の一部に読み違いが残ります"),
    ;

    companion object {
        fun fromStoredValue(value: String?): TtsEngineChoice = entries.firstOrNull { it.storedValue == value } ?: ANDROID
    }
}

data class TtsEngineAvailability(val engine: TtsEngineChoice, val available: Boolean, val message: String)

data class TtsPreviewResult(
    val engine: TtsEngineChoice,
    val text: String,
    val playbackStartMs: Long?,
    val totalMs: Long?,
    val error: String?,
    val firstPcmMs: Long? = null,
    val generationCompletedMs: Long? = null,
    val audioChunks: Int = 0,
    val streamedBeforeGenerationCompleted: Boolean = false,
    val underruns: Int? = null,
)

data class TtsPreviewState(
    val availability: Map<TtsEngineChoice, TtsEngineAvailability> = emptyMap(),
    val busy: Boolean = false,
    val stopping: Boolean = false,
    val activeEngine: TtsEngineChoice? = null,
    val results: List<TtsPreviewResult> = emptyList(),
    val error: String? = null,
)
