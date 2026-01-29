package com.sonusid.ollama.ui.components

import android.util.Log
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sonusid.ollama.BuildConfig
import com.sonusid.ollama.UiState
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.ui.animation.SpriteAnimationDefaults
import com.sonusid.ollama.ui.screens.settings.ErrorCause
import com.sonusid.ollama.ui.screens.settings.InsertionAnimationSettings
import com.sonusid.ollama.ui.screens.settings.InsertionPattern
import com.sonusid.ollama.ui.screens.settings.SettingsPreferences
import com.sonusid.ollama.ui.screens.settings.effectiveInsertionIntervalMs
import com.sonusid.ollama.ui.screens.settings.SpriteState
import com.sonusid.ollama.ui.screens.settings.shouldAttemptInsertion
import com.sonusid.ollama.viewmodels.LamiAnimationStatus
import com.sonusid.ollama.viewmodels.LamiState
import com.sonusid.ollama.viewmodels.LamiStatus
import com.sonusid.ollama.viewmodels.bucket
import com.sonusid.ollama.viewmodels.mapToAnimationLamiStatus
import com.sonusid.ollama.viewmodels.resolveErrorKey
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

private fun selectWeightedInsertionPattern(
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

@Composable
fun LamiStatusSprite(
    status: LamiSpriteStatus,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 48.dp,
    contentOffsetDp: Dp = 2.dp,
    contentOffsetYDp: Dp = 0.dp,
    animationsEnabled: Boolean = true,
    replacementEnabled: Boolean = true,
    blinkEffectEnabled: Boolean = true,
    frameXOffsetPxMap: Map<Int, Int> = emptyMap(),
    frameYOffsetPxMap: Map<Int, Int> = emptyMap(),
    frameSrcOffsetMap: Map<Int, IntOffset> = emptyMap(),
    frameSrcSizeMap: Map<Int, IntSize> = emptyMap(),
    autoCropTransparentArea: Boolean = false,
    resolvedErrorKey: String? = null,
) {
    val constrainedSize = remember(sizeDp) { sizeDp.coerceIn(32.dp, 100.dp) }
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
    val storedErrorSelectedKey by settingsPreferences
        .selectedKeyFlow(SpriteState.ERROR)
        .collectAsState(initial = null)
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
    // Effect を再起動せずに最新設定を即時反映するため rememberUpdatedState を使う
    val insertionSettingsLatest by rememberUpdatedState(insertionSettings)

    LaunchedEffect(resolvedStatus, perStateAnimJson, animSpec) {
        if (BuildConfig.DEBUG) {
            Log.d(
                "LamiStatusSprite",
                "resolvedStatus=$resolvedStatus spriteState=$spriteStateForAnim " +
                    "baseMs=${animSpec.frameDuration.minMs} frames=${animSpec.frames} " +
                    "json=${perStateAnimJson?.take(80)}",
            )
        }
    }

    var currentFrameIndex by remember(resolvedStatus, maxFrameIndex) {
        mutableStateOf(animSpec.frames.firstOrNull()?.coerceIn(0, maxFrameIndex) ?: 0)
    }
    val defaultFrameXOffsetPxMap = remember(frameMaps) {
        frameMaps.toFrameXOffsetPxMap()
    }
    val resolvedFrameXOffsetPxMap = remember(frameXOffsetPxMap, defaultFrameXOffsetPxMap) {
        defaultFrameXOffsetPxMap.toMutableMap().apply {
            putAll(frameXOffsetPxMap)
        }
    }
    val defaultFrameYOffsetPxMap = remember(frameMaps) {
        frameMaps.toFrameYOffsetPxMap()
    }
    val resolvedFrameYOffsetPxMap = remember(frameYOffsetPxMap, defaultFrameYOffsetPxMap) {
        defaultFrameYOffsetPxMap.toMutableMap().apply {
            putAll(frameYOffsetPxMap)
        }
    }
    val targetFrameYOffsetPx = resolvedFrameYOffsetPxMap[currentFrameIndex] ?: 0
    // スナップで即時反映し、フレーム切替時の縦揺れ（バネ挙動のオーバーシュート）を防ぐ
    val animatedFrameYOffsetPx by animateIntAsState(
        targetValue = targetFrameYOffsetPx,
        animationSpec = snap(),
        label = "frameYOffsetPx",
    )
    val animatedFrameYOffsetPxMap = remember(
        resolvedFrameYOffsetPxMap,
        currentFrameIndex,
        animatedFrameYOffsetPx,
    ) {
        resolvedFrameYOffsetPxMap.toMutableMap().apply {
            put(currentFrameIndex, animatedFrameYOffsetPx)
        }
    }

    LaunchedEffect(resolvedStatus, animationsEnabled, animSpec, insertionKey) {
        loopCountState.value = 0
        lastInsertionLoopState.value = null
        lastInsertionPatternIndex = null
        lastInsertionResolvedIntervalMs = null
        lastInsertionFrames = null
        currentFrameIndex = animSpec.frames.firstOrNull()?.coerceIn(0, maxFrameIndex) ?: 0
        if (!animationsEnabled || animSpec.frames.isEmpty()) {
            return@LaunchedEffect
        }

        val random = Random(System.currentTimeMillis())

        suspend fun playInsertionFrames(frameSequence: List<Int>, intervalMs: Int) {
            if (frameSequence.isEmpty()) return
            // 設定の intervalMs を固定間隔として使用する
            val resolvedIntervalMs = intervalMs.coerceAtLeast(0).toLong()
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
            if (shouldInsert && settings?.patterns?.isNotEmpty() == true) {
                val activeSettings = requireNotNull(settings)
                val defaultIntervalMs = effectiveInsertionIntervalMs(
                    activeSettings,
                    activeSettings.intervalMs ?: InsertionAnimationSettings.DEFAULT.intervalMs ?: 0,
                )
                // 挿入イベント内で重み付き抽選を行う（weight/frames が有効なもののみ）
                val (patternIndex, pattern) = selectWeightedInsertionPattern(activeSettings.patterns, random)
                    ?: continue
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
                delay(animSpec.frameDuration.draw(random))
            }

            if (!animSpec.loop) {
                break
            }
        }
    }

    LamiSprite3x3(
        frameIndex = currentFrameIndex,
        sizeDp = constrainedSize,
        modifier = modifier,
        contentOffsetDp = contentOffsetDp,
        contentOffsetYDp = contentOffsetYDp,
        frameXOffsetPxMap = resolvedFrameXOffsetPxMap,
        frameYOffsetPxMap = animatedFrameYOffsetPxMap,
        frameSrcOffsetMap = resolvedFrameSrcOffsetMap,
        frameSrcSizeMap = resolvedFrameSrcSizeMap,
        autoCropTransparentArea = autoCropTransparentArea,
        frameSizePx = frameMaps.frameSize,
        frameMaps = frameMaps,
        spriteSheetConfig = spriteSheetConfig,
    )
}

@Composable
fun LamiStatusSprite(
    status: State<LamiStatus>,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 48.dp,
    contentOffsetDp: Dp = 2.dp,
    contentOffsetYDp: Dp = 0.dp,
    animationsEnabled: Boolean = true,
    replacementEnabled: Boolean = true,
    blinkEffectEnabled: Boolean = true,
    frameXOffsetPxMap: Map<Int, Int> = emptyMap(),
    frameYOffsetPxMap: Map<Int, Int> = emptyMap(),
    frameSrcOffsetMap: Map<Int, IntOffset> = emptyMap(),
    frameSrcSizeMap: Map<Int, IntSize> = emptyMap(),
    autoCropTransparentArea: Boolean = false,
    resolvedErrorKey: String? = null,
) {
    val spriteStatus = remember(status.value) {
        mapToLamiSpriteStatus(lamiStatus = status.value)
    }
    LamiStatusSprite(
        status = spriteStatus,
        modifier = modifier,
        sizeDp = sizeDp,
        contentOffsetDp = contentOffsetDp,
        contentOffsetYDp = contentOffsetYDp,
        animationsEnabled = animationsEnabled,
        replacementEnabled = replacementEnabled,
        blinkEffectEnabled = blinkEffectEnabled,
        frameXOffsetPxMap = frameXOffsetPxMap,
        frameYOffsetPxMap = frameYOffsetPxMap,
        frameSrcOffsetMap = frameSrcOffsetMap,
        frameSrcSizeMap = frameSrcSizeMap,
        autoCropTransparentArea = autoCropTransparentArea,
        resolvedErrorKey = resolvedErrorKey,
    )
}

@Composable
fun LamiStatusSprite(
    status: State<LamiAnimationStatus>,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 48.dp,
    contentOffsetDp: Dp = 2.dp,
    contentOffsetYDp: Dp = 0.dp,
    animationsEnabled: Boolean = true,
    replacementEnabled: Boolean = true,
    blinkEffectEnabled: Boolean = true,
    selectedModel: String? = null,
    lastError: String? = null,
    retryCount: Int = 0,
    talkingTextLength: Int? = null,
    frameXOffsetPxMap: Map<Int, Int> = emptyMap(),
    frameYOffsetPxMap: Map<Int, Int> = emptyMap(),
    frameSrcOffsetMap: Map<Int, IntOffset> = emptyMap(),
    frameSrcSizeMap: Map<Int, IntSize> = emptyMap(),
    autoCropTransparentArea: Boolean = false,
    resolvedErrorKey: String? = null,
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
        sizeDp = sizeDp,
        contentOffsetDp = contentOffsetDp,
        contentOffsetYDp = contentOffsetYDp,
        animationsEnabled = animationsEnabled,
        replacementEnabled = replacementEnabled,
        blinkEffectEnabled = blinkEffectEnabled,
        frameXOffsetPxMap = frameXOffsetPxMap,
        frameYOffsetPxMap = frameYOffsetPxMap,
        frameSrcOffsetMap = frameSrcOffsetMap,
        frameSrcSizeMap = frameSrcSizeMap,
        autoCropTransparentArea = autoCropTransparentArea,
        resolvedErrorKey = resolvedErrorKey,
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
        LamiStatus.CONNECTING -> LamiSpriteStatus.Thinking
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
        LamiStatus.CONNECTING -> LamiAnimationStatus.Thinking
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
