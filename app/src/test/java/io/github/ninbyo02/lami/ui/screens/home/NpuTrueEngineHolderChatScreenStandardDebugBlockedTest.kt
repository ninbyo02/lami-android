package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NpuTrueEngine {
    @Test
    fun `standardDebug no-result summary is startup safe before DEV probe run`() {
        val state = NpuTrueEngineHolderCreateCloseProbeState()

        val summary = formatNpuTrueEngineHolderCreateCloseSummaryForCopy(state)
        val fullDump = formatNpuTrueEngineHolderCreateCloseFullDumpForCopy(state)

        assertTrue(summary.contains("no true engine holder create/close probe result available"))
        assertTrue(summary.contains("true_engine_probe_flavor=${expectedVariantName()}"))
        assertTrue(summary.contains("isolated_flavor_available=${BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR}"))
        assertTrue(
            summary.contains(
                "isolated_native_payload_staged=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED}",
            ),
        )
        assertTrue(
            summary.contains(
                "isolated_native_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED}",
            ),
        )
        assertTrue(summary.contains("true_engine_create_close_probe_startup_safe=true"))
        assertTrue(summary.contains("native_call_deferred_until_button_click=true"))
        assertTrue(summary.contains("startup_native_call_blocked=true"))
        assertTrue(summary.contains("probe_execution_available=${expectedExecutionAvailable()}"))
        assertTrue(summary.contains("probe_execution_block_reason=${expectedBlockReason()}"))
        assertTrue(fullDump.contains("probe_status=idle"))
        assertTrue(fullDump.contains("startup_native_call_blocked=true"))
    }

    @Test
    fun `standardDebug blocked summary fixes true engine native execution off`() {
        val state = blockedState()

        val summary = formatNpuTrueEngineHolderCreateCloseSummaryForCopy(state)
        val fullDump = formatNpuTrueEngineHolderCreateCloseFullDumpForCopy(state)

        assertBlockedSummary(summary)
        assertTrue(fullDump.contains("native_return=blocked"))
        assertTrue(fullDump.contains("model_path_or_reason=not_resolved_startup_safe_block"))
        assertTrue(fullDump.contains("probe_execution_available=${expectedExecutionAvailable()}"))
    }

    @Test
    fun `standardDebug runner returns blocked state without entering native probe`() = runTest {
        val runner = createNpuTrueEngineHolderCreateCloseProbeRunner(
            context = RuntimeEnvironment.getApplication(),
        )

        assertNotNull(runner)
        val state = runner!!.run()
        val summary = formatNpuTrueEngineHolderCreateCloseSummaryForCopy(state)

        if (expectedExecutionAvailable()) {
            assertEquals("failed", state.status)
            assertTrue(state.reason.startsWith("model_resolution_failed:"))
            assertTrue(summary.contains("probe_execution_available=true"))
            assertTrue(summary.contains("session_create_count=0"))
            assertTrue(summary.contains("decode_count=0"))
            assertTrue(summary.contains("generate_count=0"))
            assertTrue(summary.contains("npu_decode_called=false"))
            assertTrue(summary.contains("qnn_decode_called=false"))
        } else {
            assertEquals("blocked", state.status)
            assertEquals(expectedBlockReason(), state.reason)
            assertEquals("not_resolved_startup_safe_block", state.modelPathOrReason)
            assertBlockedSummary(summary)
        }
    }

    @Test
    fun `ChatScreen developer full copy renders standardDebug blocked true engine summary`() {
        val text = buildInferenceStatsFullCopyText(
            stats = InferenceStats(modelName = "local-dev"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            sections = emptyList(),
            detailSections = emptyList(),
            npuTrueEngineHolderCreateCloseState = blockedState(),
        )

        assertTrue(text.contains("[DEV診断: NPU true engine holder create close full dump]"))
        assertBlockedSummary(text)
    }

    private fun blockedState(): NpuTrueEngineHolderCreateCloseProbeState =
        NpuTrueEngineHolderCreateCloseProbeState(
            status = "blocked",
            reason = expectedBlockReason(),
            modelPathOrReason = "not_resolved_startup_safe_block",
            holderId = "true-engine-holder-create-close-dev",
            nativeResult = blockedNpuTrueEngineHolderCreateCloseNativeResult(),
        )

    private fun assertBlockedSummary(text: String) {
        assertTrue(text.contains("test_name=NPU True Engine Holder Create Close Probe"))
        assertTrue(text.contains("probe_status=blocked"))
        assertTrue(text.contains("probe_reason=${expectedBlockReason()}"))
        assertTrue(text.contains("selected_native_probe_mode=true_engine_create_close_only"))
        assertTrue(text.contains("true_engine_probe_flavor=${expectedVariantName()}"))
        assertTrue(text.contains("isolated_flavor_available=${BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR}"))
        assertTrue(
            text.contains(
                "isolated_native_payload_staged=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED}",
            ),
        )
        assertTrue(
            text.contains(
                "isolated_native_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED}",
            ),
        )
        assertTrue(text.contains("true_engine_create_close_probe_startup_safe=true"))
        assertTrue(text.contains("native_call_deferred_until_button_click=true"))
        assertTrue(text.contains("startup_native_call_blocked=true"))
        assertTrue(text.contains("probe_execution_available=${expectedExecutionAvailable()}"))
        assertTrue(text.contains("probe_execution_block_reason=${expectedBlockReason()}"))
        assertTrue(text.contains("session_create_count=0"))
        assertTrue(text.contains("decode_count=0"))
        assertTrue(text.contains("generate_count=0"))
        assertTrue(text.contains("npu_decode_called=false"))
        assertTrue(text.contains("qnn_decode_called=false"))
        assertTrue(text.contains("restart_app_recommended=false"))
        assertTrue(text.contains("true_engine_persistent_reuse=false"))
        assertTrue(text.contains("engine_reuse_observed=unavailable"))
    }

    private fun expectedVariantName(): String =
        BuildConfig.CURRENT_FLAVOR + BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercaseChar() }

    private fun expectedExecutionAvailable(): Boolean =
        BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR &&
            BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED &&
            BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_EXECUTION_ENABLED

    private fun expectedBlockReason(): String =
        if (expectedExecutionAvailable()) {
            "unavailable"
        } else if (BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR &&
            BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED
        ) {
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_ISOLATED_PAYLOAD_STAGED_DISABLED_REASON
        } else if (BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR) {
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_ISOLATED_DISABLED_REASON
        } else {
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_BLOCK_REASON
        }
}
