package com.sonusid.ollama.viewmodels

import com.sonusid.ollama.UiState

/**
 * アニメーションで使用する LamiStatus を UI/内部ステートから算出するシンプルなマッパー。
 * - talking: 音声再生や Speaking ステートを優先
 * - loading: UiState が Loading もしくは Thinking 状態
 * - error  : UiState.Error または lastError がある場合
 */
fun mapToAnimationLamiStatus(
    lamiState: LamiState?,
    uiState: UiState,
    selectedModel: String?,
    isTtsPlaying: Boolean = false,
    lastError: String? = (uiState as? UiState.Error)?.errorMessage,
): LamiStatus {
    if (isTtsPlaying || lamiState is LamiState.Speaking) {
        return LamiStatus.TALKING
    }

    if (uiState is UiState.Loading || lamiState is LamiState.Thinking) {
        return LamiStatus.CONNECTING
    }

    if (!lastError.isNullOrBlank() || uiState is UiState.Error) {
        return LamiStatus.ERROR
    }

    if (selectedModel.isNullOrBlank()) {
        return LamiStatus.NO_MODELS
    }

    return LamiStatus.READY
}
