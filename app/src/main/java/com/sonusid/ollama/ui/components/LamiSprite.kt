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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.sprite.SpriteSheetData
import com.sonusid.ollama.sprite.SpriteSheetFrameRegion
import com.sonusid.ollama.sprite.SpriteSheetLoadResult
import com.sonusid.ollama.sprite.rememberLamiSpriteSheetState
import com.sonusid.ollama.viewmodels.LamiState
import kotlin.math.roundToInt

private val DefaultSpriteSheetConfig = SpriteSheetConfig.default3x3()
private val DefaultFrameIndexBound = (DefaultSpriteSheetConfig.rows * DefaultSpriteSheetConfig.cols - 1).coerceAtLeast(0)

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
    val spriteSheetState by rememberLamiSpriteSheetState(DefaultSpriteSheetConfig)
    val spriteSheetData: SpriteSheetData? = (spriteSheetState as? SpriteSheetLoadResult.Success)?.data
    val safeFrameIndex = frameIndex.coerceIn(0, spriteSheetData?.frameCount?.minus(1) ?: DefaultFrameIndexBound)
    val sheetFrameRegion: SpriteSheetFrameRegion? = remember(spriteSheetData, safeFrameIndex) {
        spriteSheetData?.frameRegion(frameIndex = safeFrameIndex)
    }
    val bitmap = spriteSheetData?.imageBitmap ?: return
    val defaultFrameSize = sheetFrameRegion?.srcSize ?: IntSize.Zero
    val baseOffset = sheetFrameRegion?.srcOffset ?: IntOffset.Zero
    if (defaultFrameSize.width <= 0 || defaultFrameSize.height <= 0) {
        return
    }
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
        frameSrcSizeMap[safeFrameIndex] ?: defaultFrameSize
    } else {
        defaultFrameSize
    }
    val frameRegion = remember(sheetFrameRegion, srcOffset, srcSize) {
        SpriteFrameRegion(
            srcOffset = srcOffset,
            srcSize = srcSize,
        )
    }

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
        drawFrameRegion(
            sheet = bitmap,
            region = frameRegion,
            dstSize = dstSize,
            dstOffset = dstOffset,
            placeholder = { offset, size -> drawFramePlaceholder(offset, size) },
        )
    }
}

@Immutable
data class LamiSpriteFrameMaps(
    val offsetMap: Map<Int, IntOffset>,
    val sizeMap: Map<Int, IntSize>,
)

fun LamiSpriteFrameMaps.toFrameYOffsetPxMap(): Map<Int, Int> {
    val bottoms = offsetMap.mapNotNull { (index, offset) ->
        val size = sizeMap[index] ?: return@mapNotNull null
        val bottom = offset.y + size.height - 1
        index to bottom
    }
    if (bottoms.isEmpty()) {
        return emptyMap()
    }
    val baselineBottom = bottoms.maxOf { it.second }
    return bottoms.associate { (index, bottom) ->
        index to (baselineBottom - bottom)
    }
}

@Composable
fun rememberLamiSprite3x3FrameMaps(): LamiSpriteFrameMaps {
    val spriteSheetState by rememberLamiSpriteSheetState(DefaultSpriteSheetConfig)
    return remember(spriteSheetState) {
        val spriteSheetData = (spriteSheetState as? SpriteSheetLoadResult.Success)?.data
        val measuredMaps = spriteSheetData?.let { data ->
            measureFrameMaps(bitmap = data.bitmap, frameSize = data.cellSize, columns = data.cols)
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
