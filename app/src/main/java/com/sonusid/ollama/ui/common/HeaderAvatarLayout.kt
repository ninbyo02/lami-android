package com.sonusid.ollama.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset

private val HeaderAvatarStartOffset = (-8).dp

fun Modifier.headerAvatarModifier(): Modifier {
    return this.offset(x = HeaderAvatarStartOffset)
}
