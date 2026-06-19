package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting

internal data class LocalInferenceSuccessTimingDiagnosticsInput(
    val preferredBackendSetting: PreferredBackendDryRunSetting,
    val npuStandardRouteMode: NpuStandardRouteMode,
    val status: String = "success",
    val generationFinishedAtElapsedMs: Long? = null,
    val ttsRequestedAtElapsedMs: Long? = null,
    val ttsStartedAtElapsedMs: Long? = null,
    val ttsCompletedAtElapsedMs: Long? = null,
    val statsBuildStartedAtElapsedMs: Long? = null,
    val statsBuildFinishedAtElapsedMs: Long? = null,
    val bottomSheetUpdateStartedAtElapsedMs: Long? = null,
    val bottomSheetUpdateFinishedAtElapsedMs: Long? = null,
    val tokenizerCountStartedAtElapsedMs: Long? = null,
    val tokenizerCountFinishedAtElapsedMs: Long? = null,
    val tokenizerCountDurationMs: Long? = null,
    val tokenCountMode: String? = null,
)

internal fun buildLocalInferenceSuccessTimingDiagnosticsText(
    input: LocalInferenceSuccessTimingDiagnosticsInput,
): String {
    val backendDiagnostics = npuS1BackendDiagnosticsForPreferredSetting(
        setting = input.preferredBackendSetting,
        npuStandardRouteMode = input.npuStandardRouteMode,
    )
    val statsDisplayLagAfterGenerationMs = diffMs(
        start = input.generationFinishedAtElapsedMs,
        end = input.bottomSheetUpdateFinishedAtElapsedMs,
    )
    val statsDisplayLagAfterTtsMs = diffMs(
        start = input.ttsStartedAtElapsedMs,
        end = input.bottomSheetUpdateFinishedAtElapsedMs,
    )
    val tokenizerCountDurationMs = input.tokenizerCountDurationMs
        ?: diffMs(
            start = input.tokenizerCountStartedAtElapsedMs,
            end = input.tokenizerCountFinishedAtElapsedMs,
        )
    val finalUsesTokenizer = input.tokenCountMode?.contains("tokenizer", ignoreCase = true) == true
    val tokenizerDelayedStatsUpdate = tokenizerCountDurationMs?.let { it > 0L } ?: false
    val initialTokenMetricSource = "estimated_tokens_before_tokenizer"
    val finalTokenMetricSource = if (finalUsesTokenizer) {
        "tokenizer_tokens"
    } else {
        input.tokenCountMode?.takeIf { it.isNotBlank() } ?: "estimated_tokens"
    }
    return listOf(
        "[DEV診断: Local inference success timing compact]",
        "status=${input.status}",
        "selected_backend=${backendDiagnostics.selectedBackend}",
        "requested_backend=${backendDiagnostics.requestedBackend}",
        "effective_backend=${backendDiagnostics.effectiveBackend}",
        "route_family=${backendDiagnostics.routeFamily}",
        "backend_evidence=${backendDiagnostics.backendEvidence}",
        "generation_finished_at_elapsed_ms=${formatTimingValue(input.generationFinishedAtElapsedMs)}",
        "tts_requested_at_elapsed_ms=${formatTimingValue(input.ttsRequestedAtElapsedMs)}",
        "tts_started_at_elapsed_ms=${formatTimingValue(input.ttsStartedAtElapsedMs)}",
        "tts_completed_at_elapsed_ms=${formatTimingValue(input.ttsCompletedAtElapsedMs)}",
        "stats_build_started_at_elapsed_ms=${formatTimingValue(input.statsBuildStartedAtElapsedMs)}",
        "stats_build_finished_at_elapsed_ms=${formatTimingValue(input.statsBuildFinishedAtElapsedMs)}",
        "bottom_sheet_update_started_at_elapsed_ms=${formatTimingValue(input.bottomSheetUpdateStartedAtElapsedMs)}",
        "bottom_sheet_update_finished_at_elapsed_ms=${formatTimingValue(input.bottomSheetUpdateFinishedAtElapsedMs)}",
        "stats_display_lag_after_generation_ms=${formatTimingValue(statsDisplayLagAfterGenerationMs)}",
        "stats_display_lag_after_tts_ms=${formatTimingValue(statsDisplayLagAfterTtsMs)}",
        "tokenizer_count_started_at_elapsed_ms=${formatTimingValue(input.tokenizerCountStartedAtElapsedMs)}",
        "tokenizer_count_finished_at_elapsed_ms=${formatTimingValue(input.tokenizerCountFinishedAtElapsedMs)}",
        "tokenizer_count_duration_ms=${formatTimingValue(tokenizerCountDurationMs)}",
        "stats_initial_display_used_estimated_tokens=true",
        "stats_final_display_used_tokenizer_tokens=$finalUsesTokenizer",
        "tokenizer_count_delayed_stats_update=$tokenizerDelayedStatsUpdate",
        "stats_token_metrics_initial_source=$initialTokenMetricSource",
        "stats_token_metrics_final_source=$finalTokenMetricSource",
    ).joinToString("\n")
}

private fun diffMs(start: Long?, end: Long?): Long? {
    if (start == null || end == null) return null
    return (end - start).coerceAtLeast(0L)
}

private fun formatTimingValue(value: Long?): String = value?.coerceAtLeast(0L)?.toString() ?: "unavailable"
