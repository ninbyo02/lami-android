package com.sonusid.ollama.ui.common

import androidx.compose.ui.Modifier

fun Modifier.headerAvatarModifier(): Modifier {
    // TopAppBar の navigationIcon にアバターを移したため、位置補正は不要。
    return this
}
