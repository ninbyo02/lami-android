package io.github.ninbyo02.lami.ui.components

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.UiState
import io.github.ninbyo02.lami.data.SpriteSheetConfig
import io.github.ninbyo02.lami.ui.animation.SpriteAnimationDefaults
import io.github.ninbyo02.lami.ui.screens.settings.ErrorCause
import io.github.ninbyo02.lami.ui.screens.settings.InsertionAnimationSettings
import io.github.ninbyo02.lami.ui.screens.settings.InsertionPattern
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import io.github.ninbyo02.lami.ui.screens.settings.effectiveInsertionIntervalMs
import io.github.ninbyo02.lami.ui.screens.settings.SpriteState
import io.github.ninbyo02.lami.ui.screens.settings.shouldAttemptInsertion
import io.github.ninbyo02.lami.util.DebugTraceFile
import io.github.ninbyo02.lami.util.RuntimeFlags
import io.github.ninbyo02.lami.viewmodels.LamiAnimationStatus
import io.github.ninbyo02.lami.viewmodels.LamiState
import io.github.ninbyo02.lami.viewmodels.LamiStatus
import io.github.ninbyo02.lami.viewmodels.bucket
import io.github.ninbyo02.lami.viewmodels.mapToAnimationLamiStatus
import io.github.ninbyo02.lami.viewmodels.resolveErrorKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import kotlin.random.Random

enum class LamiSpriteStatus {
    Idle,
    Thinking,
    TalkShort,
    TalkLong,
    TalkCalm,
    ErrorLight,
    ErrorHeavy,
    OfflineLoop,
    Ready,
}

data class LamiStatusSpriteLayout(
    val sizeDp: Dp = 48.dp,
    val maxSizeDp: Dp = 100.dp,
    val contentOffsetDp: Dp = 2.dp,
    val contentOffsetYDp: Dp = 0.dp,
)

data class LamiStatusSpriteOptions(
    val animationsEnabled: Boolean = true,
    val replacementEnabled: Boolean = true,
    val blinkEffectEnabled: Boolean = true,
    val debugOverlayEnabled: Boolean = true,
)

data class LamiStatusSpriteFrameOverrides(
    val frameXOffsetPxMap: Map<Int, Int> = emptyMap(),
    val frameYOffsetPxMap: Map<Int, Int> = emptyMap(),
    val frameSrcOffsetMap: Map<Int, IntOffset> = emptyMap(),
    val frameSrcSizeMap: Map<Int, IntSize> = emptyMap(),
    val autoCropTransparentArea: Boolean = false,
)

data class LamiStatusSpriteTrace(
    val debugOverloadLabel: String = "core(status: LamiSpriteStatus)",
    val lamiStatus: LamiStatus? = null,
    val lamiState: LamiState? = null,
    val resolvedAnimationStatus: LamiAnimationStatus? = null,
    val isSpeaking: Boolean = false,
)

private val DEBUG_OVERLAY_ENABLED: Boolean = BuildConfig.DEBUG

private fun appendSpriteTraceToFile(context: Context, line: String) {
    DebugTraceFile.append(context, "[LAMI_SPRITE_TRACE] $line")
}

// 96x96 各フレームの不透明バウンディングボックス下端（顎先基準想定）は
// 0:95, 1:95, 2:95, 3:94, 4:94, 5:94, 6:90, 7:90, 8:90。
// 下端を 95px に揃えるための補正量（+ は下方向シフト）。
@Suppress("unused")
private val spriteFrameYOffsetPx: Map<Int, Int> = mapOf(
    0 to 0,
    1 to 0,
    2 to 0,
    3 to 1,
    4 to 1,
    5 to 1,
    6 to 5,
    7 to 5,
    8 to 5,
)

data class FrameDurationSpec(
    val minMs: Long,
    val maxMs: Long,
    val jitterFraction: Float? = null,
) {
    fun draw(random: Random): Long {
        val clampedMin = minMs.coerceAtMost(maxMs)
        val clampedMax = maxMs.coerceAtLeast(minMs)
        val raw = random.nextLong(clampedMin, clampedMax + 1)
        val jitterBound = jitterFraction?.takeIf { it > 0f }
        if (jitterBound != null) {
            val midpoint = (clampedMin + clampedMax) / 2f
            val spread = (midpoint * jitterBound).toLong().coerceAtLeast(0L)
            val lower = (midpoint - spread).toLong()
            val upper = (midpoint + spread).toLong()
            return raw.coerceIn(lower, upper)
        }
        return raw
    }
}

sealed class InsertionFrequency {
    data class ByTime(val msRange: LongRange) : InsertionFrequency()
    data class ByLoops(val loopRange: IntRange) : InsertionFrequency()
    data class ByProbability(val probability: Float) : InsertionFrequency()
}

data class InsertionSpec(
    val frames: List<Int>,
    val frequency: InsertionFrequency,
    val exclusive: Boolean = false,
    val frameDuration: FrameDurationSpec? = null,
)

data class AnimationSpec(
    val frames: List<Int>,
    val frameDuration: FrameDurationSpec,
    val loop: Boolean = true,
    val insertions: List<InsertionSpec> = emptyList(),
)

private data class InsertionSettingsKey(
    val enabled: Boolean,
    val everyNLoops: Int,
    val probabilityPercent: Int,
    val cooldownLoops: Int,
    val exclusive: Boolean,
    val intervalMs: Int,
    val patterns: List<InsertionPattern>,
)

private data class SyncDiagnostics(
    val loopCount: Int,
    val tickIndex: Long,
)

