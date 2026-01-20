package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.sonusid.ollama.BuildConfig
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.data.normalize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random
import java.io.File

private const val SETTINGS_DATA_STORE_NAME = "ollama_settings"
private const val OFFLINE_BASE_INTERVAL_MIN_MS = 500
private val Context.dataStore by preferencesDataStore(
    name = SETTINGS_DATA_STORE_NAME
)

data class ReadyAnimationSettings(
    val frameSequence: List<Int>,
    val intervalMs: Int,
) {
    companion object {
        val READY_DEFAULT = ReadyAnimationSettings(
            frameSequence = listOf(0, 0, 0, 0),
            intervalMs = 180,
        )
        val TALKING_DEFAULT = ReadyAnimationSettings(
            frameSequence = listOf(0, 6, 0, 6),
            intervalMs = 140,
        )
        // Thinking のデフォルト: base の intervalMs=180ms/frames は指定JSONに合わせる
        val THINKING_DEFAULT = ReadyAnimationSettings(
            frameSequence = listOf(7, 7, 7, 6, 7, 7, 6, 7),
            intervalMs = 180,
        )
        // OFFLINE のデフォルト: UI側の OfflineLoop に合わせる
        val OFFLINE_DEFAULT = ReadyAnimationSettings(
            frameSequence = listOf(8, 8),
            intervalMs = 1_250,
        )
        // ERROR のデフォルト: UI側の ErrorLight に合わせる
        val ERROR_DEFAULT = ReadyAnimationSettings(
            frameSequence = listOf(5, 7, 5),
            intervalMs = 390,
        )
        val DEFAULT = READY_DEFAULT

        const val MIN_INTERVAL_MS: Int = 50
        const val MAX_INTERVAL_MS: Int = 5_000
    }
}

data class InsertionPattern(
    val frameSequence: List<Int>,
    val weight: Int = 1,
    val intervalMs: Int? = null,
)

data class InsertionAnimationSettings(
    val enabled: Boolean,
    val patterns: List<InsertionPattern>,
    val intervalMs: Int,
    val everyNLoops: Int,
    val probabilityPercent: Int,
    val cooldownLoops: Int,
    val exclusive: Boolean,
) {
    companion object {
        val READY_DEFAULT = InsertionAnimationSettings(
            enabled = true,
            patterns = listOf(
                // Ready insertion のデフォルトを仕様に合わせて更新
                InsertionPattern(frameSequence = listOf(5, 0), weight = 3, intervalMs = 110),
                InsertionPattern(frameSequence = listOf(5, 0, 5, 0, 0), weight = 1, intervalMs = 110),
            ),
            intervalMs = 110,
            everyNLoops = 5,
            probabilityPercent = 58,
            cooldownLoops = 6,
            exclusive = false,
        )
        val TALKING_DEFAULT = InsertionAnimationSettings(
            enabled = true,
            patterns = listOf(
                InsertionPattern(frameSequence = listOf(5, 0), weight = 2, intervalMs = 110),
                InsertionPattern(frameSequence = listOf(5, 0, 5), weight = 1, intervalMs = 110),
            ),
            intervalMs = 110,
            everyNLoops = 4,
            probabilityPercent = 50,
            cooldownLoops = 2,
            exclusive = false,
        )
        // Thinking のデフォルト: pattern intervalMs/intervalMs は指定JSONに合わせる
        // everyNLoops/probabilityPercent/cooldownLoops は連発抑制のための挿入判定パラメータ
        val THINKING_DEFAULT = InsertionAnimationSettings(
            enabled = true,
            patterns = listOf(
                InsertionPattern(frameSequence = listOf(5, 7), weight = 2, intervalMs = 110),
                InsertionPattern(frameSequence = listOf(4, 8, 4), weight = 1, intervalMs = 80),
            ),
            intervalMs = 200,
            everyNLoops = 5,
            probabilityPercent = 65,
            cooldownLoops = 5,
            exclusive = false,
        )
        // OFFLINE は挿入アニメ無効（パターン空）
        val OFFLINE_DEFAULT = InsertionAnimationSettings(
            enabled = false,
            patterns = emptyList(),
            intervalMs = 1_250,
            everyNLoops = MIN_EVERY_N_LOOPS,
            probabilityPercent = MIN_PROBABILITY_PERCENT,
            cooldownLoops = MIN_COOLDOWN_LOOPS,
            exclusive = false,
        )
        // ERROR は挿入アニメ無効（パターン空）
        val ERROR_DEFAULT = InsertionAnimationSettings(
            enabled = false,
            patterns = emptyList(),
            intervalMs = 390,
            everyNLoops = MIN_EVERY_N_LOOPS,
            probabilityPercent = MIN_PROBABILITY_PERCENT,
            cooldownLoops = MIN_COOLDOWN_LOOPS,
            exclusive = false,
        )
        val DEFAULT = READY_DEFAULT

        const val MIN_INTERVAL_MS: Int = ReadyAnimationSettings.MIN_INTERVAL_MS
        const val MAX_INTERVAL_MS: Int = ReadyAnimationSettings.MAX_INTERVAL_MS
        const val MIN_EVERY_N_LOOPS: Int = 1
        const val MIN_PROBABILITY_PERCENT: Int = 0
        const val MAX_PROBABILITY_PERCENT: Int = 100
        const val MIN_COOLDOWN_LOOPS: Int = 0
    }
}

data class PerStateAnimationConfig(
    val animationKey: String,
    val baseFrames: List<Int>,
    val baseIntervalMs: Int,
    val insertion: InsertionConfig,
)

data class InsertionConfig(
    val enabled: Boolean,
    val patterns: List<InsertionPatternConfig>,
    val intervalMs: Int,
    val everyNLoops: Int,
    val probabilityPercent: Int,
    val cooldownLoops: Int,
    val exclusive: Boolean,
)

data class InsertionPatternConfig(
    val frames: List<Int>,
    val weight: Int,
    val intervalMs: Int,
)

// state別選択キー保存用の最小enum（既存定義が無い前提）
enum class SpriteState {
    READY,
    IDLE,
    SPEAKING,
    TALK_SHORT,
    TALK_LONG,
    TALK_CALM,
    THINKING,
    ERROR,
    OFFLINE,
}

class SettingsPreferences(private val context: Context) {

