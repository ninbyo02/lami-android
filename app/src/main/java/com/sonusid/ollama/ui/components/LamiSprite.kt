package com.sonusid.ollama.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import com.sonusid.ollama.R
import com.sonusid.ollama.viewmodels.LamiState
import com.sonusid.ollama.ui.components.mapToLamiSpriteStatus
import kotlin.math.roundToInt

@Composable
fun LamiSprite(
    spriteRes: Int,
    frameIndex: Int,
    frameWidth: Int,
    frameHeight: Int,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberSpriteSheet(spriteRes)
    val resolvedFrameWidth = frameWidth.takeIf { it > 0 } ?: bitmap.width
    val resolvedFrameHeight = frameHeight.takeIf { it > 0 } ?: bitmap.height

    val cols = columns.coerceAtLeast(1)
    val safeFrame = frameIndex.coerceAtLeast(0)
    val col = safeFrame % cols
    val row = safeFrame / cols

    val srcX = col * resolvedFrameWidth
    val srcY = row * resolvedFrameHeight

    Canvas(modifier = modifier) {
        val dstW = size.width.roundToInt().coerceAtLeast(1)
        val dstH = size.height.roundToInt().coerceAtLeast(1)

        drawIntoCanvas { canvas ->
            canvas.drawImageRect(
                image = bitmap,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(resolvedFrameWidth, resolvedFrameHeight),
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize(dstW, dstH),
                paint = Paint()
            )
        }
    }
}

@Composable
fun LamiSprite3x3(
    frameIndex: Int,
    sizeDp: Dp = 48.dp,
    modifier: Modifier = Modifier,
    contentOffsetDp: Dp = 0.dp,
    contentOffsetYDp: Dp = 0.dp,
    frameYOffsetPxMap: Map<Int, Int> = emptyMap(),
    frameSrcOffsetMap: Map<Int, IntOffset> = emptyMap(),
    frameSrcSizeMap: Map<Int, IntSize> = emptyMap(),
    autoCropTransparentArea: Boolean = false,
) {
    val bitmap = rememberSpriteSheet(R.drawable.lami_sprite_3x3_288)
    val safeFrameIndex = frameIndex.coerceAtLeast(0)
    val col = safeFrameIndex % 3
    val row = safeFrameIndex / 3

    val baseOffset = IntOffset(x = col * 96, y = row * 96)
    val srcOffset = if (autoCropTransparentArea) {
        val srcOffsetAdjustment = frameSrcOffsetMap[safeFrameIndex] ?: IntOffset.Zero
        IntOffset(
            x = baseOffset.x + srcOffsetAdjustment.x,
            y = baseOffset.y + srcOffsetAdjustment.y,
        )
    } else {
        baseOffset
    }
    val srcSize = if (autoCropTransparentArea) {
        frameSrcSizeMap[safeFrameIndex] ?: IntSize(width = 96, height = 96)
    } else {
        IntSize(width = 96, height = 96)
    }
    val paint = remember { Paint().apply { filterQuality = FilterQuality.None } }

    val dstSize = with(LocalDensity.current) {
        val sizePx = sizeDp.roundToPx().coerceAtLeast(1)
        IntSize(sizePx, sizePx)
    }

    val dstOffset = with(LocalDensity.current) {
        val baseOffsetX = contentOffsetDp.roundToPx()
        val baseOffsetY = contentOffsetYDp.roundToPx()
        val frameYOffsetPx = frameYOffsetPxMap[safeFrameIndex] ?: 0
        val scaleY = dstSize.height.toFloat() / srcSize.height
        IntOffset(
            x = baseOffsetX,
            y = baseOffsetY + (frameYOffsetPx * scaleY).roundToInt()
        )
    }

    Canvas(modifier = modifier.size(sizeDp)) {
        drawIntoCanvas { canvas ->
            canvas.drawImageRect(
                image = bitmap,
                srcOffset = srcOffset,
                srcSize = srcSize,
                dstOffset = dstOffset,
                dstSize = dstSize,
                paint = paint
            )
        }
    }
}

