package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealNpuStandardRouteS1ProviderTest {
    @Test
    fun `real provider skeleton implements S1 provider contract`() {
        val provider: NpuStandardRouteS1Provider = RealNpuStandardRouteS1Provider()

        assertEquals("real_provider_not_implemented", provider.invoke().reason)
    }

    @Test
    fun `real provider skeleton returns explicit not implemented failure`() {
        val raw = RealNpuStandardRouteS1Provider().invoke()

        assertEquals("failure", raw.status)
        assertEquals("failure", raw.result)
        assertEquals(false, raw.success)
        assertEquals("real_provider_not_implemented", raw.reason)
        assertEquals("", raw.rawOutput)
        assertEquals("", raw.sanitizedOutput)
        assertEquals("unknown", raw.qualityClassification)
        assertFalse(raw.runDecodeReached)
        assertEquals("", raw.npuBackendEvidence)
        assertFalse(raw.fallbackUsed)
        assertFalse(raw.timeout)
        assertFalse(raw.freshCrash)
        assertEquals(32, raw.requestedMaxOutputTokens)
        assertEquals(32, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `real provider skeleton maps to failed S1 result without side effects`() {
        val result = NpuStandardRouteS1Mapper.map(RealNpuStandardRouteS1Provider().invoke())

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("real_provider_not_implemented", result.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
        assertFalse(result.selection.sideEffects.db)
        assertFalse(result.selection.sideEffects.tts)
        assertFalse(result.selection.sideEffects.markdown)
        assertFalse(result.selection.sideEffects.streaming)
        assertFalse(result.selection.sideEffects.backendNpuPersisted)
        assertFalse(result.selection.sideEffects.conversationHistorySaved)
    }

    @Test
    fun `custom build experiment default provider selects real provider scaffold`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider().invoke()
        val result = NpuStandardRouteS1Mapper.map(raw)

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", raw.status)
        assertEquals("real_provider_not_implemented", raw.reason)
        assertEquals("real_provider_not_implemented", result.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `custom build experiment invoker default propagates real provider scaffold failure`() {
        val result = NpuStandardRouteS1Mapper.map(NpuStandardRouteS1Invoker().invoke())

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("real_provider_not_implemented", result.reason)
        assertTrue(result.selection.sideEffects.allDisconnected)
    }
}
