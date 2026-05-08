package io.github.ninbyo02.lami.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredBackendDryRunSettingTest {
    @Test
    fun `fromStorage restores qualcomm qnn npu setting`() {
        assertEquals(
            PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU,
            PreferredBackendDryRunSetting.fromStorage("QUALCOMM_QNN_NPU"),
        )
    }

    @Test
    fun `fromStorage migrates legacy generic npu setting to qualcomm qnn npu`() {
        assertEquals(
            PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU,
            PreferredBackendDryRunSetting.fromStorage("NPU"),
        )
    }

    @Test
    fun `selectableEntries hides unsafe generic npu setting`() {
        assertEquals(
            listOf(
                PreferredBackendDryRunSetting.DEFAULT,
                PreferredBackendDryRunSetting.CPU,
                PreferredBackendDryRunSetting.GPU,
                PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU,
            ),
            PreferredBackendDryRunSetting.selectableEntries,
        )
    }

    @Test
    fun `fromStorage falls back to default for unknown values`() {
        assertEquals(PreferredBackendDryRunSetting.DEFAULT, PreferredBackendDryRunSetting.fromStorage("unknown"))
    }
}
