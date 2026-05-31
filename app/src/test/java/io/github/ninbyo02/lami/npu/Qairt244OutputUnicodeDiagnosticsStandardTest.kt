package io.github.ninbyo02.lami.npu

import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1Contract
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1Mapper
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1RawResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Qairt244OutputUnicodeDiagnosticsStandardTest {
    @Test
    fun `natural Japanese with Python technical term is classified as natural Japanese`() {
        val fields = Qairt244OutputUnicodeDiagnostics.buildFields(
            "Pythonは学習しやすく、多目的なプログラミング言語です。",
        ).toMap()

        assertEquals("natural_japanese", fields["quality_classification"])
    }

    @Test
    fun `natural Japanese with GPU technical term is classified as natural Japanese`() {
        val fields = Qairt244OutputUnicodeDiagnostics.buildFields(
            "GPUは高速化に使われます。",
        ).toMap()

        assertEquals("natural_japanese", fields["quality_classification"])
    }

    @Test
    fun `non Japanese script output remains mixed language`() {
        val fields = Qairt244OutputUnicodeDiagnostics.buildFields("अच्छे。").toMap()

        assertEquals("mixed_language", fields["quality_classification"])
    }

    @Test
    fun `raw role contamination remains role contamination`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                result = "success",
                success = true,
                rawOutput = "Pythonは便利です。\nユーザー: Pythonについて一言で教えて\nアシスタント: Pythonは便利です。",
                sanitizedOutput = "Pythonは便利です。",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )

        assertFalse(result.successCriteriaMet)
        assertEquals(NpuStandardRouteS1Contract.REASON_RAW_ROLE_CONTAMINATION, result.reason)
        assertEquals(NpuStandardRouteS1Contract.QUALITY_ROLE_CONTAMINATION, result.qualityClassification)
    }
}
