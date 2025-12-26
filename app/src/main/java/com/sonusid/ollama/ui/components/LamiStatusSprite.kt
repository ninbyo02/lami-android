package com.sonusid.ollama.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class LamiSpriteStatus {
    Idle,
    Thinking,
    Speaking,
    Error,
}

data class AnimSpec(
    val frames: List<Int>,
    val frameMs: Long,
)

private val statusAnimationMap: Map<LamiSpriteStatus, AnimSpec> = mapOf(
    LamiSpriteStatus.Idle to AnimSpec(
        frames = listOf(0, 1, 2, 1),
        frameMs = 180L
    ),
    LamiSpriteStatus.Thinking to AnimSpec(
        frames = listOf(3, 4, 5, 4),
        frameMs = 150L
    ),
    LamiSpriteStatus.Speaking to AnimSpec(
        frames = listOf(6, 7, 8, 7),
        frameMs = 120L
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
) {
    val constrainedSize = remember(sizeDp) { sizeDp.coerceIn(32.dp, 56.dp) }
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
        modifier = modifier
    )
}
