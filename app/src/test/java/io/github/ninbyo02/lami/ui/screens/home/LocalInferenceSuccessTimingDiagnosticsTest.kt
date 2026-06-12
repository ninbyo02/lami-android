package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInferenceSuccessTimingDiagnosticsTest {
    @Test
    fun `CPU success timing diagnostics include stats display lag keys`() {
        val text = buildLocalInferenceSuccessTimingDiagnosticsText(
            LocalInferenceSuccessTimingDiagnosticsInput(
                preferredBackendSetting = PreferredBackendDryRunSetting.CPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                generationFinishedAtElapsedMs = 1_000L,
                ttsRequestedAtElapsedMs = 1_050L,
                ttsStartedAtElapsedMs = 1_050L,
                statsBuildStartedAtElapsedMs = 1_010L,
                statsBuildFinishedAtElapsedMs = 1_030L,
                bottomSheetUpdateStartedAtElapsedMs = 1_040L,
                bottomSheetUpdateFinishedAtElapsedMs = 1_090L,
                tokenizerCountStartedAtElapsedMs = 970L,
                tokenizerCountFinishedAtElapsedMs = 1_005L,
                tokenCountMode = "mediapipe_tokenizer_recount",
            ),
        )

        assertTrue(text.contains("[DEV診断: Local inference success timing compact]"))
        assertTrue(text.contains("status=success"))
        assertTrue(text.contains("selected_backend=CPU"))
        assertTrue(text.contains("requested_backend=CPU"))
        assertTrue(text.contains("route_family=local_cpu"))
        assertTrue(text.contains("generation_finished_at_elapsed_ms=1000"))
        assertTrue(text.contains("stats_build_started_at_elapsed_ms=1010"))
        assertTrue(text.contains("stats_build_finished_at_elapsed_ms=1030"))
        assertTrue(text.contains("bottom_sheet_update_started_at_elapsed_ms=1040"))
        assertTrue(text.contains("bottom_sheet_update_finished_at_elapsed_ms=1090"))
        assertTrue(text.contains("stats_display_lag_after_generation_ms=90"))
        assertTrue(text.contains("stats_display_lag_after_tts_ms=40"))
        assertTrue(text.contains("tokenizer_count_started_at_elapsed_ms=970"))
        assertTrue(text.contains("tokenizer_count_finished_at_elapsed_ms=1005"))
        assertTrue(text.contains("tokenizer_count_duration_ms=35"))
        assertTrue(text.contains("stats_initial_display_used_estimated_tokens=true"))
        assertTrue(text.contains("stats_final_display_used_tokenizer_tokens=true"))
        assertTrue(text.contains("tokenizer_count_delayed_stats_update=true"))
        assertTrue(text.contains("stats_token_metrics_initial_source=estimated_tokens_before_tokenizer"))
        assertTrue(text.contains("stats_token_metrics_final_source=tokenizer_tokens"))
    }

    @Test
    fun `tokenizer count duration prefers measured duration when present`() {
        val text = buildLocalInferenceSuccessTimingDiagnosticsText(
            LocalInferenceSuccessTimingDiagnosticsInput(
                preferredBackendSetting = PreferredBackendDryRunSetting.CPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                tokenizerCountStartedAtElapsedMs = 10L,
                tokenizerCountFinishedAtElapsedMs = 50L,
                tokenizerCountDurationMs = 25L,
            ),
        )

        assertTrue(text.contains("tokenizer_count_duration_ms=25"))
    }
}
