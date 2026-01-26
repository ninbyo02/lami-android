package com.sonusid.ollama.viewmodels

import com.sonusid.ollama.UiState

enum class LamiAnimationStatus {
    Idle,
    Thinking,
    TalkShort,
    TalkLong,
    TalkCalm,
    ErrorLight,
    ErrorHeavy,
    OfflineLoop,
    Ready,
}

private fun LamiAnimationStatus.isOffline(): Boolean {
    return this == LamiAnimationStatus.OfflineLoop
}

private fun decideErrorSeverity(lastError: String?, retryCount: Int): LamiAnimationStatus {
    val normalized = lastError?.lowercase().orEmpty()
    val looksSevere = normalized.length >= 40 ||
        listOf("timeout", "refused", "unreachable", "offline", "reset", "unresolved")
            .any { normalized.contains(it) }
    val exceededRetries = retryCount >= 2
    return if (looksSevere || exceededRetries) {
        LamiAnimationStatus.ErrorHeavy
    } else {
        LamiAnimationStatus.ErrorLight
    }
}

/**
 * 新しいアニメーション状態 enum を返すマッパー。
 * Offline は OfflineLoop のみで扱い、
 * 話速 bucket やエラー強度もここで決定する。
 */
fun mapToAnimationLamiStatus(
    lamiState: LamiState?,
    uiState: UiState,
    selectedModel: String?,
    isTtsPlaying: Boolean = false,
    lastError: String? = (uiState as? UiState.Error)?.errorMessage,
    retryCount: Int = 0,
    previousStatus: LamiAnimationStatus = LamiAnimationStatus.Idle,
    talkingTextLength: Int? = null,
): LamiAnimationStatus {
    val speakingBucket = when (lamiState) {
        is LamiState.Speaking -> bucket(lamiState.textLength)
        else -> talkingTextLength?.let { bucket(it) }
    }

    val talkingStatus = when {
        isTtsPlaying || lamiState is LamiState.Speaking -> when (speakingBucket) {
            1 -> LamiAnimationStatus.TalkShort
            2 -> LamiAnimationStatus.TalkLong
            3 -> LamiAnimationStatus.TalkCalm
            else -> LamiAnimationStatus.TalkShort
        }

        else -> null
    }

    if (talkingStatus != null) {
        return talkingStatus
    }

    val hasModels = !selectedModel.isNullOrBlank()
    val offlineFromError = lastError?.contains("offline", ignoreCase = true) == true ||
        lastError?.contains("network", ignoreCase = true) == true
    val shouldOffline = !hasModels || offlineFromError

    if (shouldOffline) {
        return LamiAnimationStatus.OfflineLoop
    }

    val hasError = !lastError.isNullOrBlank() || uiState is UiState.Error
    if (hasError) {
        return decideErrorSeverity(lastError, retryCount)
    }

    if (uiState is UiState.Loading || lamiState is LamiState.Thinking) {
        return LamiAnimationStatus.Thinking
    }

    return if (hasModels) {
        LamiAnimationStatus.Ready
    } else {
        LamiAnimationStatus.Idle
    }
}

/**
 * 既存の LamiStatus API を壊さないためのラッパー。
 * 新 enum から従来の LamiStatus にフォールバック変換する。
 */
fun mapToAnimationLamiStatus(
    lamiState: LamiState?,
    uiState: UiState,
    selectedModel: String?,
    isTtsPlaying: Boolean = false,
    lastError: String? = (uiState as? UiState.Error)?.errorMessage,
): LamiStatus {
    val animation = mapToAnimationLamiStatus(
        lamiState = lamiState,
        uiState = uiState,
        selectedModel = selectedModel,
        isTtsPlaying = isTtsPlaying,
        lastError = lastError,
        // previousStatus を明示して LamiAnimationStatus オーバーロードを確実に利用する。
        // 影響範囲はこのラッパー関数内に限定。
        previousStatus = LamiAnimationStatus.Idle,
    )

    return when (animation) {
        LamiAnimationStatus.Thinking -> LamiStatus.CONNECTING
        LamiAnimationStatus.TalkShort,
        LamiAnimationStatus.TalkLong,
        LamiAnimationStatus.TalkCalm -> LamiStatus.TALKING
        LamiAnimationStatus.ErrorLight,
        LamiAnimationStatus.ErrorHeavy -> LamiStatus.ERROR
        LamiAnimationStatus.OfflineLoop -> LamiStatus.OFFLINE
        LamiAnimationStatus.Ready -> LamiStatus.READY
        LamiAnimationStatus.Idle -> if (selectedModel.isNullOrBlank()) {
            LamiStatus.NO_MODELS
        } else {
            LamiStatus.READY
        }
    }
}
