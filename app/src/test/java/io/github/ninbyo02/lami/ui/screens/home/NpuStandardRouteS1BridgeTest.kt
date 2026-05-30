package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1BridgeTest {
    private val userPrompt = "好きな色を一つだけ答えてください"

    @Test
    fun `bridge returns successful mapped S1 result`() {
        val result = NpuStandardRouteS1Bridge(
            invoker = NpuStandardRouteS1Invoker(provider = FixedNpuStandardRouteS1Provider()),
        ).run(userPrompt = userPrompt)

        assertTrue(result.successCriteriaMet)
        assertEquals("success", result.status)
        assertEquals("success", result.reason)
        assertEquals("こんにちは。", result.displayText)
        assertEquals("こんにちは。", result.sanitizedOutput)
        assertEquals("QNN_HTP_V79_FastRPC_native_diag", result.npuBackendEvidence)
        assertTrue(result.runDecodeReached)
        assertFalse(result.fallbackUsed)
        assertFalse(result.timeout)
        assertFalse(result.freshCrash)
    }

    @Test
    fun `bridge keeps S1 side effects disconnected`() {
        val sideEffects = NpuStandardRouteS1Bridge(
            invoker = NpuStandardRouteS1Invoker(provider = FixedNpuStandardRouteS1Provider()),
        ).run(userPrompt = userPrompt).selection.sideEffects

        assertTrue(sideEffects.allDisconnected)
        assertFalse(sideEffects.db)
        assertFalse(sideEffects.tts)
        assertFalse(sideEffects.markdown)
        assertFalse(sideEffects.streaming)
        assertFalse(sideEffects.backendNpuPersisted)
        assertFalse(sideEffects.conversationHistorySaved)
    }

    @Test
    fun `bridge uses injected invoker without owning side effects`() {
        val traces = mutableListOf<String>()
        val bridge = NpuStandardRouteS1Bridge(
            invoker = NpuStandardRouteS1Invoker(
                provider = NpuStandardRouteS1Provider { receivedPrompt, _ ->
                    assertEquals(userPrompt, receivedPrompt)
                    NpuStandardRouteS1RawResult(
                        status = "failure",
                        result = "failure",
                        success = false,
                        reason = "test_failure",
                        rawOutput = "",
                        sanitizedOutput = "",
                        qualityClassification = "unknown",
                        runDecodeReached = false,
                        npuBackendEvidence = "",
                        fallbackUsed = false,
                        timeout = false,
                        freshCrash = false,
                        requestedMaxOutputTokens = 32,
                        effectiveMaxOutputTokens = 32,
                    )
                },
                trace = traces::add,
            ),
            trace = traces::add,
        )

        val result = bridge.run(userPrompt = userPrompt)

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("test_failure", result.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
        assertTrue(traces.any { it.contains("NPU_REAL_PROMPT bridge_prompt_hash=") })
        assertTrue(traces.any { it.contains("NPU_REAL_PROMPT invoker_prompt_hash=") })
        assertFalse(traces.joinToString("\n").contains(userPrompt))
    }
}
