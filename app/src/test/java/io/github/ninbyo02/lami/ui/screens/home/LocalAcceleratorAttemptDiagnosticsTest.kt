package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
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
        )
    }
}
