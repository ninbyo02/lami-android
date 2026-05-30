package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1InvokerTest {
    private val userPrompt = "好きな色を一つだけ答えてください"

    @Test
    fun `invoker returns mapper compatible success raw result`() {
        val raw = NpuStandardRouteS1Invoker(provider = FixedNpuStandardRouteS1Provider()).invoke(userPrompt)
        val mapped = NpuStandardRouteS1Mapper.map(raw)

        assertTrue(mapped.successCriteriaMet)
        assertEquals("success", raw.status)
        assertEquals("success", raw.result)
        assertEquals(true, raw.success)
        assertEquals("success", raw.reason)
        assertEquals("こんにちは。", raw.sanitizedOutput)
        assertEquals("こんにちは。", mapped.displayText)
    }

    @Test
    fun `invoker preserves NPU evidence and max output`() {
        val raw = NpuStandardRouteS1Invoker(provider = FixedNpuStandardRouteS1Provider()).invoke(userPrompt)

        assertTrue(raw.runDecodeReached)
        assertEquals("QNN_HTP_V79_FastRPC_native_diag", raw.npuBackendEvidence)
        assertEquals(32, raw.requestedMaxOutputTokens)
        assertEquals(32, raw.effectiveMaxOutputTokens)
    }

    @Test
    fun `invoker result maps with side effects disconnected`() {
        val mapped = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1Invoker(provider = FixedNpuStandardRouteS1Provider()).invoke(userPrompt),
        )

        assertTrue(mapped.selection.sideEffects.allDisconnected)
        assertFalse(mapped.selection.sideEffects.db)
        assertFalse(mapped.selection.sideEffects.tts)
        assertFalse(mapped.selection.sideEffects.markdown)
        assertFalse(mapped.selection.sideEffects.streaming)
        assertFalse(mapped.selection.sideEffects.backendNpuPersisted)
        assertFalse(mapped.selection.sideEffects.conversationHistorySaved)
    }

    @Test
    fun `invoker can be supplied a test raw result without ChatScreen dependency`() {
        val traces = mutableListOf<String>()
        val invoker = NpuStandardRouteS1Invoker(
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
        )

        val mapped = NpuStandardRouteS1Mapper.map(invoker.invoke(userPrompt))

        assertFalse(mapped.successCriteriaMet)
        assertEquals("failure", mapped.status)
        assertEquals("test_failure", mapped.reason)
        assertTrue(traces.any { it.contains("NPU_REAL_PROMPT invoker_prompt_hash=") })
        assertFalse(traces.joinToString("\n").contains(userPrompt))
    }
}
