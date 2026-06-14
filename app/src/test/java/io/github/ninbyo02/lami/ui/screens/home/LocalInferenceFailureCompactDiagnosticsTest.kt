package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files

class LocalInferenceFailureCompactDiagnosticsTest {
    @Test
    fun `CPU failure builds local compact diagnostics`() {
        val text = buildFailureText(PreferredBackendDryRunSetting.CPU)

        assertTrue(text.contains("[DEV診断: Local inference failure compact]"))
        assertFalse(text.contains("[DEV診断: NPU S1 compact]"))
        assertTrue(text.contains("selected_backend=CPU"))
        assertTrue(text.contains("requested_backend=CPU"))
        assertTrue(text.contains("effective_backend=CPU"))
        assertTrue(text.contains("route_family=local_cpu"))
        assertTrue(text.contains("backend_evidence=cpu_route"))
        assertFalse(text.contains("selected_backend=CPU\nrequested_backend=NPU"))
    }

    @Test
    fun `GPU failure builds local compact diagnostics`() {
        val text = buildFailureText(PreferredBackendDryRunSetting.GPU)

        assertTrue(text.contains("[DEV診断: Local inference failure compact]"))
        assertFalse(text.contains("[DEV診断: NPU S1 compact]"))
        assertTrue(text.contains("selected_backend=GPU"))
        assertTrue(text.contains("requested_backend=GPU"))
        assertTrue(text.contains("effective_backend=GPU"))
        assertTrue(text.contains("route_family=local_gpu"))
        assertTrue(text.contains("backend_evidence=gpu_route"))
        assertFalse(text.contains("selected_backend=GPU\nrequested_backend=NPU"))
    }

    @Test
    fun `Automatic failure builds local compact diagnostics`() {
        val text = buildFailureText(PreferredBackendDryRunSetting.DEFAULT)

        assertTrue(text.contains("[DEV診断: Local inference failure compact]"))
        assertFalse(text.contains("[DEV診断: NPU S1 compact]"))
        assertTrue(text.contains("selected_backend=Automatic"))
        assertTrue(text.contains("requested_backend=Automatic"))
        assertTrue(text.contains("effective_backend=Automatic"))
        assertTrue(text.contains("route_family=local_default"))
        assertTrue(text.contains("backend_evidence=local_default"))
    }

