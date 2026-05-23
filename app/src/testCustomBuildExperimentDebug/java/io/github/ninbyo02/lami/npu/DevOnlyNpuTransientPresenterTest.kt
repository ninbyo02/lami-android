package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuTransientPresenterTest {
    @Test
    fun `success remains transient and is not persisted or spoken`() {
        val state = DevOnlyNpuTransientPresenter.present(
            model(
                status = DevOnlyNpuRouteDisplayStatus.SUCCESS,
                output = "ok",
                reasonCode = "ok",
            ),
        )

        assertTrue(state.visible)
        assertEquals(DevOnlyNpuRouteDisplayStatus.SUCCESS, state.status)
        assertEquals("ok", state.outputPreview)
        assertTransientOnly(state)
    }

    @Test
    fun `blocked state is transient error display only`() {
        val state = DevOnlyNpuTransientPresenter.present(
            model(
                status = DevOnlyNpuRouteDisplayStatus.BLOCKED,
                reasonCode = "adapter_not_connected",
                message = "NPU route adapter is not connected",
            ),
        )

        assertEquals(DevOnlyNpuRouteDisplayStatus.BLOCKED, state.status)
        assertEquals("adapter_not_connected", state.reasonCode)
        assertEquals("NPU route adapter is not connected", state.message)
        assertTransientOnly(state)
    }

    @Test
    fun `timeout state remains visible and transient`() {
        val state = DevOnlyNpuTransientPresenter.present(
            model(
                status = DevOnlyNpuRouteDisplayStatus.TIMEOUT,
                reasonCode = "timeout",
            ),
        )

        assertEquals(DevOnlyNpuRouteDisplayStatus.TIMEOUT, state.status)
        assertTrue(state.visible)
        assertTransientOnly(state)
    }

    @Test
    fun `crash state remains visible and transient`() {
        val state = DevOnlyNpuTransientPresenter.present(
            model(
                status = DevOnlyNpuRouteDisplayStatus.CRASH,
                reasonCode = "fresh_crash",
            ),
        )

        assertEquals(DevOnlyNpuRouteDisplayStatus.CRASH, state.status)
        assertTrue(state.visible)
        assertTransientOnly(state)
    }

    @Test
    fun `error state remains visible and transient`() {
        val state = DevOnlyNpuTransientPresenter.present(
            model(
                status = DevOnlyNpuRouteDisplayStatus.ERROR,
                reasonCode = "unexpected_failure",
            ),
        )

        assertEquals(DevOnlyNpuRouteDisplayStatus.ERROR, state.status)
        assertTrue(state.visible)
        assertTransientOnly(state)
    }

    @Test
    fun `null output remains null preview`() {
        val state = DevOnlyNpuTransientPresenter.present(
            model(output = null),
        )

        assertNull(state.outputPreview)
    }

    @Test
    fun `debug details include reason elapsed backend and artifact`() {
        val state = DevOnlyNpuTransientPresenter.present(
            model(
                reasonCode = "adapter_not_connected",
                elapsedText = "elapsed_ms=10 decode_elapsed_ms=2",
                backendEvidenceText = "backendEvidence=QNN",
                artifactText = "artifactPath=artifacts/dev",
            ),
        )

        assertTrue(state.debugDetails.contains("reasonCode=adapter_not_connected"))
        assertTrue(state.debugDetails.contains("elapsed_ms=10 decode_elapsed_ms=2"))
        assertTrue(state.debugDetails.contains("backendEvidence=QNN"))
        assertTrue(state.debugDetails.contains("artifactPath=artifacts/dev"))
    }

    private fun assertTransientOnly(state: DevOnlyNpuTransientUiState) {
        assertFalse(state.shouldPersistToDb)
        assertFalse(state.shouldSpeakTts)
        assertFalse(state.shouldRenderMarkdown)
        assertFalse(state.shouldStream)
    }

    private fun model(
        title: String = "DEV NPU route blocked",
        message: String = "NPU route adapter is not connected",
        status: DevOnlyNpuRouteDisplayStatus = DevOnlyNpuRouteDisplayStatus.BLOCKED,
        output: String? = null,
        reasonCode: String = "adapter_not_connected",
        elapsedText: String = "elapsed_ms=unknown decode_elapsed_ms=unknown",
        backendEvidenceText: String = "backendEvidence=none",
        artifactText: String = "artifactPath=none",
    ): DevOnlyNpuRouteDisplayModel =
        DevOnlyNpuRouteDisplayModel(
            title = title,
            message = message,
            status = status,
            output = output,
            reasonCode = reasonCode,
            elapsedText = elapsedText,
            backendEvidenceText = backendEvidenceText,
            artifactText = artifactText,
        )
}
