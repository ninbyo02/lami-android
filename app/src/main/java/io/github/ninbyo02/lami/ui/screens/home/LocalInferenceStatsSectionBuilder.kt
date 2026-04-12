package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.util.formatFinishReason
import io.github.ninbyo02.lami.ui.util.formatImageInputCount
import io.github.ninbyo02.lami.ui.util.formatInferenceTime
import io.github.ninbyo02.lami.ui.util.formatModelLoadDuration
import io.github.ninbyo02.lami.ui.util.formatOutputTokens
import io.github.ninbyo02.lami.ui.util.formatPromptEvalDuration
import io.github.ninbyo02.lami.ui.util.formatTimeToFirstToken
import io.github.ninbyo02.lami.ui.util.formatTokenPerSec
import io.github.ninbyo02.lami.ui.util.formatTotalTokens
import java.util.Locale

internal fun buildInferenceSummarySections(
    stats: InferenceStats,
    localTraceForDev: LocalInferenceTrace? = null,
    enableDevLlmSessionAsyncPoc: Boolean = false,
): List<InferenceStatsSectionUi> {
    val isLocalMinimal = isLocalMinimalInferenceStats(stats)
    val localSourceSummaryText = stats.localSourceSummary
        ?.takeIf { it.isNotBlank() }
        ?: localTraceForDev?.let { buildLocalSourceSummaryText(trace = it, stats = stats) }
    val summaryItems = if (isLocalMinimal) {
        buildList {
            add(InferenceStatItemUi(label = "応答時間", value = formatInferenceTime(stats) ?: "—"))
            add(InferenceStatItemUi(label = "応答文字数", value = stats.responseCharCount?.toString() ?: "—"))
            if (localSourceSummaryText != null) {
                add(InferenceStatItemUi(label = "採用元", value = localSourceSummaryText))
            }
        }
    } else {
        buildList {
            add(InferenceStatItemUi(label = "初回受信まで（端末基準）", value = formatTimeToFirstToken(stats) ?: "—"))
            add(InferenceStatItemUi(label = "全体完了まで（統計基準）", value = formatInferenceTime(stats) ?: "—"))
            add(
                InferenceStatItemUi(
                    label = "生成速度",
                    value = formatTokenPerSec(stats)?.removePrefix("⚡")?.trim() ?: "—",
                    emphasizeValue = true,
                )
            )
            add(InferenceStatItemUi(label = "完了理由", value = formatFinishReason(stats) ?: "—"))

            if (localSourceSummaryText != null) {
                add(InferenceStatItemUi(label = "採用元", value = localSourceSummaryText))
            }
        }
    }
    val summarySection = InferenceStatsSectionUi(
        title = "概要",
        items = summaryItems,
    )
    val localInventorySection = buildLocalInventorySectionForDev(
        isLocalMinimal = isLocalMinimal,
        trace = localTraceForDev,
        stats = stats,
        enableDevLlmSessionAsyncPoc = enableDevLlmSessionAsyncPoc,
    )
    return listOfNotNull(summarySection, localInventorySection)
}

