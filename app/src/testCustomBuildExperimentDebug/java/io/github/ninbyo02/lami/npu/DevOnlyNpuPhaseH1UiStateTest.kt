package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuPhaseH1UiStateTest {
    @Test
    fun `success with sanitized output is visible transient preview`() {
        val state = DevOnlyNpuPhaseH1Presenter.present(
            input(sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？"),
        )

        assertTrue(state.visible)
        assertEquals("DEV NPU transient preview", state.devLabel)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.SUCCESS, state.status)
        assertEquals("こんにちは！何かお手伝いできることはありますか？", state.outputPreview)
        assertEquals("success", state.reasonCode)
        assertTransientOnly(state)
    }

    @Test
    fun `raw output is not exposed in ui state`() {
        val state = DevOnlyNpuPhaseH1Presenter.present(
            input(sanitizedOutput = "何かご用でしょうか？"),
        )

        assertEquals("何かご用でしょうか？", state.outputPreview)
        assertFalse(state.toString().contains("raw artifact"))
        assertFalse(state.toString().contains("<end_of_turn>"))
    }

    @Test
    fun `side effect flags are always false even when input tries to enable them`() {
        val state = DevOnlyNpuPhaseH1Presenter.present(
            input(
                shouldPersistToDb = true,
                shouldSpeakTts = true,
                shouldRenderMarkdown = true,
                shouldStream = true,
            ),
        )

        assertEquals(DevOnlyNpuPhaseH1UiState.Status.ROLLBACK, state.status)
        assertEquals("db_connected", state.reasonCode)
        assertTransientOnly(state)
    }

    @Test
    fun `failure shows reason only without output preview`() {
        val state = DevOnlyNpuPhaseH1Presenter.present(
            input(
                success = false,
                sanitizedOutput = null,
                reasonCode = "adapter_failure",
            ),
        )

        assertTrue(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.FAILURE, state.status)
        assertNull(state.outputPreview)
        assertEquals("adapter_failure", state.reasonCode)
        assertTransientOnly(state)
    }

    @Test
    fun `rollback is warning state without output preview`() {
        val state = DevOnlyNpuPhaseH1Presenter.present(
            input(
                rollback = true,
                sanitizedOutput = "表示してはいけない内容",
                reasonCode = "rollback_requested",
            ),
        )

        assertFalse(state.visible)
        assertTrue(state.rollback)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.ROLLBACK, state.status)
        assertNull(state.outputPreview)
        assertEquals("rollback_requested", state.reasonCode)
        assertTransientOnly(state)
    }

    @Test
    fun `empty sanitized output is hidden rollback`() {
        val state = DevOnlyNpuPhaseH1Presenter.present(
            input(sanitizedOutput = " "),
        )

        assertFalse(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.ROLLBACK, state.status)
        assertNull(state.outputPreview)
        assertEquals("empty_sanitized_output", state.reasonCode)
        assertTransientOnly(state)
    }

    @Test
    fun `max output tokens backend evidence and artifact are formatted for preview`() {
        val state = DevOnlyNpuPhaseH1Presenter.present(
            input(
                decodeMs = 2345L,
                backendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                artifactPath = "artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810/summary.md",
            ),
        )

        assertEquals("decode_ms=2345", state.decodeMsText)
        assertEquals("backendEvidence=QNN_HTP_V79_FastRPC", state.backendEvidenceText)
        assertEquals("maxOutputTokens=128", state.maxOutputTokensText)
        assertEquals("artifact=20260525_211810/summary.md", state.artifactText)
    }

    @Test
    fun `standard route connected blocks preview`() {
        val state = DevOnlyNpuPhaseH1Presenter.present(
            input(standardRouteConnected = true),
        )

        assertFalse(state.visible)
        assertEquals(DevOnlyNpuPhaseH1UiState.Status.ROLLBACK, state.status)
        assertEquals("standard_route_connected", state.reasonCode)
        assertNull(state.outputPreview)
        assertTransientOnly(state)
    }

    private fun assertTransientOnly(state: DevOnlyNpuPhaseH1UiState) {
        assertFalse(state.shouldPersistToDb)
        assertFalse(state.shouldSpeakTts)
        assertFalse(state.shouldRenderMarkdown)
        assertFalse(state.shouldStream)
    }

    private fun input(
        success: Boolean = true,
        sanitizedOutput: String? = "何かご用でしょうか？",
        reasonCode: String = "success",
        decodeMs: Long? = 10L,
        backendEvidence: String? = "QNN_HTP_V79_FastRPC_native_diag",
        maxOutputTokens: Int = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
        artifactPath: String? = "artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810/summary.md",
        qualityClassification: String = "natural_japanese",
        rollback: Boolean = false,
        artifactFresh: Boolean = true,
        fallbackUsed: Boolean = false,
        timeout: Boolean = false,
        freshCrash: Boolean = false,
        standardRouteConnected: Boolean = false,
        normalUiRouteConnected: Boolean = false,
        shouldPersistToDb: Boolean = false,
        shouldSpeakTts: Boolean = false,
        shouldRenderMarkdown: Boolean = false,
        shouldStream: Boolean = false,
    ): DevOnlyNpuPhaseH1UiInput =
        DevOnlyNpuPhaseH1UiInput(
            success = success,
            sanitizedOutput = sanitizedOutput,
            reasonCode = reasonCode,
            decodeMs = decodeMs,
            backendEvidence = backendEvidence,
            maxOutputTokens = maxOutputTokens,
            artifactPath = artifactPath,
            qualityClassification = qualityClassification,
            rollback = rollback,
            artifactFresh = artifactFresh,
            fallbackUsed = fallbackUsed,
            timeout = timeout,
            freshCrash = freshCrash,
            standardRouteConnected = standardRouteConnected,
            normalUiRouteConnected = normalUiRouteConnected,
            shouldPersistToDb = shouldPersistToDb,
            shouldSpeakTts = shouldSpeakTts,
            shouldRenderMarkdown = shouldRenderMarkdown,
            shouldStream = shouldStream,
        )
}
