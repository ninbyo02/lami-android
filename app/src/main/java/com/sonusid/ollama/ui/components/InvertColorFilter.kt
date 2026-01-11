package com.sonusid.ollama.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

@Composable
fun rememberInvertColorFilterForDarkTheme(): ColorFilter? {
    val isDark = isSystemInDarkTheme()
    return remember(isDark) {
        if (!isDark) {
            return@remember null
        }
        // ダークテーマ時のみ画像を反転するためのフィルター
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
}
