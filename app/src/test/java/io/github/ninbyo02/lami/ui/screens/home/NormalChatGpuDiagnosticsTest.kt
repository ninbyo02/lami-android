package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalChatGpuDiagnosticsTest {
    @Test
    fun `GPU default normal chat config is already text only null modalities`() {
        val experimentMode = resolveGpuDiagnosticExperimentModeForBackend("GPU")
        val diagnostics = buildGpuRouteConfigDiagnostics(
            modelPath = "/models/gemma-4-E2B-it.litertlm",
            cacheDirPath = "/cache",
            preferredBackend = "GPU",
            experimentMode = experimentMode,
        )

        assertEquals(GPU_EXPERIMENT_MODE_EDGE_GALLERY_LIKE, experimentMode)
        assertEquals(GPU_ENGINE_CONFIG_PROFILE_EDGE_GALLERY_LIKE, diagnostics.normalChatEngineConfigStyle)
        assertEquals("GPU", diagnostics.backend)
        assertEquals("null", diagnostics.visionBackend)
        assertEquals("null", diagnostics.audioBackend)
        assertEquals(
            GPU_RECOMMENDED_NEXT_CONFIG_NONE_ALREADY_TEXT_ONLY,
            diagnostics.recommendedNextConfigVariant,
        )
        assertFalse(
            shouldUseNormalChatGpuTextOnlyNullModalities(
                preferredBackend = PreferredBackendDryRunSetting.CPU,
                experimentMode = GPU_EXPERIMENT_MODE_TEXT_ONLY_NULL_MODALITIES,
            ),
        )
    }

    @Test
    fun `experimental GPU text only null modalities variant is selectable`() {
        val diagnostics = buildGpuRouteConfigDiagnostics(
            modelPath = "/models/gemma-4-E2B-it.litertlm",
            cacheDirPath = "/cache",
            preferredBackend = "GPU",
            experimentMode = GPU_EXPERIMENT_MODE_TEXT_ONLY_NULL_MODALITIES,
        )

        assertTrue(
            shouldUseNormalChatGpuTextOnlyNullModalities(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                experimentMode = GPU_EXPERIMENT_MODE_TEXT_ONLY_NULL_MODALITIES,
            ),
        )
        assertEquals(
            GPU_ENGINE_CONFIG_PROFILE_TEXT_ONLY_NULL_MODALITIES,
            diagnostics.normalChatEngineConfigStyle,
        )
        assertEquals("GPU", diagnostics.backend)
        assertEquals("null", diagnostics.visionBackend)
        assertEquals("null", diagnostics.audioBackend)
    }

    @Test
    fun `compact diagnostics include normal chat GPU failure fields`() {
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "engine_create_exception",
            context = buildLocalRouteDiagnosticContext(
                selectedModelName = "gemma",
                selectedModelFile = "/models/gemma.litertlm",
                selectedModelPath = "/models/gemma.litertlm",
                selectedModelSlot = "generic_fallback",
                genericFallbackModelConfigured = true,
                preferredBackend = "GPU",
                npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                shouldEnterNpuS1 = false,
                localRouteEntered = true,
            ),
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = false,
                heldEngineReused = false,
                engineConfigBuildStarted = true,
                engineConfigBuildFinished = true,
                engineCreateStarted = true,
                engineCreateFinished = false,
                failureStage = "engine-create",
                gpuConfigDiagnostics = buildGpuRouteConfigDiagnostics(
                    modelPath = "/models/gemma.litertlm",
                    cacheDirPath = "/cache",
                    preferredBackend = "GPU",
                ),
            ),
            elapsedMs = 14_000L,
        ) + "\n" +
            "normal_chat_engine_create_stage=engine-create\n" +
            "normal_chat_held_engine_reused=false\n" +
            "normal_chat_selected_model_slot=generic_fallback\n" +
            "normal_chat_model_path=/models/gemma.litertlm\n" +
            "normal_chat_requested_preferred_backend=GPU\n" +
            "normal_chat_applied_preferred_backend=GPU\n" +
            "normal_chat_engine_config_style=edge_gallery_like_text_only\n" +
            "normal_chat_recommended_next_gpu_config_variant=$GPU_RECOMMENDED_NEXT_CONFIG_NONE_ALREADY_TEXT_ONLY\n" +
            "normal_chat_exception_class=java.lang.IllegalStateException\n" +
            "normal_chat_exception_message=create failed\n" +
            "normal_chat_exception_cause_chain=java.lang.IllegalStateException:create failed"

        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "hello",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(localFailureDiagnosticsText = routeDiagnostics),
            ),
        )

        assertTrue(compact.contains("gpu_selected_model_slot=generic_fallback"))
        assertTrue(compact.contains("gpu_normal_chat_engine_config_style=edge_gallery_like_text_only"))
        assertTrue(compact.contains("gpu_recommended_next_config_variant=$GPU_RECOMMENDED_NEXT_CONFIG_NONE_ALREADY_TEXT_ONLY"))
        assertTrue(compact.contains("normal_chat_engine_create_stage=engine-create"))
        assertTrue(compact.contains("normal_chat_held_engine_reused=false"))
        assertTrue(compact.contains("normal_chat_selected_model_slot=generic_fallback"))
        assertTrue(compact.contains("normal_chat_model_path=/models/gemma.litertlm"))
        assertTrue(compact.contains("normal_chat_requested_preferred_backend=GPU"))
        assertTrue(compact.contains("normal_chat_applied_preferred_backend=GPU"))
        assertTrue(compact.contains("normal_chat_exception_class=java.lang.IllegalStateException"))
        assertTrue(compact.contains("normal_chat_exception_cause_chain=java.lang.IllegalStateException:create failed"))
    }
}
