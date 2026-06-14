package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import java.io.File
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
        assertEquals("true", defaultConfig.samplerConfigEnabled)
        assertEquals("null", defaultConfig.cacheDir)
        assertEquals("32", maxTokens32.maxTokens)
        assertEquals("false", noSampler.samplerConfigEnabled)
        assertEquals("conversation_config_without_sampler", noSampler.samplerAccelerationPolicy)
        assertEquals("/data/user/0/io.github.ninbyo02.lami/cache", appCache.cacheDir)
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
        val eligibility = resolveStandardGpuRuntimeAlignmentCandidateEligibilityForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            modelPath = "/sdcard/Download/gemma-4-E2B-it-edge-gallery.litertlm",
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
        assertEquals("edge_gallery_e2b_expected_size_unavailable", eligibility.modelIdentityHint)
        assertTrue(selected)
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
