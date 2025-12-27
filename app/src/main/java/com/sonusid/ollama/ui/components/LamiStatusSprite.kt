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
import kotlinx.coroutines.delay

enum class LamiSpriteStatus {
    Idle,
    Thinking,
    TalkShort,
    TalkLong,
    TalkCalm,
    Error,
}

data class AnimSpec(
    val frames: List<Int>,
    val frameMs: Long,
)

private val statusAnimationMap: Map<LamiSpriteStatus, AnimSpec> = mapOf(
    LamiSpriteStatus.Idle to AnimSpec(
        frames = listOf(0, 1, 2, 1),
        frameMs = 220L
    ),
    LamiSpriteStatus.Thinking to AnimSpec(
        frames = listOf(3, 4, 5, 4),
        frameMs = 160L
    ),
    LamiSpriteStatus.TalkShort to AnimSpec(
        frames = listOf(6, 7, 8, 7),
        frameMs = 120L
    ),
    LamiSpriteStatus.TalkLong to AnimSpec(
        frames = listOf(6, 7, 8, 7, 6),
        frameMs = 160L
    ),
    LamiSpriteStatus.TalkCalm to AnimSpec(
        frames = listOf(0, 1, 0, 2),
        frameMs = 360L
    ),
    LamiSpriteStatus.Error to AnimSpec(
        frames = listOf(8, 7, 8, 7),
        frameMs = 220L
    ),
)

@Composable
fun LamiStatusSprite(
    status: LamiSpriteStatus,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 48.dp,
    contentOffsetDp: Dp = 2.dp,
) {
    val constrainedSize = remember(sizeDp) { sizeDp.coerceIn(32.dp, 100.dp) }
    val animSpec = statusAnimationMap[status] ?: statusAnimationMap.getValue(LamiSpriteStatus.Idle)

    var currentFrameIndex by remember(status) {
        mutableStateOf(animSpec.frames.firstOrNull() ?: 0)
    }

    LaunchedEffect(status) {
        var index = 0
        while (true) {
            currentFrameIndex = animSpec.frames.getOrNull(index % animSpec.frames.size) ?: 0
            index++
            delay(animSpec.frameMs)
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
) {
    val spriteStatus = remember(status.value) {
        mapToLamiSpriteStatus(lamiStatus = status.value)
    }
    LamiStatusSprite(
        status = spriteStatus,
        modifier = modifier,
        sizeDp = sizeDp,
        contentOffsetDp = contentOffsetDp,
    )
}

fun mapToLamiSpriteStatus(
    lamiStatus: LamiStatus? = null,
    uiState: UiState? = null,
    lamiState: LamiState? = null,
    isSpeaking: Boolean = false,
    lastError: String? = null,
): LamiSpriteStatus {
    val speakingStatus = when {
        lamiState is LamiState.Speaking -> when (bucket(lamiState.textLength)) {
            1 -> LamiSpriteStatus.TalkShort
            2 -> LamiSpriteStatus.TalkLong
            3 -> LamiSpriteStatus.TalkCalm
            else -> LamiSpriteStatus.Idle
        }
        isSpeaking -> LamiSpriteStatus.TalkShort
        else -> null
    }
    if (speakingStatus != null) {
        return speakingStatus
    }

    when (uiState) {
        UiState.Loading -> return LamiSpriteStatus.Thinking
        is UiState.Error -> return if (!lastError.isNullOrBlank()) {
            LamiSpriteStatus.Error
        } else {
            LamiSpriteStatus.Idle
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
        LamiStatus.TALKING -> LamiSpriteStatus.TalkLong
        LamiStatus.CONNECTING -> LamiSpriteStatus.Thinking
        LamiStatus.READY, LamiStatus.DEGRADED -> LamiSpriteStatus.Idle
        LamiStatus.NO_MODELS, LamiStatus.ERROR -> LamiSpriteStatus.Error
        LamiStatus.OFFLINE -> if (lastError.isNullOrBlank()) {
            LamiSpriteStatus.Idle
        } else {
            LamiSpriteStatus.Error
        }
        null -> LamiSpriteStatus.Idle
    }
}
