package com.sonusid.ollama.ui.util

import com.sonusid.ollama.ui.model.InferenceStats
import java.util.Locale
import kotlin.math.roundToInt

fun formatTokenPerSec(stats: InferenceStats): String? {
    val tokens = stats.completionTokens ?: return null
    val generationTimeMs = stats.generationTimeMs ?: return null
    if (tokens <= 0 || generationTimeMs <= 0L) return null
    val tokensPerSec = (tokens * 1000.0) / generationTimeMs.toDouble()
    return String.format(Locale.US, "⚡%d token/s", tokensPerSec.roundToInt())
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
