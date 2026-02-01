package com.sonusid.ollama.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sonusid.ollama.viewmodels.LamiState

internal val LamiCharacterBackdropLight = Color(0xFFFFE3EC)
internal val LamiCharacterBackdropDark = Color(0xFFFFCDD9)

@Composable
internal fun rememberLamiCharacterBackdropColor(): Color {
    return if (isSystemInDarkTheme()) {
        LamiCharacterBackdropDark
    } else {
        LamiCharacterBackdropLight
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun resolveLamiSpriteBackgroundColor(
    state: LamiState,
    backgroundColor: Color?,
): Color {
    // 背景色はスプライト/アバター側で統一管理する（呼び出し側の責務分散を防ぐ）
    return backgroundColor ?: rememberLamiCharacterBackdropColor()
}
