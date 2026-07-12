package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInferenceStatsSectionBuilderNpuTest {
    @Test
    fun `NPU stats override DEFAULT only for resident policy diagnostics`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(
                inputTokens = 128,
                outputTokens = 24,
                notes = "backend=NPU; evidence=QNN_HTP_V79_FastRPC_native_diag",
                localSourceSummary = "route_family=npu_standard; backend=NPU; evidence=QNN_HTP_V79_FastRPC_native_diag",
            ),
            displayMode = InferenceStatsDisplayMode.DETAILED,
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
        )

        val details = sections.single { it.title == "詳細" }.items
        assertTrue(details.any {
            it.label == "ローカル常駐方針" && it.value.startsWith("常駐: NPU")
        })
        assertTrue(details.any {
            it.label == "Resident Router dry-run" &&
                it.value.contains("選択予定: NPU / interactive_use_npu")
        })
    }

    @Test
    fun `QNN HTP evidence in source summary is sufficient for NPU diagnostics`() {
        val sections = buildInferenceDetailSections(
            stats = InferenceStats(
                localSourceSummary = "route_family=npu_standard; evidence=QNN_HTP_V79",
            ),
            displayMode = InferenceStatsDisplayMode.DETAILED,
        )

        val details = sections.single { it.title == "詳細" }.items
        assertTrue(details.any {
            it.label == "ローカル常駐方針" && it.value.startsWith("常駐: NPU")
        })
        assertFalse(sections.any { section ->
            section.items.any { it.label == "App/System memory diagnostics" }
        })
    }
}