internal fun buildInferenceDetailSections(
    stats: InferenceStats,
    localTraceForDev: LocalInferenceTrace? = null,
    devHeldStateText: String? = null,
    devCloseLifecycleText: String? = null,
    devDebugText: String? = null,
    enableDevLlmSessionAsyncPoc: Boolean = false,
): List<InferenceStatsSectionUi> {
    val hasRealGenerationDuration = stats.generationDurationNs?.let { it > 0L } == true
    val localStatsUiModel = localTraceForDev?.let { createLocalInferenceStatsUiModel(trace = it, stats = stats) }
    val devSectionItems = buildList {
        devHeldStateText?.takeIf { it.isNotBlank() }?.let {
            add(InferenceStatItemUi(label = "Held Engine State", value = it))
        }
        devCloseLifecycleText?.takeIf { it.isNotBlank() }?.let {
            add(InferenceStatItemUi(label = "Close Lifecycle", value = it))
        }
        devDebugText?.takeIf { it.isNotBlank() }?.let {
            add(InferenceStatItemUi(label = "Failure / Debug", value = it))
        }
    }
    val devDiagnosticSummarySection = buildDevDiagnosticSummarySection(
        stats = stats,
        trace = localTraceForDev,
        devHeldStateText = devHeldStateText,
        devCloseLifecycleText = devCloseLifecycleText,
        devDebugText = devDebugText,
    )

    return listOfNotNull(
        devDiagnosticSummarySection,
        InferenceStatsSectionUi(
            title = "トークン",
            items = listOf(
                InferenceStatItemUi(
                    label = "入力トークン",
                    value = withProbeStateLabel(
                        value = localStatsUiModel?.tokens?.inputTokens?.valueText ?: stats.inputTokens?.toString(),
                        state = localStatsUiModel?.tokens?.inputTokens?.source?.toUiStateLabel() ?: "未取得",
                    ),
                ),
                InferenceStatItemUi(
                    label = "生成トークン",
                    value = withProbeStateLabel(
                        value = localStatsUiModel?.tokens?.outputTokens?.valueText ?: formatOutputTokens(stats),
                        state = localStatsUiModel?.tokens?.outputTokens?.source?.toUiStateLabel() ?: "未取得",
                    ),
                ),
                InferenceStatItemUi(
                    label = "合計トークン",
                    value = withProbeStateLabel(
                        value = localStatsUiModel?.tokens?.totalTokens?.valueText ?: formatTotalTokens(stats),
                        state = localStatsUiModel?.tokens?.totalTokens?.source?.toUiStateLabel() ?: "未取得",
                    ),
                ),
            ),
        ),
        InferenceStatsSectionUi(
            title = "バックエンド時間詳細",
            items = listOfNotNull(
                InferenceStatItemUi(
                    label = "モデルロード時間",
                    value = withProbeStateLabel(
                        value = localStatsUiModel?.modelLoadTime?.valueText ?: formatModelLoadDuration(stats),
                        state = localStatsUiModel?.modelLoadTime?.source?.toUiStateLabel()
                            ?: if (stats.modelLoadDurationNs != null) "取得済み" else "未取得",
                    ),
                ),
                InferenceStatItemUi(
                    label = "入力評価時間",
                    value = withProbeStateLabel(
                        value = localStatsUiModel?.promptEvalTime?.valueText ?: formatPromptEvalDuration(stats),
                        state = localStatsUiModel?.promptEvalTime?.source?.toUiStateLabel()
                            ?: if (stats.promptEvalDurationNs != null) "取得済み" else "未取得",
                    ),
                ),
                InferenceStatItemUi(
                    label = "生成時間",
                    value = withProbeStateLabel(
                        value = localStatsUiModel?.generationTime?.valueText
                            ?: if (hasRealGenerationDuration) formatProbeDurationForUi(stats.generationDurationNs) else null,
                        state = localStatsUiModel?.generationTime?.source?.toUiStateLabel()
                            ?: if (hasRealGenerationDuration) "取得済み" else "未取得",
                    ),
                ),
                InferenceStatItemUi(
                    label = "推論時間",
                    value = withProbeStateLabel(
                        value = localStatsUiModel?.totalTime?.valueText ?: formatProbeDurationForUi(stats.evalDurationNs),
                        state = localStatsUiModel?.totalTime?.source?.toUiStateLabel()
                            ?: if (stats.evalDurationNs != null) "取得済み" else "未取得",
                    ),
                ),
            ),
        ),
        InferenceStatsSectionUi(
            title = "補足",
            items = buildList {
                add(InferenceStatItemUi(label = "画像入力", value = formatImageInputCount(stats) ?: "—"))
                if (localTraceForDev != null && enableDevLlmSessionAsyncPoc) {
                    add(InferenceStatItemUi(label = "evalTime", value = localTraceForDev.evalTimeProbe.availability.name))
                    add(InferenceStatItemUi(label = "evalTimeSignature", value = localTraceForDev.evalTimeProbe.signature ?: "—"))
                    add(InferenceStatItemUi(label = "rawEvalTime", value = localTraceForDev.evalTimeProbe.valueSummary ?: "—"))
                    add(InferenceStatItemUi(label = "outputTokens", value = localTraceForDev.outputTokenProbe.availability.name))
                    add(InferenceStatItemUi(label = "outputTokensSignature", value = localTraceForDev.outputTokenProbe.signature ?: "—"))
                    add(InferenceStatItemUi(label = "rawOutputTokens", value = localTraceForDev.outputTokenProbe.valueSummary ?: "—"))
                    add(InferenceStatItemUi(label = "estimatedTokens", value = localTraceForDev.estimatedTokenProbe.availability.name))
                    add(InferenceStatItemUi(label = "estimatedTokensSignature", value = localTraceForDev.estimatedTokenProbe.signature ?: "—"))
                    add(InferenceStatItemUi(label = "rawEstimatedTokens", value = localTraceForDev.estimatedTokenProbe.valueSummary ?: "—"))
                    add(InferenceStatItemUi(label = "firstToken", value = localTraceForDev.firstTokenProbe.availability.name))
                    add(InferenceStatItemUi(label = "firstTokenSignature", value = localTraceForDev.firstTokenProbe.signature ?: "—"))
                    add(InferenceStatItemUi(label = "rawFirstToken", value = localTraceForDev.firstTokenProbe.valueSummary ?: "—"))
                    add(InferenceStatItemUi(label = "assistantUpdateCount", value = localTraceForDev.assistantUpdateCount.toString()))
                    add(InferenceStatItemUi(label = "firstNonEmptyAssistantChunkSeen", value = localTraceForDev.firstNonEmptyAssistantChunkSeen.toString()))
                    add(InferenceStatItemUi(label = "assistantStreamedToUi", value = localTraceForDev.assistantStreamedToUi.toString()))
                    add(InferenceStatItemUi(label = "realPartialReceived", value = localTraceForDev.realPartialReceived.toString()))
                    add(InferenceStatItemUi(label = "realPartialChunkCount", value = localTraceForDev.realPartialChunkCount.toString()))
                    add(InferenceStatItemUi(label = "officialFlowAttempted", value = localTraceForDev.officialFlowAttempted.toString()))
                    add(InferenceStatItemUi(label = "officialFlowUsed", value = localTraceForDev.officialFlowUsed.toString()))
                    add(InferenceStatItemUi(label = "officialFlowFallbackReason", value = localTraceForDev.officialFlowFallbackReason ?: "—"))
                    add(InferenceStatItemUi(label = "officialConversationApiAvailable", value = localTraceForDev.officialConversationApiAvailable?.toString() ?: "—"))
                    add(InferenceStatItemUi(label = "officialFlowChunkCount", value = localTraceForDev.officialFlowChunkCount.toString()))
                }
            },
        ),
        InferenceStatsSectionUi(
            title = "DEV診断",
            items = devSectionItems,
        ).takeIf { it.items.isNotEmpty() },
    )
}

