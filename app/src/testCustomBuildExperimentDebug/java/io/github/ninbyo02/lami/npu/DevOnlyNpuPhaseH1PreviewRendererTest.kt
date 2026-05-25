package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1PreviewRendererTest {
    private val sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？"

    @Test
    fun `success render order is stable`() {
        val lines = DevOnlyNpuPhaseH1PreviewRenderer.renderLines(successModel())

        assertEquals(
            listOf(
                "DEV ONLY",
                "DEV NPU transient preview",
                "Status: SUCCESS",
                "Read-only sanitized output",
                "Output:",
                sanitizedOutput,
                "Reason: reasonCode=ok",
                "Details:",
                "- maxOutputTokens=128",
                "- decode_ms=2756",
                "- backendEvidence=QNN_HTP_V79_FastRPC",
                "- artifact=qairt244_npu_turn_stop_quality_compare/20260525_211810",
                "- selectedPathSaved=false",
                "- db=false",
                "- tts=false",
                "- markdown=false",
                "- streaming=false",
            ),
            lines,
        )
    }

    @Test
    fun `success body is sanitized output only`() {
        val lines = DevOnlyNpuPhaseH1PreviewRenderer.renderLines(successModel())

        assertTrue(lines.contains(sanitizedOutput))
        assertFalse(lines.joinToString("\n").contains("raw_output"))
        assertFalse(lines.joinToString("\n").contains("<end_of_turn>"))
        assertFalse(lines.joinToString("\n").contains("<start_of_turn>"))
    }

    @Test
    fun `action and button labels are not rendered`() {
        val text = DevOnlyNpuPhaseH1PreviewRenderer.renderContractText(successModel())

        assertFalse(text.contains("Retry", ignoreCase = true))
        assertFalse(text.contains("Persist", ignoreCase = true))
        assertFalse(text.contains("TTS button", ignoreCase = true))
        assertFalse(text.contains("Markdown button", ignoreCase = true))
        assertFalse(text.contains("Streaming indicator", ignoreCase = true))
    }

    @Test
    fun `rollback invisible model renders no lines`() {
        val model = DevOnlyNpuPhaseH1CardViewModelMapper.from(rollbackState())

        assertFalse(model.visible)
        assertTrue(DevOnlyNpuPhaseH1PreviewRenderer.renderLines(model).isEmpty())
        assertEquals("", DevOnlyNpuPhaseH1PreviewRenderer.renderContractText(model))
    }

    @Test
    fun `hidden model renders no lines`() {
        val model = DevOnlyNpuPhaseH1CardViewModelMapper.from(DevOnlyNpuPhaseH1StateReducer.initial())

        assertFalse(model.visible)
        assertTrue(DevOnlyNpuPhaseH1PreviewRenderer.renderLines(model).isEmpty())
        assertEquals("", DevOnlyNpuPhaseH1PreviewRenderer.renderContractText(model))
    }

    @Test
    fun `detail lines keep source order`() {
        val lines = DevOnlyNpuPhaseH1PreviewRenderer.renderLines(successModel())
        val detailsIndex = lines.indexOf("Details:")

        assertEquals("- maxOutputTokens=128", lines[detailsIndex + 1])
        assertEquals("- decode_ms=2756", lines[detailsIndex + 2])
        assertEquals("- backendEvidence=QNN_HTP_V79_FastRPC", lines[detailsIndex + 3])
        assertEquals("- artifact=qairt244_npu_turn_stop_quality_compare/20260525_211810", lines[detailsIndex + 4])
        assertEquals("- selectedPathSaved=false", lines[detailsIndex + 5])
        assertEquals("- db=false", lines[detailsIndex + 6])
        assertEquals("- tts=false", lines[detailsIndex + 7])
        assertEquals("- markdown=false", lines[detailsIndex + 8])
        assertEquals("- streaming=false", lines[detailsIndex + 9])
    }

    @Test
    fun `warning lines render after details when visible`() {
        val model = successModel().copy(warningLines = listOf("manual_warning=visible_contract"))
        val lines = DevOnlyNpuPhaseH1PreviewRenderer.renderLines(model)

        assertEquals("Warnings:", lines[lines.size - 2])
        assertEquals("- manual_warning=visible_contract", lines.last())
        assertTrue(lines.indexOf("Warnings:") > lines.indexOf("Details:"))
    }

    @Test
    fun `success contract text snapshot is stable`() {
        assertEquals(
            """
            DEV ONLY
            DEV NPU transient preview
            Status: SUCCESS
            Read-only sanitized output
            Output:
            こんにちは！何かお手伝いできることはありますか？
            Reason: reasonCode=ok
            Details:
            - maxOutputTokens=128
            - decode_ms=2756
            - backendEvidence=QNN_HTP_V79_FastRPC
            - artifact=qairt244_npu_turn_stop_quality_compare/20260525_211810
            - selectedPathSaved=false
            - db=false
            - tts=false
            - markdown=false
            - streaming=false
            """.trimIndent(),
            DevOnlyNpuPhaseH1PreviewRenderer.renderContractText(successModel()),
        )
    }

    private fun successModel(): DevOnlyNpuPhaseH1CardViewModel =
        DevOnlyNpuPhaseH1CardViewModelMapper.from(successState())

    private fun successState(): DevOnlyNpuPhaseH1UiState =
        DevOnlyNpuPhaseH1Presenter.present(
            DevOnlyNpuPhaseH1UiInput(
                success = true,
                sanitizedOutput = sanitizedOutput,
                reasonCode = "ok",
                decodeMs = 2756L,
                backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                maxOutputTokens = 128,
                artifactPath = "artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810",
                qualityClassification = "natural_japanese",
            ),
        )

    private fun rollbackState(): DevOnlyNpuPhaseH1UiState =
        DevOnlyNpuPhaseH1Presenter.present(
            DevOnlyNpuPhaseH1UiInput(
                success = true,
                sanitizedOutput = sanitizedOutput,
                reasonCode = "ok",
                decodeMs = 2756L,
                backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                maxOutputTokens = 128,
                artifactPath = "artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810",
                qualityClassification = "natural_japanese",
                fallbackUsed = true,
            ),
        )
}
