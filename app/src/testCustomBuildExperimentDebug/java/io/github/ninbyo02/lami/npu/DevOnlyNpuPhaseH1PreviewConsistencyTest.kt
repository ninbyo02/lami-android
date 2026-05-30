package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1PreviewConsistencyTest {
    private val sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？"

    @Test
    fun `success XML renderer host and compose render text are aligned`() {
        val snapshot = DevOnlyNpuPhaseH1PreviewConsistency.from(successCard())

        assertTrue(snapshot.visible)
        assertTrue(snapshot.showCard)
        assertTrue(snapshot.shouldShowSurface)
        assertEquals(snapshot.xmlCardText, snapshot.previewRendererText)
        assertEquals(snapshot.xmlCardText, snapshot.previewHostText)
        assertEquals(snapshot.xmlCardText, snapshot.composeRenderText)
        assertEquals(successText(), snapshot.xmlCardText)
        assertSafe(snapshot)
    }

    @Test
    fun `hidden is empty across XML renderer host and compose render text`() {
        assertEmptySnapshot(DevOnlyNpuPhaseH1PreviewConsistency.from(hiddenCard()))
    }

    @Test
    fun `rollback is empty across XML renderer host and compose render text`() {
        assertEmptySnapshot(DevOnlyNpuPhaseH1PreviewConsistency.from(rollbackCard()))
    }

    @Test
    fun `stale is empty across XML renderer host and compose render text`() {
        assertEmptySnapshot(DevOnlyNpuPhaseH1PreviewConsistency.from(staleCard()))
    }

    @Test
    fun `toggle false is empty across XML renderer host and compose render text`() {
        assertEmptySnapshot(DevOnlyNpuPhaseH1PreviewConsistency.from(hiddenCard()))
    }

    @Test
    fun `raw output and turn template artifacts are absent across all layers`() {
        val snapshot = DevOnlyNpuPhaseH1PreviewConsistency.from(successCard())
        val text = snapshot.allContractText()

        assertFalse(text.contains("raw_output"))
        assertFalse(text.contains("<start_of_turn>"))
        assertFalse(text.contains("<end_of_turn>"))
        assertFalse(text.contains("template_artifact"))
        assertFalse(text.contains("assistant message list", ignoreCase = true))
    }

    @Test
    fun `assistant list and side effect routes stay false across all layers`() {
        val snapshot = DevOnlyNpuPhaseH1PreviewConsistency.from(successCard())

        assertFalse(snapshot.insertIntoAssistantList)
        assertFalse(snapshot.persistToDb)
        assertFalse(snapshot.speakTts)
        assertFalse(snapshot.renderMarkdown)
        assertFalse(snapshot.stream)
        assertFalse(snapshot.showRetry)
        assertFalse(snapshot.showFallback)
        assertFalse(snapshot.readsMetadata)
        assertFalse(snapshot.runsNpu)
        assertFalse(snapshot.engineInitialize)
        assertFalse(snapshot.runDecode)
        assertTrue(snapshot.composeContractText.contains("insertIntoAssistantList=false"))
        assertTrue(snapshot.composeContractText.contains("persistToDb=false"))
        assertTrue(snapshot.composeContractText.contains("speakTts=false"))
        assertTrue(snapshot.composeContractText.contains("renderMarkdown=false"))
        assertTrue(snapshot.composeContractText.contains("stream=false"))
        assertTrue(snapshot.hostContractText.contains("showAssistantInsertion=false"))
        assertTrue(snapshot.hostContractText.contains("showDbPersistence=false"))
        assertTrue(snapshot.hostContractText.contains("showTts=false"))
        assertTrue(snapshot.hostContractText.contains("showMarkdown=false"))
        assertTrue(snapshot.hostContractText.contains("showStreaming=false"))
    }

    @Test
    fun `success consistency snapshot is stable`() {
        val snapshot = DevOnlyNpuPhaseH1PreviewConsistency.from(successCard())

        assertEquals(
            """
            visible=true
            showCard=true
            shouldShowSurface=true
            xmlCardText=DEV ONLY
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
            previewRendererText=DEV ONLY
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
            previewHostText=DEV ONLY
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
            composeRenderText=DEV ONLY
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
            insertIntoAssistantList=false
            persistToDb=false
            speakTts=false
            renderMarkdown=false
            stream=false
            showRetry=false
            showFallback=false
            readsMetadata=false
            runsNpu=false
            engineInitialize=false
            runDecode=false
            """.trimIndent(),
            snapshot.toSnapshotText(),
        )
    }

    private fun assertEmptySnapshot(snapshot: DevOnlyNpuPhaseH1PreviewConsistencySnapshot) {
        assertFalse(snapshot.visible)
        assertFalse(snapshot.showCard)
        assertFalse(snapshot.shouldShowSurface)
        assertEquals("", snapshot.xmlCardText)
        assertEquals("", snapshot.previewRendererText)
        assertEquals("", snapshot.previewHostText)
        assertEquals("", snapshot.composeRenderText)
        assertSafe(snapshot)
    }

    private fun assertSafe(snapshot: DevOnlyNpuPhaseH1PreviewConsistencySnapshot) {
        assertFalse(snapshot.insertIntoAssistantList)
        assertFalse(snapshot.persistToDb)
        assertFalse(snapshot.speakTts)
        assertFalse(snapshot.renderMarkdown)
        assertFalse(snapshot.stream)
        assertFalse(snapshot.showRetry)
        assertFalse(snapshot.showFallback)
        assertFalse(snapshot.readsMetadata)
        assertFalse(snapshot.runsNpu)
        assertFalse(snapshot.engineInitialize)
        assertFalse(snapshot.runDecode)
    }

    private fun successText(): String =
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
        """.trimIndent()

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
