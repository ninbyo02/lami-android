package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualcommNpuModelCompatibilityTest {
    @Test
    fun `generic litertlm model is not treated as qualcomm npu compatible`() {
        val result = probeQualcommNpuModelCompatibility("/models/gemma-4-E4B-it.litertlm")

        assertFalse(result.compatible)
        assertTrue(result.status.contains("missing-soc-specific"))
        assertTrue(result.evidence.contains("markers=none"))
    }

    @Test
    fun `qualcomm npu litertlm model is treated as candidate`() {
        val result = probeQualcommNpuModelCompatibility("/models/gemma3-1b-sm8750-qnn-npu.litertlm")

        assertTrue(result.compatible)
        assertTrue(result.status.contains("candidate-detected"))
        assertTrue(result.evidence.contains("sm8750"))
    }
}
