package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.sonusid.ollama.BuildConfig
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.data.normalize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private const val SETTINGS_DATA_STORE_NAME = "ollama_settings"
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
            intervalMs = 90,
        )
        val TALKING_DEFAULT = ReadyAnimationSettings(
            frameSequence = listOf(0, 1, 2, 1),
            intervalMs = 700,
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
                InsertionPattern(frameSequence = listOf(5), weight = 3, intervalMs = 110),
                InsertionPattern(frameSequence = listOf(5, 0, 5), weight = 1, intervalMs = 70),
            ),
            intervalMs = 120,
            everyNLoops = 5,
            probabilityPercent = 80,
            cooldownLoops = 3,
            exclusive = false,
        )
        val TALKING_DEFAULT = InsertionAnimationSettings(
            enabled = false,
            patterns = listOf(InsertionPattern(frameSequence = listOf(3, 4, 5))),
            intervalMs = 200,
            everyNLoops = 1,
            probabilityPercent = 50,
            cooldownLoops = 0,
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
    // 全アニメーション設定の一括保存用キー（段階2でUIをこの形式へ切替予定）
    // JSON形式: { "version": 1, "animations": { "<statusKey>": { "base": {...}, "insertion": {...} } } }
    private val spriteAnimationsJsonKey = stringPreferencesKey("sprite_animations_json")

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

    suspend fun saveSpriteAnimationsJson(json: String) {
        context.dataStore.edit { preferences ->
            preferences[spriteAnimationsJsonKey] = json
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

    private companion object {
        const val ALL_ANIMATIONS_JSON_VERSION = 1
        const val ALL_ANIMATIONS_READY_KEY = "Ready"
        const val ALL_ANIMATIONS_TALKING_KEY = "Talking"
        const val ALL_ANIMATIONS_READY_LEGACY_KEY = "ReadyBlink"
        const val JSON_VERSION_KEY = "version"
        const val JSON_ANIMATIONS_KEY = "animations"
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
