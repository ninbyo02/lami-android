package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1XmlCardContractTest {
    private val sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？"

    @Test
    fun `success xml card text equals preview host render text`() {
        val card = successCard()
        val composeModel = DevOnlyNpuPhaseH1ComposeAdapter.from(card)
        val host = DevOnlyNpuPhaseH1PreviewHost.create(composeModel)
        val xmlText = DevOnlyNpuPhaseH1XmlCardContract.renderText(
            visible = card.visible,
            renderedLines = DevOnlyNpuPhaseH1PreviewRenderer.renderLines(card),
        )

        assertTrue(host.visible)
        assertEquals(xmlText, host.renderText)
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
            xmlText,
        )
    }

    @Test
    fun `hidden xml card and preview host are empty`() {
        assertEmptyXmlAndHost(hiddenCard())
    }

    @Test
    fun `rollback xml card and preview host are empty`() {
        assertEmptyXmlAndHost(rollbackCard())
    }

    @Test
    fun `stale xml card and preview host are empty`() {
        assertEmptyXmlAndHost(staleCard())
    }

    @Test
    fun `toggle false xml card and preview host are empty`() {
        assertEmptyXmlAndHost(hiddenCard())
    }

    @Test
    fun `xml card and host never expose raw output or template tokens`() {
        val card = successCard()
        val composeModel = DevOnlyNpuPhaseH1ComposeAdapter.from(card)
        val host = DevOnlyNpuPhaseH1PreviewHost.create(composeModel)
        val xmlText = DevOnlyNpuPhaseH1XmlCardContract.renderText(
            visible = card.visible,
            renderedLines = DevOnlyNpuPhaseH1PreviewRenderer.renderLines(card),
        )

        listOf(xmlText, host.renderText, host.toContractText()).forEach { text ->
            assertFalse(text.contains("raw_output"))
            assertFalse(text.contains("<start_of_turn>"))
            assertFalse(text.contains("<end_of_turn>"))
        }
    }

    @Test
    fun `host remains disconnected from assistant and side effect routes`() {
        val host = DevOnlyNpuPhaseH1PreviewHost.create(
            DevOnlyNpuPhaseH1ComposeAdapter.from(successCard()),
        )

        assertFalse(host.showAssistantInsertion)
        assertFalse(host.showDbPersistence)
        assertFalse(host.showTts)
        assertFalse(host.showMarkdown)
        assertFalse(host.showStreaming)
        assertFalse(host.showRetry)
        assertFalse(host.showFallback)
        assertFalse(host.readsMetadata)
        assertFalse(host.runsNpu)
        assertFalse(host.engineInitialize)
        assertFalse(host.runDecode)
    }

    private fun assertEmptyXmlAndHost(card: DevOnlyNpuPhaseH1CardViewModel) {
        val composeModel = DevOnlyNpuPhaseH1ComposeAdapter.from(card)
        val host = DevOnlyNpuPhaseH1PreviewHost.create(composeModel)
        val xmlText = DevOnlyNpuPhaseH1XmlCardContract.renderText(
            visible = card.visible,
            renderedLines = DevOnlyNpuPhaseH1PreviewRenderer.renderLines(card),
        )

        assertFalse(host.visible)
        assertFalse(host.showCard)
        assertEquals("", xmlText)
        assertEquals("", host.renderText)
    }

    private fun successCard(): DevOnlyNpuPhaseH1CardViewModel =
        DevOnlyNpuPhaseH1CardViewModelMapper.from(
            DevOnlyNpuPhaseH1Presenter.present(successInput()),
        )

    private fun hiddenCard(): DevOnlyNpuPhaseH1CardViewModel =
        DevOnlyNpuPhaseH1CardViewModelMapper.from(DevOnlyNpuPhaseH1StateReducer.initial())

    private fun rollbackCard(): DevOnlyNpuPhaseH1CardViewModel =
        DevOnlyNpuPhaseH1CardViewModelMapper.from(
            DevOnlyNpuPhaseH1Presenter.present(successInput(fallbackUsed = true)),
        )

    private fun staleCard(): DevOnlyNpuPhaseH1CardViewModel =
        DevOnlyNpuPhaseH1CardViewModelMapper.from(
            DevOnlyNpuPhaseH1Presenter.present(successInput(artifactFresh = false)),
        )

    private fun successInput(
        artifactFresh: Boolean = true,
        fallbackUsed: Boolean = false,
    ): DevOnlyNpuPhaseH1UiInput =
        DevOnlyNpuPhaseH1UiInput(
            success = true,
            sanitizedOutput = sanitizedOutput,
            reasonCode = "ok",
            decodeMs = 2756L,
            backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
            maxOutputTokens = 128,
            artifactPath = "artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810",
            qualityClassification = "natural_japanese",
            artifactFresh = artifactFresh,
            fallbackUsed = fallbackUsed,
        )
}
