package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuS1PersistentEngineDiagnosticsTest {
    @Test
    fun `persistent engine state can represent statuses`() {
        listOf(
            NPU_S1_PERSISTENT_ENGINE_STATUS_IDLE,
            NPU_S1_PERSISTENT_ENGINE_STATUS_RUNNING,
            NPU_S1_PERSISTENT_ENGINE_STATUS_COMPLETED,
            NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED,
            NPU_S1_PERSISTENT_ENGINE_STATUS_CANCELLED,
        ).forEach { status ->
            val text = formatNpuS1PersistentEngineDiagnosticsForDev(
                NpuS1PersistentEngineProbeState(persistentProbeStatus = status),
            )

            assertTrue(text.contains("persistent_probe_status=$status"))
        }
    }

    @Test
    fun `summary includes engine initialize count and first failure`() {
        val state = NpuS1PersistentEngineProbeState(
            persistentProbeStatus = NPU_S1_PERSISTENT_ENGINE_STATUS_STOPPED,
            engineInitializeCount = 1,
            firstFailureRunIndex = 7,
            firstFailureStage = "decode",
            firstFailureReason = "decode_failed:LiteRtLmJniException",
            firstFailureExceptionClass = "LiteRtLmJniException",
            persistentEngineHypothesisResult = "decode_failed",
            records = listOf(
                NpuS1PersistentEngineRunRecord(
                    runIndex = 7,
                    status = FailureNpuStandardRouteS1Provider.STATUS_FAILURE,
                    reason = "decode_failed:LiteRtLmJniException",
                    conversationCreated = "true",
                    decodeStarted = "true",
                    failureStage = "decode",
                    failureExceptionClass = "LiteRtLmJniException",
                ),
            ),
        )
        val text = formatNpuS1PersistentEngineDiagnosticsForDev(state)

        assertTrue(text.contains("engine_initialize_count=1"))
        assertTrue(text.contains("first_failure_run_index=7"))
        assertTrue(text.contains("first_failure_stage=decode"))
        assertTrue(text.contains("first_failure_exception_class=LiteRtLmJniException"))
        assertTrue(text.contains("persistent_engine_hypothesis_result=decode_failed"))
    }

    @Test
    fun `unavailable session counters are not formatted as false or zero`() {
        val text = formatNpuS1PersistentEngineDiagnosticsForDev(
            NpuS1PersistentEngineProbeState(
                records = listOf(
                    NpuS1PersistentEngineRunRecord(
                        runIndex = 1,
                        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                        reason = "success",
                    ),
                ),
            ),
        )

        assertTrue(text.contains("session_create_count=unavailable"))
        assertTrue(text.contains("session_close_count=unavailable"))
        assertTrue(text.contains("session_created=unavailable"))
        assertTrue(text.contains("session_closed=unavailable"))
        assertFalse(text.contains("session_create_count=0"))
        assertFalse(text.contains("session_created=false"))
    }

    @Test
    fun `append function keeps base text and persistent diagnostics`() {
        val text = appendNpuS1PersistentEngineDiagnosticsForDev(
            text = "base=true",
            state = NpuS1PersistentEngineProbeState(engineInitializeCount = 1),
        )

        assertTrue(text.startsWith("base=true"))
        assertTrue(text.contains("[DEV診断: NPU S1 persistent engine summary]"))
        assertTrue(text.contains("engine_initialize_count=1"))
    }
}
