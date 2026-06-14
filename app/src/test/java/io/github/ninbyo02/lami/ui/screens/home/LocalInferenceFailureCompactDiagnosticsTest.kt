package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException

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
        assertTrue(routeDiagnostics.contains("gpu_generate_exception_error_line=735"))
        assertTrue(compact.contains("standard_gpu_probe_expected_edge_gallery_e2b=true"))
        assertTrue(compact.contains("standard_gpu_probe_result_candidate=failure"))
        assertTrue(compact.contains("litert_lm_error_status_code=13"))
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
        assertTrue(compact.contains("gpu_alignment_holder_reuse_block_reason=holder_cleared_after_failure"))
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
                gpuAlignmentHolderReused = true,
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
}
