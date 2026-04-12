package io.github.ninbyo02.lami.ui.screens.home

import java.util.Locale

internal enum class StatsValueSource {
    MEASURED,
    DERIVED,
    API_CANDIDATE_ONLY,
    UNAVAILABLE,
}

internal data class ResolvedLongStat(
    val value: Long?,
    val source: StatsValueSource,
)

internal data class ResolvedIntStat(
    val value: Int?,
    val source: StatsValueSource,
)

internal data class LocalInferenceResolvedStats(
    val firstTokenMs: ResolvedLongStat,
    val generationDurationNs: ResolvedLongStat,
    val totalDurationNs: ResolvedLongStat,
    val evalDurationNs: ResolvedLongStat,
    val promptEvalDurationNs: ResolvedLongStat,
    val outputTokens: ResolvedIntStat,
    val totalTokens: ResolvedIntStat,
)

internal fun resolveLocalInferenceStats(trace: LocalInferenceTrace): LocalInferenceResolvedStats {
    val usesOfficialApi = trace.officialFlowUsed || trace.officialConversationApiAvailable == true
    val startElapsedMs = trace.localTraceStartElapsedRealtimeMs
    val firstResponseElapsedMs = trace.localTraceFirstResponseElapsedRealtimeMs
    val completedElapsedMs = trace.localTraceCompletedElapsedRealtimeMs
    val derivedFirstTokenMs = deriveElapsedDurationMsOrNull(startElapsedMs, firstResponseElapsedMs)
    val derivedGenerationNs = deriveElapsedDurationNsOrNull(firstResponseElapsedMs, completedElapsedMs)
    val derivedTotalNs = deriveElapsedDurationNsOrNull(startElapsedMs, completedElapsedMs)

    val firstTokenMs = trace.firstTokenProbe.longValueOrNull()?.takeIf { it >= 0L }?.let {
        ResolvedLongStat(value = it, source = StatsValueSource.MEASURED)
    } ?: run {
        if (derivedFirstTokenMs != null) {
            ResolvedLongStat(value = derivedFirstTokenMs, source = StatsValueSource.DERIVED)
        } else {
            ResolvedLongStat(
                value = null,
                source = resolveMissingValueSource(trace.firstTokenProbe.availability, usesOfficialApi),
            )
        }
    }

    val measuredEvalDurationNs = trace.evalTimeProbe.durationNsOrNull()?.takeIf { it >= 0L }
    val measuredPromptEvalDurationNs = trace.promptEvalTimeProbe.durationNsOrNull()?.takeIf { it >= 0L }

    val generationDurationNs = measuredEvalDurationNs?.let {
        ResolvedLongStat(value = it, source = StatsValueSource.MEASURED)
    } ?: run {
        if (derivedGenerationNs != null) {
            ResolvedLongStat(value = derivedGenerationNs, source = StatsValueSource.DERIVED)
        } else {
            ResolvedLongStat(
                value = null,
                source = resolveMissingValueSource(trace.evalTimeProbe.availability, usesOfficialApi),
            )
        }
    }

    val totalDurationNs = run {
        if (derivedTotalNs != null) {
            // total = completed - start
            ResolvedLongStat(value = derivedTotalNs, source = StatsValueSource.DERIVED)
        } else {
            trace.wallClockTotalInferenceDurationNs?.takeIf { it >= 0L }?.let {
                ResolvedLongStat(value = it, source = StatsValueSource.DERIVED)
            } ?: ResolvedLongStat(value = null, source = StatsValueSource.UNAVAILABLE)
        }
    }

    val evalDurationNs = if (measuredEvalDurationNs != null) {
        // eval は API/probe が返す純粋な generation 相当時間として扱う。
        ResolvedLongStat(value = measuredEvalDurationNs, source = StatsValueSource.MEASURED)
    } else if (totalDurationNs.value != null) {
        // eval 取得不可時のみ total を代替値に固定する。
        ResolvedLongStat(value = totalDurationNs.value, source = StatsValueSource.DERIVED)
    } else {
        ResolvedLongStat(
            value = null,
            source = resolveMissingValueSource(trace.evalTimeProbe.availability, usesOfficialApi),
        )
    }

    val promptEvalDurationNs = measuredPromptEvalDurationNs?.let {
        // promptEval は API/probe の取得値を優先し、未取得時は total - generation を使う。
        ResolvedLongStat(value = it, source = StatsValueSource.MEASURED)
    } ?: run {
        val totalNs = totalDurationNs.value
        val generationNs = generationDurationNs.value
        if (totalNs != null && generationNs != null) {
            ResolvedLongStat(value = (totalNs - generationNs).coerceAtLeast(0L), source = StatsValueSource.DERIVED)
        } else {
            ResolvedLongStat(
                value = null,
                source = resolveMissingValueSource(trace.promptEvalTimeProbe.availability, usesOfficialApi),
            )
        }
    }

    val outputTokens = trace.outputTokenProbe.intValueOrNull()?.takeIf { it >= 0 }?.let {
        ResolvedIntStat(value = it, source = StatsValueSource.MEASURED)
    } ?: trace.sessionResponseTokens?.takeIf { it >= 0 }?.let {
        ResolvedIntStat(value = it, source = StatsValueSource.DERIVED)
    } ?: ResolvedIntStat(
        value = null,
        source = resolveMissingValueSource(trace.outputTokenProbe.availability, usesOfficialApi),
    )

    val totalTokens = trace.estimatedTokenProbe.intValueOrNull()?.takeIf { it >= 0 }?.let {
        ResolvedIntStat(value = it, source = StatsValueSource.MEASURED)
    } ?: trace.sessionTotalTokens?.takeIf { it >= 0 }?.let {
        ResolvedIntStat(value = it, source = StatsValueSource.DERIVED)
    } ?: ResolvedIntStat(
        value = null,
        source = resolveMissingValueSource(trace.estimatedTokenProbe.availability, usesOfficialApi),
    )

    return LocalInferenceResolvedStats(
        firstTokenMs = firstTokenMs,
        generationDurationNs = generationDurationNs,
        totalDurationNs = totalDurationNs,
        evalDurationNs = evalDurationNs,
        promptEvalDurationNs = promptEvalDurationNs,
        outputTokens = outputTokens,
        totalTokens = totalTokens,
    )
}