// 挿入判定は InsertionAnimationSettings に統一し、旧 insertions は無効化する。
private val statusAnimationMap: Map<LamiSpriteStatus, AnimationSpec> = mapOf(
    LamiSpriteStatus.Idle to AnimationSpec(
        frames = listOf(0, 8, 0, 5, 0),
        frameDuration = FrameDurationSpec(minMs = 420L, maxMs = 560L, jitterFraction = 0.2f),
        loop = true,
        insertions = emptyList(),
    ),
    LamiSpriteStatus.Thinking to AnimationSpec(
        frames = listOf(4, 4, 4, 7, 4, 4, 4),
        frameDuration = FrameDurationSpec(minMs = 220L, maxMs = 280L, jitterFraction = 0.1f),
        loop = true,
        insertions = emptyList(),
    ),
    LamiSpriteStatus.TalkShort to AnimationSpec(
        frames = listOf(0, 6, 2, 6, 0),
        frameDuration = FrameDurationSpec(minMs = 120L, maxMs = 140L, jitterFraction = 0.1f),
        insertions = emptyList(),
    ),
    LamiSpriteStatus.TalkLong to AnimationSpec(
        frames = listOf(0, 4, 6, 4, 4, 6, 4, 0),
        frameDuration = FrameDurationSpec(minMs = 170L, maxMs = 210L, jitterFraction = 0.1f),
        loop = true,
        insertions = emptyList(),
    ),
    LamiSpriteStatus.TalkCalm to AnimationSpec(
        frames = listOf(7, 4, 7, 8, 7),
        frameDuration = FrameDurationSpec(minMs = 240L, maxMs = 320L, jitterFraction = 0.1f),
        insertions = emptyList(),
    ),
    LamiSpriteStatus.ErrorLight to AnimationSpec(
        frames = SpriteAnimationDefaults.ERROR_LIGHT_FRAMES,
        frameDuration = FrameDurationSpec(
            minMs = SpriteAnimationDefaults.ERROR_LIGHT_INTERVAL_MS.toLong(),
            maxMs = SpriteAnimationDefaults.ERROR_LIGHT_INTERVAL_MS.toLong(),
            jitterFraction = 0f,
        ),
        insertions = emptyList(),
    ),
    LamiSpriteStatus.ErrorHeavy to AnimationSpec(
        frames = SpriteAnimationDefaults.ERROR_HEAVY_FRAMES,
        frameDuration = FrameDurationSpec(
            minMs = SpriteAnimationDefaults.ERROR_HEAVY_BASE_INTERVAL_MS.toLong(),
            maxMs = SpriteAnimationDefaults.ERROR_HEAVY_BASE_INTERVAL_MS.toLong(),
            jitterFraction = 0f,
        ),
        insertions = emptyList(),
    ),
    LamiSpriteStatus.OfflineLoop to AnimationSpec(
        frames = listOf(8, 8),
        frameDuration = FrameDurationSpec(minMs = 1_000L, maxMs = 1_500L),
        loop = true,
        insertions = emptyList(),
    ),
    LamiSpriteStatus.Ready to AnimationSpec(
        frames = listOf(0),
        frameDuration = FrameDurationSpec(minMs = 700L, maxMs = 1_200L, jitterFraction = 0.15f),
        insertions = emptyList(),
    ),
)

private fun LamiSpriteStatus.toSpriteStateOrNull(): SpriteState? = when (this) {
    LamiSpriteStatus.Ready -> SpriteState.READY
    LamiSpriteStatus.Idle -> SpriteState.IDLE
    LamiSpriteStatus.Thinking -> SpriteState.THINKING
    LamiSpriteStatus.TalkShort -> SpriteState.SPEAKING
    LamiSpriteStatus.TalkLong -> SpriteState.TALK_LONG
    LamiSpriteStatus.TalkCalm -> SpriteState.TALK_CALM
    LamiSpriteStatus.ErrorLight,
    LamiSpriteStatus.ErrorHeavy,
    -> SpriteState.ERROR
    LamiSpriteStatus.OfflineLoop -> SpriteState.OFFLINE
}

private fun animSpecFromPerStateJsonOrFallback(
    json: String?,
    fallback: AnimationSpec,
    maxFrameIndex: Int,
): AnimationSpec {
    if (json.isNullOrBlank()) return fallback
    return runCatching {
        val root = JSONObject(json)
        val base = root.getJSONObject("base")
        val framesJson = base.getJSONArray("frames")
        val frames = buildList(framesJson.length()) {
            for (index in 0 until framesJson.length()) {
                add(framesJson.getInt(index))
            }
        }.ifEmpty { fallback.frames }
        val intervalMs = base.getInt("intervalMs").coerceAtLeast(1)
        val clampedFrames = frames.map { it.coerceIn(0, maxFrameIndex) }
        AnimationSpec(
            frames = clampedFrames,
            frameDuration = FrameDurationSpec(
                minMs = intervalMs.toLong(),
                maxMs = intervalMs.toLong(),
                jitterFraction = 0f,
            ),
            loop = true,
            insertions = emptyList(),
        )
    }.getOrElse { fallback }
}

private fun selectInsertionSettingsForStatus(
    status: LamiSpriteStatus,
    readySettings: InsertionAnimationSettings,
    talkingSettings: InsertionAnimationSettings,
): InsertionAnimationSettings? =
    when (status) {
        LamiSpriteStatus.Ready,
        LamiSpriteStatus.Idle,
        LamiSpriteStatus.Thinking,
        LamiSpriteStatus.ErrorLight,
        LamiSpriteStatus.ErrorHeavy,
        -> readySettings
        LamiSpriteStatus.TalkShort,
        LamiSpriteStatus.TalkLong,
        LamiSpriteStatus.TalkCalm,
        -> talkingSettings
        LamiSpriteStatus.OfflineLoop,
        -> null
    }

private fun resolveAnimationKeyForTrace(perStateAnimJson: String?): String {
    if (perStateAnimJson.isNullOrBlank()) return "fallback"
    return runCatching {
        val root = JSONObject(perStateAnimJson)
        val directAnimationKey = root.optString("animationKey").takeIf { it.isNotBlank() }
        val directKey = root.optString("key").takeIf { it.isNotBlank() }
        val metaAnimationKey = root.optJSONObject("meta")
            ?.optString("animationKey")
            ?.takeIf { it.isNotBlank() }
        directAnimationKey ?: directKey ?: metaAnimationKey ?: "per-state-json(no-key-extracted)"
    }.getOrElse {
        "per-state-json(no-key-extracted)"
    }
}

internal fun selectWeightedInsertionPattern(
    patterns: List<InsertionPattern>,
    random: Random,
): Pair<Int, InsertionPattern>? {
    // 抽選対象は weight>0 かつ frameSequence が空でないものに限定する
    val candidates = patterns.withIndex().filter { (_, pattern) ->
        pattern.weight > 0 && pattern.frameSequence.isNotEmpty()
    }
    if (candidates.isEmpty()) return null
    val totalWeight = candidates.sumOf { (_, pattern) -> pattern.weight }
    if (totalWeight <= 0) return null
    val roll = random.nextInt(totalWeight)
    var cursor = 0
    for ((index, pattern) in candidates) {
        cursor += pattern.weight
        if (roll < cursor) return index to pattern
    }
    return candidates.lastOrNull()?.let { (index, pattern) -> index to pattern }
}

private data class DeterministicInsertionDecision(
    val patternIndex: Int,
    val frames: List<Int>,
    val intervalMs: Int,
    val exclusive: Boolean,
)

