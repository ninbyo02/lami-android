package io.github.ninbyo02.lami.ui.util

import io.github.ninbyo02.lami.ui.model.InferenceStats
import java.text.NumberFormat
import java.util.Locale

private fun resolveOutputTokens(stats: InferenceStats): Int? =
    stats.outputTokens ?: stats.completionTokens

private fun resolveModelName(stats: InferenceStats): String? =
    stats.modelName?.takeIf { it.isNotBlank() }
        ?: stats.model?.takeIf { it.isNotBlank() }
        ?: stats.modelLabel?.takeIf { it.isNotBlank() }

private fun formatDurationNsAsSeconds(durationNs: Long?): String? {
    val safeDurationNs = durationNs ?: return null
    if (safeDurationNs < 0L) return null
    val seconds = safeDurationNs / 1_000_000_000.0
    if (seconds > 0.0 && seconds < 0.1) return "<0.1 s"
    return String.format(Locale.US, "%.1f s", seconds)
}

fun formatTokenPerSec(stats: InferenceStats): String? {
    val tokensPerSec = stats.tokensPerSecond ?: run {
        val tokens = resolveOutputTokens(stats) ?: return null
        val evalDurationNs = stats.evalDurationNs ?: return null
        if (tokens < 0 || evalDurationNs <= 0L) return null
        val seconds = evalDurationNs / 1_000_000_000.0
        if (seconds <= 0.0) return null
        tokens / seconds
    }
    if (!tokensPerSec.isFinite() || tokensPerSec < 0.0) return null
    return String.format(Locale.US, "⚡%.1f token/s", tokensPerSec)
}

fun formatGenerationTime(stats: InferenceStats): String? {
    val generationTimeMs = stats.generationTimeMs ?: return null
    if (generationTimeMs < 0L) return null
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


fun formatTimeToFirstToken(stats: InferenceStats): String? {
    val latencyMs = stats.timeToFirstTokenMs ?: return null
    if (latencyMs < 0L) return null
    return if (latencyMs < 1_000L) {
        "$latencyMs ms"
    } else {
        String.format(Locale.US, "%.1f s", latencyMs / 1000.0)
    }
}

fun formatInferenceTime(stats: InferenceStats): String? {
    val seconds = stats.inferenceTimeSec ?: return formatGenerationTime(stats)?.replace("s", " s")
    if (!seconds.isFinite() || seconds < 0.0) return null
    if (seconds > 0.0 && seconds < 0.1) return "<0.1 s"
    return String.format(Locale.US, "%.1f s", seconds)
}

fun formatModelLoadDuration(stats: InferenceStats): String? =
    formatDurationNsAsSeconds(stats.modelLoadDurationNs)

fun formatPromptEvalDuration(stats: InferenceStats): String? =
    formatDurationNsAsSeconds(stats.promptEvalDurationNs)

fun formatGenerationDuration(stats: InferenceStats): String? {
    val generationDurationNs = stats.generationDurationNs
    val evalDurationNs = stats.evalDurationNs
    val durationNs = when {
        generationDurationNs != null && generationDurationNs >= 0L -> generationDurationNs
        evalDurationNs != null && evalDurationNs >= 0L -> evalDurationNs
        else -> null
    }
    return formatDurationNsAsSeconds(durationNs)
}

fun formatOutputTokens(stats: InferenceStats): String? {
    val outputTokens = resolveOutputTokens(stats) ?: return null
    if (outputTokens < 0) return null
    return NumberFormat.getIntegerInstance(Locale.US).format(outputTokens)
}

// モデル名の優先順位: modelName(正規) -> model(移行用) -> modelLabel(移行用)
fun formatModelName(stats: InferenceStats): String? = resolveModelName(stats)

fun formatTotalTokens(stats: InferenceStats): String? {
    val total = stats.totalTokens ?: run {
        val input = stats.inputTokens
        val output = resolveOutputTokens(stats)
        if (input != null && output != null) input + output else null
    } ?: return null
    if (total < 0) return null
    return NumberFormat.getIntegerInstance(Locale.US).format(total)
}

// 旧関数名の互換。既存呼び出しは段階的に formatOutputTokens / formatModelName へ移行する。
fun formatCompletionTokens(stats: InferenceStats): String? = formatOutputTokens(stats)
fun formatModelLabel(stats: InferenceStats): String? = formatModelName(stats)


fun formatFinishReason(stats: InferenceStats): String? {
    val raw = stats.finishReason?.trim()?.lowercase() ?: return null
    if (raw.isBlank()) return null

    val label = when (raw) {
        "stop" -> "通常終了"
        "length" -> "トークン上限"
        "content_filter" -> "フィルター停止"
        "error" -> "エラー終了"
        "cancelled" -> "ユーザー停止"
        else -> return raw
    }

    return "$label ($raw)"
}

fun formatImageInputCount(stats: InferenceStats): String? {
    val count = stats.imageInputCount ?: return null
    if (count < 0) return null
    return "${count}枚"
}
