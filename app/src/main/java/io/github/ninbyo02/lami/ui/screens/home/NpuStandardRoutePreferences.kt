package io.github.ninbyo02.lami.ui.screens.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal const val NPU_STANDARD_ROUTE_MODE_DATASTORE_KEY = "npu_standard_route_mode"
internal const val NPU_STANDARD_ROUTE_MAX_OUTPUT_TOKENS_DATASTORE_KEY = "npu_standard_route_max_output_tokens"

internal data class NpuStandardRouteMaxOutputTokensResolution(
    val requestedMaxOutputTokens: Int,
    val effectiveMaxOutputTokens: Int,
    val clamped: Boolean,
    val clampLimit: Int,
    val clampReason: String,
)

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

    suspend fun getMaxOutputTokens(): Int =
        dataStore.data
            .map { preferences ->
                sanitizeMaxOutputTokens(preferences[npuStandardRouteMaxOutputTokensKey])
            }
            .first()

    suspend fun setMaxOutputTokens(maxOutputTokens: Int) {
        dataStore.edit { preferences ->
            preferences[npuStandardRouteMaxOutputTokensKey] = sanitizeMaxOutputTokens(maxOutputTokens)
        }
    }

    internal companion object {
        val npuStandardRouteModeKey = stringPreferencesKey(NPU_STANDARD_ROUTE_MODE_DATASTORE_KEY)
        val npuStandardRouteMaxOutputTokensKey = intPreferencesKey(
            NPU_STANDARD_ROUTE_MAX_OUTPUT_TOKENS_DATASTORE_KEY,
        )
        val selectableMaxOutputTokens = listOf(32, 64, 128, 256, 512, 1024, 2048, 4096)
        const val DEFAULT_MAX_OUTPUT_TOKENS = 128
        const val MIN_MAX_OUTPUT_TOKENS = 1
        const val MAX_MAX_OUTPUT_TOKENS = 4096
        const val NATIVE_MAX_OUTPUT_TOKENS_LIMIT = 2048
        const val MAX_OUTPUT_TOKENS_CLAMP_REASON_NATIVE_LIMIT = "native_limit"
        const val MAX_OUTPUT_TOKENS_CLAMP_REASON_NONE = "none"

        private fun NpuStandardRouteMode.toDataStoreValue(): String = name

        fun fromDataStoreValue(raw: String?): NpuStandardRouteMode =
            NpuStandardRouteMode.entries.firstOrNull { it.name == raw } ?: NpuStandardRouteMode.OFF

        fun sanitizeMaxOutputTokens(raw: Int?): Int {
            val value = raw ?: DEFAULT_MAX_OUTPUT_TOKENS
            return if (value in selectableMaxOutputTokens && value in MIN_MAX_OUTPUT_TOKENS..MAX_MAX_OUTPUT_TOKENS) {
                value
            } else {
                DEFAULT_MAX_OUTPUT_TOKENS
            }
        }

        fun resolveNativeMaxOutputTokens(raw: Int?): NpuStandardRouteMaxOutputTokensResolution {
            val requested = sanitizeMaxOutputTokens(raw)
            val effective = requested.coerceAtMost(NATIVE_MAX_OUTPUT_TOKENS_LIMIT)
            val clamped = requested != effective
            return NpuStandardRouteMaxOutputTokensResolution(
                requestedMaxOutputTokens = requested,
                effectiveMaxOutputTokens = effective,
                clamped = clamped,
                clampLimit = NATIVE_MAX_OUTPUT_TOKENS_LIMIT,
                clampReason = if (clamped) {
                    MAX_OUTPUT_TOKENS_CLAMP_REASON_NATIVE_LIMIT
                } else {
                    MAX_OUTPUT_TOKENS_CLAMP_REASON_NONE
                },
            )
        }
    }
}