private class DeterministicInsertionCache {
    var lastComputedLoop: Int = 0
    var lastInsertionLoop: Int? = null
    var lastDecisionLoop: Int = 0
    var lastDecision: DeterministicInsertionDecision? = null
}

private val DETERMINISTIC_GOLDEN_GAMMA: Long = 0x9E3779B97F4A7C15uL.toLong()
private val DETERMINISTIC_MIX1: Long = 0xBF58476D1CE4E5B9uL.toLong()
private val DETERMINISTIC_MIX2: Long = 0x94D049BB133111EBuL.toLong()

private fun deterministicSeed(
    syncEpochMs: Long,
    status: LamiSpriteStatus,
    loopCount: Int,
    insertionKey: Int,
    salt: Long,
): Long {
    val base = syncEpochMs xor (status.ordinal.toLong() shl 32) xor insertionKey.toLong()
    val mixed = base + (loopCount.toLong() * DETERMINISTIC_GOLDEN_GAMMA) + salt
    return mixed
}

private fun deterministicNextInt(seed: Long, bound: Int): Int {
    if (bound <= 0) return 0
    var value = seed
    value = value xor (value ushr 30)
    value *= DETERMINISTIC_MIX1
    value = value xor (value ushr 27)
    value *= DETERMINISTIC_MIX2
    value = value xor (value ushr 31)
    val positive = value ushr 1
    return (positive % bound.toLong()).toInt()
}

private fun selectWeightedInsertionPatternDeterministic(
    patterns: List<InsertionPattern>,
    seed: Long,
): Pair<Int, InsertionPattern>? {
    // 抽選対象は weight>0 かつ frameSequence が空でないものに限定する
    val candidates = patterns.withIndex().filter { (_, pattern) ->
        pattern.weight > 0 && pattern.frameSequence.isNotEmpty()
    }
    if (candidates.isEmpty()) return null
    val totalWeight = candidates.sumOf { (_, pattern) -> pattern.weight }
    if (totalWeight <= 0) return null
    val roll = deterministicNextInt(seed, totalWeight)
    var cursor = 0
    for ((index, pattern) in candidates) {
        cursor += pattern.weight
        if (roll < cursor) return index to pattern
    }
    return candidates.lastOrNull()?.let { (index, pattern) -> index to pattern }
}

internal fun shouldAttemptInsertionDeterministic(
    settings: InsertionAnimationSettings,
    loopCount: Int,
    lastInsertionLoop: Int?,
    seed: Long,
): Boolean {
    if (!settings.enabled) return false
    val hasCooldown = settings.cooldownLoops > 0 && lastInsertionLoop != null
    if (hasCooldown && (loopCount - lastInsertionLoop) < settings.cooldownLoops) return false
    if (settings.everyNLoops <= 0) return false
    if (loopCount % settings.everyNLoops != 0) return false
    if (settings.probabilityPercent == 0) return false
    if (settings.probabilityPercent == 100) return true
    val roll = deterministicNextInt(seed, 100) // roll は 0..99
    return roll < settings.probabilityPercent
}

private fun createSyncFrameResolver(
    animSpec: AnimationSpec,
    syncEpochMs: Long,
    insertionSettings: InsertionAnimationSettings?,
    insertionKey: Int,
    insertionCache: DeterministicInsertionCache,
    resolvedStatus: LamiSpriteStatus,
    maxFrameIndex: Int,
): (Long) -> SpriteFrameSample {
    val baseFrames = animSpec.frames.ifEmpty { listOf(0) }
    val baseIntervalMs = animSpec.frameDuration.minMs.coerceAtLeast(1L)
    val loopDurationMs = baseIntervalMs * baseFrames.size
    val canInsert = insertionSettings?.let { settings ->
        settings.enabled && settings.everyNLoops > 0 && settings.probabilityPercent > 0 &&
            settings.patterns.any { it.weight > 0 && it.frameSequence.isNotEmpty() }
    } == true
    var cachedLoop = -1
    var cachedDecision: DeterministicInsertionDecision? = null
    var cachedTimeline: SpriteEventTimeline? = null
    return { nowMs ->
        val elapsedMs = (nowMs - syncEpochMs).coerceAtLeast(0L)
        val loopCount = (elapsedMs / loopDurationMs).toInt() + 1
        if (cachedTimeline == null || (canInsert && cachedLoop != loopCount)) {
            val insertionDecision = insertionSettings?.takeIf { canInsert }?.let { settings ->
                if (loopCount < insertionCache.lastComputedLoop) {
                    insertionCache.lastComputedLoop = 0
                    insertionCache.lastInsertionLoop = null
                    insertionCache.lastDecisionLoop = 0
                    insertionCache.lastDecision = null
                }
                for (loop in (insertionCache.lastComputedLoop + 1)..loopCount) {
                    val attemptSeed = deterministicSeed(
                        syncEpochMs = syncEpochMs,
                        status = resolvedStatus,
                        loopCount = loop,
                        insertionKey = insertionKey,
                        salt = 0x51C7FCD39C65E5E0L,
                    )
                    val shouldInsert = shouldAttemptInsertionDeterministic(
                        settings = settings,
                        loopCount = loop,
                        lastInsertionLoop = insertionCache.lastInsertionLoop,
                        seed = attemptSeed,
                    )
                    val decision = if (shouldInsert && settings.patterns.isNotEmpty()) {
                        val defaultIntervalMs = effectiveInsertionIntervalMs(
                            settings,
                            settings.intervalMs ?: InsertionAnimationSettings.DEFAULT.intervalMs ?: 0,
                        )
                        val patternSeed = deterministicSeed(
                            syncEpochMs = syncEpochMs,
                            status = resolvedStatus,
                            loopCount = loop,
                            insertionKey = insertionKey,
                            salt = DETERMINISTIC_GOLDEN_GAMMA,
                        )
                        val selection = selectWeightedInsertionPatternDeterministic(
                            patterns = settings.patterns,
                            seed = patternSeed,
                        )
                        selection?.let { (patternIndex, pattern) ->
                            val resolvedIntervalMs = pattern.intervalMs ?: defaultIntervalMs
                            DeterministicInsertionDecision(
                                patternIndex = patternIndex,
                                frames = pattern.frameSequence.toList(),
                                intervalMs = resolvedIntervalMs,
                                exclusive = settings.exclusive,
                            )
                        }
                    } else {
                        null
                    }
                    if (decision != null) {
                        insertionCache.lastInsertionLoop = loop
                    }
                    insertionCache.lastComputedLoop = loop
                    if (loop == loopCount) {
                        insertionCache.lastDecisionLoop = loop
                        insertionCache.lastDecision = decision
                    }
                }
                if (insertionCache.lastDecisionLoop == loopCount) {
                    insertionCache.lastDecision
                } else {
                    null
                }
            }
            if (cachedTimeline == null || cachedDecision != insertionDecision) {
                cachedTimeline = SpriteEventTimeline(
                    baseFrames = baseFrames,
                    intervalMs = baseIntervalMs,
                    insertionFrames = insertionDecision?.frames.orEmpty(),
                    insertionIntervalMs = insertionDecision?.intervalMs?.toLong() ?: baseIntervalMs,
                    exclusive = insertionDecision?.exclusive == true,
                    maxFrameIndex = maxFrameIndex,
                )
                cachedDecision = insertionDecision
            }
            cachedLoop = loopCount
        }
        requireNotNull(cachedTimeline).sample(elapsedMs % loopDurationMs, canInsert)
    }
}

