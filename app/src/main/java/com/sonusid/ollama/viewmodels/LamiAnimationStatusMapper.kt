package com.sonusid.ollama.viewmodels

import com.sonusid.ollama.UiState

/**
 * アニメーションで使用する LamiStatus を UI/内部ステートから算出するシンプルなマッパー。
 * - talking: 音声再生や RESPONDING フラグを優先
 * - loading: UiState が Loading もしくは THINKING 状態
 * - error  : UiState.Error, lastError, LamiState.ERROR のいずれか
 */
fun mapToAnimationLamiStatus(
    lamiState: LamiState?,
    uiState: UiState,
    selectedModel: String?,
    isTtsPlaying: Boolean = false,
    lastError: String? = (uiState as? UiState.Error)?.errorMessage,
): LamiStatus {
    if (isTtsPlaying || lamiState == LamiState.RESPONDING) {
        return LamiStatus.TALKING
    }

    if (uiState is UiState.Loading || lamiState == LamiState.THINKING) {
        return LamiStatus.CONNECTING
    }

    if (!lastError.isNullOrBlank() || uiState is UiState.Error || lamiState == LamiState.ERROR) {
        return LamiStatus.ERROR
    }

    if (selectedModel.isNullOrBlank()) {
        return LamiStatus.NO_MODELS
    }

    return LamiStatus.READY
}
