package com.sonusid.ollama.ui.model

data class InferenceStats(
    val model: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
    val tokensPerSecond: Double? = null,
    val inferenceTimeSec: Double? = null,
    val modelLabel: String? = null,
    val deviceLabel: String? = null,
    val completionTokens: Int? = null,
    val generationTimeMs: Long? = null,
    val evalDurationNs: Long? = null,
)
