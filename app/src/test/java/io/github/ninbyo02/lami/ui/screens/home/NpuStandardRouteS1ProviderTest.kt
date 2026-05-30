package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
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
    fun `invoker default provider follows build variant provider selection`() {
        val raw = NpuStandardRouteS1Invoker().invoke()
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            assertFalse(mapped.successCriteriaMet)
            assertEquals("dev_only_entry_unavailable", mapped.reason)
        } else {
            assertTrue(mapped.successCriteriaMet)
            assertEquals("こんにちは。", mapped.displayText)
        }
        assertTrue(mapped.selection.sideEffects.allDisconnected)
    }

    @Test
    fun `default provider follows build variant provider selection`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider().invoke()
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            assertFalse(mapped.successCriteriaMet)
            assertEquals("failure", raw.status)
            assertEquals("dev_only_entry_unavailable", raw.reason)
        } else {
            assertTrue(mapped.successCriteriaMet)
            assertEquals("success", raw.status)
            assertEquals("こんにちは。", raw.sanitizedOutput)
            assertEquals("QNN_HTP_V79_FastRPC_native_diag", raw.npuBackendEvidence)
        }
    }

    @Test
    fun `provider selector uses fixed provider when S1 gate is disabled`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider(s1GateEnabled = false).invoke()
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        assertTrue(mapped.successCriteriaMet)
        assertEquals("success", raw.status)
        assertEquals("こんにちは。", raw.sanitizedOutput)
    }

    @Test
    fun `provider selector uses real provider path when S1 gate is enabled`() {
        val raw = NpuStandardRouteS1ProviderSelector.defaultProvider(s1GateEnabled = true).invoke()
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        assertFalse(mapped.successCriteriaMet)
        assertEquals("failure", raw.status)
        assertEquals("dev_only_entry_unavailable", raw.reason)
    }

    @Test
    fun `provider selector for Settings mode keeps standard OFF fixed and S1 real while preserving custom compatibility`() {
        val offRaw = NpuStandardRouteS1ProviderSelector.defaultProviderForMode(NpuStandardRouteMode.OFF).invoke()
        val s1Raw = NpuStandardRouteS1ProviderSelector.defaultProviderForMode(NpuStandardRouteMode.S1_ONLY).invoke()

        if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
            assertEquals("failure", offRaw.status)
            assertEquals("dev_only_entry_unavailable", offRaw.reason)
        } else {
            assertEquals("success", offRaw.status)
            assertEquals("こんにちは。", offRaw.sanitizedOutput)
        }
        assertEquals("failure", s1Raw.status)
        assertEquals("dev_only_entry_unavailable", s1Raw.reason)
    }

    @Test
    fun `real provider class is resolvable from debug source set`() {
        val providerClass = Class.forName(NpuStandardRouteS1ProviderSelector.REAL_PROVIDER_CLASS_NAME)

        assertTrue(NpuStandardRouteS1Provider::class.java.isAssignableFrom(providerClass))
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
