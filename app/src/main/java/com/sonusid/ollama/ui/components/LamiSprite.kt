package com.sonusid.ollama.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sonusid.ollama.R
import com.sonusid.ollama.viewmodels.LamiState
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
    val bitmap: ImageBitmap = ImageBitmap.imageResource(id = spriteRes)
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
fun LamiSprite(
    state: LamiState,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, tint) = remember(state) {
        when (state) {
            LamiState.THINKING -> MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer

            LamiState.ERROR -> MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer

            LamiState.IDLE -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
        }
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .background(backgroundColor, CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Lami sprite $state",
            tint = tint,
            modifier = Modifier.fillMaxSize()
        )
    }
}
