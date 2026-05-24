package io.github.ninbyo02.lami.npu

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DevOnlyNpuRouteAdapterTest {
    @Test
    fun `default max output tokens is the current bounded phase`() {
        assertEquals(128, DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS)
    }

    @Test
    fun `blocked adapter returns not connected result`() = runBlocking {
        val result = BlockedDevOnlyNpuRouteAdapter().runOnce(prompt = "Hello")

        assertFalse(result.success)
        assertEquals("adapter_not_connected", result.reasonCode)
        assertEquals("Hello", result.prompt)
        assertEquals(DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS, result.maxOutputTokens)
        assertNull(result.output)
        assertNull(result.elapsedMs)
        assertNull(result.decodeElapsedMs)
        assertNull(result.backendEvidence)
        assertNull(result.artifactPath)
        assertFalse(result.freshCrash)
        assertFalse(result.timeout)
    }

    @Test
    fun `blocked adapter preserves explicit max tokens without running`() = runBlocking {
        val result = BlockedDevOnlyNpuRouteAdapter().runOnce(
            prompt = "Hi",
            maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
            timeoutMs = 1_000L,
        )

        assertFalse(result.success)
        assertEquals("adapter_not_connected", result.reasonCode)
        assertEquals(DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS, result.maxOutputTokens)
        assertFalse(result.timeout)
    }

    @Test
    fun `result schema supports timeout and fresh crash flags`() {
        val result = DevOnlyNpuRouteResult(
            success = false,
            output = null,
            reasonCode = "timeout",
            elapsedMs = 30_000L,
            decodeElapsedMs = null,
            prompt = "Hello",
            maxOutputTokens = DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
            backendEvidence = null,
            artifactPath = "artifacts/dev_only_npu_route/timeout",
            freshCrash = true,
            timeout = true,
        )

        assertFalse(result.success)
        assertEquals("timeout", result.reasonCode)
        assertEquals(30_000L, result.elapsedMs)
        assertNull(result.decodeElapsedMs)
        assertEquals("artifacts/dev_only_npu_route/timeout", result.artifactPath)
        assertEquals(true, result.freshCrash)
        assertEquals(true, result.timeout)
    }
}
