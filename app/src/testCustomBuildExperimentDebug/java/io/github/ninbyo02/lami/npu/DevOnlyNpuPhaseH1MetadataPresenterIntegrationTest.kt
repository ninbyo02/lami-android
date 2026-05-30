package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1MetadataPresenterIntegrationTest {
    private val nowMs = 2_000_000_000_000L
    private val sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？"
    private val rawOutput = "こんにちは\\n<end_of_turn>\\n$sanitizedOutput\\n<end_of_turn>"

    @Test
    fun `valid baseline metadata becomes visible sanitized transient state`() {
        val state = stateFromMetadata(baselineMetadataText())

        assertTrue(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.SUCCESS, state.status)
        assertEquals("ok", state.reasonCode)
        assertEquals(sanitizedOutput, state.outputPreview)
        assertEquals("decode_ms=2756", state.decodeMsText)
        assertEquals("maxOutputTokens=128", state.maxOutputTokensText)
        assertEquals("backendEvidence=QNN_HTP_V79_FastRPC", state.backendEvidenceText)
        assertEquals("artifact=qairt244_npu_turn_stop_quality_compare/20260525_211810", state.artifactText)
        assertTransientOnly(state)
    }

    @Test
    fun `raw output is not propagated through metadata boundary input or state`() {
        val parseResult = DevOnlyNpuPhaseH1ArtifactMetadataParser.parseKeyValueText(
            baselineMetadataText(),
        )
        val input = DevOnlyNpuPhaseH1ArtifactMetadataParser.uiInputFrom(parseResult, nowMs)
        val state = DevOnlyNpuPhaseH1Presenter.present(input)

        assertFalse(parseResult.toString().contains("raw_output"))
        assertFalse(input.toString().contains("raw_output"))
        assertFalse(input.toString().contains("<end_of_turn>"))
        assertFalse(state.toString().contains("raw_output"))
        assertFalse(state.toString().contains("<end_of_turn>"))
        assertEquals(sanitizedOutput, state.outputPreview)
    }

    @Test
    fun `fallback metadata rolls back before display`() {
        assertGateRollback("fallback_used", "fallback_used" to "true")
    }

    @Test
    fun `timeout metadata rolls back before display`() {
        assertGateRollback("timeout", "timeout" to "true")
    }

    @Test
    fun `template artifact quality rolls back before display`() {
        assertGateRollback(
            "quality_not_natural_japanese",
            "quality_classification" to "template_artifact",
        )
    }

    @Test
    fun `standard route connected metadata rolls back before display`() {
        assertGateRollback("standard_route_connected", "standard_route_connected" to "true")
    }

    @Test
    fun `db connected metadata rolls back before display`() {
        assertGateRollback("db_connected", "db" to "true")
    }

    @Test
    fun `toggle false does not call metadata provider and stays hidden`() {
        var providerCalled = false

        val result = DevOnlyNpuPhaseH1ArtifactMetadataParser.boundaryForToggle(
            devEnableNpuChatScreenRoute = false,
            nowMs = nowMs,
            metadataTextProvider = {
                providerCalled = true
                baselineMetadataText()
            },
        )

        assertFalse(providerCalled)
        assertFalse(result.shouldReadMetadata)
        assertNull(result.metadata)
        assertFalse(result.uiState.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.HIDDEN, result.uiState.status)
        assertNull(result.uiState.outputPreview)
        assertTransientOnly(result.uiState)
    }

    @Test
    fun `toggle true reads fresh metadata and presents sanitized state`() {
        var providerCalled = false

        val result = DevOnlyNpuPhaseH1ArtifactMetadataParser.boundaryForToggle(
            devEnableNpuChatScreenRoute = true,
            nowMs = nowMs,
            metadataTextProvider = {
                providerCalled = true
                baselineMetadataText()
            },
        )

        assertTrue(providerCalled)
        assertTrue(result.shouldReadMetadata)
        assertTrue(result.uiState.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.SUCCESS, result.uiState.status)
        assertEquals(sanitizedOutput, result.uiState.outputPreview)
        assertTransientOnly(result.uiState)
    }

    private fun assertGateRollback(
        expectedReason: String,
        override: Pair<String, String>,
    ) {
        val state = stateFromMetadata(baselineMetadataText(override))

        assertFalse(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.ROLLBACK, state.status)
        assertEquals(expectedReason, state.reasonCode)
        assertNull(state.outputPreview)
        assertTrue(state.rollback)
        assertTransientOnly(state)
    }

    private fun stateFromMetadata(text: String): DevOnlyNpuPhaseH1UiState =
        DevOnlyNpuPhaseH1ArtifactMetadataParser.stateFromKeyValueText(text, nowMs)

    private fun baselineMetadataText(vararg overrides: Pair<String, String>): String =
        keyValueText(
            buildMap {
                put("artifact_timestamp_ms", nowMs.toString())
                put("result", "success")
                put("reasonCode", "ok")
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
                put("decode_ms", "2756")
                put("max_output_tokens", "128")
                put("artifact_path", "artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810")
                overrides.forEach { (key, value) -> put(key, value) }
            },
        )

    private fun keyValueText(values: Map<String, String>): String =
        buildString {
            values.forEach { (key, value) -> appendLine("$key=$value") }
        }

    private fun assertTransientOnly(state: DevOnlyNpuPhaseH1UiState) {
        assertFalse(state.shouldPersistToDb)
        assertFalse(state.shouldSpeakTts)
        assertFalse(state.shouldRenderMarkdown)
        assertFalse(state.shouldStream)
    }
}
