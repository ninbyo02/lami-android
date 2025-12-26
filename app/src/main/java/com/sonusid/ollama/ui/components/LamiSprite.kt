package com.sonusid.ollama.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawImage
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

@Composable
fun LamiSprite(
    @DrawableRes spriteRes: Int,
    frameIndex: Int,
    frameWidth: Int,
    frameHeight: Int,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val spriteSheet = remember(spriteRes) { ImageBitmap.imageResource(id = spriteRes) }

    Canvas(modifier = modifier) {
        if (frameWidth <= 0 || frameHeight <= 0 || columns <= 0) return@Canvas
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val columnIndex = frameIndex % columns
        val rowIndex = frameIndex / columns

        val srcOffset = IntOffset(columnIndex * frameWidth, rowIndex * frameHeight)
        val srcSize = IntSize(frameWidth, frameHeight)

        val dstWidth = size.width.roundToInt().coerceAtLeast(1)
        val dstHeight = size.height.roundToInt().coerceAtLeast(1)

        drawImage(
            image = spriteSheet,
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstSize = IntSize(dstWidth, dstHeight),
            filterQuality = FilterQuality.None,
        )
    }
}
