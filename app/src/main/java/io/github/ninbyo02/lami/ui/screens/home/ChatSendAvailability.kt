package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.InferenceTarget
import io.github.ninbyo02.lami.util.validateUrlFormat

internal data class ChatSendAvailability(
    val enabled: Boolean,
    val blockedReason: ChatSendBlockedReason? = null,
)

internal enum class ChatSendBlockedReason {
    EMPTY_INPUT,
    LOCAL_MODEL_MISSING,
    SERVER_MISSING,
    SERVER_URL_INVALID,
    SERVER_MODEL_MISSING,
}

internal fun resolveChatSendAvailability(
    selectedInferenceTarget: InferenceTarget,
    selectedServerModel: String?,
    selectedLocalModelPath: String?,
    serverUrl: String?,
    hasPromptText: Boolean,
    hasImageInput: Boolean,
    isInferenceRunning: Boolean,
): ChatSendAvailability {
    if (isInferenceRunning) {
        return ChatSendAvailability(enabled = true)
    }
    if (!hasPromptText && !hasImageInput) {
        return ChatSendAvailability(
            enabled = false,
            blockedReason = ChatSendBlockedReason.EMPTY_INPUT,
        )
    }

    return when (selectedInferenceTarget) {
        InferenceTarget.LOCAL -> {
            if (!selectedLocalModelPath.isNullOrBlank()) {
                ChatSendAvailability(enabled = true)
            } else {
                ChatSendAvailability(
                    enabled = false,
                    blockedReason = ChatSendBlockedReason.LOCAL_MODEL_MISSING,
                )
            }
        }

        InferenceTarget.SERVER -> {
            val normalizedServerUrl = serverUrl.orEmpty().trim()
            when {
                normalizedServerUrl.isBlank() -> ChatSendAvailability(
                    enabled = false,
                    blockedReason = ChatSendBlockedReason.SERVER_MISSING,
                )

                !validateUrlFormat(normalizedServerUrl).isValid -> ChatSendAvailability(
                    enabled = false,
                    blockedReason = ChatSendBlockedReason.SERVER_URL_INVALID,
                )

                selectedServerModel.isNullOrBlank() -> ChatSendAvailability(
                    enabled = false,
                    blockedReason = ChatSendBlockedReason.SERVER_MODEL_MISSING,
                )

                else -> ChatSendAvailability(enabled = true)
            }
        }
    }
}

internal fun chatSendBlockedSnackbarMessage(reason: ChatSendBlockedReason?): String = when (reason) {
    ChatSendBlockedReason.LOCAL_MODEL_MISSING -> "ローカルモデルを選択してください"
    ChatSendBlockedReason.SERVER_MISSING -> "サーバーを追加してください"
    ChatSendBlockedReason.SERVER_URL_INVALID -> "サーバーURLを設定してください"
    ChatSendBlockedReason.SERVER_MODEL_MISSING -> "モデルを選択してください"
    ChatSendBlockedReason.EMPTY_INPUT,
    null -> "メッセージを入力してください"
}
