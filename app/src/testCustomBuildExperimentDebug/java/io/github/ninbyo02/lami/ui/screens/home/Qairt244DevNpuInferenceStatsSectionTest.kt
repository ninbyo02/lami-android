package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class Qairt244DevNpuInferenceStatsSectionTest {
    @Test
    fun `buildInferenceDetailSections shows qairt244 sm8750 dev npu diagnostics`() {
        val stats = InferenceStats(
            modelName = "qairt244_sm8750_dev_npu",
            localSourceSummary = """
                selected_route=qairt244_sm8750_dev_npu
                resolved_model_basename=gemma-4-E2B-it_qualcomm_sm8750.litertlm
                required_sm8750_model_path=true
                npu_backend=NPU
                npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
                native_max_output_tokens_limit=128
                run_decode_reached=true
                decode_elapsed_ms=400
                max_output_tokens=128
                fallback_used=false
                ui_cleanup_status=success
            """.trimIndent(),
        )

        val sections = buildInferenceDetailSections(
            stats = stats,
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

        val devSection = sections.single { it.title == "DEV診断: qairt244 SM8750 NPU" }
        assertEquals(
            listOf(
                "実験経路" to "qairt244_sm8750_dev_npu",
                "モデル" to "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
                "required_sm8750_model_path" to "true",
                "max_output_tokens" to "128",
                "native_max_output_tokens_limit" to "128",
                "RunDecode到達" to "true",
                "decode_elapsed_ms" to "400",
                "npu_backend" to "NPU",
                "npu_backend_evidence" to "QNN_HTP_V79_FastRPC_native_diag",
                "fallback_used" to "false",
                "UI cleanup status" to "success",
            ),
            devSection.items.map { it.label to it.value },
        )
    }

    @Test
    fun `buildInferenceSummarySections does not mix dev npu decode time into normal speed`() {
        val stats = InferenceStats(
            modelName = "qairt244_sm8750_dev_npu",
            localSourceSummary = """
                selected_route=qairt244_sm8750_dev_npu
                decode_elapsed_ms=400
                max_output_tokens=128
                npu_backend=NPU
                fallback_used=false
            """.trimIndent(),
        )

        val sections = buildInferenceSummarySections(
            stats = stats,
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

        val generationSpeed = sections.single { it.title == "概要" }
            .items.single { it.label == "生成速度" }
        assertEquals("—", generationSpeed.value)
    }
}
