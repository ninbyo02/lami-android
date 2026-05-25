package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1CardViewModelTest {
    private val sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？"

    @Test
    fun `success contract exposes sanitized read only card`() {
        val viewModel = DevOnlyNpuPhaseH1CardViewModelMapper.from(successState())

        assertTrue(viewModel.visible)
        assertEquals("DEV NPU transient preview", viewModel.title)
        assertEquals("Read-only sanitized output", viewModel.subtitle)
        assertEquals(sanitizedOutput, viewModel.body)
        assertEquals("SUCCESS", viewModel.statusLabel)
        assertEquals("reasonCode=ok", viewModel.reasonLabel)
        assertEquals("DEV ONLY", viewModel.devBadge)
        assertNoUnsafeControls(viewModel)
    }

    @Test
    fun `rollback contract is hidden and reason only`() {
        val viewModel = DevOnlyNpuPhaseH1CardViewModelMapper.from(rollbackState())

        assertFalse(viewModel.visible)
        assertEquals("DEV NPU transient preview", viewModel.title)
        assertEquals("Hidden by promotion gate", viewModel.subtitle)
        assertNull(viewModel.body)
        assertEquals("ROLLBACK", viewModel.statusLabel)
        assertEquals("reasonCode=fallback_used", viewModel.reasonLabel)
        assertEquals(listOf("rollback=fallback_used"), viewModel.warningLines)
        assertNoUnsafeControls(viewModel)
    }

    @Test
    fun `hidden contract is invisible with no body or warning`() {
        val viewModel = DevOnlyNpuPhaseH1CardViewModelMapper.from(DevOnlyNpuPhaseH1StateReducer.initial())

        assertFalse(viewModel.visible)
        assertEquals("Hidden until a fresh gated artifact passes", viewModel.subtitle)
        assertNull(viewModel.body)
        assertEquals("HIDDEN", viewModel.statusLabel)
        assertEquals("reasonCode=initial", viewModel.reasonLabel)
        assertTrue(viewModel.warningLines.isEmpty())
        assertNoUnsafeControls(viewModel)
    }

    @Test
    fun `detail lines include required read only metadata`() {
        val viewModel = DevOnlyNpuPhaseH1CardViewModelMapper.from(successState())

        assertTrue(viewModel.detailLines.contains("maxOutputTokens=128"))
        assertTrue(viewModel.detailLines.contains("decode_ms=2756"))
        assertTrue(viewModel.detailLines.contains("backendEvidence=QNN_HTP_V79_FastRPC"))
        assertTrue(viewModel.detailLines.contains("artifact=qairt244_npu_turn_stop_quality_compare/20260525_211810"))
        assertTrue(viewModel.detailLines.contains("selectedPathSaved=false"))
        assertTrue(viewModel.detailLines.contains("db=false"))
        assertTrue(viewModel.detailLines.contains("tts=false"))
        assertTrue(viewModel.detailLines.contains("markdown=false"))
        assertTrue(viewModel.detailLines.contains("streaming=false"))
    }

    @Test
    fun `raw output is never included in view model or contract text`() {
        val viewModel = DevOnlyNpuPhaseH1CardViewModelMapper.from(successState())

        assertFalse(viewModel.showRawOutput)
        assertFalse(viewModel.toString().contains("raw_output"))
        assertFalse(viewModel.toString().contains("<end_of_turn>"))
        assertFalse(viewModel.toContractText().contains("raw_output"))
        assertFalse(viewModel.toContractText().contains("<end_of_turn>"))
    }

    @Test
    fun `success contract snapshot is stable`() {
        val viewModel = DevOnlyNpuPhaseH1CardViewModelMapper.from(successState())

        assertEquals(
            """
            visible=true
            title=DEV NPU transient preview
            subtitle=Read-only sanitized output
            statusLabel=SUCCESS
            reasonLabel=reasonCode=ok
            devBadge=DEV ONLY
            body=こんにちは！何かお手伝いできることはありますか？
            detailLines=maxOutputTokens=128|decode_ms=2756|backendEvidence=QNN_HTP_V79_FastRPC|artifact=qairt244_npu_turn_stop_quality_compare/20260525_211810|selectedPathSaved=false|db=false|tts=false|markdown=false|streaming=false
            warningLines=
            showRawOutput=false
            showRetryButton=false
            showPersistButton=false
            showTtsButton=false
            showMarkdownButton=false
            showStreamingIndicator=false
            """.trimIndent(),
            viewModel.toContractText(),
        )
    }

    @Test
    fun `rollback contract snapshot is stable`() {
        val viewModel = DevOnlyNpuPhaseH1CardViewModelMapper.from(rollbackState())

        assertEquals(
            """
            visible=false
            title=DEV NPU transient preview
            subtitle=Hidden by promotion gate
            statusLabel=ROLLBACK
            reasonLabel=reasonCode=fallback_used
            devBadge=DEV ONLY
            body=null
            detailLines=maxOutputTokens=128|decode_ms=2756|backendEvidence=QNN_HTP_V79_FastRPC|artifact=qairt244_npu_turn_stop_quality_compare/20260525_211810|selectedPathSaved=false|db=false|tts=false|markdown=false|streaming=false
            warningLines=rollback=fallback_used
            showRawOutput=false
            showRetryButton=false
            showPersistButton=false
            showTtsButton=false
            showMarkdownButton=false
            showStreamingIndicator=false
            """.trimIndent(),
            viewModel.toContractText(),
        )
    }

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

    private fun assertNoUnsafeControls(viewModel: DevOnlyNpuPhaseH1CardViewModel) {
        assertFalse(viewModel.showRawOutput)
        assertFalse(viewModel.showRetryButton)
        assertFalse(viewModel.showPersistButton)
        assertFalse(viewModel.showTtsButton)
        assertFalse(viewModel.showMarkdownButton)
        assertFalse(viewModel.showStreamingIndicator)
    }
}
