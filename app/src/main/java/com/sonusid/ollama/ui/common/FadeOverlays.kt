package com.sonusid.ollama.ui.common

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TopFadeOverlay(
    show: Boolean,
    bg: Color,
    tint: Color = bg,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    label: String = "topFade",
) {
    val alpha by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = label,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .clipToBounds()
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to tint.copy(alpha = 1.0f),
                            0.5f to tint.copy(alpha = 0.6f),
                            1.0f to tint.copy(alpha = 0.0f),
                        ),
                    ),
                    size = size,
                )
            },
    )
}

@Composable
fun BottomFadeOverlay(
    show: Boolean,
    bg: Color,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    label: String = "bottomFade",
) {
    val alpha by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = label,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .clipToBounds()
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to bg.copy(alpha = 0.0f),
                            0.5f to bg.copy(alpha = 0.6f),
                            1.0f to bg.copy(alpha = 1.0f),
                        ),
                    ),
                    size = size,
                )
            },
    )
}
