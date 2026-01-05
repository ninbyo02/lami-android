package com.sonusid.ollama.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
internal fun ReadyPreviewCardShell(
    cardHeightModifier: Modifier,
    contentHorizontalPadding: Dp,
    effectiveInnerVPadDp: Int,
    innerBottomDp: Int,
    effectiveInnerBottomDp: Int,
    innerPaddingColor: Color,
    innerPaddingStroke: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = cardHeightModifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentHorizontalPadding, vertical = effectiveInnerVPadDp.dp)
                .drawBehind {
                    val indicatorHeight = abs(innerBottomDp).dp.toPx().coerceAtMost(size.height)
                    if (indicatorHeight > 0f) {
                        val top = size.height - indicatorHeight
                        drawRect(
                            color = innerPaddingColor,
                            topLeft = Offset(x = 0f, y = top),
                            size = Size(width = size.width, height = indicatorHeight)
                        )
                        drawLine(
                            color = innerPaddingStroke,
                            start = Offset(x = 0f, y = top),
                            end = Offset(x = size.width, y = top),
                            strokeWidth = 2f
                        )
                    }
                }
                .padding(bottom = effectiveInnerBottomDp.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}

@Composable
internal fun ReadyPreviewSlot(
    cardHeightModifier: Modifier,
    contentHorizontalPadding: Dp,
    effectiveInnerVPadDp: Int,
    innerBottomDp: Int,
    effectiveInnerBottomDp: Int,
    innerPaddingColor: Color,
    innerPaddingStroke: Color,
    sprite: @Composable () -> Unit,
    info: @Composable ColumnScope.() -> Unit,
    controls: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            ReadyPreviewCardShell(
                cardHeightModifier = cardHeightModifier,
                contentHorizontalPadding = contentHorizontalPadding,
                effectiveInnerVPadDp = effectiveInnerVPadDp,
                innerBottomDp = innerBottomDp,
                effectiveInnerBottomDp = effectiveInnerBottomDp,
                innerPaddingColor = innerPaddingColor,
                innerPaddingStroke = innerPaddingStroke,
                content = info
            )
            sprite()
        }
        controls?.invoke()
    }
}
