package com.sonusid.ollama.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.sonusid.ollama.ui.common.LocalAppSnackbarHostState

@Composable
fun TestAppWrapper(content: @Composable () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    CompositionLocalProvider(
        LocalAppSnackbarHostState provides snackbarHostState,
    ) {
        content()
    }
}
