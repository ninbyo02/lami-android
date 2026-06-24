package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Persistent {
    @Test
    fun `non streaming repeat remains separate from persistent reuse`() {
        val text = buildNpuNonStreamingRepeatedStabilitySummaryCopyText(
            NpuNonStreamingRepeatedStabilityState(),
        )

        assertTrue(text.contains("true_engine_persistent_reuse=false"))
        assertTrue(text.contains("engine_reuse_observed=unavailable"))
        assertTrue(text.contains("true_engine_probe_status=disabled_or_blocked"))
    }
}

class ChatScreen {
    @Test
    fun `dev diagnostics exposes non streaming repeat labels`() {
        assertEquals("Run Non-Streaming Repeat Test", NPU_NON_STREAMING_REPEATED_STABILITY_RUN_LABEL)
        assertEquals(
            "Copy Non-Streaming Repeat Summary",
            NPU_NON_STREAMING_REPEATED_STABILITY_COPY_SUMMARY_LABEL,
        )
        assertEquals(
            "Copy Non-Streaming Repeat Full Dump",
            NPU_NON_STREAMING_REPEATED_STABILITY_COPY_FULL_DUMP_LABEL,
        )
        assertEquals("▶ DEV診断を表示", npuStandardRouteDevDiagnosticsToggleLabel(expanded = false))
        assertEquals("▼ DEV診断を隠す", npuStandardRouteDevDiagnosticsToggleLabel(expanded = true))
    }
}
