package io.github.ninbyo02.lami.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf

val LocalAppSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("LocalAppSnackbarHostState not provided")
}
