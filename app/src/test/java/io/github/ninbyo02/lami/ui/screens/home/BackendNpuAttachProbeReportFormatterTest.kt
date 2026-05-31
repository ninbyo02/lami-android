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
        )

        val text = BackendNpuAttachProbeReportFormatter.formatText(snapshot, request)
        val markdown = BackendNpuAttachProbeReportFormatter.formatMarkdown(snapshot, request)

        assertTrue(text.contains("backend_npu_attach_probe_v1"))
        assertTrue(text.contains("model_path=/sdcard/Download/gemma-4-E2B-it_qualcomm_sm8750.litertlm"))
        assertTrue(text.contains("native_library_dir=/data/app/lib/arm64"))
        assertTrue(text.contains("backend_npu_constructor_used=Backend.NPU(String)"))
        assertTrue(text.contains("backend_npu_object_class=com.google.ai.edge.litertlm.Backend\$NPU"))
        assertTrue(text.contains("engineconfig_constructor_factory_used=EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)"))
        assertTrue(text.contains("engineconfig_backend_getter_result=com.google.ai.edge.litertlm.Backend\$NPU"))
        assertTrue(text.contains("engine_initialize_invoked=no"))
        assertTrue(text.contains("engine_initialize_skip_reason=explicit-opt-in-required"))
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
        )
        val request = BackendNpuAttachProbeReportRequest(
            runId = "20260601_120001",
            phase = BackendNpuAttachProbeReportFormatter.PHASE_ENGINE_INITIALIZE,
            engineInitializeOptIn = true,
            processAliveAfterProbe = "alive",
        )

        val text = BackendNpuAttachProbeReportFormatter.formatText(snapshot, request)

        assertTrue(text.contains("engine_initialize_invoked=yes"))
        assertTrue(text.contains("exception_class=java.lang.UnsatisfiedLinkError"))
        assertTrue(text.contains("unsatisfied_link_error_detected=true"))
        assertTrue(text.contains("dispatch_api_load_error_detected=true"))
        assertTrue(text.contains("symbol_mismatch_suspected=true"))
        assertTrue(text.contains("elapsed_ms=1234"))
        assertTrue(text.contains("process_alive_after_probe=alive"))
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
            currentFlavor = "npuExperiment",
            applicationId = "io.github.ninbyo02.lami.npu",
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
            galleryStackExpectedBuildIdMatch = true,
            customStackExpectedBuildIdMatch = true,
            backendNpuInstantiateConstructor = "Backend.NPU(String)",
            backendNpuInstantiateResult = backendNpuInstantiateResult,
            backendNpuInstantiateObjectClass = "com.google.ai.edge.litertlm.Backend\$NPU",
            engineConfigNpuDryBuildSelectedConstructor =
                "EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)",
            engineConfigNpuDryBuildConstructorArgsSummary =
                "String=modelPath, Backend=Backend.NPU, Backend=Backend.GPU, Backend=Backend.CPU",
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
