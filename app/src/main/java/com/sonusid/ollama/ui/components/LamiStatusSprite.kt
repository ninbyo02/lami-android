package com.sonusid.ollama.ui.components

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
    val insertion: InsertionSpec? = null,
)

private val statusAnimationMap: Map<LamiSpriteStatus, AnimationSpec> = mapOf(
    LamiSpriteStatus.Idle to AnimationSpec(
        frames = listOf(0, 1, 0, 2),
        frameDuration = FrameDurationSpec(minMs = 210L, maxMs = 260L),
        loop = true,
        insertion = InsertionSpec(
            frames = listOf(0, 7, 8, 7, 0),
            frequency = InsertionFrequency.ByTime(8_000L..15_000L),
        ),
    ),
    LamiSpriteStatus.Thinking to AnimationSpec(
        frames = listOf(4, 2, 4, 1, 4),
        frameDuration = FrameDurationSpec(minMs = 150L, maxMs = 190L),
        loop = true,
        insertion = InsertionSpec(
            frames = listOf(4, 5, 4),
            frequency = InsertionFrequency.ByLoops(4..8),
        ),
    ),
    LamiSpriteStatus.TalkShort to AnimationSpec(
        frames = listOf(6, 1, 6, 2),
        frameDuration = FrameDurationSpec(minMs = 100L, maxMs = 150L),
    ),
    LamiSpriteStatus.TalkLong to AnimationSpec(
        frames = listOf(6, 1, 4, 3, 6, 1, 4, 3),
        frameDuration = FrameDurationSpec(minMs = 120L, maxMs = 170L),
        loop = true,
        insertion = InsertionSpec(
            frames = listOf(6, 7, 8, 7),
            frequency = InsertionFrequency.ByProbability(0.125f),
            exclusive = true,
        ),
    ),
    LamiSpriteStatus.TalkCalm to AnimationSpec(
        frames = listOf(6, 7, 8, 7, 6),
        frameDuration = FrameDurationSpec(minMs = 180L, maxMs = 220L, jitterFraction = 0.1f),
    ),
    LamiSpriteStatus.ErrorLight to AnimationSpec(
        frames = listOf(5, 4, 5, 4),
        frameDuration = FrameDurationSpec(minMs = 180L, maxMs = 230L),
    ),
    LamiSpriteStatus.ErrorHeavy to AnimationSpec(
        frames = listOf(5, 8, 5, 4, 5, 8),
        frameDuration = FrameDurationSpec(minMs = 160L, maxMs = 200L),
        insertion = InsertionSpec(
            frames = listOf(8, 5),
            frequency = InsertionFrequency.ByLoops(2..3),
            exclusive = true,
        ),
    ),
    LamiSpriteStatus.OfflineEnter to AnimationSpec(
        frames = listOf(2, 5, 8),
        frameDuration = FrameDurationSpec(minMs = 240L, maxMs = 300L),
        loop = false,
    ),
    LamiSpriteStatus.OfflineLoop to AnimationSpec(
        frames = listOf(8, 5, 2, 5),
        frameDuration = FrameDurationSpec(minMs = 260L, maxMs = 320L),
        loop = true,
    ),
    LamiSpriteStatus.OfflineExit to AnimationSpec(
        frames = listOf(5, 2, 0),
        frameDuration = FrameDurationSpec(minMs = 240L, maxMs = 300L),
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
    animationsEnabled: Boolean = true,
    replacementEnabled: Boolean = true,
    blinkEffectEnabled: Boolean = true,
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

    LaunchedEffect(resolvedStatus, animationsEnabled, animSpec) {
        currentFrameIndex = animSpec.frames.firstOrNull()?.coerceIn(0, 8) ?: 0
        if (!animationsEnabled || animSpec.frames.isEmpty()) {
            return@LaunchedEffect
        }

        val random = Random(System.currentTimeMillis())
        var loopsUntilInsertion = (animSpec.insertion?.frequency as? InsertionFrequency.ByLoops)?.loopRange?.let {
            random.nextInt(it.first, it.last + 1)
        }
        var nextInsertionAtMs = (animSpec.insertion?.frequency as? InsertionFrequency.ByTime)?.msRange?.let {
            val now = System.currentTimeMillis()
            now + random.nextLong(it.first, it.last + 1)
        }

        suspend fun playFrames(frames: List<Int>) {
            for (frame in frames) {
                currentFrameIndex = frame.coerceIn(0, 8)
                delay(animSpec.frameDuration.draw(random))
            }
        }

        while (true) {
            for (frame in animSpec.frames) {
                val insertionSpec = animSpec.insertion
                val shouldInsert = insertionSpec?.let { spec ->
                    when (val frequency = spec.frequency) {
                        is InsertionFrequency.ByProbability -> random.nextFloat() < frequency.probability
                        is InsertionFrequency.ByLoops -> (loopsUntilInsertion ?: Int.MAX_VALUE) <= 0
                        is InsertionFrequency.ByTime -> {
                            val now = System.currentTimeMillis()
                            nextInsertionAtMs?.let { now >= it } == true
                        }
                    }
                } ?: false

                if (shouldInsert && insertionSpec != null) {
                    playFrames(insertionSpec.frames)
                    if (insertionSpec.exclusive) {
                        when (val frequency = insertionSpec.frequency) {
                            is InsertionFrequency.ByLoops -> loopsUntilInsertion =
                                random.nextInt(frequency.loopRange.first, frequency.loopRange.last + 1)
                            is InsertionFrequency.ByTime -> nextInsertionAtMs =
                                System.currentTimeMillis() + random.nextLong(
                                    frequency.msRange.first,
                                    frequency.msRange.last + 1
                                )
                            is InsertionFrequency.ByProbability -> Unit
                        }
                        continue
                    }
                    when (val frequency = insertionSpec.frequency) {
                        is InsertionFrequency.ByLoops -> loopsUntilInsertion =
                            random.nextInt(frequency.loopRange.first, frequency.loopRange.last + 1)
                        is InsertionFrequency.ByTime -> nextInsertionAtMs = System.currentTimeMillis() +
                            random.nextLong(frequency.msRange.first, frequency.msRange.last + 1)
                        is InsertionFrequency.ByProbability -> Unit
                    }
                }

                currentFrameIndex = frame.coerceIn(0, 8)
                delay(animSpec.frameDuration.draw(random))
            }

            if (!animSpec.loop) {
                break
            }

            if (loopsUntilInsertion != null) {
                loopsUntilInsertion = loopsUntilInsertion!! - 1
            }
        }
    }

    LamiSprite3x3(
        frameIndex = currentFrameIndex,
        sizeDp = constrainedSize,
        modifier = modifier,
        contentOffsetDp = contentOffsetDp
    )
}

@Composable
fun LamiStatusSprite(
    status: State<LamiStatus>,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 48.dp,
    contentOffsetDp: Dp = 2.dp,
    animationsEnabled: Boolean = true,
    replacementEnabled: Boolean = true,
    blinkEffectEnabled: Boolean = true,
) {
    val spriteStatus = remember(status.value) {
        mapToLamiSpriteStatus(lamiStatus = status.value)
    }
    LamiStatusSprite(
        status = spriteStatus,
        modifier = modifier,
        sizeDp = sizeDp,
        contentOffsetDp = contentOffsetDp,
        animationsEnabled = animationsEnabled,
        replacementEnabled = replacementEnabled,
        blinkEffectEnabled = blinkEffectEnabled,
    )
}

fun mapToLamiSpriteStatus(
    lamiStatus: LamiStatus? = null,
    uiState: UiState? = null,
    lamiState: LamiState? = null,
    isSpeaking: Boolean = false,
    lastError: String? = null,
    talkingTextLength: Int? = null,
): LamiSpriteStatus {
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
