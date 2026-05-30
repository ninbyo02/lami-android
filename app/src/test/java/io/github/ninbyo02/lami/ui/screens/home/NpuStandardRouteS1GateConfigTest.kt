package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class NpuStandardRouteS1GateConfigTest {
    @Test
    fun `gate config follows custom build experiment flag`() {
        assertEquals(BuildConfig.CUSTOM_BUILD_EXPERIMENT, NpuStandardRouteS1GateConfig.enabled)
    }
}
