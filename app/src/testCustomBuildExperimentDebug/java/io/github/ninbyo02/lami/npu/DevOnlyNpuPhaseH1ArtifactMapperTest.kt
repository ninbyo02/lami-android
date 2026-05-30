package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1ArtifactMapperTest {
    @Test
    fun `success natural japanese gate pass maps to visible preview`() {
        val state = present(
            artifactValues(sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？"),
        )

        assertTrue(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.SUCCESS, state.status)
        assertEquals("こんにちは！何かお手伝いできることはありますか？", state.outputPreview)
        assertEquals("success", state.reasonCode)
        assertTransientOnly(state)
    }

    @Test
    fun `raw output is discarded before ui input`() {
        val rawOutput = "<start_of_turn>model\\nraw artifact\\n<end_of_turn>"
        val input = DevOnlyNpuPhaseH1ArtifactMapper.from(
            artifactValues(
                sanitizedOutput = "何かご用でしょうか？",
                rawOutput = rawOutput,
            ),
        )
        val state = DevOnlyNpuPhaseH1Presenter.present(input)

        assertEquals("何かご用でしょうか？", state.outputPreview)
        assertFalse(input.toString().contains(rawOutput))
        assertFalse(state.toString().contains("raw artifact"))
        assertFalse(state.toString().contains("<end_of_turn>"))
    }

    @Test
    fun `key value text input unescapes sanitized output`() {
        val input = DevOnlyNpuPhaseH1ArtifactMapper.fromKeyValueText(
            artifactValues(sanitizedOutput = "一行目\\n二行目")
                .entries
                .joinToString("\n") { "${it.key}=${it.value}" },
        )

        assertEquals("一行目\n二行目", input.sanitizedOutput)
    }

    @Test
    fun `empty sanitized output rolls back`() {
        assertRollback("empty_sanitized_output", artifactValues(sanitizedOutput = " "))
    }

    @Test
    fun `template artifact after sanitize rolls back`() {
        assertRollback(
            "template_artifact_after_sanitize",
            artifactValues(sanitizedOutput = "こんにちは<end_of_turn>"),
        )
    }

    @Test
    fun `fallback true rolls back`() {
        assertRollback("fallback_used", artifactValues("fallback_used" to "true"))
    }

    @Test
    fun `timeout true rolls back`() {
        assertRollback("timeout", artifactValues("timeout" to "true"))
    }

    @Test
    fun `fresh crash true rolls back`() {
        assertRollback("fresh_crash", artifactValues("fresh_crash" to "true"))
    }

    @Test
    fun `standard route connected rolls back`() {
        assertRollback("standard_route_connected", artifactValues("standard_route_connected" to "true"))
    }

    @Test
    fun `db true rolls back`() {
        assertRollback("db_connected", artifactValues("db" to "true"))
    }

    @Test
    fun `npu backend evidence missing rolls back`() {
        assertRollback("npu_backend_evidence_missing", artifactValues("npu_backend_evidence" to ""))
    }

    @Test
    fun `npu backend not npu rolls back`() {
        assertRollback("npu_backend_not_npu", artifactValues("npu_backend" to "CPU"))
    }

    @Test
    fun `max output tokens decode ms and artifact path are preserved for presenter labels`() {
        val state = present(
            artifactValues(
                "max_output_tokens" to "128",
                "decode_ms" to "2345",
                "artifact_path" to "artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810/summary.md",
            ),
        )

        assertEquals("maxOutputTokens=128", state.maxOutputTokensText)
        assertEquals("decode_ms=2345", state.decodeMsText)
        assertEquals("artifact=20260525_211810/summary.md", state.artifactText)
        assertEquals("backendEvidence=QNN_HTP_V79_FastRPC", state.backendEvidenceText)
    }

    private fun assertRollback(expectedReason: String, values: Map<String, String>) {
        val state = present(values)

        assertFalse(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.ROLLBACK, state.status)
        assertNull(state.outputPreview)
        assertEquals(expectedReason, state.reasonCode)
        assertTransientOnly(state)
    }

    private fun present(values: Map<String, String>): DevOnlyNpuPhaseH1UiState =
        DevOnlyNpuPhaseH1Presenter.present(DevOnlyNpuPhaseH1ArtifactMapper.from(values))

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
