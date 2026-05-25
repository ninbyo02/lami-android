package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1StateReducerTest {
    private val nowMs = 2_000_000_000_000L

    @Test
    fun `initial state is hidden`() {
        val state = DevOnlyNpuPhaseH1StateReducer.initial()

        assertFalse(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.HIDDEN, state.status)
        assertNull(state.outputPreview)
        assertTransientOnly(state)
    }

    @Test
    fun `valid artifact loaded is visible`() {
        val state = DevOnlyNpuPhaseH1StateReducer.onValidArtifactLoaded(
            DevOnlyNpuPhaseH1ArtifactMapper.from(artifactValues()),
        )

        assertTrue(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.SUCCESS, state.status)
        assertEquals("何かご用でしょうか？", state.outputPreview)
        assertTransientOnly(state)
    }

    @Test
    fun `clear by new input hides state`() {
        val state = DevOnlyNpuPhaseH1StateReducer.onClearByNewInput(visibleState())

        assertHidden("clear_new_input", state)
    }

    @Test
    fun `clear by navigation away hides state`() {
        val state = DevOnlyNpuPhaseH1StateReducer.onClearByNavigationAway(visibleState())

        assertHidden("clear_navigation_away", state)
    }

    @Test
    fun `clear by toggle off hides state`() {
        val state = DevOnlyNpuPhaseH1StateReducer.onClearByToggleOff(visibleState())

        assertHidden("clear_toggle_off", state)
    }

    @Test
    fun `clear by failure rollback hides state`() {
        val state = DevOnlyNpuPhaseH1StateReducer.onClearByFailureRollback(visibleState())

        assertHidden("clear_failure_rollback", state)
    }

    @Test
    fun `app restart hides state`() {
        val state = DevOnlyNpuPhaseH1StateReducer.onAppRestart()

        assertHidden("app_restart", state)
    }

    @Test
    fun `refresh with fresh artifact reapplies mapper and restores visible preview`() {
        val state = DevOnlyNpuPhaseH1StateReducer.onRefreshWithArtifactMetadata(
            artifactValues("artifact_timestamp_ms" to nowMs.toString()),
            nowMs = nowMs,
        )

        assertTrue(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.SUCCESS, state.status)
        assertEquals("何かご用でしょうか？", state.outputPreview)
        assertEquals("success", state.reasonCode)
        assertTransientOnly(state)
    }

    @Test
    fun `refresh with stale artifact hides preview`() {
        val state = DevOnlyNpuPhaseH1StateReducer.onRefreshWithArtifactMetadata(
            artifactValues(
                "artifact_timestamp_ms" to (
                    nowMs - DevOnlyNpuPhaseH1ArtifactFreshness.DEFAULT_FRESHNESS_WINDOW_MS - 1L
                    ).toString(),
            ),
            nowMs = nowMs,
        )

        assertFalse(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.ROLLBACK, state.status)
        assertNull(state.outputPreview)
        assertEquals("stale_artifact", state.reasonCode)
        assertTrue(state.rollback)
        assertTransientOnly(state)
    }

    @Test
    fun `refresh with missing timestamp hides preview as stale unknown`() {
        val state = DevOnlyNpuPhaseH1StateReducer.onRefreshWithArtifactMetadata(
            artifactValues(),
            nowMs = nowMs,
        )

        assertFalse(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.ROLLBACK, state.status)
        assertEquals("stale_or_unknown", state.reasonCode)
        assertNull(state.outputPreview)
    }

    @Test
    fun `refresh event is metadata only and never runs npu engine or decode`() {
        val policy = DevOnlyNpuPhaseH1StateReducer.refreshPolicy

        assertTrue(policy.readsArtifactMetadataOnly)
        assertFalse(policy.runsNpu)
        assertFalse(policy.initializesEngine)
        assertFalse(policy.runsDecode)
    }

    private fun visibleState(): DevOnlyNpuPhaseH1UiState =
        DevOnlyNpuPhaseH1StateReducer.onValidArtifactLoaded(
            DevOnlyNpuPhaseH1ArtifactMapper.from(artifactValues()),
        )

    private fun assertHidden(expectedReasonCode: String, state: DevOnlyNpuPhaseH1UiState) {
        assertFalse(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.HIDDEN, state.status)
        assertNull(state.outputPreview)
        assertEquals(expectedReasonCode, state.reasonCode)
        assertTransientOnly(state)
    }

    private fun artifactValues(
        vararg overrides: Pair<String, String>,
        sanitizedOutput: String = "何かご用でしょうか？",
        rawOutput: String = "<end_of_turn>raw",
    ): Map<String, String> =
        buildMap {
            put("result", "success")
            put("reasonCode", "success")
            put("sanitized_output", sanitizedOutput)
            put("raw_output", rawOutput)
            put("quality_classification", "natural_japanese")
            put("npu_backend", "NPU")
            put("npu_backend_evidence", "QNN_HTP_V79_FastRPC_native_diag")
            put("fallback_used", "false")
            put("timeout", "false")
            put("fresh_crash", "false")
            put("selected_path_npu_saved", "false")
            put("standard_route_connected", "false")
            put("normal_ui_route_connected", "false")
            put("db", "false")
            put("tts", "false")
            put("markdown", "false")
            put("streaming", "false")
            put("decode_ms", "10")
            put("max_output_tokens", "128")
            put("artifact_path", "artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810/summary.md")
            overrides.forEach { (key, value) -> put(key, value) }
        }

    private fun assertTransientOnly(state: DevOnlyNpuPhaseH1UiState) {
        assertFalse(state.shouldPersistToDb)
        assertFalse(state.shouldSpeakTts)
        assertFalse(state.shouldRenderMarkdown)
        assertFalse(state.shouldStream)
    }
}