private fun isLocalMinimalInferenceStats(stats: InferenceStats): Boolean {
    return stats.generationTimeMs != null &&
        stats.evalDurationNs == null &&
        stats.outputTokens == null &&
        stats.completionTokens == null &&
        stats.finishReason == null
}

internal fun buildLocalSourceSummaryText(
    trace: LocalInferenceTrace,
    stats: InferenceStats,
): String? {
    val sourceByLabel = resolveLocalSourceItemsForDev(trace = trace, stats = stats)
        .associate { it.label to shortenLocalSourceLabelForSummary(it.value) }

    val summaryParts = listOfNotNull(
        sourceByLabel["modelNameSource"]?.let { "model:$it" },
        sourceByLabel["finishReasonSource"]?.let { "finish:$it" },
        sourceByLabel["outputTokenSource"]?.let { "out:$it" },
        sourceByLabel["evalDurationSource"]?.let { "total:$it" },
        sourceByLabel["tokensPerSecondSource"]?.let { "tps:$it" },
    )

    return summaryParts.takeIf { it.isNotEmpty() }?.joinToString(separator = " / ")
}

private fun shortenLocalSourceLabelForSummary(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "unavailable") return null
    return when {
        raw == "probe" || raw.startsWith("probe-") -> "probe"
        raw == "session" || raw.startsWith("session-") -> "session"
        raw == "trace-local-display-name" -> "trace"
        raw == "trace-finishReason-fallback" -> "fallback"
        raw == "derived-from-total-minus-first" -> "fallback"
        raw == "fallback-generationTimeMs-minus-ttft" -> "fallback"
        raw == "derived-from-eval-minus-generation" -> "fallback"
        raw == "derived-from-total-minus-generation" -> "fallback"
        raw == "self-trace-completed-minus-first" -> "trace"
        raw == "self-trace-completed-minus-start" -> "trace"
        raw == "wall-clock-total-inference" -> "trace"
        raw == "probe-eval-as-total-fallback" -> "fallback"
        raw == "fallback-generationTimeMs" -> "fallback"
        raw == "derived-from-output-and-generationTimeMs" -> "fallback"
        else -> null
    }
}

