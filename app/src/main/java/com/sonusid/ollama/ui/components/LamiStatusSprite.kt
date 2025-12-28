package com.sonusid.ollama.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonusid.ollama.UiState
import com.sonusid.ollama.viewmodels.LamiState
import com.sonusid.ollama.viewmodels.LamiStatus
import com.sonusid.ollama.viewmodels.LamiAnimationStatus
import com.sonusid.ollama.viewmodels.mapToAnimationLamiStatus
import com.sonusid.ollama.viewmodels.bucket
import kotlin.random.Random
import kotlinx.coroutines.delay

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

private val spriteFrameYOffsetPx: Map<Int, Int> = mapOf(
    0 to -2,
    1 to -2,
    2 to -1,
    3 to -1,
    4 to 0,
    5 to 0,
    6 to 1,
    7 to 1,
    8 to 1,
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
        frames = listOf(4, 1, 4, 2, 4, 7, 4, 3, 4),
        frameDuration = FrameDurationSpec(minMs = 260L, maxMs = 380L, jitterFraction = 0.15f),
        loop = true,
        insertions = listOf(
            InsertionSpec(
                frames = listOf(4, 1, 4, 5, 4),
                frequency = InsertionFrequency.ByLoops(3..6),
                exclusive = false,
            ),
        ),
    ),
    LamiSpriteStatus.TalkShort to AnimationSpec(
        frames = listOf(0, 6, 2, 0, 6, 3, 0),
        frameDuration = FrameDurationSpec(minMs = 110L, maxMs = 160L, jitterFraction = 0.1f),
        insertions = listOf(
            InsertionSpec(
                frames = listOf(0, 6, 1, 0),
                frequency = InsertionFrequency.ByProbability(0.2f),
                exclusive = false,
            ),
        ),
    ),
    LamiSpriteStatus.TalkLong to AnimationSpec(
        frames = listOf(0, 6, 2, 0, 6, 3, 0, 6, 4, 0),
        frameDuration = FrameDurationSpec(minMs = 130L, maxMs = 180L, jitterFraction = 0.1f),
        loop = true,
        insertions = listOf(
            InsertionSpec(
                frames = listOf(0, 6, 0),
                frequency = InsertionFrequency.ByLoops(1..2),
                exclusive = true,
            ),
            InsertionSpec(
                frames = listOf(0, 5, 0),
                frequency = InsertionFrequency.ByLoops(2..4),
                exclusive = true,
            ),
        ),
    ),
    LamiSpriteStatus.TalkCalm to AnimationSpec(
        frames = listOf(0, 6, 2, 0, 6, 1, 0, 6, 0),
        frameDuration = FrameDurationSpec(minMs = 150L, maxMs = 210L, jitterFraction = 0.1f),
        insertions = listOf(
            InsertionSpec(
                frames = listOf(0, 6, 8, 6, 0),
                frequency = InsertionFrequency.ByTime(3_000L..6_000L),
                exclusive = false,
            ),
            InsertionSpec(
                frames = listOf(0, 6, 0),
                frequency = InsertionFrequency.ByLoops(1..2),
                exclusive = true,
            ),
        ),
    ),
    LamiSpriteStatus.ErrorLight to AnimationSpec(
        frames = listOf(5, 4, 5, 4, 5),
        frameDuration = FrameDurationSpec(minMs = 150L, maxMs = 220L, jitterFraction = 0.1f),
        insertions = listOf(
            InsertionSpec(
                frames = listOf(5, 7, 5),
                frequency = InsertionFrequency.ByTime(3_000L..6_000L),
                exclusive = true,
            ),
        ),
    ),
    LamiSpriteStatus.ErrorHeavy to AnimationSpec(
        frames = listOf(5, 8, 5, 4, 5, 8, 5),
        frameDuration = FrameDurationSpec(minMs = 140L, maxMs = 200L, jitterFraction = 0.1f),
        insertions = listOf(
            InsertionSpec(
                frames = listOf(8, 8, 5, 8),
                frequency = InsertionFrequency.ByLoops(2..3),
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
        frames = listOf(0, 7, 8, 7, 0),
        frameDuration = FrameDurationSpec(minMs = 150L, maxMs = 190L),
    ),
)

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
    frameYOffsetPxMap: Map<Int, Int> = spriteFrameYOffsetPx,
) {
    val constrainedSize = remember(sizeDp) { sizeDp.coerceIn(32.dp, 100.dp) }
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

    var currentFrameIndex by remember(resolvedStatus) {
        mutableStateOf(animSpec.frames.firstOrNull()?.coerceIn(0, 8) ?: 0)
    }
    val targetFrameYOffsetPx = frameYOffsetPxMap[currentFrameIndex] ?: 0
    val animatedFrameYOffsetPx by animateIntAsState(
        targetValue = targetFrameYOffsetPx,
        label = "frameYOffsetPx",
    )
    val animatedFrameYOffsetPxMap = remember(
        frameYOffsetPxMap,
        currentFrameIndex,
        animatedFrameYOffsetPx,
    ) {
        frameYOffsetPxMap.toMutableMap().apply {
            put(currentFrameIndex, animatedFrameYOffsetPx)
        }
    }

    LaunchedEffect(resolvedStatus, animationsEnabled, animSpec) {
        currentFrameIndex = animSpec.frames.firstOrNull()?.coerceIn(0, 8) ?: 0
        if (!animationsEnabled || animSpec.frames.isEmpty()) {
            return@LaunchedEffect
        }

        val random = Random(System.currentTimeMillis())

        data class InsertionState(
            val spec: InsertionSpec,
            var loopsUntilInsertion: Int? = null,
            var nextInsertionAtMs: Long? = null,
        )

        fun InsertionSpec.toState(now: Long): InsertionState {
            val initialLoops = (frequency as? InsertionFrequency.ByLoops)?.loopRange?.let {
                random.nextInt(it.first, it.last + 1)
            }
            val initialNextInsertion = (frequency as? InsertionFrequency.ByTime)?.msRange?.let {
                now + random.nextLong(it.first, it.last + 1)
            }
            return InsertionState(
                spec = this,
                loopsUntilInsertion = initialLoops,
                nextInsertionAtMs = initialNextInsertion,
            )
        }

        fun resetInsertionState(state: InsertionState) {
            when (val frequency = state.spec.frequency) {
                is InsertionFrequency.ByLoops -> state.loopsUntilInsertion =
                    random.nextInt(frequency.loopRange.first, frequency.loopRange.last + 1)
                is InsertionFrequency.ByTime -> state.nextInsertionAtMs =
                    System.currentTimeMillis() +
                        random.nextLong(frequency.msRange.first, frequency.msRange.last + 1)
                is InsertionFrequency.ByProbability -> Unit
            }
        }

        val insertionStates = animSpec.insertions.map { insertionSpec ->
            insertionSpec.toState(System.currentTimeMillis())
        }

        suspend fun playFrames(frames: List<Int>) {
            for (frame in frames) {
                currentFrameIndex = frame.coerceIn(0, 8)
                delay(animSpec.frameDuration.draw(random))
            }
        }

        while (true) {
            for (frame in animSpec.frames) {
                val triggeredInsertion = insertionStates.firstOrNull { state ->
                    when (val frequency = state.spec.frequency) {
                        is InsertionFrequency.ByProbability ->
                            random.nextFloat() < frequency.probability
                        is InsertionFrequency.ByLoops ->
                            (state.loopsUntilInsertion ?: Int.MAX_VALUE) <= 0
                        is InsertionFrequency.ByTime -> {
                            val scheduledAt = state.nextInsertionAtMs
                            scheduledAt != null && System.currentTimeMillis() >= scheduledAt
                        }
                    }
                }

                if (triggeredInsertion != null) {
                    playFrames(triggeredInsertion.spec.frames)
                    resetInsertionState(triggeredInsertion)
                    if (triggeredInsertion.spec.exclusive) {
                        continue
                    }
                }

                currentFrameIndex = frame.coerceIn(0, 8)
                delay(animSpec.frameDuration.draw(random))
            }

            if (!animSpec.loop) {
                break
            }

            insertionStates.forEach { state ->
                if (state.spec.frequency is InsertionFrequency.ByLoops) {
                    state.loopsUntilInsertion = (state.loopsUntilInsertion ?: Int.MAX_VALUE) - 1
                }
            }
        }
    }

    LamiSprite3x3(
        frameIndex = currentFrameIndex,
        sizeDp = constrainedSize,
        modifier = modifier,
        contentOffsetDp = contentOffsetDp,
        contentOffsetYDp = contentOffsetYDp,
        frameYOffsetPxMap = animatedFrameYOffsetPxMap,
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
    frameYOffsetPxMap: Map<Int, Int> = spriteFrameYOffsetPx,
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
        frameYOffsetPxMap = frameYOffsetPxMap,
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
    frameYOffsetPxMap: Map<Int, Int> = spriteFrameYOffsetPx,
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
        frameYOffsetPxMap = frameYOffsetPxMap,
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
