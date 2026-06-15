package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuInternalSurfaceProbeDiagnosticsTest {
    @Test
    fun `property off emits disabled minimal diagnostics for candidate GPU flavor`() {
        val diagnostics = buildGpuInternalSurfaceProbeDiagnostics(
            preferredBackend = "GPU",
            propertyReader = { null },
            debugBuild = true,
            standardGpuMinimalRuntimeCandidateFlavor = true,
        )

        assertTrue(diagnostics.emit)
        assertEquals("false", diagnostics.enabled)
        assertEquals("disabled", diagnostics.result)
        assertEquals("property_off", diagnostics.disabledReason)
        assertEquals("unavailable", diagnostics.runtimeConfigClassPresent)
    }

    @Test
    fun `property on scans classes and native symbols read only`() {
        val tempDir = Files.createTempDirectory("gpu-internal-surface-test").toFile()
        try {
            File(tempDir, "liblitertlm_jni.so").writeText(
                "GPU_ARTISAN\nLlmGpuArtisanExecutor\ntflite_gpu_kv_cache\n",
            )
            val diagnostics = buildGpuInternalSurfaceProbeDiagnostics(
                preferredBackend = "GPU",
                nativeLibraryDir = tempDir.absolutePath,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_internal_surface_probe") "true" else null
                },
                debugBuild = true,
                standardGpuMinimalRuntimeCandidateFlavor = false,
                applicationId = "io.github.ninbyo02.lami.gpustandardminimal",
            )

            assertTrue(diagnostics.emit)
            assertEquals("true", diagnostics.enabled)
            assertEquals("completed", diagnostics.result)
            assertEquals("none", diagnostics.disabledReason)
            assertEquals("false", diagnostics.runtimeConfigClassPresent)
            assertEquals("class_absent", diagnostics.runtimeConfigMethods)
            assertEquals("true", diagnostics.llmGpuArtisanExecutorSymbolPresent)
            assertEquals("true", diagnostics.kvCacheSymbolPresent)
            assertEquals("none", diagnostics.exceptionClass)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `CPU route emits not eligible without running internal surface probe`() {
        val diagnostics = buildGpuInternalSurfaceProbeDiagnostics(
            preferredBackend = "CPU",
            propertyReader = { "true" },
            debugBuild = true,
            standardGpuMinimalRuntimeCandidateFlavor = true,
            applicationId = "io.github.ninbyo02.lami.gpustandardminimal",
        )

        assertTrue(diagnostics.emit)
        assertEquals("false", diagnostics.enabled)
        assertEquals("not_eligible", diagnostics.result)
        assertEquals("not_gpu_backend", diagnostics.disabledReason)
        assertEquals("unavailable", diagnostics.runtimeConfigClassPresent)
    }

    @Test
    fun `standard application id is not eligible even when property is true`() {
        val diagnostics = buildGpuInternalSurfaceProbeDiagnostics(
            preferredBackend = "GPU",
            propertyReader = { "true" },
            debugBuild = true,
            standardGpuMinimalRuntimeCandidateFlavor = false,
            applicationId = "io.github.ninbyo02.lami",
        )

        assertTrue(diagnostics.emit)
        assertEquals("false", diagnostics.enabled)
        assertEquals("not_eligible", diagnostics.result)
        assertEquals("not_gpustandardminimal_application", diagnostics.disabledReason)
    }

    @Test
    fun `probe exceptions are reported without throwing`() {
        val throwingClassLoader = object : ClassLoader(null) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                throw LinkageError("hidden surface unavailable")
            }
        }
        val diagnostics = buildGpuInternalSurfaceProbeDiagnostics(
            preferredBackend = "GPU",
            propertyReader = { key ->
                if (key == "debug.lami.gpu_internal_surface_probe") "true" else null
            },
            debugBuild = true,
            standardGpuMinimalRuntimeCandidateFlavor = true,
            applicationId = "io.github.ninbyo02.lami.gpustandardminimal",
            classLoader = throwingClassLoader,
        )

        assertTrue(diagnostics.emit)
        assertEquals("true", diagnostics.enabled)
        assertEquals("exception", diagnostics.result)
        assertEquals("java.lang.LinkageError", diagnostics.exceptionClass)
        assertTrue(diagnostics.exceptionMessage.contains("hidden_surface_unavailable"))
    }

    @Test
    fun `compact diagnostics include internal surface probe keys`() {
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = buildLocalRouteDiagnosticContext(
                selectedModelName = "gemma-4-E2B-it-edge-gallery",
                selectedModelFile = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
                selectedModelPath = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
                preferredBackend = "GPU",
                npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                shouldEnterNpuS1 = false,
                localRouteEntered = true,
            ),
            flags = LocalRouteDiagnosticFlags(
                failureStage = "none",
                gpuInternalSurfaceProbeDiagnostics = mapOf(
                    "gpu_internal_surface_probe_enabled" to "true",
                    "gpu_internal_surface_probe_result" to "completed",
                    "gpu_internal_surface_probe_disabled_reason" to "none",
                    "gpu_internal_runtime_config_class_present" to "false",
                    "gpu_internal_backend_constraint_class_present" to "false",
                    "gpu_internal_preferred_engine_type_class_present" to "false",
                    "gpu_internal_gpu_options_class_present" to "false",
                    "gpu_internal_artisan_class_present" to "false",
                    "gpu_internal_llm_gpu_artisan_executor_symbol_present" to "true",
                    "gpu_internal_kv_cache_symbol_present" to "true",
                    "gpu_internal_runtime_config_methods" to "class_absent",
                    "gpu_internal_backend_constraint_methods" to "class_absent",
                    "gpu_internal_gpu_options_methods" to "class_absent",
                    "gpu_internal_probe_exception_class" to "none",
                    "gpu_internal_probe_exception_message" to "none",
                ),
            ),
            elapsedMs = 1_000L,
        )
        val compact = buildLocalInferenceFailureCompactDiagnosticsText(
            buildLocalInferenceFailureCompactInputFromTrace(
                inputPrompt = "カレーの材料をお願いします。",
                preferredBackendSetting = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
                trace = LocalInferenceTrace(localFailureDiagnosticsText = routeDiagnostics),
                status = "success",
                reason = "diagnostic_success",
                routeContext = buildLocalRouteDiagnosticContext(
                    selectedModelName = "gemma-4-E2B-it-edge-gallery",
                    selectedModelFile = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
                    selectedModelPath = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
                    preferredBackend = "GPU",
                    npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                    shouldEnterNpuS1 = false,
                    localRouteEntered = true,
                ),
            ),
        )

        assertTrue(compact.contains("gpu_internal_surface_probe_enabled=true"))
        assertTrue(compact.contains("gpu_internal_surface_probe_result=completed"))
        assertTrue(compact.contains("gpu_internal_surface_probe_disabled_reason=none"))
        assertTrue(compact.contains("gpu_internal_llm_gpu_artisan_executor_symbol_present=true"))
        assertTrue(compact.contains("gpu_internal_kv_cache_symbol_present=true"))
    }

    @Test
    fun `GPU success LOCAL_ROUTE_DIAG emits internal surface presence keys with executor probe`() {
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = buildLocalRouteDiagnosticContext(
                selectedModelName = "gemma-4-E2B-it-edge-gallery",
                selectedModelFile = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
                selectedModelPath = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
                preferredBackend = "GPU",
                npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                shouldEnterNpuS1 = false,
                localRouteEntered = true,
            ),
            flags = LocalRouteDiagnosticFlags(
                failureStage = "none",
                generateStarted = true,
                firstTokenReceived = true,
                gpuCallbackStreamingPathSelected = true,
                gpuCallbackTextPromotedToUi = true,
                gpuUiAppendStarted = true,
                gpuUiAppendFinished = true,
                gpuStreamingCompletionReason = "flow_completed_non_empty_response",
                gpuOutputQualityMatrixMode = "edge_gallery_executor_probe",
                gpuOutputQualityCandidateResult = "quality_candidate_fail",
                callbackCorruptionEarliestStage = "raw_callback",
                gpuOutputSourceCorruptionStage = "raw_callback",
                gpuPrefillProbeDiagnostics = mapOf(
                    "gpu_sampler_root_cause_candidate" to "runtime_decode_fragmentation",
                ),
            ),
            elapsedMs = 1_000L,
        )

        assertTrue(routeDiagnostics.contains("LOCAL_ROUTE_DIAG "))
        assertTrue(routeDiagnostics.contains("edge_gallery_executor_probe_result="))
        assertTrue(routeDiagnostics.contains("gpu_internal_surface_probe_enabled=false"))
        assertTrue(routeDiagnostics.contains("gpu_internal_surface_probe_result=disabled"))
        assertTrue(routeDiagnostics.contains("gpu_internal_surface_probe_disabled_reason=not_gpustandardminimal_application"))
    }

    @Test
    fun `injected internal surface map is normalized with presence keys`() {
        val routeDiagnostics = buildLocalRouteDiagnosticTrace(
            stage = "generate_streaming_completed",
            context = buildLocalRouteDiagnosticContext(
                selectedModelName = "gemma-4-E2B-it-edge-gallery",
                selectedModelFile = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
                selectedModelPath = "/models/gemma-4-E2B-it-edge-gallery.litertlm",
                preferredBackend = "GPU",
                npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                shouldEnterNpuS1 = false,
                localRouteEntered = true,
            ),
            flags = LocalRouteDiagnosticFlags(
                failureStage = "none",
                gpuInternalSurfaceProbeDiagnostics = mapOf(
                    "gpu_internal_runtime_config_class_present" to "false",
                ),
            ),
            elapsedMs = 1_000L,
        )

        assertTrue(routeDiagnostics.contains("gpu_internal_surface_probe_enabled=false"))
        assertTrue(routeDiagnostics.contains("gpu_internal_surface_probe_result=disabled"))
        assertTrue(routeDiagnostics.contains("gpu_internal_surface_probe_disabled_reason=property_off"))
        assertTrue(routeDiagnostics.contains("gpu_internal_runtime_config_class_present=false"))
    }
}