private fun resolveMissingValueSource(
    availability: LocalStatsAvailability,
    usesOfficialApi: Boolean,
): StatsValueSource {
    return when {
        availability == LocalStatsAvailability.API_CANDIDATE_ONLY || usesOfficialApi -> StatsValueSource.API_CANDIDATE_ONLY
        else -> StatsValueSource.UNAVAILABLE
    }
}

private fun deriveElapsedDurationMsOrNull(
    startElapsedRealtimeMs: Long?,
    endElapsedRealtimeMs: Long?,
): Long? {
    if (startElapsedRealtimeMs == null || endElapsedRealtimeMs == null) return null
    if (endElapsedRealtimeMs < startElapsedRealtimeMs) return null
    return endElapsedRealtimeMs - startElapsedRealtimeMs
}

private fun deriveElapsedDurationNsOrNull(
    startElapsedRealtimeMs: Long?,
    endElapsedRealtimeMs: Long?,
): Long? = deriveElapsedDurationMsOrNull(startElapsedRealtimeMs, endElapsedRealtimeMs)?.times(1_000_000L)

internal fun LocalStatsCandidateProbe.intValueOrNull(): Int? {
    if (availability == LocalStatsAvailability.NOT_FOUND) return null
    return valueSummary?.toIntOrNull()
}

internal fun LocalStatsCandidateProbe.longValueOrNull(): Long? {
    if (availability == LocalStatsAvailability.NOT_FOUND) return null
    return valueSummary?.toLongOrNull()
}

internal fun LocalStatsCandidateProbe.durationNsOrNull(): Long? {
    val rawValue = longValueOrNull() ?: return null
    if (rawValue < 0L) return null
    val signatureLower = signature?.lowercase(Locale.ROOT).orEmpty()
    val isMillisValue =
        signatureLower.contains("timems") ||
            signatureLower.contains("durationms") ||
            signatureLower.contains("millis") ||
            signatureLower.contains("milliseconds")
    return if (isMillisValue) rawValue * 1_000_000L else rawValue
}
