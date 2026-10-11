package io.github.ninbyo02.lami.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.withTimeoutOrNull

// プロジェクト定義のSnackbar短時間(900ms)
internal const val PROJECT_SNACKBAR_SHORT_MS = 900L

internal suspend fun SnackbarHostState.showProjectShortSnackbar(message: String) {
    currentSnackbarData?.dismiss()
    // Cancel this request, not an unrelated snackbar that may follow it.
    withTimeoutOrNull(PROJECT_SNACKBAR_SHORT_MS) {
        showSnackbar(message = message, duration = SnackbarDuration.Indefinite)
    }
}