@Composable
fun LamiStatusSprite(
    status: LamiSpriteStatus,
    modifier: Modifier = Modifier,
    layout: LamiStatusSpriteLayout = LamiStatusSpriteLayout(),
    options: LamiStatusSpriteOptions = LamiStatusSpriteOptions(),
    frameOverrides: LamiStatusSpriteFrameOverrides = LamiStatusSpriteFrameOverrides(),
    resolvedErrorKey: String? = null,
    syncEpochMs: Long = 0L,
    trace: LamiStatusSpriteTrace = LamiStatusSpriteTrace(),
) {
    val sizeDp = layout.sizeDp
    val maxSizeDp = layout.maxSizeDp
    val contentOffsetDp = layout.contentOffsetDp
    val contentOffsetYDp = layout.contentOffsetYDp
    val animationsEnabled = options.animationsEnabled
    val replacementEnabled = options.replacementEnabled
    val blinkEffectEnabled = options.blinkEffectEnabled
    val debugOverlayEnabled = options.debugOverlayEnabled
    val frameXOffsetPxMap = frameOverrides.frameXOffsetPxMap
    val frameYOffsetPxMap = frameOverrides.frameYOffsetPxMap
    val frameSrcOffsetMap = frameOverrides.frameSrcOffsetMap
    val frameSrcSizeMap = frameOverrides.frameSrcSizeMap
    val autoCropTransparentArea = frameOverrides.autoCropTransparentArea
    val debugOverloadLabel = trace.debugOverloadLabel
    val traceLamiStatus = trace.lamiStatus
    val traceLamiState = trace.lamiState
    val traceResolvedAnimationStatus = trace.resolvedAnimationStatus
    val traceIsSpeaking = trace.isSpeaking
    val overlayOn = DEBUG_OVERLAY_ENABLED && debugOverlayEnabled
    val constrainedSize = remember(sizeDp, maxSizeDp) { sizeDp.coerceIn(32.dp, maxSizeDp) }
    val spriteFrameRepository = rememberSpriteFrameRepository()
    val frameMaps = rememberSpriteFrameMaps(repository = spriteFrameRepository)
    val defaultConfig = remember { SpriteSheetConfig.default3x3() }
    val spriteSheetConfig by spriteFrameRepository.spriteSheetConfig.collectAsState(initial = defaultConfig)
    val maxFrameIndex = remember(spriteSheetConfig) { (spriteSheetConfig.frameCount - 1).coerceAtLeast(0) }
    val resolvedFrameSrcOffsetMap = remember(
        autoCropTransparentArea,
        frameSrcOffsetMap,
        frameMaps,
    ) {
        if (!autoCropTransparentArea) {
            emptyMap()
        } else if (frameSrcOffsetMap.isNotEmpty()) {
            frameSrcOffsetMap
        } else {
            frameMaps.offsetMap
        }
    }
    val resolvedFrameSrcSizeMap = remember(autoCropTransparentArea, frameSrcSizeMap, frameMaps) {
        if (!autoCropTransparentArea) {
            emptyMap()
        } else if (frameSrcSizeMap.isNotEmpty()) {
            frameSrcSizeMap
        } else {
            frameMaps.sizeMap
        }
    }
    val context = LocalContext.current
    val settingsPreferences = remember(context) {
        SettingsPreferences(context.applicationContext)
    }
    val selectedErrorKeyFlow = remember(settingsPreferences) { settingsPreferences.selectedKeyFlow(SpriteState.ERROR) }
    val storedErrorSelectedKey by selectedErrorKeyFlow.collectAsState(initial = null)
    val errorCause by settingsPreferences.errorCauseFlow.collectAsState(initial = ErrorCause.UNKNOWN)
    val resolvedErrorKeyFromStore = remember(storedErrorSelectedKey, errorCause) {
        resolveErrorKey(storedErrorSelectedKey, errorCause)
    }
    val finalResolvedErrorKey = resolvedErrorKey ?: resolvedErrorKeyFromStore
    val errorAdjustedStatus = remember(status, finalResolvedErrorKey) {
        if (finalResolvedErrorKey.isNullOrBlank()) {
            status
        } else if (status == LamiSpriteStatus.ErrorLight || status == LamiSpriteStatus.ErrorHeavy) {
            if (finalResolvedErrorKey == "ErrorHeavy") {
                LamiSpriteStatus.ErrorHeavy
            } else {
                LamiSpriteStatus.ErrorLight
            }
        } else {
            status
        }
    }
    val resolvedStatus = remember(errorAdjustedStatus, replacementEnabled, blinkEffectEnabled) {
        when {
            !replacementEnabled -> LamiSpriteStatus.Idle
            !blinkEffectEnabled && errorAdjustedStatus == LamiSpriteStatus.Ready -> LamiSpriteStatus.Idle
            else -> errorAdjustedStatus
        }
    }
    val spriteStateForAnim = remember(resolvedStatus) { resolvedStatus.toSpriteStateOrNull() }
    val perStateAnimJson by remember(spriteStateForAnim) {
        spriteStateForAnim?.let { settingsPreferences.resolvedSpriteAnimationJsonFlow(it) } ?: flowOf(null)
    }.collectAsState(initial = null)
    val fallbackAnimSpec = remember(resolvedStatus) {
        statusAnimationMap[resolvedStatus] ?: statusAnimationMap.getValue(LamiSpriteStatus.Idle)
    }
    val animSpec = remember(perStateAnimJson, fallbackAnimSpec, maxFrameIndex) {
        animSpecFromPerStateJsonOrFallback(
            json = perStateAnimJson,
            fallback = fallbackAnimSpec,
            maxFrameIndex = maxFrameIndex,
        )
    }
    val readyInsertionSettings by settingsPreferences.readyInsertionAnimationSettings.collectAsState(
        initial = InsertionAnimationSettings.READY_DEFAULT,
    )
    val talkingInsertionSettings by settingsPreferences.talkingInsertionAnimationSettings.collectAsState(
        initial = InsertionAnimationSettings.TALKING_DEFAULT,
    )
    val insertionSettings = remember(resolvedStatus, readyInsertionSettings, talkingInsertionSettings) {
        selectInsertionSettingsForStatus(
            status = resolvedStatus,
            readySettings = readyInsertionSettings,
            talkingSettings = talkingInsertionSettings,
        )
    }
    val resolvedAnimationKeyForTrace = remember(perStateAnimJson) {
        resolveAnimationKeyForTrace(perStateAnimJson)
    }
    // 挿入設定の変更検知用キー（null は 0 固定）
    val insertionKey = remember(insertionSettings) {
        insertionSettings?.let { settings ->
            val effectiveIntervalMs = effectiveInsertionIntervalMs(
                settings,
                settings.intervalMs ?: InsertionAnimationSettings.DEFAULT.intervalMs ?: 0,
            )
            InsertionSettingsKey(
                enabled = settings.enabled,
                everyNLoops = settings.everyNLoops,
                probabilityPercent = settings.probabilityPercent,
                cooldownLoops = settings.cooldownLoops,
                exclusive = settings.exclusive,
                intervalMs = effectiveIntervalMs,
                patterns = settings.patterns,
            ).hashCode()
        } ?: 0
    }
    // 設定変更時は Effect 開始時にループ状態をリセットする
    val loopCountState = remember(resolvedStatus) { mutableStateOf(0) }
    // 設定変更時は Effect 開始時にクールダウン状態もリセットする
    val lastInsertionLoopState = remember(resolvedStatus) { mutableStateOf<Int?>(null) }
    // 挿入イベントの確定値を保持する
    var lastInsertionPatternIndex by remember(resolvedStatus, insertionKey) { mutableStateOf<Int?>(null) }
    var lastInsertionResolvedIntervalMs by remember(resolvedStatus, insertionKey) { mutableStateOf<Int?>(null) }
    var lastInsertionFrames by remember(resolvedStatus, insertionKey) { mutableStateOf<List<Int>?>(null) }
    val lastLoggedSyncLoopState = remember(syncEpochMs, resolvedStatus, insertionKey) {
        mutableStateOf<Int?>(null)
    }
    var lastLoggedSyncAtMs by remember(syncEpochMs, resolvedStatus, insertionKey) { mutableStateOf(0L) }
    // Effect を再起動せずに最新設定を即時反映するため rememberUpdatedState を使う
    val insertionSettingsLatest by rememberUpdatedState(insertionSettings)

    val useSyncMode = syncEpochMs > 0L
    val insertionCache = remember(syncEpochMs, resolvedStatus, insertionKey) {
        DeterministicInsertionCache()
    }
    var syncTimeMs by remember(syncEpochMs) { mutableStateOf(SystemClock.uptimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val syncDiagnostics by remember(useSyncMode, animationsEnabled, animSpec, syncEpochMs) {
        derivedStateOf {
            if (!useSyncMode || !animationsEnabled) {
                SyncDiagnostics(loopCount = 0, tickIndex = 0L)
            } else {
                val baseFrames = animSpec.frames.ifEmpty { listOf(0) }
                val baseIntervalMs = animSpec.frameDuration.minMs.coerceAtLeast(1L)
                val loopDurationMs = baseIntervalMs * baseFrames.size
                val elapsedMs = (syncTimeMs - syncEpochMs).coerceAtLeast(0L)
                val loopCount = if (loopDurationMs > 0L) {
                    (elapsedMs / loopDurationMs).toInt() + 1
                } else {
                    1
                }
                val loopElapsedMs = if (loopDurationMs > 0L) {
                    (elapsedMs % loopDurationMs).toInt()
                } else {
                    0
                }
                val ticksPerFrame = if (baseIntervalMs > 0L) baseIntervalMs else 1L
                val tickIndex = if (ticksPerFrame > 0L) {
                    (loopElapsedMs / ticksPerFrame).coerceAtLeast(0)
                } else {
                    0
                }
                SyncDiagnostics(loopCount = loopCount, tickIndex = tickIndex)
            }
        }
    }
    LaunchedEffect(
        useSyncMode,
        animationsEnabled,
        animSpec,
        resolvedStatus,
        insertionKey,
        lastInsertionPatternIndex,
        lastInsertionFrames,
        lastInsertionResolvedIntervalMs,
        syncEpochMs,
    ) {
        if (!BuildConfig.DEBUG || !useSyncMode || !animationsEnabled) {
            return@LaunchedEffect
        }
        // Diagnostics observe the clock in a coroutine, not in the composition's keys.
        snapshotFlow { syncDiagnostics }.collect { diagnostics ->
            val loopCount = diagnostics.loopCount
            val nowMs = SystemClock.uptimeMillis()
            val lastLoggedLoop = lastLoggedSyncLoopState.value
            val shouldLog = loopCount != lastLoggedLoop || nowMs - lastLoggedSyncAtMs >= 1_000L
            if (!shouldLog) {
                return@collect
            }
            Log.d(
                "LamiSync",
                "useSyncMode=$useSyncMode " +
                    "syncEpochMs=$syncEpochMs " +
                    "resolvedStatus=$resolvedStatus " +
                    "loopCount=$loopCount " +
                    "tickIndex=${diagnostics.tickIndex} " +
                    "insertionKey=$insertionKey " +
                    "lastInsertionPatternIndex=$lastInsertionPatternIndex " +
                    "lastInsertionFrames=$lastInsertionFrames " +
                    "lastInsertionResolvedIntervalMs=$lastInsertionResolvedIntervalMs",
            )
            lastLoggedSyncLoopState.value = loopCount
            lastLoggedSyncAtMs = nowMs
        }
    }

    LaunchedEffect(resolvedStatus, perStateAnimJson, animSpec) {
        if (overlayOn) {
            val json = perStateAnimJson
            Log.d(
                "LamiStatusSprite",
                "resolvedStatus=$resolvedStatus spriteState=$spriteStateForAnim " +
                    "baseMs=${animSpec.frameDuration.minMs} frames=${animSpec.frames} " +
                    "json=${json?.take(80)}",
            )
        }
    }
    val tracePayload = remember(
        traceLamiStatus,
        traceLamiState,
        traceResolvedAnimationStatus,
        resolvedStatus,
        spriteStateForAnim,
        resolvedAnimationKeyForTrace,
        animSpec.frames,
        animSpec.frameDuration.minMs,
        traceIsSpeaking,
    ) {
        "lamiStatus=$traceLamiStatus " +
            "lamiState=$traceLamiState " +
            "resolvedAnimationStatus=$traceResolvedAnimationStatus " +
            "resolvedSpriteStatus=$resolvedStatus " +
            "spriteStateForAnim=$spriteStateForAnim " +
            "animationKey=$resolvedAnimationKeyForTrace " +
            "frameSequence=${animSpec.frames} " +
            "intervalMs=${animSpec.frameDuration.minMs} " +
            "isSpeaking=$traceIsSpeaking"
    }
    val lastTracePayload = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(BuildConfig.DEBUG, tracePayload) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        if (lastTracePayload.value == tracePayload) return@LaunchedEffect
        Log.d("LamiSpriteTrace", tracePayload)
        appendSpriteTraceToFile(
            context = context,
            line = tracePayload,
        )
        lastTracePayload.value = tracePayload
    }

    val currentFrameState = remember(resolvedStatus, maxFrameIndex) {
        mutableStateOf(animSpec.frames.firstOrNull()?.coerceIn(0, maxFrameIndex) ?: 0)
    }
    var currentFrameIndex by currentFrameState
    val resolveSyncSample = remember(
        animSpec, syncEpochMs, insertionSettings, insertionKey, insertionCache, resolvedStatus, maxFrameIndex,
    ) {
        createSyncFrameResolver(
            animSpec = animSpec,
            syncEpochMs = syncEpochMs,
            insertionSettings = insertionSettings,
            insertionKey = insertionKey,
            insertionCache = insertionCache,
            resolvedStatus = resolvedStatus,
            maxFrameIndex = maxFrameIndex,
        )
    }
    val syncFrameState = remember(animSpec, maxFrameIndex) {
        mutableStateOf(animSpec.frames.firstOrNull()?.coerceIn(0, maxFrameIndex) ?: 0)
    }
    var syncFrameIndex by syncFrameState
    LaunchedEffect(lifecycleOwner, syncEpochMs, animationsEnabled, useSyncMode, resolveSyncSample) {
        if (RuntimeFlags.shouldDisableContinuousAnimations()) return@LaunchedEffect
        if (!useSyncMode || !animationsEnabled) {
            syncFrameIndex = animSpec.frames.firstOrNull()?.coerceIn(0, maxFrameIndex) ?: 0
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.runSpriteEventClock(SystemClock::uptimeMillis) { now ->
            val sample = resolveSyncSample(now)
            syncTimeMs = now
            syncFrameIndex = sample.frame
            sample.delayMs
        }
    }
    val frameIndexProvider = remember(useSyncMode, syncFrameState, currentFrameState) {
        { if (useSyncMode) syncFrameState.value else currentFrameState.value }
    }
    // Only the optional diagnostic text needs a composition-time frame read.
    val resolvedFrameIndex = if (overlayOn) frameIndexProvider() else 0
    val currentFrameXOffsetPx = frameXOffsetPxMap[resolvedFrameIndex] ?: 0
    val currentFrameYOffsetPx = frameYOffsetPxMap[resolvedFrameIndex] ?: 0
    val debugOverlayText = remember(
        overlayOn,
        debugOverloadLabel,
        lastInsertionResolvedIntervalMs,
        lastInsertionFrames,
        resolvedStatus,
        spriteStateForAnim,
        perStateAnimJson,
        animSpec,
        resolvedFrameIndex,
        currentFrameXOffsetPx,
        currentFrameYOffsetPx,
    ) {
        if (!overlayOn) {
            ""
        } else {
            val json = perStateAnimJson
            val perStateJsonState = when {
                json == null -> "null"
                json.isBlank() -> "blank"
                else -> "present"
            }
            val animationKey = when {
                json.isNullOrBlank() -> "fallback"
                else -> "json:${json.hashCode()}"
            }
            "usedOverload=$debugOverloadLabel\n" +
                "resolvedStatus=$resolvedStatus spriteState=$spriteStateForAnim\n" +
                "animationKey=$animationKey perStateAnimJson=$perStateJsonState\n" +
                "baseFrames=${animSpec.frames} baseIntervalMs=${animSpec.frameDuration.minMs}\n" +
                "currentFrameIndex=$resolvedFrameIndex\n" +
                "dstOffsetPx=(x=$currentFrameXOffsetPx, y=$currentFrameYOffsetPx)"
        }
    }

    if (!useSyncMode) {
        LaunchedEffect(lifecycleOwner, resolvedStatus, animationsEnabled, animSpec, insertionKey) {
            if (RuntimeFlags.shouldDisableContinuousAnimations()) {
                return@LaunchedEffect
            }
            loopCountState.value = 0
            lastInsertionLoopState.value = null
            lastInsertionPatternIndex = null
            lastInsertionResolvedIntervalMs = null
            lastInsertionFrames = null
            currentFrameIndex = animSpec.frames.firstOrNull()?.coerceIn(0, maxFrameIndex) ?: 0
            if (!animationsEnabled || animSpec.frames.isEmpty()) {
                return@LaunchedEffect
            }

            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val random = Random(System.currentTimeMillis())

                suspend fun playInsertionFrames(frameSequence: List<Int>, intervalMs: Int) {
                    if (frameSequence.isEmpty()) return
                    // 設定の intervalMs を固定間隔として使用する
                    val resolvedIntervalMs = intervalMs.toLong().coerceAtLeast(MIN_SPRITE_FRAME_DELAY_MS)
                    for (frame in frameSequence) {
                        currentFrameIndex = frame.coerceIn(0, maxFrameIndex)
                        delay(resolvedIntervalMs)
                    }
                }

                while (true) {
                    loopCountState.value += 1
                    val loopCount = loopCountState.value
                    val lastInsertionLoop = lastInsertionLoopState.value
                    val settings = insertionSettingsLatest
                    // 設定に基づく挿入判定はループ単位で行う（挿入の可否は shouldAttemptInsertion のみで決定）
                    val shouldInsert = settings?.shouldAttemptInsertion(
                        loopCount = loopCount,
                        lastInsertionLoop = lastInsertionLoop,
                        random = random,
                    ) == true
                    val selection = if (shouldInsert) selectWeightedInsertionPattern(settings.patterns, random) else null
                    if (selection != null) {
                        val activeSettings = requireNotNull(settings)
                        val defaultIntervalMs = effectiveInsertionIntervalMs(
                            activeSettings,
                            activeSettings.intervalMs ?: InsertionAnimationSettings.DEFAULT.intervalMs ?: 0,
                        )
                        // 挿入イベント内で重み付き抽選を行う（weight/frames が有効なもののみ）
                        val (patternIndex, pattern) = selection
                        val resolvedIntervalMs = pattern.intervalMs ?: defaultIntervalMs
                        lastInsertionPatternIndex = patternIndex
                        lastInsertionResolvedIntervalMs = resolvedIntervalMs
                        lastInsertionFrames = pattern.frameSequence.toList()
                        if (BuildConfig.DEBUG) {
                            // 実効 interval の決定根拠をログで確認できるようにする
                            Log.d(
                                "LamiStatusSprite",
                                "insertion pick: status=$resolvedStatus loopCount=$loopCount " +
                                    "patternIndex=$patternIndex " +
                                    "frames=${pattern.frameSequence} " +
                                    "patternInterval=${pattern.intervalMs} " +
                                    "defaultInterval=$defaultIntervalMs " +
                                    "resolvedInterval=$resolvedIntervalMs " +
                                    "weight=${pattern.weight} lastInsertionLoop=$lastInsertionLoop"
                            )
                        }
                        playInsertionFrames(
                            frameSequence = pattern.frameSequence,
                            intervalMs = resolvedIntervalMs,
                        )
                        lastInsertionLoopState.value = loopCount
                        if (activeSettings.exclusive) {
                            // exclusive：挿入が発生したループでは Base を再生せず次へ進む
                            if (!animSpec.loop) {
                                break
                            }
                            continue
                        }
                    }

                    for (frame in animSpec.frames) {
                        currentFrameIndex = frame.coerceIn(0, maxFrameIndex)
                        delay(animSpec.frameDuration.draw(random).coerceAtLeast(MIN_SPRITE_FRAME_DELAY_MS))
                    }

                    if (!animSpec.loop) {
                        break
                    }
                }
            }
        }
    }

    Box(modifier = modifier) {
        LamiSprite3x3(
            frameIndex = 0,
            frameIndexProvider = frameIndexProvider,
            modifier = Modifier,
            layout = LamiSprite3x3Layout(
                sizeDp = constrainedSize,
                contentOffsetDp = contentOffsetDp,
                contentOffsetYDp = contentOffsetYDp,
            ),
            frameOverrides = LamiSprite3x3FrameOverrides(
                frameXOffsetPxMap = frameXOffsetPxMap,
                frameYOffsetPxMap = frameYOffsetPxMap,
                frameSrcOffsetMap = resolvedFrameSrcOffsetMap,
                frameSrcSizeMap = resolvedFrameSrcSizeMap,
                autoCropTransparentArea = autoCropTransparentArea,
                frameSizePx = frameMaps.frameSize,
                frameMaps = frameMaps,
            ),
            spriteSheetConfig = spriteSheetConfig,
        )
        if (overlayOn) {
            Text(
                text = debugOverlayText,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                    )
                    // デバッグ表示の読みやすさのため最小限の内側余白
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 11.sp,
            )
        }
    }
}

