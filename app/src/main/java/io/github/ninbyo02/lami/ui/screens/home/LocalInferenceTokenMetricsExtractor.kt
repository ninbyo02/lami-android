package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats

internal enum class LocalInferenceTokenMetricSource {
    MEASURED,
    DERIVED,
    ESTIMATED,
    API_CANDIDATE_ONLY,
    UNAVAILABLE,
}

internal data class LocalInferenceTokenMetric(
    val value: Int?,
    val source: LocalInferenceTokenMetricSource,
)

internal data class LocalInferenceTokenMetrics(
    val inputTokens: LocalInferenceTokenMetric,
    val outputTokens: LocalInferenceTokenMetric,
    val totalTokens: LocalInferenceTokenMetric,
)

internal data class LocalInferenceMeasuredTokenSnapshot(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
)

internal fun extractLocalInferenceTokenMetrics(
    trace: LocalInferenceTrace,
    resolved: LocalInferenceResolvedStats,
    stats: InferenceStats,
    measuredSnapshot: LocalInferenceMeasuredTokenSnapshot? = null,
): LocalInferenceTokenMetrics {
    val usesOfficialApi = trace.officialFlowUsed || trace.officialConversationApiAvailable == true
    val hasEstimatedTokenProbe = trace.estimatedTokenProbe.availability != LocalStatsAvailability.NOT_FOUND

    val inputTokens = measuredSnapshot?.inputTokens
        ?.takeIf { it >= 0 }
        ?.let { LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.MEASURED) }
        ?: stats.inputTokens
            ?.takeIf { it >= 0 }
            ?.let { LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.DERIVED) }
        ?: LocalInferenceTokenMetric(
            value = null,
            source = resolveMissingTokenMetricSource(usesOfficialApi = usesOfficialApi),
        )

    val outputTokens = measuredSnapshot?.outputTokens
        ?.takeIf { it >= 0 }
        ?.let { LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.MEASURED) }
        ?: LocalInferenceTokenMetric(
            value = resolved.outputTokens.value,
            source = resolved.outputTokens.source.toTokenMetricSource(),
        )

    val totalTokens = measuredSnapshot?.totalTokens
        ?.takeIf { it >= 0 }
        ?.let { LocalInferenceTokenMetric(value = it, source = LocalInferenceTokenMetricSource.MEASURED) }
        ?: run {
            if (
                inputTokens.value != null &&
                outputTokens.value != null &&
                inputTokens.source == LocalInferenceTokenMetricSource.MEASURED &&
                outputTokens.source == LocalInferenceTokenMetricSource.MEASURED
            ) {
                LocalInferenceTokenMetric(
                    value = inputTokens.value + outputTokens.value,
                    source = LocalInferenceTokenMetricSource.MEASURED,
                )
            } else if (resolved.totalTokens.value != null) {
                LocalInferenceTokenMetric(
                    value = resolved.totalTokens.value,
                    source = if (hasEstimatedTokenProbe) {
                        LocalInferenceTokenMetricSource.ESTIMATED
                    } else {
                        resolved.totalTokens.source.toTokenMetricSource()
                    },
                )
            } else {
                LocalInferenceTokenMetric(
                    value = null,
                    source = resolveMissingTokenMetricSource(usesOfficialApi = usesOfficialApi),
                )
            }
        }

    return LocalInferenceTokenMetrics(
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = totalTokens,
    )
}

private fun StatsValueSource.toTokenMetricSource(): LocalInferenceTokenMetricSource = when (this) {
    StatsValueSource.MEASURED -> LocalInferenceTokenMetricSource.MEASURED
    StatsValueSource.DERIVED -> LocalInferenceTokenMetricSource.DERIVED
    StatsValueSource.API_CANDIDATE_ONLY -> LocalInferenceTokenMetricSource.API_CANDIDATE_ONLY
    StatsValueSource.UNAVAILABLE -> LocalInferenceTokenMetricSource.UNAVAILABLE
}

private fun resolveMissingTokenMetricSource(usesOfficialApi: Boolean): LocalInferenceTokenMetricSource {
    return if (usesOfficialApi) {
        LocalInferenceTokenMetricSource.API_CANDIDATE_ONLY
    } else {
        LocalInferenceTokenMetricSource.UNAVAILABLE
    }
}
