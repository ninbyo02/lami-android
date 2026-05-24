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
                currentFlavor = "npuExperiment",
                dispatchRuntimePresentInFlavor = false,
                dispatchRuntimeSource = "none",
                liteRtBuildId = "80fa0688ac32301185275c903cec97bd",
                liteRtLmJniBuildId = "c2c27170ba409dbd0bc01820fa738580",
                dispatchRuntimeBuildId = null,
                dispatchRuntimeAbiCompatibility = "unknown",
            ),
        )

        val readinessSection = sections.first { it.title == "DEV診断: LiteRT-LM NPU Readiness" }
        assertEquals("qualcomm-sm8750-litertlm", readinessSection.items.first { it.label == "model kind" }.value)
        assertEquals("requires-soc-specific-qualcomm-litertlm-for-sm8750", readinessSection.items.first { it.label == "model requirement" }.value)
        assertEquals("missing", readinessSection.items.first { it.label == "dispatch API status" }.value)
        assertEquals("missing", readinessSection.items.first { it.label == "dispatch API .so" }.value)
        assertEquals("libQnnHtpV79Skel.so, libQnnHtpV79Stub.so", readinessSection.items.first { it.label == "V79 skel/stub candidates" }.value)
        assertEquals("gpu-ok-npu-blocked-dispatch-missing", readinessSection.items.first { it.label == "readiness" }.value)
        assertEquals("gpu", readinessSection.items.first { it.label == "selected path" }.value)
        assertEquals("disabled / blocked", readinessSection.items.first { it.label == "NPU apply status" }.value)
        assertTrue(readinessSection.items.first { it.label == "next action" }.value.contains("scripts/check_litert_npu_dispatch.sh"))

        val dispatchCompatibilitySection = sections.first { it.title == "DEV診断: Dispatch Runtime Compatibility" }
        assertEquals("npuExperiment", dispatchCompatibilitySection.items.first { it.label == "current flavor" }.value)
        assertEquals("false", dispatchCompatibilitySection.items.first { it.label == "dispatch runtime present in flavor" }.value)
        assertEquals("unknown", dispatchCompatibilitySection.items.first { it.label == "ABI compatibility" }.value)
    }

    @Test
    fun `developer diagnostics show backend npu instantiate probe as skipped without changing gpu path`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttempted = false,
                qnnNpuSelectedPath = "gpu",
                backendNpuInstantiateProbeEnabled = false,
                backendNpuInstantiateProbeSkipReason = "not-npuExperiment-flavor",
                backendNpuInstantiateNativeLibraryDirArgument = "/data/app/lib/arm64",
                backendNpuInstantiateConstructor = "Backend.NPU(String)",
                backendNpuInstantiateResult = "skipped",
                backendNpuInstantiateWarning = "instantiate-only; object not passed to engine; no inference",
            ),
        )

        val instantiateSection = sections.first { it.title == "DEV診断: Backend.NPU Instantiate Probe" }
        assertEquals("false", instantiateSection.items.first { it.label == "enabled" }.value)
        assertEquals("not-npuExperiment-flavor", instantiateSection.items.first { it.label == "reason if skipped" }.value)
        assertEquals("Backend.NPU(String)", instantiateSection.items.first { it.label == "constructor" }.value)
        assertEquals("skipped", instantiateSection.items.first { it.label == "instantiate result" }.value)
        assertTrue(instantiateSection.items.first { it.label == "warning" }.value.contains("object not passed to engine"))

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("no", devSection.items.first { it.label == "QNN/NPU試行" }.value)
        assertEquals("gpu", devSection.items.first { it.label == "QNN/NPU selectedPath" }.value)
    }

    @Test
    fun `developer diagnostics show backend npu attach dry run without changing gpu path`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttempted = false,
                qnnNpuSelectedPath = "gpu",
                backendNpuAttachDryRunEnabled = true,
                backendNpuAttachDryRunNpuObjectClass = "com.google.ai.edge.litertlm.Backend\$NPU",
                backendNpuAttachDryRunTargetBuilderCandidates = listOf("EngineConfig"),
                backendNpuAttachDryRunSetterCandidates = listOf("EngineConfig.EngineConfig.setBackend(Backend): EngineConfig"),
                backendNpuAttachDryRunSelectedSetter = "EngineConfig.EngineConfig.setBackend(Backend): EngineConfig",
                backendNpuAttachDryRunSetterInvokeResult = "success",
                backendNpuAttachDryRunBuildInvoked = "no",
                backendNpuAttachDryRunBuildResult = "skipped-build-not-invoked-safety",
                backendNpuAttachDryRunWarning = "attach-dry-run only; no Engine; no Conversation; no inference",
                backendNpuAttachDryRunNote = "This setter belongs to MediaPipe LlmInference.Backend enum path and is not assignable from LiteRT-LM Backend.NPU.",
            ),
        )

        val attachDryRunSection = sections.first { it.title == "DEV診断: Backend.NPU Attach Dry-Run Probe" }
        assertEquals("true", attachDryRunSection.items.first { it.label == "enabled" }.value)
        assertEquals("EngineConfig", attachDryRunSection.items.first { it.label == "target builder candidates" }.value)
        assertEquals("success", attachDryRunSection.items.first { it.label == "setter invoke result" }.value)
        assertEquals("no", attachDryRunSection.items.first { it.label == "build invoked" }.value)
        assertTrue(attachDryRunSection.items.first { it.label == "warning" }.value.contains("no Engine"))
        assertTrue(attachDryRunSection.items.first { it.label == "note" }.value.contains("MediaPipe LlmInference.Backend enum path"))

        val devSection = sections.first { it.title == "DEV診断" }
        assertEquals("no", devSection.items.first { it.label == "QNN/NPU試行" }.value)
        assertEquals("gpu", devSection.items.first { it.label == "QNN/NPU selectedPath" }.value)
    }

    @Test
    fun `developer diagnostics show attach dry run method not found as non inference result`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttempted = false,
                qnnNpuSelectedPath = "gpu",
                backendNpuAttachDryRunEnabled = true,
                backendNpuAttachDryRunNpuObjectClass = "com.google.ai.edge.litertlm.Backend\$NPU",
                backendNpuAttachDryRunTargetBuilderCandidates = listOf("EngineConfig:no-instantiable-builder"),
                backendNpuAttachDryRunSetterInvokeResult = "method-not-found",
                backendNpuAttachDryRunBuildInvoked = "no",
                backendNpuAttachDryRunBuildResult = "skipped",
                backendNpuAttachDryRunCauseChain = "NoSuchMethodException:setBackend",
            ),
        )

        val attachDryRunSection = sections.first { it.title == "DEV診断: Backend.NPU Attach Dry-Run Probe" }
        assertEquals("method-not-found", attachDryRunSection.items.first { it.label == "setter invoke result" }.value)
        assertEquals("NoSuchMethodException:setBackend", attachDryRunSection.items.first { it.label == "cause chain" }.value)
        assertEquals("no", attachDryRunSection.items.first { it.label == "build invoked" }.value)
    }

    @Test
    fun `developer diagnostics show litert lm api inventory and engineconfig candidate`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttempted = false,
                qnnNpuSelectedPath = "gpu",
                liteRtLmNpuApiInventoryEnabled = true,
                liteRtLmNpuApiClassInventory = listOf("com.google.ai.edge.litertlm.EngineConfig: found"),
                liteRtLmNpuApiAssignability = listOf("Backend base class <- Backend.NPU object class: true"),
                engineConfigConstructorInventory = listOf("EngineConfig(String, Backend) count=2 defaultMarker=false modelPathString=true backendParamIndexes=1"),
                engineConfigBackendPropertyInventory = listOf("EngineConfig.getBackend(): Backend"),
                engineConfigCopyMethodInventory = listOf("EngineConfig.copy(String, Backend): EngineConfig"),
                engineConfigComponentMethodInventory = listOf("EngineConfig.component1(): String"),
            ),
        )

        val inventorySection = sections.first { it.title == "DEV診断: LiteRT-LM NPU API Inventory" }
        assertEquals("true", inventorySection.items.first { it.label == "enabled" }.value)
        assertTrue(inventorySection.items.first { it.label == "assignability" }.value.contains("Backend base class"))
        assertTrue(inventorySection.items.first { it.label == "EngineConfig constructors" }.value.contains("backendParamIndexes=1"))
    }

    @Test
    fun `developer diagnostics show engineconfig dry build and connection candidate`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttempted = false,
                qnnNpuSelectedPath = "gpu",
                engineConfigNpuDryBuildEnabled = true,
                engineConfigNpuDryBuildSelectedConstructor = "EngineConfig(String, Backend)",
                engineConfigNpuDryBuildConstructorArgsSummary = "arg0:String=dummy-model-path, arg1:Backend=Backend.NPU",
                engineConfigNpuDryBuildNpuBackendObjectClass = "com.google.ai.edge.litertlm.Backend\$NPU",
                engineConfigNpuDryBuildResult = "success",
                engineConfigNpuDryBuildCreatedObjectClass = "com.google.ai.edge.litertlm.EngineConfig",
                engineConfigNpuDryBuildBackendGetterResultClass = "com.google.ai.edge.litertlm.Backend\$NPU",
                engineConfigNpuDryBuildWarning = "config-only; not passed to Engine; no inference",
                backendNpuConnectionPreferredBackendEnumPath = "incompatible",
                backendNpuConnectionPreferredBackendEnumReason = "MediaPipe LlmInference preferredBackend setter does not accept LiteRT-LM Backend.NPU.",
                backendNpuConnectionEngineConfigBackendPath = "candidate",
                backendNpuConnectionEngineInitializePath = "not attempted",
                backendNpuConnectionRecommendedNextPhase = "next: isolated Engine.initialize dry-run only, no generate",
            ),
        )

        val dryBuildSection = sections.first { it.title == "DEV診断: EngineConfig NPU Dry-Build Probe" }
        assertEquals("success", dryBuildSection.items.first { it.label == "result" }.value)
        assertTrue(dryBuildSection.items.first { it.label == "warning" }.value.contains("not passed to Engine"))

        val candidateSection = sections.first { it.title == "DEV診断: Backend.NPU Connection Candidate" }
        assertEquals("incompatible", candidateSection.items.first { it.label == "preferredBackend enum path" }.value)
        assertEquals("candidate", candidateSection.items.first { it.label == "EngineConfig backend path" }.value)
        assertEquals("not attempted", candidateSection.items.first { it.label == "Engine initialize path" }.value)
    }

    @Test
    fun `developer diagnostics show inventory skipped for standard flavor`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "gemma-4-E2B-it.litertlm"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            acceleratorProbeSnapshot = acceleratorSnapshot(
                qnnNpuAttempted = false,
                qnnNpuSelectedPath = "gpu",
                liteRtLmNpuApiInventoryEnabled = false,
                liteRtLmNpuApiInventorySkipReason = "not-npuExperiment-flavor",
                engineConfigNpuDryBuildEnabled = false,
                engineConfigNpuDryBuildSkipReason = "not-npuExperiment-flavor",
                engineConfigNpuDryBuildResult = "skipped",
            ),
        )

        val inventorySection = sections.first { it.title == "DEV診断: LiteRT-LM NPU API Inventory" }
        assertEquals("false", inventorySection.items.first { it.label == "enabled" }.value)
        assertEquals("not-npuExperiment-flavor", inventorySection.items.first { it.label == "skipped reason" }.value)

        val dryBuildSection = sections.first { it.title == "DEV診断: EngineConfig NPU Dry-Build Probe" }
        assertEquals("false", dryBuildSection.items.first { it.label == "enabled" }.value)
        assertEquals("skipped", dryBuildSection.items.first { it.label == "result" }.value)
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
        assertEquals("found", readinessSection.items.first { it.label == "dispatch API status" }.value)
        assertEquals("found-exact-libLiteRtDispatch_Qualcomm-so", readinessSection.items.first { it.label == "dispatch API detail status" }.value)
        assertEquals("true", readinessSection.items.first { it.label == "dispatch API exact match" }.value)
        assertEquals("npu-prerequisites-present-probe-only", readinessSection.items.first { it.label == "readiness" }.value)
        assertEquals("npu-probe-only", readinessSection.items.first { it.label == "selected path" }.value)
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
        assertEquals("found", readinessSection.items.first { it.label == "dispatch API status" }.value)
        assertEquals("found-dispatch-candidate", readinessSection.items.first { it.label == "dispatch API detail status" }.value)
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
        currentFlavor: String? = null,
        dispatchRuntimePresentInFlavor: Boolean? = null,
        dispatchRuntimeSource: String? = null,
        liteRtBuildId: String? = null,
        liteRtLmJniBuildId: String? = null,
        dispatchRuntimeBuildId: String? = null,
        dispatchRuntimeAbiCompatibility: String? = null,
        backendNpuInstantiateProbeEnabled: Boolean? = null,
        backendNpuInstantiateProbeSkipReason: String? = null,
        backendNpuInstantiateNativeLibraryDirArgument: String? = null,
        backendNpuInstantiateConstructor: String? = null,
        backendNpuInstantiateResult: String? = null,
        backendNpuInstantiateWarning: String? = null,
        backendNpuAttachDryRunEnabled: Boolean? = null,
        backendNpuAttachDryRunSkipReason: String? = null,
        backendNpuAttachDryRunNpuObjectClass: String? = null,
        backendNpuAttachDryRunTargetBuilderCandidates: List<String> = emptyList(),
        backendNpuAttachDryRunSetterCandidates: List<String> = emptyList(),
        backendNpuAttachDryRunSelectedSetter: String? = null,
        backendNpuAttachDryRunSetterInvokeResult: String? = null,
        backendNpuAttachDryRunBuildInvoked: String? = null,
        backendNpuAttachDryRunBuildResult: String? = null,
        backendNpuAttachDryRunCauseChain: String? = null,
        backendNpuAttachDryRunWarning: String? = null,
        backendNpuAttachDryRunNote: String? = null,
        liteRtLmNpuApiInventoryEnabled: Boolean? = null,
        liteRtLmNpuApiInventorySkipReason: String? = null,
        liteRtLmNpuApiClassInventory: List<String> = emptyList(),
        liteRtLmNpuApiAssignability: List<String> = emptyList(),
        engineConfigConstructorInventory: List<String> = emptyList(),
        engineConfigBackendPropertyInventory: List<String> = emptyList(),
        engineConfigCopyMethodInventory: List<String> = emptyList(),
        engineConfigComponentMethodInventory: List<String> = emptyList(),
        engineConfigNpuDryBuildEnabled: Boolean? = null,
        engineConfigNpuDryBuildSkipReason: String? = null,
        engineConfigNpuDryBuildSelectedConstructor: String? = null,
        engineConfigNpuDryBuildConstructorArgsSummary: String? = null,
        engineConfigNpuDryBuildNpuBackendObjectClass: String? = null,
        engineConfigNpuDryBuildResult: String? = null,
        engineConfigNpuDryBuildCreatedObjectClass: String? = null,
        engineConfigNpuDryBuildBackendGetterResultClass: String? = null,
        engineConfigNpuDryBuildWarning: String? = null,
        backendNpuConnectionPreferredBackendEnumPath: String? = null,
        backendNpuConnectionPreferredBackendEnumReason: String? = null,
        backendNpuConnectionEngineConfigBackendPath: String? = null,
        backendNpuConnectionEngineInitializePath: String? = null,
        backendNpuConnectionRecommendedNextPhase: String? = null,
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
            currentFlavor = currentFlavor,
            dispatchRuntimePresentInFlavor = dispatchRuntimePresentInFlavor,
            dispatchRuntimeSource = dispatchRuntimeSource,
            liteRtBuildId = liteRtBuildId,
            liteRtLmJniBuildId = liteRtLmJniBuildId,
            dispatchRuntimeBuildId = dispatchRuntimeBuildId,
            dispatchRuntimeAbiCompatibility = dispatchRuntimeAbiCompatibility,
            backendNpuInstantiateProbeEnabled = backendNpuInstantiateProbeEnabled,
            backendNpuInstantiateProbeSkipReason = backendNpuInstantiateProbeSkipReason,
            backendNpuInstantiateNativeLibraryDirArgument = backendNpuInstantiateNativeLibraryDirArgument,
            backendNpuInstantiateConstructor = backendNpuInstantiateConstructor,
            backendNpuInstantiateResult = backendNpuInstantiateResult,
            backendNpuInstantiateWarning = backendNpuInstantiateWarning,
            backendNpuAttachDryRunEnabled = backendNpuAttachDryRunEnabled,
            backendNpuAttachDryRunSkipReason = backendNpuAttachDryRunSkipReason,
            backendNpuAttachDryRunNpuObjectClass = backendNpuAttachDryRunNpuObjectClass,
            backendNpuAttachDryRunTargetBuilderCandidates = backendNpuAttachDryRunTargetBuilderCandidates,
            backendNpuAttachDryRunSetterCandidates = backendNpuAttachDryRunSetterCandidates,
            backendNpuAttachDryRunSelectedSetter = backendNpuAttachDryRunSelectedSetter,
            backendNpuAttachDryRunSetterInvokeResult = backendNpuAttachDryRunSetterInvokeResult,
            backendNpuAttachDryRunBuildInvoked = backendNpuAttachDryRunBuildInvoked,
            backendNpuAttachDryRunBuildResult = backendNpuAttachDryRunBuildResult,
            backendNpuAttachDryRunCauseChain = backendNpuAttachDryRunCauseChain,
            backendNpuAttachDryRunWarning = backendNpuAttachDryRunWarning,
            backendNpuAttachDryRunNote = backendNpuAttachDryRunNote,
            liteRtLmNpuApiInventoryEnabled = liteRtLmNpuApiInventoryEnabled,
            liteRtLmNpuApiInventorySkipReason = liteRtLmNpuApiInventorySkipReason,
            liteRtLmNpuApiClassInventory = liteRtLmNpuApiClassInventory,
            liteRtLmNpuApiAssignability = liteRtLmNpuApiAssignability,
            engineConfigConstructorInventory = engineConfigConstructorInventory,
            engineConfigBackendPropertyInventory = engineConfigBackendPropertyInventory,
            engineConfigCopyMethodInventory = engineConfigCopyMethodInventory,
            engineConfigComponentMethodInventory = engineConfigComponentMethodInventory,
            engineConfigNpuDryBuildEnabled = engineConfigNpuDryBuildEnabled,
            engineConfigNpuDryBuildSkipReason = engineConfigNpuDryBuildSkipReason,
            engineConfigNpuDryBuildSelectedConstructor = engineConfigNpuDryBuildSelectedConstructor,
            engineConfigNpuDryBuildConstructorArgsSummary = engineConfigNpuDryBuildConstructorArgsSummary,
            engineConfigNpuDryBuildNpuBackendObjectClass = engineConfigNpuDryBuildNpuBackendObjectClass,
            engineConfigNpuDryBuildResult = engineConfigNpuDryBuildResult,
            engineConfigNpuDryBuildCreatedObjectClass = engineConfigNpuDryBuildCreatedObjectClass,
            engineConfigNpuDryBuildBackendGetterResultClass = engineConfigNpuDryBuildBackendGetterResultClass,
            engineConfigNpuDryBuildWarning = engineConfigNpuDryBuildWarning,
            backendNpuConnectionPreferredBackendEnumPath = backendNpuConnectionPreferredBackendEnumPath,
            backendNpuConnectionPreferredBackendEnumReason = backendNpuConnectionPreferredBackendEnumReason,
            backendNpuConnectionEngineConfigBackendPath = backendNpuConnectionEngineConfigBackendPath,
            backendNpuConnectionEngineInitializePath = backendNpuConnectionEngineInitializePath,
            backendNpuConnectionRecommendedNextPhase = backendNpuConnectionRecommendedNextPhase,
        )
    }
}