@Composable
fun LamiStatusSprite(
    status: State<LamiStatus>,
    lamiState: LamiState? = null,
    modifier: Modifier = Modifier,
    layout: LamiStatusSpriteLayout = LamiStatusSpriteLayout(),
    options: LamiStatusSpriteOptions = LamiStatusSpriteOptions(),
    frameOverrides: LamiStatusSpriteFrameOverrides = LamiStatusSpriteFrameOverrides(),
    resolvedErrorKey: String? = null,
    syncEpochMs: Long = 0L,
) {
    val resolvedAnimationStatusForTrace = remember(status.value) {
        status.value.toAnimationStatus()
    }
    val isSpeakingForTrace = lamiState is LamiState.Speaking
    val spriteStatus = remember(status.value, lamiState) {
        mapToLamiSpriteStatus(
            lamiStatus = status.value,
            lamiState = lamiState,
        )
    }
    LamiStatusSprite(
        status = spriteStatus,
        modifier = modifier,
        layout = layout,
        options = options,
        frameOverrides = frameOverrides,
        resolvedErrorKey = resolvedErrorKey,
        syncEpochMs = syncEpochMs,
        trace = LamiStatusSpriteTrace(
            debugOverloadLabel = "wrapper(status: State<LamiStatus>)",
            lamiStatus = status.value,
            lamiState = lamiState,
            resolvedAnimationStatus = resolvedAnimationStatusForTrace,
            isSpeaking = isSpeakingForTrace,
        ),
    )
}

