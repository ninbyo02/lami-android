package com.sonusid.ollama.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawImage
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sonusid.ollama.R
import kotlin.math.roundToInt

enum class LamiState {
    IDLE,
    LISTENING,
    THINKING,
    RESPONDING,
    ERROR,
}

data class LamiAnimSpec(
    val frames: Int,
    val fps: Int,
    val drawableRes: Int,
)

@Composable
fun LamiSprite(
    state: LamiState,
    modifier: Modifier = Modifier,
    sizeDp: Int = 1,
    animSpecs: Map<LamiState, LamiAnimSpec> = remember { defaultLamiAnimSpecs() },
) {
    val resolvedSpec = animSpecs[state] ?: animSpecs[LamiState.IDLE] ?: return
    val safeFrames = resolvedSpec.frames.coerceAtLeast(1)
    val safeFps = resolvedSpec.fps.coerceAtLeast(1)
    val frameDurationNanos = 1_000_000_000L / safeFps
    val spriteBitmap = remember(resolvedSpec.drawableRes) {
        ImageBitmap.imageResource(resolvedSpec.drawableRes)
    }

    var frameIndex by remember(resolvedSpec) { mutableStateOf(0) }

    LaunchedEffect(state, resolvedSpec) {
        var lastFrameTimeNanos = 0L
        while (true) {
            val frameTime = withFrameNanos { time ->
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = time
                }
                val elapsed = time - lastFrameTimeNanos
                if (elapsed >= frameDurationNanos) {
                    frameIndex = (frameIndex + 1) % safeFrames
                    lastFrameTimeNanos = time
                }
                time
            }
            val elapsedSinceLast = frameTime - lastFrameTimeNanos
            val remaining = (frameDurationNanos - elapsedSinceLast).coerceAtLeast(0L)
            if (remaining > 0) {
                // 少し待つことでフレームレートを維持しつつ無駄な CPU 消費を避ける
                kotlinx.coroutines.delay(remaining / 1_000_000L)
            }
        }
    }

    // 32dp を基準にした整数倍スケール。ニアレストネイバーと組み合わせてドット感を維持する。
    val displaySize = (32 * sizeDp).dp
    Canvas(
        modifier = modifier.size(displaySize)
    ) {
        val frameWidth = (spriteBitmap.width / safeFrames).coerceAtLeast(1)
        val srcOffsetX = (frameIndex % safeFrames) * frameWidth
        val destSize = Size(size.width, size.height)
        val destIntSize = IntSize(destSize.width.roundToInt(), destSize.height.roundToInt())

        drawImage(
            image = spriteBitmap,
            srcOffset = IntOffset(srcOffsetX, 0),
            srcSize = IntSize(frameWidth, spriteBitmap.height),
            dstSize = destIntSize,
            // ドット感を残すためにニアレストネイバー相当を指定
            filterQuality = FilterQuality.None,
        )
    }
}

private fun defaultLamiAnimSpecs(): Map<LamiState, LamiAnimSpec> {
    // TODO: 実際のスプライトシートが届き次第 drawableRes を差し替える
    val idle = LamiAnimSpec(frames = 1, fps = 1, drawableRes = R.drawable.logo)
    return mapOf(
        LamiState.IDLE to idle,
        LamiState.LISTENING to idle,
        LamiState.THINKING to idle,
        LamiState.RESPONDING to idle,
        LamiState.ERROR to idle,
    )
}
