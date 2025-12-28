package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.sonusid.ollama.data.SpriteSheetConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_DATA_STORE_NAME = "ollama_settings"
private val Context.dataStore by preferencesDataStore(
    name = SETTINGS_DATA_STORE_NAME
)

class SettingsPreferences(private val context: Context) {

    private val dynamicColorKey = booleanPreferencesKey("dynamic_color_enabled")
    private val spriteSheetConfigKey = stringPreferencesKey("sprite_sheet_config")

    val settingsData: Flow<SettingsData> = context.dataStore.data.map { preferences ->
        SettingsData(
            useDynamicColor = preferences[dynamicColorKey] ?: false
        )
    }

    val spriteSheetConfig: Flow<SpriteSheetConfig> = context.dataStore.data.map { preferences ->
        val json = preferences[spriteSheetConfigKey]
        val parsed = json?.let { SpriteSheetConfig.fromJson(it) }
        parsed ?: SpriteSheetConfig.default3x3()
    }

    suspend fun updateDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[dynamicColorKey] = enabled
        }
    }

    suspend fun saveSpriteSheetConfig(config: SpriteSheetConfig) {
        context.dataStore.edit { preferences ->
            preferences[spriteSheetConfigKey] = config.toJson()
        }
    }

    suspend fun resetSpriteSheetConfig() {
        saveSpriteSheetConfig(SpriteSheetConfig.default3x3())
    }
}
