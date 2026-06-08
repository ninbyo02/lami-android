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
                provider = NpuStandardRouteS1Provider { receivedPrompt, receivedMaxOutputTokens, _ ->
                    assertEquals(userPrompt, receivedPrompt)
                    assertEquals(128, receivedMaxOutputTokens)
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
                        requestedMaxOutputTokens = receivedMaxOutputTokens,
                        effectiveMaxOutputTokens = receivedMaxOutputTokens,
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

    @Test
    fun `bridge passes explicit max output token setting to invoker`() {
        val bridge = NpuStandardRouteS1Bridge(
            invoker = NpuStandardRouteS1Invoker(
                provider = NpuStandardRouteS1Provider { _, receivedMaxOutputTokens, _ ->
                    NpuStandardRouteS1RawResult(
                        status = "success",
                        result = "success",
                        success = true,
                        reason = "success",
                        rawOutput = "こんにちは。",
                        sanitizedOutput = "こんにちは。",
                        qualityClassification = "natural_japanese",
                        runDecodeReached = true,
                        npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                        fallbackUsed = false,
                        timeout = false,
                        freshCrash = false,
                        requestedMaxOutputTokens = receivedMaxOutputTokens,
                        effectiveMaxOutputTokens = receivedMaxOutputTokens,
                    )
                },
            ),
        )

        val result = bridge.run(userPrompt = userPrompt, maxOutputTokens = 512)

        assertEquals(512, result.selection.requestedMaxOutputTokens)
        assertEquals(512, result.selection.effectiveMaxOutputTokens)
    }

    @Test
    fun `normal chat mode bridge blocks custom JNI native route`() {
        val result = NpuStandardRouteS1Bridge(mode = NpuStandardRouteMode.S1_ONLY)
            .run(userPrompt = "こんにちは")

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals(
            NpuStandardRouteS1ProviderSelector.REASON_NATIVE_ROUTE_BLOCKED_FOR_NORMAL_CHAT,
            result.reason,
        )
        assertFalse(result.runDecodeReached)
        assertTrue(
            result.withTiming(NpuStandardRouteS1Timing(totalMs = 0L))
                .displayText
                .contains("normal_chat_native_route_blocked=true"),
        )
    }
}
