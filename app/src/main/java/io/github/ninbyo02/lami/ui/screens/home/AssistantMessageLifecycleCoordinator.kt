package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageStatus
import io.github.ninbyo02.lami.viewmodels.OllamaViewModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface AssistantMessageLifecycleStore {
    suspend fun getMessageById(messageId: Int): Message?
    suspend fun insertAssistantMessage(message: Message): Int
    suspend fun markAssistantMessageGenerating(messageId: Int): Boolean
    suspend fun updateGeneratingAssistantMessageContent(messageId: Int, message: String): Boolean
    suspend fun completeAssistantMessage(messageId: Int, message: String): Boolean
    suspend fun cancelAssistantMessage(messageId: Int): Boolean
    suspend fun failAssistantMessage(messageId: Int, message: String? = null): Boolean
    suspend fun updateMessage(message: Message)
}

internal class OllamaViewModelAssistantMessageLifecycleStore(
    private val viewModel: OllamaViewModel,
) : AssistantMessageLifecycleStore {
    override suspend fun getMessageById(messageId: Int): Message? =
        viewModel.getMessageById(messageId)

    override suspend fun insertAssistantMessage(message: Message): Int =
        viewModel.insertAssistantMessageAndReturnId(message).toInt()

    override suspend fun markAssistantMessageGenerating(messageId: Int): Boolean =
        viewModel.markAssistantMessageGenerating(messageId)

    override suspend fun updateGeneratingAssistantMessageContent(
        messageId: Int,
        message: String,
    ): Boolean = viewModel.updateGeneratingAssistantMessageContent(messageId, message)

    override suspend fun completeAssistantMessage(messageId: Int, message: String): Boolean =
        viewModel.completeAssistantMessage(messageId, message)

    override suspend fun cancelAssistantMessage(messageId: Int): Boolean =
        viewModel.cancelAssistantMessage(messageId)

    override suspend fun failAssistantMessage(messageId: Int, message: String?): Boolean =
        viewModel.failAssistantMessage(messageId, message)

    override suspend fun updateMessage(message: Message) {
        viewModel.updateMessage(message)
    }
}

internal enum class AssistantMessageLifecycleExecutionOutcome {
    APPLIED,
    KEPT_EXISTING,
    LOST_RACE,
    START_FAILED,
    TERMINAL_ROW_MISSING,
}

internal data class AssistantMessageLifecycleExecutionResult(
    val action: AssistantMessageLifecycleAction,
    val outcome: AssistantMessageLifecycleExecutionOutcome,
    val messageId: Int? = null,
    val persistedText: String? = null,
    val existingStatus: String? = null,
) {
    val mutationApplied: Boolean
        get() = outcome == AssistantMessageLifecycleExecutionOutcome.APPLIED ||
            outcome == AssistantMessageLifecycleExecutionOutcome.TERMINAL_ROW_MISSING

    val placeholderOwnershipReady: Boolean
        get() = mutationApplied &&
            action in setOf(
                AssistantMessageLifecycleAction.INSERT_PENDING,
                AssistantMessageLifecycleAction.UPDATE_IN_FLIGHT,
            )

    val terminalTransitionApplied: Boolean
        get() = mutationApplied &&
            action in setOf(
                AssistantMessageLifecycleAction.INSERT_COMPLETED,
                AssistantMessageLifecycleAction.COMPLETE_IN_FLIGHT,
                AssistantMessageLifecycleAction.INSERT_FAILED,
                AssistantMessageLifecycleAction.FAIL_IN_FLIGHT,
                AssistantMessageLifecycleAction.CANCEL_IN_FLIGHT,
            )
}

