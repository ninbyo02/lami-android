package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendNpuAttachProbeReportFormatterTest {
    @Test
    fun `inventory report includes required attach diagnostics without engine initialize opt-in`() {
        val snapshot = snapshot(
            backendNpuInstantiateResult = "success",
            engineConfigNpuDryBuildResult = "success",
            engineInitializeDryRunExplicitOptIn = false,
            engineInitializeDryRunInitializeInvoked = "no",
            engineInitializeDryRunInitializeResult = "skipped",
            engineInitializeDryRunSkipReason = "explicit-opt-in-required",
        )
        val request = BackendNpuAttachProbeReportRequest(
            runId = "20260601_120000",
            phase = BackendNpuAttachProbeReportFormatter.PHASE_INVENTORY,
            engineInitializeOptIn = false,
            processAliveAfterProbe = "alive",
            engineConfigVariant = "cache-files",
            engineConfigCacheDir = "/data/user/0/io.github.ninbyo02.lami.npu/files/backend_npu_attach_probe_cache",
            modelCanonicalPath = "/data/data/io.github.ninbyo02.lami.npu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm",
            modelPathVariant = "/data/user/0",
            nativeLibraryDirVariant = "applicationInfo.nativeLibraryDir",
            applicationInfoNativeLibraryDir = "/data/app/lib/arm64",
            contextApplicationInfoNativeLibraryDir = "/data/app/lib/arm64",
            hardResolvedNativeLibraryDir = "/data/app/lib/arm64",
        )

        val text = BackendNpuAttachProbeReportFormatter.formatText(snapshot, request)
        val markdown = BackendNpuAttachProbeReportFormatter.formatMarkdown(snapshot, request)

        assertTrue(text.contains("backend_npu_attach_probe_v1"))
        assertTrue(text.contains("isolated_flavor=false"))
        assertTrue(text.contains("gallery_aligned_stack=false"))
        assertTrue(text.contains("model_path=/sdcard/Download/gemma-4-E2B-it_qualcomm_sm8750.litertlm"))
        assertTrue(text.contains("model_canonical_path=/data/data/io.github.ninbyo02.lami.npu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm"))
        assertTrue(text.contains("model_path_variant=/data/user/0"))
        assertTrue(text.contains("native_library_dir=/data/app/lib/arm64"))
        assertTrue(text.contains("native_library_dir_variant=applicationInfo.nativeLibraryDir"))
        assertTrue(text.contains("application_info_native_library_dir=/data/app/lib/arm64"))
        assertTrue(text.contains("context_application_info_native_library_dir=/data/app/lib/arm64"))
        assertTrue(text.contains("hard_resolved_native_library_dir=/data/app/lib/arm64"))
        assertTrue(text.contains("backend_npu_constructor_used=Backend.NPU(String)"))
        assertTrue(text.contains("backend_npu_object_class=com.google.ai.edge.litertlm.Backend\$NPU"))
        assertTrue(text.contains("engineconfig_constructor_factory_used=EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)"))
        assertTrue(text.contains("engine_config_variant=cache-files"))
        assertTrue(text.contains("engineconfig_cache_dir=/data/user/0/io.github.ninbyo02.lami.npu/files/backend_npu_attach_probe_cache"))
        assertTrue(text.contains("engineconfig_max_num_tokens=null"))
        assertTrue(text.contains("engineconfig_backend_getter_result=com.google.ai.edge.litertlm.Backend\$NPU"))
        assertTrue(text.contains("lib_inventory_summary=liblitertlm_jni=litertlm-jni-build-id;libLiteRt=litert-build-id;libLiteRtDispatch_Qualcomm=dispatch-build-id"))
        assertTrue(text.contains("gallery_stack_comparison_result=matches-gallery-sm8750-build-ids"))
        assertTrue(text.contains("suspected_root_cause=unknown"))
        assertTrue(text.contains("engine_initialize_invoked=no"))
        assertTrue(text.contains("engine_initialize_skip_reason=explicit-opt-in-required"))
        assertTrue(text.contains("native_crash_suspected=unknown-script-checks-logcat"))
        assertTrue(text.contains("signal=-"))
        assertTrue(text.contains("abort_message=-"))
        assertTrue(text.contains("backtrace_head=-"))
        assertTrue(text.contains("conversation_create_result=not-attempted-minimal-dry-run"))
        assertTrue(text.contains("one_token_decode_result=not-attempted-minimal-dry-run"))
        assertTrue(markdown.contains("| backend_npu_attach_status | engineconfig-holds-backend-npu |"))
        assertTrue(markdown.contains("Phase 3"))
    }

    @Test
    fun `engine initialize report surfaces exception and mismatch hints`() {
        val snapshot = snapshot(
            engineInitializeDryRunExplicitOptIn = true,
            engineInitializeDryRunInitializeInvoked = "yes",
            engineInitializeDryRunInitializeReturned = "no",
            engineInitializeDryRunInitializeResult = "failed",
            engineInitializeDryRunExceptionClass = "java.lang.UnsatisfiedLinkError",
            engineInitializeDryRunExceptionMessage = "No usable Dispatch runtime found",
            engineInitializeDryRunRootCause = "UnsatisfiedLinkError:No usable Dispatch runtime found",
            engineInitializeDryRunCauseChain = "UnsatisfiedLinkError:No usable Dispatch runtime found",
            engineInitializeDryRunUnsatisfiedLinkErrorDetected = true,
            engineInitializeDryRunNoUsableDispatchRuntimeDetected = true,
            engineInitializeDryRunSymbolMismatchDetected = true,
            engineInitializeDryRunElapsedMs = 1234L,
            galleryStackExpectedBuildIdMatch = false,
        )
        val request = BackendNpuAttachProbeReportRequest(
            runId = "20260601_120001",
            phase = BackendNpuAttachProbeReportFormatter.PHASE_ENGINE_INITIALIZE,
            engineInitializeOptIn = true,
            processAliveAfterProbe = "alive",
            nativeCrashSuspected = "true",
            signal = "Fatal signal 6",
            abortMessage = "No usable Dispatch runtime found",
            backtraceHead = "#00 pc 00000000 libLiteRt.so",
            engineConfigVariant = "max128",
            engineConfigMaxNumTokens = "128",
        )

        val text = BackendNpuAttachProbeReportFormatter.formatText(snapshot, request)

        assertTrue(text.contains("engine_initialize_invoked=yes"))
        assertTrue(text.contains("engine_config_variant=max128"))
        assertTrue(text.contains("engineconfig_max_num_tokens=128"))
        assertTrue(text.contains("exception_class=java.lang.UnsatisfiedLinkError"))
        assertTrue(text.contains("unsatisfied_link_error_detected=true"))
        assertTrue(text.contains("dispatch_api_load_error_detected=true"))
        assertTrue(text.contains("symbol_mismatch_suspected=true"))
        assertTrue(text.contains("gallery_stack_comparison_result=differs-from-gallery-sm8750-build-ids"))
        assertTrue(text.contains("suspected_root_cause=runtime_stack_mismatch_candidate"))
        assertTrue(text.contains("elapsed_ms=1234"))
        assertTrue(text.contains("process_alive_after_probe=alive"))
        assertTrue(text.contains("native_crash_suspected=true"))
        assertTrue(text.contains("signal=Fatal signal 6"))
        assertTrue(text.contains("abort_message=No usable Dispatch runtime found"))
        assertTrue(text.contains("backtrace_head=#00 pc 00000000 libLiteRt.so"))
    }

    @Test
    fun `gallery aligned isolated flavor is reported explicitly`() {
        val snapshot = snapshot(
            currentFlavor = "galleryAlignedNpuProbe",
            applicationId = "io.github.ninbyo02.lami.galleryprobe",
            galleryStackExpectedBuildIdMatch = true,
        )
        val request = BackendNpuAttachProbeReportRequest(
            runId = "20260602_120000",
            phase = BackendNpuAttachProbeReportFormatter.PHASE_INVENTORY,
            engineInitializeOptIn = false,
        )

        val text = BackendNpuAttachProbeReportFormatter.formatText(snapshot, request)

        assertTrue(text.contains("current_flavor=galleryAlignedNpuProbe"))
        assertTrue(text.contains("application_id=io.github.ninbyo02.lami.galleryprobe"))
        assertTrue(text.contains("isolated_flavor=true"))
        assertTrue(text.contains("gallery_aligned_stack=true"))
    }

    @Test
    fun `engine initialize dry-run stays off without explicit opt-in`() {
        assertFalse(
            BackendNpuAttachProbeReportFormatter.shouldRunEngineInitializeDryRun(
                phase = BackendNpuAttachProbeReportFormatter.PHASE_ENGINE_INITIALIZE,
                explicitOptIn = false,
            ),
        )
        assertFalse(
            BackendNpuAttachProbeReportFormatter.shouldRunEngineInitializeDryRun(
                phase = BackendNpuAttachProbeReportFormatter.PHASE_INVENTORY,
                explicitOptIn = true,
            ),
        )
        assertTrue(
            BackendNpuAttachProbeReportFormatter.shouldRunEngineInitializeDryRun(
                phase = BackendNpuAttachProbeReportFormatter.PHASE_ENGINE_INITIALIZE,
                explicitOptIn = true,
            ),
        )
    }

    private fun snapshot(
        backendNpuInstantiateResult: String = "success",
        engineConfigNpuDryBuildResult: String = "success",
        engineInitializeDryRunExplicitOptIn: Boolean = false,
        engineInitializeDryRunInitializeInvoked: String = "no",
        engineInitializeDryRunInitializeReturned: String = "no",
        engineInitializeDryRunInitializeResult: String = "skipped",
        engineInitializeDryRunSkipReason: String? = null,
        engineInitializeDryRunExceptionClass: String? = null,
        engineInitializeDryRunExceptionMessage: String? = null,
        engineInitializeDryRunRootCause: String? = null,
        engineInitializeDryRunCauseChain: String? = null,
        engineInitializeDryRunUnsatisfiedLinkErrorDetected: Boolean? = null,
        engineInitializeDryRunNoUsableDispatchRuntimeDetected: Boolean? = null,
        engineInitializeDryRunSymbolMismatchDetected: Boolean? = null,
        engineInitializeDryRunElapsedMs: Long? = null,
        galleryStackExpectedBuildIdMatch: Boolean? = true,
        currentFlavor: String = "npuExperiment",
        applicationId: String = "io.github.ninbyo02.lami.npu",
    ): AcceleratorProbeSnapshot =
        AcceleratorProbeSnapshot(
            deviceManufacturer = "nubia",
            deviceModel = "NX733J",
            deviceBoard = "pineapple",
            androidSdk = 36,
            supportedAbis = listOf("arm64-v8a"),
            cpuCoreCount = 8,
            cpuAbi = "arm64-v8a",
            gpuVendor = "Qualcomm",
            gpuRenderer = "Adreno",
            gpuVersion = "OpenGL ES",
            nnapiAvailable = true,
            nnapiDeprecatedWarning = true,
            nnapiDevices = emptyList(),
            probeSource = "test",
            currentFlavor = currentFlavor,
            applicationId = applicationId,
            dispatchNativeLibraryDir = "/data/app/lib/arm64",
            dispatchNativeLibraryDirExists = true,
            dispatchRuntimePresentInFlavor = true,
            dispatchRuntimeFilePath = "/data/app/lib/arm64/libLiteRtDispatch_Qualcomm.so",
            dispatchRuntimeBuildId = "dispatch-build-id",
            dispatchRuntimeSha256 = "sha256",
            dispatchRuntimeExpectedSha256Match = true,
            dispatchRuntimeAbiCompatibility = "compatible",
            liteRtBuildId = "litert-build-id",
            liteRtLmJniBuildId = "litertlm-jni-build-id",
            galleryStackExpectedBuildIdMatch = galleryStackExpectedBuildIdMatch,
            customStackExpectedBuildIdMatch = true,
            backendNpuInstantiateConstructor = "Backend.NPU(String)",
            backendNpuInstantiateResult = backendNpuInstantiateResult,
            backendNpuInstantiateObjectClass = "com.google.ai.edge.litertlm.Backend\$NPU",
            engineConfigNpuDryBuildSelectedConstructor =
                "EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)",
            engineConfigNpuDryBuildConstructorArgsSummary =
                "arg0:String:modelPath=model-path, arg1:NPU:backend=Backend.NPU, arg2:Backend:visionBackend=null, arg3:Backend:audioBackend=null, arg4:Integer:maxNumTokens=null, arg6:String:cacheDir-or-extraString=cacheDir=/data/user/0/io.github.ninbyo02.lami.npu/files/backend_npu_attach_probe_cache",
            engineConfigNpuDryBuildResult = engineConfigNpuDryBuildResult,
            engineConfigNpuDryBuildCreatedObjectClass = "com.google.ai.edge.litertlm.EngineConfig",
            engineConfigNpuDryBuildBackendGetterResultClass = "com.google.ai.edge.litertlm.Backend\$NPU",
            engineInitializeDryRunExplicitOptIn = engineInitializeDryRunExplicitOptIn,
            engineInitializeDryRunModelPath = "/sdcard/Download/gemma-4-E2B-it_qualcomm_sm8750.litertlm",
            engineInitializeDryRunModelKind = "qualcomm-sm8750-litertlm",
            engineInitializeDryRunModelFileExists = true,
            engineInitializeDryRunModelFileLength = 123L,
            engineInitializeDryRunModelFileCanRead = true,
            engineInitializeDryRunNativeLibraryDir = "/data/app/lib/arm64",
            engineInitializeDryRunBackendNpuObjectClass = "com.google.ai.edge.litertlm.Backend\$NPU",
            engineInitializeDryRunEngineConfigObjectClass = "com.google.ai.edge.litertlm.EngineConfig",
            engineInitializeDryRunSelectedEngineConstructorOrFactory = "Engine(EngineConfig)",
            engineInitializeDryRunSelectedInitializeMethod = "constructor-only",
            engineInitializeDryRunLastStage = "test-stage",
            engineInitializeDryRunInitializeInvoked = engineInitializeDryRunInitializeInvoked,
            engineInitializeDryRunInitializeReturned = engineInitializeDryRunInitializeReturned,
            engineInitializeDryRunInitializeResult = engineInitializeDryRunInitializeResult,
            engineInitializeDryRunSkipReason = engineInitializeDryRunSkipReason,
            engineInitializeDryRunExceptionClass = engineInitializeDryRunExceptionClass,
            engineInitializeDryRunExceptionMessage = engineInitializeDryRunExceptionMessage,
            engineInitializeDryRunRootCause = engineInitializeDryRunRootCause,
            engineInitializeDryRunCauseChain = engineInitializeDryRunCauseChain,
            engineInitializeDryRunUnsatisfiedLinkErrorDetected = engineInitializeDryRunUnsatisfiedLinkErrorDetected,
            engineInitializeDryRunNoUsableDispatchRuntimeDetected = engineInitializeDryRunNoUsableDispatchRuntimeDetected,
            engineInitializeDryRunSymbolMismatchDetected = engineInitializeDryRunSymbolMismatchDetected,
            engineInitializeDryRunElapsedMs = engineInitializeDryRunElapsedMs,
        )
}
