package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteMode
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRoutePreferences
import io.github.ninbyo02.lami.ui.screens.home.NPU_STANDARD_ROUTE_MAX_OUTPUT_TOKENS_DATASTORE_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNpuRouteUiTest {
    @Test
    fun `NPU standard route mode labels use user facing text`() {
        assertEquals("OFF", npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.OFF))
        assertEquals("S1 応答表示", npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.S1_ONLY))
        assertEquals("S2 DB保存", npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.S2_DB))
        assertEquals("S3 Markdown", npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.S3_MARKDOWN))
        assertEquals(
            "S4-A 擬似Streaming",
            npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.S4A_PSEUDO_STREAMING),
        )
        assertEquals("FULL TTSまで", npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.FULL))
    }

    @Test
    fun `NPU standard route has six selectable modes`() {
        assertEquals(6, NpuStandardRouteMode.entries.size)
    }

    @Test
    fun `NPU standard route mode descriptions explain phase scope`() {
        assertEquals("無効", npuStandardRouteModeDescription(NpuStandardRouteMode.OFF))
        assertEquals("NPU応答を画面に表示します", npuStandardRouteModeDescription(NpuStandardRouteMode.S1_ONLY))
        assertEquals("応答をDBに保存します", npuStandardRouteModeDescription(NpuStandardRouteMode.S2_DB))
        assertEquals("Markdown表示まで有効にします", npuStandardRouteModeDescription(NpuStandardRouteMode.S3_MARKDOWN))
        assertEquals(
            "擬似Streaming表示まで有効にします",
            npuStandardRouteModeDescription(NpuStandardRouteMode.S4A_PSEUDO_STREAMING),
        )
        assertEquals("TTS読み上げまで有効にします", npuStandardRouteModeDescription(NpuStandardRouteMode.FULL))
    }

    @Test
    fun `NPU standard route DataStore values remain enum names`() {
        assertEquals(
            NpuStandardRouteMode.OFF,
            NpuStandardRoutePreferences.fromDataStoreValue("OFF"),
        )
        assertEquals(
            NpuStandardRouteMode.S1_ONLY,
            NpuStandardRoutePreferences.fromDataStoreValue("S1_ONLY"),
        )
        assertEquals(
            NpuStandardRouteMode.S2_DB,
            NpuStandardRoutePreferences.fromDataStoreValue("S2_DB"),
        )
        assertEquals(
            NpuStandardRouteMode.S3_MARKDOWN,
            NpuStandardRoutePreferences.fromDataStoreValue("S3_MARKDOWN"),
        )
        assertEquals(
            NpuStandardRouteMode.S4A_PSEUDO_STREAMING,
            NpuStandardRoutePreferences.fromDataStoreValue("S4A_PSEUDO_STREAMING"),
        )
        assertEquals(
            NpuStandardRouteMode.FULL,
            NpuStandardRoutePreferences.fromDataStoreValue("FULL"),
        )
    }

    @Test
    fun `NPU max output tokens developer options use fixed choices and default`() {
        assertEquals(
            listOf(32, 64, 128, 256, 512, 1024, 2048, 4096),
            NpuStandardRoutePreferences.selectableMaxOutputTokens,
        )
        assertEquals(128, NpuStandardRoutePreferences.DEFAULT_MAX_OUTPUT_TOKENS)
        assertEquals(
            "npu_standard_route_max_output_tokens",
            NPU_STANDARD_ROUTE_MAX_OUTPUT_TOKENS_DATASTORE_KEY,
        )
    }

    @Test
    fun `NPU max output tokens developer labels describe selected limit`() {
        assertEquals("128", npuStandardRouteMaxOutputTokensDisplayLabel(128))
        assertEquals(
            "NPU標準ルートのmax_output_tokens=128",
            npuStandardRouteMaxOutputTokensDescription(128),
        )
    }

    @Test
    fun `legacy QAIRT route label is diagnostic and separate from standard route`() {
        assertEquals("Legacy QAIRT244診断", LEGACY_QAIRT244_DIAGNOSTIC_TITLE)
        assertTrue(LEGACY_QAIRT244_DIAGNOSTIC_DESCRIPTION.contains("旧QAIRT診断経路"))
        assertTrue(LEGACY_QAIRT244_DIAGNOSTIC_DESCRIPTION.contains("S1〜S5 NPU標準ルートとは別"))
        assertTrue(LEGACY_QAIRT244_DIAGNOSTIC_DESCRIPTION.contains("通常利用は非推奨"))
    }
}