/** Executes assistant lifecycle plans against the persistence boundary. */
internal class AssistantMessageLifecycleCoordinator(
    private val store: AssistantMessageLifecycleStore,
) {
    private val persistenceMutex = Mutex()

    suspend fun upsertPlaceholder(
        existingMessageId: Int?,
        placeholderPayload: Message,
        nowEpochMs: Long,
        startFailureMessage: String = DEFAULT_START_FAILURE_MESSAGE,
    ): AssistantMessageLifecycleExecutionResult = persistenceMutex.withLock {
        upsertPlaceholderLocked(
            existingMessageId = existingMessageId,
            placeholderPayload = placeholderPayload,
            nowEpochMs = nowEpochMs,
            startFailureMessage = startFailureMessage,
        )
    }

    private suspend fun upsertPlaceholderLocked(
        existingMessageId: Int?,
        placeholderPayload: Message,
        nowEpochMs: Long,
        startFailureMessage: String,
    ): AssistantMessageLifecycleExecutionResult {
        val existingMessage = if (existingMessageId != null) {
            store.getMessageById(existingMessageId)
        } else {
            null
        }
        val plan = AssistantMessageLifecycle.planPlaceholder(
            existingMessageId = existingMessageId,
            existingMessage = existingMessage,
            placeholderPayload = placeholderPayload,
            nowEpochMs = nowEpochMs,
        )
        return when (plan.action) {
            AssistantMessageLifecycleAction.INSERT_PENDING -> {
                val payload = requireNotNull(plan.payload)
                val insertedId = store.insertAssistantMessage(payload)
                if (!store.markAssistantMessageGenerating(insertedId)) {
                    val failureApplied = store.failAssistantMessage(insertedId, startFailureMessage)
                    AssistantMessageLifecycleExecutionResult(
                        action = plan.action,
                        outcome = if (failureApplied) {
                            AssistantMessageLifecycleExecutionOutcome.START_FAILED
                        } else {
                            AssistantMessageLifecycleExecutionOutcome.LOST_RACE
                        },
                        messageId = insertedId,
                        persistedText = startFailureMessage.takeIf { failureApplied },
                    )
                } else {
                    AssistantMessageLifecycleExecutionResult(
                        action = plan.action,
                        outcome = AssistantMessageLifecycleExecutionOutcome.APPLIED,
                        messageId = insertedId,
                        persistedText = payload.message,
                    )
                }
            }

            AssistantMessageLifecycleAction.UPDATE_IN_FLIGHT -> {
                val messageId = requireNotNull(plan.messageId)
                val currentMessage = requireNotNull(existingMessage)
                val markedGenerating = currentMessage.status != MessageStatus.PENDING ||
                    store.markAssistantMessageGenerating(messageId)
                if (!markedGenerating) {
                    AssistantMessageLifecycleExecutionResult(
                        action = plan.action,
                        outcome = AssistantMessageLifecycleExecutionOutcome.LOST_RACE,
                        messageId = messageId,
                        existingStatus = currentMessage.status,
                    )
                } else if (
                    !store.updateGeneratingAssistantMessageContent(
                        messageId = messageId,
                        message = requireNotNull(plan.payload).message,
                    )
                ) {
                    AssistantMessageLifecycleExecutionResult(
                        action = plan.action,
                        outcome = AssistantMessageLifecycleExecutionOutcome.LOST_RACE,
                        messageId = messageId,
                        existingStatus = currentMessage.status,
                    )
                } else {
                    AssistantMessageLifecycleExecutionResult(
                        action = plan.action,
                        outcome = AssistantMessageLifecycleExecutionOutcome.APPLIED,
                        messageId = messageId,
                        persistedText = plan.payload.message,
                        existingStatus = currentMessage.status,
                    )
                }
            }

            AssistantMessageLifecycleAction.KEEP_EXISTING ->
                keepExistingResult(plan = plan, existingMessage = existingMessage)

            else -> error("Unexpected placeholder lifecycle action: ${plan.action}")
        }
    }

    suspend fun complete(
        existingMessageId: Int?,
        finalPayload: Message,
    ): AssistantMessageLifecycleExecutionResult = persistenceMutex.withLock {
        completeLocked(
            existingMessageId = existingMessageId,
            finalPayload = finalPayload,
        )
    }

    private suspend fun completeLocked(
        existingMessageId: Int?,
        finalPayload: Message,
    ): AssistantMessageLifecycleExecutionResult {
        val existingMessage = if (existingMessageId != null) {
            store.getMessageById(existingMessageId)
        } else {
            null
        }
        val plan = AssistantMessageLifecycle.planCompletion(
            existingMessageId = existingMessageId,
            existingMessage = existingMessage,
            finalPayload = finalPayload,
        )
        return when (plan.action) {
            AssistantMessageLifecycleAction.INSERT_COMPLETED -> {
                val payload = requireNotNull(plan.payload)
                val insertedId = store.insertAssistantMessage(payload)
                AssistantMessageLifecycleExecutionResult(
                    action = plan.action,
                    outcome = AssistantMessageLifecycleExecutionOutcome.APPLIED,
                    messageId = insertedId,
                    persistedText = payload.message,
                )
            }

            AssistantMessageLifecycleAction.COMPLETE_IN_FLIGHT ->
                executeTerminalTransition(
                    plan = plan,
                    existingMessage = existingMessage,
                    transition = { messageId, text ->
                        store.completeAssistantMessage(messageId, text)
                    },
                )

            AssistantMessageLifecycleAction.KEEP_EXISTING ->
                keepExistingResult(plan = plan, existingMessage = existingMessage)

            else -> error("Unexpected completion lifecycle action: ${plan.action}")
        }
    }

    suspend fun fail(
        existingMessageId: Int?,
        failurePayload: Message,
        nowEpochMs: Long,
    ): AssistantMessageLifecycleExecutionResult = persistenceMutex.withLock {
        failLocked(
            existingMessageId = existingMessageId,
            failurePayload = failurePayload,
            nowEpochMs = nowEpochMs,
        )
    }

    private suspend fun failLocked(
        existingMessageId: Int?,
        failurePayload: Message,
        nowEpochMs: Long,
    ): AssistantMessageLifecycleExecutionResult {
        val existingMessage = if (existingMessageId != null) {
            store.getMessageById(existingMessageId)
        } else {
            null
        }
        val plan = AssistantMessageLifecycle.planFailure(
            existingMessageId = existingMessageId,
            existingMessage = existingMessage,
            failurePayload = failurePayload,
            nowEpochMs = nowEpochMs,
        )
        return when (plan.action) {
            AssistantMessageLifecycleAction.INSERT_FAILED -> {
                val payload = requireNotNull(plan.payload)
                val insertedId = store.insertAssistantMessage(payload)
                AssistantMessageLifecycleExecutionResult(
                    action = plan.action,
                    outcome = AssistantMessageLifecycleExecutionOutcome.APPLIED,
                    messageId = insertedId,
                    persistedText = payload.message,
                )
            }

            AssistantMessageLifecycleAction.FAIL_IN_FLIGHT ->
                executeTerminalTransition(
                    plan = plan,
                    existingMessage = existingMessage,
                    transition = { messageId, text ->
                        store.failAssistantMessage(messageId, text)
                    },
                )

            AssistantMessageLifecycleAction.KEEP_EXISTING ->
                keepExistingResult(plan = plan, existingMessage = existingMessage)

            else -> error("Unexpected failure lifecycle action: ${plan.action}")
        }
    }

    suspend fun cancel(
        existingMessageId: Int?,
    ): AssistantMessageLifecycleExecutionResult = persistenceMutex.withLock {
        val existingMessage = if (existingMessageId != null) {
            store.getMessageById(existingMessageId)
        } else {
            null
        }
        val plan = AssistantMessageLifecycle.planCancellation(
            existingMessageId = existingMessageId,
            existingMessage = existingMessage,
        )
        when (plan.action) {
            AssistantMessageLifecycleAction.CANCEL_IN_FLIGHT -> {
                val messageId = requireNotNull(plan.messageId)
                if (!store.cancelAssistantMessage(messageId)) {
                    AssistantMessageLifecycleExecutionResult(
                        action = plan.action,
                        outcome = AssistantMessageLifecycleExecutionOutcome.LOST_RACE,
                        messageId = messageId,
                        existingStatus = existingMessage?.status,
                    )
                } else {
                    val cancelledMessage = store.getMessageById(messageId)
                    AssistantMessageLifecycleExecutionResult(
                        action = plan.action,
                        outcome = AssistantMessageLifecycleExecutionOutcome.APPLIED,
                        messageId = messageId,
                        persistedText = cancelledMessage?.message ?: existingMessage?.message,
                        existingStatus = cancelledMessage?.status ?: existingMessage?.status,
                    )
                }
            }

            AssistantMessageLifecycleAction.KEEP_EXISTING ->
                keepExistingResult(plan = plan, existingMessage = existingMessage)

            else -> error("Unexpected cancellation lifecycle action: ${plan.action}")
        }
    }

    private suspend fun executeTerminalTransition(
        plan: AssistantMessageLifecyclePlan,
        existingMessage: Message?,
        transition: suspend (messageId: Int, text: String) -> Boolean,
    ): AssistantMessageLifecycleExecutionResult {
        val messageId = requireNotNull(plan.messageId)
        val payload = requireNotNull(plan.payload)
        if (!transition(messageId, payload.message)) {
            return AssistantMessageLifecycleExecutionResult(
                action = plan.action,
                outcome = AssistantMessageLifecycleExecutionOutcome.LOST_RACE,
                messageId = messageId,
                existingStatus = existingMessage?.status,
            )
        }
        val terminalMessage = store.getMessageById(messageId)
            ?: return AssistantMessageLifecycleExecutionResult(
                action = plan.action,
                outcome = AssistantMessageLifecycleExecutionOutcome.TERMINAL_ROW_MISSING,
                messageId = messageId,
                persistedText = payload.message,
                existingStatus = existingMessage?.status,
            )
        store.updateMessage(
            AssistantMessageLifecycle.mergePayloadIntoTerminalMessage(
                terminalMessage = terminalMessage,
                payload = payload,
            )
        )
        return AssistantMessageLifecycleExecutionResult(
            action = plan.action,
            outcome = AssistantMessageLifecycleExecutionOutcome.APPLIED,
            messageId = messageId,
            persistedText = payload.message,
            existingStatus = terminalMessage.status,
        )
    }

    private fun keepExistingResult(
        plan: AssistantMessageLifecyclePlan,
        existingMessage: Message?,
    ): AssistantMessageLifecycleExecutionResult = AssistantMessageLifecycleExecutionResult(
        action = plan.action,
        outcome = AssistantMessageLifecycleExecutionOutcome.KEPT_EXISTING,
        messageId = plan.messageId,
        persistedText = existingMessage?.message,
        existingStatus = existingMessage?.status,
    )

    private companion object {
        const val DEFAULT_START_FAILURE_MESSAGE = "Failed to start local generation"
    }
}
