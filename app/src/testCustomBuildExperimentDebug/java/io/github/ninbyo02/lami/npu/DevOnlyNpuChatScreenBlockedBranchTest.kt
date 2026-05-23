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
}
