package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAcceleratorAttemptDiagnosticsTest {
    @Test
    fun `developer diagnostics include qnn npu fallback attempt fields`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttemptRequested = "auto",
                qnnNpuAttempted = false,
                qnnNpuAvailable = "unsupported",
                qnnNpuSelectedPath = "gpu",
                qnnNpuFallbackPath = "gpu",
                qnnNpuAttemptStage = "prerequisite-probe",
                qnnNpuAttemptErrorClass = "MissingPrerequisite",
                qnnNpuAttemptErrorMessage = "qnn-runtime-libs,dispatch-api-so",
                qnnNpuAttemptEvidence = listOf("soc=QTI/SM8750", "missing=qnn-runtime-libs,dispatch-api-so"),
            ),
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("auto", devSection.items.first { it.label == "QNN/NPU要求" }.value)
        assertEquals("no", devSection.items.first { it.label == "QNN/NPU試行" }.value)
        assertEquals("blocked", devSection.items.first { it.label == "Lami runtime QNN availability" }.value)
        assertEquals("app-packaged QNN runtime libs missing, dispatch API .so missing", devSection.items.first { it.label == "Blocked reason" }.value)
        assertEquals("gpu", devSection.items.first { it.label == "QNN/NPU selectedPath" }.value)
        assertEquals("MissingPrerequisite", devSection.items.first { it.label == "QNN/NPU errorClass" }.value)
        assertTrue(devSection.items.first { it.label == "QNN/NPU evidence" }.value.contains("SM8750"))
    }

    @Test
    fun `missing qnn reflection evidence does not crash diagnostics`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttemptStage = "class-probe",
                qnnNpuAttemptErrorClass = "ClassNotFoundException",
                qnnNpuAttemptErrorMessage = "Backend.NPU",
                qnnNpuAttemptEvidence = listOf("missingClass=Backend.NPU"),
            ),
        )

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("class-probe", devSection.items.first { it.label == "QNN/NPU stage" }.value)
        assertEquals("ClassNotFoundException", devSection.items.first { it.label == "QNN/NPU errorClass" }.value)
        assertEquals("Backend.NPU", devSection.items.first { it.label == "QNN/NPU errorMessage" }.value)
    }

    @Test
    fun `normal detail mode does not expose dev diagnostics`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DETAILED,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttemptStage = "prerequisite-probe",
                qnnNpuAttemptErrorClass = "MissingPrerequisite",
            ),
        )

        assertNull(sections.firstOrNull { it.title == "DEV診断" })
    }

    @Test
    fun `developer diagnostics infer high confidence qnn npu when npu is applied with qnn evidence`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                requestedPreferredBackend = "NPU",
                appliedPreferredBackend = "NPU",
                preferredBackendApplyResult = "applied-engine-config",
                preferredBackendHookReached = true,
            ),
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttemptRequested = "auto",
                qnnNpuAttempted = true,
                qnnNpuAvailable = "available",
                qnnNpuSelectedPath = "qualcomm-qnn-npu-candidate",
                qnnNpuAttemptEvidence = listOf("runtime=present", "backendNpu=present"),
            ),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        val devSummarySection = sections.first { it.title == "DEV診断サマリー" }
        assertEquals("qnn-npu-likely / high", devSection.items.first { it.label == "実行経路推定" }.value)
        assertEquals("qnn-npu-likely / high", devSummarySection.items.first { it.label == "推定実行先" }.value)
        assertEquals("NPU", devSummarySection.items.first { it.label == "Requested preferredBackend" }.value)
        assertEquals("NPU", devSummarySection.items.first { it.label == "Applied backend" }.value)
        assertEquals("applied-engine-config", devSummarySection.items.first { it.label == "PreferredBackend apply result" }.value)
        assertEquals("true", devSummarySection.items.first { it.label == "PreferredBackend hook" }.value)
        assertEquals("auto", devSummarySection.items.first { it.label == "QNN/NPU要求" }.value)
        assertEquals("yes", devSummarySection.items.first { it.label == "QNN/NPU試行" }.value)
        assertEquals("available", devSummarySection.items.first { it.label == "Lami runtime QNN availability" }.value)
        assertEquals("qualcomm-qnn-npu-candidate", devSummarySection.items.first { it.label == "QNN/NPU selectedPath" }.value)
        assertTrue(devSection.items.first { it.label == "推定理由" }.value.contains("preferredBackend applied NPU"))
    }

    @Test
    fun `developer diagnostics summarize qualcomm qnn prerequisite fallback as gpu fallback`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = LocalInferenceTrace(
                requestedPreferredBackend = "QUALCOMM_QNN_NPU",
                appliedPreferredBackend = "GPU",
                preferredBackendApplyResult = "fallback-gpu-before-qualcomm-qnn-npu-prerequisites-missing",
                preferredBackendHookReached = true,
                preferredBackendHookSource = "holder-acquire-engine-config",
                preferredBackendHookMissingReason = "holder-existing-engine",
            ),
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttemptRequested = "auto",
                qnnNpuAttempted = false,
                qnnNpuAvailable = "unsupported",
                qnnNpuSelectedPath = "gpu",
                qnnNpuFallbackPath = "gpu",
                qnnNpuAttemptStage = "prerequisite-probe",
                qnnNpuAttemptErrorClass = "MissingPrerequisite",
                qnnNpuAttemptErrorMessage = "qnn-runtime-libs,backend-npu-api",
                qnnNpuAttemptEvidence = listOf("missing=qnn-runtime-libs", "backendNpu=missing"),
                npuVendorRuntimeLibraryStatus = "missing:libQnnHtp.so",
                npuDispatchLibraryStatus = "missing:libLiteRtDispatch.so",
                npuReadinessSummary = "missing=qnn-runtime-libs,backend-npu-api",
                backendNpuProbeHint = "not-detected",
                backendNpuClassCandidates = emptyList(),
                backendNpuMethodCandidates = listOf("setPreferredBackend(Backend)"),
            ),
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU,
        )

        val devSection = sections.first { it.title == "DEV診断" }
        val devSummarySection = sections.first { it.title == "DEV診断サマリー" }
        assertEquals("gpu-fallback / high", devSection.items.first { it.label == "実行経路推定" }.value)
        assertEquals("gpu-fallback / high", devSummarySection.items.first { it.label == "推定実行先" }.value)
        assertEquals("QUALCOMM_QNN_NPU", devSummarySection.items.first { it.label == "Requested preferredBackend" }.value)
        assertEquals("GPU fallback", devSummarySection.items.first { it.label == "Applied backend" }.value)
        assertEquals("prerequisite-probe", devSummarySection.items.first { it.label == "QNN/NPU stage" }.value)
        assertEquals("MissingPrerequisite", devSummarySection.items.first { it.label == "QNN/NPU errorClass" }.value)
        assertEquals("qnn-runtime-libs,backend-npu-api", devSummarySection.items.first { it.label == "QNN/NPU errorMessage" }.value)
        assertEquals("missing=qnn-runtime-libs / backendNpu=missing", devSummarySection.items.first { it.label == "QNN/NPU evidence" }.value)
        assertEquals("missing:libQnnHtp.so", devSummarySection.items.first { it.label == "LiteRT-LM NPU runtime lib status" }.value)
        assertEquals("missing:libLiteRtDispatch.so", devSummarySection.items.first { it.label == "LiteRT-LM NPU dispatch lib status" }.value)
        assertEquals("blocked", devSummarySection.items.first { it.label == "Lami LiteRT-LM NPU readiness" }.value)
        assertEquals("app-packaged QNN runtime libs missing, Backend.NPU API missing, dispatch API .so missing", devSummarySection.items.first { it.label == "Blocked reason" }.value)
        assertEquals("not-detected", devSummarySection.items.first { it.label == "Backend NPU probe hint" }.value)
        assertEquals("none/unknown", devSummarySection.items.first { it.label == "Backend NPU class candidates" }.value)
        assertEquals("setPreferredBackend(Backend)", devSummarySection.items.first { it.label == "Backend NPU method candidates" }.value)
        assertTrue(devSummarySection.items.none { it.label == "PreferredBackend hook missing" })
    }

    @Test
    fun `developer diagnostics show litert lm npu readiness without enabling npu`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttemptRequested = "auto",
                qnnNpuAttempted = false,
                qnnNpuAvailable = "unsupported",
                qnnNpuSelectedPath = "gpu",
                qnnNpuFallbackPath = "gpu",
                npuSocManufacturer = "QTI",
                npuSocModel = "SM8750",
                npuNativeLibraryDir = "/data/app/lib/arm64",
                npuNativeLibraryDirExists = true,
                npuVendorRuntimeLibraryStatus = "candidate-detected-qairt",
                npuDispatchLibraryStatus = "missing-dispatch-api-so-candidate",
                npuQnnRuntimeCandidates = listOf("libQnnSystem.so", "libQnnHtp.so", "libQnnHtpPrepare.so"),
                npuHtpSkelStubCandidates = listOf("libQnnHtpV79Skel.so", "libQnnHtpV79Stub.so"),
                npuV79SkelStubCandidates = listOf("libQnnHtpV79Skel.so", "libQnnHtpV79Stub.so"),
                backendNpuClassCandidates = listOf("Backend.NPU"),
                npuConstructorAvailable = true,
                npuStringConstructorAvailable = true,
                externalQairtDspCore = "Hexagon Architecture V79",
            ),
        )

        val readinessSection = sections.first { it.title == "DEV診断: LiteRT-LM NPU Readiness" }
        assertEquals("qualcomm-sm8750-litertlm", readinessSection.items.first { it.label == "model kind" }.value)
        assertEquals("requires-soc-specific-qualcomm-litertlm-for-sm8750", readinessSection.items.first { it.label == "model requirement" }.value)
        assertEquals("missing", readinessSection.items.first { it.label == "dispatch API status" }.value)
        assertEquals("libQnnHtpV79Skel.so, libQnnHtpV79Stub.so", readinessSection.items.first { it.label == "V79 skel/stub candidates" }.value)
        assertEquals("blocked-dispatch-api-so-missing", readinessSection.items.first { it.label == "readiness" }.value)
        assertEquals("gpu", readinessSection.items.first { it.label == "selected path" }.value)
        assertEquals("disabled / blocked", readinessSection.items.first { it.label == "NPU apply status" }.value)
    }

    @Test
    fun `developer diagnostics classify exact dispatch candidate`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                npuSocManufacturer = "QTI",
                npuSocModel = "SM8750",
                npuNativeLibraryDir = "/data/app/lib/arm64",
                npuNativeLibraryDirExists = true,
                npuDispatchApiCandidates = listOf("libLiteRtDispatch_Qualcomm.so"),
                npuDispatchApiExactMatch = true,
                npuDispatchApiSelectedCandidate = "libLiteRtDispatch_Qualcomm.so",
                npuDispatchLibraryStatus = "found-exact-libLiteRtDispatch_Qualcomm-so",
                npuQnnRuntimeCandidates = listOf("libQnnSystem.so", "libQnnHtp.so", "libQnnHtpPrepare.so"),
                npuHtpSkelStubCandidates = listOf("libQnnHtpV79Skel.so", "libQnnHtpV79Stub.so"),
                npuV79SkelStubCandidates = listOf("libQnnHtpV79Skel.so", "libQnnHtpV79Stub.so"),
                backendNpuClassCandidates = listOf("Backend.NPU"),
                npuConstructorAvailable = true,
                npuStringConstructorAvailable = true,
            ),
        )

        val readinessSection = sections.first { it.title == "DEV診断: LiteRT-LM NPU Readiness" }
        assertEquals("found-exact-libLiteRtDispatch_Qualcomm-so", readinessSection.items.first { it.label == "dispatch API status" }.value)
        assertEquals("true", readinessSection.items.first { it.label == "dispatch API exact match" }.value)
        assertEquals("ready-but-disabled-cli-proof-required", readinessSection.items.first { it.label == "readiness" }.value)
        assertEquals("gpu", readinessSection.items.first { it.label == "selected path" }.value)
    }

    @Test
    fun `developer diagnostics classify non exact dispatch candidate`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it_qnn_sm8750.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                npuNativeLibraryDir = "/data/app/lib/arm64",
                npuNativeLibraryDirExists = true,
                npuDispatchApiCandidates = listOf("libVendorDispatchQnn.so"),
                npuDispatchApiExactMatch = false,
                npuDispatchApiSelectedCandidate = "libVendorDispatchQnn.so",
                npuDispatchLibraryStatus = "found-dispatch-candidate",
                npuQnnRuntimeCandidates = listOf("libQnnSystem.so", "libQnnHtp.so", "libQnnHtpPrepare.so"),
                npuHtpSkelStubCandidates = listOf("libQnnHtpV79Skel.so", "libQnnHtpV79Stub.so"),
                backendNpuClassCandidates = listOf("Backend.NPU"),
                npuStringConstructorAvailable = true,
            ),
        )

        val readinessSection = sections.first { it.title == "DEV診断: LiteRT-LM NPU Readiness" }
        assertEquals("found-dispatch-candidate", readinessSection.items.first { it.label == "dispatch API status" }.value)
        assertEquals("false", readinessSection.items.first { it.label == "dispatch API exact match" }.value)
        assertEquals("libVendorDispatchQnn.so", readinessSection.items.first { it.label == "dispatch API selected candidate" }.value)
    }

    @Test
    fun `developer diagnostics show model compatibility hints`() {
        val genericSections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(),
        )
        val qualcommSections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(),
        )

        val genericReadiness = genericSections.first { it.title == "DEV診断: LiteRT-LM NPU Readiness" }
        val qualcommReadiness = qualcommSections.first { it.title == "DEV診断: LiteRT-LM NPU Readiness" }
        assertEquals("generic-gpu-compatible", genericReadiness.items.first { it.label == "model npu compatibility hint" }.value)
        assertEquals("qualcomm-soc-specific-candidate", qualcommReadiness.items.first { it.label == "model npu compatibility hint" }.value)
        assertEquals("requires-soc-specific-qualcomm-litertlm", genericReadiness.items.first { it.label == "model npu blocker" }.value)
    }

    private fun acceleratorSnapshot(
        qnnNpuAttemptRequested: String? = null,
        qnnNpuAttempted: Boolean = false,
        qnnNpuAvailable: String? = null,
        qnnNpuSelectedPath: String? = null,
        qnnNpuFallbackPath: String? = null,
        qnnNpuAttemptStage: String? = null,
        qnnNpuAttemptErrorClass: String? = null,
        qnnNpuAttemptErrorMessage: String? = null,
        qnnNpuAttemptEvidence: List<String> = emptyList(),
        npuVendorRuntimeLibraryStatus: String? = null,
        npuDispatchLibraryStatus: String? = null,
        npuReadinessSummary: String? = null,
        npuSocManufacturer: String? = null,
        npuSocModel: String? = null,
        npuNativeLibraryDir: String? = null,
        npuNativeLibraryDirExists: Boolean? = null,
        npuDispatchApiCandidates: List<String> = emptyList(),
        npuDispatchApiExactMatch: Boolean? = null,
        npuDispatchApiSelectedCandidate: String? = null,
        npuDispatchApiSearchDir: String? = null,
        npuDispatchApiSearchError: String? = null,
        npuQnnRuntimeCandidates: List<String> = emptyList(),
        npuHtpSkelStubCandidates: List<String> = emptyList(),
        npuV79SkelStubCandidates: List<String> = emptyList(),
        backendNpuProbeHint: String? = null,
        backendNpuClassCandidates: List<String> = emptyList(),
        backendNpuMethodCandidates: List<String> = emptyList(),
        npuConstructorAvailable: Boolean = false,
        npuStringConstructorAvailable: Boolean = false,
        externalQairtDspCore: String = "unknown",
    ): AcceleratorProbeSnapshot {
        return AcceleratorProbeSnapshot(
            deviceManufacturer = "nubia",
            deviceModel = "NX733J",
            deviceBoard = "kalama",
            androidSdk = 35,
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
            qnnNpuAttemptRequested = qnnNpuAttemptRequested,
            qnnNpuAttempted = qnnNpuAttempted,
            qnnNpuAvailable = qnnNpuAvailable,
            qnnNpuSelectedPath = qnnNpuSelectedPath,
            qnnNpuFallbackPath = qnnNpuFallbackPath,
            qnnNpuAttemptStage = qnnNpuAttemptStage,
            qnnNpuAttemptErrorClass = qnnNpuAttemptErrorClass,
            qnnNpuAttemptErrorMessage = qnnNpuAttemptErrorMessage,
            qnnNpuAttemptEvidence = qnnNpuAttemptEvidence,
            npuSocManufacturer = npuSocManufacturer,
            npuSocModel = npuSocModel,
            npuNativeLibraryDir = npuNativeLibraryDir,
            npuNativeLibraryDirExists = npuNativeLibraryDirExists,
            npuDispatchApiCandidates = npuDispatchApiCandidates,
            npuDispatchApiExactMatch = npuDispatchApiExactMatch,
            npuDispatchApiSelectedCandidate = npuDispatchApiSelectedCandidate,
            npuDispatchApiSearchDir = npuDispatchApiSearchDir,
            npuDispatchApiSearchError = npuDispatchApiSearchError,
            npuQnnRuntimeCandidates = npuQnnRuntimeCandidates,
            npuHtpSkelStubCandidates = npuHtpSkelStubCandidates,
            npuV79SkelStubCandidates = npuV79SkelStubCandidates,
            npuVendorRuntimeLibraryStatus = npuVendorRuntimeLibraryStatus,
            npuDispatchLibraryStatus = npuDispatchLibraryStatus,
            npuReadinessSummary = npuReadinessSummary,
            backendNpuProbeHint = backendNpuProbeHint,
            backendNpuClassCandidates = backendNpuClassCandidates,
            backendNpuMethodCandidates = backendNpuMethodCandidates,
            npuConstructorAvailable = npuConstructorAvailable,
            npuStringConstructorAvailable = npuStringConstructorAvailable,
            externalQairtDspCore = externalQairtDspCore,
        )
    }
}
