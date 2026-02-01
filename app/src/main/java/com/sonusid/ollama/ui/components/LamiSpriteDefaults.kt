package com.sonusid.ollama.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sonusid.ollama.viewmodels.LamiState

@Composable
internal fun rememberLamiCharacterBackdropColor(): Color {
    // FAB（＋ボタン）の既定 containerColor と同系色に統一する
    return MaterialTheme.colorScheme.primaryContainer
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
