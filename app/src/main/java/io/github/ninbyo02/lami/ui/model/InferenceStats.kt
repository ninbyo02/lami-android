package io.github.ninbyo02.lami.ui.model

enum class ContextWindowFetchState {
    LOADING,
    AVAILABLE,
    UNAVAILABLE,
}

data class InferenceStats(
    // UI で扱う正規のモデル名。
    val modelName: String? = null,
    val inputTokens: Int? = null,
    // UI/表示責務としての正規出力トークン。
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
    val tokensPerSecond: Double? = null,
    val charsPerSecond: Double? = null,
    val tokenCountMode: String? = null,
    val notes: String? = null,
    // 秒単位の表示向け導出値（DB 保存値を優先）。
    val inferenceTimeSec: Double? = null,
    // 生値（ミリ秒）。表示は formatter 側で秒に整形する。
    val generationTimeMs: Long? = null,
    val decodeDurationMs: Long? = null,
    val totalDurationMs: Long? = null,
    // Ollama usage の load_duration (ns)。
    val modelLoadDurationNs: Long? = null,
    // Ollama usage の prompt_eval_duration (ns)。
    val promptEvalDurationNs: Long? = null,
    // Ollama usage の eval_duration (ns)。
    val generationDurationNs: Long? = null,
    val evalDurationNs: Long? = null,
    // finalChunk の doneReason / finishReason を保存して表示する。
    val finishReason: String? = null,
    val localSourceSummary: String? = null,
    val timeToFirstTokenMs: Long? = null,
    // 添付画像の枚数。入力トークンとは別指標として扱う。
    val imageInputCount: Int? = null,
    val contextTokensUsed: Int? = null,
    val contextWindow: Int? = null,
    val contextUsageRatio: Double? = null,
    val contextWindowFetchState: ContextWindowFetchState = ContextWindowFetchState.UNAVAILABLE,
    // 旧命名互換。mapper / formatter 内でのみ吸収し、徐々に縮退する。
    val model: String? = null,
    val modelLabel: String? = null,
    val completionTokens: Int? = null,
    val deviceLabel: String? = null,
    val responseCharCount: Int? = null,
    // ストリーミング時に UI へ反映した assistant 部分更新回数（端末側計測）。
    val assistantUpdateCount: Int? = null,
)
