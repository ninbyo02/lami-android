package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class NpuStandardRoutePersistentProbeRunnerTest {
    @Test
    fun `persistent native evidence is normalized to the standard route contract`() {
        assertEquals(
            NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
            NpuStandardRoutePersistentProbeRunner.normalizeNpuBackendEvidence(
                "QNN_HTP_V79_FastRPC_native_diag_persistent_holder",
            ),
        )
    }

    @Test
    fun `incomplete native evidence is not promoted`() {
        assertEquals(
            "QNN_HTP_only",
            NpuStandardRoutePersistentProbeRunner.normalizeNpuBackendEvidence(
                "QNN_HTP_only",
            ),
        )
    }
}
