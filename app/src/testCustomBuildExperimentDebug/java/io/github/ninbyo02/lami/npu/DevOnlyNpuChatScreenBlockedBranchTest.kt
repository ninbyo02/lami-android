package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuChatScreenBlockedBranchTest {
    @Test
    fun `valid prompt returns blocked transient summary only`() {
        val summary = DevOnlyNpuChatScreenBlockedBranch.run("Hello")

        assertTrue(summary.contains("status=BLOCKED"))
        assertTrue(summary.contains("reason=adapter_not_connected"))
        assertTrue(summary.contains("db=false"))
        assertTrue(summary.contains("tts=false"))
        assertTrue(summary.contains("markdown=false"))
        assertTrue(summary.contains("stream=false"))
    }

    @Test
    fun `invalid prompt is blocked before adapter side effects`() {
        val summary = DevOnlyNpuChatScreenBlockedBranch.run("こんにちは")

        assertTrue(summary.contains("status=BLOCKED"))
        assertTrue(summary.contains("reason=gate_blocked:VALIDATOR_INVALID"))
        assertTrue(summary.contains("db=false"))
        assertTrue(summary.contains("tts=false"))
        assertTrue(summary.contains("markdown=false"))
        assertTrue(summary.contains("stream=false"))
    }

    @Test
    fun `hidden npu safety lines keep standard route and side effects disconnected`() {
        val safetyLines = DevOnlyNpuChatScreenBlockedBranch.hiddenNpuNoStandardRouteSafetyLines()

        assertTrue(safetyLines.contains("normal_ui_route_connected=false"))
        assertTrue(safetyLines.contains("standard_route_connected=false"))
        assertTrue(safetyLines.contains("conversation_created=no"))
        assertTrue(safetyLines.contains("generate_response=no"))
        assertTrue(safetyLines.contains("selected_path_npu_normal_route=no"))
        assertTrue(safetyLines.contains("db=false"))
        assertTrue(safetyLines.contains("tts=false"))
        assertTrue(safetyLines.contains("markdown=false"))
        assertTrue(safetyLines.contains("streaming=false"))
        assertTrue(safetyLines.none { it == "normal_ui_route_connected=true" })
        assertTrue(safetyLines.none { it == "standard_route_connected=true" })
    }
}