    private val defaultSpriteSheetConfig = SpriteSheetConfig.default3x3()
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color_enabled")
    private val spriteSheetConfigKey = stringPreferencesKey("sprite_sheet_config")
    private val readyFrameSequenceKey = stringPreferencesKey("ready_frame_sequence")
    private val readyIntervalMsKey = intPreferencesKey("ready_interval_ms")
    private val readyInsertionEnabledKey = booleanPreferencesKey("insertion_enabled")
    private val readyInsertionFrameSequenceKey = stringPreferencesKey("insertion_frame_sequence")
    private val readyInsertionIntervalMsKey = intPreferencesKey("insertion_interval_ms")
    private val readyInsertionPattern1IntervalMsKey = intPreferencesKey("ready_insertion_pattern1_interval_ms")
    private val readyInsertionPattern2IntervalMsKey = intPreferencesKey("ready_insertion_pattern2_interval_ms")
    private val readyInsertionEveryNLoopsKey = intPreferencesKey("insertion_every_n_loops")
    private val readyInsertionProbabilityKey = intPreferencesKey("insertion_probability_percent")
    private val readyInsertionCooldownLoopsKey = intPreferencesKey("insertion_cooldown_loops")
    private val readyInsertionExclusiveKey = booleanPreferencesKey("insertion_exclusive")
    private val talkingFrameSequenceKey = stringPreferencesKey("talking_frame_sequence")
    private val talkingIntervalMsKey = intPreferencesKey("talking_interval_ms")
    private val talkingInsertionEnabledKey = booleanPreferencesKey("talking_insertion_enabled")
    private val talkingInsertionFrameSequenceKey = stringPreferencesKey("talking_insertion_frame_sequence")
    private val talkingInsertionIntervalMsKey = intPreferencesKey("talking_insertion_interval_ms")
    private val talkingInsertionPattern1IntervalMsKey = intPreferencesKey("talking_insertion_pattern1_interval_ms")
    private val talkingInsertionPattern2IntervalMsKey = intPreferencesKey("talking_insertion_pattern2_interval_ms")
    private val talkingInsertionEveryNLoopsKey = intPreferencesKey("talking_insertion_every_n_loops")
    private val talkingInsertionProbabilityKey = intPreferencesKey("talking_insertion_probability_percent")
    private val talkingInsertionCooldownLoopsKey = intPreferencesKey("talking_insertion_cooldown_loops")
    private val talkingInsertionExclusiveKey = booleanPreferencesKey("talking_insertion_exclusive")
    // 段階移行の再実行を避けるため、完了フラグで1回だけ実行する
    private val spriteAnimationsMigratedV1Key = booleanPreferencesKey("sprite_animations_migrated_v1")
    // state別に最後に選択したアニメキーを保存する（段階移行用）
    private val selectedKeyReadyKey = stringPreferencesKey("sprite_selected_key_ready")
    private val selectedKeyIdleKey = stringPreferencesKey("sprite_selected_key_idle")
    private val selectedKeySpeakingKey = stringPreferencesKey("sprite_selected_key_speaking")
    private val selectedKeyTalkShortKey = stringPreferencesKey("sprite_selected_key_talk_short")
    private val selectedKeyTalkLongKey = stringPreferencesKey("sprite_selected_key_talk_long")
    private val selectedKeyTalkCalmKey = stringPreferencesKey("sprite_selected_key_talk_calm")
    private val selectedKeyThinkingKey = stringPreferencesKey("sprite_selected_key_thinking")
    private val selectedKeyErrorKey = stringPreferencesKey("sprite_selected_key_error")
    private val selectedKeyOfflineKey = stringPreferencesKey("sprite_selected_key_offline")
    // 最後に選択したアニメ種別（AnimationType.internalKey）を保存して復元する
    private val lastSelectedAnimationTypeKey = stringPreferencesKey("sprite_last_selected_animation_type")
    // 最後に選択したタブ（SpriteTab名を保存して復元する）
    private val lastSelectedSpriteTabKey = stringPreferencesKey("sprite_last_selected_tab")
    // 画像調整で最後に選択したコマ番号（1始まり）
    private val lastSelectedBoxNumberKey = intPreferencesKey("sprite_last_selected_box_number")
    // 再起動時の復元用に最後の画面Routeを保持する
    private val lastRouteKey = stringPreferencesKey("last_route")
    // 旧: 全アニメーション設定の一括保存用キー（読み取り専用の移行/フォールバック）
    // state別JSONが正の保存形式のため、新規保存では書き込まない（PR24で完全削除可能）
    // JSON形式（全体）: { "version": 1, "animations": { "<statusKey>": { "base": {...}, "insertion": {...} } } }
    private val spriteAnimationsJsonKey = stringPreferencesKey("sprite_animations_json")
    // PR17: state別JSONが正（読み取り/保存の本命）
    // JSON形式（state別最小）: { "animationKey": "...", "base": {...}, "insertion": {...} }
    private val spriteAnimationJsonReadyKey = stringPreferencesKey("sprite_animation_json_ready")
    private val spriteAnimationJsonSpeakingKey = stringPreferencesKey("sprite_animation_json_speaking")
    private val spriteAnimationJsonIdleKey = stringPreferencesKey("sprite_animation_json_idle")
    private val spriteAnimationJsonTalkShortKey = stringPreferencesKey("sprite_animation_json_talk_short")
    private val spriteAnimationJsonTalkLongKey = stringPreferencesKey("sprite_animation_json_talk_long")
    private val spriteAnimationJsonTalkCalmKey = stringPreferencesKey("sprite_animation_json_talk_calm")
    private val spriteAnimationJsonThinkingKey = stringPreferencesKey("sprite_animation_json_thinking")
    private val spriteAnimationJsonOfflineKey = stringPreferencesKey("sprite_animation_json_offline")
    private val spriteAnimationJsonErrorKey = stringPreferencesKey("sprite_animation_json_error")
    // DataStoreの実体確認用ログ(デバッグ専用)
    private fun dumpDataStoreDebug(caller: String) {
        if (!BuildConfig.DEBUG) return
        val filesDirPath = context.filesDir.absolutePath
        val dataDirPath = context.applicationInfo.dataDir
        val filesDirCandidate = File(filesDirPath, "datastore/$SETTINGS_DATA_STORE_NAME.preferences_pb")
        val dataDirCandidate = File(dataDirPath, "datastore/$SETTINGS_DATA_STORE_NAME.preferences_pb")
        Log.d(
            "LamiSprite",
            "DataStore debug($caller): filesDir=$filesDirPath dataDir=$dataDirPath"
        )
        Log.d(
            "LamiSprite",
            "DataStore debug($caller): filesCandidate=${filesDirCandidate.absolutePath} " +
                "exists=${filesDirCandidate.exists()} size=${filesDirCandidate.length()}"
        )
        Log.d(
            "LamiSprite",
            "DataStore debug($caller): dataDirCandidate=${dataDirCandidate.absolutePath} " +
                "exists=${dataDirCandidate.exists()} size=${dataDirCandidate.length()}"
        )
    }

    @VisibleForTesting
    internal suspend fun debugPreferenceKeysForTest(limit: Int = 20): List<String> {
        val preferences = context.dataStore.data.first()
        return preferences.asMap().keys
            .map { it.name }
            .sorted()
            .take(limit)
    }

    val settingsData: Flow<SettingsData> = context.dataStore.data.map { preferences ->
        SettingsData(
            useDynamicColor = preferences[dynamicColorKey] ?: false
        )
    }

