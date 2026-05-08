package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalInferenceDevDiagnosticsUiModelBuilderTest {
    @Test
    fun `trace acquire action reused is summarized as reuse`() {
        val actual = buildLocalInferenceDevDiagnosticsUiModel(
            devHeldStateText = null,
            devCloseLifecycleText = null,
            devDebugText = null,
            trace = LocalInferenceTrace(
                heldEngineHash = 123,
                holderAppInForeground = true,
                holderLastAcquireAction = "reused",
            ),
        )

        assertEquals("再利用あり", actual.heldEngineReuseSummary)
        assertEquals("存在 / foreground", actual.heldEngineStateSummary)
    }

    @Test
    fun `trace acquire action created is summarized as new engine`() {
        val actual = buildLocalInferenceDevDiagnosticsUiModel(
            devHeldStateText = null,
            devCloseLifecycleText = null,
            devDebugText = null,
            trace = LocalInferenceTrace(
                heldEngineHash = 456,
                holderAppInForeground = true,
                holderLastAcquireAction = "created",
            ),
        )

        assertEquals("新規作成", actual.heldEngineReuseSummary)
        assertEquals("存在 / foreground", actual.heldEngineStateSummary)
    }
}
