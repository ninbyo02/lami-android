package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class NpuStandardRouteModeTest {
    @Test
    fun `OFF disables all NPU standard route phases`() {
        assertGateMapping(
            mode = NpuStandardRouteMode.OFF,
            s1 = false,
            s2 = false,
            s3 = false,
            s4a = false,
            s5 = false,
        )
    }

    @Test
    fun `S1_ONLY enables only S1`() {
        assertGateMapping(
            mode = NpuStandardRouteMode.S1_ONLY,
            s1 = true,
            s2 = false,
            s3 = false,
            s4a = false,
            s5 = false,
        )
    }

    @Test
    fun `S2_DB enables S1 and S2`() {
        assertGateMapping(
            mode = NpuStandardRouteMode.S2_DB,
            s1 = true,
            s2 = true,
            s3 = false,
            s4a = false,
            s5 = false,
        )
    }

    @Test
    fun `S3_MARKDOWN enables S1 through S3`() {
        assertGateMapping(
            mode = NpuStandardRouteMode.S3_MARKDOWN,
            s1 = true,
            s2 = true,
            s3 = true,
            s4a = false,
            s5 = false,
        )
    }

    @Test
    fun `S4A_PSEUDO_STREAMING enables S1 through S4A`() {
        assertGateMapping(
            mode = NpuStandardRouteMode.S4A_PSEUDO_STREAMING,
            s1 = true,
            s2 = true,
            s3 = true,
            s4a = true,
            s5 = false,
        )
    }

    @Test
    fun `FULL enables S1 through S5`() {
        assertGateMapping(
            mode = NpuStandardRouteMode.FULL,
            s1 = true,
            s2 = true,
            s3 = true,
            s4a = true,
            s5 = true,
        )
    }

    private fun assertGateMapping(
        mode: NpuStandardRouteMode,
        s1: Boolean,
        s2: Boolean,
        s3: Boolean,
        s4a: Boolean,
        s5: Boolean,
    ) {
        assertEquals(s1, mode.isS1Enabled())
        assertEquals(s2, mode.isS2Enabled())
        assertEquals(s3, mode.isS3Enabled())
        assertEquals(s4a, mode.isS4AEnabled())
        assertEquals(s5, mode.isS5Enabled())
    }
}
