package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1ProviderTest {
    @Test
    fun `fixed provider returns default S1 success raw result`() {
        val raw = FixedNpuStandardRouteS1Provider().invoke()

        assertEquals("success", raw.status)
        assertEquals("success", raw.result)
        assertEquals(true, raw.success)
        assertEquals("success", raw.reason)
        assertEquals("こんにちは。", raw.rawOutput)
        assertEquals("こんにちは。", raw.sanitizedOutput)
        assertEquals("natural_japanese", raw.qualityClassification)
        assertTrue(raw.runDecodeReached)
        assertEquals("QNN_HTP_V79_FastRPC_native_diag", raw.npuBackendEvidence)
        assertFalse(raw.fallbackUsed)
        assertFalse(raw.timeout)
        assertFalse(raw.freshCrash)
        assertEquals(32, raw.requestedMaxOutputTokens)
        assertEquals(32, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `failure provider returns mapper compatible failure raw result`() {
        val raw = FailureNpuStandardRouteS1Provider(reason = "test_failure").invoke()
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        assertEquals("failure", raw.status)
        assertEquals("failure", raw.result)
        assertEquals(false, raw.success)
        assertEquals("test_failure", raw.reason)
        assertEquals("", raw.sanitizedOutput)
        assertFalse(raw.runDecodeReached)
        assertEquals("", raw.npuBackendEvidence)
        assertEquals(32, raw.requestedMaxOutputTokens)
        assertEquals(32, raw.effectiveMaxOutputTokens)
        assertFalse(mapped.successCriteriaMet)
        assertEquals("failure", mapped.status)
        assertEquals("test_failure", mapped.reason)
    }

    @Test
    fun `invoker default provider remains fixed provider`() {
        val mapped = NpuStandardRouteS1Mapper.map(NpuStandardRouteS1Invoker().invoke())

        assertTrue(mapped.successCriteriaMet)
        assertEquals("こんにちは。", mapped.displayText)
        assertTrue(mapped.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `invoker accepts provider interface without ChatScreen dependency`() {
        val invoker = NpuStandardRouteS1Invoker(
            provider = FailureNpuStandardRouteS1Provider(
                reason = "provider_injected_failure",
                fallbackUsed = true,
            ),
        )
        val mapped = NpuStandardRouteS1Mapper.map(invoker.invoke())

        assertFalse(mapped.successCriteriaMet)
        assertEquals("provider_injected_failure", mapped.reason)
        assertTrue(mapped.fallbackUsed)
        assertTrue(mapped.selection.sideEffects.allDisconnected)
    }
}