    val spriteSheetConfigJson: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[spriteSheetConfigKey]
    }

    val spriteAnimationsJson: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[spriteAnimationsJsonKey]
    }

    // state別JSONが正（読み取り/保存の本命）
    fun spriteAnimationJsonFlow(state: SpriteState): Flow<String?> = context.dataStore.data
        .onStart {
            migrateLegacyAllAnimationsJsonToPerStateIfNeeded()
            ensurePerStateAnimationJsonsInitialized()
            repairOfflineErrorPerStateJsonIfNeeded()
        }
        .map { preferences ->
            preferences[spriteAnimationJsonPreferencesKey(state)]
        }

    // 復元優先順位:
    // 1) state別JSON（sprite_animation_json_*）※正（Single Source of Truth）
    // 2) 旧全体JSON（sprite_animations_json）※読み取り専用 fallback
    // 3) それ以前のlegacy（legacyToAllAnimationsJsonOrNull が担当）
    fun resolvedSpriteAnimationJsonFlow(state: SpriteState): Flow<String?> = context.dataStore.data.map { preferences ->
        val perState = preferences[spriteAnimationJsonPreferencesKey(state)]
        val legacy = preferences[spriteAnimationsJsonKey]
        perState?.takeIf { it.isNotBlank() } ?: legacy?.takeIf { it.isNotBlank() }
    }

    // state別の選択キー取得（DataStore未保存時は null を返す）
    fun selectedKeyFlow(state: SpriteState): Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[selectedKeyPreferencesKey(state)]
    }

    val lastSelectedSpriteTab: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[lastSelectedSpriteTabKey]
    }

    val lastSelectedAnimationType: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[lastSelectedAnimationTypeKey]
    }

    val lastSelectedBoxNumber: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[lastSelectedBoxNumberKey]
    }

    val lastRoute: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[lastRouteKey]
    }

    val spriteSheetConfig: Flow<SpriteSheetConfig> = context.dataStore.data.map { preferences ->
        val json = preferences[spriteSheetConfigKey]
        val parsed = json?.let { SpriteSheetConfig.fromJson(it) }
        parsed?.normalize(defaultSpriteSheetConfig) ?: defaultSpriteSheetConfig
    }

    val readyAnimationSettings: Flow<ReadyAnimationSettings> = context.dataStore.data.map { preferences ->
        val frameSequenceString = preferences[readyFrameSequenceKey]
        val parsedFrames = frameSequenceString
            ?.split(",")
            ?.mapNotNull { value -> value.trim().toIntOrNull() }
            ?.filter { it in 0 until defaultSpriteSheetConfig.frameCount }
            .orEmpty()
            .ifEmpty { ReadyAnimationSettings.READY_DEFAULT.frameSequence }

        val intervalMs = preferences[readyIntervalMsKey]
            ?.coerceIn(ReadyAnimationSettings.MIN_INTERVAL_MS, ReadyAnimationSettings.MAX_INTERVAL_MS)
            ?: ReadyAnimationSettings.READY_DEFAULT.intervalMs

        ReadyAnimationSettings(
            frameSequence = parsedFrames,
            intervalMs = intervalMs,
        )
    }

    val talkingAnimationSettings: Flow<ReadyAnimationSettings> = context.dataStore.data.map { preferences ->
        val frameSequenceString = preferences[talkingFrameSequenceKey]
        val parsedFrames = frameSequenceString
            ?.split(",")
            ?.mapNotNull { value -> value.trim().toIntOrNull() }
            ?.filter { it in 0 until defaultSpriteSheetConfig.frameCount }
            .orEmpty()
            .ifEmpty { ReadyAnimationSettings.TALKING_DEFAULT.frameSequence }

        val intervalMs = preferences[talkingIntervalMsKey]
            ?.coerceIn(ReadyAnimationSettings.MIN_INTERVAL_MS, ReadyAnimationSettings.MAX_INTERVAL_MS)
            ?: ReadyAnimationSettings.TALKING_DEFAULT.intervalMs

        ReadyAnimationSettings(
            frameSequence = parsedFrames,
            intervalMs = intervalMs,
        )
    }

    val readyInsertionAnimationSettings: Flow<InsertionAnimationSettings> = context.dataStore.data.map { preferences ->
        val patternsText = preferences[readyInsertionFrameSequenceKey]
        val parsedPatterns = parseInsertionPatternsFromText(
            text = patternsText,
            fallback = InsertionAnimationSettings.READY_DEFAULT.patterns,
        )
        val patternIntervals = listOf(
            resolveStoredPatternInterval(preferences[readyInsertionPattern1IntervalMsKey]),
            resolveStoredPatternInterval(preferences[readyInsertionPattern2IntervalMsKey]),
        )
        val adjustedPatterns = applyPatternIntervals(parsedPatterns, patternIntervals)

        val intervalMs = preferences[readyInsertionIntervalMsKey]
            ?.coerceIn(InsertionAnimationSettings.MIN_INTERVAL_MS, InsertionAnimationSettings.MAX_INTERVAL_MS)
            ?: InsertionAnimationSettings.READY_DEFAULT.intervalMs

        val everyNLoops = preferences[readyInsertionEveryNLoopsKey]
            ?.coerceAtLeast(InsertionAnimationSettings.MIN_EVERY_N_LOOPS)
            ?: InsertionAnimationSettings.READY_DEFAULT.everyNLoops

        val probabilityPercent = preferences[readyInsertionProbabilityKey]
            ?.coerceIn(
                InsertionAnimationSettings.MIN_PROBABILITY_PERCENT,
                InsertionAnimationSettings.MAX_PROBABILITY_PERCENT
            )
            ?: InsertionAnimationSettings.READY_DEFAULT.probabilityPercent

        val cooldownLoops = preferences[readyInsertionCooldownLoopsKey]
            ?.coerceAtLeast(InsertionAnimationSettings.MIN_COOLDOWN_LOOPS)
            ?: InsertionAnimationSettings.READY_DEFAULT.cooldownLoops

        val enabled = preferences[readyInsertionEnabledKey] ?: InsertionAnimationSettings.READY_DEFAULT.enabled
        val exclusive = preferences[readyInsertionExclusiveKey] ?: InsertionAnimationSettings.READY_DEFAULT.exclusive
        if (BuildConfig.DEBUG) {
            Log.d(
                "LamiSprite",
                "ready insertion restore: patternsText=${patternsText?.take(120)} " +
                    "intervals=$patternIntervals " +
                    "parsed=${formatPatternsForLog(parsedPatterns)} " +
                    "adjusted=${formatPatternsForLog(adjustedPatterns)} " +
                    "defaultInterval=$intervalMs"
            )
        }

        InsertionAnimationSettings(
            enabled = enabled,
            patterns = adjustedPatterns,
            intervalMs = intervalMs,
            everyNLoops = everyNLoops,
            probabilityPercent = probabilityPercent,
            cooldownLoops = cooldownLoops,
            exclusive = exclusive,
        )
    }

    val talkingInsertionAnimationSettings: Flow<InsertionAnimationSettings> = context.dataStore.data.map { preferences ->
        val patternsText = preferences[talkingInsertionFrameSequenceKey]
        val parsedPatterns = parseInsertionPatternsFromText(
            text = patternsText,
            fallback = InsertionAnimationSettings.TALKING_DEFAULT.patterns,
        )
        val patternIntervals = listOf(
            resolveStoredPatternInterval(preferences[talkingInsertionPattern1IntervalMsKey]),
            resolveStoredPatternInterval(preferences[talkingInsertionPattern2IntervalMsKey]),
        )
        val adjustedPatterns = applyPatternIntervals(parsedPatterns, patternIntervals)

        val intervalMs = preferences[talkingInsertionIntervalMsKey]
            ?.coerceIn(InsertionAnimationSettings.MIN_INTERVAL_MS, InsertionAnimationSettings.MAX_INTERVAL_MS)
            ?: InsertionAnimationSettings.TALKING_DEFAULT.intervalMs

        val everyNLoops = preferences[talkingInsertionEveryNLoopsKey]
            ?.coerceAtLeast(InsertionAnimationSettings.MIN_EVERY_N_LOOPS)
            ?: InsertionAnimationSettings.TALKING_DEFAULT.everyNLoops

        val probabilityPercent = preferences[talkingInsertionProbabilityKey]
            ?.coerceIn(
                InsertionAnimationSettings.MIN_PROBABILITY_PERCENT,
                InsertionAnimationSettings.MAX_PROBABILITY_PERCENT
            )
            ?: InsertionAnimationSettings.TALKING_DEFAULT.probabilityPercent

        val cooldownLoops = preferences[talkingInsertionCooldownLoopsKey]
            ?.coerceAtLeast(InsertionAnimationSettings.MIN_COOLDOWN_LOOPS)
            ?: InsertionAnimationSettings.TALKING_DEFAULT.cooldownLoops

        val enabled = preferences[talkingInsertionEnabledKey] ?: InsertionAnimationSettings.TALKING_DEFAULT.enabled
        val exclusive = preferences[talkingInsertionExclusiveKey] ?: InsertionAnimationSettings.TALKING_DEFAULT.exclusive
        if (BuildConfig.DEBUG) {
            Log.d(
                "LamiSprite",
                "talking insertion restore: patternsText=${patternsText?.take(120)} " +
                    "intervals=$patternIntervals " +
                    "parsed=${formatPatternsForLog(parsedPatterns)} " +
                    "adjusted=${formatPatternsForLog(adjustedPatterns)} " +
                    "defaultInterval=$intervalMs"
            )
        }

        InsertionAnimationSettings(
            enabled = enabled,
            patterns = adjustedPatterns,
            intervalMs = intervalMs,
            everyNLoops = everyNLoops,
            probabilityPercent = probabilityPercent,
            cooldownLoops = cooldownLoops,
            exclusive = exclusive,
        )
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

    // 旧全体JSONは migration / legacy fallback 専用（通常フローからは呼ばない）
    @Suppress("unused")
    internal suspend fun saveSpriteAnimationsJson(json: String) {
        dumpDataStoreDebug("before saveSpriteAnimationsJson")
        context.dataStore.edit { preferences ->
            preferences[spriteAnimationsJsonKey] = json
        }
        dumpDataStoreDebug("after saveSpriteAnimationsJson")
    }

    // state別JSONが正（保存の本命）
    suspend fun saveSpriteAnimationJson(state: SpriteState, json: String) {
        if (BuildConfig.DEBUG) {
            val key = spriteAnimationJsonPreferencesKey(state)
            val snippet = json.take(120)
            val current = context.dataStore.data.first()[key]
            Log.d(
                "LamiSprite",
                "saveSpriteAnimationJson start: state=${state.name} key=${key.name} " +
                    "length=${json.length} head=${snippet}"
            )
            Log.d(
                "LamiSprite",
                "saveSpriteAnimationJson before: state=${state.name} " +
                    "storedLength=${current?.length ?: 0} storedHead=${current?.take(120)}"
            )
            dumpDataStoreDebug("before saveSpriteAnimationJson:${state.name}")
        }
        context.dataStore.edit { preferences ->
            preferences[spriteAnimationJsonPreferencesKey(state)] = json
        }
        if (BuildConfig.DEBUG) {
            val key = spriteAnimationJsonPreferencesKey(state)
            val updated = context.dataStore.data.first()[key]
            Log.d(
                "LamiSprite",
                "saveSpriteAnimationJson after: state=${state.name} " +
                    "storedLength=${updated?.length ?: 0} storedHead=${updated?.take(120)}"
            )
            dumpDataStoreDebug("after saveSpriteAnimationJson:${state.name}")
        }
    }

    // 旧キー→state別キーへの安全な段階移行（失敗時は何もしない）
    suspend fun migrateLegacyAllAnimationsToPerStateIfNeeded() {
        val preferences = context.dataStore.data.first()
        if (preferences[spriteAnimationsMigratedV1Key] == true) return
        val legacyJson = preferences[spriteAnimationsJsonKey]?.takeIf { it.isNotBlank() } ?: return
        val missingStates = SpriteState.values().filter { state ->
            val perStateJson = preferences[spriteAnimationJsonPreferencesKey(state)]
            perStateJson.isNullOrBlank()
        }
        if (missingStates.isEmpty()) return
        runCatching {
            // 旧JSONは既存の正規化/検証ロジックを再利用して安全に移行する
            val normalizedJson = parseAndValidateAllAnimationsJson(legacyJson).getOrThrow()
            val animationsObject = JSONObject(normalizedJson).optJSONObject(JSON_ANIMATIONS_KEY)
                ?: error("animations is missing")
            val savedStates = mutableListOf<SpriteState>()
            missingStates.forEach { state ->
                val animationObject = findLegacyAnimationObjectForState(state, animationsObject) ?: return@forEach
                val (baseDefaults, insertionDefaults) = defaultsForState(state)
                val baseSettings = parseReadySettings(animationObject.optJSONObject(JSON_BASE_KEY), baseDefaults)
                val insertionSettings = parseInsertionSettings(
                    animationObject.optJSONObject(JSON_INSERTION_KEY),
                    insertionDefaults
                )
                val perStateJson = buildPerStateAnimationJsonOrNull(
                    animationKey = defaultKeyForState(state),
                    baseSettings = baseSettings,
                    insertionSettings = insertionSettings,
                ) ?: return@forEach
                saveSpriteAnimationJson(state, perStateJson)
                savedStates.add(state)
            }
            context.dataStore.edit { updated ->
                updated[spriteAnimationsMigratedV1Key] = true
            }
            if (BuildConfig.DEBUG) {
                Log.d(
                    "LamiSprite",
                    "sprite animations migrate v1 completed: saved=${savedStates.joinToString { it.name }}"
                )
            }
        }.onFailure { throwable ->
            // 失敗時はクラッシュさせず、既存の旧キーを温存して安全側に倒す
            if (BuildConfig.DEBUG) {
                Log.d(
                    "LamiSprite",
                    "sprite animations migrate v1 skipped: ${throwable.message}"
                )
            }
        }
    }

    // 旧全体JSONをstate別JSONへ安全に移行する（初回のみ・冪等）
    suspend fun migrateLegacyAllAnimationsJsonToPerStateIfNeeded(): Result<Boolean> = runCatching {
        val preferences = context.dataStore.data.first()
        val legacyJson = preferences[spriteAnimationsJsonKey]?.takeIf { it.isNotBlank() }
            ?: return@runCatching false
        val hasPerState = SpriteState.values().any { state ->
            preferences[spriteAnimationJsonPreferencesKey(state)].isNullOrBlank().not()
        }
        if (hasPerState) return@runCatching false

        val normalizedJson = parseAndValidateAllAnimationsJson(legacyJson).getOrThrow()
        val perStateJsons = buildMap<SpriteState, String> {
            SpriteState.values().forEach { state ->
                val extracted = extractPerStateJsonFromAllAnimationsJson(normalizedJson, state)
                if (extracted != null) {
                    val validated = parseAndValidatePerStateAnimationJson(extracted, state)
                    if (validated.isSuccess) {
                        put(state, extracted)
                    }
                }
            }
        }
        if (perStateJsons.isEmpty()) return@runCatching false

        context.dataStore.edit { updated ->
            perStateJsons.forEach { (state, json) ->
                updated[spriteAnimationJsonPreferencesKey(state)] = json
            }
        }
        true
    }

    suspend fun ensurePerStateAnimationJsonsInitialized(): Result<Boolean> = runCatching {
        val preferences = context.dataStore.data.first()
        val missingStates = SpriteState.values().filter { state ->
            preferences[spriteAnimationJsonPreferencesKey(state)].isNullOrBlank()
        }
        var saved = false
        missingStates.forEach { state ->
            val (baseDefaults, insertionDefaults) = defaultsForState(state)
            val perStateJson = buildPerStateAnimationJsonOrNull(
                animationKey = defaultKeyForState(state),
                baseSettings = baseDefaults,
                insertionSettings = insertionDefaults,
            )
            if (perStateJson != null) {
                saveSpriteAnimationJson(state, perStateJson)
                saved = true
            }
        }
        val corrected = correctOfflineBaseIntervalIfNeeded(preferences)
        saved || corrected
    }

    private suspend fun correctOfflineBaseIntervalIfNeeded(preferences: androidx.datastore.preferences.core.Preferences): Boolean {
        val offlineJson = preferences[spriteAnimationJsonPreferencesKey(SpriteState.OFFLINE)]
            ?.takeIf { it.isNotBlank() } ?: return false
        val root = runCatching { JSONObject(offlineJson) }.getOrNull() ?: return false
        var changed = false
        val insertionDefaults = InsertionAnimationSettings.OFFLINE_DEFAULT
        val insertionObject = root.optJSONObject(JSON_INSERTION_KEY) ?: JSONObject().also { created ->
            created.put(JSON_ENABLED_KEY, insertionDefaults.enabled)
            created.put(JSON_PATTERNS_KEY, JSONArray())
            created.put(JSON_INTERVAL_MS_KEY, insertionDefaults.intervalMs)
            root.put(JSON_INSERTION_KEY, created)
            changed = true
        }
        if (!insertionObject.has(JSON_ENABLED_KEY)) {
            insertionObject.put(JSON_ENABLED_KEY, insertionDefaults.enabled)
            changed = true
        }
        if (!insertionObject.has(JSON_PATTERNS_KEY)) {
            insertionObject.put(JSON_PATTERNS_KEY, JSONArray())
            changed = true
        }
        if (!insertionObject.has(JSON_INTERVAL_MS_KEY)) {
            insertionObject.put(JSON_INTERVAL_MS_KEY, insertionDefaults.intervalMs)
            changed = true
        }
        val baseObject = root.optJSONObject(JSON_BASE_KEY)
        if (baseObject?.has(JSON_INTERVAL_MS_KEY) == true) {
            val baseIntervalMs = baseObject.getInt(JSON_INTERVAL_MS_KEY)
            if (baseIntervalMs < OFFLINE_BASE_INTERVAL_MIN_MS) {
                baseObject.put(JSON_INTERVAL_MS_KEY, ReadyAnimationSettings.OFFLINE_DEFAULT.intervalMs)
                root.put(JSON_BASE_KEY, baseObject)
                changed = true
            }
        }
        if (!changed) return false
        saveSpriteAnimationJson(SpriteState.OFFLINE, root.toString())
        return true
    }

    private fun updatePerStateBaseIntervalMs(json: String, intervalMs: Int): String? {
        return runCatching {
            val root = JSONObject(json)
            val baseObject = root.optJSONObject(JSON_BASE_KEY) ?: return@runCatching null
            baseObject.put(JSON_INTERVAL_MS_KEY, intervalMs)
            root.put(JSON_BASE_KEY, baseObject)
            root.toString()
        }.getOrNull()
    }

    suspend fun repairOfflineErrorPerStateJsonIfNeeded(): Result<Boolean> = runCatching {
        val preferences = context.dataStore.data.first()
        val targetStates = listOf(SpriteState.OFFLINE, SpriteState.ERROR)
        var repaired = false
        targetStates.forEach { state ->
            val currentJson = preferences[spriteAnimationJsonPreferencesKey(state)]
                ?.takeIf { it.isNotBlank() }
                ?: return@forEach
            val config = parseAndValidatePerStateAnimationJson(currentJson, state).getOrNull()
                ?: return@forEach
            if (!shouldRepairLegacyPerStateConfig(state, config)) return@forEach
            val (baseDefaults, insertionDefaults) = defaultsForState(state)
            val perStateJson = buildPerStateAnimationJsonOrNull(
                animationKey = defaultKeyForState(state),
                baseSettings = baseDefaults,
                insertionSettings = insertionDefaults,
            ) ?: return@forEach
            saveSpriteAnimationJson(state, perStateJson)
            repaired = true
        }
        repaired
    }

    // state別の選択キーを保存する
    suspend fun saveSelectedKey(state: SpriteState, key: String) {
        if (key.isBlank()) return
        context.dataStore.edit { preferences ->
            preferences[selectedKeyPreferencesKey(state)] = key
        }
    }

    // state別の選択キーを保存する（段階移行用）
    suspend fun setSelectedKey(state: SpriteState, key: String) {
        saveSelectedKey(state, key)
    }

    suspend fun saveLastSelectedSpriteTab(tabKey: String) {
        if (tabKey.isBlank()) return
        context.dataStore.edit { preferences ->
            preferences[lastSelectedSpriteTabKey] = tabKey
        }
    }

    suspend fun setLastSelectedAnimationType(value: String) {
        if (value.isBlank()) return
        context.dataStore.edit { preferences ->
            preferences[lastSelectedAnimationTypeKey] = value
        }
    }

    suspend fun saveLastSelectedBoxNumber(value: Int) {
        if (value <= 0) return
        context.dataStore.edit { preferences ->
            preferences[lastSelectedBoxNumberKey] = value
        }
    }

    suspend fun saveLastRoute(route: String) {
        if (route.isBlank()) return
        context.dataStore.edit { preferences ->
            preferences[lastRouteKey] = route
        }
    }

    suspend fun resetSpriteSheetConfig() {
        saveSpriteSheetConfig(defaultSpriteSheetConfig)
    }

    suspend fun saveReadyAnimationSettings(settings: ReadyAnimationSettings) {
        context.dataStore.edit { preferences ->
            preferences[readyFrameSequenceKey] = settings.frameSequence.joinToString(separator = ",")
            preferences[readyIntervalMsKey] = settings.intervalMs
        }
    }

    suspend fun saveTalkingAnimationSettings(settings: ReadyAnimationSettings) {
        context.dataStore.edit { preferences ->
            preferences[talkingFrameSequenceKey] = settings.frameSequence.joinToString(separator = ",")
            preferences[talkingIntervalMsKey] = settings.intervalMs
        }
    }

    suspend fun saveReadyInsertionAnimationSettings(settings: InsertionAnimationSettings) {
        if (BuildConfig.DEBUG) {
            settings.patterns.forEachIndexed { index, pattern ->
                Log.d(
                    "LamiSprite",
                    "ready insertion save: index=$index frames=${pattern.frameSequence} " +
                        "weight=${pattern.weight} interval=${pattern.intervalMs} " +
                        "storedInterval=${patternIntervalToStorageValue(pattern.intervalMs)} " +
                        "defaultInterval=${settings.intervalMs}"
                )
            }
        }
        context.dataStore.edit { preferences ->
            preferences[readyInsertionEnabledKey] = settings.enabled
            preferences[readyInsertionFrameSequenceKey] = settings.patterns.toStorageText()
            preferences[readyInsertionIntervalMsKey] = settings.intervalMs
            preferences[readyInsertionPattern1IntervalMsKey] =
                patternIntervalToStorageValue(settings.patterns.getOrNull(0)?.intervalMs)
            preferences[readyInsertionPattern2IntervalMsKey] =
                patternIntervalToStorageValue(settings.patterns.getOrNull(1)?.intervalMs)
            preferences[readyInsertionEveryNLoopsKey] = settings.everyNLoops
            preferences[readyInsertionProbabilityKey] = settings.probabilityPercent
            preferences[readyInsertionCooldownLoopsKey] = settings.cooldownLoops
            preferences[readyInsertionExclusiveKey] = settings.exclusive
        }
    }

    suspend fun saveTalkingInsertionAnimationSettings(settings: InsertionAnimationSettings) {
        if (BuildConfig.DEBUG) {
            settings.patterns.forEachIndexed { index, pattern ->
                Log.d(
                    "LamiSprite",
                    "talking insertion save: index=$index frames=${pattern.frameSequence} " +
                        "weight=${pattern.weight} interval=${pattern.intervalMs} " +
                        "storedInterval=${patternIntervalToStorageValue(pattern.intervalMs)} " +
                        "defaultInterval=${settings.intervalMs}"
                )
            }
        }
        context.dataStore.edit { preferences ->
            preferences[talkingInsertionEnabledKey] = settings.enabled
            preferences[talkingInsertionFrameSequenceKey] = settings.patterns.toStorageText()
            preferences[talkingInsertionIntervalMsKey] = settings.intervalMs
            preferences[talkingInsertionPattern1IntervalMsKey] =
                patternIntervalToStorageValue(settings.patterns.getOrNull(0)?.intervalMs)
            preferences[talkingInsertionPattern2IntervalMsKey] =
                patternIntervalToStorageValue(settings.patterns.getOrNull(1)?.intervalMs)
            preferences[talkingInsertionEveryNLoopsKey] = settings.everyNLoops
            preferences[talkingInsertionProbabilityKey] = settings.probabilityPercent
            preferences[talkingInsertionCooldownLoopsKey] = settings.cooldownLoops
            preferences[talkingInsertionExclusiveKey] = settings.exclusive
        }
    }

    // stateごとのデフォルトキーはDataStoreに書かず、解決関数で返す
    fun defaultKeyForState(state: SpriteState): String =
        when (state) {
            SpriteState.READY -> "Ready"
            SpriteState.IDLE -> "Idle"
            SpriteState.SPEAKING -> "TalkDefault"
            SpriteState.TALK_SHORT -> "TalkShort"
            SpriteState.TALK_LONG -> "TalkLong"
            SpriteState.TALK_CALM -> "TalkCalm"
            SpriteState.THINKING -> "Thinking"
            SpriteState.ERROR -> "ErrorLight"
            SpriteState.OFFLINE -> "OfflineLoop"
        }

    fun buildAllAnimationsJsonFromLegacy(
        readyBase: ReadyAnimationSettings,
        readyInsertion: InsertionAnimationSettings,
        talkingBase: ReadyAnimationSettings,
        talkingInsertion: InsertionAnimationSettings,
    ): String {
        val animations = JSONObject()
        animations.put(
            ALL_ANIMATIONS_READY_KEY,
            JSONObject()
                .put(JSON_BASE_KEY, normalizeReadySettings(readyBase, ReadyAnimationSettings.READY_DEFAULT).toJsonObject())
                .put(JSON_INSERTION_KEY, normalizeInsertionSettings(readyInsertion).toJsonObject())
        )
        animations.put(
            ALL_ANIMATIONS_TALKING_KEY,
            JSONObject()
                .put(JSON_BASE_KEY, normalizeReadySettings(talkingBase, ReadyAnimationSettings.TALKING_DEFAULT).toJsonObject())
                .put(JSON_INSERTION_KEY, normalizeInsertionSettings(talkingInsertion).toJsonObject())
        )
        return JSONObject()
            .put(JSON_VERSION_KEY, ALL_ANIMATIONS_JSON_VERSION)
            .put(JSON_ANIMATIONS_KEY, animations)
            .toString()
    }

    fun parseAndValidateAllAnimationsJson(json: String): Result<String> = runCatching {
        val root = JSONObject(json)
        val version = root.optInt(JSON_VERSION_KEY, -1)
        if (version != ALL_ANIMATIONS_JSON_VERSION) {
            error("unsupported version: $version")
        }
        val animationsObject = root.optJSONObject(JSON_ANIMATIONS_KEY)
            ?: error("animations is missing")
        val normalizedAnimations = JSONObject()
        val keys = animationsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val animationObject = animationsObject.optJSONObject(key) ?: continue
            val baseObject = animationObject.optJSONObject(JSON_BASE_KEY)
            val insertionObject = animationObject.optJSONObject(JSON_INSERTION_KEY)
            val normalizedKey = if (key == ALL_ANIMATIONS_READY_LEGACY_KEY) {
                ALL_ANIMATIONS_READY_KEY
            } else {
                key
            }
            val (baseDefaults, insertionDefaults) = when (normalizedKey) {
                ALL_ANIMATIONS_READY_KEY -> ReadyAnimationSettings.READY_DEFAULT to InsertionAnimationSettings.READY_DEFAULT
                ALL_ANIMATIONS_TALKING_KEY -> ReadyAnimationSettings.TALKING_DEFAULT to InsertionAnimationSettings.TALKING_DEFAULT
                ALL_ANIMATIONS_THINKING_KEY -> ReadyAnimationSettings.THINKING_DEFAULT to InsertionAnimationSettings.THINKING_DEFAULT
                else -> ReadyAnimationSettings.DEFAULT to InsertionAnimationSettings.DEFAULT
            }
            val normalizedBase = parseReadySettings(baseObject, baseDefaults).toJsonObject()
            val normalizedInsertion = parseInsertionSettings(insertionObject, insertionDefaults).toJsonObject()
            normalizedAnimations.put(
                normalizedKey,
                JSONObject()
                    .put(JSON_BASE_KEY, normalizedBase)
                    .put(JSON_INSERTION_KEY, normalizedInsertion)
            )
        }
        JSONObject()
            .put(JSON_VERSION_KEY, ALL_ANIMATIONS_JSON_VERSION)
            .put(JSON_ANIMATIONS_KEY, normalizedAnimations)
            .toString()
    }

    // PR20: state別最小JSON（1アニメ=1JSON）の読み取り/反映のためのパーサ
    // PR20ではREADY/SPEAKINGのみ適用
    fun parseAndValidatePerStateAnimationJson(
        json: String,
        state: SpriteState,
    ): Result<PerStateAnimationConfig> = runCatching {
        val root = JSONObject(json)
        val (_, insertionDefaults) = defaultsForState(state)
        val animationKey = root.optString(JSON_ANIMATION_KEY, "").trim()
        if (animationKey.isBlank()) {
            error("animationKey is missing: state=${state.name}")
        }
        val baseObject = root.optJSONObject(JSON_BASE_KEY) ?: error("base is missing: state=${state.name}")
        val baseFramesArray = baseObject.optJSONArray(JSON_FRAMES_KEY)
            ?: error("base.frames is missing: state=${state.name}")
        if (!baseObject.has(JSON_INTERVAL_MS_KEY)) {
            error("base.intervalMs is missing: state=${state.name}")
        }
        val baseFrames = parsePerStateFrames(baseFramesArray)
        val baseIntervalMs = baseObject.getInt(JSON_INTERVAL_MS_KEY)
        val insertionObject = root.optJSONObject(JSON_INSERTION_KEY)
            ?: error("insertion is missing: state=${state.name}")
        if (!insertionObject.has(JSON_ENABLED_KEY)) {
            error("insertion.enabled is missing: state=${state.name}")
        }
        val enabled = insertionObject.getBoolean(JSON_ENABLED_KEY)
        val patterns = parsePerStatePatterns(insertionObject.optJSONArray(JSON_PATTERNS_KEY))
        val intervalMs = if (insertionObject.has(JSON_INTERVAL_MS_KEY)) {
            insertionObject.getInt(JSON_INTERVAL_MS_KEY)
        } else {
            insertionDefaults.intervalMs
        }
        val everyNLoops = insertionObject.optInt(JSON_EVERY_N_LOOPS_KEY, 1).coerceAtLeast(1)
        val probabilityPercent = insertionObject.optInt(JSON_PROBABILITY_PERCENT_KEY, 50)
            .coerceIn(0, 100)
        val cooldownLoops = insertionObject.optInt(JSON_COOLDOWN_LOOPS_KEY, 0).coerceAtLeast(0)
        val exclusive = insertionObject.optBoolean(JSON_EXCLUSIVE_KEY, false)
        PerStateAnimationConfig(
            animationKey = animationKey,
            baseFrames = baseFrames,
            baseIntervalMs = baseIntervalMs,
            insertion = InsertionConfig(
                enabled = enabled,
                patterns = patterns,
                intervalMs = intervalMs,
                everyNLoops = everyNLoops,
                probabilityPercent = probabilityPercent,
                cooldownLoops = cooldownLoops,
                exclusive = exclusive,
            ),
        )
    }

    fun legacyToAllAnimationsJsonOrNull(
        currentAllAnimationsJson: String?,
        readyBase: ReadyAnimationSettings,
        readyInsertion: InsertionAnimationSettings,
        talkingBase: ReadyAnimationSettings,
        talkingInsertion: InsertionAnimationSettings,
    ): String? {
        if (!currentAllAnimationsJson.isNullOrBlank()) return null
        return buildAllAnimationsJsonFromLegacy(
            readyBase = readyBase,
            readyInsertion = readyInsertion,
            talkingBase = talkingBase,
            talkingInsertion = talkingInsertion,
        )
    }

    private val offlineBaseDefaults = ReadyAnimationSettings(
        frameSequence = listOf(8, 8),
        intervalMs = 1_250,
    )
    private val offlineInsertionDefaults = disabledInsertionDefaults(offlineBaseDefaults.intervalMs)
    private val errorBaseDefaults = ReadyAnimationSettings(
        frameSequence = listOf(5, 7, 5),
        intervalMs = 390,
    )
    private val errorInsertionDefaults = disabledInsertionDefaults(errorBaseDefaults.intervalMs)
    private val talkShortBaseDefaults = ReadyAnimationSettings(
        frameSequence = listOf(0, 6, 2, 6, 0),
        intervalMs = 130,
    )
    private val talkShortInsertionDefaults = InsertionAnimationSettings.TALKING_DEFAULT.copy(
        enabled = false,
        patterns = listOf(InsertionPattern(listOf(0, 6, 2, 6, 0))),
        intervalMs = 130,
    )
    private val talkLongBaseDefaults = ReadyAnimationSettings(
        frameSequence = listOf(0, 4, 6, 4, 4, 6, 4, 0),
        intervalMs = 190,
    )
    private val talkLongInsertionDefaults = InsertionAnimationSettings(
        enabled = true,
        patterns = listOf(InsertionPattern(listOf(1))),
        intervalMs = 190,
        everyNLoops = 2,
        probabilityPercent = 100,
        cooldownLoops = 0,
        exclusive = true,
    )
    private val talkCalmBaseDefaults = ReadyAnimationSettings(
        frameSequence = listOf(7, 4, 7, 8, 7),
        intervalMs = 280,
    )
    private val talkCalmInsertionDefaults = InsertionAnimationSettings.TALKING_DEFAULT.copy(
        enabled = false,
        patterns = listOf(InsertionPattern(listOf(7, 4, 7, 8, 7))),
        intervalMs = 280,
    )

    private fun defaultsForState(state: SpriteState): Pair<ReadyAnimationSettings, InsertionAnimationSettings> =
        when (state) {
            SpriteState.READY -> ReadyAnimationSettings.READY_DEFAULT to InsertionAnimationSettings.READY_DEFAULT
            SpriteState.SPEAKING -> ReadyAnimationSettings.TALKING_DEFAULT to InsertionAnimationSettings.TALKING_DEFAULT
            SpriteState.TALK_SHORT -> talkShortBaseDefaults to talkShortInsertionDefaults
            SpriteState.TALK_LONG -> talkLongBaseDefaults to talkLongInsertionDefaults
            SpriteState.TALK_CALM -> talkCalmBaseDefaults to talkCalmInsertionDefaults
            SpriteState.THINKING -> ReadyAnimationSettings.THINKING_DEFAULT to InsertionAnimationSettings.THINKING_DEFAULT
            SpriteState.OFFLINE -> ReadyAnimationSettings.OFFLINE_DEFAULT to InsertionAnimationSettings.OFFLINE_DEFAULT
            SpriteState.ERROR -> ReadyAnimationSettings.ERROR_DEFAULT to InsertionAnimationSettings.ERROR_DEFAULT
            else -> ReadyAnimationSettings.DEFAULT to InsertionAnimationSettings.DEFAULT
        }

    private fun disabledInsertionDefaults(intervalMs: Int): InsertionAnimationSettings =
        InsertionAnimationSettings(
            enabled = false,
            patterns = emptyList(),
            intervalMs = intervalMs,
            everyNLoops = 1,
            probabilityPercent = 0,
            cooldownLoops = 0,
            exclusive = false,
        )

    private fun shouldRepairLegacyPerStateConfig(
        state: SpriteState,
        config: PerStateAnimationConfig,
    ): Boolean {
        if (config.animationKey != defaultKeyForState(state)) return false
        if (config.baseFrames != ReadyAnimationSettings.DEFAULT.frameSequence) return false
        if (config.baseIntervalMs != ReadyAnimationSettings.DEFAULT.intervalMs) return false
        val insertion = config.insertion
        val defaultInsertion = InsertionAnimationSettings.DEFAULT
        if (insertion.enabled != defaultInsertion.enabled) return false
        if (insertion.intervalMs != defaultInsertion.intervalMs) return false
        if (insertion.everyNLoops != defaultInsertion.everyNLoops) return false
        if (insertion.probabilityPercent != defaultInsertion.probabilityPercent) return false
        if (insertion.cooldownLoops != defaultInsertion.cooldownLoops) return false
        if (insertion.exclusive != defaultInsertion.exclusive) return false
        val defaultPatterns = defaultInsertion.patterns.take(2).mapNotNull { pattern ->
            val intervalMs = pattern.intervalMs ?: return@mapNotNull null
            InsertionPatternConfig(
                frames = pattern.frameSequence,
                weight = pattern.weight,
                intervalMs = intervalMs,
            )
        }
        return insertion.patterns == defaultPatterns
    }

    private fun legacyAnimationKeysForState(state: SpriteState): List<String> =
        when (state) {
            SpriteState.READY -> listOf(ALL_ANIMATIONS_READY_KEY, ALL_ANIMATIONS_READY_LEGACY_KEY)
            SpriteState.SPEAKING -> listOf(ALL_ANIMATIONS_TALKING_KEY, "Speaking")
            SpriteState.TALK_SHORT -> listOf("TalkShort")
            SpriteState.TALK_LONG -> listOf("TalkLong")
            SpriteState.TALK_CALM -> listOf("TalkCalm")
            SpriteState.IDLE -> listOf("Idle")
            SpriteState.THINKING -> listOf(ALL_ANIMATIONS_THINKING_KEY)
            SpriteState.OFFLINE -> listOf("OfflineLoop")
            SpriteState.ERROR -> listOf("ErrorLight", "ErrorHeavy")
        }

    private fun extractPerStateJsonFromAllAnimationsJson(allJson: String, state: SpriteState): String? {
        val root = JSONObject(allJson)
        val animationsObject = root.optJSONObject(JSON_ANIMATIONS_KEY) ?: return null
        val matchedKey = legacyAnimationKeysForState(state).firstOrNull { key ->
            animationsObject.has(key)
        } ?: return null
        val animationObject = animationsObject.optJSONObject(matchedKey) ?: return null
        val (baseDefaults, insertionDefaults) = defaultsForState(state)
        val baseSettings = parseReadySettings(animationObject.optJSONObject(JSON_BASE_KEY), baseDefaults)
        val insertionSettings = parseInsertionSettings(
            animationObject.optJSONObject(JSON_INSERTION_KEY),
            insertionDefaults,
        )
        return buildPerStateAnimationJsonOrNull(
            animationKey = matchedKey,
            baseSettings = baseSettings,
            insertionSettings = insertionSettings,
        )
    }

    private fun findLegacyAnimationObjectForState(
        state: SpriteState,
        animationsObject: JSONObject,
    ): JSONObject? =
        legacyAnimationKeysForState(state).firstNotNullOfOrNull { key ->
            animationsObject.optJSONObject(key)
        }

    private fun buildPerStateAnimationJsonOrNull(
        animationKey: String,
        baseSettings: ReadyAnimationSettings,
        insertionSettings: InsertionAnimationSettings,
    ): String? {
        val patterns = insertionSettings.patterns.take(2)
        if (patterns.any { pattern -> pattern.intervalMs == null }) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    "LamiSprite",
                    "sprite animations migrate v1 skipped: pattern intervalMs missing key=$animationKey"
                )
            }
            return null
        }
        val baseJson = JSONObject()
            .put(JSON_FRAMES_KEY, baseSettings.frameSequence.toJsonArray())
            .put(JSON_INTERVAL_MS_KEY, baseSettings.intervalMs)
        val insertionJson = JSONObject()
            .put(JSON_ENABLED_KEY, insertionSettings.enabled)
            .put(
                JSON_PATTERNS_KEY,
                JSONArray().also { array ->
                    patterns.forEach { pattern ->
                        array.put(
                            JSONObject()
                                .put(JSON_FRAMES_KEY, pattern.frameSequence.toJsonArray())
                                .put(JSON_WEIGHT_KEY, pattern.weight)
                                .put(JSON_PATTERN_INTERVAL_MS_KEY, requireNotNull(pattern.intervalMs))
                        )
                    }
                }
            )
            .put(JSON_INTERVAL_MS_KEY, insertionSettings.intervalMs)
            .put(JSON_EVERY_N_LOOPS_KEY, insertionSettings.everyNLoops)
            .put(JSON_PROBABILITY_PERCENT_KEY, insertionSettings.probabilityPercent)
            .put(JSON_COOLDOWN_LOOPS_KEY, insertionSettings.cooldownLoops)
            .put(JSON_EXCLUSIVE_KEY, insertionSettings.exclusive)
        return JSONObject()
            .put(JSON_ANIMATION_KEY, animationKey)
            .put(JSON_BASE_KEY, baseJson)
            .put(JSON_INSERTION_KEY, insertionJson)
            .toString()
    }

    private fun overridePerStateAnimationKey(json: String, animationKey: String): String? =
        runCatching {
            val root = JSONObject(json)
            root.put(JSON_ANIMATION_KEY, animationKey)
            root.toString()
        }.getOrNull()

    private fun parseReadySettings(
        json: JSONObject?,
        defaults: ReadyAnimationSettings = ReadyAnimationSettings.DEFAULT,
    ): ReadyAnimationSettings {
        val frames = parseFrames(json?.optJSONArray(JSON_FRAMES_KEY), defaults.frameSequence)
        val intervalMs = (json?.optInt(JSON_INTERVAL_MS_KEY, defaults.intervalMs)
            ?: defaults.intervalMs)
            .coerceIn(ReadyAnimationSettings.MIN_INTERVAL_MS, ReadyAnimationSettings.MAX_INTERVAL_MS)
        return ReadyAnimationSettings(frameSequence = frames, intervalMs = intervalMs)
    }

    private fun parseInsertionSettings(
        json: JSONObject?,
        defaults: InsertionAnimationSettings = InsertionAnimationSettings.DEFAULT,
    ): InsertionAnimationSettings {
        val patterns = parseInsertionPatterns(json, defaults.patterns)
        val intervalMs = (json?.optInt(JSON_INTERVAL_MS_KEY, defaults.intervalMs)
            ?: defaults.intervalMs)
            .coerceIn(InsertionAnimationSettings.MIN_INTERVAL_MS, InsertionAnimationSettings.MAX_INTERVAL_MS)
        val everyNLoops = (json?.optInt(JSON_EVERY_N_LOOPS_KEY, defaults.everyNLoops)
            ?: defaults.everyNLoops)
            .coerceAtLeast(InsertionAnimationSettings.MIN_EVERY_N_LOOPS)
        val probabilityPercent = (json?.optInt(
            JSON_PROBABILITY_PERCENT_KEY,
            defaults.probabilityPercent
        ) ?: defaults.probabilityPercent)
            .coerceIn(
                InsertionAnimationSettings.MIN_PROBABILITY_PERCENT,
                InsertionAnimationSettings.MAX_PROBABILITY_PERCENT
            )
        val cooldownLoops = (json?.optInt(JSON_COOLDOWN_LOOPS_KEY, defaults.cooldownLoops)
            ?: defaults.cooldownLoops)
            .coerceAtLeast(InsertionAnimationSettings.MIN_COOLDOWN_LOOPS)
        val enabled = json?.optBoolean(JSON_ENABLED_KEY, defaults.enabled)
            ?: defaults.enabled
        val exclusive = json?.optBoolean(JSON_EXCLUSIVE_KEY, defaults.exclusive)
            ?: defaults.exclusive
        return InsertionAnimationSettings(
            enabled = enabled,
            patterns = patterns,
            intervalMs = intervalMs,
            everyNLoops = everyNLoops,
            probabilityPercent = probabilityPercent,
            cooldownLoops = cooldownLoops,
            exclusive = exclusive,
        )
    }

    private fun normalizeReadySettings(
        settings: ReadyAnimationSettings,
        defaults: ReadyAnimationSettings,
    ): ReadyAnimationSettings =
        ReadyAnimationSettings(
            frameSequence = settings.frameSequence
                .filter { it in 0 until defaultSpriteSheetConfig.frameCount }
                .ifEmpty { defaults.frameSequence },
            intervalMs = settings.intervalMs.coerceIn(
                ReadyAnimationSettings.MIN_INTERVAL_MS,
                ReadyAnimationSettings.MAX_INTERVAL_MS
            ),
        )

    private fun normalizeInsertionSettings(settings: InsertionAnimationSettings): InsertionAnimationSettings =
        InsertionAnimationSettings(
            enabled = settings.enabled,
            patterns = normalizeInsertionPatterns(settings.patterns),
            intervalMs = settings.intervalMs.coerceIn(
                InsertionAnimationSettings.MIN_INTERVAL_MS,
                InsertionAnimationSettings.MAX_INTERVAL_MS
            ),
            everyNLoops = settings.everyNLoops.coerceAtLeast(InsertionAnimationSettings.MIN_EVERY_N_LOOPS),
            probabilityPercent = settings.probabilityPercent.coerceIn(
                InsertionAnimationSettings.MIN_PROBABILITY_PERCENT,
                InsertionAnimationSettings.MAX_PROBABILITY_PERCENT
            ),
            cooldownLoops = settings.cooldownLoops.coerceAtLeast(InsertionAnimationSettings.MIN_COOLDOWN_LOOPS),
            exclusive = settings.exclusive,
        )

    private fun parseInsertionPatterns(
        json: JSONObject?,
        defaultPatterns: List<InsertionPattern>,
    ): List<InsertionPattern> {
        val patternsArray = json?.optJSONArray(JSON_PATTERNS_KEY)
        if (patternsArray != null) {
            val parsedPatterns = buildList {
                for (index in 0 until patternsArray.length()) {
                    val patternObject = patternsArray.optJSONObject(index) ?: continue
                    val frames = parsePatternFrames(patternObject.optJSONArray(JSON_FRAMES_KEY))
                    val weight = patternObject.optInt(JSON_WEIGHT_KEY, 1).coerceAtLeast(0)
                    val intervalMs = if (patternObject.has(JSON_PATTERN_INTERVAL_MS_KEY)) {
                        patternObject.optInt(JSON_PATTERN_INTERVAL_MS_KEY, PATTERN_INTERVAL_UNSET)
                            .takeIf { value -> value >= 0 }
                            ?.coerceIn(0, InsertionAnimationSettings.MAX_INTERVAL_MS)
                    } else {
                        null
                    }
                    if (frames.isNotEmpty()) {
                        add(
                            InsertionPattern(
                                frameSequence = frames,
                                weight = weight,
                                intervalMs = intervalMs,
                            )
                        )
                    }
                }
            }
            return parsedPatterns
        }
        val legacyFrames = json?.optJSONArray(JSON_FRAMES_KEY)?.let { array ->
            parseFrames(array, defaultPatterns.firstOrNull()?.frameSequence ?: emptyList())
        } ?: defaultPatterns.firstOrNull()?.frameSequence.orEmpty()
        return listOf(InsertionPattern(frameSequence = legacyFrames, weight = 1))
    }

    private fun parsePatternFrames(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optInt(index, -1)
                if (value in 0 until defaultSpriteSheetConfig.frameCount) {
                    add(value)
                }
            }
        }
    }

    private fun parseInsertionPatternsFromText(
        text: String?,
        fallback: List<InsertionPattern>,
    ): List<InsertionPattern> {
        if (text.isNullOrBlank()) return fallback
        val rawPatterns = text
            .replace("｜", "|")
            .split("|")
            .map { token -> token.trim() }
            .filter { token -> token.isNotEmpty() }
        if (rawPatterns.isEmpty()) return fallback
        val parsed = rawPatterns.mapNotNull { patternText ->
            val (framesText, weightText) = patternText.split(":", limit = 2).let { parts ->
                parts.firstOrNull().orEmpty() to parts.getOrNull(1)
            }
            val frames = framesText
                .split(",")
                .mapNotNull { value -> value.trim().toIntOrNull() }
                .filter { it in 0 until defaultSpriteSheetConfig.frameCount }
            val weight = weightText?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 1
            if (frames.isEmpty()) null else InsertionPattern(frameSequence = frames, weight = weight)
        }
        return parsed.ifEmpty { fallback }
    }

    private fun normalizeInsertionPatterns(patterns: List<InsertionPattern>): List<InsertionPattern> {
        if (patterns.isEmpty()) return emptyList()
        return patterns.mapNotNull { pattern ->
            val frames = pattern.frameSequence.filter { it in 0 until defaultSpriteSheetConfig.frameCount }
            val weight = pattern.weight.coerceAtLeast(0)
            val intervalMs = pattern.intervalMs
                ?.takeIf { value -> value >= 0 }
                ?.coerceIn(0, InsertionAnimationSettings.MAX_INTERVAL_MS)
            if (frames.isEmpty()) {
                null
            } else {
                InsertionPattern(
                    frameSequence = frames,
                    weight = weight,
                    intervalMs = intervalMs,
                )
            }
        }
    }

    private fun parseFrames(array: JSONArray?, defaultSequence: List<Int>): List<Int> {
        if (array == null) return defaultSequence
        val frames = buildList {
            for (index in 0 until array.length()) {
                val value = array.optInt(index, -1)
                if (value in 0 until defaultSpriteSheetConfig.frameCount) {
                    add(value)
                }
            }
        }
        return frames.ifEmpty { defaultSequence }
    }

    private fun parsePerStateFrames(array: JSONArray): List<Int> {
        if (array.length() == 0) error("frames is empty")
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.getInt(index)
                if (value !in 0 until defaultSpriteSheetConfig.frameCount) {
                    error("frames out of range: $value")
                }
                add(value)
            }
        }
    }

    private fun parsePerStatePatterns(array: JSONArray?): List<InsertionPatternConfig> {
        if (array == null) return emptyList()
        val limit = minOf(array.length(), 2)
        return buildList {
            for (index in 0 until limit) {
                val patternObject = array.optJSONObject(index) ?: error("pattern is missing")
                val framesArray = patternObject.optJSONArray(JSON_FRAMES_KEY)
                    ?: error("pattern.frames is missing")
                if (!patternObject.has(JSON_PATTERN_INTERVAL_MS_KEY)) {
                    error("pattern.intervalMs is missing")
                }
                val frames = buildList {
                    if (framesArray.length() == 0) error("pattern.frames is empty")
                    for (frameIndex in 0 until framesArray.length()) {
                        val value = framesArray.getInt(frameIndex)
                        if (value !in 0 until defaultSpriteSheetConfig.frameCount) {
                            error("pattern.frames out of range: $value")
                        }
                        add(value)
                    }
                }
                val weight = patternObject.optInt(JSON_WEIGHT_KEY, 1).coerceAtLeast(0)
                val intervalMs = patternObject.getInt(JSON_PATTERN_INTERVAL_MS_KEY)
                add(
                    InsertionPatternConfig(
                        frames = frames,
                        weight = weight,
                        intervalMs = intervalMs,
                    )
                )
            }
        }
    }

    private fun ReadyAnimationSettings.toJsonObject(): JSONObject =
        JSONObject()
            .put(JSON_FRAMES_KEY, frameSequence.toJsonArray())
            .put(JSON_INTERVAL_MS_KEY, intervalMs)

    private fun InsertionAnimationSettings.toJsonObject(): JSONObject =
        JSONObject()
            .put(JSON_ENABLED_KEY, enabled)
            .put(JSON_PATTERNS_KEY, patterns.toPatternsJsonArray())
            .put(JSON_INTERVAL_MS_KEY, intervalMs)
            .put(JSON_EVERY_N_LOOPS_KEY, everyNLoops)
            .put(JSON_PROBABILITY_PERCENT_KEY, probabilityPercent)
            .put(JSON_COOLDOWN_LOOPS_KEY, cooldownLoops)
            .put(JSON_EXCLUSIVE_KEY, exclusive)

    private fun List<InsertionPattern>.toPatternsJsonArray(): JSONArray =
        JSONArray().also { array ->
            forEach { pattern ->
                val patternObject = JSONObject()
                    .put(JSON_FRAMES_KEY, pattern.frameSequence.toJsonArray())
                    .put(JSON_WEIGHT_KEY, pattern.weight)
                pattern.intervalMs?.let { intervalMs ->
                    patternObject.put(JSON_PATTERN_INTERVAL_MS_KEY, intervalMs)
                }
                array.put(patternObject)
            }
        }

    private fun List<Int>.toJsonArray(): JSONArray =
        JSONArray().also { array -> forEach { array.put(it) } }

    private fun List<InsertionPattern>.toStorageText(): String =
        joinToString(separator = " | ") { pattern ->
            "${pattern.frameSequence.joinToString(separator = ",")}:${pattern.weight}"
        }

    private fun applyPatternIntervals(
        patterns: List<InsertionPattern>,
        intervals: List<Int?>,
    ): List<InsertionPattern> =
        patterns.mapIndexed { index, pattern ->
            val intervalMs = intervals.getOrNull(index)
            val resolvedIntervalMs = pattern.intervalMs ?: intervalMs
            if (resolvedIntervalMs == null) {
                pattern
            } else {
                pattern.copy(intervalMs = resolvedIntervalMs)
            }
        }

    private fun resolveStoredPatternInterval(rawValue: Int?): Int? {
        if (rawValue == null || rawValue == PATTERN_INTERVAL_UNSET) return null
        if (rawValue < 0) return null
        return rawValue.coerceIn(0, InsertionAnimationSettings.MAX_INTERVAL_MS)
    }

    private fun patternIntervalToStorageValue(intervalMs: Int?): Int =
        intervalMs?.coerceIn(0, InsertionAnimationSettings.MAX_INTERVAL_MS) ?: PATTERN_INTERVAL_UNSET

    private fun formatPatternsForLog(patterns: List<InsertionPattern>): String =
        patterns.mapIndexed { index, pattern ->
            "index=$index frames=${pattern.frameSequence} weight=${pattern.weight} interval=${pattern.intervalMs}"
        }.joinToString(separator = ", ")

    // state別キーのマッピングはここで一元化する
    private fun selectedKeyPreferencesKey(state: SpriteState) = when (state) {
        SpriteState.READY -> selectedKeyReadyKey
        SpriteState.IDLE -> selectedKeyIdleKey
        SpriteState.SPEAKING -> selectedKeySpeakingKey
        SpriteState.TALK_SHORT -> selectedKeyTalkShortKey
        SpriteState.TALK_LONG -> selectedKeyTalkLongKey
        SpriteState.TALK_CALM -> selectedKeyTalkCalmKey
        SpriteState.THINKING -> selectedKeyThinkingKey
        SpriteState.ERROR -> selectedKeyErrorKey
        SpriteState.OFFLINE -> selectedKeyOfflineKey
    }

    private fun spriteAnimationJsonPreferencesKey(state: SpriteState) = when (state) {
        SpriteState.READY -> spriteAnimationJsonReadyKey
        SpriteState.IDLE -> spriteAnimationJsonIdleKey
        SpriteState.SPEAKING -> spriteAnimationJsonSpeakingKey
        SpriteState.TALK_SHORT -> spriteAnimationJsonTalkShortKey
        SpriteState.TALK_LONG -> spriteAnimationJsonTalkLongKey
        SpriteState.TALK_CALM -> spriteAnimationJsonTalkCalmKey
        SpriteState.THINKING -> spriteAnimationJsonThinkingKey
        SpriteState.ERROR -> spriteAnimationJsonErrorKey
        SpriteState.OFFLINE -> spriteAnimationJsonOfflineKey
    }

    private companion object {
        const val ALL_ANIMATIONS_JSON_VERSION = 1
        const val ALL_ANIMATIONS_READY_KEY = "Ready"
        const val ALL_ANIMATIONS_TALKING_KEY = "Talking"
        const val ALL_ANIMATIONS_THINKING_KEY = "Thinking"
        const val ALL_ANIMATIONS_READY_LEGACY_KEY = "ReadyBlink"
        const val JSON_VERSION_KEY = "version"
        const val JSON_ANIMATIONS_KEY = "animations"
        const val JSON_ANIMATION_KEY = "animationKey"
        const val JSON_BASE_KEY = "base"
        const val JSON_INSERTION_KEY = "insertion"
        const val JSON_ENABLED_KEY = "enabled"
        const val JSON_PATTERNS_KEY = "patterns"
        const val JSON_FRAMES_KEY = "frames"
        const val JSON_WEIGHT_KEY = "weight"
        const val JSON_INTERVAL_MS_KEY = "intervalMs"
        const val JSON_PATTERN_INTERVAL_MS_KEY = "intervalMs"
        const val JSON_EVERY_N_LOOPS_KEY = "everyNLoops"
        const val JSON_PROBABILITY_PERCENT_KEY = "probabilityPercent"
        const val JSON_COOLDOWN_LOOPS_KEY = "cooldownLoops"
        const val JSON_EXCLUSIVE_KEY = "exclusive"
        const val PATTERN_INTERVAL_UNSET = -1
    }
}

/**
 * TODO: ランタイムの挿入判定に統合する。
 * この関数は設定値に基づき「挿入を試行してよいか」を返す。
 */
fun InsertionAnimationSettings.shouldAttemptInsertion(
    loopCount: Int,
    lastInsertionLoop: Int?,
    random: Random = Random(System.currentTimeMillis()),
): Boolean {
    if (!enabled) return false
    val hasCooldown = cooldownLoops > 0 && lastInsertionLoop != null
    if (hasCooldown && (loopCount - lastInsertionLoop) < cooldownLoops) return false
    if (everyNLoops <= 0) return false
    if (loopCount % everyNLoops != 0) return false
    if (probabilityPercent == 0) return false
    if (probabilityPercent == 100) return true
    val roll = random.nextInt(100) // roll は 0..99
    if (roll >= probabilityPercent) return false
    return true
}
