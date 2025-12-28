package com.sonusid.ollama.ui.screens.debug

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.sonusid.ollama.util.SpriteAnalysis
import kotlinx.coroutines.flow.first

private const val SPRITE_DEBUG_DATA_STORE = "sprite_debug_data_store"
private val Context.spriteDebugDataStore by preferencesDataStore(name = SPRITE_DEBUG_DATA_STORE)

interface SpriteDebugDataStore {
    suspend fun readState(): SpriteDebugState?
    suspend fun saveState(state: SpriteDebugState)
    suspend fun readAnalysisResult(): SpriteAnalysis.SpriteAnalysisResult?
    suspend fun saveAnalysisResult(result: SpriteAnalysis.SpriteAnalysisResult)
    suspend fun clearAnalysis()
}

class SpriteDebugPreferences(
    private val context: Context,
    private val gson: Gson = Gson(),
) : SpriteDebugDataStore {
    private val spriteDebugStateKey = stringPreferencesKey("sprite_debug_state_json")
    private val spriteDebugAnalysisKey = stringPreferencesKey("sprite_debug_analysis_json")

    override suspend fun readState(): SpriteDebugState? {
        val preferences = context.spriteDebugDataStore.data.first()
        val json = preferences[spriteDebugStateKey] ?: return null
        return runCatching { gson.fromJson(json, SpriteDebugState::class.java) }.getOrNull()
    }

    override suspend fun saveState(state: SpriteDebugState) {
        val json = gson.toJson(state)
        context.spriteDebugDataStore.edit { prefs ->
            prefs[spriteDebugStateKey] = json
        }
    }

    override suspend fun readAnalysisResult(): SpriteAnalysis.SpriteAnalysisResult? {
        val preferences = context.spriteDebugDataStore.data.first()
        val json = preferences[spriteDebugAnalysisKey] ?: return null
        return try {
            gson.fromJson(json, SpriteAnalysis.SpriteAnalysisResult::class.java)
        } catch (ex: JsonSyntaxException) {
            null
        }
    }

    override suspend fun saveAnalysisResult(result: SpriteAnalysis.SpriteAnalysisResult) {
        val json = gson.toJson(result)
        context.spriteDebugDataStore.edit { prefs ->
            prefs[spriteDebugAnalysisKey] = json
        }
    }

    override suspend fun clearAnalysis() {
        context.spriteDebugDataStore.edit { prefs ->
            prefs.remove(spriteDebugAnalysisKey)
        }
    }
}
