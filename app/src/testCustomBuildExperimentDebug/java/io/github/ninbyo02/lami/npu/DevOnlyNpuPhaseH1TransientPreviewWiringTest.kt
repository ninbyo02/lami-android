package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1TransientPreviewWiringTest {
    private val nowMs = 2_000_000_000_000L
    private val sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？"

    @Test
    fun `enabled fresh metadata renders sanitized preview through full H1 chain`() {
        val result = DevOnlyNpuPhaseH1TransientPreviewWiring.render(
            devEnableNpuChatScreenRoute = true,
            nowMs = nowMs,
            metadataTextProvider = { baselineMetadataText() },
        )

        assertTrue(result.shouldReadMetadata)
        assertTrue(result.previewVisible)
        assertEquals("reasonCode=ok", result.reasonLabel)
        assertTrue(result.renderedLines.contains(sanitizedOutput))
        assertTrue(result.renderedLines.first() == "DEV ONLY - DEV NPU transient preview")
        assertSafety(result)
    }

    @Test
    fun `disabled toggle does not read metadata and renders no preview`() {
        var providerCalled = false
        val result = DevOnlyNpuPhaseH1TransientPreviewWiring.render(
            devEnableNpuChatScreenRoute = false,
            nowMs = nowMs,
            metadataTextProvider = {
                providerCalled = true
                baselineMetadataText()
            },
        )

        assertFalse(providerCalled)
        assertFalse(result.shouldReadMetadata)
        assertFalse(result.previewVisible)
        assertTrue(result.renderedLines.isEmpty())
        assertEquals("", result.renderedText)
        assertSafety(result)
    }

    @Test
    fun `stale metadata renders no preview and does not retry`() {
        val result = DevOnlyNpuPhaseH1TransientPreviewWiring.render(
            devEnableNpuChatScreenRoute = true,
            nowMs = nowMs,
            metadataTextProvider = {
                baselineMetadataText(
                    "artifact_timestamp_ms" to (
                        nowMs - DevOnlyNpuPhaseH1ArtifactFreshness.DEFAULT_FRESHNESS_WINDOW_MS - 1L
                        ).toString(),
                )
            },
        )

        assertTrue(result.shouldReadMetadata)
        assertFalse(result.previewVisible)
        assertTrue(result.renderedLines.isEmpty())
        assertEquals("", result.renderedText)
        assertEquals("reasonCode=stale_artifact", result.reasonLabel)
        assertSafety(result)
    }

    @Test
    fun `gate failure metadata renders no preview`() {
        val result = DevOnlyNpuPhaseH1TransientPreviewWiring.render(
            devEnableNpuChatScreenRoute = true,
            nowMs = nowMs,
            metadataTextProvider = { baselineMetadataText("fallback_used" to "true") },
        )

        assertFalse(result.previewVisible)
        assertTrue(result.renderedLines.isEmpty())
        assertEquals("reasonCode=fallback_used", result.reasonLabel)
        assertSafety(result)
    }

    @Test
    fun `raw output is not rendered by wired preview`() {
        val result = DevOnlyNpuPhaseH1TransientPreviewWiring.render(
            devEnableNpuChatScreenRoute = true,
            nowMs = nowMs,
            metadataTextProvider = {
                baselineMetadataText(
                    "raw_output" to "こんにちは\\n<end_of_turn>\\nraw native text",
                )
            },
        )

        assertFalse(result.renderedText.contains("raw_output"))
        assertFalse(result.renderedText.contains("<end_of_turn>"))
        assertFalse(result.renderedText.contains("<start_of_turn>"))
        assertTrue(result.renderedText.contains(sanitizedOutput))
    }

    private fun assertSafety(result: DevOnlyNpuPhaseH1TransientPreviewWiringResult) {
        assertTrue(result.sideEffectLines.contains("selectedPathNpuSaved=false"))
        assertTrue(result.sideEffectLines.contains("standard_route_connected=false"))
        assertTrue(result.sideEffectLines.contains("normal_ui_route_connected=false"))
        assertTrue(result.sideEffectLines.contains("db=false"))
        assertTrue(result.sideEffectLines.contains("tts=false"))
        assertTrue(result.sideEffectLines.contains("markdown=false"))
        assertTrue(result.sideEffectLines.contains("streaming=false"))
        assertTrue(result.sideEffectLines.contains("retry=false"))
        assertTrue(result.sideEffectLines.contains("auto_fallback=false"))
        assertTrue(result.sideEffectLines.contains("npu_generation=false"))
        assertTrue(result.sideEffectLines.contains("engine_initialize=false"))
        assertTrue(result.sideEffectLines.contains("run_decode=false"))
    }

    private fun baselineMetadataText(vararg overrides: Pair<String, String>): String =
        buildMap {
            put("artifact_timestamp_ms", nowMs.toString())
            put("result", "success")
            put("reasonCode", "ok")
            put("sanitized_output", sanitizedOutput)
            put("raw_output", "こんにちは\\n<end_of_turn>\\n$sanitizedOutput\\n<end_of_turn>")
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
            put("decode_ms", "829")
            put("max_output_tokens", "128")
            put("artifact_path", "artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810")
            overrides.forEach { (key, value) -> put(key, value) }
        }.entries.joinToString(separator = "\n") { (key, value) -> "$key=$value" }
}
