package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1BridgeTest {
    @Test
    fun `bridge returns successful mapped S1 result`() {
        val result = NpuStandardRouteS1Bridge(
            invoker = NpuStandardRouteS1Invoker(provider = FixedNpuStandardRouteS1Provider()),
        ).run()

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
        ).run().selection.sideEffects

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
        val bridge = NpuStandardRouteS1Bridge(
            invoker = NpuStandardRouteS1Invoker {
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
        )

        val result = bridge.run()

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("test_failure", result.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
    }
}
