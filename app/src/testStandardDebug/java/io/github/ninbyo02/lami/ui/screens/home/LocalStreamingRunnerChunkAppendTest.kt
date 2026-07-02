package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStreamingRunnerChunkAppendTest {
    @Test
    fun `Automatic backend policy uses CPU priority`() {
        val applied = resolveLiteRtTextBackendSelection(PreferredBackendDryRunSetting.DEFAULT)

        assertEquals("CPU", applied.appliedPreferredBackend)
        assertEquals("cpu-priority-default-engine-config", applied.preferredBackendApplyResult)
    }

    @Test
    fun `GPU edge gallery compatibility uses null cache dir for app model path`() {
        assertEquals(
            null,
            resolveLiteRtEngineConfigCacheDir(
                modelPath = "/data/user/0/io.github.ninbyo02.lami/files/gemma-4-E2B-it.litertlm",
                cacheDirPath = "/data/user/0/io.github.ninbyo02.lami/cache",
                edgeGalleryLike = true,
            ),
        )
    }

    @Test
    fun `GPU edge gallery compatibility keeps cache dir for data local tmp model path`() {
        assertEquals(
            "/data/user/0/io.github.ninbyo02.lami/cache",
            resolveLiteRtEngineConfigCacheDir(
                modelPath = "/data/local/tmp/gemma-4-E2B-it.litertlm",
                cacheDirPath = "/data/user/0/io.github.ninbyo02.lami/cache",
                edgeGalleryLike = true,
            ),
        )
    }

    @Test
    fun `GPU diagnostic experiment modes resolve config variants without changing default`() {
        val defaultConfig = buildGpuRouteConfigDiagnostics(
            modelPath = "/data/user/0/io.github.ninbyo02.lami/files/gemma-4-E2B-it.litertlm",
            cacheDirPath = "/data/user/0/io.github.ninbyo02.lami/cache",
            preferredBackend = "GPU",
        )
        val maxTokens32 = buildGpuRouteConfigDiagnostics(
            modelPath = "/data/user/0/io.github.ninbyo02.lami/files/gemma-4-E2B-it.litertlm",
            cacheDirPath = "/data/user/0/io.github.ninbyo02.lami/cache",
            preferredBackend = "GPU",
            experimentMode = GPU_EXPERIMENT_MODE_MAX_TOKENS_32,
        )
        val maxTokens4096 = buildGpuRouteConfigDiagnostics(
            modelPath = "/data/user/0/io.github.ninbyo02.lami/files/gemma-4-E2B-it.litertlm",
            cacheDirPath = "/data/user/0/io.github.ninbyo02.lami/cache",
            preferredBackend = "GPU",
            experimentMode = GPU_EXPERIMENT_MODE_MAX_TOKENS_4096,
        )
        val noSampler = buildGpuRouteConfigDiagnostics(
            modelPath = "/data/user/0/io.github.ninbyo02.lami/files/gemma-4-E2B-it.litertlm",
            cacheDirPath = "/data/user/0/io.github.ninbyo02.lami/cache",
            preferredBackend = "GPU",
            experimentMode = GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION,
        )
        val appCache = buildGpuRouteConfigDiagnostics(
            modelPath = "/data/user/0/io.github.ninbyo02.lami/files/gemma-4-E2B-it.litertlm",
            cacheDirPath = "/data/user/0/io.github.ninbyo02.lami/cache",
            preferredBackend = "GPU",
            experimentMode = GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES,
        )

        assertEquals(GPU_EXPERIMENT_MODE_EDGE_GALLERY_LIKE, defaultConfig.experimentMode)
        assertEquals("1024", defaultConfig.maxTokens)
        assertEquals("4096", defaultConfig.edgeGalleryAllowlistMaxTokens)
        assertEquals("differs_from_edge_gallery_e2b_allowlist", defaultConfig.maxTokensAlignment)
        assertEquals("true", defaultConfig.samplerConfigEnabled)
        assertEquals("null", defaultConfig.cacheDir)
        assertEquals("32", maxTokens32.maxTokens)
        assertEquals("4096", maxTokens4096.maxTokens)
        assertEquals("matches_edge_gallery_e2b_allowlist", maxTokens4096.maxTokensAlignment)
        assertEquals("true", maxTokens4096.samplerConfigEnabled)
        assertEquals("gallery_sampler_config", maxTokens4096.samplerAccelerationPolicy)
        assertEquals("null", maxTokens4096.cacheDir)
        assertEquals("false", maxTokens4096.cacheDirPresent)
        assertEquals(GPU_CONVERSATION_CONFIG_PROFILE_EDGE_GALLERY_LIKE, maxTokens4096.conversationConfigProfile)
        assertEquals("null", maxTokens4096.visionBackend)
        assertEquals("null", maxTokens4096.audioBackend)
        assertEquals("false", noSampler.samplerConfigEnabled)
        assertEquals("conversation_config_without_sampler", noSampler.samplerAccelerationPolicy)
        assertEquals("/data/user/0/io.github.ninbyo02.lami/cache", appCache.cacheDir)
    }

    @Test
    fun `normal chat GPU experiment selector maps requested variants to config`() {
        data class ExpectedConfig(
            val mode: String,
            val maxTokens: String,
            val samplerEnabled: String,
            val samplerPolicy: String,
            val cacheDir: String,
            val cacheDirPresent: String,
            val conversationProfile: String,
            val maxTokensAlignment: String = "differs_from_edge_gallery_e2b_allowlist",
        )

        val modelPath = "/data/user/0/io.github.ninbyo02.lami/files/gemma-4-E2B-it.litertlm"
        val cacheDirPath = "/data/user/0/io.github.ninbyo02.lami/cache"
        val cases = listOf(
            ExpectedConfig(
                mode = GPU_EXPERIMENT_MODE_MAX_TOKENS_32,
                maxTokens = "32",
                samplerEnabled = "true",
                samplerPolicy = "gallery_sampler_config",
                cacheDir = "null",
                cacheDirPresent = "false",
                conversationProfile = GPU_CONVERSATION_CONFIG_PROFILE_EDGE_GALLERY_LIKE,
            ),
            ExpectedConfig(
                mode = GPU_EXPERIMENT_MODE_MAX_TOKENS_4096,
                maxTokens = "4096",
                samplerEnabled = "true",
                samplerPolicy = "gallery_sampler_config",
                cacheDir = "null",
                cacheDirPresent = "false",
                conversationProfile = GPU_CONVERSATION_CONFIG_PROFILE_EDGE_GALLERY_LIKE,
                maxTokensAlignment = "matches_edge_gallery_e2b_allowlist",
            ),
            ExpectedConfig(
                mode = GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION,
                maxTokens = "1024",
                samplerEnabled = "false",
                samplerPolicy = "conversation_config_without_sampler",
                cacheDir = "null",
                cacheDirPresent = "false",
                conversationProfile = "no_sampler_config",
            ),
            ExpectedConfig(
                mode = GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES,
                maxTokens = "1024",
                samplerEnabled = "true",
                samplerPolicy = "gallery_sampler_config",
                cacheDir = cacheDirPath,
                cacheDirPresent = "true",
                conversationProfile = GPU_CONVERSATION_CONFIG_PROFILE_EDGE_GALLERY_LIKE,
            ),
            ExpectedConfig(
                mode = GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER,
                maxTokens = "1024",
                samplerEnabled = "false",
                samplerPolicy = "cache_dir_probe_without_sampler",
                cacheDir = cacheDirPath,
                cacheDirPresent = "true",
                conversationProfile = "no_sampler_config",
            ),
            ExpectedConfig(
                mode = GPU_EXPERIMENT_MODE_CACHE_DIR_NULL_NO_SAMPLER,
                maxTokens = "1024",
                samplerEnabled = "false",
                samplerPolicy = "cache_dir_probe_without_sampler",
                cacheDir = "null",
                cacheDirPresent = "false",
                conversationProfile = "no_sampler_config",
            ),
        )

        cases.forEach { expected ->
            val selectedMode = resolveGpuDiagnosticExperimentModeForBackend(
                preferredBackend = "GPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_experiment_mode") expected.mode else null
                },
                debugSelectorAllowed = true,
            )
            val diagnostics = buildGpuRouteConfigDiagnostics(
                modelPath = modelPath,
                cacheDirPath = cacheDirPath,
                preferredBackend = "GPU",
                experimentMode = selectedMode,
            )

            assertEquals(expected.mode, selectedMode)
            assertEquals(expected.mode, diagnostics.experimentMode)
            assertEquals(GPU_ENGINE_CONFIG_PROFILE_EDGE_GALLERY_LIKE, diagnostics.normalChatEngineConfigStyle)
            assertEquals("GPU", diagnostics.backend)
            assertEquals("null", diagnostics.visionBackend)
            assertEquals("null", diagnostics.audioBackend)
            assertEquals(expected.maxTokens, diagnostics.maxTokens)
            assertEquals(expected.samplerEnabled, diagnostics.samplerConfigEnabled)
            assertEquals(expected.samplerPolicy, diagnostics.samplerAccelerationPolicy)
            assertEquals(expected.cacheDir, diagnostics.cacheDir)
            assertEquals(expected.cacheDirPresent, diagnostics.cacheDirPresent)
            assertEquals("4096", diagnostics.edgeGalleryAllowlistMaxTokens)
            assertEquals(expected.maxTokensAlignment, diagnostics.maxTokensAlignment)
            assertEquals(expected.conversationProfile, diagnostics.conversationConfigProfile)
            assertEquals(expected.samplerEnabled, diagnostics.conversationConfigSamplerPresent)
            assertEquals(
                if (expected.samplerEnabled == "true") GPU_EDGE_GALLERY_LIKE_TOP_K.toString() else "unavailable",
                diagnostics.samplerTopK,
            )
            assertEquals(
                if (expected.samplerEnabled == "true") GPU_EDGE_GALLERY_LIKE_TOP_P else "unavailable",
                diagnostics.samplerTopP,
            )
            assertEquals(
                if (expected.samplerEnabled == "true") GPU_EDGE_GALLERY_LIKE_TEMPERATURE else "unavailable",
                diagnostics.samplerTemperature,
            )
        }
    }

    @Test
    fun `normal chat GPU experiment selector preserves CPU and Automatic`() {
        val propertyReader: (String) -> String? = { key ->
            if (key == "debug.lami.gpu_experiment_mode") {
                GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES_NO_SAMPLER
            } else {
                null
            }
        }

        assertEquals(
            "unavailable",
            resolveGpuDiagnosticExperimentModeForBackend(
                preferredBackend = PreferredBackendDryRunSetting.CPU.name,
                propertyReader = propertyReader,
                debugSelectorAllowed = true,
            ),
        )
        assertEquals(
            "unavailable",
            resolveGpuDiagnosticExperimentModeForBackend(
                preferredBackend = PreferredBackendDryRunSetting.DEFAULT.name,
                propertyReader = propertyReader,
                debugSelectorAllowed = true,
            ),
        )
        assertEquals(
            LiteRtTextBackendSelection("CPU", "applied-engine-config"),
            resolveLiteRtTextBackendSelection(PreferredBackendDryRunSetting.CPU),
        )
        assertEquals(
            LiteRtTextBackendSelection("CPU", "cpu-priority-default-engine-config"),
            resolveLiteRtTextBackendSelection(PreferredBackendDryRunSetting.DEFAULT),
        )
    }

    @Test
    fun `GPU output quality matrix properties resolve sampler and collect only modes`() {
        assertEquals(
            GPU_OUTPUT_QUALITY_MATRIX_MODE_SAMPLER_MINIMAL,
            resolveGpuOutputQualityMatrixModeForDebug(
                preferredBackend = "GPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") "sampler_minimal" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertEquals(
            GPU_EXPERIMENT_MODE_SAMPLER_ONLY_MINIMAL,
            resolveGpuOutputQualityExperimentOverrideForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") "sampler_minimal" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertTrue(
            isGpuOutputQualityCollectOnlyModeForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") "collect_only" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertEquals(
            512,
            resolveGpuOutputQualityMaxTokensOverrideForDebug(
                preferredBackend = "GPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_max_tokens") "512" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertEquals(
            4096,
            resolveGpuOutputQualityMaxTokensOverrideForDebug(
                preferredBackend = "GPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_max_tokens") "4096" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertEquals(
            null,
            resolveGpuOutputQualityMaxTokensOverrideForDebug(
                preferredBackend = "GPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_max_tokens") "4000" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertEquals(
            "matches_edge_gallery_e2b_allowlist",
            resolveGpuMaxTokensAlignment("4096"),
        )
        assertEquals(
            "differs_from_edge_gallery_e2b_allowlist",
            resolveGpuMaxTokensAlignment("1024"),
        )
    }

    @Test
    fun `GPU output quality matrix properties resolve Edge Gallery parity modes`() {
        assertEquals(
            GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_PARITY_MINIMAL,
            resolveGpuOutputQualityMatrixModeForDebug(
                preferredBackend = "GPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_parity_minimal"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertEquals(
            GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES,
            resolveGpuOutputQualityExperimentOverrideForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_parity_cache_app_files"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertEquals(
            GPU_EXPERIMENT_MODE_CACHE_DIR_NULL,
            resolveGpuOutputQualityExperimentOverrideForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_parity_cache_null"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertEquals(
            GPU_EXPERIMENT_MODE_NO_SAMPLING_ACCELERATION,
            resolveGpuOutputQualityExperimentOverrideForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_parity_sampler_none"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertTrue(
            isGpuOutputQualityCollectOnlyModeForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_parity_collect_final"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertTrue(
            isEdgeGalleryParityNoHolderReuseModeForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_parity_no_holder_reuse"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
    }

    @Test
    fun `GPU output quality matrix resolves Edge Gallery final response probe only for GPU candidate flavor`() {
        assertEquals(
            GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE,
            resolveGpuOutputQualityMatrixModeForDebug(
                preferredBackend = "GPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_final_response_probe"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertTrue(
            isGpuOutputQualityCollectOnlyModeForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_final_response_probe"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertEquals(
            "unavailable",
            resolveGpuOutputQualityMatrixModeForDebug(
                preferredBackend = "CPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_final_response_probe"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertEquals(
            "unavailable",
            resolveGpuOutputQualityMatrixModeForDebug(
                preferredBackend = "GPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_final_response_probe"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = false,
            ),
        )
    }

    @Test
    fun `GPU output quality matrix resolves Edge Gallery executor probe only for GPU candidate flavor`() {
        assertEquals(
            GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_EXECUTOR_PROBE,
            resolveGpuOutputQualityMatrixModeForDebug(
                preferredBackend = "GPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_executor_probe"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertTrue(
            isGpuOutputQualityCollectOnlyModeForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_executor_probe"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertTrue(isEdgeGalleryExecutorProbeMode(GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_EXECUTOR_PROBE))
        assertEquals(
            "unavailable",
            resolveGpuOutputQualityMatrixModeForDebug(
                preferredBackend = "CPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") {
                        "edge_gallery_executor_probe"
                    } else {
                        null
                    }
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
    }

    @Test
    fun `Edge Gallery final response probe classifies delta and accumulated callback semantics`() {
        assertEquals(
            "delta_chunks",
            resolveEdgeGalleryCallbackTextSemanticsCandidate(
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE,
                callbackCount = 120,
                accumulatedTextLength = 1200,
                lastNonEmptyTextLength = 2,
            ),
        )
        assertEquals(
            "accumulated_text",
            resolveEdgeGalleryCallbackTextSemanticsCandidate(
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE,
                callbackCount = 12,
                accumulatedTextLength = 1200,
                lastNonEmptyTextLength = 1100,
            ),
        )
        assertEquals(
            "final_only",
            resolveEdgeGalleryCallbackTextSemanticsCandidate(
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE,
                callbackCount = 1,
                accumulatedTextLength = 1200,
                lastNonEmptyTextLength = 1200,
            ),
        )
        assertEquals(
            "unavailable",
            resolveEdgeGalleryCallbackTextSemanticsCandidate(
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_BASELINE,
                callbackCount = 120,
                accumulatedTextLength = 1200,
                lastNonEmptyTextLength = 2,
            ),
        )
    }

    @Test
    fun `Edge Gallery final response probe result keeps quality blocker classification`() {
        assertEquals(
            "pass",
            resolveEdgeGalleryFinalResponseProbeResult(
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE,
                finalCandidateLength = 400,
                finalCandidateSuspiciousReason = "none",
            ),
        )
        assertEquals(
            "fail",
            resolveEdgeGalleryFinalResponseProbeResult(
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE,
                finalCandidateLength = 400,
                finalCandidateSuspiciousReason = "many_tiny_fragments",
            ),
        )
        assertEquals(
            "last_non_empty_callback_is_delta_not_final_response",
            resolveEdgeGalleryFinalResponseProbeDifferenceSummary(
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE,
                appendAllSuspiciousReason = "many_tiny_fragments",
                finalCandidateSuspiciousReason = "none",
                callbackSemanticsCandidate = "delta_chunks",
                accumulatedTextLength = 1200,
                finalCandidateLength = 2,
            ),
        )
        assertEquals(
            "append_all_chunks_and_last_non_empty_both_suspicious",
            resolveEdgeGalleryFinalResponseProbeDifferenceSummary(
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_EDGE_GALLERY_FINAL_RESPONSE_PROBE,
                appendAllSuspiciousReason = "many_tiny_fragments",
                finalCandidateSuspiciousReason = "mixed_language_fragment",
                callbackSemanticsCandidate = "unknown",
                accumulatedTextLength = 1200,
                finalCandidateLength = 400,
            ),
        )
    }

    @Test
    fun `GPU output quality matrix ignores non candidate or non GPU route`() {
        assertEquals(
            "unavailable",
            resolveGpuOutputQualityMatrixModeForDebug(
                preferredBackend = "GPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") "collect_only" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = false,
            ),
        )
        assertEquals(
            "unavailable",
            resolveGpuOutputQualityMatrixModeForDebug(
                preferredBackend = "CPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_matrix_mode") "collect_only" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertEquals(
            null,
            resolveGpuOutputQualityMaxTokensOverrideForDebug(
                preferredBackend = "CPU",
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_output_quality_max_tokens") "512" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
    }

    @Test
    fun `callback quality classifier separates severe fragmentation from healthy chunks`() {
        assertEquals(
            "pathological_single_char_stream",
            classifyCallbackQuality(
                callbackCount = 40,
                twoCharOrLessRatio = "0.900",
                averageChunkLength = "1.20",
            ),
        )
        assertEquals(
            "healthy_large_chunks",
            classifyCallbackQuality(
                callbackCount = 8,
                twoCharOrLessRatio = "0.000",
                averageChunkLength = "12.50",
            ),
        )
    }

    @Test
    fun `callback quality comparison marks GPU corrupt CPU exception as unavailable GPU corrupt`() {
        assertEquals(
            "gpu_corrupt_cpu_unavailable",
            classifyCallbackQualityCompareResult(
                gpuCandidateResult = "quality_candidate_fail",
                gpuSuspiciousDetected = true,
                cpuSuspiciousDetected = false,
                cpuFinished = true,
                cpuSkippedReason = "none",
                cpuExceptionClass = "com.google.ai.edge.litertlm.LiteRtLmJniException",
                cpuFailureStage = "generate_collect",
                cpuCallbackCount = 0,
            ),
        )
    }

    @Test
    fun `callback quality comparison marks healthy GPU CPU exception unavailable`() {
        assertEquals(
            "comparison_unavailable",
            classifyCallbackQualityCompareResult(
                gpuCandidateResult = "quality_candidate_pass",
                gpuSuspiciousDetected = false,
                cpuSuspiciousDetected = false,
                cpuFinished = true,
                cpuSkippedReason = "none",
                cpuExceptionClass = "com.google.ai.edge.litertlm.LiteRtLmJniException",
                cpuFailureStage = "generate_collect",
                cpuCallbackCount = 0,
            ),
        )
    }

    @Test
    fun `callback quality comparison handles skipped and timeout CPU compare`() {
        assertEquals(
            "gpu_corrupt_cpu_unavailable",
            classifyCallbackQualityCompareResult(
                gpuCandidateResult = "quality_candidate_fail",
                gpuSuspiciousDetected = true,
                cpuSuspiciousDetected = false,
                cpuFinished = false,
                cpuSkippedReason = "not_standard_gpu_minimal_runtime_candidate_flavor",
                cpuExceptionClass = "none",
                cpuFailureStage = "none",
                cpuCallbackCount = null,
            ),
        )
        assertEquals(
            "gpu_corrupt_cpu_unavailable",
            classifyCallbackQualityCompareResult(
                gpuCandidateResult = "quality_candidate_fail",
                gpuSuspiciousDetected = true,
                cpuSuspiciousDetected = false,
                cpuFinished = true,
                cpuSkippedReason = "none",
                cpuExceptionClass = "Timeout",
                cpuFailureStage = "timeout",
                cpuCallbackCount = 0,
            ),
        )
    }

    @Test
    fun `GPU sampler root cause candidate separates no sampler from streaming join`() {
        assertEquals(
            "not_sampler_related",
            classifyGpuSamplerRootCauseCandidate(
                suspiciousDetected = true,
                sourceCorruptionStage = "raw_callback",
                uiAppendChangedText = false,
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_NO_SAMPLING_ACCELERATION,
                callbackQualityClassification = "severe_fragmentation",
            ),
        )
        assertEquals(
            "streaming_join_issue",
            classifyGpuSamplerRootCauseCandidate(
                suspiciousDetected = true,
                sourceCorruptionStage = "ui_append_or_final_commit",
                uiAppendChangedText = true,
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_BASELINE,
                callbackQualityClassification = "healthy_large_chunks",
            ),
        )
    }

    @Test
    fun `GPU quality matrix all fail keeps runtime decode fragmentation candidate`() {
        val candidates = listOf(
            classifyGpuSamplerRootCauseCandidate(
                suspiciousDetected = true,
                sourceCorruptionStage = "raw_callback",
                uiAppendChangedText = false,
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_BASELINE,
                callbackQualityClassification = "severe_fragmentation",
            ),
            classifyGpuSamplerRootCauseCandidate(
                suspiciousDetected = true,
                sourceCorruptionStage = "raw_callback",
                uiAppendChangedText = false,
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_COLLECT_ONLY,
                callbackQualityClassification = "severe_fragmentation",
            ),
            classifyGpuSamplerRootCauseCandidate(
                suspiciousDetected = true,
                sourceCorruptionStage = "raw_callback",
                uiAppendChangedText = false,
                matrixMode = GPU_OUTPUT_QUALITY_MATRIX_MODE_NO_SAMPLING_ACCELERATION,
                callbackQualityClassification = "severe_fragmentation",
            ),
        )

        assertEquals(
            listOf(
                "runtime_decode_fragmentation",
                "runtime_decode_fragmentation",
                "not_sampler_related",
            ),
            candidates,
        )
    }

    @Test
    fun `CPU GPU callback compare request is disabled by default`() {
        val request = resolveCpuGpuCallbackCompareRequestForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            propertyReader = { null },
            standardGpuMinimalRuntimeCandidateFlavor = true,
        )

        assertFalse(request.requested)
        assertFalse(request.enabled)
        assertEquals("not_requested", request.skippedReason)
    }

    @Test
    fun `CPU GPU callback compare request enables only candidate flavor by default`() {
        val request = resolveCpuGpuCallbackCompareRequestForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            propertyReader = { key ->
                if (key == "debug.lami.compare_cpu_gpu_callback") "true" else null
            },
            standardGpuMinimalRuntimeCandidateFlavor = true,
        )

        assertTrue(request.requested)
        assertTrue(request.enabled)
        assertEquals("none", request.skippedReason)
    }

    @Test
    fun `CPU GPU callback compare request reports skip reason outside candidate flavor`() {
        val request = resolveCpuGpuCallbackCompareRequestForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            propertyReader = { key ->
                if (key == "debug.lami.compare_cpu_gpu_callback") "true" else null
            },
            standardGpuMinimalRuntimeCandidateFlavor = false,
        )

        assertTrue(request.requested)
        assertFalse(request.enabled)
        assertEquals("not_standard_gpu_minimal_runtime_candidate_flavor", request.skippedReason)
    }

    @Test
    fun `CPU GPU callback compare request supports explicit debug flavor override`() {
        val request = resolveCpuGpuCallbackCompareRequestForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            propertyReader = { key ->
                when (key) {
                    "debug.lami.compare_cpu_gpu_callback" -> "true"
                    "debug.lami.compare_cpu_gpu_callback_allow_any_debug_flavor" -> "true"
                    else -> null
                }
            },
            standardGpuMinimalRuntimeCandidateFlavor = false,
        )

        assertTrue(request.requested)
        assertTrue(request.enabled)
        assertEquals("none", request.skippedReason)
    }

    @Test
    fun `GPU callback raw passthrough is candidate flavor debug gated`() {
        assertTrue(
            isGpuCallbackRawPassthroughEnabledForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_callback_raw_passthrough") "true" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
        assertFalse(
            isGpuCallbackRawPassthroughEnabledForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_callback_raw_passthrough") "true" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = false,
            ),
        )
        assertFalse(
            isGpuCallbackRawPassthroughEnabledForDebug(
                preferredBackend = PreferredBackendDryRunSetting.CPU,
                propertyReader = { key ->
                    if (key == "debug.lami.gpu_callback_raw_passthrough") "true" else null
                },
                standardGpuMinimalRuntimeCandidateFlavor = true,
            ),
        )
    }

    @Test
    fun `GPU diagnostic cache dir resolver supports forced experiment modes`() {
        assertEquals(
            null,
            resolveLiteRtEngineConfigCacheDir(
                modelPath = "/data/user/0/io.github.ninbyo02.lami/files/gemma-4-E2B-it.litertlm",
                cacheDirPath = "/data/user/0/io.github.ninbyo02.lami/cache",
                edgeGalleryLike = true,
                gpuExperimentMode = GPU_EXPERIMENT_MODE_CACHE_DIR_NULL,
            ),
        )
        assertEquals(
            "/data/user/0/io.github.ninbyo02.lami/cache",
            resolveLiteRtEngineConfigCacheDir(
                modelPath = "/data/user/0/io.github.ninbyo02.lami/files/gemma-4-E2B-it.litertlm",
                cacheDirPath = "/data/user/0/io.github.ninbyo02.lami/cache",
                edgeGalleryLike = true,
                gpuExperimentMode = GPU_EXPERIMENT_MODE_CACHE_DIR_APP_FILES,
            ),
        )
    }

    @Test
    fun `GPU prefill probe is disabled by default and for non GPU backend`() {
        assertEquals(
            null,
            resolveGpuPrefillProbeRequestForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                modelPath = "/models/gemma-4-E2B-it.litertlm",
                cacheDirPath = "/cache",
                propertyReader = { null },
            ),
        )
        assertEquals(
            null,
            resolveGpuPrefillProbeRequestForDebug(
                preferredBackend = PreferredBackendDryRunSetting.CPU,
                modelPath = "/models/gemma-4-E2B-it.litertlm",
                cacheDirPath = "/cache",
                propertyReader = { key -> if (key == "debug.lami.gpu_prefill_probe") "true" else null },
            ),
        )
    }

    @Test
    fun `GPU prefill probe request reads debug properties`() {
        val request = resolveGpuPrefillProbeRequestForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            modelPath = "/models/gemma-4-E2B-it.litertlm",
            cacheDirPath = "/cache",
            propertyReader = { key ->
                when (key) {
                    "debug.lami.gpu_prefill_probe" -> "true"
                    "debug.lami.gpu_prefill_probe_prompt" -> "hi"
                    "debug.lami.gpu_prefill_probe_max_tokens" -> "1"
                    "debug.lami.gpu_prefill_probe_sampler" -> "gallery"
                    "debug.lami.gpu_prefill_probe_cache_dir" -> "app_cache"
                    else -> null
                }
            },
        )

        requireNotNull(request)
        assertEquals("hi", request.prompt)
        assertEquals(1, request.maxTokens)
        assertTrue(request.samplerEnabled)
        assertEquals("app_cache", request.cacheDirMode)
        assertTrue(request.skippedNormalGenerate)
        assertTrue(request.isolatedEngineUsed)
        assertFalse(request.sharedEngineUsed)
        assertTrue(request.invalidatesHeldEngine)
    }

    @Test
    fun `GPU held engine prefill probe request is opt in and skips normal generate`() {
        val request = resolveGpuHeldEnginePrefillProbeRequestForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            modelPath = "/models/gemma-4-E2B-it.litertlm",
            cacheDirPath = "/cache",
            propertyReader = { key ->
                when (key) {
                    "debug.lami.gpu_probe_use_held_engine" -> "true"
                    "debug.lami.gpu_prefill_probe_prompt" -> "hi"
                    "debug.lami.gpu_prefill_probe_max_tokens" -> "1"
                    else -> null
                }
            },
        )

        requireNotNull(request)
        assertEquals("hi", request.prompt)
        assertEquals(1, request.maxTokens)
        assertFalse(request.isolatedEngineUsed)
        assertTrue(request.sharedEngineUsed)
        assertTrue(request.usedHeldEngine)
        assertTrue(request.skippedNormalGenerate)
        assertTrue(request.invalidatesHeldEngine)
    }

    @Test
    fun `GPU held engine probe start blocked without held engine reports skip reason`() {
        val text = buildGpuPrefillProbeStartBlockedDiagnosticsText(
            reason = "no_held_engine",
            useHeldEngineRequested = true,
            heldEnginePresentBefore = false,
            heldEngineAcquireResult = "blocked_no_held_engine",
        )

        assertTrue(text.contains("probe_requested=true"))
        assertTrue(text.contains("probe_run_started=false"))
        assertTrue(text.contains("probe_skipped_normal_generate=true"))
        assertTrue(text.contains("probe_start_blocked_reason=no_held_engine"))
        assertTrue(text.contains("probe_normal_generate_blocked_reason=no_held_engine"))
        assertTrue(text.contains("probe_use_held_engine_requested=true"))
        assertTrue(text.contains("probe_used_held_engine=false"))
        assertTrue(text.contains("probe_held_engine_present_before=false"))
        assertTrue(text.contains("probe_held_engine_acquire_result=blocked_no_held_engine"))
        assertTrue(text.contains("probe_held_engine_generate_started=false"))
    }

    @Test
    fun `GPU prefill probe diagnostics classify generate before first token`() {
        val state = GpuPrefillProbeState(
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
        state.engineConfigStarted.set(true)
        state.engineConfigFinished.set(true)
        state.engineInitializeStarted.set(true)
        state.engineInitializeFinished.set(true)
        state.conversationCreateStarted.set(true)
        state.conversationCreateFinished.set(true)
        state.runStarted.set(true)
        state.runTimedOut.set(true)
        state.generateStarted.set(true)
        state.generateStartedAtMs.set(100L)
        state.firstTokenReceived.set(false)
        state.staleCallbackIgnored.set(true)
        state.cleanupStarted.set(true)
        state.cleanupResult.set("cancel_requested_native_generate_may_still_be_processing")

        val text = buildGpuPrefillProbeDiagnosticsText(state)

        assertTrue(text.contains("[DEV診断: GPU prefill probe]"))
        assertTrue(text.contains("probe_requested=true"))
        assertTrue(text.contains("probe_enabled=true"))
        assertTrue(text.contains("probe_run_started=true"))
        assertTrue(text.contains("probe_run_finished=false"))
        assertTrue(text.contains("probe_run_timed_out=true"))
        assertTrue(text.contains("probe_skipped_normal_generate=true"))
        assertTrue(text.contains("probe_isolated_engine_used=true"))
        assertTrue(text.contains("probe_shared_engine_used=false"))
        assertTrue(text.contains("probe_prompt_variant=single_ascii"))
        assertTrue(text.contains("probe_max_tokens=1"))
        assertTrue(text.contains("probe_sampler_enabled=false"))
        assertTrue(text.contains("probe_cache_dir_mode=null"))
        assertTrue(text.contains("probe_generate_started=true"))
        assertTrue(text.contains("probe_first_token_received=false"))
        assertTrue(text.contains("probe_timeout_stage=generate_before_first_token"))
        assertTrue(text.contains("probe_failure_stage=gpu_prefill_probe_timeout_generate_before_first_token"))
        assertTrue(text.contains("probe_stale_callback_ignored=true"))
        assertTrue(text.contains("probe_cleanup_started=true"))
        assertTrue(text.contains("probe_cleanup_finished=false"))
        assertTrue(text.contains("probe_cleanup_result=cancel_requested_native_generate_may_still_be_processing"))
        assertTrue(text.contains("probe_invalidated_held_engine=true"))
        assertTrue(text.contains("probe_normal_generate_blocked_reason=probe_opt_in_runs_without_normal_generate"))
        assertTrue(text.contains("previous_invocation_still_processing_detected=false"))
    }

    @Test
    fun `standard GPU runtime alignment candidate is disabled by default`() {
        val eligibility = resolveStandardGpuRuntimeAlignmentCandidateEligibilityForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            modelPath = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            callbackStreamingGateEnabled = true,
            propertyReader = { null },
        )

        assertFalse(isStandardGpuRuntimeAlignmentCandidateEnabledForDebug(propertyReader = { null }))
        assertFalse(eligibility.enabled)
        assertFalse(eligibility.eligible)
        assertEquals("candidate_gate_disabled", eligibility.blockReason)
    }

    @Test
    fun `standard GPU runtime alignment candidate eligible selects callback streaming path`() {
        val tempDir = File.createTempFile("lami-gpu-model", "dir").apply {
            delete()
            mkdirs()
        }
        val model = tempDir.resolve("gemma-4-E2B-it-edge-gallery.litertlm")
        try {
            RandomAccessFile(model, "rw").use { file ->
                file.setLength(STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES)
            }
            val eligibility = resolveStandardGpuRuntimeAlignmentCandidateEligibilityForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                modelPath = model.absolutePath,
                callbackStreamingGateEnabled = true,
                propertyReader = { key ->
                    when (key) {
                        "debug.lami.standard_gpu_runtime_alignment_candidate" -> "true"
                        else -> null
                    }
                },
            )
            val selected = isGpuCallbackStreamingPathSelectedForDebug(
                probeMode = GPU_GENERATE_PROBE_MODE_NORMAL,
                normalRouteUseCallbackStreaming = true && eligibility.eligible,
            )

            assertTrue(eligibility.enabled)
            assertTrue(eligibility.eligible)
            assertEquals("none", eligibility.blockReason)
            assertEquals("edge_gallery_e2b_expected", eligibility.modelIdentityHint)
            assertTrue(selected)
        } finally {
            model.delete()
            tempDir.delete()
        }
    }

    @Test
    fun `standard GPU runtime alignment candidate blocks ineligible model`() {
        val tempDir = File.createTempFile("lami-gpu-model", "dir").apply {
            delete()
            mkdirs()
        }
        val mismatch = tempDir.resolve("gemma-4-E2B-it-edge-gallery.litertlm")
        try {
            mismatch.writeText("not a real model")

            val eligibility = resolveStandardGpuRuntimeAlignmentCandidateEligibilityForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                modelPath = mismatch.absolutePath,
                callbackStreamingGateEnabled = true,
                propertyReader = { key ->
                    when (key) {
                        "debug.lami.standard_gpu_runtime_alignment_candidate" -> "true"
                        else -> null
                    }
                },
            )

            assertTrue(eligibility.enabled)
            assertFalse(eligibility.eligible)
            assertEquals("model_size_mismatch", eligibility.blockReason)
            assertEquals("edge_gallery_e2b_size_mismatch", eligibility.modelIdentityHint)
        } finally {
            mismatch.delete()
            tempDir.delete()
        }
    }

    @Test
    fun `standard GPU runtime alignment candidate blocks model identity mismatch`() {
        val tempDir = File.createTempFile("lami-gpu-model", "dir").apply {
            delete()
            mkdirs()
        }
        val otherModel = tempDir.resolve("other-model.litertlm")
        try {
            RandomAccessFile(otherModel, "rw").use { file ->
                file.setLength(STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES)
            }
            val eligibility = resolveStandardGpuRuntimeAlignmentCandidateEligibilityForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                modelPath = otherModel.absolutePath,
                callbackStreamingGateEnabled = true,
                propertyReader = { key ->
                    when (key) {
                        "debug.lami.standard_gpu_runtime_alignment_candidate" -> "true"
                        else -> null
                    }
                },
            )

            assertTrue(eligibility.enabled)
            assertFalse(eligibility.eligible)
            assertEquals("model_identity_not_edge_gallery_e2b", eligibility.blockReason)
            assertEquals("not_edge_gallery_e2b", eligibility.modelIdentityHint)
        } finally {
            otherModel.delete()
            tempDir.delete()
        }
    }

    @Test
    fun `standard GPU runtime alignment candidate requires callback streaming gate`() {
        val eligibility = resolveStandardGpuRuntimeAlignmentCandidateEligibilityForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            modelPath = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            callbackStreamingGateEnabled = false,
            propertyReader = { key ->
                when (key) {
                    "debug.lami.standard_gpu_runtime_alignment_candidate" -> "true"
                    else -> null
                }
            },
        )

        assertTrue(eligibility.enabled)
        assertFalse(eligibility.eligible)
        assertEquals("callback_streaming_gate_disabled", eligibility.blockReason)
    }

    @Test
    fun `standard GPU runtime alignment candidate requires normal GPU generate probe mode`() {
        val eligibility = resolveStandardGpuRuntimeAlignmentCandidateEligibilityForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            modelPath = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            callbackStreamingGateEnabled = true,
            gpuGenerateProbeMode = GPU_GENERATE_PROBE_MODE_RAW_CALLBACK_ONLY,
            propertyReader = { key ->
                when (key) {
                    "debug.lami.standard_gpu_runtime_alignment_candidate" -> "true"
                    else -> null
                }
            },
        )

        assertTrue(eligibility.enabled)
        assertFalse(eligibility.eligible)
        assertEquals("unsupported_gpu_generate_probe_mode", eligibility.blockReason)
    }

    @Test
    fun `standard GPU minimal runtime candidate is disabled by default`() {
        val eligibility = resolveStandardGpuMinimalRuntimeCandidateEligibilityForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            modelPath = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            callbackStreamingGateEnabled = true,
            libLiteRtSha256 = STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERT_SHA256,
            libLiteRtLmJniSha256 = STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERTLM_JNI_SHA256,
            dispatchPresent = "false",
            compilerPluginPresent = "false",
            constraintProviderPresent = "false",
            propertyReader = { null },
        )

        assertFalse(isStandardGpuMinimalRuntimeCandidateEnabledForDebug(propertyReader = { null }))
        assertFalse(eligibility.enabled)
        assertFalse(eligibility.eligible)
        assertEquals("candidate_gate_disabled", eligibility.blockReason)
    }

    @Test
    fun `standard GPU minimal runtime candidate is eligible for matching core pair`() {
        val tempDir = File.createTempFile("lami-gpu-minimal-model", "dir").apply {
            delete()
            mkdirs()
        }
        val model = tempDir.resolve("gemma-4-E2B-it-edge-gallery.litertlm")
        try {
            RandomAccessFile(model, "rw").use { file ->
                file.setLength(STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES)
            }
            val eligibility = resolveStandardGpuMinimalRuntimeCandidateEligibilityForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                modelPath = model.absolutePath,
                callbackStreamingGateEnabled = true,
                libLiteRtSha256 = STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERT_SHA256,
                libLiteRtLmJniSha256 = STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERTLM_JNI_SHA256,
                dispatchPresent = "false",
                compilerPluginPresent = "false",
                constraintProviderPresent = "false",
                propertyReader = { key ->
                    when (key) {
                        "debug.lami.standard_gpu_minimal_runtime_candidate" -> "true"
                        else -> null
                    }
                },
            )

            assertTrue(eligibility.enabled)
            assertTrue(eligibility.eligible)
            assertEquals("none", eligibility.blockReason)
            assertEquals("edge_gallery_e2b_expected", eligibility.modelIdentityHint)
            assertEquals("standardDebug_minimal_runtime_dev_gate", eligibility.runtimeStack)
        } finally {
            model.delete()
            tempDir.delete()
        }
    }

    @Test
    fun `standard GPU minimal runtime candidate requires callback streaming gate`() {
        val eligibility = resolveStandardGpuMinimalRuntimeCandidateEligibilityForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            modelPath = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
            callbackStreamingGateEnabled = false,
            libLiteRtSha256 = STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERT_SHA256,
            libLiteRtLmJniSha256 = STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERTLM_JNI_SHA256,
            dispatchPresent = "false",
            compilerPluginPresent = "false",
            constraintProviderPresent = "false",
            propertyReader = { key ->
                when (key) {
                    "debug.lami.standard_gpu_minimal_runtime_candidate" -> "true"
                    else -> null
                }
            },
        )

        assertTrue(eligibility.enabled)
        assertFalse(eligibility.eligible)
        assertEquals("callback_streaming_gate_disabled", eligibility.blockReason)
    }

    @Test
    fun `standard GPU minimal runtime candidate blocks SHA mismatch`() {
        val tempDir = File.createTempFile("lami-gpu-minimal-model", "dir").apply {
            delete()
            mkdirs()
        }
        val model = tempDir.resolve("gemma-4-E2B-it-edge-gallery.litertlm")
        try {
            RandomAccessFile(model, "rw").use { file ->
                file.setLength(STANDARD_GPU_PROBE_EDGE_GALLERY_E2B_MODEL_SIZE_BYTES)
            }
            val eligibility = resolveStandardGpuMinimalRuntimeCandidateEligibilityForDebug(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                modelPath = model.absolutePath,
                callbackStreamingGateEnabled = true,
                libLiteRtSha256 = "sha-mismatch",
                libLiteRtLmJniSha256 = STANDARD_GPU_MINIMAL_RUNTIME_CANDIDATE_LITERTLM_JNI_SHA256,
                dispatchPresent = "false",
                compilerPluginPresent = "false",
                constraintProviderPresent = "false",
                propertyReader = { key ->
                    when (key) {
                        "debug.lami.standard_gpu_minimal_runtime_candidate" -> "true"
                        else -> null
                    }
                },
            )

            assertTrue(eligibility.enabled)
            assertFalse(eligibility.eligible)
            assertEquals("liblitert_sha_mismatch", eligibility.blockReason)
        } finally {
            model.delete()
            tempDir.delete()
        }
    }

    @Test
    fun `GPU prefill probe expands invocation target exception at engine initialize`() {
        val root = IllegalArgumentException("gpu env missing")
        val target = IllegalStateException("initialize failed", root)
        val wrapper = InvocationTargetException(target)
        val state = GpuPrefillProbeState(
            request = GpuPrefillProbeRequest(
                modelPath = "/models/gemma-4-E2B-it.litertlm",
                cacheDirPath = "/cache",
                prompt = "hi",
                maxTokens = 1,
                samplerEnabled = false,
                cacheDirMode = "null",
                heldEnginePresentBefore = true,
            ),
            startedAtMs = 0L,
            elapsedOverrideMs = 3_008L,
        )
        state.runStarted.set(true)
        state.runFinished.set(true)
        state.engineConfigStarted.set(true)
        state.engineConfigFinished.set(true)
        state.engineInitializeStarted.set(true)
        state.engineInitializeFinished.set(false)
        state.exceptionClass.set(wrapper.javaClass.name)
        state.exceptionMessage.set(wrapper.message ?: "none")
        state.exceptionExpansion.set(
            buildLocalFailureExceptionExpansion(
                throwable = wrapper,
                parsed = emptyMap(),
                failureExceptionClass = wrapper.javaClass.name,
                failureExceptionMessage = wrapper.message ?: "none",
            ),
        )
        state.cleanupStarted.set(true)
        state.cleanupFinished.set(true)
        state.cleanupResult.set("closed_probe_conversation_and_engine")

        val text = buildGpuPrefillProbeDiagnosticsText(state)

        assertTrue(text.contains("probe_timeout_stage=engine_initialize"))
        assertTrue(text.contains("probe_failure_stage=gpu_prefill_probe_engine_initialize_invocation_target_exception"))
        assertTrue(text.contains("probe_exception_class=java.lang.reflect.InvocationTargetException"))
        assertTrue(text.contains("probe_exception_message=none"))
        assertTrue(text.contains("probe_exception_cause_class=java.lang.IllegalStateException"))
        assertTrue(text.contains("probe_exception_cause_message=initialize failed"))
        assertTrue(text.contains("probe_exception_cause_message_raw=initialize failed"))
        assertTrue(text.contains("probe_exception_cause_message_sanitized=initialize_failed"))
        assertTrue(text.contains("probe_exception_root_cause_class=java.lang.IllegalArgumentException"))
        assertTrue(text.contains("probe_exception_root_cause_message=gpu env missing"))
        assertTrue(text.contains("probe_exception_chain=java.lang.reflect.InvocationTargetException:none -> java.lang.IllegalStateException:initialize failed -> java.lang.IllegalArgumentException:gpu env missing"))
        assertTrue(text.contains("probe_reflection_target_exception_class=java.lang.IllegalStateException"))
        assertTrue(text.contains("probe_reflection_target_exception_message=initialize failed"))
        assertTrue(text.contains("probe_reflection_target_exception_root_cause_class=java.lang.IllegalArgumentException"))
        assertTrue(text.contains("probe_reflection_target_exception_root_cause_message=gpu env missing"))
        assertTrue(text.contains("probe_isolated_engine_used=true"))
        assertTrue(text.contains("probe_shared_engine_used=false"))
        assertTrue(text.contains("probe_used_held_engine=false"))
        assertTrue(text.contains("probe_held_engine_present_before=true"))
        assertTrue(text.contains("probe_held_engine_invalidated_after=true"))
        assertTrue(text.contains("normal_gpu_last_known_stage=normal_generate_skipped_before_start"))
        assertTrue(text.contains("normal_gpu_can_initialize_with_held_engine_hint=true"))
        assertTrue(text.contains("isolated_gpu_engine_initialize_failed_hint=true"))
    }

    @Test
    fun `LiteRT compiled model failure classification extracts file lines`() {
        val classification = classifyGpuLiteRtFailure(
            message = "Failed_to_create_engine:_INTERNAL:_ERROR:_[runtime/executor/llm_litert_compiled_model_executor.cc:1546] " +
                "ERROR:[external/litert/litert/cc/litert_compiled_model.h:1140]",
            failureStage = "gpu_prefill_probe_engine_initialize_invocation_target_exception",
            timeoutStage = "engine_initialize",
            generateStarted = false,
            firstTokenReceived = false,
            engineInitializeFinished = false,
            conversationCreateFinished = false,
        )

        assertEquals("runtime/executor/llm_litert_compiled_model_executor.cc", classification.executorErrorFile)
        assertEquals("1546", classification.executorErrorLine)
        assertEquals("external/litert/litert/cc/litert_compiled_model.h", classification.compiledModelErrorFile)
        assertEquals("1140", classification.compiledModelErrorLine)
        assertTrue(classification.engineInitializeInternalErrorDetected)
        assertTrue(classification.compiledModelCreationFailed)
        assertEquals("compiled_model_creation_failed_before_conversation", classification.interpretation)
    }

    @Test
    fun `GPU normal route generate before first token is interpreted separately`() {
        val classification = classifyGpuLiteRtFailure(
            message = null,
            failureStage = "gpu_watchdog_timeout_generate_before_first_token",
            timeoutStage = "generate_before_first_token",
            generateStarted = true,
            firstTokenReceived = false,
            engineInitializeFinished = true,
            conversationCreateFinished = true,
        )

        assertEquals("normal_route_generate_hangs_after_successful_initialize", classification.interpretation)
        assertFalse(classification.compiledModelCreationFailed)
    }

    @Test
    fun `GPU prefill probe exception with null message keeps class chain`() {
        val wrapper = InvocationTargetException(IllegalStateException())
        val state = GpuPrefillProbeState(
            request = GpuPrefillProbeRequest(
                modelPath = "/models/gemma-4-E2B-it.litertlm",
                cacheDirPath = "/cache",
            ),
            startedAtMs = 0L,
            elapsedOverrideMs = 1_000L,
        )
        state.runStarted.set(true)
        state.runFinished.set(true)
        state.engineConfigStarted.set(true)
        state.engineConfigFinished.set(true)
        state.engineInitializeStarted.set(true)
        state.exceptionClass.set(wrapper.javaClass.name)
        state.exceptionMessage.set(wrapper.message ?: "none")
        state.exceptionExpansion.set(
            buildLocalFailureExceptionExpansion(
                throwable = wrapper,
                parsed = emptyMap(),
                failureExceptionClass = wrapper.javaClass.name,
                failureExceptionMessage = wrapper.message ?: "none",
            ),
        )

        val text = buildGpuPrefillProbeDiagnosticsText(state)

        assertTrue(text.contains("probe_exception_class=java.lang.reflect.InvocationTargetException"))
        assertTrue(text.contains("probe_exception_message=none"))
        assertTrue(text.contains("probe_exception_cause_class=java.lang.IllegalStateException"))
        assertTrue(text.contains("probe_exception_cause_message=none"))
        assertTrue(text.contains("probe_exception_chain=java.lang.reflect.InvocationTargetException:none -> java.lang.IllegalStateException:none"))
    }

    @Test
    fun `Hello と World の境界では最小 join を入れる`() {
        val builder = StringBuilder("Hello")

        val join = appendStreamingChunk(builder, "World")

        assertEquals(" ", join)
        assertEquals("Hello World", builder.toString())
    }

    @Test
    fun `hello dot py と 日本語助詞は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("hello.py", "を"))
    }

    @Test
    fun `Python と 日本語接続は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("Python", "で"))
    }

    @Test
    fun `print 呼び出しトークンは join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("print(", "\"x\")"))
    }

    @Test
    fun `fenced code の開始は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("```python", "\nimport os"))
    }

    @Test
    fun `foo と comma は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween("foo", ","))
    }

    @Test
    fun `comma と World は join しない`() {
        assertFalse(shouldInsertMinimalJoinBetween(",", "World"))
    }

    @Test
    fun `先頭空白を含む chunk には join を追加しない`() {
        val builder = StringBuilder("Hello")

        val join = appendStreamingChunk(builder, " World")

        assertEquals("", join)
        assertEquals("Hello World", builder.toString())
    }

    @Test
    fun `空白のみ chunk も streaming chunk として保持対象にする`() {
        assertTrue(shouldPreserveWhitespaceChunk(" "))
        assertTrue(isViableStreamingChunk(" \t"))
        val builder = StringBuilder("Hello")

        val join = appendStreamingChunk(builder, " ")

        assertEquals("", join)
        assertEquals("Hello ", builder.toString())
    }

    @Test
    fun `prose lane は日本語の chunk を壊さず連結する`() {
        val builder = StringBuilder("はい、")
        val context = StreamingAppendContext()

        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = " 以下",
            context = context,
        )

        assertEquals("", join)
        assertEquals("はい、 以下", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `python と import の連結で code lane に入り不要 join を入れない`() {
        val builder = StringBuilder("以下に")
        val context = StreamingAppendContext()
        appendStreamingChunk(
            builder = builder,
            extractedRaw = "python",
            context = context,
        )

        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = "import turtle",
            context = context,
        )

        assertEquals("", join)
        assertEquals("以下に\npython\nimport turtle", builder.toString())
        assertEquals(StreamingLane.CODE, context.lane)
    }

    @Test
    fun `code lane の print トークン連結では join しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(
            builder = builder,
            extractedRaw = "print(",
            context = context,
        )
        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = "\"x\")",
            context = context,
        )

        assertEquals("", join)
        assertEquals("print(\"x\")", builder.toString())
        assertEquals(StreamingLane.CODE, context.lane)
    }

    @Test
    fun `prose から code へ遷移しても lane ごとの連結規則を維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(
            builder = builder,
            extractedRaw = "以下に",
            context = context,
        )
        appendStreamingChunk(
            builder = builder,
            extractedRaw = "python",
            context = context,
        )
        val join = appendStreamingChunk(
            builder = builder,
            extractedRaw = "print(\"x\")",
            context = context,
        )

        assertEquals("", join)
        assertEquals("以下に\npython\nprint(\"x\")", builder.toString())
        assertEquals(StreamingLane.CODE, context.lane)
    }

    @Test
    fun `python 単独タグの後に import が来たら改行で再構成する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "import os", context)

        assertEquals("python\nimport os", builder.toString())
    }

    @Test
    fun `python tag は必ず改行でコードと分離される`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(\"Hello\")", context)

        assertEquals("python\nprint(\"Hello\")", builder.toString())
    }

    @Test
    fun `prose の後に python が来た場合も改行される`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "以下に", context)
        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(\"x\")", context)

        assertEquals("以下に\npython\nprint(\"x\")", builder.toString())
    }

    @Test
    fun `pythonprint には絶対にならない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(", context)
        appendStreamingChunk(builder, "\"x\")", context)

        assertEquals("python\nprint(\"x\")", builder.toString())
    }

    @Test
    fun `python タグと複数行コードを再構成する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "def main():", context)
        appendStreamingChunk(builder, "    print(\"x\")", context)

        assertEquals("python\ndef main():\n    print(\"x\")", builder.toString())
    }

    @Test
    fun `prose lane は従来どおり自然文を連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "こんにちは、", context)
        appendStreamingChunk(builder, "承知しました。", context)

        assertEquals("こんにちは、承知しました。", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `x equal と空白付き値は 1 行のまま連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "x =", context)
        appendStreamingChunk(builder, " 1", context)

        assertEquals("x = 1", builder.toString())
    }

    @Test
    fun `if の次の print は必要に応じて改行する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "if x > 0:", context)
        appendStreamingChunk(builder, "print(x)", context)

        assertEquals("if x > 0:\nprint(x)", builder.toString())
    }

    @Test
    fun `python の次に改行付き import が来ても line reassembler で維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "import os\n", context)
        appendStreamingChunk(builder, "print(os.getcwd())", context)

        assertEquals("python\nimport os\nprint(os.getcwd())", builder.toString())
    }

    @Test
    fun `prose と code の後に prose が来たら prose lane に戻る`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "以下に", context)
        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(\"Hello, World!\")", context)
        appendStreamingChunk(builder, "このコードを実行すると", context)

        assertEquals("以下に\npython\nprint(\"Hello, World!\") このコードを実行すると", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `python のコード後に日本語 prose chunk が来たら code lane 固定を解除する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(\"Hello, World!\")", context)
        appendStreamingChunk(builder, "このコードは非常にシンプルです", context)

        assertEquals("python\nprint(\"Hello, World!\")\nこのコードは非常にシンプルです", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `hello dot py と prose は不自然な改行を入れない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "hello.py", context)
        appendStreamingChunk(builder, "というファイル", context)

        assertEquals("hello.pyというファイル", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `python hello dot py は command 風 chunk でも文字単位分解しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python hello.py", context)

        assertEquals("python hello.py", builder.toString())
    }

    @Test
    fun `引用風 inline chunk は prose lane を維持する`() {
        val builder = StringBuilder("説明: ")
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "\"Hello, World!\"", context)
        appendStreamingChunk(builder, "です。", context)

        assertEquals("説明: \"Hello, World!\"です。", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `language tag の直後に fenced code 風 chunk が来ても python と連結しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "```print(\"x\")", context)

        assertEquals("python\n```print(\"x\")", builder.toString())
    }

    @Test
    fun `prose 中の Python と Hello comma は code lane に入らない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("はい、以下に", "Python", "で", "「", "Hello", ",", " World", "！」")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("はい、以下にPythonで「Hello, World！」", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `fenced code chunk は code lane で連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print(", "\"Hello, World!\"", ")", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"Hello, World!\")\n```", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `fenced python で import 行はキーワード境界で論理行を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "import pygame", "import random", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nimport pygame\nimport random\n```", builder.toString())
    }

    @Test
    fun `fenced python で class と def を別論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "class Block:", "def __init__(self):", "self.x = 1", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nclass Block:\ndef __init__(self):\nself.x = 1\n```", builder.toString())
    }

    @Test
    fun `fenced python で class の次に空白付き __init__ 開始を別論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "class Block:", " __init__(self, x, y)", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nclass Block:\n __init__(self, x, y)\n```", builder.toString())
    }

    @Test
    fun `fenced python で空白付き draw 開始を別論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "class Block:", " draw(self, screen)", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nclass Block:\n draw(self, screen)\n```", builder.toString())
    }

    @Test
    fun `fenced python で空白付き if と for と return を別論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "value = 1", " if value > 0:", " for x in items:", " return x", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nvalue = 1\n if value > 0:\n for x in items:\n return x\n```", builder.toString())
    }

    @Test
    fun `fenced python で while は強い開始子として新しい論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "running = True", "while running:", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nrunning = True\nwhile running:\n```", builder.toString())
    }

    @Test
    fun `fenced python で未閉じ quote 継続中は空白付き chunk でも分離しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print(\"Hello,", " World\")", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"Hello, World\")\n```", builder.toString())
    }

    @Test
    fun `fenced python でも print の文字列断片は 1 行維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print", "(\"", "Hello,", " World", "!\")", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"Hello, World!\")\n```", builder.toString())
    }

    @Test
    fun `fenced python でハッシュ記号と日本語コメント断片を 1 行維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "#", " ブ", "ロック", "の色", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n# ブロックの色\n```", builder.toString())
    }

    @Test
    fun `fenced python で inline comment 断片を 1 行維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf(
            "```python",
            "blocked_colors = COLORS[:6] #",
            " ブ",
            "ロック",
            "の色",
            "リスト",
            "を",
            "初期",
            "化",
            "```",
        )

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nblocked_colors = COLORS[:6] # ブロックの色リストを初期化\n```", builder.toString())
    }


    @Test
    fun `fenced python で single chunk 内の fused import を順次分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "import pygameimport randomimport sys", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nimport pygame\nimport random\nimport sys\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の import と assignment を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "import pygame randomWIDTH = 1", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nimport pygame\nrandomWIDTH = 1\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の assignment と assignment を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "WIDTH =80,60GRID_SIZE =30", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nWIDTH =80,60\nGRID_SIZE =30\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の SCREEN 系 assignment 連鎖を3行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "SCREEN_WIDTH =80SCREEN_HEIGHT =60FPS =60", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nSCREEN_WIDTH =80\nSCREEN_HEIGHT =60\nFPS =60\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の True tail から assignment を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "running = Truescore =0", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nrunning = True\nscore =0\n```", builder.toString())
    }

    @Test
    fun `fenced python で import 直後の random と続く assignment を single chunk でも分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "import pygame", " randomWIDTH, HEIGHT =80,60GRID_SIZE =30", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nimport pygame\nrandom\nWIDTH, HEIGHT =80,60\nGRID_SIZE =30\n```", builder.toString())
    }

    @Test
    fun `fenced python で import tail の後ろに identifier が fused したら分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "import pygame random", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nimport pygame\nrandom\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の comment と assignment を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "(0,25,25)# ブロックの色blocked_colors = COLORS[:6] # ブロックの色リストを初期化", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals(
            "```python\n(0,25,25)# ブロックの色\nblocked_colors = COLORS[:6] # ブロックの色リストを初期化\n```",
            builder.toString(),
        )
    }

    @Test
    fun `fenced python で single chunk 内の comment と assignment と class を順次分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf(
            "```python",
            "(0,25,25)# ブロックの色blocked_colors = COLORS[:6] # ブロックの色リストを初期化class Block:",
            "```",
        )

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals(
            "```python\n(0,25,25)# ブロックの色\nblocked_colors = COLORS[:6] # ブロックの色リストを初期化\nclass Block:\n```",
            builder.toString(),
        )
    }

    @Test
    fun `fenced python で single chunk 内の closing tail と comment と class を順次分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "COLORS[:6]# コメントclass Block:", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nCOLORS[:6]\n# コメント\nclass Block:\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の comment と class を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "# 初期化class Block:", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n# 初期化\nclass Block:\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の comment と assignment を分離する 先頭コメント版`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "# ブロックの色blocked_colors = COLORS[:6]", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n# ブロックの色\nblocked_colors = COLORS[:6]\n```", builder.toString())
    }

    @Test
    fun `fenced python で closing bracket tail の後ろに comment が来たら分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "(0,25,25)# ブロックの色", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n(0,25,25)\n# ブロックの色\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の comment と def を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "# 初期化def build():", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n# 初期化\ndef build():\n```", builder.toString())
    }

    @Test
    fun `fenced python で identifier tail の後ろに assignment starter が来たら分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "randomWIDTH, HEIGHT =80,60", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nrandom\nWIDTH, HEIGHT =80,60\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk の print 文字列はそのまま維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print(\"Hello, World!\")", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"Hello, World!\")\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の assignment と assignment を連続分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "ball_color = COLORS[0]running = True", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nball_color = COLORS[0]\nrunning = True\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の assignment 連鎖と while 開始を順次分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "ball_color = COLORS[0]running = Truewhile running:", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nball_color = COLORS[0]\nrunning = True\nwhile running:\n```", builder.toString())
    }


    @Test
    fun `fenced python で call tail の後ろに identifier が fused したら分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "pygame.quit()clock = pygame.time.Clock()", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\npygame.quit()\nclock = pygame.time.Clock()\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の call tail と class starter を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "pygame.quit()class Block:", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\npygame.quit()\nclass Block:\n```", builder.toString())
    }

    @Test
    fun `fenced python で single chunk 内の連続コメントを順次分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "# ブロックの色# 次のコメント", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n# ブロックの色\n# 次のコメント\n```", builder.toString())
    }

    @Test
    fun `fenced python で quoted string 内の class キーワードは分離しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print(\"class Block:\")", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"class Block:\")\n```", builder.toString())
    }

    @Test
    fun `fenced python でコメント行の後に class が来たら新しい論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "# コメント", "class Block:", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n# コメント\nclass Block:\n```", builder.toString())
    }

    @Test
    fun `fenced python でコメント行の後に assignment が来たら新しい論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "# ブロックの色", "blocked_colors = COLORS[:6]", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n# ブロックの色\nblocked_colors = COLORS[:6]\n```", builder.toString())
    }

    @Test
    fun `fenced python でコメント行の後に def が来たら新しい論理行に分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "# 初期化", "def build():", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n# 初期化\ndef build():\n```", builder.toString())
    }

    @Test
    fun `fenced python でコメント continuation は維持しつつ次の assignment を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "# ブロック", "の色", "blocked_colors = COLORS[:6]", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\n# ブロックの色\nblocked_colors = COLORS[:6]\n```", builder.toString())
    }

    @Test
    fun `prose lane の C sharp と日本語は従来どおり連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("C#", "の話")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("C#の話", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `fenced bash は python 専用ルールで誤改行しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```bash", "echo", " hello", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```bash\necho hello\n```", builder.toString())
    }

    @Test
    fun `fenced bash のコメント行は既存挙動のまま次行を分離する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```bash", "# hello", "echo world", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```bash\n# hello\necho world\n```", builder.toString())
    }

    @Test
    fun `fenced bash の空白付き if 風 chunk でも python 専用分離はしない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```bash", "echo", " if true", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```bash\necho if true\n```", builder.toString())
    }

    @Test
    fun `fenced bash の single chunk は python 専用 pre split を適用しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```bash", "echo helloecho world", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```bash\necho helloecho world\n```", builder.toString())
    }

    @Test
    fun `fenced bash では hash と assignment fused でも python 専用分離はしない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```bash", "echo hello#noteVALUE=1", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```bash\necho hello#noteVALUE=1\n```", builder.toString())
    }

    @Test
    fun `fenced bash の single chunk 混在は python 専用 pre split を適用しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```bash", "echo helloecho world", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```bash\necho helloecho world\n```", builder.toString())
    }

    @Test
    fun `fenced bash には python の single chunk 分離を適用しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```bash", "echo hello#noteVALUE=1", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```bash\necho hello#noteVALUE=1\n```", builder.toString())
    }

    @Test
    fun `prose lane の Python 説明文は従来どおり連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("Python", "の基本", "構造")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("Pythonの基本構造", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `prose lane の single chunk は python 専用 pre split の対象外`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "Pythonの基本構造", context)

        assertEquals("Pythonの基本構造", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `prose lane の先頭空白付き if は従来どおり prose 連結する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("Python の基本", " 構造", " は", " 大事")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("Python の基本 構造 は 大事", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `language tag の後に prose が来たら prose lane を維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "このコードは", context)

        assertEquals("pythonこのコードは", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `language tag と strong code で code lane に入る`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print(\"x\")", context)

        assertEquals("python\nprint(\"x\")", builder.toString())
        assertEquals(StreamingLane.CODE, context.lane)
    }

    @Test
    fun `code lane 中に prose like chunk が来たら prose lane に戻る`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "def main():", context)
        appendStreamingChunk(builder, "print(\"x\")", context)
        appendStreamingChunk(builder, "このコードは", context)

        assertEquals("def main():\nprint(\"x\") このコードは", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `Hello comma World は prose lane で改行しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "Hello", context)
        appendStreamingChunk(builder, ",", context)
        appendStreamingChunk(builder, " World", context)

        assertEquals("Hello, World", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `code lane で print 文字列断片を 1 行に再構成する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)
        val chunks = listOf("print", "(\"", "Hello", ",", " World", "!\")")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("print(\"Hello, World!\")", builder.toString())
    }

    @Test
    fun `code lane で未閉じ double quote は commit しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "print(\"Hello,", context)
        appendStreamingChunk(builder, " World!\")", context)

        assertEquals("print(\"Hello, World!\")", builder.toString())
    }

    @Test
    fun `code lane で未閉じ single quote は commit しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "msg = 'abc", context)
        appendStreamingChunk(builder, " def'", context)

        assertEquals("msg = 'abc def'", builder.toString())
    }

    @Test
    fun `code lane で開き括弧継続中は commit しない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext(lane = StreamingLane.CODE)

        appendStreamingChunk(builder, "print(", context)
        appendStreamingChunk(builder, "\"x\")", context)

        assertEquals("print(\"x\")", builder.toString())
    }

    @Test
    fun `code lane で language tag 後の print 文字列断片を 1 行に再構成する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()

        appendStreamingChunk(builder, "python", context)
        appendStreamingChunk(builder, "print", context)
        appendStreamingChunk(builder, "(\"", context)
        appendStreamingChunk(builder, "Hello,", context)
        appendStreamingChunk(builder, " World", context)
        appendStreamingChunk(builder, "!\")", context)

        assertEquals("python\nprint(\"Hello, World!\")", builder.toString())
    }

    @Test
    fun `opening fence の直後は必ず改行される`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print", "(\"x\")")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"x\")", builder.toString())
        assertFalse(builder.toString().contains("```pythonprint"))
    }

    @Test
    fun `closing fence の前に未閉じ quote 行を flush しても 1 行を維持する`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print", "(\"", "Hello,", " World", "!\")", "```")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"Hello, World!\")\n```", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
    }

    @Test
    fun `closing fence の後は prose lane に戻り prose を混在させない`() {
        val builder = StringBuilder()
        val context = StreamingAppendContext()
        val chunks = listOf("```python", "print", "(\"Hello, World!\")", "```", "このコードを実行すると")

        chunks.forEach { chunk -> appendStreamingChunk(builder, chunk, context) }

        assertEquals("```python\nprint(\"Hello, World!\")\n```\nこのコードを実行すると", builder.toString())
        assertEquals(StreamingLane.PROSE, context.lane)
        assertFalse(builder.toString().contains("World!\")このコード"))
    }
}