@Composable
fun LamiStatusSprite(
    status: State<LamiAnimationStatus>,
    modifier: Modifier = Modifier,
    layout: LamiStatusSpriteLayout = LamiStatusSpriteLayout(),
    options: LamiStatusSpriteOptions = LamiStatusSpriteOptions(),
    selectedModel: String? = null,
    lastError: String? = null,
    retryCount: Int = 0,
    talkingTextLength: Int? = null,
    frameOverrides: LamiStatusSpriteFrameOverrides = LamiStatusSpriteFrameOverrides(),
    resolvedErrorKey: String? = null,
    syncEpochMs: Long = 0L,
) {
    var previousAnimationStatus by remember {
        mutableStateOf(status.value)
    }
    val animationStatus = status.value
    val spriteStatus = remember(
        animationStatus,
        selectedModel,
        lastError,
        retryCount,
        talkingTextLength,
    ) {
        mapToLamiSpriteStatus(
            animationStatus = animationStatus,
            selectedModel = selectedModel,
            lastError = lastError,
            retryCount = retryCount,
            talkingTextLength = talkingTextLength,
            previousAnimationStatus = previousAnimationStatus,
        )
    }
    LaunchedEffect(animationStatus) {
        previousAnimationStatus = animationStatus
    }
    LamiStatusSprite(
        status = spriteStatus,
        modifier = modifier,
        layout = layout,
        options = options,
        frameOverrides = frameOverrides,
        resolvedErrorKey = resolvedErrorKey,
        syncEpochMs = syncEpochMs,
        trace = LamiStatusSpriteTrace(
            debugOverloadLabel = "wrapper(status: State<LamiAnimationStatus>)",
        ),
    )
}

