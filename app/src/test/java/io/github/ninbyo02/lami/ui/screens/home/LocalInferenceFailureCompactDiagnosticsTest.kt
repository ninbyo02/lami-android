package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
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
        assertTrue(text.contains("gpu_generate_started=false"))
        assertTrue(text.contains("gpu_first_token_received=false"))
        assertTrue(text.contains("gpu_stale_callback_ignored=true"))
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
