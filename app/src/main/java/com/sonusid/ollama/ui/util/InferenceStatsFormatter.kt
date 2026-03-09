package com.sonusid.ollama.ui.util

import com.sonusid.ollama.ui.model.InferenceStats
import java.util.Locale

fun formatTokenPerSec(stats: InferenceStats): String? {
    val tokens = stats.completionTokens ?: return null
    val evalDurationNs = stats.evalDurationNs ?: return null
    if (tokens <= 0 || evalDurationNs <= 0L) return null
    val seconds = evalDurationNs / 1_000_000_000.0
    if (seconds <= 0.0) return null
    val tokensPerSec = tokens / seconds
    if (!tokensPerSec.isFinite()) return null
    return String.format(Locale.US, "⚡%.1f token/s", tokensPerSec)
}

fun formatGenerationTime(stats: InferenceStats): String? {
    val generationTimeMs = stats.generationTimeMs ?: return null
    if (generationTimeMs <= 0L) return null
    val seconds = generationTimeMs / 1000.0
    return String.format(Locale.US, "%.1fs", seconds)
}

fun buildInferenceSummary(stats: InferenceStats): String? {
    val tokenPerSec = formatTokenPerSec(stats)
    val generationTime = formatGenerationTime(stats)
    return when {
        tokenPerSec != null && generationTime != null -> "$tokenPerSec · $generationTime"
        generationTime != null -> generationTime
        tokenPerSec != null -> tokenPerSec
        else -> null
    }
}
