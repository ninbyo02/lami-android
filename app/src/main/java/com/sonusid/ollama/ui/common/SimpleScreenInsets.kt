package com.sonusid.ollama.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only

// 単純画面では左右の system bars のみを Scaffold 側で受け持つ。
val SimpleScreenHorizontalInsets: WindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
