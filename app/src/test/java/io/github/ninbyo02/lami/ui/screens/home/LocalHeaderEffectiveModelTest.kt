package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalHeaderEffectiveModelTest {
    @Test
    fun `generic fallback model overrides selected npu model while running`() {
        val displayName = resolveActiveLocalHeaderModelDisplayName(
            effectiveBackendModelDisplayName = "gemma-4-E2B-it.litertlm",
            automaticNpuRouteSelected = true,
            npuModelDisplayName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
            selectedModelDisplayName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
        )

        assertEquals("gemma-4-E2B-it.litertlm", displayName)
    }

    @Test
    fun `selected npu model is used when no effective override exists`() {
        val displayName = resolveActiveLocalHeaderModelDisplayName(
            effectiveBackendModelDisplayName = null,
            automaticNpuRouteSelected = true,
            npuModelDisplayName = "npu.litertlm",
            selectedModelDisplayName = "generic.litertlm",
        )

        assertEquals("npu.litertlm", displayName)
    }
}
