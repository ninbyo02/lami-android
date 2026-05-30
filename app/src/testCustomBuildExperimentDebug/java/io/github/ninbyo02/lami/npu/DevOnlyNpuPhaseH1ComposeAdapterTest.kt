package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1ComposeAdapterTest {
    private val sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？"

    @Test
    fun `success card maps to visible compose surface with sanitized body`() {
        val model = DevOnlyNpuPhaseH1ComposeAdapter.from(successCard())

        assertTrue(model.shouldShowSurface)
        assertEquals("DEV NPU transient preview", model.title)
        assertEquals(sanitizedOutput, model.body)
        assertEquals("SUCCESS", model.statusLabel)
        assertEquals("DEV ONLY", model.devBadge)
        assertTrue(model.detailLines.contains("maxOutputTokens=128"))
        assertTrue(model.detailLines.contains("db=false"))
        assertTrue(model.detailLines.contains("tts=false"))
        assertTrue(model.detailLines.contains("markdown=false"))
        assertTrue(model.detailLines.contains("streaming=false"))
        assertNoUnsafeActions(model)
    }

    @Test
    fun `rollback card does not reach compose surface`() {
        val model = DevOnlyNpuPhaseH1ComposeAdapter.from(rollbackCard())

        assertFalse(model.shouldShowSurface)
        assertNull(model.body)
        assertEquals("ROLLBACK", model.statusLabel)
        assertTrue(model.detailLines.isEmpty())
        assertNoUnsafeActions(model)
    }

    @Test
    fun `hidden card does not reach compose surface`() {
        val model = DevOnlyNpuPhaseH1ComposeAdapter.from(hiddenCard())

        assertFalse(model.shouldShowSurface)
        assertNull(model.body)
        assertEquals("HIDDEN", model.statusLabel)
        assertTrue(model.detailLines.isEmpty())
        assertNoUnsafeActions(model)
    }

    @Test
    fun `compose model never inserts into assistant list or side effect routes`() {
        val model = DevOnlyNpuPhaseH1ComposeAdapter.from(successCard())

        assertFalse(model.insertIntoAssistantList)
        assertFalse(model.persistToDb)
        assertFalse(model.speakTts)
        assertFalse(model.renderMarkdown)
        assertFalse(model.stream)
        assertFalse(model.showRetryButton)
        assertFalse(model.showFallbackButton)
    }

    @Test
    fun `raw output and template tokens do not appear in model or contract text`() {
        val model = DevOnlyNpuPhaseH1ComposeAdapter.from(successCard())

        assertFalse(model.toString().contains("raw_output"))
        assertFalse(model.toString().contains("<end_of_turn>"))
        assertFalse(model.toString().contains("<start_of_turn>"))
        assertFalse(model.toContractText().contains("raw_output"))
        assertFalse(model.toContractText().contains("<end_of_turn>"))
        assertFalse(model.toContractText().contains("<start_of_turn>"))
    }

    @Test
    fun `success compose contract snapshot is stable`() {
        val model = DevOnlyNpuPhaseH1ComposeAdapter.from(successCard())

        assertEquals(
            """
            shouldShowSurface=true
            title=DEV NPU transient preview
            subtitle=Read-only sanitized output
            statusLabel=SUCCESS
            reasonLabel=reasonCode=ok
            devBadge=DEV ONLY
            body=こんにちは！何かお手伝いできることはありますか？
            detailLines=maxOutputTokens=128|decode_ms=2756|backendEvidence=QNN_HTP_V79_FastRPC|artifact=qairt244_npu_turn_stop_quality_compare/20260525_211810|selectedPathSaved=false|db=false|tts=false|markdown=false|streaming=false
            insertIntoAssistantList=false
            persistToDb=false
            speakTts=false
            renderMarkdown=false
            stream=false
            showRetryButton=false
            showFallbackButton=false
            """.trimIndent(),
            model.toContractText(),
        )
    }

    @Test
    fun `hidden compose contract snapshot is stable`() {
        val model = DevOnlyNpuPhaseH1ComposeAdapter.from(hiddenCard())

        assertEquals(
            """
            shouldShowSurface=false
            title=DEV NPU transient preview
            subtitle=Hidden until a fresh gated artifact passes
            statusLabel=HIDDEN
            reasonLabel=reasonCode=initial
            devBadge=DEV ONLY
            body=null
            detailLines=
            insertIntoAssistantList=false
            persistToDb=false
            speakTts=false
            renderMarkdown=false
            stream=false
            showRetryButton=false
            showFallbackButton=false
            """.trimIndent(),
            model.toContractText(),
        )
    }

    private fun successCard(): DevOnlyNpuPhaseH1CardViewModel =
        DevOnlyNpuPhaseH1CardViewModelMapper.from(successState())

    private fun rollbackCard(): DevOnlyNpuPhaseH1CardViewModel =
        DevOnlyNpuPhaseH1CardViewModelMapper.from(rollbackState())

    private fun hiddenCard(): DevOnlyNpuPhaseH1CardViewModel =
        DevOnlyNpuPhaseH1CardViewModelMapper.from(DevOnlyNpuPhaseH1StateReducer.initial())

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

    private fun assertNoUnsafeActions(model: DevOnlyNpuPhaseH1ComposeModel) {
        assertFalse(model.insertIntoAssistantList)
        assertFalse(model.persistToDb)
        assertFalse(model.speakTts)
        assertFalse(model.renderMarkdown)
        assertFalse(model.stream)
        assertFalse(model.showRetryButton)
        assertFalse(model.showFallbackButton)
    }
}
