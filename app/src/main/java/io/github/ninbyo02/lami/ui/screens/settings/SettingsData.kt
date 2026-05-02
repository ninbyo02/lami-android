package io.github.ninbyo02.lami.ui.screens.settings

data class SettingsData(
    val url: String = "",
    val name: String = "",
    val logo: Int = 0,
    val useDynamicColor: Boolean = false,
    val characterAnimationEnabled: Boolean = true,
    val inferenceStatsDisplayMode: InferenceStatsDisplayMode = InferenceStatsDisplayMode.SIMPLE,
    val preferredBackendDryRunSetting: PreferredBackendDryRunSetting = PreferredBackendDryRunSetting.DEFAULT,
)
