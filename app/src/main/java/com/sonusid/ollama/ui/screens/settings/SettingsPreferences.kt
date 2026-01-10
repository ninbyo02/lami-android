package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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
        val DEFAULT = ReadyAnimationSettings(
            frameSequence = listOf(0, 1, 2, 1),
            intervalMs = 700,
        )

        const val MIN_INTERVAL_MS: Int = 50
        const val MAX_INTERVAL_MS: Int = 5_000
    }
}

data class InsertionPattern(
    val frameSequence: List<Int>,
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
        val DEFAULT = InsertionAnimationSettings(
            enabled = false,
            patterns = listOf(InsertionPattern(frameSequence = listOf(3, 4, 5))),
            intervalMs = 200,
            everyNLoops = 1,
            probabilityPercent = 50,
            cooldownLoops = 0,
            exclusive = false,
        )

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
    private val readyInsertionEveryNLoopsKey = intPreferencesKey("insertion_every_n_loops")
    private val readyInsertionProbabilityKey = intPreferencesKey("insertion_probability_percent")
    private val readyInsertionCooldownLoopsKey = intPreferencesKey("insertion_cooldown_loops")
    private val readyInsertionExclusiveKey = booleanPreferencesKey("insertion_exclusive")
    private val talkingFrameSequenceKey = stringPreferencesKey("talking_frame_sequence")
    private val talkingIntervalMsKey = intPreferencesKey("talking_interval_ms")
    private val talkingInsertionEnabledKey = booleanPreferencesKey("talking_insertion_enabled")
    private val talkingInsertionFrameSequenceKey = stringPreferencesKey("talking_insertion_frame_sequence")
    private val talkingInsertionIntervalMsKey = intPreferencesKey("talking_insertion_interval_ms")
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
            .ifEmpty { ReadyAnimationSettings.DEFAULT.frameSequence }

        val intervalMs = preferences[readyIntervalMsKey]
            ?.coerceIn(ReadyAnimationSettings.MIN_INTERVAL_MS, ReadyAnimationSettings.MAX_INTERVAL_MS)
            ?: ReadyAnimationSettings.DEFAULT.intervalMs

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
            .ifEmpty { ReadyAnimationSettings.DEFAULT.frameSequence }

        val intervalMs = preferences[talkingIntervalMsKey]
            ?.coerceIn(ReadyAnimationSettings.MIN_INTERVAL_MS, ReadyAnimationSettings.MAX_INTERVAL_MS)
            ?: ReadyAnimationSettings.DEFAULT.intervalMs

        ReadyAnimationSettings(
            frameSequence = parsedFrames,
            intervalMs = intervalMs,
        )
    }

    val readyInsertionAnimationSettings: Flow<InsertionAnimationSettings> = context.dataStore.data.map { preferences ->
        val patternsText = preferences[readyInsertionFrameSequenceKey]
        val parsedPatterns = parseInsertionPatternsFromText(
            text = patternsText,
            fallback = InsertionAnimationSettings.DEFAULT.patterns,
        )

        val intervalMs = preferences[readyInsertionIntervalMsKey]
            ?.coerceIn(InsertionAnimationSettings.MIN_INTERVAL_MS, InsertionAnimationSettings.MAX_INTERVAL_MS)
            ?: InsertionAnimationSettings.DEFAULT.intervalMs

        val everyNLoops = preferences[readyInsertionEveryNLoopsKey]
            ?.coerceAtLeast(InsertionAnimationSettings.MIN_EVERY_N_LOOPS)
            ?: InsertionAnimationSettings.DEFAULT.everyNLoops

        val probabilityPercent = preferences[readyInsertionProbabilityKey]
            ?.coerceIn(
                InsertionAnimationSettings.MIN_PROBABILITY_PERCENT,
                InsertionAnimationSettings.MAX_PROBABILITY_PERCENT
            )
            ?: InsertionAnimationSettings.DEFAULT.probabilityPercent

        val cooldownLoops = preferences[readyInsertionCooldownLoopsKey]
            ?.coerceAtLeast(InsertionAnimationSettings.MIN_COOLDOWN_LOOPS)
            ?: InsertionAnimationSettings.DEFAULT.cooldownLoops

        val enabled = preferences[readyInsertionEnabledKey] ?: InsertionAnimationSettings.DEFAULT.enabled
        val exclusive = preferences[readyInsertionExclusiveKey] ?: InsertionAnimationSettings.DEFAULT.exclusive

        InsertionAnimationSettings(
            enabled = enabled,
            patterns = parsedPatterns,
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
            fallback = InsertionAnimationSettings.DEFAULT.patterns,
        )

        val intervalMs = preferences[talkingInsertionIntervalMsKey]
            ?.coerceIn(InsertionAnimationSettings.MIN_INTERVAL_MS, InsertionAnimationSettings.MAX_INTERVAL_MS)
            ?: InsertionAnimationSettings.DEFAULT.intervalMs

        val everyNLoops = preferences[talkingInsertionEveryNLoopsKey]
            ?.coerceAtLeast(InsertionAnimationSettings.MIN_EVERY_N_LOOPS)
            ?: InsertionAnimationSettings.DEFAULT.everyNLoops

        val probabilityPercent = preferences[talkingInsertionProbabilityKey]
            ?.coerceIn(
                InsertionAnimationSettings.MIN_PROBABILITY_PERCENT,
                InsertionAnimationSettings.MAX_PROBABILITY_PERCENT
            )
            ?: InsertionAnimationSettings.DEFAULT.probabilityPercent

        val cooldownLoops = preferences[talkingInsertionCooldownLoopsKey]
            ?.coerceAtLeast(InsertionAnimationSettings.MIN_COOLDOWN_LOOPS)
            ?: InsertionAnimationSettings.DEFAULT.cooldownLoops

        val enabled = preferences[talkingInsertionEnabledKey] ?: InsertionAnimationSettings.DEFAULT.enabled
        val exclusive = preferences[talkingInsertionExclusiveKey] ?: InsertionAnimationSettings.DEFAULT.exclusive

        InsertionAnimationSettings(
            enabled = enabled,
            patterns = parsedPatterns,
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
        context.dataStore.edit { preferences ->
            preferences[readyInsertionEnabledKey] = settings.enabled
            preferences[readyInsertionFrameSequenceKey] = settings.patterns.toStorageText()
            preferences[readyInsertionIntervalMsKey] = settings.intervalMs
            preferences[readyInsertionEveryNLoopsKey] = settings.everyNLoops
            preferences[readyInsertionProbabilityKey] = settings.probabilityPercent
            preferences[readyInsertionCooldownLoopsKey] = settings.cooldownLoops
            preferences[readyInsertionExclusiveKey] = settings.exclusive
        }
    }

    suspend fun saveTalkingInsertionAnimationSettings(settings: InsertionAnimationSettings) {
        context.dataStore.edit { preferences ->
            preferences[talkingInsertionEnabledKey] = settings.enabled
            preferences[talkingInsertionFrameSequenceKey] = settings.patterns.toStorageText()
            preferences[talkingInsertionIntervalMsKey] = settings.intervalMs
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
                .put(JSON_BASE_KEY, normalizeReadySettings(readyBase).toJsonObject())
                .put(JSON_INSERTION_KEY, normalizeInsertionSettings(readyInsertion).toJsonObject())
        )
        animations.put(
            ALL_ANIMATIONS_TALKING_KEY,
            JSONObject()
                .put(JSON_BASE_KEY, normalizeReadySettings(talkingBase).toJsonObject())
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
            val normalizedBase = parseReadySettings(baseObject).toJsonObject()
            val normalizedInsertion = parseInsertionSettings(insertionObject).toJsonObject()
            normalizedAnimations.put(
                key,
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

    private fun parseReadySettings(json: JSONObject?): ReadyAnimationSettings {
        val frames = parseFrames(json?.optJSONArray(JSON_FRAMES_KEY), ReadyAnimationSettings.DEFAULT.frameSequence)
        val intervalMs = (json?.optInt(JSON_INTERVAL_MS_KEY, ReadyAnimationSettings.DEFAULT.intervalMs)
            ?: ReadyAnimationSettings.DEFAULT.intervalMs)
            .coerceIn(ReadyAnimationSettings.MIN_INTERVAL_MS, ReadyAnimationSettings.MAX_INTERVAL_MS)
        return ReadyAnimationSettings(frameSequence = frames, intervalMs = intervalMs)
    }

    private fun parseInsertionSettings(json: JSONObject?): InsertionAnimationSettings {
        val patterns = parseInsertionPatterns(json)
        val intervalMs = (json?.optInt(JSON_INTERVAL_MS_KEY, InsertionAnimationSettings.DEFAULT.intervalMs)
            ?: InsertionAnimationSettings.DEFAULT.intervalMs)
            .coerceIn(InsertionAnimationSettings.MIN_INTERVAL_MS, InsertionAnimationSettings.MAX_INTERVAL_MS)
        val everyNLoops = (json?.optInt(JSON_EVERY_N_LOOPS_KEY, InsertionAnimationSettings.DEFAULT.everyNLoops)
            ?: InsertionAnimationSettings.DEFAULT.everyNLoops)
            .coerceAtLeast(InsertionAnimationSettings.MIN_EVERY_N_LOOPS)
        val probabilityPercent = (json?.optInt(
            JSON_PROBABILITY_PERCENT_KEY,
            InsertionAnimationSettings.DEFAULT.probabilityPercent
        ) ?: InsertionAnimationSettings.DEFAULT.probabilityPercent)
            .coerceIn(
                InsertionAnimationSettings.MIN_PROBABILITY_PERCENT,
                InsertionAnimationSettings.MAX_PROBABILITY_PERCENT
            )
        val cooldownLoops = (json?.optInt(JSON_COOLDOWN_LOOPS_KEY, InsertionAnimationSettings.DEFAULT.cooldownLoops)
            ?: InsertionAnimationSettings.DEFAULT.cooldownLoops)
            .coerceAtLeast(InsertionAnimationSettings.MIN_COOLDOWN_LOOPS)
        val enabled = json?.optBoolean(JSON_ENABLED_KEY, InsertionAnimationSettings.DEFAULT.enabled)
            ?: InsertionAnimationSettings.DEFAULT.enabled
        val exclusive = json?.optBoolean(JSON_EXCLUSIVE_KEY, InsertionAnimationSettings.DEFAULT.exclusive)
            ?: InsertionAnimationSettings.DEFAULT.exclusive
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

    private fun normalizeReadySettings(settings: ReadyAnimationSettings): ReadyAnimationSettings =
        ReadyAnimationSettings(
            frameSequence = settings.frameSequence
                .filter { it in 0 until defaultSpriteSheetConfig.frameCount }
                .ifEmpty { ReadyAnimationSettings.DEFAULT.frameSequence },
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

    private fun parseInsertionPatterns(json: JSONObject?): List<InsertionPattern> {
        val patternsArray = json?.optJSONArray(JSON_PATTERNS_KEY)
        if (patternsArray != null) {
            val parsedPatterns = buildList {
                for (index in 0 until patternsArray.length()) {
                    val patternObject = patternsArray.optJSONObject(index) ?: continue
                    val frames = parsePatternFrames(patternObject.optJSONArray(JSON_FRAMES_KEY))
                    if (frames.isNotEmpty()) {
                        add(InsertionPattern(frameSequence = frames))
                    }
                }
            }
            return parsedPatterns
        }
        val legacyFrames = json?.optJSONArray(JSON_FRAMES_KEY)?.let { array ->
            parseFrames(array, InsertionAnimationSettings.DEFAULT.patterns.first().frameSequence)
        } ?: InsertionAnimationSettings.DEFAULT.patterns.first().frameSequence
        return listOf(InsertionPattern(frameSequence = legacyFrames))
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
            val frames = patternText
                .split(",")
                .mapNotNull { value -> value.trim().toIntOrNull() }
                .filter { it in 0 until defaultSpriteSheetConfig.frameCount }
            if (frames.isEmpty()) null else InsertionPattern(frameSequence = frames)
        }
        return parsed.ifEmpty { fallback }
    }

    private fun normalizeInsertionPatterns(patterns: List<InsertionPattern>): List<InsertionPattern> {
        if (patterns.isEmpty()) return emptyList()
        return patterns.mapNotNull { pattern ->
            val frames = pattern.frameSequence.filter { it in 0 until defaultSpriteSheetConfig.frameCount }
            if (frames.isEmpty()) null else InsertionPattern(frameSequence = frames)
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
            .put(JSON_PATTERNS_KEY, patterns.toJsonArray())
            .put(JSON_INTERVAL_MS_KEY, intervalMs)
            .put(JSON_EVERY_N_LOOPS_KEY, everyNLoops)
            .put(JSON_PROBABILITY_PERCENT_KEY, probabilityPercent)
            .put(JSON_COOLDOWN_LOOPS_KEY, cooldownLoops)
            .put(JSON_EXCLUSIVE_KEY, exclusive)

    private fun List<InsertionPattern>.toJsonArray(): JSONArray =
        JSONArray().also { array ->
            forEach { pattern ->
                array.put(
                    JSONObject()
                        .put(JSON_FRAMES_KEY, pattern.frameSequence.toJsonArray())
                )
            }
        }

    private fun List<Int>.toJsonArray(): JSONArray =
        JSONArray().also { array -> forEach { array.put(it) } }

    private fun List<InsertionPattern>.toStorageText(): String =
        joinToString(separator = " | ") { pattern ->
            pattern.frameSequence.joinToString(separator = ",")
        }

    private companion object {
        const val ALL_ANIMATIONS_JSON_VERSION = 1
        const val ALL_ANIMATIONS_READY_KEY = "Ready"
        const val ALL_ANIMATIONS_TALKING_KEY = "Talking"
        const val JSON_VERSION_KEY = "version"
        const val JSON_ANIMATIONS_KEY = "animations"
        const val JSON_BASE_KEY = "base"
        const val JSON_INSERTION_KEY = "insertion"
        const val JSON_ENABLED_KEY = "enabled"
        const val JSON_PATTERNS_KEY = "patterns"
        const val JSON_FRAMES_KEY = "frames"
        const val JSON_INTERVAL_MS_KEY = "intervalMs"
        const val JSON_EVERY_N_LOOPS_KEY = "everyNLoops"
        const val JSON_PROBABILITY_PERCENT_KEY = "probabilityPercent"
        const val JSON_COOLDOWN_LOOPS_KEY = "cooldownLoops"
        const val JSON_EXCLUSIVE_KEY = "exclusive"
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
