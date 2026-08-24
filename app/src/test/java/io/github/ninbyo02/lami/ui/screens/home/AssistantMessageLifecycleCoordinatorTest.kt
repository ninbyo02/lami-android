package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageErrorCode
import io.github.ninbyo02.lami.db.entity.MessageStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMessageLifecycleCoordinatorTest {
    @Test
    fun `new placeholder is inserted and promoted to generating`() = runBlocking {
        val store = FakeAssistantMessageLifecycleStore()
        val coordinator = AssistantMessageLifecycleCoordinator(store)

        val result = coordinator.upsertPlaceholder(
            existingMessageId = null,
            placeholderPayload = message(text = "partial"),
            nowEpochMs = 200L,
        )

        assertEquals(AssistantMessageLifecycleAction.INSERT_PENDING, result.action)
        assertEquals(AssistantMessageLifecycleExecutionOutcome.APPLIED, result.outcome)
        assertTrue(result.placeholderOwnershipReady)
        assertEquals("partial", result.persistedText)
        assertEquals(MessageStatus.GENERATING, store.messages[result.messageId]?.status)
        assertEquals(listOf("insert", "mark-generating:${result.messageId}"), store.calls)
    }

    @Test
    fun `placeholder start failure is terminalized by the coordinator`() = runBlocking {
        val store = FakeAssistantMessageLifecycleStore().apply {
            markGeneratingResult = false
        }
        val coordinator = AssistantMessageLifecycleCoordinator(store)

        val result = coordinator.upsertPlaceholder(
            existingMessageId = null,
            placeholderPayload = message(text = "partial"),
            nowEpochMs = 200L,
        )

        assertEquals(AssistantMessageLifecycleExecutionOutcome.START_FAILED, result.outcome)
        assertFalse(result.placeholderOwnershipReady)
        assertEquals("Failed to start local generation", result.persistedText)
        val stored = store.messages[result.messageId]
        assertEquals(MessageStatus.FAILED, stored?.status)
        assertEquals(MessageErrorCode.GENERATION_FAILED, stored?.errorCode)
        assertEquals("Failed to start local generation", stored?.message)
    }

    @Test
    fun `stream update losing the terminal race does not overwrite the row`() = runBlocking {
        val store = FakeAssistantMessageLifecycleStore().apply {
            messages[10] = message(id = 10, text = "partial", status = MessageStatus.GENERATING)
            updateGeneratingResult = false
        }
        val coordinator = AssistantMessageLifecycleCoordinator(store)

        val result = coordinator.upsertPlaceholder(
            existingMessageId = 10,
            placeholderPayload = message(text = "late partial"),
            nowEpochMs = 300L,
        )

        assertEquals(AssistantMessageLifecycleExecutionOutcome.LOST_RACE, result.outcome)
        assertFalse(result.mutationApplied)
        assertEquals("partial", store.messages[10]?.message)
        assertEquals(MessageStatus.GENERATING, store.messages[10]?.status)
    }

    @Test
    fun `completion executes transition and overlays inference metadata`() = runBlocking {
        val store = FakeAssistantMessageLifecycleStore().apply {
            messages[20] = message(id = 20, text = "partial", status = MessageStatus.GENERATING)
        }
        val coordinator = AssistantMessageLifecycleCoordinator(store)
        val payload = message(text = "answer").copy(
            completionTokens = 8,
            tokensPerSecond = 16.0,
            finishReason = "stop",
        )

        val result = coordinator.complete(
            existingMessageId = 20,
            finalPayload = payload,
        )

        assertEquals(AssistantMessageLifecycleExecutionOutcome.APPLIED, result.outcome)
        assertTrue(result.terminalTransitionApplied)
        val stored = store.messages[20]
        assertEquals(MessageStatus.COMPLETED, stored?.status)
        assertNull(stored?.errorCode)
        assertEquals("answer", stored?.message)
        assertEquals(8, stored?.completionTokens)
        assertEquals(16.0, stored?.tokensPerSecond ?: 0.0, 0.0)
        assertEquals("stop", stored?.finishReason)
        assertTrue(store.calls.contains("complete:20"))
        assertTrue(store.calls.contains("update:20"))
    }

    @Test
    fun `terminal completion is kept without any persistence mutation`() = runBlocking {
        val store = FakeAssistantMessageLifecycleStore().apply {
            messages[30] = message(id = 30, text = "terminal", status = MessageStatus.COMPLETED)
        }
        val coordinator = AssistantMessageLifecycleCoordinator(store)

        val result = coordinator.complete(
            existingMessageId = 30,
            finalPayload = message(text = "late answer"),
        )

        assertEquals(AssistantMessageLifecycleExecutionOutcome.KEPT_EXISTING, result.outcome)
        assertFalse(result.terminalTransitionApplied)
        assertEquals("terminal", result.persistedText)
        assertEquals(listOf("get:30"), store.calls)
        assertEquals("terminal", store.messages[30]?.message)
    }

    @Test
    fun `failure executes one terminal transition and overlays diagnostics`() = runBlocking {
        val store = FakeAssistantMessageLifecycleStore().apply {
            messages[40] = message(id = 40, text = "partial", status = MessageStatus.PENDING)
        }
        val coordinator = AssistantMessageLifecycleCoordinator(store)
        val payload = message(text = "Generation failed").copy(
            finishReason = "empty_after_sanitize",
            localSourceSummary = "route_family=npu_standard",
        )

        val result = coordinator.fail(
            existingMessageId = 40,
            failurePayload = payload,
            nowEpochMs = 500L,
        )

        assertEquals(AssistantMessageLifecycleExecutionOutcome.APPLIED, result.outcome)
        assertTrue(result.terminalTransitionApplied)
        val stored = store.messages[40]
        assertEquals(MessageStatus.FAILED, stored?.status)
        assertEquals(MessageErrorCode.GENERATION_FAILED, stored?.errorCode)
        assertEquals("Generation failed", stored?.message)
        assertEquals("empty_after_sanitize", stored?.finishReason)
        assertEquals("route_family=npu_standard", stored?.localSourceSummary)
    }

    @Test
    fun `cancellation transitions an in flight row and preserves its text`() = runBlocking {
        val store = FakeAssistantMessageLifecycleStore().apply {
            messages[44] = message(id = 44, text = "partial", status = MessageStatus.GENERATING)
        }
        val coordinator = AssistantMessageLifecycleCoordinator(store)
        val result = coordinator.cancel(existingMessageId = 44)
        assertEquals(AssistantMessageLifecycleAction.CANCEL_IN_FLIGHT, result.action)
        assertEquals(AssistantMessageLifecycleExecutionOutcome.APPLIED, result.outcome)
        assertTrue(result.terminalTransitionApplied)
        assertEquals("partial", result.persistedText)
        assertEquals(MessageStatus.CANCELLED, store.messages[44]?.status)
        assertEquals(MessageErrorCode.USER_CANCELLED, store.messages[44]?.errorCode)
        assertTrue(store.calls.contains("cancel:44"))
    }

    @Test
    fun `cancellation of a terminal row is kept without mutation`() = runBlocking {
        val store = FakeAssistantMessageLifecycleStore().apply {
            messages[45] = message(id = 45, text = "done", status = MessageStatus.COMPLETED)
        }
        val coordinator = AssistantMessageLifecycleCoordinator(store)
        val result = coordinator.cancel(existingMessageId = 45)
        assertEquals(AssistantMessageLifecycleExecutionOutcome.KEPT_EXISTING, result.outcome)
        assertFalse(result.terminalTransitionApplied)
        assertEquals(listOf("get:45"), store.calls)
        assertEquals(MessageStatus.COMPLETED, store.messages[45]?.status)
    }

    @Test
    fun `coordinator serializes concurrent terminal persistence`() = runBlocking {
        val store = FakeAssistantMessageLifecycleStore().apply {
            messages[45] = message(id = 45, text = "partial-a", status = MessageStatus.GENERATING)
            messages[46] = message(id = 46, text = "partial-b", status = MessageStatus.GENERATING)
            terminalTransitionDelayMs = 40L
        }
        val coordinator = AssistantMessageLifecycleCoordinator(store)

        coroutineScope {
            listOf(
                async {
                    coordinator.complete(
                        existingMessageId = 45,
                        finalPayload = message(text = "answer-a"),
                    )
                },
                async {
                    coordinator.complete(
                        existingMessageId = 46,
                        finalPayload = message(text = "answer-b"),
                    )
                },
            ).awaitAll()
        }

        assertEquals(1, store.maxConcurrentTerminalTransitions)
        assertEquals(MessageStatus.COMPLETED, store.messages[45]?.status)
        assertEquals(MessageStatus.COMPLETED, store.messages[46]?.status)
    }

    @Test
    fun `missing terminal row is reported after an applied transition`() = runBlocking {
        val store = FakeAssistantMessageLifecycleStore().apply {
            messages[50] = message(id = 50, text = "partial", status = MessageStatus.GENERATING)
            removeRowAfterCompletion = true
        }
        val coordinator = AssistantMessageLifecycleCoordinator(store)

        val result = coordinator.complete(
            existingMessageId = 50,
            finalPayload = message(text = "answer"),
        )

        assertEquals(AssistantMessageLifecycleExecutionOutcome.TERMINAL_ROW_MISSING, result.outcome)
        assertTrue(result.terminalTransitionApplied)
        assertEquals(50, result.messageId)
        assertEquals("answer", result.persistedText)
    }

    private class FakeAssistantMessageLifecycleStore : AssistantMessageLifecycleStore {
        val messages = linkedMapOf<Int, Message>()
        val calls = mutableListOf<String>()
        var nextId = 100
        var markGeneratingResult = true
        var updateGeneratingResult = true
        var completeResult = true
        var cancelResult = true
        var failResult = true
        var removeRowAfterCompletion = false
        var terminalTransitionDelayMs = 0L
        var activeTerminalTransitions = 0
        var maxConcurrentTerminalTransitions = 0

        override suspend fun getMessageById(messageId: Int): Message? {
            calls += "get:$messageId"
            return messages[messageId]
        }

        override suspend fun insertAssistantMessage(message: Message): Int {
            calls += "insert"
            val id = if (message.messageID > 0) message.messageID else nextId++
            messages[id] = message.copy(messageID = id)
            return id
        }

        override suspend fun markAssistantMessageGenerating(messageId: Int): Boolean {
            calls += "mark-generating:$messageId"
            if (!markGeneratingResult) return false
            val existing = messages[messageId] ?: return false
            if (existing.status !in MessageStatus.IN_FLIGHT) return false
            messages[messageId] = existing.copy(status = MessageStatus.GENERATING)
            return true
        }

        override suspend fun updateGeneratingAssistantMessageContent(
            messageId: Int,
            message: String,
        ): Boolean {
            calls += "update-generating:$messageId"
            if (!updateGeneratingResult) return false
            val existing = messages[messageId] ?: return false
            if (existing.status != MessageStatus.GENERATING) return false
            messages[messageId] = existing.copy(message = message)
            return true
        }

        override suspend fun completeAssistantMessage(messageId: Int, message: String): Boolean {
            calls += "complete:$messageId"
            if (!completeResult) return false
            val existing = messages[messageId] ?: return false
            if (existing.status !in MessageStatus.IN_FLIGHT) return false
            activeTerminalTransitions += 1
            maxConcurrentTerminalTransitions = maxOf(
                maxConcurrentTerminalTransitions,
                activeTerminalTransitions,
            )
            try {
                if (terminalTransitionDelayMs > 0L) delay(terminalTransitionDelayMs)
                messages[messageId] = existing.copy(
                    message = message,
                    status = MessageStatus.COMPLETED,
                    errorCode = null,
                )
                if (removeRowAfterCompletion) messages.remove(messageId)
                return true
            } finally {
                activeTerminalTransitions -= 1
            }
        }

        override suspend fun cancelAssistantMessage(messageId: Int): Boolean {
            calls += "cancel:$messageId"
            if (!cancelResult) return false
            val existing = messages[messageId] ?: return false
            if (existing.status !in MessageStatus.IN_FLIGHT) return false
            messages[messageId] = existing.copy(
                status = MessageStatus.CANCELLED,
                errorCode = MessageErrorCode.USER_CANCELLED,
            )
            return true
        }

        override suspend fun failAssistantMessage(messageId: Int, message: String?): Boolean {
            calls += "fail:$messageId"
            if (!failResult) return false
            val existing = messages[messageId] ?: return false
            if (existing.status !in MessageStatus.IN_FLIGHT) return false
            messages[messageId] = existing.copy(
                message = message ?: existing.message,
                status = MessageStatus.FAILED,
                errorCode = MessageErrorCode.GENERATION_FAILED,
            )
            return true
        }

        override suspend fun updateMessage(message: Message) {
            calls += "update:${message.messageID}"
            messages[message.messageID] = message
        }
    }

    private fun message(
        id: Int = 0,
        text: String,
        status: String = MessageStatus.COMPLETED,
    ): Message = Message(
        messageID = id,
        chatId = 1,
        message = text,
        isSendbyMe = false,
        status = status,
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
    )
}
