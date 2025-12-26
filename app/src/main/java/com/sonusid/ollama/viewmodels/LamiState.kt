package com.sonusid.ollama.viewmodels

import com.sonusid.ollama.UiState
import com.sonusid.ollama.viewmodels.LamiStatus.CONNECTING
import com.sonusid.ollama.viewmodels.LamiStatus.DEGRADED
import com.sonusid.ollama.viewmodels.LamiStatus.ERROR
import com.sonusid.ollama.viewmodels.LamiStatus.NO_MODELS
import com.sonusid.ollama.viewmodels.LamiStatus.OFFLINE
import com.sonusid.ollama.viewmodels.LamiStatus.READY
import com.sonusid.ollama.viewmodels.LamiStatus.TALKING

enum class LamiState {
    IDLE,
    THINKING,
    ERROR,
}

fun mapToLamiState(uiState: UiState, selectedModel: String?): LamiState {
    if (selectedModel.isNullOrBlank()) {
        return LamiState.ERROR
    }
    return when (uiState) {
        UiState.Loading -> LamiState.THINKING
        is UiState.Error -> LamiState.IDLE
        is UiState.Success -> LamiState.IDLE
        is UiState.ModelsLoaded -> LamiState.IDLE
        UiState.Initial -> LamiState.IDLE
    }
}

fun mapToLamiState(
    lamiStatus: LamiStatus,
    selectedModel: String?,
    lastError: String?,
): LamiState {
    if (selectedModel.isNullOrBlank()) {
        // モデル未選択時はユーザーに明示的な動作要求を促したいので ERROR 側に寄せる。
        return LamiState.ERROR
    }

    return when (lamiStatus) {
        TALKING -> LamiState.THINKING // 音声出力中は思考中アニメーションを共有して動きを示す。
        CONNECTING -> LamiState.THINKING // 接続確立中は待機が必要なため思考中として扱う。
        READY -> LamiState.IDLE // 正常時は落ち着いた待機表示。
        DEGRADED -> LamiState.IDLE // フォールバック運用中でも利用可能なためエラーにはせず静的表示。
        NO_MODELS -> LamiState.ERROR // モデル未取得はユーザー操作が必要なのでエラーとして警告する。
        OFFLINE -> if (lastError.isNullOrBlank()) {
            // 接続が切れていても明確なエラーが無ければ落ち着いた IDLE を優先。
            LamiState.IDLE
        } else {
            // 接続エラー内容がある場合はリカバリを促すため ERROR に倒す。
            LamiState.ERROR
        }
        ERROR -> LamiState.ERROR // 明示的なエラー状態はそのまま赤系の表情にする。
    }
}
