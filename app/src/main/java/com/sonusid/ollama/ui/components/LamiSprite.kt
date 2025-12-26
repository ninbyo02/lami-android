package com.sonusid.ollama.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    // IMPORTANT: imageResource is @Composable -> must be here, NOT inside Canvas draw lambda
    val bitmap: ImageBitmap = ImageBitmap.imageResource(id = spriteRes)

    val cols = columns.coerceAtLeast(1)
    val safeFrame = frameIndex.coerceAtLeast(0)
    val col = safeFrame % cols
    val row = safeFrame / cols

    val srcX = col * frameWidth
    val srcY = row * frameHeight

    Canvas(modifier = modifier) {
        // drawImage is available only in DrawScope (this lambda)
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(frameWidth, frameHeight),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(
                size.width.roundToInt().coerceAtLeast(1),
                size.height.roundToInt().coerceAtLeast(1),
            ),
        )
    }
}
