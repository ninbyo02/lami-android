package io.github.ninbyo02.lami.npu

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuRoutePlannerTest {
    @Test
    fun `gate blocked returns gate reason and does not call adapter`() = runBlocking {
        val adapter = RecordingAdapter()
        val planner = DevOnlyNpuRoutePlanner(adapter = adapter)

        val result = planner.runIfAllowed(
            gateInput = validGateInput(validatorValid = false),
            prompt = "Hello",
        )

        assertFalse(result.success)
        assertEquals("gate_blocked:VALIDATOR_INVALID", result.reasonCode)
        assertEquals("Hello", result.prompt)
        assertEquals(3, result.maxOutputTokens)
        assertNull(result.output)
        assertFalse(result.freshCrash)
        assertFalse(result.timeout)
        assertEquals(0, adapter.callCount)
    }

    @Test
    fun `gate ok calls adapter`() = runBlocking {
        val adapter = RecordingAdapter(
            result = DevOnlyNpuRouteResult(
                success = true,
                output = "ok",
                reasonCode = "ok",
                elapsedMs = 10L,
                decodeElapsedMs = 3L,
                prompt = "placeholder",
                maxOutputTokens = 3,
                backendEvidence = "fake",
                artifactPath = "artifact",
                freshCrash = false,
                timeout = false,
            ),
        )
        val planner = DevOnlyNpuRoutePlanner(adapter = adapter)

        val result = planner.runIfAllowed(
            gateInput = validGateInput(),
            prompt = "Hello",
            maxOutputTokens = 3,
            timeoutMs = 1_000L,
        )

        assertTrue(result.success)
        assertEquals("ok", result.reasonCode)
        assertEquals(1, adapter.callCount)
        assertEquals("Hello", adapter.lastPrompt)
        assertEquals(3, adapter.lastMaxOutputTokens)
        assertEquals(1_000L, adapter.lastTimeoutMs)
    }

    @Test
    fun `gate ok with blocked adapter returns adapter not connected`() = runBlocking {
        val result = DevOnlyNpuRoutePlanner().runIfAllowed(
            gateInput = validGateInput(),
            prompt = "Hello",
        )

        assertFalse(result.success)
        assertEquals("adapter_not_connected", result.reasonCode)
        assertEquals("Hello", result.prompt)
        assertEquals(3, result.maxOutputTokens)
        assertNull(result.backendEvidence)
        assertNull(result.artifactPath)
    }

    @Test
    fun `invalid max tokens is blocked before adapter`() = runBlocking {
        val adapter = RecordingAdapter()
        val result = DevOnlyNpuRoutePlanner(adapter = adapter).runIfAllowed(
            gateInput = validGateInput(maxOutputTokens = 4),
            prompt = "Hello",
            maxOutputTokens = 4,
        )

        assertFalse(result.success)
        assertEquals("gate_blocked:INVALID_MAX_OUTPUT_TOKENS", result.reasonCode)
        assertEquals(4, result.maxOutputTokens)
        assertEquals(0, adapter.callCount)
    }

    @Test
    fun `fake adapter proves planner does not call npu APIs by itself`() = runBlocking {
        val adapter = RecordingAdapter()
        val planner = DevOnlyNpuRoutePlanner(adapter = adapter)

        planner.runIfAllowed(
            gateInput = validGateInput(),
            prompt = "Hello",
        )

        assertEquals(1, adapter.callCount)
        assertFalse(adapter.engineInitializeCalled)
        assertFalse(adapter.runDecodeCalled)
    }

    private class RecordingAdapter(
        private val result: DevOnlyNpuRouteResult? = null,
    ) : DevOnlyNpuRouteAdapter {
        var callCount = 0
            private set
        var lastPrompt: String? = null
            private set
        var lastMaxOutputTokens: Int? = null
            private set
        var lastTimeoutMs: Long? = null
            private set
        var engineInitializeCalled = false
            private set
        var runDecodeCalled = false
            private set

        override suspend fun runOnce(
            prompt: String,
            maxOutputTokens: Int,
            timeoutMs: Long,
        ): DevOnlyNpuRouteResult {
            callCount += 1
            lastPrompt = prompt
            lastMaxOutputTokens = maxOutputTokens
            lastTimeoutMs = timeoutMs
            return result ?: DevOnlyNpuRouteResult(
                success = false,
                output = null,
                reasonCode = "fake_blocked",
                elapsedMs = null,
                decodeElapsedMs = null,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                backendEvidence = null,
                artifactPath = null,
                freshCrash = false,
                timeout = false,
            )
        }
    }

    private fun validGateInput(
        customBuildExperiment: Boolean = true,
        allowEditablePromptPreview: Boolean = true,
        allowGuardedNpuRun: Boolean = true,
        allowEditablePromptExecution: Boolean = true,
        devCheckboxChecked: Boolean = true,
        validatorValid: Boolean = true,
        nativeEditablePromptSupported: Boolean = true,
        running: Boolean = false,
        maxOutputTokens: Int = 3,
    ): DevOnlyNpuRouteGateInput = DevOnlyNpuRouteGateInput(
        customBuildExperiment = customBuildExperiment,
        allowEditablePromptPreview = allowEditablePromptPreview,
        allowGuardedNpuRun = allowGuardedNpuRun,
        allowEditablePromptExecution = allowEditablePromptExecution,
        devCheckboxChecked = devCheckboxChecked,
        validatorValid = validatorValid,
        nativeEditablePromptSupported = nativeEditablePromptSupported,
        running = running,
        maxOutputTokens = maxOutputTokens,
    )
}
