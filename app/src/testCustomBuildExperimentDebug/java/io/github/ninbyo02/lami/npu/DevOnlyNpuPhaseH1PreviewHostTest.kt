package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1PreviewHostTest {
    private val sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？"

    @Test
    fun `success host state renders sanitized read only preview`() {
        val host = DevOnlyNpuPhaseH1PreviewHost.create(successComposeModel())

        assertTrue(host.visible)
        assertTrue(host.showCard)
        assertTrue(host.renderText.contains("DEV ONLY\nDEV NPU transient preview"))
        assertTrue(host.renderText.contains("Status: SUCCESS"))
        assertTrue(host.renderText.contains(sanitizedOutput))
        assertTrue(host.renderText.contains("- maxOutputTokens=128"))
        assertNoUnsafeHostActions(host)
    }

    @Test
    fun `hidden host state renders nothing`() {
        val host = DevOnlyNpuPhaseH1PreviewHost.create(hiddenComposeModel())

        assertHiddenHost(host)
    }

    @Test
    fun `rollback host state renders nothing`() {
        val host = DevOnlyNpuPhaseH1PreviewHost.create(rollbackComposeModel())

        assertHiddenHost(host)
    }

    @Test
    fun `stale host state renders nothing`() {
        val host = DevOnlyNpuPhaseH1PreviewHost.create(staleComposeModel())

        assertHiddenHost(host)
    }

    @Test
    fun `toggle false host state renders nothing`() {
        val host = DevOnlyNpuPhaseH1PreviewHost.create(toggleFalseComposeModel())

        assertHiddenHost(host)
    }

    @Test
    fun `host never exposes assistant insertion or side effect actions`() {
        val host = DevOnlyNpuPhaseH1PreviewHost.create(successComposeModel())

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

    @Test
    fun `raw output and template tokens do not appear in host state`() {
        val host = DevOnlyNpuPhaseH1PreviewHost.create(successComposeModel())

        assertFalse(host.toString().contains("raw_output"))
        assertFalse(host.toString().contains("<start_of_turn>"))
        assertFalse(host.toString().contains("<end_of_turn>"))
        assertFalse(host.toContractText().contains("raw_output"))
        assertFalse(host.toContractText().contains("<start_of_turn>"))
        assertFalse(host.toContractText().contains("<end_of_turn>"))
    }

    @Test
    fun `success host contract snapshot is stable`() {
        val host = DevOnlyNpuPhaseH1PreviewHost.create(successComposeModel())

        assertEquals(
            """
            visible=true
            showCard=true
            renderText=DEV ONLY
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
            showRetry=false
            showFallback=false
            showAssistantInsertion=false
            showDbPersistence=false
            showTts=false
            showMarkdown=false
            showStreaming=false
            readsMetadata=false
            runsNpu=false
            engineInitialize=false
            runDecode=false
            """.trimIndent(),
            host.toContractText(),
        )
    }

    @Test
    fun `hidden host contract snapshot is stable`() {
        val host = DevOnlyNpuPhaseH1PreviewHost.create(hiddenComposeModel())

        assertEquals(
            """
            visible=false
            showCard=false
            renderText=null
            showRetry=false
            showFallback=false
            showAssistantInsertion=false
            showDbPersistence=false
            showTts=false
            showMarkdown=false
            showStreaming=false
            readsMetadata=false
            runsNpu=false
            engineInitialize=false
            runDecode=false
            """.trimIndent(),
            host.toContractText(),
        )
    }

    private fun successComposeModel(): DevOnlyNpuPhaseH1ComposeModel =
        DevOnlyNpuPhaseH1ComposeAdapter.from(successCard())

    private fun hiddenComposeModel(): DevOnlyNpuPhaseH1ComposeModel =
        DevOnlyNpuPhaseH1ComposeAdapter.from(
            DevOnlyNpuPhaseH1CardViewModelMapper.from(DevOnlyNpuPhaseH1StateReducer.initial()),
        )

    private fun rollbackComposeModel(): DevOnlyNpuPhaseH1ComposeModel =
        DevOnlyNpuPhaseH1ComposeAdapter.from(rollbackCard())

    private fun staleComposeModel(): DevOnlyNpuPhaseH1ComposeModel =
        DevOnlyNpuPhaseH1ComposeAdapter.from(staleCard())

    private fun toggleFalseComposeModel(): DevOnlyNpuPhaseH1ComposeModel =
        hiddenComposeModel()

    private fun successCard(): DevOnlyNpuPhaseH1CardViewModel =
        DevOnlyNpuPhaseH1CardViewModelMapper.from(
            DevOnlyNpuPhaseH1Presenter.present(successInput()),
        )

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

    private fun assertHiddenHost(host: DevOnlyNpuPhaseH1PreviewHostState) {
        assertFalse(host.visible)
        assertFalse(host.showCard)
        assertEquals("", host.renderText)
        assertNoUnsafeHostActions(host)
    }

    private fun assertNoUnsafeHostActions(host: DevOnlyNpuPhaseH1PreviewHostState) {
        assertFalse(host.showRetry)
        assertFalse(host.showFallback)
        assertFalse(host.showAssistantInsertion)
        assertFalse(host.showDbPersistence)
        assertFalse(host.showTts)
        assertFalse(host.showMarkdown)
        assertFalse(host.showStreaming)
        assertFalse(host.readsMetadata)
        assertFalse(host.runsNpu)
        assertFalse(host.engineInitialize)
        assertFalse(host.runDecode)
    }
}
