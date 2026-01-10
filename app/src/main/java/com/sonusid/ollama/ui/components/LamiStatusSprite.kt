package com.sonusid.ollama.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sonusid.ollama.UiState
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.ui.screens.settings.InsertionAnimationSettings
import com.sonusid.ollama.ui.screens.settings.SettingsPreferences
import com.sonusid.ollama.ui.screens.settings.shouldAttemptInsertion
import com.sonusid.ollama.viewmodels.LamiAnimationStatus
import com.sonusid.ollama.viewmodels.LamiState
import com.sonusid.ollama.viewmodels.LamiStatus
import com.sonusid.ollama.viewmodels.bucket
import com.sonusid.ollama.viewmodels.mapToAnimationLamiStatus
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class LamiSpriteStatus {
    Idle,
    Thinking,
    TalkShort,
    TalkLong,
    TalkCalm,
    ErrorLight,
    ErrorHeavy,
    OfflineEnter,
    OfflineLoop,
    OfflineExit,
    ReadyBlink,
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

private val statusAnimationMap: Map<LamiSpriteStatus, AnimationSpec> = mapOf(
    LamiSpriteStatus.Idle to AnimationSpec(
        frames = listOf(0, 8, 0, 5, 0),
        frameDuration = FrameDurationSpec(minMs = 420L, maxMs = 560L, jitterFraction = 0.2f),
        loop = true,
        insertions = listOf(
            InsertionSpec(
                frames = listOf(0, 0, 8, 0),
                frequency = InsertionFrequency.ByTime(8_000L..15_000L),
                exclusive = false,
            ),
        ),
    ),
    LamiSpriteStatus.Thinking to AnimationSpec(
        frames = listOf(4, 4, 4, 7, 4, 4, 4),
        frameDuration = FrameDurationSpec(minMs = 220L, maxMs = 280L, jitterFraction = 0.1f),
        loop = true,
        insertions = listOf(
            InsertionSpec(
                frames = listOf(4, 4, 5, 4),
                frequency = InsertionFrequency.ByLoops(4..8),
                exclusive = false,
            ),
        ),
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
        insertions = listOf(
            InsertionSpec(
                frames = listOf(1),
                frequency = InsertionFrequency.ByLoops(2..4),
                exclusive = true,
            ),
            InsertionSpec(
                frames = listOf(8),
                frequency = InsertionFrequency.ByLoops(3..6),
                exclusive = true,
            ),
        ),
    ),
    LamiSpriteStatus.TalkCalm to AnimationSpec(
        frames = listOf(7, 4, 7, 8, 7),
        frameDuration = FrameDurationSpec(minMs = 240L, maxMs = 320L, jitterFraction = 0.1f),
        insertions = emptyList(),
    ),
    LamiSpriteStatus.ErrorLight to AnimationSpec(
        frames = listOf(5, 7, 5),
        frameDuration = FrameDurationSpec(minMs = 360L, maxMs = 420L, jitterFraction = 0f),
        insertions = emptyList(),
    ),
    LamiSpriteStatus.ErrorHeavy to AnimationSpec(
        frames = listOf(5, 5, 5, 7, 5),
        frameDuration = FrameDurationSpec(minMs = 340L, maxMs = 460L, jitterFraction = 0.1f),
        insertions = listOf(
            InsertionSpec(
                frames = listOf(2),
                frequency = InsertionFrequency.ByLoops(6..12),
                exclusive = true,
            ),
        ),
    ),
    LamiSpriteStatus.OfflineEnter to AnimationSpec(
        frames = listOf(0, 8, 8),
        frameDuration = FrameDurationSpec(minMs = 1_000L, maxMs = 1_500L),
        loop = false,
    ),
    LamiSpriteStatus.OfflineLoop to AnimationSpec(
        frames = listOf(8, 8),
        frameDuration = FrameDurationSpec(minMs = 1_000L, maxMs = 1_500L),
        loop = true,
    ),
    LamiSpriteStatus.OfflineExit to AnimationSpec(
        frames = listOf(8, 0),
        frameDuration = FrameDurationSpec(minMs = 1_000L, maxMs = 1_500L),
        loop = false,
    ),
    LamiSpriteStatus.ReadyBlink to AnimationSpec(
        frames = listOf(0),
        frameDuration = FrameDurationSpec(minMs = 700L, maxMs = 1_200L, jitterFraction = 0.15f),
        insertions = emptyList(),
    ),
)

private fun selectInsertionSettingsForStatus(
    status: LamiSpriteStatus,
    readySettings: InsertionAnimationSettings,
    talkingSettings: InsertionAnimationSettings,
): InsertionAnimationSettings? =
    when (status) {
        LamiSpriteStatus.ReadyBlink,
        LamiSpriteStatus.Idle,
        LamiSpriteStatus.Thinking,
        LamiSpriteStatus.ErrorLight,
        LamiSpriteStatus.ErrorHeavy,
        -> readySettings
        LamiSpriteStatus.TalkShort,
        LamiSpriteStatus.TalkLong,
        LamiSpriteStatus.TalkCalm,
        -> talkingSettings
        LamiSpriteStatus.OfflineEnter,
        LamiSpriteStatus.OfflineLoop,
        LamiSpriteStatus.OfflineExit,
        -> null
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
    val resolvedStatus = remember(status, replacementEnabled, blinkEffectEnabled) {
        when {
            !replacementEnabled -> LamiSpriteStatus.Idle
            !blinkEffectEnabled && status == LamiSpriteStatus.ReadyBlink -> LamiSpriteStatus.Idle
            else -> status
        }
    }

    val animSpec = remember(resolvedStatus) {
        statusAnimationMap[resolvedStatus] ?: statusAnimationMap.getValue(LamiSpriteStatus.Idle)
    }
    val context = LocalContext.current
    val settingsPreferences = remember(context) {
        SettingsPreferences(context.applicationContext)
    }
    val readyInsertionSettings by settingsPreferences.readyInsertionAnimationSettings.collectAsState(
        initial = InsertionAnimationSettings.DEFAULT,
    )
    val talkingInsertionSettings by settingsPreferences.talkingInsertionAnimationSettings.collectAsState(
        initial = InsertionAnimationSettings.DEFAULT,
    )
    val insertionSettings = remember(resolvedStatus, readyInsertionSettings, talkingInsertionSettings) {
        selectInsertionSettingsForStatus(
            status = resolvedStatus,
            readySettings = readyInsertionSettings,
            talkingSettings = talkingInsertionSettings,
        )
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

    LaunchedEffect(resolvedStatus, animationsEnabled, animSpec, insertionSettings) {
        currentFrameIndex = animSpec.frames.firstOrNull()?.coerceIn(0, maxFrameIndex) ?: 0
        if (!animationsEnabled || animSpec.frames.isEmpty()) {
            return@LaunchedEffect
        }

        val random = Random(System.currentTimeMillis())

        suspend fun playInsertionFrames(settings: InsertionAnimationSettings) {
            if (settings.frameSequence.isEmpty()) {
                return
            }
            // 設定の intervalMs を固定間隔として使用する
            val intervalMs = settings.intervalMs.coerceAtLeast(0).toLong()
            for (frame in settings.frameSequence) {
                currentFrameIndex = frame.coerceIn(0, maxFrameIndex)
                delay(intervalMs)
            }
        }

        var loopCount = 0
        var lastInsertionLoop: Int? = null
        while (true) {
            loopCount += 1
            // 設定に基づく挿入判定はループ単位で行う（挿入の可否は shouldAttemptInsertion のみで決定）
            // exclusive の意味その1：READY再生中は shouldAttemptInsertion 側で抑止される
            val shouldInsert = insertionSettings?.shouldAttemptInsertion(
                loopCount = loopCount,
                lastInsertionLoop = lastInsertionLoop,
                isReadyPlaying = resolvedStatus == LamiSpriteStatus.ReadyBlink,
                random = random,
            ) == true
            if (shouldInsert && insertionSettings != null) {
                playInsertionFrames(settings = insertionSettings)
                lastInsertionLoop = loopCount
                if (insertionSettings.exclusive) {
                    // exclusive の意味その2：挿入が発生したループでは通常フレームを描画せず次へ進む
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
) {
    var previousAnimationStatus by remember {
        mutableStateOf(status.value)
    }
    val animationStatus = remember(
        status.value,
        selectedModel,
        lastError,
        retryCount,
        talkingTextLength,
        previousAnimationStatus,
    ) {
        val derived = status.value
        if (derived.isOfflineStatus() && previousAnimationStatus == LamiAnimationStatus.OfflineEnter) {
            LamiAnimationStatus.OfflineLoop
        } else if (derived == LamiAnimationStatus.ReadyBlink && previousAnimationStatus.isOfflineStatus()) {
            LamiAnimationStatus.OfflineExit
        } else {
            derived
        }
    }
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
            previousStatus = previousAnimationStatus,
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
        LamiStatus.READY -> LamiSpriteStatus.ReadyBlink
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
        LamiAnimationStatus.OfflineEnter -> LamiSpriteStatus.OfflineEnter
        LamiAnimationStatus.OfflineLoop -> LamiSpriteStatus.OfflineLoop
        LamiAnimationStatus.OfflineExit -> LamiSpriteStatus.OfflineExit
        LamiAnimationStatus.ReadyBlink -> LamiSpriteStatus.ReadyBlink
    }
}

private fun LamiStatus.toAnimationStatus(
    lastError: String? = null,
    selectedModel: String? = null,
    previousStatus: LamiAnimationStatus = LamiAnimationStatus.Idle,
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
        LamiStatus.READY -> if (previousStatus.isOfflineStatus()) {
            LamiAnimationStatus.OfflineExit
        } else {
            LamiAnimationStatus.ReadyBlink
        }
        LamiStatus.DEGRADED -> LamiAnimationStatus.Thinking
        LamiStatus.NO_MODELS -> if (previousStatus.isOfflineStatus()) {
            LamiAnimationStatus.OfflineLoop
        } else {
            LamiAnimationStatus.OfflineEnter
        }
        LamiStatus.OFFLINE -> if (previousStatus.isOfflineStatus()) {
            LamiAnimationStatus.OfflineLoop
        } else {
            LamiAnimationStatus.OfflineEnter
        }
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
    return this == LamiAnimationStatus.OfflineEnter ||
        this == LamiAnimationStatus.OfflineLoop ||
        this == LamiAnimationStatus.OfflineExit
}