@Immutable
data class LamiSpriteFrameMaps(
    val offsetMap: Map<Int, IntOffset>,
    val sizeMap: Map<Int, IntSize>,
)

@Composable
fun rememberLamiSprite3x3FrameMaps(): LamiSpriteFrameMaps {
    val context = LocalContext.current
    return remember {
        val frameSize = IntSize(width = 96, height = 96)
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.lami_sprite_3x3_288)
        val measuredMaps = bitmap?.let {
            measureFrameMaps(bitmap = it, frameSize = frameSize, columns = 3)
        }
        measuredMaps ?: LamiSpriteFrameMaps(offsetMap = emptyMap(), sizeMap = emptyMap())
    }
}

private fun measureFrameMaps(
    bitmap: Bitmap,
    frameSize: IntSize,
    columns: Int,
): LamiSpriteFrameMaps {
    val offsets = mutableMapOf<Int, IntOffset>()
    val sizes = mutableMapOf<Int, IntSize>()
    val frameWidth = frameSize.width.coerceAtLeast(1)
    val frameHeight = frameSize.height.coerceAtLeast(1)
    val rows = (bitmap.height / frameHeight).coerceAtLeast(1)
    val frameCount = columns * rows

    for (frameIndex in 0 until frameCount) {
        val col = frameIndex % columns
        val row = frameIndex / columns
        val startX = col * frameWidth
        val startY = row * frameHeight
        val endX = (startX + frameWidth).coerceAtMost(bitmap.width)
        val endY = (startY + frameHeight).coerceAtMost(bitmap.height)

        var left = frameWidth
        var top = frameHeight
        var right = -1
        var bottom = -1

        for (y in startY until endY) {
            for (x in startX until endX) {
                val alpha = (bitmap.getPixel(x, y) ushr 24) and 0xFF
                if (alpha != 0) {
                    val localX = x - startX
                    val localY = y - startY
                    if (localX < left) left = localX
                    if (localY < top) top = localY
                    if (localX > right) right = localX
                    if (localY > bottom) bottom = localY
                }
            }
        }

        if (right >= left && bottom >= top) {
            offsets[frameIndex] = IntOffset(x = left, y = top)
            sizes[frameIndex] = IntSize(width = right - left + 1, height = bottom - top + 1)
        }
    }

    return LamiSpriteFrameMaps(
        offsetMap = offsets,
        sizeMap = sizes,
    )
}

@Composable
private fun rememberSpriteSheet(@DrawableRes resId: Int): ImageBitmap {
    val context = LocalContext.current
    return remember(resId) {
        BitmapFactory
            .decodeResource(context.resources, resId)
            .asImageBitmap()
    }
}

@Composable
fun LamiSprite(
    state: LamiState,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    animationsEnabled: Boolean = true,
    replacementEnabled: Boolean = true,
    blinkEffectEnabled: Boolean = true,
) {
    val backgroundColor = when (state) {
        is LamiState.Thinking -> MaterialTheme.colorScheme.secondaryContainer
        is LamiState.Speaking -> MaterialTheme.colorScheme.tertiaryContainer
        LamiState.Idle -> MaterialTheme.colorScheme.primaryContainer
    }
    val spriteStatus = mapToLamiSpriteStatus(lamiState = state)

    val contentPadding = 6.dp
    val spriteSize = sizeDp - (contentPadding * 2)

    Box(
        modifier = modifier
            .size(sizeDp)
            .background(backgroundColor, shape)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        LamiStatusSprite(
            status = spriteStatus,
            sizeDp = spriteSize,
            modifier = Modifier.clip(shape),
            animationsEnabled = animationsEnabled,
            replacementEnabled = replacementEnabled,
            blinkEffectEnabled = blinkEffectEnabled,
        )
    }
}
