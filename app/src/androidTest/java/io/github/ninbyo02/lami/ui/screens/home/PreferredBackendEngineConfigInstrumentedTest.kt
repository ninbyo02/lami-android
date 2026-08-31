package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferredBackendEngineConfigInstrumentedTest {
    @Test
    fun gpuMaxTokens4096Experiment_setsNormalGpuEngineConfigMaxTokens() {
        val key = "debug.lami.gpu_experiment_mode"
        val previous = System.getProperty(key)
        System.setProperty(key, GPU_EXPERIMENT_MODE_MAX_TOKENS_4096)
        try {
            val config = buildLiteRtEngineConfig(
                modelPath = "/data/user/0/io.github.ninbyo02.lami/files/gemma-4-E2B-it.litertlm",
                cacheDirPath = "/tmp/cache",
                nativeLibraryDir = "/tmp/native-libs",
                preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
            )

            assertEquals("GPU", config.backend.name)
            assertEquals(4096, config.maxNumTokens)
            assertNull(config.cacheDir)
            assertNull(config.visionBackend)
            assertNull(config.audioBackend)
        } finally {
            if (previous == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, previous)
            }
        }
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
