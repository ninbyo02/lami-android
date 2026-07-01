package io.github.ninbyo02.lami.ui.screens.settings

import kotlinx.coroutines.flow.Flow

internal sealed class LocalModelSlot(
    val title: String,
    val description: String,
) {
    abstract fun displayNameFlow(settingsPreferences: SettingsPreferences): Flow<String?>
    abstract fun filePathFlow(settingsPreferences: SettingsPreferences): Flow<String?>
    abstract suspend fun saveModelInfo(
        settingsPreferences: SettingsPreferences,
        displayName: String,
        filePath: String,
    )

    abstract suspend fun clearModelInfo(settingsPreferences: SettingsPreferences)

    data object NpuPreview : LocalModelSlot(
        title = "NPUプレビューモデル",
        description = "NPU対応端末で高速推論に使用します。",
    ) {
        override fun displayNameFlow(settingsPreferences: SettingsPreferences): Flow<String?> {
            return settingsPreferences.localBaseModelDisplayNameFlow
        }

        override fun filePathFlow(settingsPreferences: SettingsPreferences): Flow<String?> {
            return settingsPreferences.localBaseModelFilePathFlow
        }

        override suspend fun saveModelInfo(
            settingsPreferences: SettingsPreferences,
            displayName: String,
            filePath: String,
        ) {
            settingsPreferences.saveLocalBaseModelInfo(displayName = displayName, filePath = filePath)
        }

        override suspend fun clearModelInfo(settingsPreferences: SettingsPreferences) {
            settingsPreferences.clearLocalBaseModelInfo()
        }
    }

    data object GenericFallback : LocalModelSlot(
        title = "汎用フォールバックモデル",
        description = "NPUが使えない場合のGPU/CPU推論に使用します。",
    ) {
        override fun displayNameFlow(settingsPreferences: SettingsPreferences): Flow<String?> {
            return settingsPreferences.localGenericModelDisplayNameFlow
        }

        override fun filePathFlow(settingsPreferences: SettingsPreferences): Flow<String?> {
            return settingsPreferences.localGenericModelFilePathFlow
        }

        override suspend fun saveModelInfo(
            settingsPreferences: SettingsPreferences,
            displayName: String,
            filePath: String,
        ) {
            settingsPreferences.saveLocalGenericModelInfo(displayName = displayName, filePath = filePath)
        }

        override suspend fun clearModelInfo(settingsPreferences: SettingsPreferences) {
            settingsPreferences.clearLocalGenericModelInfo()
        }
    }
}
