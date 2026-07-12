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

internal fun shouldShowTransientAssistantRow(
    currentChatId: Int?,
    isInferenceRunning: Boolean,
    streamingAssistantMessageId: Int?,
    streamingResponseText: String?,
    lastPersistedStreamingAssistantText: String?,
): Boolean {
    if (currentChatId == null) return false
    if (!isInferenceRunning) return false
    if (streamingAssistantMessageId != null) return false
    val normalizedStreamingText = streamingResponseText?.trim().orEmpty()
    if (normalizedStreamingText.isBlank()) return false
    return normalizedStreamingText != lastPersistedStreamingAssistantText
}

internal fun shouldShowPendingLocalUserMessage(
    currentChatId: Int?,
    pendingLocalUserMessageText: String?,
    latestPersistedUserMessageText: String?,
): Boolean {
    if (currentChatId == null) return false
    val normalizedPendingText = pendingLocalUserMessageText?.trim().orEmpty()
    if (normalizedPendingText.isBlank()) return false
    return normalizedPendingText != latestPersistedUserMessageText?.trim()
}

internal fun stableChatMessageKey(
    messages: List<io.github.ninbyo02.lami.db.entity.Message>,
    index: Int,
): String {
    val message = messages[index]
    if (!message.isSendbyMe) {
        return message.messageID.takeIf { it != 0 }?.let { "message-$it" }
            ?: "assistant-${message.chatId}-$index-${message.message.hashCode()}"
    }

    val normalizedText = message.message.trim()
    val sameTextOccurrence = messages
        .take(index + 1)
        .count { candidate ->
            candidate.isSendbyMe && candidate.message.trim() == normalizedText
        }
    // User rows deliberately do not depend on messageID. The pending row has ID 0,
    // then Room replaces it with an auto-generated ID; keeping this key stable avoids
    // Compose disposing/recreating the bubble at a visibly different position.
    return "user-${message.chatId}-${normalizedText.hashCode()}-$sameTextOccurrence"
}

internal sealed interface ChatScrollDecision {
    data object None : ChatScrollDecision
    data class Item(val index: Int) : ChatScrollDecision
}

internal fun resolveChatAppendScrollDecision(
    previousMessages: List<io.github.ninbyo02.lami.db.entity.Message>,
    currentMessages: List<io.github.ninbyo02.lami.db.entity.Message>,
    isNearBottom: Boolean,
    autoFollowEnabled: Boolean,
): ChatScrollDecision {
    if (currentMessages.isEmpty()) return ChatScrollDecision.None

    val previousUserCount = previousMessages.count { it.isSendbyMe }
    val currentUserCount = currentMessages.count { it.isSendbyMe }
    if (currentUserCount > previousUserCount) {
        val latestUserIndex = currentMessages.indexOfLast { it.isSendbyMe }
        return if (latestUserIndex >= 0) {
            ChatScrollDecision.Item(latestUserIndex)
        } else {
            ChatScrollDecision.None
        }
    }

    val previousKeys = previousMessages.indices.map {
        stableChatMessageKey(previousMessages, it)
    }
    val currentKeys = currentMessages.indices.map {
        stableChatMessageKey(currentMessages, it)
    }
    if (previousKeys == currentKeys) return ChatScrollDecision.None

    return if (
        currentMessages.size > previousMessages.size &&
        isNearBottom &&
        autoFollowEnabled
    ) {
        ChatScrollDecision.Item(currentMessages.lastIndex)
    } else {
        ChatScrollDecision.None
    }
}

internal fun shouldShowLocalRespondingPlaceholder(
    isLocalRunning: Boolean,
    localStopRequested: Boolean,
    streamingAssistantMessageId: Int?,
    localStreamingResponseText: String?,
    showDelayedPlaceholder: Boolean,
): Boolean {
    // Keep local/NPU chat visually aligned with the server route: do not add a
    // separate "応答中..." bubble while waiting. The assistant row appears when
    // real text is available via the transient/streaming path.
    return false
}