private fun resolveLocalSourceItemsForDev(
    trace: LocalInferenceTrace,
    stats: InferenceStats,
): List<InferenceStatItemUi> {
    val resolved = resolveLocalInferenceStats(trace)
    val statsUiModel = createLocalInferenceStatsUiModel(trace = trace, stats = stats)
    fun formatResolvedSource(
        source: StatsValueSource,
        detail: String,
    ): String {
        val base = when (source) {
            StatsValueSource.MEASURED -> "measured"
            StatsValueSource.DERIVED -> "derived"
            StatsValueSource.API_CANDIDATE_ONLY -> LocalStatsAvailability.API_CANDIDATE_ONLY.name
            StatsValueSource.UNAVAILABLE -> "unavailable"
        }
        return if (source == StatsValueSource.MEASURED || source == StatsValueSource.DERIVED) {
            "$base:$detail"
        } else {
            base
        }
    }
    val modelNameSource = when {
        trace.modelNameProbe.stringValueOrNull() != null -> "probe"
        !trace.localModelDisplayName.isNullOrBlank() -> "trace-local-display-name"
        else -> "unavailable"
    }
    val finishReasonSource = when {
        trace.finishReasonProbe.stringValueOrNull() != null -> "probe"
        !stats.finishReason.isNullOrBlank() -> "trace-finishReason-fallback"
        else -> "unavailable"
    }
    val outputTokenSource = statsUiModel.tokens.outputTokens.source.toDevLabel().lowercase(Locale.ROOT)
    val totalTokenSource = statsUiModel.tokens.totalTokens.source.toDevLabel().lowercase(Locale.ROOT)
    val firstTokenSource = when (resolved.firstTokenMs.source) {
        StatsValueSource.MEASURED -> formatResolvedSource(resolved.firstTokenMs.source, "probe-first-token")
        StatsValueSource.DERIVED -> formatResolvedSource(resolved.firstTokenMs.source, "self-trace-first-response")
        StatsValueSource.API_CANDIDATE_ONLY -> formatResolvedSource(resolved.firstTokenMs.source, "")
        StatsValueSource.UNAVAILABLE ->
            if (stats.timeToFirstTokenMs != null) "fallback-generationTimeMs" else "unavailable"
    }
    val generationDurationSource = when (resolved.generationDurationNs.source) {
        StatsValueSource.MEASURED -> formatResolvedSource(resolved.generationDurationNs.source, "probe-eval")
        StatsValueSource.DERIVED -> formatResolvedSource(resolved.generationDurationNs.source, "self-trace-completed-minus-first")
        StatsValueSource.API_CANDIDATE_ONLY -> formatResolvedSource(resolved.generationDurationNs.source, "")
        StatsValueSource.UNAVAILABLE ->
            if (stats.generationDurationNs != null) "fallback-generationTimeMs-minus-ttft" else "unavailable"
    }
    val evalDurationSource = when (resolved.evalDurationNs.source) {
        StatsValueSource.MEASURED -> formatResolvedSource(resolved.evalDurationNs.source, "probe-eval")
        StatsValueSource.DERIVED -> formatResolvedSource(resolved.evalDurationNs.source, "self-trace-completed-minus-start")
        StatsValueSource.API_CANDIDATE_ONLY -> formatResolvedSource(resolved.evalDurationNs.source, "")
        StatsValueSource.UNAVAILABLE -> "unavailable"
    }
    val promptEvalDurationSource = when (resolved.promptEvalDurationNs.source) {
        StatsValueSource.MEASURED -> formatResolvedSource(resolved.promptEvalDurationNs.source, "probe-prompt-eval")
        StatsValueSource.DERIVED -> formatResolvedSource(resolved.promptEvalDurationNs.source, "derived-from-total-minus-generation")
        StatsValueSource.API_CANDIDATE_ONLY -> formatResolvedSource(resolved.promptEvalDurationNs.source, "")
        StatsValueSource.UNAVAILABLE -> "unavailable"
    }
    val tokensPerSecondSource = statsUiModel.tokensPerSecond.source.toDevLabel().lowercase(Locale.ROOT)
    return listOf(
        InferenceStatItemUi(label = "modelNameSource", value = modelNameSource),
        InferenceStatItemUi(label = "finishReasonSource", value = finishReasonSource),
        InferenceStatItemUi(label = "outputTokenSource", value = outputTokenSource),
        InferenceStatItemUi(label = "totalTokenSource", value = totalTokenSource),
        InferenceStatItemUi(label = "firstTokenSource", value = firstTokenSource),
        InferenceStatItemUi(label = "generationDurationSource", value = generationDurationSource),
        InferenceStatItemUi(label = "evalDurationSource", value = evalDurationSource),
        InferenceStatItemUi(label = "promptEvalDurationSource", value = promptEvalDurationSource),
        InferenceStatItemUi(label = "tokensPerSecondSource", value = tokensPerSecondSource),
    )
}