fun mapToLamiSpriteStatus(
    animationStatus: LamiAnimationStatus? = null,
    lamiStatus: LamiStatus? = null,
    uiState: UiState? = null,
    lamiState: LamiState? = null,
    isSpeaking: Boolean = false,
    lastError: String? = null,
    talkingTextLength: Int? = null,
    selectedModel: String? = null,
    retryCount: Int = 0,
    previousAnimationStatus: LamiAnimationStatus = animationStatus ?: LamiAnimationStatus.Idle,
): LamiSpriteStatus {
    val resolvedAnimationStatus = animationStatus
        ?: uiState?.let { nonNullUiState ->
            mapToAnimationLamiStatus(
                lamiState = lamiState,
                uiState = nonNullUiState,
                selectedModel = selectedModel,
                isTtsPlaying = isSpeaking,
                lastError = lastError,
                retryCount = retryCount,
                previousStatus = previousAnimationStatus,
                talkingTextLength = talkingTextLength,
            )
        }
        ?: lamiStatus?.toAnimationStatus(
            lastError = lastError,
            selectedModel = selectedModel,
            talkingTextLength = talkingTextLength,
        )

    val speakingFromLamiState = (lamiState as? LamiState.Speaking)?.let { speakingState ->
        when (bucket(speakingState.textLength)) {
            1 -> LamiSpriteStatus.TalkShort
            2 -> LamiSpriteStatus.TalkLong
            3 -> LamiSpriteStatus.TalkCalm
            else -> null
        }
    }
    if (speakingFromLamiState != null) {
        return speakingFromLamiState
    }

    if (resolvedAnimationStatus != null) {
        return resolvedAnimationStatus.toSpriteStatus()
    }

    val speakingBucket = when (lamiState) {
        is LamiState.Speaking -> bucket(lamiState.textLength)
        else -> talkingTextLength?.let { bucket(it) }
    }
    val speakingStatus = speakingBucket?.let { bucketValue ->
        when (bucketValue) {
            1 -> LamiSpriteStatus.TalkShort
            2 -> LamiSpriteStatus.TalkLong
            3 -> LamiSpriteStatus.TalkCalm
            else -> null
        }
    } ?: when {
        isSpeaking -> LamiSpriteStatus.TalkShort
        else -> null
    }
    if (speakingStatus != null) {
        return speakingStatus
    }

    when (uiState) {
        UiState.Loading -> return LamiSpriteStatus.Thinking
        is UiState.Thinking -> return LamiSpriteStatus.Thinking
        is UiState.Error -> return if (!lastError.isNullOrBlank()) {
            LamiSpriteStatus.ErrorHeavy
        } else {
            LamiSpriteStatus.ErrorLight
        }
        else -> Unit
    }

    when (lamiState) {
        is LamiState.Thinking -> return LamiSpriteStatus.Thinking
        is LamiState.Speaking -> return when (bucket(lamiState.textLength)) {
            1 -> LamiSpriteStatus.TalkShort
            2 -> LamiSpriteStatus.TalkLong
            3 -> LamiSpriteStatus.TalkCalm
            else -> LamiSpriteStatus.Idle
        }
        LamiState.Idle -> return LamiSpriteStatus.Idle
        else -> Unit
    }

    return when (lamiStatus) {
        LamiStatus.TALKING -> speakingStatus
            ?: when (speakingBucket) {
                1 -> LamiSpriteStatus.TalkShort
                2 -> LamiSpriteStatus.TalkLong
                3 -> LamiSpriteStatus.TalkCalm
                else -> LamiSpriteStatus.TalkLong
            }
        LamiStatus.CONNECTING,
        LamiStatus.THINKING,
        -> LamiSpriteStatus.Thinking
        LamiStatus.READY -> LamiSpriteStatus.Ready
        LamiStatus.DEGRADED -> LamiSpriteStatus.Idle
        LamiStatus.NO_MODELS, LamiStatus.ERROR -> LamiSpriteStatus.ErrorHeavy
        LamiStatus.OFFLINE -> if (lastError.isNullOrBlank()) {
            LamiSpriteStatus.OfflineLoop
        } else {
            LamiSpriteStatus.ErrorHeavy
        }
        null -> LamiSpriteStatus.Idle
    }
}

