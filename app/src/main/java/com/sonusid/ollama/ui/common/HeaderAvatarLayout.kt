package com.sonusid.ollama.ui.common

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val HeaderAvatarStartOffset = (-6).dp

fun Modifier.headerAvatarModifier(): Modifier {
    // TopAppBar のデフォルト先頭インセット相殺目的。padding だと加算されるため offset を使う。
    // まだ右寄りなら -8.dp, -10.dp, -12.dp の順で微調整する。
    return this.offset(x = HeaderAvatarStartOffset)
}
