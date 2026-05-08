package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferredBackendEngineConfigInstrumentedTest {
    @Test
    fun npuPreferredBackend_appliesLiteRtNpuBackendToEngineConfig() {
        var applyResult: PreferredBackendApplyResult? = null

        val config = buildLiteRtEngineConfig(
            modelPath = "/tmp/model.task",
            cacheDirPath = "/tmp/cache",
            nativeLibraryDir = "/tmp/native-libs",
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.NPU,
            onPreferredBackendApplied = { applyResult = it },
        )

        assertEquals("NPU", config.backend.name)
        assertEquals("NPU", applyResult?.appliedPreferredBackend)
        assertEquals("applied-engine-config", applyResult?.preferredBackendApplyResult)
        assertNull(applyResult?.preferredBackendApplyNotSupportedReason)
    }

    @Test
    fun qualcommQnnNpu_fallsBackToGpuWhenRuntimeLibrariesAreMissing() {
        var applyResult: PreferredBackendApplyResult? = null

        val config = buildLiteRtEngineConfig(
            modelPath = "/tmp/model.task",
            cacheDirPath = "/tmp/cache",
            nativeLibraryDir = "/tmp/native-libs",
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU,
            onPreferredBackendApplied = { applyResult = it },
        )

        assertEquals("GPU", config.backend.name)
        assertEquals("GPU", applyResult?.appliedPreferredBackend)
        assertEquals(
            "fallback-gpu-before-qualcomm-qnn-npu-prerequisites-missing",
            applyResult?.preferredBackendApplyResult,
        )
        assertTrue(applyResult?.preferredBackendApplyNotSupportedReason.orEmpty().contains("qnn-runtime-libs"))
    }
}