private fun LamiAnimationStatus.toSpriteStatus(): LamiSpriteStatus {
    return when (this) {
        LamiAnimationStatus.Idle -> LamiSpriteStatus.Idle
        LamiAnimationStatus.Thinking -> LamiSpriteStatus.Thinking
        LamiAnimationStatus.TalkShort -> LamiSpriteStatus.TalkShort
        LamiAnimationStatus.TalkLong -> LamiSpriteStatus.TalkLong
        LamiAnimationStatus.TalkCalm -> LamiSpriteStatus.TalkCalm
        LamiAnimationStatus.ErrorLight -> LamiSpriteStatus.ErrorLight
        LamiAnimationStatus.ErrorHeavy -> LamiSpriteStatus.ErrorHeavy
        LamiAnimationStatus.OfflineLoop -> LamiSpriteStatus.OfflineLoop
        LamiAnimationStatus.Ready -> LamiSpriteStatus.Ready
    }
}

private fun LamiStatus.toAnimationStatus(
    lastError: String? = null,
    selectedModel: String? = null,
    talkingTextLength: Int? = null,
): LamiAnimationStatus {
    val hasModels = !selectedModel.isNullOrBlank()
    return when (this) {
        LamiStatus.TALKING -> when (bucket(talkingTextLength ?: 0)) {
            1 -> LamiAnimationStatus.TalkShort
            3 -> LamiAnimationStatus.TalkCalm
            else -> LamiAnimationStatus.TalkLong
        }
        LamiStatus.CONNECTING,
        LamiStatus.THINKING,
        -> LamiAnimationStatus.Thinking
        LamiStatus.READY -> LamiAnimationStatus.Ready
        LamiStatus.DEGRADED -> LamiAnimationStatus.Thinking
        LamiStatus.NO_MODELS -> LamiAnimationStatus.OfflineLoop
        LamiStatus.OFFLINE -> LamiAnimationStatus.OfflineLoop
        LamiStatus.ERROR -> if (!lastError.isNullOrBlank()) {
            LamiAnimationStatus.ErrorHeavy
        } else if (!hasModels) {
            LamiAnimationStatus.OfflineLoop
        } else {
            LamiAnimationStatus.ErrorLight
        }
    }
}

private fun LamiAnimationStatus.isOfflineStatus(): Boolean {
    return this == LamiAnimationStatus.OfflineLoop
}