    @Test
    fun `GPU watchdog timeout details are copied into local failure compact`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "timeout_failure",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = false,
                heldEngineReused = false,
                engineCreateStarted = true,
                engineCreateFinished = false,
                conversationCreateStarted = false,
                conversationCreateFinished = false,
                generateStarted = false,
                firstTokenReceived = false,
                failureStage = "gpu_watchdog_timeout",
                fallbackUsed = false,
                staleCallbackIgnored = true,
            ),
            elapsedMs = 60_001L,
        )
        val text = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    localModelDisplayName = "gemma-4-E2B-it",
                    mediaPipeProbeModelPath = "/models/gemma-4-E2B-it.litertlm",
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "timeout",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                reason = "gpu_watchdog_timeout",
                routeContext = routeContext,
                timeout = true,
            ),
        )

        assertTrue(text.contains("[DEV診断: Local inference failure compact]"))
        assertTrue(text.contains("route_family=local_gpu"))
        assertTrue(text.contains("failure_stage=gpu_watchdog_timeout"))
        assertTrue(text.contains("gpu_watchdog_timeout_ms=60000"))
        assertTrue(text.contains("gpu_watchdog_mode=extended_dev_60s"))
        assertTrue(text.contains("gpu_timeout_stage=engine_constructor"))
        assertTrue(text.contains("gpu_timeout_elapsed_ms=60001"))
        assertTrue(text.contains("gpu_engine_create_duration_ms=60001"))
        assertTrue(text.contains("gpu_engine_create_started=true"))
        assertTrue(text.contains("gpu_engine_create_finished=false"))
        assertTrue(text.contains("gpu_engine_create_timeout_suspected=true"))
        assertTrue(text.contains("guard_recommendation=switch_to_cpu_or_npu"))
        assertTrue(text.contains("gpu_compatibility_mode=edge_gallery_like"))
        assertTrue(text.contains("gpu_engine_config_profile=edge_gallery_like_text_only"))
        assertTrue(text.contains("gpu_experiment_mode=edge_gallery_like"))
        assertTrue(text.contains("gpu_experiment_modes_available=edge_gallery_like,gpu_sampler_only_minimal,gpu_no_sampling_acceleration,gpu_disable_topk_gpu_sampler_candidate,gpu_cache_dir_null,gpu_cache_dir_app_files,gpu_max_tokens_32"))
        assertTrue(text.contains("gpu_cache_dir_mode=gallery_like_null_for_app_model_path"))
        assertTrue(text.contains("gpu_engine_config_model_path=/models/gemma-4-E2B-it.litertlm"))
        assertTrue(text.contains("gpu_engine_config_model_path_tail=gemma-4-E2B-it.litertlm"))
        assertTrue(text.contains("gpu_engine_config_cache_dir=null"))
        assertTrue(text.contains("gpu_engine_config_backend=GPU"))
        assertTrue(text.contains("gpu_engine_config_vision_backend=null"))
        assertTrue(text.contains("gpu_engine_config_audio_backend=null"))
        assertTrue(text.contains("gpu_engine_config_max_tokens=1024"))
        assertTrue(text.contains("gpu_engine_initialize_call_state=not_reached_engine_constructor_pending"))
        assertTrue(text.contains("gpu_timeout_checkpoint=engine_constructor"))
        assertTrue(text.contains("generate_call_started_at_elapsed_ms=unavailable"))
        assertTrue(text.contains("first_token_received_at_elapsed_ms=unavailable"))
        assertTrue(text.contains("generate_before_first_token_elapsed_ms=unavailable"))
        assertTrue(text.contains("gpu_generate_before_first_token_timeout_suspected=false"))
        assertTrue(text.contains("gpu_sampler_config_enabled=true"))
        assertTrue(text.contains("gpu_sampler_config_top_k=64"))
        assertTrue(text.contains("gpu_sampler_config_top_p=0.95"))
        assertTrue(text.contains("gpu_sampler_config_temperature=1.0"))
        assertTrue(text.contains("gpu_sampler_acceleration_policy=gallery_sampler_config"))
        assertTrue(text.contains("gpu_conversation_config_sampler_present=true"))
        assertTrue(text.contains("gpu_options_configured=false"))
        assertTrue(text.contains("gpu_options_source=EngineConfig_backend_only_no_explicit_GpuOptions"))
        assertTrue(text.contains("gpu_edge_gallery_diff_applied=true"))
        assertTrue(text.contains("gpu_litert_executor_error_file=unavailable"))
        assertTrue(text.contains("gpu_failure_interpretation=unknown"))
        assertTrue(text.contains("litert_lm_backend_candidates="))
        assertTrue(text.contains("litert_lm_backend_gpu_artisan_available="))
        assertTrue(text.contains("litert_lm_backend_cpu_artisan_available="))
        assertTrue(text.contains("litert_lm_backend_google_tensor_artisan_available="))
        assertTrue(text.contains("litert_lm_engine_config_artisan_api_available="))
        assertTrue(text.contains("litert_lm_runtime_config_available="))
        assertTrue(text.contains("litert_lm_backend_constraint_api_available="))
        assertTrue(text.contains("litert_lm_preferred_engine_type_api_available="))
        assertTrue(text.contains("selected_model_backend_constraint_hint=not_detected_by_path"))
        assertTrue(text.contains("selected_model_artisan_hint=not_detected_by_path"))
        assertTrue(text.contains("edge_gallery_artisan_static_evidence=GPU_ARTISAN,CPU_ARTISAN,GOOGLE_TENSOR_ARTISAN,Artisan_model_detected,LlmGpuArtisanExecutor"))
        assertTrue(text.contains("litert_runtime_executor_candidates="))
        assertTrue(text.contains("litert_runtime_executor_selection_hint="))
        assertTrue(text.contains("litert_runtime_backend_constraint_hint="))
        assertTrue(text.contains("litert_runtime_compiled_model_executor_hint="))
        assertTrue(text.contains("litert_runtime_gpu_executor_hint="))
        assertTrue(text.contains("litert_runtime_artisan_evidence="))
        assertTrue(text.contains("gpu_generate_started=false"))
        assertTrue(text.contains("gpu_first_token_received=false"))
        assertTrue(text.contains("gpu_stale_callback_ignored=true"))
    }

    @Test
    fun `LiteRT LM artisan API diagnostics provide model path hints without creating engine`() {
        val diagnostics = buildLiteRtLmBackendArtisanApiDiagnostics(
            selectedModelPath = "/models/gemma-4-E2B-it_qualcomm_sm8750.litertlm",
        )

        assertTrue(diagnostics.backendCandidates.isNotBlank())
        assertTrue(diagnostics.gpuArtisanAvailable in setOf("true", "false"))
        assertTrue(diagnostics.cpuArtisanAvailable in setOf("true", "false"))
        assertTrue(diagnostics.googleTensorArtisanAvailable in setOf("true", "false"))
        assertTrue(diagnostics.engineConfigArtisanApiAvailable in setOf("true", "false"))
        assertTrue(diagnostics.runtimeConfigAvailable in setOf("true", "false"))
        assertTrue(diagnostics.backendConstraintApiAvailable in setOf("true", "false"))
        assertTrue(diagnostics.preferredEngineTypeApiAvailable in setOf("true", "false"))
        assertEquals("path_contains_sm8750_or_qualcomm", diagnostics.selectedModelBackendConstraintHint)
        assertEquals("not_detected_by_path", diagnostics.selectedModelArtisanHint)
        assertTrue(diagnostics.edgeGalleryArtisanStaticEvidence.contains("GPU_ARTISAN"))
        assertTrue(diagnostics.runtimeExecutorCandidates.isNotBlank())
        assertTrue(
            diagnostics.runtimeExecutorSelectionHint in setOf(
                "public_api_executor_selection_surface_detected",
                "runtime_config_public_but_no_executor_selection_surface",
                "public_api_executor_selection_surface_unavailable",
            ),
        )
        assertTrue(
            diagnostics.runtimeBackendConstraintHint in setOf(
                "public_api_backend_constraint_surface_detected",
                "public_api_backend_constraint_surface_unavailable",
            ),
        )
        assertTrue(
            diagnostics.runtimeCompiledModelExecutorHint in setOf(
                "public_api_compiled_model_executor_surface_detected",
                "native_or_internal_compiled_model_executor_only",
            ),
        )
        assertTrue(
            diagnostics.runtimeGpuExecutorHint in setOf(
                "public_backend_gpu_artisan_available",
                "public_gpu_executor_surface_detected",
                "public_backend_gpu_only",
                "no_public_gpu_executor_surface_detected",
            ),
        )
        assertTrue(
            diagnostics.runtimeArtisanEvidence in setOf(
                "public_api_artisan_surface_detected",
                "edge_gallery_static_only_public_api_unavailable",
                "none_detected",
            ),
        )
    }

    @Test
    fun `GPU watchdog compact classifies generate before first token timeout`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "timeout_failure",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = false,
                engineConfigBuildStarted = true,
                engineConfigBuildFinished = true,
                engineCreateStarted = true,
                engineCreateFinished = true,
                engineInitializeStarted = true,
                engineInitializeFinished = true,
                conversationCreateStarted = true,
                conversationCreateFinished = true,
                generateStarted = true,
                generateStartedElapsedMs = 1_234L,
                firstTokenReceived = false,
                failureStage = "gpu_watchdog_timeout",
                fallbackUsed = false,
                staleCallbackIgnored = true,
                gpuConfigDiagnostics = buildGpuRouteConfigDiagnostics(
                    modelPath = "/models/gemma-4-E2B-it.litertlm",
                    cacheDirPath = "/data/user/0/io.github.ninbyo02.lami/cache",
                    preferredBackend = "GPU",
                    experimentMode = GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION,
                ),
            ),
            elapsedMs = 60_001L,
        )
        val text = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    localModelDisplayName = "gemma-4-E2B-it",
                    mediaPipeProbeModelPath = "/models/gemma-4-E2B-it.litertlm",
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "timeout",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                reason = "gpu_watchdog_timeout",
                routeContext = routeContext,
                timeout = true,
            ),
        )

        assertTrue(text.contains("failure_stage=gpu_watchdog_timeout"))
        assertTrue(text.contains("gpu_watchdog_failure_stage=gpu_watchdog_timeout_generate_before_first_token"))
        assertTrue(text.contains("gpu_timeout_stage=generate_before_first_token"))
        assertTrue(text.contains("engine_initialize_finished=true"))
        assertTrue(text.contains("conversation_create_finished=true"))
        assertTrue(text.contains("generate_started=true"))
        assertTrue(text.contains("first_token_received=false"))
        assertTrue(text.contains("gpu_last_known_stage=generate_started"))
        assertTrue(text.contains("gpu_timeout_checkpoint=generate_started"))
        assertTrue(text.contains("generate_call_started_at_elapsed_ms=1234"))
        assertTrue(text.contains("first_token_received_at_elapsed_ms=unavailable"))
        assertTrue(text.contains("generate_before_first_token_elapsed_ms=58767"))
        assertTrue(text.contains("gpu_generate_before_first_token_timeout_suspected=true"))
        assertTrue(text.contains("stale_callback_ignored=true"))
        assertTrue(text.contains("held_engine_exists=true"))
        assertTrue(text.contains("held_engine_reused=false"))
        assertTrue(text.contains("experiment_mode=gpu_no_sampling_acceleration"))
        assertTrue(text.contains("gpu_sampler_config_enabled=false"))
        assertTrue(text.contains("gpu_conversation_config_sampler_present=false"))
        assertTrue(text.contains("gpu_sampler_acceleration_policy=conversation_config_without_sampler"))
        assertTrue(text.contains("gpu_failure_interpretation=normal_route_generate_hangs_after_successful_initialize"))
        assertTrue(text.contains("gpu_route_divergence_point=normal_route_generate_started_before_first_token_timeout"))
    }

    @Test
    fun `GPU timeout compact includes generate callback lifecycle diagnostics`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "timeout_failure",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                engineCreateStarted = true,
                engineCreateFinished = true,
                engineInitializeStarted = true,
                engineInitializeFinished = true,
                conversationCreateStarted = true,
                conversationCreateFinished = true,
                generateStarted = true,
                generateStartedElapsedMs = 1_000L,
                firstTokenReceived = false,
                failureStage = "gpu_watchdog_timeout",
                staleCallbackIgnored = false,
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_RAW_CALLBACK_ONLY,
                gpuGenerateCallEntered = true,
                gpuGenerateCallReturned = true,
                gpuCallbackInvokedCount = 0,
                gpuCallbackEmptyTextCount = 0,
                gpuCallbackNonEmptyTextCount = 0,
                gpuCallbackDoneTrueSeen = false,
                gpuCallbackErrorSeen = false,
                gpuCallbackExceptionClass = "none",
                gpuCallbackExceptionMessage = "none",
                gpuCallbackExceptionChain = "none",
                gpuCallbackExceptionStage = "none",
            ),
            elapsedMs = 60_000L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "timeout",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                routeContext = routeContext,
                timeout = true,
            ),
        )

        assertTrue(routeDiagnostics.contains("debug_lami_gpu_generate_probe_mode=raw_callback_only"))
        assertTrue(routeDiagnostics.contains("gpu_generate_call_entered=true"))
        assertTrue(routeDiagnostics.contains("gpu_generate_call_returned=true"))
        assertTrue(routeDiagnostics.contains("gpu_callback_invoked_count=0"))
        assertTrue(routeDiagnostics.contains("gpu_generate_stall_interpretation=native_generate_no_callback"))
        assertTrue(routeDiagnostics.contains("callback_route_diff=gpu_generate_entered_no_callback"))
        assertTrue(compact.contains("debug_lami_gpu_generate_probe_mode=raw_callback_only"))
        assertTrue(compact.contains("gpu_generate_call_entered=true"))
        assertTrue(compact.contains("gpu_generate_call_returned=true"))
        assertTrue(compact.contains("gpu_callback_invoked_count=0"))
        assertTrue(compact.contains("gpu_done_true_seen=false"))
        assertTrue(compact.contains("gpu_callback_exception_class=none"))
        assertTrue(compact.contains("gpu_generate_stall_interpretation=native_generate_no_callback"))
        assertTrue(compact.contains("callback_route_diff=gpu_generate_entered_no_callback"))
    }

    @Test
    fun `GPU raw callback non-empty text is diagnostic success not compiled model failure`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery.litertlm",
            selectedModelFile = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "gpu_raw_callback_probe_success",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                engineCreateStarted = true,
                engineCreateFinished = true,
                engineInitializeStarted = true,
                engineInitializeFinished = true,
                conversationCreateStarted = true,
                conversationCreateFinished = true,
                generateStarted = true,
                generateStartedElapsedMs = 1_000L,
                firstTokenReceived = true,
                firstTokenElapsedMs = 1_200L,
                failureStage = "gpu_raw_callback_probe_success",
                staleCallbackIgnored = false,
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_RAW_CALLBACK_ONLY,
                gpuGenerateCallEntered = true,
                gpuGenerateCallReturned = true,
                gpuCallbackInvokedCount = 12,
                gpuCallbackEmptyTextCount = 0,
                gpuCallbackNonEmptyTextCount = 12,
                gpuCallbackLastTextLength = 3,
                gpuCallbackLastTextHead = "_😊",
                gpuCallbackDoneTrueSeen = true,
                gpuCallbackErrorSeen = false,
                gpuCallbackExceptionClass = "none",
                gpuCallbackExceptionMessage = "none",
                gpuCallbackExceptionChain = "none",
                gpuCallbackExceptionStage = "none",
            ),
            elapsedMs = 1_500L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "gpu-raw-callback-probe-success-skipped-post-processing",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                status = "diagnostic_success",
                reason = "gpu_raw_callback_probe_success",
                failureStage = "gpu_raw_callback_probe_success",
                routeContext = routeContext,
                timeout = false,
            ),
        )

        assertTrue(routeDiagnostics.contains("failure_stage=gpu_raw_callback_probe_success"))
        assertTrue(routeDiagnostics.contains("gpu_callback_non_empty_text_count=12"))
        assertTrue(routeDiagnostics.contains("gpu_generate_stall_interpretation=gpu_callback_text_observed"))
        assertTrue(routeDiagnostics.contains("gpu_callback_success_classification=gpu_callback_text_observed"))
        assertTrue(routeDiagnostics.contains("gpu_raw_callback_probe_status=success"))
        assertTrue(routeDiagnostics.contains("gpu_failure_interpretation=gpu_raw_callback_success_ui_path_needs_promotion"))
        assertTrue(routeDiagnostics.contains("litert_compiled_model_executor_failure_category=unknown"))
        assertTrue(compact.contains("status=diagnostic_success"))
        assertTrue(compact.contains("reason=gpu_raw_callback_probe_success"))
        assertTrue(compact.contains("failure_stage=gpu_raw_callback_probe_success"))
        assertTrue(compact.contains("gpu_raw_callback_probe_status=success"))
        assertTrue(compact.contains("gpu_failure_interpretation=gpu_raw_callback_success_ui_path_needs_promotion"))
        assertTrue(compact.contains("litert_compiled_model_executor_failure_category=unknown"))
        assertFalse(compact.contains("gpu_generate_exception_summary=failed_to_invoke_compiled_model"))
    }

    @Test
    fun `GPU callback to UI mode records promoted callback text diagnostics`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery.litertlm",
            selectedModelFile = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_ui_append_finished",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                engineCreateFinished = true,
                conversationCreateStarted = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_CALLBACK_TO_UI,
                gpuGenerateCallEntered = true,
                gpuGenerateCallReturned = true,
                gpuCallbackInvokedCount = 3,
                gpuCallbackNonEmptyTextCount = 3,
                gpuCallbackLastTextLength = 7,
                gpuCallbackLastTextHead = "こんにちは",
                gpuCallbackToUiEnabled = true,
                gpuCallbackTextPromotedToUi = true,
                gpuCallbackPromotedTextLength = 7,
                gpuCallbackPromotedNonEmptyCount = 3,
                gpuCallbackSuccessClassification = "gpu_callback_text_promoted_to_ui",
                gpuUiAppendStarted = true,
                gpuUiAppendFinished = true,
                gpuUiFirstVisibleTextElapsedMs = 1_111L,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
            ),
            elapsedMs = 1_400L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "applied",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                status = "success",
                reason = "gpu_callback_to_ui_success",
                failureStage = "none",
                routeContext = routeContext,
                timeout = false,
            ),
        )

        assertTrue(routeDiagnostics.contains("debug_lami_gpu_generate_probe_mode=callback_to_ui"))
        assertTrue(routeDiagnostics.contains("gpu_callback_to_ui_enabled=true"))
        assertTrue(routeDiagnostics.contains("gpu_callback_text_promoted_to_ui=true"))
        assertTrue(routeDiagnostics.contains("gpu_callback_promoted_text_length=7"))
        assertTrue(routeDiagnostics.contains("gpu_callback_promoted_non_empty_count=3"))
        assertTrue(routeDiagnostics.contains("gpu_callback_success_classification=gpu_callback_text_promoted_to_ui"))
        assertTrue(routeDiagnostics.contains("gpu_ui_append_started=true"))
        assertTrue(routeDiagnostics.contains("gpu_ui_append_finished=true"))
        assertTrue(routeDiagnostics.contains("gpu_ui_first_visible_text_elapsed_ms=1111"))
        assertTrue(routeDiagnostics.contains("gpu_streaming_completion_reason=flow_completed_non_empty_response"))
        assertTrue(compact.contains("status=success"))
        assertTrue(compact.contains("gpu_callback_to_ui_enabled=true"))
        assertTrue(compact.contains("gpu_callback_text_promoted_to_ui=true"))
        assertTrue(compact.contains("gpu_callback_success_classification=gpu_callback_text_promoted_to_ui"))
    }

    @Test
    fun `GPU normal callback streaming mode records success diagnostics`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery.litertlm",
            selectedModelFile = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                engineCreateFinished = true,
                conversationCreateStarted = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL_CALLBACK_STREAMING,
                gpuGenerateCallEntered = true,
                gpuGenerateCallReturned = true,
                gpuCallbackInvokedCount = 12,
                gpuCallbackNonEmptyTextCount = 12,
                gpuCallbackLastTextLength = 3,
                gpuCallbackLastTextHead = "_😊",
                gpuCallbackToUiEnabled = true,
                gpuCallbackTextPromotedToUi = true,
                gpuCallbackPromotedTextLength = 16,
                gpuCallbackPromotedNonEmptyCount = 12,
                gpuCallbackSuccessClassification = "gpu_callback_text_promoted_to_ui",
                gpuUiAppendStarted = true,
                gpuUiAppendFinished = true,
                gpuUiFirstVisibleTextElapsedMs = 370L,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
            ),
            elapsedMs = 1_500L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "applied",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                status = "success",
                reason = "gpu_normal_callback_streaming_success",
                failureStage = "none",
                routeContext = routeContext,
                timeout = false,
            ),
        )

        assertTrue(routeDiagnostics.contains("debug_lami_gpu_generate_probe_mode=normal_callback_streaming"))
        assertTrue(routeDiagnostics.contains("gpu_callback_to_ui_enabled=true"))
        assertTrue(routeDiagnostics.contains("gpu_callback_text_promoted_to_ui=true"))
        assertTrue(routeDiagnostics.contains("gpu_ui_append_finished=true"))
        assertTrue(routeDiagnostics.contains("gpu_streaming_completion_reason=flow_completed_non_empty_response"))
        assertTrue(routeDiagnostics.contains("gpu_generate_stall_interpretation=gpu_callback_text_observed"))
        assertTrue(compact.contains("status=success"))
        assertTrue(compact.contains("failure_stage=none"))
        assertTrue(compact.contains("debug_lami_gpu_generate_probe_mode=normal_callback_streaming"))
        assertTrue(compact.contains("gpu_callback_text_promoted_to_ui=true"))
        assertTrue(compact.contains("gpu_ui_append_finished=true"))
    }

    @Test
    fun `GPU guarded normal route callback streaming records success diagnostics`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery.litertlm",
            selectedModelFile = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateFinished = true,
                conversationCreateStarted = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                firstTokenElapsedMs = 486L,
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                gpuGenerateCallEntered = true,
                gpuGenerateCallReturned = true,
                gpuCallbackInvokedCount = 461,
                gpuCallbackNonEmptyTextCount = 461,
                gpuCallbackLastTextLength = 24,
                gpuCallbackLastTextHead = "材料リスト",
                gpuCallbackDoneTrueSeen = true,
                gpuCallbackToUiEnabled = true,
                gpuCallbackTextPromotedToUi = true,
                gpuCallbackPromotedTextLength = 1_200,
                gpuCallbackPromotedNonEmptyCount = 461,
                gpuCallbackSuccessClassification = "gpu_callback_text_promoted_to_ui",
                gpuUiAppendStarted = true,
                gpuUiAppendFinished = true,
                gpuUiFirstVisibleTextElapsedMs = 486L,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuNormalRouteUseCallbackStreaming = true,
                gpuCallbackStreamingPathSelected = true,
                gpuCallbackStreamingPathReason = "dev_gate_normal_route",
                gpuCallbackStreamingSuccessCount = 1,
                gpuCallbackStreamingEmptyCallbackCount = 0,
                gpuCallbackStreamingNonEmptyCallbackCount = 461,
                gpuCallbackStreamingDoneTrueSeen = true,
                gpuCallbackStreamingFinalTextLength = 1_200,
                gpuCallbackStreamingReusedHeldEngine = true,
                gpuCallbackStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuCallbackStreamingFailureReason = "none",
                standardGpuRuntimeAlignmentCandidateEnabled = true,
                standardGpuRuntimeAlignmentCandidateEligible = true,
                standardGpuRuntimeAlignmentCandidateBlockReason = "none",
                standardGpuRuntimeAlignmentCandidateModelSizeBytes = "2588147712",
                standardGpuRuntimeAlignmentCandidateModelIdentityHint = "edge_gallery_e2b_expected",
                standardGpuRuntimeAlignmentCandidateRuntimeStack = "standardDebug_dev_gate",
                standardGpuRuntimeAlignmentCandidateResult = "success",
            ),
            elapsedMs = 18_000L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "カレーの材料をお願いします。",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "applied",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                status = "success",
                reason = "gpu_guarded_callback_streaming_success",
                failureStage = "none",
                routeContext = routeContext,
                timeout = false,
            ),
        )

        assertTrue(routeDiagnostics.contains("debug_lami_gpu_generate_probe_mode=normal"))
        assertTrue(routeDiagnostics.contains("gpu_normal_route_use_callback_streaming=true"))
        assertTrue(routeDiagnostics.contains("gpu_callback_streaming_path_selected=true"))
        assertTrue(routeDiagnostics.contains("gpu_callback_streaming_path_reason=dev_gate_normal_route"))
        assertTrue(routeDiagnostics.contains("gpu_callback_streaming_success_count=1"))
        assertTrue(routeDiagnostics.contains("gpu_callback_streaming_non_empty_callback_count=461"))
        assertTrue(routeDiagnostics.contains("gpu_callback_streaming_done_true_seen=true"))
        assertTrue(routeDiagnostics.contains("gpu_callback_streaming_final_text_length=1200"))
        assertTrue(routeDiagnostics.contains("gpu_callback_streaming_reused_held_engine=true"))
        assertTrue(routeDiagnostics.contains("gpu_callback_streaming_completion_reason=flow_completed_non_empty_response"))
        assertTrue(routeDiagnostics.contains("gpu_callback_streaming_failure_reason=none"))
        assertTrue(routeDiagnostics.contains("standard_gpu_probe_expected_edge_gallery_e2b=true"))
        assertTrue(routeDiagnostics.contains("standard_gpu_probe_model_size_bytes=2588147712"))
        assertTrue(
            routeDiagnostics.contains(
                "standard_gpu_probe_model_sha256_expected=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            ),
        )
        assertTrue(routeDiagnostics.contains("standard_gpu_probe_model_sha256_actual=device_unavailable"))
        assertTrue(routeDiagnostics.contains("standard_gpu_probe_model_identity_hint=edge_gallery_e2b_expected"))
        assertTrue(routeDiagnostics.contains("standard_gpu_probe_runtime_stack=standardDebug"))
        assertTrue(routeDiagnostics.contains("standard_gpu_probe_callback_streaming_gate=true"))
        assertTrue(routeDiagnostics.contains("standard_gpu_probe_result_candidate=success"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_enabled=true"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_eligible=true"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_block_reason=none"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_model_size_bytes=2588147712"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_model_identity_hint=edge_gallery_e2b_expected"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_runtime_stack=standardDebug_dev_gate"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_result=success"))
        assertTrue(routeDiagnostics.contains("gpu_callback_text_promoted_to_ui=true"))
        assertTrue(routeDiagnostics.contains("gpu_ui_append_finished=true"))
        assertTrue(routeDiagnostics.contains("gpu_streaming_completion_reason=flow_completed_non_empty_response"))
        assertTrue(compact.contains("status=success"))
        assertTrue(compact.contains("failure_stage=none"))
        assertTrue(compact.contains("gpu_normal_route_use_callback_streaming=true"))
        assertTrue(compact.contains("gpu_callback_streaming_path_selected=true"))
        assertTrue(compact.contains("gpu_callback_streaming_failure_reason=none"))
        assertTrue(compact.contains("standard_gpu_probe_expected_edge_gallery_e2b=true"))
        assertTrue(compact.contains("standard_gpu_probe_result_candidate=success"))
        assertTrue(compact.contains("standard_gpu_runtime_alignment_candidate_enabled=true"))
        assertTrue(compact.contains("standard_gpu_runtime_alignment_candidate_eligible=true"))
        assertTrue(compact.contains("standard_gpu_runtime_alignment_candidate_result=success"))
    }

    @Test
    fun `Standard GPU probe classifies Edge Gallery E2B callback streaming failure`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery.litertlm",
            selectedModelFile = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_exception",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateFinished = true,
                conversationCreateStarted = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = false,
                failureStage = "gpu_generate_compiled_model_invoke_failed",
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                gpuGenerateCallEntered = true,
                gpuGenerateCallReturned = true,
                gpuGenerateExceptionSeen = true,
                gpuGenerateExceptionStatusCode = "13",
                gpuGenerateExceptionErrorFile = "runtime/executor/llm_litert_compiled_model_executor.cc",
                gpuGenerateExceptionErrorLine = "735",
                gpuGenerateExceptionSummary = "failed_to_invoke_compiled_model",
                liteRtLmErrorStatusCode = "13",
                liteRtLmErrorPrimaryFile = "runtime/executor/llm_litert_compiled_model_executor.cc",
                liteRtLmErrorPrimaryLine = "735",
                gpuNormalRouteUseCallbackStreaming = true,
                gpuCallbackStreamingPathSelected = true,
                gpuCallbackStreamingPathReason = "dev_gate_normal_route",
                gpuCallbackStreamingSuccessCount = 0,
                gpuCallbackStreamingFailureReason = "flow_response",
                standardGpuRuntimeAlignmentCandidateEnabled = true,
                standardGpuRuntimeAlignmentCandidateEligible = true,
                standardGpuRuntimeAlignmentCandidateBlockReason = "none",
                standardGpuRuntimeAlignmentCandidateModelSizeBytes = "2588147712",
                standardGpuRuntimeAlignmentCandidateModelIdentityHint = "edge_gallery_e2b_expected",
                standardGpuRuntimeAlignmentCandidateRuntimeStack = "standardDebug_dev_gate",
                standardGpuRuntimeAlignmentCandidateResult = "failure",
            ),
            elapsedMs = 2_000L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "applied",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                status = "failure",
                reason = "local_inference_failure",
                failureStage = "gpu_generate_compiled_model_invoke_failed",
                routeContext = routeContext,
                timeout = false,
            ),
        )

        assertTrue(routeDiagnostics.contains("standard_gpu_probe_expected_edge_gallery_e2b=true"))
        assertTrue(routeDiagnostics.contains("standard_gpu_probe_callback_streaming_gate=true"))
        assertTrue(routeDiagnostics.contains("standard_gpu_probe_result_candidate=failure"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_enabled=true"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_eligible=true"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_result=failure"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_stack_mismatch_summary=runtime_stack_mismatch_suspected"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_stack_single_so_swap_forbidden=true"))
        assertTrue(
            routeDiagnostics.contains(
                "standard_gpu_runtime_stack_promotion_blocked_reason=standard_runtime_stack_not_aligned",
            ),
        )
        assertTrue(routeDiagnostics.contains("gpu_generate_exception_error_line=735"))
        assertTrue(compact.contains("standard_gpu_probe_expected_edge_gallery_e2b=true"))
        assertTrue(compact.contains("standard_gpu_probe_result_candidate=failure"))
        assertTrue(compact.contains("standard_gpu_runtime_alignment_candidate_result=failure"))
        assertTrue(compact.contains("standard_gpu_runtime_stack_mismatch_summary=runtime_stack_mismatch_suspected"))
        assertTrue(compact.contains("standard_gpu_runtime_stack_single_so_swap_forbidden=true"))
        assertTrue(
            compact.contains(
                "standard_gpu_runtime_stack_promotion_blocked_reason=standard_runtime_stack_not_aligned",
            ),
        )
        assertTrue(compact.contains("litert_lm_error_status_code=13"))
    }

    @Test
    fun `Standard candidate failure remains classified as runtime stack mismatch in compact`() {
        val nativeDir = createNativeStackTestDir()
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery.litertlm",
            selectedModelFile = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
            nativeLibraryDir = nativeDir.absolutePath,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_exception",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                engineCreateFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = false,
                failureStage = "gpu_generate_compiled_model_invoke_failed",
                gpuGenerateExceptionSeen = true,
                gpuGenerateExceptionStatusCode = "13",
                gpuGenerateExceptionErrorFile = "runtime/executor/llm_litert_compiled_model_executor.cc",
                gpuGenerateExceptionErrorLine = "735",
                gpuGenerateExceptionSummary = "failed_to_invoke_compiled_model",
                liteRtLmErrorStatusCode = "13",
                liteRtLmErrorPrimaryFile = "runtime/executor/llm_litert_compiled_model_executor.cc",
                liteRtLmErrorPrimaryLine = "735",
                gpuNormalRouteUseCallbackStreaming = true,
                gpuCallbackStreamingPathSelected = true,
                gpuCallbackStreamingFailureReason = "flow_response",
                standardGpuRuntimeAlignmentCandidateEnabled = true,
                standardGpuRuntimeAlignmentCandidateEligible = true,
                standardGpuRuntimeAlignmentCandidateBlockReason = "none",
                standardGpuRuntimeAlignmentCandidateModelSizeBytes = "2588147712",
                standardGpuRuntimeAlignmentCandidateModelIdentityHint = "edge_gallery_e2b_expected",
                standardGpuRuntimeAlignmentCandidateRuntimeStack = "standardDebug_dev_gate",
                standardGpuRuntimeAlignmentCandidateResult = "failure",
            ),
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "applied",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                failureStage = "gpu_generate_compiled_model_invoke_failed",
                routeContext = routeContext,
            ),
        )

        assertTrue(routeDiagnostics.contains("runtime_stack_loaded_source_flavor=standard"))
        assertTrue(routeDiagnostics.contains("runtime_stack_loaded_liblitert_present=true"))
        assertTrue(routeDiagnostics.contains("runtime_stack_loaded_liblitertlm_jni_present=true"))
        assertTrue(routeDiagnostics.contains("runtime_stack_loaded_dispatch_qualcomm_present=true"))
        assertTrue(routeDiagnostics.contains("runtime_stack_loaded_compiler_plugin_qualcomm_present=true"))
        assertTrue(routeDiagnostics.contains("runtime_stack_loaded_gemma_constraint_provider_present=true"))
        assertTrue(routeDiagnostics.contains("runtime_stack_alignment_interpretation=standard_runtime_stack_mismatch_candidate"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_stack_mismatch_high_priority_candidates=libLiteRt.so,liblitertlm_jni.so,libLiteRtDispatch_Qualcomm.so,libLiteRtCompilerPlugin_Qualcomm.so,libGemmaModelConstraintProvider.so"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_stack_required_alignment_unit=libLiteRt.so+liblitertlm_jni.so+libLiteRtDispatch_Qualcomm.so+libLiteRtCompilerPlugin_Qualcomm.so+libGemmaModelConstraintProvider.so"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_stack_mismatch_summary=runtime_stack_mismatch_suspected"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_stack_promotion_blocked_reason=standard_runtime_stack_not_aligned"))
        assertTrue(compact.contains("runtime_stack_loaded_source_flavor=standard"))
        assertTrue(compact.contains("runtime_stack_loaded_liblitert_present=true"))
        assertTrue(compact.contains("runtime_stack_loaded_liblitertlm_jni_present=true"))
        assertTrue(compact.contains("runtime_stack_alignment_interpretation=standard_runtime_stack_mismatch_candidate"))
        assertTrue(compact.contains("standard_gpu_runtime_alignment_candidate_result=failure"))
        assertTrue(compact.contains("standard_gpu_runtime_stack_mismatch_summary=runtime_stack_mismatch_suspected"))
        assertTrue(compact.contains("standard_gpu_runtime_stack_promotion_blocked_reason=standard_runtime_stack_not_aligned"))
    }

    @Test
    fun `Runtime alignment probe success keeps success stack interpretation`() {
        assertEquals(
            "runtime_alignment_probe_stack_success",
            resolveRuntimeNativeStackAlignmentInterpretation(
                sourceFlavor = "gpuRuntimeAlignmentProbe",
                standardCandidateResult = "unknown",
                runtimeAlignmentResult = "success",
            ),
        )
    }

    @Test
    fun `standard GPU minimal runtime candidate flavor keeps stack interpretation`() {
        assertEquals(
            "standard_gpu_minimal_runtime_candidate_stack_success",
            resolveRuntimeNativeStackAlignmentInterpretation(
                sourceFlavor = "standardGpuMinimalRuntimeCandidate",
                standardCandidateResult = "unknown",
                runtimeAlignmentResult = "success",
            ),
        )
        assertEquals(
            "standard_gpu_minimal_runtime_candidate_stack_failure",
            resolveRuntimeNativeStackAlignmentInterpretation(
                sourceFlavor = "standardGpuMinimalRuntimeCandidate",
                standardCandidateResult = "unknown",
                runtimeAlignmentResult = "failure",
            ),
        )
    }

    @Test
    fun `minimal runtime probe classifies callback streaming success`() {
        val result = resolveMinimalRuntimeProbeResultCandidateForDebug(
            flags = LocalRouteDiagnosticFlags(
                gpuCallbackTextPromotedToUi = true,
                gpuUiAppendFinished = true,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuCallbackStreamingPathSelected = true,
            ),
            failureStage = "none",
        )

        assertEquals("success", result)
    }

    @Test
    fun `minimal runtime probe classifies GPU generate failure`() {
        val result = resolveMinimalRuntimeProbeResultCandidateForDebug(
            flags = LocalRouteDiagnosticFlags(
                gpuGenerateExceptionSeen = true,
                gpuGenerateExceptionStatusCode = "13",
                gpuGenerateExceptionErrorLine = "735",
                gpuCallbackStreamingPathSelected = true,
            ),
            failureStage = "gpu_generate_compiled_model_invoke_failed",
        )

        assertEquals("failure", result)
    }

    @Test
    fun `CPU and NPU routes do not emit GPU loaded runtime stack diagnostics`() {
        val cpuDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "success",
            context = buildLocalRouteDiagnosticContext(
                selectedModelName = "gemma-4-E2B-it.litertlm",
                selectedModelFile = "/sdcard/Download/gemma-4-E2B-it.litertlm",
                preferredBackend = "CPU",
                npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                shouldEnterNpuS1 = false,
                localRouteEntered = true,
                nativeLibraryDir = createNativeStackTestDir().absolutePath,
            ),
            flags = LocalRouteDiagnosticFlags(failureStage = "none"),
        )
        val npuDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "success",
            context = buildLocalRouteDiagnosticContext(
                selectedModelName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
                selectedModelFile = "/sdcard/Download/gemma-4-E2B-it_qualcomm_sm8750.litertlm",
                preferredBackend = "NPU_S1",
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
                effectiveNpuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
                shouldEnterNpuS1 = true,
                localRouteEntered = false,
                nativeLibraryDir = createNativeStackTestDir().absolutePath,
            ),
            flags = LocalRouteDiagnosticFlags(failureStage = "none"),
        )

        assertFalse(cpuDiagnostics.contains("runtime_stack_loaded_source_flavor="))
        assertFalse(npuDiagnostics.contains("runtime_stack_loaded_source_flavor="))
        assertFalse(cpuDiagnostics.contains("minimal_runtime_probe_flavor="))
        assertFalse(npuDiagnostics.contains("minimal_runtime_probe_flavor="))
        assertFalse(cpuDiagnostics.contains("standard_gpu_minimal_runtime_candidate_flavor="))
        assertFalse(npuDiagnostics.contains("standard_gpu_minimal_runtime_candidate_flavor="))
        assertFalse(cpuDiagnostics.contains("standard_gpu_minimal_runtime_candidate_enabled="))
        assertFalse(npuDiagnostics.contains("standard_gpu_minimal_runtime_candidate_enabled="))
        assertFalse(cpuDiagnostics.contains("standard_gpu_runtime_stack_mismatch_summary="))
        assertFalse(npuDiagnostics.contains("standard_gpu_runtime_stack_mismatch_summary="))
    }

    @Test
    fun `standard candidate gate off does not add standard runtime diagnostics to normal GPU model`() {
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_exception",
            context = buildLocalRouteDiagnosticContext(
                selectedModelName = "some-other-model.litertlm",
                selectedModelFile = "/sdcard/Download/some-other-model.litertlm",
                preferredBackend = "GPU",
                npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                shouldEnterNpuS1 = false,
                localRouteEntered = true,
            ),
            flags = LocalRouteDiagnosticFlags(
                failureStage = "gpu_generate_compiled_model_invoke_failed",
                gpuGenerateExceptionSeen = true,
                gpuGenerateExceptionErrorLine = "735",
                gpuNormalRouteUseCallbackStreaming = false,
            ),
        )

        assertFalse(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_enabled="))
        assertFalse(routeDiagnostics.contains("standard_gpu_runtime_stack_mismatch_summary="))
    }

    @Test
    fun `Standard GPU runtime alignment candidate is disabled by default in diagnostics`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery.litertlm",
            selectedModelFile = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_exception",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                engineCreateFinished = true,
                conversationCreateStarted = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = false,
                failureStage = "gpu_generate_compiled_model_invoke_failed",
                gpuGenerateExceptionSeen = true,
                gpuGenerateExceptionStatusCode = "13",
                gpuGenerateExceptionErrorFile = "runtime/executor/llm_litert_compiled_model_executor.cc",
                gpuGenerateExceptionErrorLine = "735",
                gpuGenerateExceptionSummary = "failed_to_invoke_compiled_model",
                gpuNormalRouteUseCallbackStreaming = false,
                gpuCallbackStreamingPathSelected = false,
            ),
            elapsedMs = 2_000L,
        )

        assertTrue(routeDiagnostics.contains("standard_gpu_probe_expected_edge_gallery_e2b=true"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_enabled=false"))
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_eligible=false"))
        assertTrue(
            routeDiagnostics.contains(
                "standard_gpu_runtime_alignment_candidate_block_reason=candidate_gate_disabled",
            ),
        )
        assertTrue(routeDiagnostics.contains("standard_gpu_runtime_alignment_candidate_result=failure"))
        assertTrue(routeDiagnostics.contains("gpu_generate_exception_error_line=735"))
    }

    @Test
    fun `Standard GPU minimal runtime candidate gate emits blocked diagnostics in compact`() {
        val tempDir = Files.createTempDirectory("lami-standard-minimal-candidate-model").toFile()
        val model = tempDir.resolve("gemma-4-E2B-it-edge-gallery.litertlm")
        val nativeDir = createCorePairNativeStackTestDir()
        System.setProperty("debug.lami.standard_gpu_minimal_runtime_candidate", "true")
        try {
            RandomAccessFile(model, "rw").use { file ->
                file.setLength(STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES)
            }
            val routeContext = buildLocalRouteDiagnosticContext(
                selectedModelName = model.name,
                selectedModelFile = model.absolutePath,
                selectedModelPath = model.absolutePath,
                preferredBackend = "GPU",
                npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                shouldEnterNpuS1 = false,
                localRouteEntered = true,
                nativeLibraryDir = nativeDir.absolutePath,
            )
            val routeDiagnostics = buildLocalRouteDiagnosticTrace(
                stage = "generate_exception",
                context = routeContext,
                flags = LocalRouteDiagnosticFlags(
                    failureStage = "gpu_generate_compiled_model_invoke_failed",
                    gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                    gpuGenerateExceptionSeen = true,
                    gpuGenerateExceptionStatusCode = "13",
                    gpuGenerateExceptionErrorLine = "735",
                    gpuNormalRouteUseCallbackStreaming = true,
                    gpuCallbackStreamingPathSelected = true,
                ),
            )
            val compact = buildLocalInferenceFailureCompactDiagnosticsText(
                buildLocalInferenceFailureCompactInputFromTrace(
                    inputPrompt = "こんにちは",
                    preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                    npuStandardRouteMode = NpuStandardRouteMode.OFF,
                    trace = LocalInferenceTrace(
                        requestedPreferredBackend = "GPU",
                        appliedPreferredBackend = "GPU",
                        preferredBackendApplyResult = "applied",
                        localFailureDiagnosticsText = routeDiagnostics,
                    ),
                    failureStage = "gpu_generate_compiled_model_invoke_failed",
                    routeContext = routeContext,
                ),
            )

            assertTrue(routeDiagnostics.contains("standard_gpu_minimal_runtime_candidate_enabled=true"))
            assertTrue(routeDiagnostics.contains("standard_gpu_minimal_runtime_candidate_eligible=false"))
            assertTrue(
                routeDiagnostics.contains(
                    "standard_gpu_minimal_runtime_candidate_block_reason=liblitert_sha_mismatch",
                ),
            )
            assertTrue(routeDiagnostics.contains("standard_gpu_minimal_runtime_candidate_result=failure"))
            assertTrue(routeDiagnostics.contains("standard_gpu_minimal_runtime_candidate_success_gate=false"))
            assertTrue(routeDiagnostics.contains("standard_gpu_minimal_runtime_candidate_dispatch_present=false"))
            assertTrue(routeDiagnostics.contains("standard_gpu_minimal_runtime_candidate_compiler_plugin_present=false"))
            assertTrue(routeDiagnostics.contains("standard_gpu_minimal_runtime_candidate_constraint_provider_present=false"))
            assertTrue(
                routeDiagnostics.contains(
                    "standard_gpu_minimal_runtime_candidate_runtime_stack=standardDebug_minimal_runtime_dev_gate",
                ),
            )
            assertTrue(
                routeDiagnostics.contains(
                    "standard_gpu_minimal_runtime_candidate_interpretation=blocked:liblitert_sha_mismatch",
                ),
            )
            assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_enabled=true"))
            assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_eligible=false"))
            assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_result=failure"))
            assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_dispatch_present=false"))
            assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_compiler_plugin_present=false"))
            assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_constraint_provider_present=false"))
        } finally {
            System.clearProperty("debug.lami.standard_gpu_minimal_runtime_candidate")
            model.delete()
            tempDir.delete()
            nativeDir.deleteRecursively()
        }
    }

    @Test
    fun `Standard GPU minimal runtime candidate classifies success with matching core pair`() {
        val tempDir = Files.createTempDirectory("lami-standard-minimal-candidate-model").toFile()
        val model = tempDir.resolve("gemma-4-E2B-it-edge-gallery.litertlm")
        System.setProperty("debug.lami.standard_gpu_minimal_runtime_candidate", "true")
        try {
            RandomAccessFile(model, "rw").use { file ->
                file.setLength(STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES)
            }
            val diagnostics = buildStandardGpuMinimalRuntimeCandidateDiagnostics(
                context = buildLocalRouteDiagnosticContext(
                    selectedModelName = model.name,
                    selectedModelFile = model.absolutePath,
                    selectedModelPath = model.absolutePath,
                    preferredBackend = "GPU",
                    npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                    shouldEnterNpuS1 = false,
                    localRouteEntered = true,
                ),
                flags = LocalRouteDiagnosticFlags(
                    failureStage = "none",
                    gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                    gpuNormalRouteUseCallbackStreaming = true,
                    gpuCallbackStreamingPathSelected = true,
                    gpuCallbackTextPromotedToUi = true,
                    gpuUiAppendFinished = true,
                    gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                ),
                failureStage = "none",
                loadedRuntimeNativeStack = matchingMinimalLoadedRuntimeStack(),
            )

            assertTrue(diagnostics.emit)
            assertEquals("true", diagnostics.enabled)
            assertEquals("true", diagnostics.eligible)
            assertEquals("none", diagnostics.blockReason)
            assertEquals("success", diagnostics.result)
            assertEquals("true", diagnostics.successGate)
            assertEquals("false", diagnostics.dispatchPresent)
            assertEquals("false", diagnostics.compilerPluginPresent)
            assertEquals("false", diagnostics.constraintProviderPresent)
            assertEquals("minimal_runtime_core_pair_candidate_success", diagnostics.interpretation)
        } finally {
            System.clearProperty("debug.lami.standard_gpu_minimal_runtime_candidate")
            model.delete()
            tempDir.delete()
        }
    }

    @Test
    fun `Standard GPU minimal runtime candidate classifies cc735 failure with matching core pair`() {
        val tempDir = Files.createTempDirectory("lami-standard-minimal-candidate-model").toFile()
        val model = tempDir.resolve("gemma-4-E2B-it-edge-gallery.litertlm")
        System.setProperty("debug.lami.standard_gpu_minimal_runtime_candidate", "true")
        try {
            RandomAccessFile(model, "rw").use { file ->
                file.setLength(STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES)
            }
            val diagnostics = buildStandardGpuMinimalRuntimeCandidateDiagnostics(
                context = buildLocalRouteDiagnosticContext(
                    selectedModelName = model.name,
                    selectedModelFile = model.absolutePath,
                    selectedModelPath = model.absolutePath,
                    preferredBackend = "GPU",
                    npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                    shouldEnterNpuS1 = false,
                    localRouteEntered = true,
                ),
                flags = LocalRouteDiagnosticFlags(
                    failureStage = "gpu_generate_compiled_model_invoke_failed",
                    gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                    gpuNormalRouteUseCallbackStreaming = true,
                    gpuCallbackStreamingPathSelected = true,
                    gpuGenerateExceptionSeen = true,
                    gpuGenerateExceptionStatusCode = "13",
                    gpuGenerateExceptionErrorLine = "735",
                    liteRtLmErrorStatusCode = "13",
                    liteRtLmErrorPrimaryLine = "735",
                ),
                failureStage = "gpu_generate_compiled_model_invoke_failed",
                loadedRuntimeNativeStack = matchingMinimalLoadedRuntimeStack(),
            )

            assertTrue(diagnostics.emit)
            assertEquals("true", diagnostics.eligible)
            assertEquals("failure", diagnostics.result)
            assertEquals("minimal_runtime_core_pair_candidate_failed_cc735", diagnostics.interpretation)
        } finally {
            System.clearProperty("debug.lami.standard_gpu_minimal_runtime_candidate")
            model.delete()
            tempDir.delete()
        }
    }

    @Test
    fun `standard GPU minimal runtime candidate flavor diagnostics are preserved in compact`() {
        val routeDiagnostics = listOf(
            "LOCAL_ROUTE_DIAG",
            "selected_model_name=gemma-4-E2B-it-edge-gallery.litertlm",
            "selected_model_file=/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            "preferred_backend=GPU",
            "failure_stage=none",
            "gpu_callback_streaming_path_selected=true",
            "gpu_callback_text_promoted_to_ui=true",
            "gpu_ui_append_finished=true",
            "gpu_streaming_completion_reason=flow_completed_non_empty_response",
            "standard_gpu_minimal_runtime_candidate_flavor=true",
            "standard_gpu_minimal_runtime_candidate_application_id=io.github.ninbyo02.lami.gpustandardminimal",
            "standard_gpu_minimal_runtime_candidate_enabled=true",
            "standard_gpu_minimal_runtime_candidate_eligible=true",
            "standard_gpu_minimal_runtime_candidate_block_reason=none",
            "standard_gpu_minimal_runtime_candidate_result=success",
            "standard_gpu_minimal_runtime_candidate_success_gate=true",
            "standard_gpu_minimal_runtime_candidate_loaded_liblitert_sha256=$STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERT_SHA256",
            "standard_gpu_minimal_runtime_candidate_loaded_liblitertlm_jni_sha256=$STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERTLM_JNI_SHA256",
            "standard_gpu_minimal_runtime_candidate_liblitert_sha256=$STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERT_SHA256",
            "standard_gpu_minimal_runtime_candidate_liblitertlm_jni_sha256=$STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERTLM_JNI_SHA256",
            "standard_gpu_minimal_runtime_candidate_dispatch_present=false",
            "standard_gpu_minimal_runtime_candidate_compiler_plugin_present=false",
            "standard_gpu_minimal_runtime_candidate_constraint_provider_present=false",
            "standard_gpu_minimal_runtime_candidate_runtime_stack=standardGpuMinimalRuntimeCandidateDebug_minimal_runtime_pair",
            "standard_gpu_minimal_runtime_candidate_runtime_stack_source=dev-only-standard-like-GPU-minimal-runtime-candidate",
            "standard_gpu_minimal_runtime_candidate_interpretation=minimal_runtime_core_pair_candidate_success",
            "runtime_stack_loaded_source_flavor=standardGpuMinimalRuntimeCandidate",
            "runtime_stack_alignment_interpretation=standard_gpu_minimal_runtime_candidate_stack_success",
        ).joinToString(" ")
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                status = "success",
                reason = "local_inference_success",
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "applied",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                failureStage = "none",
            ),
        )

        assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_flavor=true"))
        assertTrue(
            compact.contains(
                "standard_gpu_minimal_runtime_candidate_application_id=io.github.ninbyo02.lami.gpustandardminimal",
            ),
        )
        assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_result=success"))
        assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_success_gate=true"))
        assertTrue(
            compact.contains(
                "standard_gpu_minimal_runtime_candidate_loaded_liblitert_sha256=$STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERT_SHA256",
            ),
        )
        assertTrue(
            compact.contains(
                "standard_gpu_minimal_runtime_candidate_loaded_liblitertlm_jni_sha256=$STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERTLM_JNI_SHA256",
            ),
        )
        assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_dispatch_present=false"))
        assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_compiler_plugin_present=false"))
        assertTrue(compact.contains("standard_gpu_minimal_runtime_candidate_constraint_provider_present=false"))
        assertTrue(
            compact.contains(
                "runtime_stack_alignment_interpretation=standard_gpu_minimal_runtime_candidate_stack_success",
            ),
        )
    }

    @Test
    fun `GPU callback done without text is classified`() {
        val flags = LocalRouteDiagnosticFlags(
            generateStarted = true,
            firstTokenReceived = false,
            gpuGenerateCallEntered = true,
            gpuGenerateCallReturned = true,
            gpuCallbackInvokedCount = 1,
            gpuCallbackEmptyTextCount = 1,
            gpuCallbackNonEmptyTextCount = 0,
            gpuCallbackDoneTrueSeen = true,
            gpuCallbackExceptionClass = "none",
        )

        assertEquals("callback_done_without_text", resolveGpuGenerateStallInterpretation(flags))
    }

    @Test
    fun `GPU callback exception before first token is copied into compact`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_callback_exception",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                engineInitializeFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = false,
                failureStage = "generate-callback-exception",
                gpuGenerateCallEntered = true,
                gpuGenerateCallReturned = true,
                gpuCallbackInvokedCount = 0,
                gpuCallbackErrorSeen = true,
                gpuCallbackExceptionClass = "java.lang.IllegalStateException",
                gpuCallbackExceptionMessage = "callback failed",
                gpuCallbackExceptionChain = "java.lang.IllegalStateException:callback failed",
                gpuCallbackExceptionStage = "flow_collect_callback",
            ),
            elapsedMs = 1_500L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "failed",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                routeContext = routeContext,
            ),
        )

        assertTrue(routeDiagnostics.contains("gpu_callback_exception_class=java.lang.IllegalStateException"))
        assertTrue(routeDiagnostics.contains("gpu_generate_stall_interpretation=callback_exception_before_first_token"))
        assertTrue(compact.contains("gpu_callback_exception_class=java.lang.IllegalStateException"))
        assertTrue(compact.contains("gpu_callback_exception_message=callback_failed"))
        assertTrue(compact.contains("gpu_callback_exception_chain=java.lang.IllegalStateException:callback_failed"))
        assertTrue(compact.contains("gpu_callback_exception_stage=flow_collect_callback"))
        assertTrue(compact.contains("gpu_generate_stall_interpretation=callback_exception_before_first_token"))
    }

    @Test
    fun `LiteRT LM status code 13 invoke failure is parsed`() {
        val error = classifyLiteRtLmError(
            "Status Code: 13. Message: ERROR: [runtime/executor/llm_litert_compiled_model_executor.cc:735] " +
                "Failed to invoke the compiled model",
        )

        assertEquals("compiled_model_invoke_failed", error.kind)
        assertEquals("13", error.statusCode)
        assertEquals("runtime/executor/llm_litert_compiled_model_executor.cc", error.primaryFile)
        assertEquals("735", error.primaryLine)
        assertEquals("failed_to_invoke_compiled_model", error.summary)
        assertEquals("try_gpu_runtime_stack_alignment", error.recoverabilityHint)
        assertEquals("compiled_model_invoke", classifyLiteRtCompiledModelExecutorFailureCategory(error))
    }

    @Test
    fun `LiteRT LM status code 3 max token budget is parsed`() {
        val error = classifyLiteRtLmError(
            "Status_Code:_3._Message:_Input_token_ids_are_too_long._10_>=_1",
        )

        assertEquals("max_tokens_too_small", error.kind)
        assertEquals("3", error.statusCode)
        assertEquals("input_token_ids_too_long", error.summary)
        assertEquals("max_tokens_too_small", error.recoverabilityHint)
        assertEquals("compiled_model_invoke_input_budget", classifyLiteRtCompiledModelExecutorFailureCategory(error))
    }

    @Test
    fun `LiteRT LM create engine file lines are parsed`() {
        val error = classifyLiteRtLmError(
            "Failed_to_create_engine:_INTERNAL:_ERROR:_[runtime/executor/llm_litert_compiled_model_executor.cc:1546] " +
                "ERROR:[external/litert/litert/cc/litert_compiled_model.h:1140]",
        )

        assertEquals("compiled_model_creation_failed", error.kind)
        assertEquals("runtime/executor/llm_litert_compiled_model_executor.cc", error.primaryFile)
        assertEquals("1546", error.primaryLine)
        assertEquals("external/litert/litert/cc/litert_compiled_model.h", error.secondaryFile)
        assertEquals("1140", error.secondaryLine)
        assertEquals("failed_to_create_engine", error.summary)
        assertEquals("compiled_model_load", classifyLiteRtCompiledModelExecutorFailureCategory(error))
    }

    @Test
    fun `GPU generate compiled model invoke failure bypasses watchdog in compact`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_exception",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                engineInitializeFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = false,
                failureStage = "gpu_generate_compiled_model_invoke_failed",
                gpuConfigDiagnostics = buildGpuRouteConfigDiagnostics(
                    modelPath = "/models/gemma-4-E2B-it.litertlm",
                    cacheDirPath = "/cache",
                    preferredBackend = "GPU",
                    experimentMode = GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION,
                ),
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NO_SAMPLER,
                gpuGeneratePrompt = "こんにちは",
                gpuGeneratePromptLengthChars = 5,
                gpuGenerateInputTokenEstimate = "unavailable",
                gpuGenerateCallEntered = true,
                gpuGenerateCallReturned = true,
                gpuCallbackInvokedCount = 0,
                gpuCallbackErrorSeen = true,
                gpuCallbackExceptionClass = "com.google.ai.edge.litertlm.LiteRtLmJniException",
                gpuCallbackExceptionMessage =
                    "Status_Code:_13._Message:_ERROR:_[runtime/executor/llm_litert_compiled_model_executor.cc:735]_Failed_to_invoke_the_compiled_model",
                gpuCallbackExceptionStage = "flow_response",
                gpuGenerateExceptionSeen = true,
                gpuGenerateExceptionClass = "com.google.ai.edge.litertlm.LiteRtLmJniException",
                gpuGenerateExceptionMessageRaw =
                    "com.google.ai.edge.litertlm.LiteRtLmJniException:Status Code: 13. Message: ERROR: " +
                        "[runtime/executor/llm_litert_compiled_model_executor.cc:735] Failed to invoke the compiled model",
                gpuGenerateExceptionMessageSanitized =
                    "com.google.ai.edge.litertlm.LiteRtLmJniException:Status_Code:_13._Message:_ERROR:_[runtime/executor/llm_litert_compiled_model_executor.cc:735]_Failed_to_invoke_the_compiled_model",
                gpuGenerateExceptionStatusCode = "13",
                gpuGenerateExceptionErrorFile = "runtime/executor/llm_litert_compiled_model_executor.cc",
                gpuGenerateExceptionErrorLine = "735",
                gpuGenerateExceptionSummary = "failed_to_invoke_compiled_model",
                gpuGenerateFailedBeforeFirstToken = true,
                gpuWatchdogBypassedDueToGenerateException = true,
                liteRtLmErrorKind = "compiled_model_invoke_failed",
                liteRtLmErrorStatusCode = "13",
                liteRtLmErrorPrimaryFile = "runtime/executor/llm_litert_compiled_model_executor.cc",
                liteRtLmErrorPrimaryLine = "735",
                liteRtLmErrorRecoverabilityHint = "try_gpu_runtime_stack_alignment",
            ),
            elapsedMs = 3_000L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "failed",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                routeContext = routeContext,
            ),
        )

        assertTrue(routeDiagnostics.contains("failure_stage=gpu_generate_compiled_model_invoke_failed"))
        assertTrue(routeDiagnostics.contains("gpu_generate_exception_status_code=13"))
        assertTrue(routeDiagnostics.contains("gpu_generate_exception_error_line=735"))
        assertTrue(routeDiagnostics.contains("gpu_generate_exception_summary=failed_to_invoke_compiled_model"))
        assertTrue(routeDiagnostics.contains("gpu_watchdog_bypassed_due_to_generate_exception=true"))
        assertTrue(routeDiagnostics.contains("litert_lm_error_kind=compiled_model_invoke_failed"))
        assertTrue(routeDiagnostics.contains("litert_lm_error_recoverability_hint=try_gpu_runtime_stack_alignment"))
        assertTrue(routeDiagnostics.contains("litert_compiled_model_executor_failure_category=compiled_model_invoke"))
        assertTrue(routeDiagnostics.contains("gpu_sampler_config_enabled=false"))
        assertTrue(routeDiagnostics.contains("gpu_sampler_acceleration_policy=conversation_config_without_sampler"))
        assertTrue(routeDiagnostics.contains("gpu_failure_interpretation=compiled_model_invoke_failed_during_generate"))
        assertTrue(routeDiagnostics.contains("litert_runtime_executor_candidates="))
        assertTrue(routeDiagnostics.contains("litert_runtime_executor_selection_hint="))
        assertTrue(routeDiagnostics.contains("litert_runtime_backend_constraint_hint="))
        assertTrue(routeDiagnostics.contains("litert_runtime_compiled_model_executor_hint="))
        assertTrue(routeDiagnostics.contains("litert_runtime_gpu_executor_hint="))
        assertTrue(routeDiagnostics.contains("litert_runtime_artisan_evidence="))
        assertFalse(routeDiagnostics.contains("gpu_compiled_model_creation_failed=true"))
        assertTrue(compact.contains("failure_stage=gpu_generate_compiled_model_invoke_failed"))
        assertTrue(compact.contains("gpu_generate_exception_seen=true"))
        assertTrue(compact.contains("gpu_generate_exception_status_code=13"))
        assertTrue(compact.contains("gpu_generate_exception_error_file=runtime/executor/llm_litert_compiled_model_executor.cc"))
        assertTrue(compact.contains("gpu_generate_exception_error_line=735"))
        assertTrue(compact.contains("gpu_generate_exception_summary=failed_to_invoke_compiled_model"))
        assertTrue(compact.contains("gpu_generate_failed_before_first_token=true"))
        assertTrue(compact.contains("gpu_watchdog_bypassed_due_to_generate_exception=true"))
        assertTrue(compact.contains("litert_lm_error_kind=compiled_model_invoke_failed"))
        assertTrue(compact.contains("litert_lm_error_status_code=13"))
        assertTrue(compact.contains("litert_lm_error_recoverability_hint=try_gpu_runtime_stack_alignment"))
        assertTrue(compact.contains("litert_compiled_model_executor_failure_category=compiled_model_invoke"))
        assertTrue(compact.contains("litert_runtime_executor_candidates="))
        assertTrue(compact.contains("litert_runtime_executor_selection_hint="))
        assertTrue(compact.contains("litert_runtime_backend_constraint_hint="))
        assertTrue(compact.contains("litert_runtime_compiled_model_executor_hint="))
        assertTrue(compact.contains("litert_runtime_gpu_executor_hint="))
        assertTrue(compact.contains("litert_runtime_artisan_evidence="))
        assertTrue(compact.contains("gpu_generate_actual_prompt=こんにちは"))
        assertTrue(compact.contains("gpu_generate_prompt_length_chars=5"))
        assertTrue(compact.contains("gpu_generate_input_token_estimate=unavailable"))
    }

    @Test
    fun `GPU generate probe mode resolves only for GPU debug route`() {
        val reader = { key: String ->
            when (key) {
                "debug.lami.gpu_generate_probe_mode" -> GPU_GENERATE_PROBE_MODE_CALLBACK_TO_UI
                else -> null
            }
        }

        assertEquals(
            GPU_GENERATE_PROBE_MODE_CALLBACK_TO_UI,
            resolveGpuGenerateProbeModeForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = reader,
            ),
        )
        assertEquals(
            GPU_GENERATE_PROBE_MODE_NORMAL,
            resolveGpuGenerateProbeModeForDebug(
                preferredBackend = PreferredBackendDryRunSetting.CPU,
                propertyReader = reader,
            ),
        )
        assertEquals(
            GPU_GENERATE_PROBE_MODE_NORMAL_CALLBACK_STREAMING,
            resolveGpuGenerateProbeModeForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    when (key) {
                        "debug.lami.gpu_generate_probe_mode" -> GPU_GENERATE_PROBE_MODE_NORMAL_CALLBACK_STREAMING
                        else -> null
                    }
                },
            ),
        )
        assertTrue(usesGpuCallbackStreamingPathForDebug(GPU_GENERATE_PROBE_MODE_CALLBACK_TO_UI))
        assertTrue(usesGpuCallbackStreamingPathForDebug(GPU_GENERATE_PROBE_MODE_NORMAL_CALLBACK_STREAMING))
        assertFalse(usesGpuCallbackStreamingPathForDebug(GPU_GENERATE_PROBE_MODE_RAW_CALLBACK_ONLY))
        assertTrue(
            isGpuNormalRouteUseCallbackStreamingRequestedForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    when (key) {
                        "debug.lami.gpu_normal_route_use_callback_streaming" -> "true"
                        else -> null
                    }
                },
            ),
        )
        assertFalse(
            isGpuNormalRouteUseCallbackStreamingRequestedForDebug(
                preferredBackend = PreferredBackendDryRunSetting.CPU,
                propertyReader = { "true" },
            ),
        )
        assertTrue(
            isGpuCallbackStreamingPathSelectedForDebug(
                probeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                normalRouteUseCallbackStreaming = true,
            ),
        )
        assertEquals(
            "dev_gate_normal_route",
            resolveGpuCallbackStreamingPathReasonForDebug(
                probeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                normalRouteUseCallbackStreaming = true,
            ),
        )
    }

    @Test
    fun `GPU timeout compact includes held engine lifecycle destroy diagnostics`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "gpu_watchdog_holder_cleanup_finished",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = false,
                heldEngineReused = false,
                engineInitializeFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = false,
                failureStage = "gpu_watchdog_timeout",
                fallbackUsed = false,
                staleCallbackIgnored = true,
                holderCreated = true,
                holderAcquired = true,
                holderReused = false,
                holderInvalidated = true,
                holderClosed = true,
                holderTimeoutCleanup = true,
                holderFailureCleanup = false,
                holderProcessRestart = true,
                heldEngineLifecycleHistory =
                    "holder_acquired@elapsed_ms=1:reason=acquire:heldHash=123|" +
                        "holder_timeout_cleanup@elapsed_ms=2:reason=gpu_watchdog_timeout_holder_clear:heldHash=123|" +
                        "holder_closed@elapsed_ms=3:reason=gpu_watchdog_timeout_holder_clear:heldHash=123",
                heldEngineDestroyReason = "gpu_watchdog_timeout_holder_clear",
                heldEngineLastOwner = "ChatScreen.gpuExperimentalWatchdog",
                heldEngineLastFailureStage = "gpu_watchdog_timeout",
                heldEngineSnapshotBeforeDestroy =
                    "holder_hash=7;engine_hash=123;backend=GPU;model_path=/models/gemma-4-E2B-it.litertlm;" +
                        "initialize_state=see_gpu_engine_initialize_finished;conversation_state=see_gpu_conversation_create_finished;generate_state=see_gpu_generate_started",
            ),
            elapsedMs = 60_002L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "timeout",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                reason = "gpu_watchdog_timeout",
                routeContext = routeContext,
                timeout = true,
            ),
        )

        assertTrue(routeDiagnostics.contains("holder_timeout_cleanup=true"))
        assertTrue(routeDiagnostics.contains("held_engine_destroy_reason=gpu_watchdog_timeout_holder_clear"))
        assertTrue(routeDiagnostics.contains("held_engine_last_owner=ChatScreen.gpuExperimentalWatchdog"))
        assertTrue(routeDiagnostics.contains("held_engine_last_failure_stage=gpu_watchdog_timeout"))
        assertTrue(routeDiagnostics.contains("held_engine_snapshot_before_destroy=holder_hash=7"))
        assertTrue(compact.contains("holder_invalidated=true"))
        assertTrue(compact.contains("holder_closed=true"))
        assertTrue(compact.contains("holder_timeout_cleanup=true"))
        assertTrue(compact.contains("held_engine_destroy_reason=gpu_watchdog_timeout_holder_clear"))
        assertTrue(compact.contains("held_engine_lifecycle_history=holder_acquired@elapsed_ms=1"))
        assertTrue(compact.contains("held_engine_snapshot_before_destroy=holder_hash=7"))
        assertTrue(compact.contains("gpu_alignment_holder_cleared=true"))
        assertTrue(compact.contains("gpu_alignment_holder_clear_reason=gpu_watchdog_timeout_holder_clear"))
        assertTrue(compact.contains("gpu_alignment_holder_reuse_block_reason=timeout_cleanup"))
    }

    @Test
    fun `runtime alignment success compact includes holder first turn diagnostics`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery",
            selectedModelFile = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = false,
                engineCreateStarted = true,
                engineCreateFinished = true,
                conversationCreateStarted = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                fallbackUsed = false,
                gpuCallbackTextPromotedToUi = true,
                gpuUiAppendFinished = true,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuCallbackStreamingPathSelected = true,
                gpuCallbackStreamingReusedHeldEngine = false,
                gpuAlignmentHolderPresentBeforeAcquire = false,
                gpuAlignmentHolderAcquireResult = "created",
                gpuAlignmentHolderReused = false,
                gpuAlignmentHolderCreated = true,
                gpuAlignmentHolderCleared = false,
                gpuAlignmentPreviousTurnSuccess = "unavailable",
            ),
            elapsedMs = 1_234L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "success",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                status = "success",
                reason = "gpu_callback_streaming_success",
                failureStage = "none",
                routeContext = routeContext,
            ),
        )

        assertTrue(routeDiagnostics.contains("gpu_alignment_holder_present_before_acquire=false"))
        assertTrue(routeDiagnostics.contains("gpu_alignment_holder_acquire_result=created"))
        assertTrue(routeDiagnostics.contains("gpu_alignment_holder_reused=false"))
        assertTrue(routeDiagnostics.contains("gpu_alignment_holder_created=true"))
        assertTrue(routeDiagnostics.contains("gpu_alignment_holder_reuse_block_reason=first_turn_no_previous_holder"))
        assertTrue(compact.contains("gpu_alignment_holder_present_before_acquire=false"))
        assertTrue(compact.contains("gpu_alignment_holder_acquire_result=created"))
        assertTrue(compact.contains("gpu_alignment_holder_reuse_block_reason=first_turn_no_previous_holder"))
        assertTrue(compact.contains("gpu_callback_streaming_reused_held_engine=false"))
    }

    @Test
    fun `runtime alignment holder reuse is classified as reuse ok`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery",
            selectedModelFile = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateStarted = false,
                engineCreateFinished = true,
                conversationCreateStarted = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                fallbackUsed = false,
                gpuCallbackTextPromotedToUi = true,
                gpuUiAppendFinished = true,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuCallbackStreamingPathSelected = true,
                gpuCallbackStreamingReusedHeldEngine = true,
                gpuAlignmentHolderPresentBeforeAcquire = true,
                gpuAlignmentHolderAcquireResult = "reused",
                gpuAlignmentHolderReused = true,
                gpuAlignmentHolderCreated = false,
                gpuAlignmentPreviousTurnSuccess = "true",
            ),
            elapsedMs = 980L,
        )

        assertTrue(routeDiagnostics.contains("gpu_alignment_holder_reused=true"))
        assertTrue(routeDiagnostics.contains("gpu_alignment_holder_acquire_result=reused"))
        assertTrue(routeDiagnostics.contains("gpu_alignment_holder_reuse_block_reason=reuse_ok"))
        assertTrue(routeDiagnostics.contains("gpu_callback_streaming_reused_held_engine=true"))
    }

    @Test
    fun `runtime alignment holder cleanup distinguishes success cleanup`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery",
            selectedModelFile = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = false,
                heldEngineReused = false,
                engineCreateFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                fallbackUsed = false,
                holderInvalidated = true,
                holderClosed = true,
                holderFailureCleanup = false,
                heldEngineDestroyReason = "success_cleanup",
                gpuAlignmentHolderPresentBeforeAcquire = true,
                gpuAlignmentHolderAcquireResult = "reused",
                gpuAlignmentHolderReused = false,
                gpuAlignmentHolderCleared = true,
                gpuAlignmentHolderClearReason = "success_cleanup",
            ),
            elapsedMs = 1_001L,
        )

        assertTrue(routeDiagnostics.contains("gpu_alignment_holder_cleared=true"))
        assertTrue(routeDiagnostics.contains("gpu_alignment_holder_clear_reason=success_cleanup"))
        assertTrue(routeDiagnostics.contains("gpu_alignment_holder_reuse_block_reason=holder_cleared_after_success"))
    }

    @Test
    fun `GPU prefill probe timeout diagnostics are included in route and compact text`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val probeState = GpuPrefillProbeState(
            request = GpuPrefillProbeRequest(
                modelPath = "/models/gemma-4-E2B-it.litertlm",
                cacheDirPath = "/cache",
                prompt = "hi",
                maxTokens = 1,
                samplerEnabled = false,
                cacheDirMode = "null",
            ),
            startedAtMs = 0L,
            elapsedOverrideMs = 15_000L,
        )
        probeState.runStarted.set(true)
        probeState.runTimedOut.set(true)
        probeState.engineConfigStarted.set(true)
        probeState.engineConfigFinished.set(true)
        probeState.engineInitializeStarted.set(true)
        probeState.engineInitializeFinished.set(true)
        probeState.conversationCreateStarted.set(true)
        probeState.conversationCreateFinished.set(true)
        probeState.generateStarted.set(true)
        probeState.generateStartedAtMs.set(100L)
        probeState.firstTokenReceived.set(false)
        probeState.staleCallbackIgnored.set(true)
        probeState.cleanupStarted.set(true)
        probeState.cleanupResult.set("cancel_requested_native_generate_may_still_be_processing")
        probeState.exceptionExpansion.set(LocalFailureExceptionExpansion())
        val probeText = buildGpuPrefillProbeDiagnosticsText(probeState)
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "gpu_prefill_probe_completed",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                failureStage = "timeout",
                fallbackUsed = false,
                gpuPrefillProbeDiagnostics = extractGpuPrefillProbeDiagnostics(probeText),
            ),
            elapsedMs = 15_000L,
        )
        val combined = "$routeDiagnostics\n$probeText"
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "gpu-prefill-probe-skipped-normal-generate",
                    localFailureDiagnosticsText = combined,
                ),
                reason = "local_inference_failure",
                routeContext = routeContext,
                timeout = true,
            ),
        )

        assertTrue(routeDiagnostics.startsWith("LOCAL_ROUTE_DIAG "))
        assertTrue(routeDiagnostics.contains("probe_enabled=true"))
        assertTrue(routeDiagnostics.contains("probe_run_timed_out=true"))
        assertTrue(routeDiagnostics.contains("probe_timeout_stage=generate_before_first_token"))
        assertTrue(routeDiagnostics.contains("probe_skipped_normal_generate=true"))
        assertTrue(compact.contains("probe_requested=true"))
        assertTrue(compact.contains("probe_enabled=true"))
        assertTrue(compact.contains("probe_run_started=true"))
        assertTrue(compact.contains("probe_run_timed_out=true"))
        assertTrue(compact.contains("probe_skipped_normal_generate=true"))
        assertTrue(compact.contains("probe_prompt_variant=single_ascii"))
        assertTrue(compact.contains("probe_prompt_length_chars=2"))
        assertTrue(compact.contains("probe_max_tokens=1"))
        assertTrue(compact.contains("probe_sampler_enabled=false"))
        assertTrue(compact.contains("probe_cache_dir_mode=null"))
        assertTrue(compact.contains("probe_engine_config_started=true"))
        assertTrue(compact.contains("probe_engine_initialize_finished=true"))
        assertTrue(compact.contains("probe_conversation_create_finished=true"))
        assertTrue(compact.contains("probe_generate_started=true"))
        assertTrue(compact.contains("probe_first_token_received=false"))
        assertTrue(compact.contains("probe_timeout_stage=generate_before_first_token"))
        assertTrue(compact.contains("probe_failure_stage=gpu_prefill_probe_timeout_generate_before_first_token"))
        assertTrue(compact.contains("probe_exception_cause_class=unavailable"))
        assertTrue(compact.contains("probe_exception_cause_message_raw=unavailable"))
        assertTrue(compact.contains("probe_exception_cause_message_sanitized=unavailable"))
        assertTrue(compact.contains("probe_exception_root_cause_class=unavailable"))
        assertTrue(compact.contains("probe_exception_chain=unavailable"))
        assertTrue(compact.contains("probe_reflection_target_exception_class=unavailable"))
        assertTrue(compact.contains("probe_generate_before_first_token_elapsed_ms=14900"))
        assertTrue(compact.contains("probe_cleanup_started=true"))
        assertTrue(compact.contains("probe_cleanup_result=cancel_requested_native_generate_may_still_be_processing"))
        assertTrue(compact.contains("probe_invalidated_held_engine=true"))
        assertTrue(compact.contains("probe_normal_generate_blocked_reason=probe_opt_in_runs_without_normal_generate"))
        assertTrue(compact.contains("previous_invocation_still_processing_detected=false"))
        assertTrue(compact.contains("lite_rt_lm_previous_invocation_still_processing=false"))
    }

    @Test
    fun `compiled model initialize failure keys are included in route and compact text`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val probeDiagnostics = mapOf(
            "probe_exception_cause_message_raw" to
                "Failed_to_create_engine:_INTERNAL:_ERROR:_[runtime/executor/llm_litert_compiled_model_executor.cc:1546] " +
                    "ERROR:[external/litert/litert/cc/litert_compiled_model.h:1140]",
            "probe_failure_stage" to "gpu_prefill_probe_engine_initialize_invocation_target_exception",
            "probe_timeout_stage" to "engine_initialize",
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "gpu_prefill_probe_completed",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                failureStage = "gpu_prefill_probe_engine_initialize_invocation_target_exception",
                engineInitializeStarted = true,
                engineInitializeFinished = false,
                conversationCreateStarted = false,
                conversationCreateFinished = false,
                gpuPrefillProbeDiagnostics = probeDiagnostics,
            ),
            elapsedMs = 3_008L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "hi",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    requestedPreferredBackend = "GPU",
                    appliedPreferredBackend = "GPU",
                    preferredBackendApplyResult = "gpu-prefill-probe-skipped-normal-generate",
                    localFailureDiagnosticsText = routeDiagnostics,
                ),
                routeContext = routeContext,
            ),
        )

        assertTrue(routeDiagnostics.contains("gpu_litert_executor_error_file=runtime/executor/llm_litert_compiled_model_executor.cc"))
        assertTrue(routeDiagnostics.contains("gpu_litert_executor_error_line=1546"))
        assertTrue(routeDiagnostics.contains("gpu_litert_compiled_model_error_file=external/litert/litert/cc/litert_compiled_model.h"))
        assertTrue(routeDiagnostics.contains("gpu_litert_compiled_model_error_line=1140"))
        assertTrue(routeDiagnostics.contains("gpu_engine_initialize_internal_error_detected=true"))
        assertTrue(routeDiagnostics.contains("gpu_compiled_model_creation_failed=true"))
        assertTrue(routeDiagnostics.contains("gpu_failure_interpretation=compiled_model_creation_failed_before_conversation"))
        assertTrue(compact.contains("gpu_litert_executor_error_line=1546"))
        assertTrue(compact.contains("gpu_litert_compiled_model_error_line=1140"))
        assertTrue(compact.contains("gpu_failure_interpretation=compiled_model_creation_failed_before_conversation"))
    }

    @Test
    fun `held engine probe blocked route reports divergence point`() {
        val routeContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val probeText = buildGpuPrefillProbeStartBlockedDiagnosticsText(
            reason = "no_held_engine",
            useHeldEngineRequested = true,
            heldEnginePresentBefore = false,
            heldEngineAcquireResult = "blocked_no_held_engine",
        )
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "gpu_held_engine_prefill_probe_completed",
            context = routeContext,
            flags = LocalRouteDiagnosticFlags(
                failureStage = "gpu_prefill_probe_start_blocked",
                gpuPrefillProbeDiagnostics = extractGpuPrefillProbeDiagnostics(probeText),
            ),
            elapsedMs = 10L,
        )

        assertTrue(routeDiagnostics.contains("probe_start_blocked_reason=no_held_engine"))
        assertTrue(routeDiagnostics.contains("gpu_route_divergence_point=held_engine_probe_blocked_no_held_engine"))
    }

    @Test
    fun `InvocationTargetException target and root cause are expanded`() {
        val root = IllegalArgumentException("backend enum mismatch")
        val target = IllegalStateException("engine create failed", root)
        val wrapper = InvocationTargetException(target)
        val text = buildFailureText(
            setting = PreferredBackendDryRunSetting.CPU,
            throwable = wrapper,
            exceptionClass = wrapper.javaClass.name,
            exceptionMessage = wrapper.message ?: "none",
        )

        assertTrue(text.contains("failure_exception_class=java.lang.reflect.InvocationTargetException"))
        assertTrue(text.contains("failure_exception_message=none"))
        assertTrue(text.contains("failure_cause_class=java.lang.IllegalStateException"))
        assertTrue(text.contains("failure_cause_message=engine create failed"))
        assertTrue(text.contains("failure_root_cause_class=java.lang.IllegalArgumentException"))
        assertTrue(text.contains("failure_root_cause_message=backend enum mismatch"))
        assertTrue(text.contains("reflection_target_exception_class=java.lang.IllegalStateException"))
        assertTrue(text.contains("reflection_target_exception_message=engine create failed"))
        assertTrue(text.contains("reflection_target_exception_root_cause_class=java.lang.IllegalArgumentException"))
        assertTrue(text.contains("reflection_target_exception_root_cause_message=backend enum mismatch"))
        assertTrue(text.contains("exception_chain=java.lang.reflect.InvocationTargetException:none -> java.lang.IllegalStateException:engine create failed -> java.lang.IllegalArgumentException:backend enum mismatch"))
    }

    @Test
    fun `null exception messages are rendered as none in chain`() {
        val root = IllegalStateException()
        val wrapper = InvocationTargetException(root)
        val text = buildFailureText(
            setting = PreferredBackendDryRunSetting.GPU,
            throwable = wrapper,
            exceptionClass = wrapper.javaClass.name,
            exceptionMessage = wrapper.message ?: "none",
        )

        assertTrue(text.contains("failure_exception_message=none"))
        assertTrue(text.contains("failure_cause_message=none"))
        assertTrue(text.contains("failure_root_cause_message=none"))
        assertTrue(text.contains("reflection_target_exception_message=none"))
        assertTrue(text.contains("exception_chain=java.lang.reflect.InvocationTargetException:none -> java.lang.IllegalStateException:none"))
    }

    @Test
    fun `probe disabled compact does not include probe diagnostics`() {
        val text = buildFailureText(
            setting = PreferredBackendDryRunSetting.GPU,
            throwable = IllegalStateException("create failed"),
            exceptionClass = IllegalStateException::class.java.name,
            exceptionMessage = "create failed",
        )

        assertFalse(text.contains("probe_enabled="))
        assertFalse(text.contains("probe_run_started="))
        assertTrue(text.contains("lite_rt_lm_previous_invocation_still_processing=false"))
    }

    @Test
    fun `failure compact includes gallery stack probe diagnostics when provided`() {
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            LocalInferenceFailureCompactInput(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                status = "failure",
                reason = "local_inference_failure",
                failureStage = "gpu_generate_compiled_model_invoke_failed",
                failureExceptionClass = "com.google.ai.edge.litertlm.LiteRtLmJniException",
                failureExceptionMessage = "Status Code: 13. Failed to invoke the compiled model",
                gpuGenerateExceptionSummary = "failed_to_invoke_compiled_model",
                gpuGenerateExceptionErrorFile = "runtime/executor/llm_litert_compiled_model_executor.cc",
                gpuGenerateExceptionErrorLine = "735",
                liteRtCompiledModelExecutorFailureCategory = "compiled_model_invoke",
                cpuGpuGenerateDiff = "cpu_callback_ok_gpu_compiled_model_invoke_failed",
                modelName = "gemma-4-E2B-it-edge-gallery.litertlm",
                modelFile = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
                galleryStackProbeDiagnostics = GalleryStackGpuProbeRuntimeDiagnostics(
                    flavor = true,
                    enabled = true,
                    applicationId = "io.github.ninbyo02.lami.gallerystackgpu",
                    nativeStackSource = "not_staged",
                    libLiteRtSha256 = "unavailable",
                    libLiteRtLmJniSha256 = "unavailable",
                    libsManifestPresent = "artifact_only_not_packaged",
                    edgeGalleryModelExpected = "modelId=litert-community/gemma-4-E2B-it-litert-lm;size=2588147712",
                    modelPath = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
                    modelExists = "true",
                    modelSizeBytes = "2588147712",
                    modelSha256IfAvailable = "script_only_not_computed_on_device",
                    allowlistConfigApplied = "true",
                    runtimeStackAlignmentLevel = "model_only",
                    thinkingApiAvailable = "false",
                    speculativeDecodingApiAvailable = "false",
                    allowlistAccelerators = "gpu,cpu",
                    allowlistVisionAccelerator = "gpu",
                    allowlistTopK = "64",
                    allowlistTopP = "0.95",
                    allowlistTemperature = "1.0",
                    allowlistMaxTokens = "4000",
                    allowlistMaxContextLength = "32000",
                ),
            ),
        )

        assertTrue(compact.contains("selected_backend=GPU"))
        assertTrue(compact.contains("route_family=local_gpu"))
        assertTrue(compact.contains("status=failure"))
        assertTrue(compact.contains("reason=local_inference_failure"))
        assertTrue(compact.contains("failure_stage=gpu_generate_compiled_model_invoke_failed"))
        assertTrue(compact.contains("gallery_stack_probe_enabled=true"))
        assertTrue(compact.contains("gallery_stack_probe_application_id=io.github.ninbyo02.lami.gallerystackgpu"))
        assertTrue(compact.contains("gallery_stack_probe_native_stack_source=not_staged"))
        assertTrue(compact.contains("gallery_stack_probe_liblitert_sha256=unavailable"))
        assertTrue(compact.contains("gallery_stack_probe_liblitertlm_jni_sha256=unavailable"))
        assertTrue(compact.contains("gallery_stack_probe_runtime_stack_alignment_level=model_only"))
        assertTrue(compact.contains("gallery_stack_probe_allowlist_config_applied=true"))
        assertTrue(compact.contains("litert_compiled_model_executor_failure_category=compiled_model_invoke"))
        assertTrue(compact.contains("gpu_generate_exception_error_file=runtime/executor/llm_litert_compiled_model_executor.cc"))
        assertTrue(compact.contains("gpu_generate_exception_error_line=735"))
        assertTrue(compact.contains("cpu_gpu_generate_diff=cpu_callback_ok_gpu_compiled_model_invoke_failed"))
    }

    @Test
    fun `failure compact includes runtime alignment probe diagnostics when provided`() {
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            LocalInferenceFailureCompactInput(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                status = "failure",
                reason = "local_inference_failure",
                failureStage = "gpu_generate_compiled_model_invoke_failed",
                failureExceptionClass = "com.google.ai.edge.litertlm.LiteRtLmJniException",
                failureExceptionMessage = "Status Code: 13. Failed to invoke the compiled model",
                gpuCallbackStreamingPathSelected = "true",
                gpuCallbackTextPromotedToUi = "false",
                gpuUiAppendFinished = "false",
                gpuStreamingCompletionReason = "unavailable",
                gpuGenerateExceptionErrorFile = "runtime/executor/llm_litert_compiled_model_executor.cc",
                gpuGenerateExceptionErrorLine = "735",
                liteRtLmErrorKind = "compiled_model_invoke_failed",
                gpuLiteRtExecutorErrorFile = "runtime/executor/llm_litert_compiled_model_executor.cc",
                gpuLiteRtExecutorErrorLine = "735",
                runtimeAlignmentProbeDiagnostics = RuntimeAlignmentProbeDiagnostics(
                    flavor = true,
                    stackSource = "dev-only GPU runtime alignment promotion candidate",
                    libLiteRtSha256 = "31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24",
                    libLiteRtLmJniSha256 = "ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f",
                    dispatchQualcommPresent = "false",
                    compilerPluginQualcommPresent = "false",
                    gemmaConstraintProviderPresent = "false",
                    resultCandidate = "failure",
                    successGate = "true",
                ),
            ),
        )

        assertTrue(compact.contains("runtime_alignment_probe_flavor=true"))
        assertTrue(compact.contains("runtime_alignment_stack_source=dev-only GPU runtime alignment promotion candidate"))
        assertTrue(
            compact.contains(
                "runtime_alignment_liblitert_sha256=31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24",
            ),
        )
        assertTrue(
            compact.contains(
                "runtime_alignment_liblitertlm_jni_sha256=ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f",
            ),
        )
        assertTrue(compact.contains("runtime_alignment_dispatch_qualcomm_present=false"))
        assertTrue(compact.contains("runtime_alignment_compiler_plugin_qualcomm_present=false"))
        assertTrue(compact.contains("runtime_alignment_gemma_constraint_provider_present=false"))
        assertTrue(compact.contains("runtime_alignment_result_candidate=failure"))
        assertTrue(compact.contains("runtime_alignment_success_gate=true"))
        assertTrue(compact.contains("gpu_callback_streaming_path_selected=true"))
        assertTrue(compact.contains("failure_stage=gpu_generate_compiled_model_invoke_failed"))
        assertTrue(compact.contains("litert_lm_error_kind=compiled_model_invoke_failed"))
        assertTrue(compact.contains("gpu_litert_executor_error_line=735"))
    }

    @Test
    fun `failure compact includes minimal runtime probe diagnostics when provided`() {
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            LocalInferenceFailureCompactInput(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                status = "success",
                reason = "gpu_callback_streaming_success",
                failureStage = "none",
                gpuCallbackStreamingPathSelected = "true",
                gpuCallbackTextPromotedToUi = "true",
                gpuUiAppendFinished = "true",
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuPrefillProbeDiagnostics = mapOf(
                    "minimal_runtime_probe_flavor" to "true",
                    "minimal_runtime_probe_liblitert_present" to "true",
                    "minimal_runtime_probe_liblitertlm_jni_present" to "true",
                    "minimal_runtime_probe_runtime_stack_source" to "core_pair_only",
                    "minimal_runtime_probe_result_candidate" to "success",
                    "minimal_runtime_probe_success_gate" to "true",
                    "minimal_runtime_probe_loaded_liblitert_sha256" to
                        "31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24",
                    "minimal_runtime_probe_loaded_liblitertlm_jni_sha256" to
                        "ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f",
                    "minimal_runtime_probe_dispatch_present" to "false",
                    "minimal_runtime_probe_compiler_plugin_present" to "false",
                    "minimal_runtime_probe_constraint_provider_present" to "false",
                ),
            ),
        )

        assertTrue(compact.contains("minimal_runtime_probe_flavor=true"))
        assertTrue(compact.contains("minimal_runtime_probe_liblitert_present=true"))
        assertTrue(compact.contains("minimal_runtime_probe_liblitertlm_jni_present=true"))
        assertTrue(compact.contains("minimal_runtime_probe_runtime_stack_source=core_pair_only"))
        assertTrue(compact.contains("minimal_runtime_probe_result_candidate=success"))
        assertTrue(compact.contains("minimal_runtime_probe_success_gate=true"))
        assertTrue(
            compact.contains(
                "minimal_runtime_probe_loaded_liblitert_sha256=31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24",
            ),
        )
        assertTrue(
            compact.contains(
                "minimal_runtime_probe_loaded_liblitertlm_jni_sha256=ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f",
            ),
        )
        assertTrue(compact.contains("minimal_runtime_probe_dispatch_present=false"))
        assertTrue(compact.contains("minimal_runtime_probe_compiler_plugin_present=false"))
        assertTrue(compact.contains("minimal_runtime_probe_constraint_provider_present=false"))
    }

    @Test
    fun `LiteRT-LM previous invocation still processing is classified`() {
        val target = IllegalStateException("Previous invocation still processing. Wait for done=true.")
        val wrapper = InvocationTargetException(target)
        val text = buildFailureText(
            setting = PreferredBackendDryRunSetting.GPU,
            throwable = wrapper,
            exceptionClass = wrapper.javaClass.name,
            exceptionMessage = wrapper.message ?: "none",
        )

        assertTrue(text.contains("failure_stage=engine-create"))
        assertTrue(text.contains("failure_cause_class=java.lang.IllegalStateException"))
        assertTrue(text.contains("failure_cause_message=Previous invocation still processing. Wait for done=true."))
        assertTrue(text.contains("lite_rt_lm_previous_invocation_still_processing=true"))
        assertTrue(text.contains("generate_concurrency_violation_suspected=true"))
        assertTrue(text.contains("guard_recommendation=reset_gpu_engine_or_force_cpu"))
    }

    @Test
    fun `local compact reflection keys do not change NPU S1 compact header`() {
        val localText = buildFailureText(
            setting = PreferredBackendDryRunSetting.DEFAULT,
            throwable = InvocationTargetException(IllegalStateException("local failure")),
            exceptionClass = InvocationTargetException::class.java.name,
            exceptionMessage = "none",
        )
        val npuText = buildNpuStandardRouteS1CompactDiagnosticCopyText(
            input = "こんにちは",
            result = NpuStandardRouteS1Result(
                status = "success",
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                inputPrompt = "こんにちは",
            ),
        )

        assertTrue(localText.contains("[DEV診断: Local inference failure compact]"))
        assertFalse(localText.contains("[DEV診断: NPU S1 compact]"))
        assertTrue(npuText.contains("[DEV診断: NPU S1 compact]"))
        assertFalse(npuText.contains("[DEV診断: Local inference failure compact]"))
        assertFalse(npuText.contains("reflection_target_exception_class="))
    }

    @Test
    fun `GPU callback streaming success compact includes output quality diagnostics`() {
        val context = buildGpuRouteContextForNewDiagnostics()
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = context,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateFinished = true,
                conversationCreateStarted = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                gpuNormalRouteUseCallbackStreaming = true,
                gpuCallbackStreamingPathSelected = true,
                gpuCallbackTextPromotedToUi = true,
                gpuUiAppendFinished = true,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuOutputRawCallbackTextLength = 18,
                gpuOutputRawCallbackTextHead = "こんにちは。材料です。",
                gpuOutputRawCallbackTextTail = "こんにちは。材料です。",
                gpuOutputPromotedTextLength = 18,
                gpuOutputPromotedTextHead = "こんにちは。材料です。",
                gpuOutputPromotedTextTail = "こんにちは。材料です。",
                gpuOutputFinalAssistantTextLength = 18,
                gpuOutputFinalAssistantTextHead = "こんにちは。材料です。",
                gpuOutputFinalAssistantTextTail = "こんにちは。材料です。",
                gpuOutputCallbackChunkCount = 3,
                gpuOutputEmptyChunkCount = 0,
                gpuOutputNonEmptyChunkCount = 3,
                gpuOutputChunkJoinStrategy = "raw_callback_append:dev_gate_normal_route",
                gpuOutputChunkBoundarySuspected = false,
                gpuOutputLastChunksSummary = "6:こん|6:材料|6:です",
                gpuOutputChunkLengthHistogram = "0=0;1_2=0;3_8=3;9_32=0;33_plus=0",
                gpuOutputQualityMatrixMode = "baseline",
                gpuOutputQualitySamplerMode = "edge_gallery_like",
                gpuOutputQualityStreamingMode = "incremental_callback_streaming",
                gpuOutputQualityEffectiveMaxTokens = "4000",
                gpuOutputQualityCollectOnlyEnabled = false,
                gpuOutputQualityUiIncrementalAppendEnabled = true,
                gpuOutputQualityCandidateResult = "quality_candidate_pass",
                gpuOutputQualityFailureBlockReason = "none",
                gpuOutputQualityRecommendation = "none",
                gpuOutputActualUiAppendedTextLength = 18,
                gpuOutputActualUiAppendedTextHead = "こんにちは。材料です。",
                gpuOutputActualUiAppendedTextTail = "こんにちは。材料です。",
                gpuOutputUiAppendChangedText = false,
                gpuOutputSourceCorruptionStage = "none",
                gpuCallbackAverageChunkLength = "6.00",
                gpuCallbackMedianChunkLength = "6",
                gpuCallbackP50ChunkLength = "6",
                gpuCallbackP90ChunkLength = "6",
                gpuCallbackP95ChunkLength = "6",
                gpuCallbackOneCharChunkCount = 0,
                gpuCallbackTwoCharOrLessChunkCount = 0,
                gpuCallbackOneCharChunkRatio = "0.000",
                gpuCallbackTwoCharOrLessChunkRatio = "0.000",
                gpuCallbackLongestChunkLength = 6,
                gpuCallbackShortestNonEmptyChunkLength = 6,
                gpuCallbackFirstChunksArtifact = "chunk_001 len=6 text=\"こん\"|chunk_002 len=6 text=\"材料\"",
                gpuCallbackLastChunksArtifact = "chunk_002 len=6 text=\"材料\"|chunk_003 len=6 text=\"です\"",
                callbackQualityClassification = "healthy_large_chunks",
                callbackCorruptionEarliestStage = "none",
            ),
            elapsedMs = 2_000L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(localFailureDiagnosticsText = routeDiagnostics),
                status = "success",
                reason = "gpu_callback_streaming_success",
                routeContext = context,
            ),
        )

        assertTrue(compact.contains("gpu_output_raw_callback_text_length=18"))
        assertTrue(compact.contains("gpu_output_promoted_text_head=こんにちは。材料です。"))
        assertTrue(compact.contains("gpu_output_final_assistant_text_tail=こんにちは。材料です。"))
        assertTrue(compact.contains("gpu_output_callback_chunk_count=3"))
        assertTrue(compact.contains("gpu_output_suspicious_fragment_detected=false"))
        assertTrue(compact.contains("gpu_output_suspicious_fragment_reason=none"))
        assertTrue(compact.contains("gpu_output_suspicious_fragment_position=none"))
        assertTrue(compact.contains("gpu_output_chunk_join_strategy=raw_callback_append:dev_gate_normal_route"))
        assertTrue(compact.contains("gpu_output_chunk_boundary_suspected=false"))
        assertTrue(compact.contains("gpu_output_last_chunks_summary=6:こん|6:材料|6:です"))
        assertTrue(compact.contains("gpu_output_quality_matrix_mode=baseline"))
        assertTrue(compact.contains("gpu_output_quality_sampler_mode=edge_gallery_like"))
        assertTrue(compact.contains("gpu_output_quality_streaming_mode=incremental_callback_streaming"))
        assertTrue(compact.contains("gpu_output_quality_effective_max_tokens=4000"))
        assertTrue(compact.contains("gpu_output_quality_collect_only_enabled=false"))
        assertTrue(compact.contains("gpu_output_quality_ui_incremental_append_enabled=true"))
        assertTrue(compact.contains("gpu_output_chunk_length_histogram=0=0;1_2=0;3_8=3;9_32=0;33_plus=0"))
        assertTrue(compact.contains("gpu_output_quality_candidate_result=quality_candidate_pass"))
        assertTrue(compact.contains("gpu_output_quality_recommendation=none"))
        assertTrue(compact.contains("gpu_output_actual_ui_appended_text_tail=こんにちは。材料です。"))
        assertTrue(compact.contains("average_chunk_length=6.00"))
        assertTrue(compact.contains("median_chunk_length=6"))
        assertTrue(compact.contains("p90_chunk_length=6"))
        assertTrue(compact.contains("one_char_chunk_ratio=0.000"))
        assertTrue(compact.contains("two_char_or_less_chunk_ratio=0.000"))
        assertTrue(compact.contains("callback_first_30_chunks=chunk_001"))
        assertTrue(compact.contains("callback_last_30_chunks=chunk_002"))
        assertTrue(compact.contains("callback_quality_classification=healthy_large_chunks"))
        assertTrue(compact.contains("callback_corruption_earliest_stage=none"))
    }

    @Test
    fun `GPU output suspicious fragment classifier detects synthetic corruption`() {
        val reason = classifyGpuOutputSuspiciousFragmentReason(
            rawSample = "材料は普通です。",
            promotedSample = "材料は普通です。",
            finalSample = "材料:** ml2 g）に）：：",
            rawLength = 8,
            finalLength = 18,
            nonEmptyChunkCount = 4,
        )

        assertEquals("final_text_only_suspicious_after_ui_or_markdown", reason)
    }

    @Test
    fun `GPU output suspicious fragment classifier detects tail tiny chunks`() {
        val reason = classifyGpuOutputSuspiciousFragmentReason(
            rawSample = "材料は普通です。 ：など易簡単いい調スパ",
            promotedSample = "材料は普通です。 ：など易簡単いい調スパ",
            finalSample = "材料は普通です。 ：など易簡単いい調スパ",
            rawLength = 45,
            finalLength = 45,
            nonEmptyChunkCount = 22,
        )

        assertEquals("tail_tiny_chunk_run", reason)
    }

    @Test
    fun `GPU output suspicious tail fragment diagnostics expose position and chunk boundary`() {
        val context = buildGpuRouteContextForNewDiagnostics()
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = context,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                gpuNormalRouteUseCallbackStreaming = true,
                gpuCallbackStreamingPathSelected = true,
                gpuCallbackTextPromotedToUi = true,
                gpuUiAppendFinished = true,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuOutputRawCallbackTextLength = 44,
                gpuOutputRawCallbackTextHead = "材料は普通です。",
                gpuOutputRawCallbackTextTail = "：など易簡単いい調スパ ml2 g）に）：：",
                gpuOutputPromotedTextLength = 44,
                gpuOutputPromotedTextHead = "材料は普通です。",
                gpuOutputPromotedTextTail = "：など易簡単いい調スパ ml2 g）に）：：",
                gpuOutputFinalAssistantTextLength = 44,
                gpuOutputFinalAssistantTextHead = "材料は普通です。",
                gpuOutputFinalAssistantTextTail = "：など易簡単いい調スパ ml2 g）に）：：",
                gpuOutputCallbackChunkCount = 30,
                gpuOutputEmptyChunkCount = 0,
                gpuOutputNonEmptyChunkCount = 30,
                gpuOutputChunkJoinStrategy = "raw_callback_append:dev_gate_normal_route",
                gpuOutputLastChunksSummary = "1:：|1:な|1:ど|1:易|1:簡|1:g|1:）",
                gpuOutputChunkLengthHistogram = "0=2;1_2=25;3_8=5;9_32=0;33_plus=0",
                gpuOutputQualityCandidateResult = "quality_candidate_fail",
                gpuOutputQualityFailureBlockReason = "chunk_boundary_or_sampler_fragmentation",
                gpuOutputQualityRecommendation = "run_collect_only_and_sampler_matrix",
                gpuCallbackAverageChunkLength = "1.50",
                gpuCallbackMedianChunkLength = "1",
                gpuCallbackP50ChunkLength = "1",
                gpuCallbackP90ChunkLength = "2",
                gpuCallbackP95ChunkLength = "2",
                gpuCallbackOneCharChunkCount = 20,
                gpuCallbackTwoCharOrLessChunkCount = 25,
                gpuCallbackOneCharChunkRatio = "0.667",
                gpuCallbackTwoCharOrLessChunkRatio = "0.833",
                gpuCallbackLongestChunkLength = 2,
                gpuCallbackShortestNonEmptyChunkLength = 1,
                gpuCallbackFirstChunksArtifact = "chunk_001 len=1 text=\"：\"|chunk_002 len=1 text=\"な\"",
                gpuCallbackLastChunksArtifact = "chunk_029 len=1 text=\"g\"|chunk_030 len=1 text=\"）\"",
                gpuPrefillProbeDiagnostics = mapOf(
                    "gpu_fragmentation_score" to "0.900",
                    "gpu_fragmentation_percentile" to "p50=1;p90=2;p95=2",
                    "gpu_fragmentation_head_score" to "0.600",
                    "gpu_fragmentation_middle_score" to "0.800",
                    "gpu_fragmentation_tail_score" to "1.000",
                    "gpu_chunk_size_distribution" to "0=2;1_2=25;3_8=5;9_32=0;33_plus=0",
                    "gpu_chunk_length_sequence" to "1,1,2,1,3,1,1",
                    "gpu_fragmentation_cluster_count" to "4",
                    "gpu_fragmentation_cluster_max_length" to "7",
                    "gpu_fragmentation_cluster_avg_length" to "4.25",
                    "gpu_sampler_root_cause_candidate" to "runtime_decode_fragmentation",
                ),
                callbackQualityClassification = "severe_fragmentation",
                callbackCorruptionEarliestStage = "raw_callback",
            ),
            elapsedMs = 2_000L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "カレーの材料をお願いします。",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(localFailureDiagnosticsText = routeDiagnostics),
                status = "success",
                reason = "gpu_callback_streaming_success",
                routeContext = context,
            ),
        )

        assertTrue(compact.contains("gpu_output_suspicious_fragment_detected=true"))
        assertTrue(compact.contains("gpu_output_suspicious_fragment_position=tail"))
        assertTrue(compact.contains("gpu_output_mixed_japanese_fragment_detected=true"))
        assertTrue(compact.contains("gpu_output_mixed_language_fragment_detected="))
        assertTrue(compact.contains("gpu_output_chunk_boundary_suspected=true"))
        assertTrue(compact.contains("gpu_output_last_chunks_summary=1:：|1:な|1:ど|1:易|1:簡|1:g|1:）"))
        assertTrue(compact.contains("gpu_output_chunk_length_histogram=0=2;1_2=25;3_8=5;9_32=0;33_plus=0"))
        assertTrue(compact.contains("gpu_output_quality_candidate_result=quality_candidate_fail"))
        assertTrue(compact.contains("gpu_output_quality_failure_block_reason=chunk_boundary_or_sampler_fragmentation"))
        assertTrue(compact.contains("average_chunk_length=1.50"))
        assertTrue(compact.contains("two_char_or_less_chunk_ratio=0.833"))
        assertTrue(compact.contains("callback_quality_classification=severe_fragmentation"))
        assertTrue(compact.contains("callback_corruption_earliest_stage=raw_callback"))
        assertTrue(compact.contains("gpu_fragmentation_score=0.900"))
        assertTrue(compact.contains("gpu_fragmentation_percentile=p50=1;p90=2;p95=2"))
        assertTrue(compact.contains("gpu_fragmentation_tail_score=1.000"))
        assertTrue(compact.contains("gpu_chunk_size_distribution=0=2;1_2=25;3_8=5;9_32=0;33_plus=0"))
        assertTrue(compact.contains("gpu_chunk_length_sequence=1,1,2,1,3,1,1"))
        assertTrue(compact.contains("gpu_fragmentation_cluster_count=4"))
        assertTrue(compact.contains("gpu_fragmentation_cluster_max_length=7"))
        assertTrue(compact.contains("gpu_fragmentation_cluster_avg_length=4.25"))
        assertTrue(compact.contains("gpu_sampler_root_cause_candidate=runtime_decode_fragmentation"))
    }

    @Test
    fun `GPU callback quality comparison diagnostics identify smaller GPU chunks`() {
        val context = buildGpuRouteContextForNewDiagnostics()
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = context,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                gpuNormalRouteUseCallbackStreaming = true,
                gpuCallbackStreamingPathSelected = true,
                gpuCallbackTextPromotedToUi = true,
                gpuUiAppendFinished = true,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuOutputFinalAssistantTextLength = 60,
                gpuOutputCallbackChunkCount = 40,
                gpuOutputNonEmptyChunkCount = 40,
                gpuOutputSuspiciousFragmentDetected = true,
                gpuOutputQualityCandidateResult = "quality_candidate_fail",
                gpuCallbackAverageChunkLength = "1.75",
                gpuCallbackTwoCharOrLessChunkRatio = "0.900",
                cpuCompareRequested = true,
                cpuCompareEnabled = true,
                cpuCompareStarted = true,
                cpuCompareFinished = true,
                cpuCompareSkippedReason = "none",
                cpuCompareFailureStage = "none",
                cpuCompareEngineInitializeFinished = true,
                cpuCompareConversationCreateFinished = true,
                cpuCompareGenerateStarted = true,
                cpuCompareCallbackInvokedCount = 8,
                cpuCompareEmptyTextCount = 1,
                cpuCompareNonEmptyTextCount = 8,
                cpuCallbackAverageChunkLength = "12.50",
                cpuCallbackMedianChunkLength = "12",
                cpuCallbackP90ChunkLength = "16",
                cpuCallbackP95ChunkLength = "18",
                cpuCallbackOneCharChunkCount = 0,
                cpuCallbackTwoCharOrLessChunkCount = 0,
                cpuCallbackOneCharChunkRatio = "0.000",
                cpuCallbackTwoCharOrLessRatio = "0.000",
                cpuCallbackChunkLengthHistogram = "0=1;1_2=0;3_8=0;9_32=8;33_plus=0",
                cpuCallbackFirstChunksArtifact = "chunk_001 len=12 text=\"材料を\"",
                cpuCallbackLastChunksArtifact = "chunk_008 len=12 text=\"完成\"",
                cpuCallbackQualityClassification = "healthy_large_chunks",
                cpuOutputSuspiciousFragmentDetected = false,
                cpuOutputSuspiciousFragmentReason = "none",
                cpuOutputSourceCorruptionStage = "none",
                cpuGpuSamePrompt = true,
                cpuGpuSameMaxTokens = true,
                cpuGpuSameSamplerConfigHint = "edge_gallery_like",
            ),
            elapsedMs = 2_000L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "カレーの材料をお願いします。",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(localFailureDiagnosticsText = routeDiagnostics),
                status = "success",
                reason = "gpu_callback_streaming_success",
                routeContext = context,
            ),
        )

        assertTrue(compact.contains("cpu_compare_requested=true"))
        assertTrue(compact.contains("cpu_compare_enabled=true"))
        assertTrue(compact.contains("cpu_compare_finished=true"))
        assertTrue(compact.contains("cpu_compare_skipped_reason=none"))
        assertTrue(compact.contains("cpu_callback_invoked_count=8"))
        assertTrue(compact.contains("cpu_callback_non_empty_text_count=8"))
        assertTrue(compact.contains("cpu_avg_chunk_length=12.50"))
        assertTrue(compact.contains("cpu_median_chunk_length=12"))
        assertTrue(compact.contains("cpu_p90_chunk_length=16"))
        assertTrue(compact.contains("cpu_one_char_chunk_ratio=0.000"))
        assertTrue(compact.contains("gpu_avg_chunk_length=1.75"))
        assertTrue(compact.contains("cpu_callback_count=8"))
        assertTrue(compact.contains("gpu_callback_count=40"))
        assertTrue(compact.contains("cpu_two_char_or_less_ratio=0.000"))
        assertTrue(compact.contains("cpu_chunk_length_histogram=0=1;1_2=0;3_8=0;9_32=8;33_plus=0"))
        assertTrue(compact.contains("cpu_callback_quality_classification=healthy_large_chunks"))
        assertTrue(compact.contains("cpu_output_suspicious_fragment_detected=false"))
        assertTrue(compact.contains("gpu_two_char_or_less_ratio=0.900"))
        assertTrue(compact.contains("callback_quality_compare_result=gpu_only_corrupt"))
        assertTrue(compact.contains("callback_quality_compare_reason=gpu_chunks_much_smaller_than_cpu"))
        assertTrue(compact.contains("cpu_gpu_avg_chunk_length_ratio=0.140"))
        assertTrue(compact.contains("cpu_gpu_two_char_or_less_ratio_delta=0.900"))
        assertTrue(compact.contains("cpu_gpu_callback_count_delta=32"))
        assertTrue(compact.contains("cpu_gpu_same_prompt=true"))
        assertTrue(compact.contains("cpu_gpu_same_max_tokens=true"))
    }

    @Test
    fun `GPU output collect only diagnostics show final commit mode`() {
        val context = buildGpuRouteContextForNewDiagnostics()
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = context,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                engineCreateFinished = true,
                conversationCreateFinished = true,
                generateStarted = true,
                firstTokenReceived = true,
                failureStage = "none",
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                gpuNormalRouteUseCallbackStreaming = true,
                gpuCallbackStreamingPathSelected = true,
                gpuCallbackTextPromotedToUi = true,
                gpuUiAppendFinished = true,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuOutputQualityMatrixMode = "collect_only",
                gpuOutputQualityStreamingMode = "collect_only_final_commit",
                gpuOutputQualityCollectOnlyEnabled = true,
                gpuOutputQualityUiIncrementalAppendEnabled = false,
                gpuOutputRawCallbackTextLength = 20,
                gpuOutputRawCallbackTextHead = "材料をまとめます。",
                gpuOutputRawCallbackTextTail = "材料をまとめます。",
                gpuOutputPromotedTextLength = 20,
                gpuOutputPromotedTextHead = "材料をまとめます。",
                gpuOutputPromotedTextTail = "材料をまとめます。",
                gpuOutputFinalAssistantTextLength = 20,
                gpuOutputFinalAssistantTextHead = "材料をまとめます。",
                gpuOutputFinalAssistantTextTail = "材料をまとめます。",
                gpuOutputCallbackChunkCount = 4,
                gpuOutputEmptyChunkCount = 0,
                gpuOutputNonEmptyChunkCount = 4,
                gpuOutputChunkJoinStrategy = "raw_callback_collect_only_final_commit:dev_gate_normal_route",
                gpuOutputActualUiAppendedTextLength = 20,
                gpuOutputActualUiAppendedTextHead = "材料をまとめます。",
                gpuOutputActualUiAppendedTextTail = "材料をまとめます。",
                gpuOutputUiAppendChangedText = false,
            ),
            elapsedMs = 2_000L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "カレーの材料をお願いします。",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(localFailureDiagnosticsText = routeDiagnostics),
                status = "success",
                reason = "gpu_callback_streaming_success",
                routeContext = context,
            ),
        )

        assertTrue(compact.contains("gpu_output_quality_matrix_mode=collect_only"))
        assertTrue(compact.contains("gpu_output_quality_streaming_mode=collect_only_final_commit"))
        assertTrue(compact.contains("gpu_output_quality_collect_only_enabled=true"))
        assertTrue(compact.contains("gpu_output_quality_ui_incremental_append_enabled=false"))
        assertTrue(compact.contains("gpu_output_chunk_join_strategy=raw_callback_collect_only_final_commit:dev_gate_normal_route"))
        assertTrue(compact.contains("gpu_output_ui_append_changed_text=false"))
    }

    @Test
    fun `GPU perf slow path classifier separates first token and tokenizer delays`() {
        assertEquals(
            "slow_first_token",
            classifyGpuPerfSlowPathReason(
                engineCreateOrReuse = "reuse",
                engineAcquireElapsedMs = 100,
                generateToFirstTokenMs = 2_500,
                callbackTotalElapsedMs = 500,
                visibleTokensPerSecond = "35.0",
                tokenizerCountDurationMs = 0,
            ),
        )
        assertEquals(
            "slow_tokenizer_count",
            classifyGpuPerfSlowPathReason(
                engineCreateOrReuse = "reuse",
                engineAcquireElapsedMs = 100,
                generateToFirstTokenMs = 300,
                callbackTotalElapsedMs = 500,
                visibleTokensPerSecond = "35.0",
                tokenizerCountDurationMs = 1_500,
            ),
        )
    }

    @Test
    fun `GPU perf and holder lifecycle diagnostics are copied into compact`() {
        val context = buildGpuRouteContextForNewDiagnostics()
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = context,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = true,
                heldEngineReused = true,
                failureStage = "none",
                gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                gpuNormalRouteUseCallbackStreaming = true,
                gpuCallbackStreamingPathSelected = true,
                gpuCallbackTextPromotedToUi = true,
                gpuUiAppendFinished = true,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuPerfEngineAcquireElapsedMs = 42,
                gpuPerfEngineCreateOrReuse = "reuse",
                gpuPerfConversationCreateElapsedMs = 12,
                gpuPerfGenerateToFirstTokenMs = 350,
                gpuPerfFirstToLastCallbackMs = 900,
                gpuPerfCallbackTotalElapsedMs = 980,
                gpuPerfLamiVisibleTokensPerSecond = "24.5",
                gpuPerfTokenizerCountDurationMs = 75,
                gpuHolderLifecycleEventAfterSuccess = "clear_after_success",
                gpuHolderLifecycleLastActivityState = "background",
                gpuHolderLifecycleLastAppVisibility = "background",
                gpuHolderLifecycleClearTriggerElapsedMs = 10_000,
                gpuHolderLifecycleClearAfterSuccessMs = 120,
                gpuHolderLifecycleClearDuringActiveGenerate = false,
                gpuHolderLifecycleClearAfterUiAppend = true,
                gpuHolderLifecycleClearReasonDetail = "app-backgrounded",
                gpuHolderLifecycleBackgroundDetectionSource = "HeldEngineLifecycleBridge.onStop",
                gpuHolderLifecycleOnStopDeferred = true,
                gpuHolderLifecycleOnStopDeferReason = "transient_onstop_after_success_ui_append",
                gpuHolderLifecycleClearSuppressedAfterSuccess = true,
                gpuHolderLifecycleClearSuppressedReason = "transient_onstop_after_success_ui_append",
                gpuHolderLifecycleActualBackgroundConfirmed = false,
                gpuHolderLifecycleReuseExpectedNextTurn = true,
            ),
            elapsedMs = 2_000L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(localFailureDiagnosticsText = routeDiagnostics),
                status = "success",
                reason = "gpu_callback_streaming_success",
                routeContext = context,
            ),
        )

        assertTrue(compact.contains("gpu_perf_engine_acquire_elapsed_ms=42"))
        assertTrue(compact.contains("gpu_perf_engine_create_or_reuse=reuse"))
        assertTrue(compact.contains("gpu_perf_slow_path_detected=false"))
        assertTrue(compact.contains("gpu_holder_lifecycle_clear_reason_detail=app-backgrounded"))
        assertTrue(compact.contains("gpu_holder_lifecycle_clear_after_success_ms=120"))
        assertTrue(compact.contains("gpu_holder_lifecycle_background_detection_source=HeldEngineLifecycleBridge.onStop"))
        assertTrue(compact.contains("gpu_holder_lifecycle_onstop_deferred=true"))
        assertTrue(compact.contains("gpu_holder_lifecycle_clear_suppressed_after_success=true"))
        assertTrue(compact.contains("gpu_holder_lifecycle_actual_background_confirmed=false"))
        assertTrue(compact.contains("gpu_holder_lifecycle_reuse_expected_next_turn=true"))
    }

    @Test
    fun `GPU prefill probe disabled does not report normal generate blocking`() {
        val trace = buildLocalRouteDiagnosticTrace(
            stage = "generate_started",
            context = buildGpuRouteContextForNewDiagnostics(),
            flags = LocalRouteDiagnosticFlags(
                gpuPrefillProbeEnabled = false,
                gpuPrefillProbeRequested = false,
                gpuPrefillProbeBlocksNormalGenerate = false,
            ),
        )

        assertTrue(trace.contains("gpu_prefill_probe_enabled=false"))
        assertTrue(trace.contains("gpu_prefill_probe_requested=false"))
        assertTrue(trace.contains("gpu_prefill_probe_blocks_normal_generate=false"))
        assertTrue(trace.contains("gpu_prefill_probe_block_reason=none"))
    }

    @Test
    fun `GPU prefill probe enabled without held engine exposes clear block reason`() {
        val probeText = buildGpuPrefillProbeStartBlockedDiagnosticsText(
            reason = "no_held_engine",
            useHeldEngineRequested = true,
            heldEnginePresentBefore = false,
            heldEngineAcquireResult = "blocked_no_held_engine",
        )
        val trace = buildLocalRouteDiagnosticTrace(
            stage = "gpu_prefill_probe_start_blocked",
            context = buildGpuRouteContextForNewDiagnostics(),
            flags = LocalRouteDiagnosticFlags(
                failureStage = "gpu_prefill_probe_start_blocked",
                gpuPrefillProbeDiagnostics = extractGpuPrefillProbeDiagnostics(probeText),
            ),
        )

        assertTrue(trace.contains("gpu_prefill_probe_enabled=true"))
        assertTrue(trace.contains("gpu_prefill_probe_requested=true"))
        assertTrue(trace.contains("gpu_prefill_probe_blocks_normal_generate=true"))
        assertTrue(trace.contains("gpu_prefill_probe_block_reason=no_held_engine"))
        assertTrue(trace.contains("gpu_prefill_probe_requires_held_engine=true"))
        assertTrue(trace.contains("gpu_prefill_probe_held_engine_present=false"))
    }

    @Test
    fun `CPU compact does not include GPU quality and minimal runtime candidate diagnostics without route trace`() {
        val text = buildFailureText(PreferredBackendDryRunSetting.CPU)

        assertFalse(text.contains("standard_gpu_minimal_runtime_candidate_flavor="))
        assertFalse(text.contains("gpu_output_raw_callback_text_length="))
        assertFalse(text.contains("gpu_perf_engine_acquire_elapsed_ms="))
    }

    private fun buildFailureText(
        setting: PreferredBackendDryRunSetting,
        throwable: Throwable? = null,
        exceptionClass: String? = null,
        exceptionMessage: String? = null,
    ): String =
        buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "こんにちは",
                preferredBackendSetting = setting,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(
                    localModelDisplayName = "gemma-local",
                    mediaPipeProbeModelPath = "/tmp/gemma-local.litertlm",
                    requestedPreferredBackend = setting.name,
                    appliedPreferredBackend = when (setting) {
                        PreferredBackendDryRunSetting.DEFAULT -> null
                        else -> setting.name
                    },
                    preferredBackendApplyResult = if (setting == PreferredBackendDryRunSetting.DEFAULT) {
                        "skipped-default"
                    } else {
                        "applied"
                    },
                    localFailureDiagnosticsText = """
                        failure stage=engine-create
                        exception class=IllegalStateException
                        exception message=create failed
                    """.trimIndent(),
                ),
                reason = "local_inference_failure",
                exceptionClass = exceptionClass,
                exceptionMessage = exceptionMessage,
                throwable = throwable,
                routeContext = buildLocalRouteDiagnosticContext(
                    selectedModelName = "gemma-local",
                    selectedModelFile = "/tmp/gemma-local.litertlm",
                    preferredBackend = setting.name,
                    npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                    effectiveNpuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                    shouldEnterNpuS1 = false,
                    localRouteEntered = true,
                ),
                ttsRequested = true,
                markdownRequested = true,
                streamingRequested = true,
                processPid = "1234",
            ),
        )

    private fun createNativeStackTestDir(): File {
        val dir = Files.createTempDirectory("lami-runtime-stack-test").toFile()
        dir.resolve("libLiteRt.so").writeText("litert")
        dir.resolve("liblitertlm_jni.so").writeText("litertlm")
        dir.resolve("libLiteRtDispatch_Qualcomm.so").writeText("dispatch")
        dir.resolve("libLiteRtCompilerPlugin_Qualcomm.so").writeText("compiler")
        dir.resolve("libGemmaModelConstraintProvider.so").writeText("constraint")
        return dir
    }

    private fun createCorePairNativeStackTestDir(): File {
        val dir = Files.createTempDirectory("lami-runtime-core-pair-test").toFile()
        dir.resolve("libLiteRt.so").writeText("litert")
        dir.resolve("liblitertlm_jni.so").writeText("litertlm")
        return dir
    }

    private fun matchingMinimalLoadedRuntimeStack(): LoadedRuntimeNativeStackDiagnostics =
        LoadedRuntimeNativeStackDiagnostics(
            sourceFlavor = "standard",
            nativeLibraryDir = "/data/app/lib/arm64-v8a",
            nativeStackSource = "standardDebug_minimal_runtime_dev_gate",
            libLiteRtPresent = "true",
            libLiteRtSha256 = STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERT_SHA256,
            libLiteRtLmJniPresent = "true",
            libLiteRtLmJniSha256 = STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERTLM_JNI_SHA256,
            dispatchQualcommPresent = "false",
            dispatchQualcommSha256 = "unavailable",
            compilerPluginQualcommPresent = "false",
            compilerPluginQualcommSha256 = "unavailable",
            gemmaConstraintProviderPresent = "false",
            gemmaConstraintProviderSha256 = "unavailable",
            fullStackCandidateUnit = "libLiteRt.so+liblitertlm_jni.so",
            alignmentInterpretation = "minimal_runtime_core_pair_candidate",
        )

    private fun buildGpuRouteContextForNewDiagnostics(): LocalRouteDiagnosticContext =
        buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it-edge-gallery",
            selectedModelFile = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
            selectedModelPath = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
}
