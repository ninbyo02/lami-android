package com.sonusid.ollama.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val HeaderAvatarStartPadding = 6.dp

fun Modifier.headerAvatarModifier(): Modifier {
    // 左上アバター：TopAppBar先頭からの開始位置を中央寄りにそろえるための最小余白
    return this.padding(start = HeaderAvatarStartPadding)
}