private fun buildLocalInventorySectionForDev(
    isLocalMinimal: Boolean,
    trace: LocalInferenceTrace?,
    stats: InferenceStats,
    enableDevLlmSessionAsyncPoc: Boolean,
): InferenceStatsSectionUi? {
    if (!isLocalMinimal || trace == null) return null
    val statsUiModel = createLocalInferenceStatsUiModel(trace = trace, stats = stats)
    val rawProbeComparisonItems = listOf(
        InferenceStatItemUi(label = "rawOutputTokens", value = trace.outputTokenProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "rawEstimatedTokens", value = trace.estimatedTokenProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "rawLoadTime", value = trace.loadTimeProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "rawPromptEvalTime", value = trace.promptEvalTimeProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "rawEvalTime", value = trace.evalTimeProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "rawFirstToken", value = trace.firstTokenProbe.valueSummary ?: "—"),
        InferenceStatItemUi(label = "parsedLoadTime", value = trace.loadTimeProbe.longValueOrNull()?.toString() ?: "—"),
        InferenceStatItemUi(label = "parsedPromptEvalTime", value = trace.promptEvalTimeProbe.longValueOrNull()?.toString() ?: "—"),
        InferenceStatItemUi(label = "rawSessionPromptTokens", value = trace.sessionPromptTokens?.toString() ?: "—"),
        InferenceStatItemUi(label = "rawSessionResponseTokens", value = trace.sessionResponseTokens?.toString() ?: "—"),
        InferenceStatItemUi(label = "rawSessionTotalTokens", value = trace.sessionTotalTokens?.toString() ?: "—"),
    )
    val fallbackSourceItems = resolveLocalSourceItemsForDev(
        trace = trace,
        stats = stats,
    )
    val sessionAsyncPocDetailItems = if (enableDevLlmSessionAsyncPoc) {
        listOf(
            InferenceStatItemUi(label = "sessionAsyncPocAttempted", value = trace.sessionAsyncPocAttempted.toString()),
            InferenceStatItemUi(label = "sessionAsyncPocCreate", value = trace.sessionAsyncPocCreateSucceeded.toString()),
            InferenceStatItemUi(label = "sessionAsyncPocMethod", value = trace.sessionAsyncPocMethodSignature ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocFutureClass", value = trace.sessionAsyncPocFutureClassName ?: "—"),
            InferenceStatItemUi(
                label = "sessionAsyncPocResponseLength",
                value = trace.sessionAsyncPocResponseLength?.toString() ?: "—",
            ),
            InferenceStatItemUi(label = "sessionAsyncPocResponseHead", value = trace.sessionAsyncPocResponseHead ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocClose", value = trace.sessionAsyncPocCloseSucceeded?.toString() ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocErrorStage", value = trace.sessionAsyncPocErrorStage ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocErrorClass", value = trace.sessionAsyncPocErrorClassName ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocErrorMessage", value = trace.sessionAsyncPocErrorMessage ?: "—"),
        )
    } else {
        emptyList()
    }
    return InferenceStatsSectionUi(
        title = "LOCAL棚卸し（開発用）",
        items = listOf(
            InferenceStatItemUi(label = "modelName", value = trace.modelNameProbe.availability.name),
            InferenceStatItemUi(label = "finishReason", value = trace.finishReasonProbe.availability.name),
            InferenceStatItemUi(label = "outputTokens", value = statsUiModel.tokens.outputTokens.source.toDevLabel()),
            InferenceStatItemUi(label = "outputTokensSignature", value = trace.outputTokenProbe.signature ?: "—"),
            InferenceStatItemUi(label = "estimatedTokens", value = statsUiModel.tokens.totalTokens.source.toDevLabel()),
            InferenceStatItemUi(label = "estimatedTokensSignature", value = trace.estimatedTokenProbe.signature ?: "—"),
            InferenceStatItemUi(label = "loadTime", value = trace.loadTimeProbe.availability.name),
            InferenceStatItemUi(label = "loadTimeSignature", value = trace.loadTimeProbe.signature ?: "—"),
            InferenceStatItemUi(label = "promptEvalTime", value = trace.promptEvalTimeProbe.availability.name),
            InferenceStatItemUi(label = "promptEvalTimeSignature", value = trace.promptEvalTimeProbe.signature ?: "—"),
            InferenceStatItemUi(label = "evalTime", value = statsUiModel.totalTime.source.toDevLabel()),
            InferenceStatItemUi(label = "evalTimeSignature", value = trace.evalTimeProbe.signature ?: "—"),
            InferenceStatItemUi(label = "firstToken", value = statsUiModel.firstToken.source.toDevLabel()),
            InferenceStatItemUi(label = "firstTokenSignature", value = trace.firstTokenProbe.signature ?: "—"),
            InferenceStatItemUi(
                label = "streamingCandidate",
                value = trace.streamingCandidateDetected?.toString() ?: "—",
            ),
            InferenceStatItemUi(
                label = "streamingCandidateDetected",
                value = trace.streamingCandidateDetected?.toString() ?: "—",
            ),
            InferenceStatItemUi(
                label = "asyncApi",
                value = if (trace.asyncApiSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "asyncSignature", value = trace.asyncApiSignature ?: "—"),
            InferenceStatItemUi(
                label = "listenerApi",
                value = if (trace.listenerApiSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "listenerSignature", value = trace.listenerApiSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionApi",
                value = if (trace.sessionApiSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionSignature", value = trace.sessionApiSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionGenerateApi",
                value = if (trace.sessionGenerateSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionGenerateSignature", value = trace.sessionGenerateSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionAsyncApi",
                value = if (trace.sessionAsyncSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionAsyncSignature", value = trace.sessionAsyncSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionStreamingApi",
                value = if (trace.sessionStreamingSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionStreamingSignature", value = trace.sessionStreamingSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionTokenApi",
                value = if (trace.sessionTokenSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionTokenSignature", value = trace.sessionTokenSignature ?: "—"),
        ) + rawProbeComparisonItems + fallbackSourceItems + listOf(
            InferenceStatItemUi(label = "sessionPromptTokens", value = trace.sessionPromptTokens?.toString() ?: "—"),
            InferenceStatItemUi(label = "sessionResponseTokens", value = trace.sessionResponseTokens?.toString() ?: "—"),
            InferenceStatItemUi(label = "sessionTotalTokens", value = trace.sessionTotalTokens?.toString() ?: "—"),
            InferenceStatItemUi(label = "sessionTokenProbeErrorStage", value = trace.sessionTokenProbeErrorStage ?: "—"),
            InferenceStatItemUi(label = "sessionTokenProbeErrorClass", value = trace.sessionTokenProbeErrorClassName ?: "—"),
            InferenceStatItemUi(
                label = "sessionListenerApi",
                value = if (trace.sessionListenerSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionListenerSignature", value = trace.sessionListenerSignature ?: "—"),
            InferenceStatItemUi(
                label = "sessionLifecycleApi",
                value = if (trace.sessionLifecycleSignature != null) {
                    LocalStatsAvailability.API_CANDIDATE_ONLY.name
                } else {
                    LocalStatsAvailability.NOT_FOUND.name
                },
            ),
            InferenceStatItemUi(label = "sessionLifecycleSignature", value = trace.sessionLifecycleSignature ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocEnabled", value = enableDevLlmSessionAsyncPoc.toString()),
        ) + sessionAsyncPocDetailItems + listOf(
            InferenceStatItemUi(label = "assistantResponseSource", value = trace.selectedAssistantResponseSource ?: "—"),
            InferenceStatItemUi(label = "selectedAssistantResponseHead", value = trace.selectedAssistantResponseHead ?: "—"),
            InferenceStatItemUi(label = "oneShotResponseHead", value = trace.oneShotResponseHead ?: "—"),
            InferenceStatItemUi(label = "sessionAsyncPocCandidateHead", value = trace.sessionAsyncPocSelectedCandidateHead ?: "—"),
            InferenceStatItemUi(label = "generateMethod", value = trace.generateMethodSignature ?: "—"),
            InferenceStatItemUi(label = "createPath", value = trace.createMethodSignature ?: "—"),
            InferenceStatItemUi(label = "optionsBuildPath", value = trace.optionsBuildPath ?: "—"),
        ),
    )
}

private fun buildDevDiagnosticSummarySection(
    stats: InferenceStats,
    trace: LocalInferenceTrace?,
    devHeldStateText: String?,
    devCloseLifecycleText: String?,
    devDebugText: String?,
): InferenceStatsSectionUi? {
    if (
        trace == null &&
        stats.modelName.isNullOrBlank() &&
        devHeldStateText.isNullOrBlank() &&
        devCloseLifecycleText.isNullOrBlank() &&
        devDebugText.isNullOrBlank()
    ) {
        return null
    }
    val items = listOf(
        InferenceStatItemUi(label = "実行経路", value = resolveDevSummaryExecutionPath(stats, trace)),
        InferenceStatItemUi(label = "使用モデル", value = resolveDevSummaryModelName(stats, trace)),
        InferenceStatItemUi(label = "モデル解決", value = resolveDevSummaryModelResolution(stats, trace)),
        InferenceStatItemUi(label = "held engine再利用", value = resolveDevSummaryEngineReuse(devHeldStateText)),
        InferenceStatItemUi(label = "held engine状態", value = resolveDevSummaryHeldState(devHeldStateText)),
        InferenceStatItemUi(label = "close結果", value = resolveDevSummaryCloseStatus(devCloseLifecycleText)),
        InferenceStatItemUi(label = "失敗要約", value = resolveDevSummaryFailure(devDebugText)),
    )
    if (items.all { it.value == "—" || it.value == "不明" }) return null
    return InferenceStatsSectionUi(
        title = "DEV診断サマリー",
        items = items,
    )
}

private fun resolveDevSummaryExecutionPath(
    stats: InferenceStats,
    trace: LocalInferenceTrace?,
): String {
    val source = trace?.selectedAssistantResponseSource?.trim()?.takeIf { it.isNotBlank() }
    if (source != null) return source
    val localSourceSummary = stats.localSourceSummary?.trim()?.takeIf { it.isNotBlank() }
    if (localSourceSummary != null) return localSourceSummary
    return "unknown"
}

private fun resolveDevSummaryModelName(
    stats: InferenceStats,
    trace: LocalInferenceTrace?,
): String {
    return stats.modelName?.trim()?.takeIf { it.isNotBlank() }
        ?: trace?.localModelDisplayName?.trim()?.takeIf { it.isNotBlank() }
        ?: "—"
}

private fun resolveDevSummaryModelResolution(
    stats: InferenceStats,
    trace: LocalInferenceTrace?,
): String {
    val modelName = stats.modelName?.trim()?.takeIf { it.isNotBlank() }
    val traceModel = trace?.localModelDisplayName?.trim()?.takeIf { it.isNotBlank() }
    return when {
        modelName != null && (traceModel == null || modelName == traceModel) -> "設定モデル使用"
        modelName == null && traceModel != null -> "設定モデル使用"
        else -> "不明"
    }
}

private fun resolveDevSummaryEngineReuse(devHeldStateText: String?): String {
    val heldExists = devHeldStateText.devLineValue("heldExists")?.toBooleanStrictOrNull()
    val useCount = devHeldStateText.devLineValue("useCount")?.toIntOrNull()
    val heldHash = devHeldStateText.devLineValue("heldHash")
    return when {
        heldExists == true && useCount != null && useCount >= 1 -> "再利用あり"
        heldExists == true && !heldHash.isNullOrBlank() && heldHash != "null" -> "再利用あり"
        heldExists == false -> "再利用なし"
        heldExists == true && useCount == 0 -> "再利用なし"
        heldExists == true -> "再利用あり"
        else -> "不明"
    }
}

private fun resolveDevSummaryHeldState(devHeldStateText: String?): String {
    val heldExists = devHeldStateText.devLineValue("heldExists")?.toBooleanStrictOrNull()
    return when (heldExists) {
        true -> "存在"
        false -> "未保持"
        null -> "不明"
    }
}

private fun resolveDevSummaryCloseStatus(devCloseLifecycleText: String?): String {
    if (devCloseLifecycleText.isNullOrBlank()) return "—"
    val conversation = devCloseLifecycleText.devLineValue("conversation")?.substringAfter("status=")?.substringBefore(" ")
    val engine = devCloseLifecycleText.devLineValue("engine")?.substringAfter("status=")?.substringBefore(" ")
    return listOfNotNull(
        conversation?.let { "conversation:$it" },
        engine?.let { "engine:$it" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "—"
}

private fun resolveDevSummaryFailure(devDebugText: String?): String {
    if (devDebugText.isNullOrBlank()) return "—"
    val stage = devDebugText.devLineValue("stage")
    val errorClass = devDebugText.devLineValue("class")
    val message = devDebugText.devLineValue("message")
    val primary = listOfNotNull(stage, errorClass)
        .filter { it.isNotBlank() }
    if (primary.isNotEmpty()) return primary.take(2).joinToString(" / ")
    return message
        ?.takeIf { it.isNotBlank() }
        ?.take(40)
        ?.let { if (it.length < (message.length)) "$it…" else it }
        ?: "—"
}

private fun String?.devLineValue(key: String): String? {
    if (this.isNullOrBlank()) return null
    return lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun withProbeStateLabel(value: String?, state: String): String =
    "${value ?: "—"}（$state）"
