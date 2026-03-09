package com.sonusid.ollama.ui.model

data class InferenceStats(
    val modelLabel: String? = null,
    val deviceLabel: String? = null,
    val completionTokens: Int? = null,
    val generationTimeMs: Long? = null,
    val evalDurationNs: Long? = null,
)
