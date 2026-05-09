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
        assertEquals("unsupported", devSection.items.first { it.label == "QNN利用可否" }.value)
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
        assertEquals("NPU", devSummarySection.items.first { it.label == "Applied preferredBackend" }.value)
        assertEquals("applied-engine-config", devSummarySection.items.first { it.label == "PreferredBackend apply result" }.value)
        assertEquals("true", devSummarySection.items.first { it.label == "PreferredBackend hook" }.value)
        assertEquals("auto", devSummarySection.items.first { it.label == "QNN/NPU要求" }.value)
        assertEquals("yes", devSummarySection.items.first { it.label == "QNN/NPU試行" }.value)
        assertEquals("available", devSummarySection.items.first { it.label == "QNN利用可否" }.value)
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
        assertEquals("GPU", devSummarySection.items.first { it.label == "Applied preferredBackend" }.value)
        assertEquals("prerequisite-probe", devSummarySection.items.first { it.label == "QNN/NPU stage" }.value)
        assertEquals("MissingPrerequisite", devSummarySection.items.first { it.label == "QNN/NPU errorClass" }.value)
        assertEquals("qnn-runtime-libs,backend-npu-api", devSummarySection.items.first { it.label == "QNN/NPU errorMessage" }.value)
        assertEquals("missing=qnn-runtime-libs / backendNpu=missing", devSummarySection.items.first { it.label == "QNN/NPU evidence" }.value)
        assertEquals("missing:libQnnHtp.so", devSummarySection.items.first { it.label == "LiteRT-LM NPU runtime lib status" }.value)
        assertEquals("missing:libLiteRtDispatch.so", devSummarySection.items.first { it.label == "LiteRT-LM NPU dispatch lib status" }.value)
        assertEquals("missing=qnn-runtime-libs,backend-npu-api", devSummarySection.items.first { it.label == "LiteRT-LM NPU readiness" }.value)
        assertEquals("not-detected", devSummarySection.items.first { it.label == "Backend NPU probe hint" }.value)
        assertEquals("none/unknown", devSummarySection.items.first { it.label == "Backend NPU class candidates" }.value)
        assertEquals("setPreferredBackend(Backend)", devSummarySection.items.first { it.label == "Backend NPU method candidates" }.value)
        assertTrue(devSummarySection.items.none { it.label == "PreferredBackend hook missing" })
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
        backendNpuProbeHint: String? = null,
        backendNpuClassCandidates: List<String> = emptyList(),
        backendNpuMethodCandidates: List<String> = emptyList(),
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
            npuVendorRuntimeLibraryStatus = npuVendorRuntimeLibraryStatus,
            npuDispatchLibraryStatus = npuDispatchLibraryStatus,
            npuReadinessSummary = npuReadinessSummary,
            backendNpuProbeHint = backendNpuProbeHint,
            backendNpuClassCandidates = backendNpuClassCandidates,
            backendNpuMethodCandidates = backendNpuMethodCandidates,
        )
    }
}
