package com.sonusid.ollama.ui.screens.settings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.sonusid.ollama.BuildConfig

private const val TopGapProbeTag = "TopGapProbe"

internal val TopGapProbeTopBarColor = Color(0x33FF6B6B)
internal val TopGapProbeContentColor = Color(0x334D96FF)
internal val TopGapProbeActualContentColor = Color(0x3347C97A)

internal fun Modifier.topGapProbeOverlay(color: Color): Modifier {
    return if (BuildConfig.DEBUG) {
        background(color)
    } else {
        this
    }
}

internal fun Modifier.topGapProbeBounds(label: String): Modifier {
    return if (BuildConfig.DEBUG) {
        onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            Log.d(TopGapProbeTag, "$label ${bounds.toProbeMessage()}")
        }
    } else {
        this
    }
}

@Composable
internal fun TopGapProbeLog(message: String) {
    if (BuildConfig.DEBUG) {
        SideEffect {
            Log.d(TopGapProbeTag, message)
        }
    }
}

private fun Rect.toProbeMessage(): String {
    return "x=${left.formatProbeValue()} y=${top.formatProbeValue()} width=${width.formatProbeValue()} height=${height.formatProbeValue()}"
}

private fun Float.formatProbeValue(): String = String.format("%.1f", this)
