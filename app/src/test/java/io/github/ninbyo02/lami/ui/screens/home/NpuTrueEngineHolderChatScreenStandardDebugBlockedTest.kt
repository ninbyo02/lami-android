package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `trueEngineNpuProbeDebug keeps standalone DEV diagnostics visible without standard route text`() {
        val shouldShow = shouldShowNpuTrueEngineHolderStandaloneDevDiagnostics(
            routeText = null,
            devTraceText = null,
            s4Text = null,
        )

        assertEquals(BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR, shouldShow)
        assertTrue(NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_UI_TITLE.contains("NPU True Engine Holder Create/Close Probe"))
        assertTrue(
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_RUN_LABEL.contains(
                "Run True Engine Holder Create/Close Probe",
            ),
        )
        assertTrue(
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_COPY_SUMMARY_LABEL.contains(
                "Copy True Engine Holder Summary",
            ),
        )
        assertTrue(
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_COPY_FULL_DUMP_LABEL.contains(
                "Copy True Engine Holder Full Dump",
            ),
        )
    }

    @Test
    fun `standalone true engine DEV diagnostics do not duplicate standard route diagnostics`() {
        assertFalse(
            shouldShowNpuTrueEngineHolderStandaloneDevDiagnostics(
                routeText = "route_type=standard_chat_screen_s1",
                devTraceText = null,
                s4Text = null,
            ),
        )
    }

    @Test
    fun `true engine create close native mode summary is valid and keeps generation paths unused`() {
        val state = NpuTrueEngineHolderCreateCloseProbeState(
            status = "completed",
            reason = "true_engine_create_close_only_success",
            holderId = "true-engine-holder-create-close-dev",
            nativeResult = NpuTrueEngineHolderNativeResult(
                nativeReturn = "completed",
                resultText = """
                    selected_native_probe_mode=true_engine_create_close_only
                    argument_validation_passed=true
                    run_count_validation_skipped_for_create_close_only=true
                    persistent_custom_jni_status=completed
                    model_assets_create_reached=true
                    model_assets_create_returned=true
                    model_assets_create_succeeded=true
                    engine_settings_create_reached=true
                    engine_settings_create_returned=true
                    engine_settings_create_succeeded=true
                    engine_create_reached=true
                    engine_create_returned=true
                    engine_create_succeeded=true
                    engine_create_count=1
                    engine_close_reached=true
                    engine_close_success=true
                    session_create_reached=false
                    session_create_count=0
                    prefill_reached=false
                    decode_reached=false
                    decode_count=0
                    generate_count=0
                    npu_decode_called=false
                    qnn_decode_called=false
                """.trimIndent(),
            ),
        )

        val summary = formatNpuTrueEngineHolderCreateCloseSummaryForCopy(state)

        assertTrue(summary.contains("selected_native_probe_mode=true_engine_create_close_only"))
        assertTrue(summary.contains("argument_validation_passed=true"))
        assertTrue(summary.contains("run_count_validation_skipped_for_create_close_only=true"))
        assertFalse(summary.contains("invalid_native_probe_mode"))
        assertFalse(summary.contains("invalid persistent probe native_probe_mode"))
        assertTrue(summary.contains("model_assets_create_called=true"))
        assertTrue(summary.contains("engine_settings_create_called=true"))
        assertTrue(summary.contains("engine_create_called=true"))
        assertTrue(summary.contains("engine_close_count=1"))
        assertTrue(summary.contains("session_create_count=0"))
        assertTrue(summary.contains("decode_count=0"))
        assertTrue(summary.contains("generate_count=0"))
        assertTrue(summary.contains("npu_decode_called=false"))
        assertTrue(summary.contains("qnn_decode_called=false"))
        assertTrue(summary.contains("true_engine_persistent_reuse=false"))
        assertFalse(summary.contains("true_engine_persistent_reuse=true"))
        assertFalse(summary.contains("engine_reuse_observed=true"))
    }

    @Test
    fun `entrypoint only summary is gated by dedicated flag and keeps startup safe`() {
        val summary = formatNpuTrueEngineEntrypointSummaryForCopy(NpuTrueEngineEntrypointProbeState())
        val fullDump = formatNpuTrueEngineEntrypointFullDumpForCopy(NpuTrueEngineEntrypointProbeState())

        assertTrue(summary.contains("test_name=NPU True Engine Entrypoint Probe"))
        assertTrue(summary.contains("probe_status=idle"))
        assertTrue(summary.contains("selected_native_probe_mode=entrypoint_only"))
        assertTrue(summary.contains("entrypoint_only_probe_available=${expectedEntrypointAvailable()}"))
        assertTrue(
            summary.contains(
                "entrypoint_only_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED}",
            ),
        )
        assertTrue(summary.contains("isolated_native_execution_enabled=false"))
        assertTrue(summary.contains("probe_execution_available=${expectedExecutionAvailable()}"))
        assertTrue(summary.contains("entrypoint_only_probe_execution_available=${expectedEntrypointAvailable()}"))
        assertTrue(summary.contains("startup_native_call_blocked=true"))
        assertTrue(summary.contains("native_call_deferred_until_button_click=true"))
        assertTrue(summary.contains("native_entrypoint_reached=false"))
        assertTrue(summary.contains("model_assets_create_reached=false"))
        assertTrue(summary.contains("engine_settings_create_reached=false"))
        assertTrue(summary.contains("engine_create_reached=false"))
        assertTrue(summary.contains("session_create_count=0"))
        assertTrue(summary.contains("decode_count=0"))
        assertTrue(summary.contains("generate_count=0"))
        assertTrue(fullDump.contains("no true engine entrypoint probe result available"))
    }

    @Test
    fun `entrypoint only completed summary proves native entrypoint returned before engine work`() {
        val state = NpuTrueEngineEntrypointProbeState(
            status = "completed",
            reason = "entrypoint_only_completed",
            modelPathOrReason = "/models/gemma.task",
            nativeResult = NpuTrueEngineHolderNativeResult(
                nativeReturn = "completed",
                resultText = """
                    selected_native_probe_mode=entrypoint_only
                    true_engine_probe_flavor=trueEngineNpuProbeDebug
                    persistent_custom_jni_status=completed
                    hypothesis_result=entrypoint_only_success
                    last_native_stage=entrypoint
                    native_entrypoint_reached=true
                    model_assets_create_reached=false
                    engine_settings_create_reached=false
                    engine_create_reached=false
                    session_create_reached=false
                    session_create_count=0
                    prefill_reached=false
                    decode_reached=false
                    decode_count=0
                    generate_count=0
                """.trimIndent(),
            ),
        )

        val summary = formatNpuTrueEngineEntrypointSummaryForCopy(state)
        val fullDump = formatNpuTrueEngineEntrypointFullDumpForCopy(state)

        assertTrue(summary.contains("probe_status=completed"))
        assertTrue(summary.contains("probe_reason=entrypoint_only_completed"))
        assertTrue(summary.contains("selected_native_probe_mode=entrypoint_only"))
        assertTrue(summary.contains("native_entrypoint_reached=true"))
        assertTrue(summary.contains("hypothesis_result=entrypoint_only_success"))
        assertTrue(summary.contains("model_assets_create_reached=false"))
        assertTrue(summary.contains("engine_settings_create_reached=false"))
        assertTrue(summary.contains("engine_create_reached=false"))
        assertTrue(summary.contains("session_create_count=0"))
        assertTrue(summary.contains("prefill_reached=false"))
        assertTrue(summary.contains("decode_count=0"))
        assertTrue(summary.contains("generate_count=0"))
        assertTrue(summary.contains("npu_decode_called=false"))
        assertTrue(summary.contains("qnn_decode_called=false"))
        assertTrue(summary.contains("restart_app_recommended=false"))
        assertTrue(summary.contains("true_engine_persistent_reuse=false"))
        assertTrue(summary.contains("engine_reuse_observed=unavailable"))
        assertTrue(fullDump.contains("native_result_begin"))
        assertFalse(summary.contains("true_engine_create_close_only"))
        assertFalse(summary.contains("model_assets_create_reached=true"))
        assertFalse(summary.contains("engine_create_reached=true"))
    }

    @Test
    fun `model assets only summary is gated by dedicated flag and keeps startup safe`() {
        val summary = formatNpuTrueEngineModelAssetsSummaryForCopy(NpuTrueEngineModelAssetsProbeState())
        val fullDump = formatNpuTrueEngineModelAssetsFullDumpForCopy(NpuTrueEngineModelAssetsProbeState())

        assertTrue(summary.contains("test_name=NPU True Engine ModelAssets Probe"))
        assertTrue(summary.contains("probe_status=idle"))
        assertTrue(summary.contains("selected_native_probe_mode=model_assets_only"))
        assertTrue(summary.contains("model_assets_only_probe_available=${expectedModelAssetsAvailable()}"))
        assertTrue(
            summary.contains(
                "model_assets_only_execution_enabled=${BuildConfig.TRUE_ENGINE_NPU_PROBE_MODEL_ASSETS_ONLY_ENABLED}",
            ),
        )
        assertTrue(summary.contains("isolated_native_execution_enabled=false"))
        assertTrue(summary.contains("probe_execution_available=${expectedExecutionAvailable()}"))
        assertTrue(summary.contains("model_assets_only_probe_execution_available=${expectedModelAssetsAvailable()}"))
        assertTrue(summary.contains("startup_native_call_blocked=true"))
        assertTrue(summary.contains("native_call_deferred_until_button_click=true"))
        assertTrue(summary.contains("native_entrypoint_reached=false"))
        assertTrue(summary.contains("model_assets_create_reached=false"))
        assertTrue(summary.contains("model_assets_create_returned=false"))
        assertTrue(summary.contains("model_assets_create_succeeded=false"))
        assertTrue(summary.contains("engine_settings_create_reached=false"))
        assertTrue(summary.contains("engine_create_reached=false"))
        assertTrue(summary.contains("session_create_count=0"))
        assertTrue(summary.contains("decode_count=0"))
        assertTrue(summary.contains("generate_count=0"))
        assertTrue(fullDump.contains("no true engine model assets probe result available"))
    }

    @Test
    fun `model assets only completed summary stops before settings and engine create`() {
        val state = NpuTrueEngineModelAssetsProbeState(
            status = "failed",
            reason = "unavailable",
            modelPathOrReason = "/models/gemma.task",
            nativeResult = NpuTrueEngineHolderNativeResult(
                nativeReturn = "completed",
                resultText = """
                    selected_native_probe_mode=model_assets_only
                    true_engine_probe_flavor=trueEngineNpuProbeDebug
                    persistent_custom_jni_status=completed
                    persistent_custom_jni_hypothesis_result=model_assets_only_success
                    last_native_stage=model_assets_create_returned
                    native_entrypoint_reached=true
                    model_assets_create_reached=true
                    model_assets_create_returned=true
                    model_assets_create_succeeded=true
                    engine_settings_create_reached=false
                    engine_create_reached=false
                    session_create_reached=false
                    session_create_count=0
                    prefill_reached=false
                    decode_reached=false
                    decode_count=0
                    generate_count=0
                """.trimIndent(),
            ),
        )

        val summary = formatNpuTrueEngineModelAssetsSummaryForCopy(state)
        val fullDump = formatNpuTrueEngineModelAssetsFullDumpForCopy(state)

        assertTrue(summary.contains("probe_status=completed"))
        assertTrue(summary.contains("probe_reason=model_assets_only_completed"))
        assertTrue(summary.lineSequence().any { it == "probe_status=completed" })
        assertTrue(summary.lineSequence().none { it == "probe_status=failed" })
        assertTrue(summary.lineSequence().none { it == "probe_reason=unavailable" })
        assertTrue(fullDump.lineSequence().take(4).any { it == "probe_status=completed" })
        assertTrue(fullDump.lineSequence().take(4).any { it == "probe_reason=model_assets_only_completed" })
        assertTrue(fullDump.lineSequence().none { it == "probe_status=failed" })
        assertTrue(summary.contains("selected_native_probe_mode=model_assets_only"))
        assertTrue(summary.contains("hypothesis_result=model_assets_only_success"))
        assertTrue(summary.contains("native_entrypoint_reached=true"))
        assertTrue(summary.contains("model_assets_create_reached=true"))
        assertTrue(summary.contains("model_assets_create_returned=true"))
        assertTrue(summary.contains("model_assets_create_succeeded=true"))
        assertTrue(summary.contains("engine_settings_create_reached=false"))
        assertTrue(summary.contains("engine_create_reached=false"))
        assertTrue(summary.contains("session_create_count=0"))
        assertTrue(summary.contains("prefill_reached=false"))
        assertTrue(summary.contains("decode_count=0"))
        assertTrue(summary.contains("generate_count=0"))
        assertTrue(summary.contains("npu_decode_called=false"))
        assertTrue(summary.contains("qnn_decode_called=false"))
        assertTrue(summary.contains("restart_app_recommended=false"))
        assertTrue(summary.contains("true_engine_persistent_reuse=false"))
        assertTrue(summary.contains("engine_reuse_observed=unavailable"))
        assertTrue(fullDump.contains("native_result_begin"))
        assertFalse(summary.contains("true_engine_create_close_only"))
        assertFalse(summary.contains("engine_settings_create_reached=true"))
        assertFalse(summary.contains("engine_create_reached=true"))
    }

    @Test
    fun `model assets only completed summary accepts direct hypothesis result`() {
        val state = NpuTrueEngineModelAssetsProbeState(
            status = "failed",
            reason = "unavailable",
            modelPathOrReason = "/models/gemma.task",
            nativeResult = NpuTrueEngineHolderNativeResult(
                nativeReturn = "completed",
                resultText = """
                    selected_native_probe_mode=model_assets_only
                    true_engine_probe_flavor=trueEngineNpuProbeDebug
                    hypothesis_result=model_assets_only_success
                    last_native_stage=model_assets_create_returned
                    native_entrypoint_reached=true
                    model_assets_create_reached=true
                    model_assets_create_returned=true
                    model_assets_create_succeeded=true
                    engine_settings_create_reached=false
                    engine_create_reached=false
                    session_create_count=0
                    decode_count=0
                    generate_count=0
                """.trimIndent(),
            ),
        )

        val summary = formatNpuTrueEngineModelAssetsSummaryForCopy(state)
        val fullDump = formatNpuTrueEngineModelAssetsFullDumpForCopy(state)

        assertTrue(summary.lineSequence().any { it == "probe_status=completed" })
        assertTrue(summary.lineSequence().any { it == "probe_reason=model_assets_only_completed" })
        assertTrue(summary.lineSequence().none { it == "probe_status=failed" })
        assertTrue(summary.contains("hypothesis_result=model_assets_only_success"))
        assertTrue(fullDump.lineSequence().take(4).any { it == "probe_status=completed" })
        assertTrue(fullDump.lineSequence().take(4).any { it == "probe_reason=model_assets_only_completed" })
        assertTrue(fullDump.lineSequence().none { it == "probe_status=failed" })
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

    private fun expectedEntrypointAvailable(): Boolean =
        BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR &&
            BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED &&
            BuildConfig.TRUE_ENGINE_NPU_PROBE_ENTRYPOINT_ONLY_ENABLED

    private fun expectedModelAssetsAvailable(): Boolean =
        BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR &&
            BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED &&
            BuildConfig.TRUE_ENGINE_NPU_PROBE_MODEL_ASSETS_ONLY_ENABLED

    private fun expectedBlockReason(): String =
        if (expectedExecutionAvailable()) {
            "unavailable"
        } else if (BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR &&
            BuildConfig.TRUE_ENGINE_NPU_PROBE_NATIVE_PAYLOAD_STAGED
        ) {
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_STARTUP_CRASH_DISABLED_REASON
        } else if (BuildConfig.TRUE_ENGINE_NPU_PROBE_FLAVOR) {
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_ISOLATED_DISABLED_REASON
        } else {
            NPU_TRUE_ENGINE_HOLDER_CREATE_CLOSE_BLOCK_REASON
        }
}
