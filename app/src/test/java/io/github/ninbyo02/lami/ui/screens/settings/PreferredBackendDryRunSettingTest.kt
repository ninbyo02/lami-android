package io.github.ninbyo02.lami.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredBackendDryRunSettingTest {
    @Test
    fun `fromStorage migrates qualcomm qnn npu setting to gpu`() {
        assertEquals(
            PreferredBackendDryRunSetting.GPU,
            PreferredBackendDryRunSetting.fromStorage("QUALCOMM_QNN_NPU"),
        )
    }

    @Test
    fun `fromStorage migrates legacy generic npu setting to gpu`() {
        assertEquals(
            PreferredBackendDryRunSetting.GPU,
            PreferredBackendDryRunSetting.fromStorage("NPU"),
        )
    }

    @Test
    fun `selectableEntries exposes stable gpu and cpu settings only`() {
        assertEquals(
            listOf(
                PreferredBackendDryRunSetting.DEFAULT,
                PreferredBackendDryRunSetting.CPU,
                PreferredBackendDryRunSetting.GPU,
            ),
            PreferredBackendDryRunSetting.selectableEntries,
        )
    }

    @Test
    fun `fromStorage falls back to default for unknown values`() {
        assertEquals(PreferredBackendDryRunSetting.DEFAULT, PreferredBackendDryRunSetting.fromStorage("unknown"))
    }
}
