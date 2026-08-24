package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageErrorCode
import io.github.ninbyo02.lami.db.entity.MessageStatus

internal enum class AssistantMessageLifecycleAction {
    INSERT_PENDING,
    UPDATE_IN_FLIGHT,
    INSERT_COMPLETED,
    COMPLETE_IN_FLIGHT,
    INSERT_FAILED,
    FAIL_IN_FLIGHT,
    CANCEL_IN_FLIGHT,
    KEEP_EXISTING,
}

internal data class AssistantMessageLifecyclePlan(
    val action: AssistantMessageLifecycleAction,
    val messageId: Int? = null,
    val payload: Message? = null,
)

/**
 * Pure lifecycle decisions for the one assistant row owned by an active generation.
 *
 * Database transitions remain atomic in the repository. This policy decides which
 * transition is legal and prevents late output from rewriting a terminal row.
 */
internal object AssistantMessageLifecycle {
    fun planPlaceholder(
        existingMessageId: Int?,
        existingMessage: Message?,
        placeholderPayload: Message,
        nowEpochMs: Long,
    ): AssistantMessageLifecyclePlan {
        if (existingMessageId == null) {
            return AssistantMessageLifecyclePlan(
                action = AssistantMessageLifecycleAction.INSERT_PENDING,
                payload = placeholderPayload.copy(
                    status = MessageStatus.PENDING,
                    errorCode = null,
                    updatedAtEpochMs = nowEpochMs,
                ),
            )
        }
        if (existingMessage == null || existingMessage.status !in MessageStatus.IN_FLIGHT) {
            return AssistantMessageLifecyclePlan(
                action = AssistantMessageLifecycleAction.KEEP_EXISTING,
                messageId = existingMessageId,
            )
        }
        if (existingMessage.message == placeholderPayload.message) {
            return AssistantMessageLifecyclePlan(
                action = AssistantMessageLifecycleAction.KEEP_EXISTING,
                messageId = existingMessageId,
            )
        }
        return AssistantMessageLifecyclePlan(
            action = AssistantMessageLifecycleAction.UPDATE_IN_FLIGHT,
            messageId = existingMessageId,
            payload = placeholderPayload.copy(
                messageID = existingMessageId,
                status = existingMessage.status,
                errorCode = existingMessage.errorCode,
                createdAtEpochMs = existingMessage.createdAtEpochMs,
                updatedAtEpochMs = existingMessage.updatedAtEpochMs,
            ),
        )
    }

    fun planCompletion(
        existingMessageId: Int?,
        existingMessage: Message?,
        finalPayload: Message,
    ): AssistantMessageLifecyclePlan {
        if (existingMessageId == null) {
            return AssistantMessageLifecyclePlan(
                action = AssistantMessageLifecycleAction.INSERT_COMPLETED,
                payload = finalPayload.copy(
                    status = MessageStatus.COMPLETED,
                    errorCode = null,
                ),
            )
        }
        if (existingMessage == null || existingMessage.status !in MessageStatus.IN_FLIGHT) {
            return AssistantMessageLifecyclePlan(
                action = AssistantMessageLifecycleAction.KEEP_EXISTING,
                messageId = existingMessageId,
            )
        }
        return AssistantMessageLifecyclePlan(
            action = AssistantMessageLifecycleAction.COMPLETE_IN_FLIGHT,
            messageId = existingMessageId,
            payload = finalPayload.copy(messageID = existingMessageId),
        )
    }

    fun planFailure(
        existingMessageId: Int?,
        existingMessage: Message?,
        failurePayload: Message,
        nowEpochMs: Long,
    ): AssistantMessageLifecyclePlan {
        if (existingMessageId == null) {
            return AssistantMessageLifecyclePlan(
                action = AssistantMessageLifecycleAction.INSERT_FAILED,
                payload = failurePayload.copy(
                    status = MessageStatus.FAILED,
                    errorCode = MessageErrorCode.GENERATION_FAILED,
                    updatedAtEpochMs = nowEpochMs,
                ),
            )
        }
        if (existingMessage == null || existingMessage.status !in MessageStatus.IN_FLIGHT) {
            return AssistantMessageLifecyclePlan(
                action = AssistantMessageLifecycleAction.KEEP_EXISTING,
                messageId = existingMessageId,
            )
        }
        return AssistantMessageLifecyclePlan(
            action = AssistantMessageLifecycleAction.FAIL_IN_FLIGHT,
            messageId = existingMessageId,
            payload = failurePayload.copy(messageID = existingMessageId),
        )
    }

    fun planCancellation(
        existingMessageId: Int?,
        existingMessage: Message?,
    ): AssistantMessageLifecyclePlan {
        if (
            existingMessageId == null ||
            existingMessage == null ||
            existingMessage.status !in MessageStatus.IN_FLIGHT
        ) {
            return AssistantMessageLifecyclePlan(
                action = AssistantMessageLifecycleAction.KEEP_EXISTING,
                messageId = existingMessageId,
            )
        }
        return AssistantMessageLifecyclePlan(
            action = AssistantMessageLifecycleAction.CANCEL_IN_FLIGHT,
            messageId = existingMessageId,
        )
    }

    fun mergePayloadIntoTerminalMessage(
        terminalMessage: Message,
        payload: Message,
    ): Message = payload.copy(
        messageID = terminalMessage.messageID,
        status = terminalMessage.status,
        errorCode = terminalMessage.errorCode,
        createdAtEpochMs = terminalMessage.createdAtEpochMs,
        updatedAtEpochMs = terminalMessage.updatedAtEpochMs,
    )
}
