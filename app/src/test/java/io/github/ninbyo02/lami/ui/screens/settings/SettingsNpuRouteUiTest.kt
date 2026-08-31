package io.github.ninbyo02.lami.ui.screens.settings

import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteMode
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRoutePreferences
import io.github.ninbyo02.lami.ui.screens.home.NPU_STANDARD_ROUTE_MAX_OUTPUT_TOKENS_DATASTORE_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "S4 Streaming",
            npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.S4A_PSEUDO_STREAMING),
        )
        assertEquals("S5 TTS", npuStandardRouteModeDisplayLabel(NpuStandardRouteMode.FULL))
    }

    @Test
    fun `unified inference backend selection maps local choices to NPU route off`() {
        assertEquals(
            InferenceBackendSelection.AUTOMATIC,
            InferenceBackendSelection.fromSettings(
                preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
            ),
        )
        assertEquals(
            NpuStandardRouteMode.OFF,
            effectiveNpuStandardRouteModeForBackendSelection(
                preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
                npuStandardRouteMode = NpuStandardRouteMode.OFF,
            ),
        )
        assertEquals(
            NpuStandardRouteMode.OFF,
            effectiveNpuStandardRouteModeForBackendSelection(
                preferredBackend = PreferredBackendDryRunSetting.CPU,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            ),
        )
        assertEquals(
            NpuStandardRouteMode.OFF,
            effectiveNpuStandardRouteModeForBackendSelection(
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            ),
        )
    }

    @Test
    fun `user facing backend list exposes promoted local NPU as one option`() {
        assertEquals(
            listOf(
                InferenceBackendSelection.AUTOMATIC,
                InferenceBackendSelection.CPU,
                InferenceBackendSelection.GPU,
                InferenceBackendSelection.NPU,
            ),
            InferenceBackendSelection.userFacingEntries,
        )
        assertEquals("NPU ローカル", InferenceBackendSelection.NPU.displayLabel)
        assertFalse(InferenceBackendSelection.NPU.displayLabel.contains("Beta"))
        assertTrue(InferenceBackendSelection.userFacingEntries.none { isDeveloperNpuPhaseSelection(it) })
    }

    @Test
    fun `developer NPU phase entries remain available for compatibility`() {
        assertEquals(
            listOf(
                InferenceBackendSelection.NPU_S1,
                InferenceBackendSelection.NPU_S2,
                InferenceBackendSelection.NPU_S3,
                InferenceBackendSelection.NPU_S4,
                InferenceBackendSelection.NPU_S5,
            ),
            InferenceBackendSelection.developerNpuPhaseEntries,
        )
        assertEquals(
            InferenceBackendSelection.developerNpuPhaseEntries,
            InferenceBackendSelection.npuEntries,
        )
        assertTrue(InferenceBackendSelection.developerNpuPhaseEntries.all { isDeveloperNpuPhaseSelection(it) })
        assertTrue(InferenceBackendSelection.NPU_S1.displayLabel.startsWith("DEV:"))
        assertTrue(InferenceBackendSelection.NPU_S5.displayLabel.startsWith("DEV:"))
    }

    @Test
    fun `unified inference backend selection maps NPU phases to single user facing NPU option`() {
        assertEquals(
            InferenceBackendSelection.NPU,
            InferenceBackendSelection.userFacingFromSettings(
                preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            ),
        )
        assertEquals(
            InferenceBackendSelection.NPU,
            InferenceBackendSelection.userFacingFromSettings(
                preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
                npuStandardRouteMode = NpuStandardRouteMode.FULL,
            ),
        )
        assertEquals(
            InferenceBackendSelection.NPU_S1,
            InferenceBackendSelection.fromSettings(
                preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            ),
        )
        assertEquals(
            NpuStandardRouteMode.S1_ONLY,
            effectiveNpuStandardRouteModeForBackendSelection(
                preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            ),
        )
        assertEquals(
            NpuStandardRouteMode.FULL,
            effectiveNpuStandardRouteModeForBackendSelection(
                preferredBackend = PreferredBackendDryRunSetting.DEFAULT,
                npuStandardRouteMode = NpuStandardRouteMode.FULL,
            ),
        )
    }

    @Test
    fun `selecting user facing NPU maps to completed standard route mode without changing CPU or GPU`() {
        assertEquals(PreferredBackendDryRunSetting.DEFAULT, InferenceBackendSelection.NPU.preferredBackend)
        assertEquals(NpuStandardRouteMode.FULL, InferenceBackendSelection.NPU.npuStandardRouteMode)
        assertEquals(
            NpuStandardRouteSelectionSource.USER_FACING_NPU_EXPERIMENTAL,
            NpuStandardRouteSelectionSource.forSelection(InferenceBackendSelection.NPU),
        )
        assertEquals(PreferredBackendDryRunSetting.CPU, InferenceBackendSelection.CPU.preferredBackend)
        assertEquals(NpuStandardRouteMode.OFF, InferenceBackendSelection.CPU.npuStandardRouteMode)
        assertEquals(
            NpuStandardRouteSelectionSource.LOCAL_BACKEND,
            NpuStandardRouteSelectionSource.forSelection(InferenceBackendSelection.CPU),
        )
        assertEquals(PreferredBackendDryRunSetting.GPU, InferenceBackendSelection.GPU.preferredBackend)
        assertEquals(NpuStandardRouteMode.OFF, InferenceBackendSelection.GPU.npuStandardRouteMode)
        assertEquals(
            NpuStandardRouteSelectionSource.LOCAL_BACKEND,
            NpuStandardRouteSelectionSource.forSelection(InferenceBackendSelection.GPU),
        )
        assertEquals(
            NpuStandardRouteSelectionSource.DEVELOPER_PHASE_OVERRIDE,
            NpuStandardRouteSelectionSource.forSelection(InferenceBackendSelection.NPU_S5),
        )
    }

    @Test
    fun `GPU backend label marks experimental deprecated status`() {
        assertTrue(InferenceBackendSelection.GPU.displayLabel.contains("Experimental"))
        assertTrue(InferenceBackendSelection.GPU.displayLabel.contains("非推奨"))
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
    fun `NPU native max output tokens experiment allows up to 4096`() {
        val resolution = NpuStandardRoutePreferences.resolveNativeMaxOutputTokens(4096)

        assertEquals(4096, resolution.requestedMaxOutputTokens)
        assertEquals(4096, resolution.effectiveMaxOutputTokens)
        assertEquals(4096, resolution.clampLimit)
        assertFalse(resolution.clamped)
        assertEquals(
            NpuStandardRoutePreferences.MAX_OUTPUT_TOKENS_CLAMP_REASON_NONE,
            resolution.clampReason,
        )
    }

    @Test
    fun `legacy QAIRT route label is diagnostic and separate from standard route`() {
        assertEquals("Legacy QAIRT244診断", LEGACY_QAIRT244_DIAGNOSTIC_TITLE)
        assertTrue(LEGACY_QAIRT244_DIAGNOSTIC_DESCRIPTION.contains("旧QAIRT診断経路"))
        assertTrue(LEGACY_QAIRT244_DIAGNOSTIC_DESCRIPTION.contains("S1〜S5 NPU標準ルートとは別"))
        assertTrue(LEGACY_QAIRT244_DIAGNOSTIC_DESCRIPTION.contains("通常利用は非推奨"))
    }

    @Test
    fun `NPU local backend description explains promoted standard route and model requirement`() {
        assertTrue(NPU_EXPERIMENTAL_BACKEND_DESCRIPTION.contains("NPU ローカル"))
        assertTrue(NPU_EXPERIMENTAL_BACKEND_DESCRIPTION.contains("UI・TTS・DB保存・Markdown"))
        assertTrue(NPU_EXPERIMENTAL_BACKEND_DESCRIPTION.contains("擬似Streaming"))
        assertTrue(NPU_EXPERIMENTAL_BACKEND_DESCRIPTION.contains("モデル未読込時"))
        assertFalse(NPU_EXPERIMENTAL_BACKEND_DESCRIPTION.contains("Beta"))
    }
}
