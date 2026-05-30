package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNpuRouteUiTest {
    @Test
    fun `NPU standard route mode labels match enum choices`() {
        assertEquals("OFF", npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.OFF))
        assertEquals("S1_ONLY", npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.S1_ONLY))
        assertEquals("S2_DB", npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.S2_DB))
        assertEquals("S3_MARKDOWN", npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.S3_MARKDOWN))
        assertEquals(
            "S4A_PSEUDO_STREAMING",
            npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.S4A_PSEUDO_STREAMING),
        )
        assertEquals("FULL", npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.FULL))
    }

    @Test
    fun `NPU standard route has six selectable modes`() {
        assertEquals(6, NpuStandardRouteMode.entries.size)
    }

    @Test
    fun `NPU standard route mode descriptions explain phase scope`() {
        assertEquals("無効", npuStandardRouteModeDescription(NpuStandardRouteMode.OFF))
        assertEquals("NPU応答表示のみ", npuStandardRouteModeDescription(NpuStandardRouteMode.S1_ONLY))
        assertEquals("DB保存まで", npuStandardRouteModeDescription(NpuStandardRouteMode.S2_DB))
        assertEquals("Markdown表示まで", npuStandardRouteModeDescription(NpuStandardRouteMode.S3_MARKDOWN))
        assertEquals(
            "擬似Streamingまで",
            npuStandardRouteModeDescription(NpuStandardRouteMode.S4A_PSEUDO_STREAMING),
        )
        assertEquals("TTSまで", npuStandardRouteModeDescription(NpuStandardRouteMode.FULL))
    }

    @Test
    fun `legacy QAIRT route label is diagnostic and separate from standard route`() {
        assertEquals("Legacy QAIRT244診断", LEGACY_QAIRT244_DIAGNOSTIC_TITLE)
        assertTrue(LEGACY_QAIRT244_DIAGNOSTIC_DESCRIPTION.contains("旧QAIRT診断経路"))
        assertTrue(LEGACY_QAIRT244_DIAGNOSTIC_DESCRIPTION.contains("S1〜S5 NPU標準ルートとは別"))
        assertTrue(LEGACY_QAIRT244_DIAGNOSTIC_DESCRIPTION.contains("通常利用は非推奨"))
    }
}
