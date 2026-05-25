package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1ArtifactMetadataParserTest {
    private val nowMs = 2_000_000_000_000L

    @Test
    fun `valid key value text parses metadata`() {
        val result = DevOnlyNpuPhaseH1ArtifactMetadataParser.parseKeyValueText(
            keyValueText(validValues()),
        )

        assertTrue(result.success)
        assertEquals("success", result.reasonCode)
        assertEquals(nowMs.toString(), result.metadata?.values?.get("artifact_timestamp_ms"))
        assertEquals("何かご用でしょうか？", result.metadata?.values?.get("sanitized_output"))
    }

    @Test
    fun `valid metadata maps to ui input success`() {
        val input = DevOnlyNpuPhaseH1ArtifactMetadataParser.uiInputFrom(
            DevOnlyNpuPhaseH1ArtifactMetadataParser.parseMap(validValues()),
            nowMs = nowMs,
        )

        assertTrue(input.success)
        assertEquals("何かご用でしょうか？", input.sanitizedOutput)
        assertEquals("success", input.reasonCode)
    }

    @Test
    fun `raw output is discarded at metadata boundary`() {
        val result = DevOnlyNpuPhaseH1ArtifactMetadataParser.parseMap(
            validValues("raw_output" to "<start_of_turn>raw<end_of_turn>"),
        )
        val input = DevOnlyNpuPhaseH1ArtifactMetadataParser.uiInputFrom(result, nowMs)

        assertFalse(result.metadata.toString().contains("raw_output"))
        assertFalse(input.toString().contains("raw"))
        assertEquals("何かご用でしょうか？", input.sanitizedOutput)
    }

    @Test
    fun `missing sanitized output rolls back before ui display`() {
        assertRollback(
            expectedReason = "missing_required_field:sanitized_output",
            values = validValues() - "sanitized_output",
        )
    }

    @Test
    fun `invalid bool rolls back before mapper`() {
        assertRollback(
            expectedReason = "invalid_bool:fallback_used",
            values = validValues("fallback_used" to "maybe"),
        )
    }

    @Test
    fun `invalid timestamp rolls back before freshness`() {
        assertRollback(
            expectedReason = "invalid_number:artifact_timestamp_ms",
            values = validValues("artifact_timestamp_ms" to "not-a-number"),
        )
    }

    @Test
    fun `stale timestamp rolls back after freshness`() {
        val state = DevOnlyNpuPhaseH1ArtifactMetadataParser.stateFromKeyValueText(
            keyValueText(
                validValues(
                    "artifact_timestamp_ms" to (
                        nowMs - DevOnlyNpuPhaseH1ArtifactFreshness.DEFAULT_FRESHNESS_WINDOW_MS - 1L
                        ).toString(),
                ),
            ),
            nowMs = nowMs,
        )

        assertFalse(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.ROLLBACK, state.status)
        assertEquals("stale_artifact", state.reasonCode)
        assertNull(state.outputPreview)
    }

    @Test
    fun `unknown key is ignored`() {
        val result = DevOnlyNpuPhaseH1ArtifactMetadataParser.parseMap(
            validValues("unknown_key" to "unknown-value"),
        )

        assertTrue(result.success)
        assertFalse(result.metadata.toString().contains("unknown_key"))
    }

    @Test
    fun `duplicate key uses last value`() {
        val result = DevOnlyNpuPhaseH1ArtifactMetadataParser.parseKeyValueText(
            keyValueText(validValues()) + "\nsanitized_output=最後の値",
        )
        val input = DevOnlyNpuPhaseH1ArtifactMetadataParser.uiInputFrom(result, nowMs)

        assertTrue(input.success)
        assertEquals("最後の値", input.sanitizedOutput)
    }

    @Test
    fun `toggle false does not read or parse metadata`() {
        var readCalled = false

        val result = DevOnlyNpuPhaseH1ArtifactMetadataParser.boundaryForToggle(
            devEnableNpuChatScreenRoute = false,
            nowMs = nowMs,
            metadataTextProvider = {
                readCalled = true
                keyValueText(validValues())
            },
        )

        assertFalse(readCalled)
        assertFalse(result.shouldReadMetadata)
        assertNull(result.metadata)
        assertFalse(result.uiState.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.HIDDEN, result.uiState.status)
    }

    @Test
    fun `toggle true reads metadata but still requires fresh gate pass`() {
        var readCalled = false

        val result = DevOnlyNpuPhaseH1ArtifactMetadataParser.boundaryForToggle(
            devEnableNpuChatScreenRoute = true,
            nowMs = nowMs,
            metadataTextProvider = {
                readCalled = true
                keyValueText(validValues())
            },
        )

        assertTrue(readCalled)
        assertTrue(result.shouldReadMetadata)
        assertTrue(result.uiState.visible)
        assertEquals("何かご用でしょうか？", result.uiState.outputPreview)
    }

    private fun assertRollback(
        expectedReason: String,
        values: Map<String, String>,
    ) {
        val parseResult = DevOnlyNpuPhaseH1ArtifactMetadataParser.parseMap(values)
        val state = DevOnlyNpuPhaseH1Presenter.present(
            DevOnlyNpuPhaseH1ArtifactMetadataParser.uiInputFrom(parseResult, nowMs),
        )

        assertFalse(parseResult.success)
        assertFalse(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.ROLLBACK, state.status)
        assertEquals(expectedReason, state.reasonCode)
        assertNull(state.outputPreview)
    }

    private fun keyValueText(values: Map<String, String>): String =
        buildString {
            appendLine("# artifact metadata")
            appendLine()
            values.forEach { (key, value) -> appendLine("$key=$value") }
        }

    private fun validValues(vararg overrides: Pair<String, String>): Map<String, String> =
        buildMap {
            put("artifact_timestamp_ms", nowMs.toString())
            put("result", "success")
            put("reasonCode", "success")
            put("sanitized_output", "何かご用でしょうか？")
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
}
