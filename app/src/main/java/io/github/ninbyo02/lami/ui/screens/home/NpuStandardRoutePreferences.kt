package io.github.ninbyo02.lami.ui.screens.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal const val NPU_STANDARD_ROUTE_MODE_DATASTORE_KEY = "npu_standard_route_mode"

internal class NpuStandardRoutePreferences(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun getMode(): NpuStandardRouteMode =
        dataStore.data
            .map { preferences ->
                fromDataStoreValue(
                    preferences[npuStandardRouteModeKey],
                )
            }
            .first()

    suspend fun setMode(mode: NpuStandardRouteMode) {
        dataStore.edit { preferences ->
            preferences[npuStandardRouteModeKey] = mode.toDataStoreValue()
        }
    }

    internal companion object {
        val npuStandardRouteModeKey = stringPreferencesKey(NPU_STANDARD_ROUTE_MODE_DATASTORE_KEY)

        private fun NpuStandardRouteMode.toDataStoreValue(): String = name

        fun fromDataStoreValue(raw: String?): NpuStandardRouteMode =
            NpuStandardRouteMode.entries.firstOrNull { it.name == raw } ?: NpuStandardRouteMode.OFF
    }
}
