package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageErrorCode
import io.github.ninbyo02.lami.db.entity.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantMessageLifecycleTest {
    @Test
    fun `placeholder without an existing row plans pending insert`() {
        val payload = message(text = "partial")

        val plan = AssistantMessageLifecycle.planPlaceholder(
            existingMessageId = null,
            existingMessage = null,
            placeholderPayload = payload,
            nowEpochMs = 200L,
        )

        assertEquals(AssistantMessageLifecycleAction.INSERT_PENDING, plan.action)
        assertEquals(MessageStatus.PENDING, plan.payload?.status)
        assertNull(plan.payload?.errorCode)
        assertEquals(200L, plan.payload?.updatedAtEpochMs)
    }

    @Test
    fun `changed generating placeholder plans atomic in flight update`() {
        val existing = message(id = 41, text = "partial", status = MessageStatus.GENERATING)

        val plan = AssistantMessageLifecycle.planPlaceholder(
            existingMessageId = existing.messageID,
            existingMessage = existing,
            placeholderPayload = message(text = "partial answer"),
            nowEpochMs = 300L,
        )

        assertEquals(AssistantMessageLifecycleAction.UPDATE_IN_FLIGHT, plan.action)
        assertEquals(41, plan.messageId)
        assertEquals("partial answer", plan.payload?.message)
        assertEquals(MessageStatus.GENERATING, plan.payload?.status)
    }

    @Test
    fun `late placeholder cannot overwrite a completed row`() {
        val existing = message(id = 42, text = "final", status = MessageStatus.COMPLETED)

        val plan = AssistantMessageLifecycle.planPlaceholder(
            existingMessageId = existing.messageID,
            existingMessage = existing,
            placeholderPayload = message(text = "late partial"),
            nowEpochMs = 400L,
        )

        assertEquals(AssistantMessageLifecycleAction.KEEP_EXISTING, plan.action)
        assertNull(plan.payload)
    }

    @Test
    fun `completion without placeholder plans completed insert`() {
        val plan = AssistantMessageLifecycle.planCompletion(
            existingMessageId = null,
            existingMessage = null,
            finalPayload = message(text = "answer", status = MessageStatus.PENDING),
        )

        assertEquals(AssistantMessageLifecycleAction.INSERT_COMPLETED, plan.action)
        assertEquals(MessageStatus.COMPLETED, plan.payload?.status)
        assertNull(plan.payload?.errorCode)
    }

    @Test
    fun `completion of generating row plans one terminal transition`() {
        val existing = message(id = 51, text = "partial", status = MessageStatus.GENERATING)

        val plan = AssistantMessageLifecycle.planCompletion(
            existingMessageId = existing.messageID,
            existingMessage = existing,
            finalPayload = message(text = "answer"),
        )

        assertEquals(AssistantMessageLifecycleAction.COMPLETE_IN_FLIGHT, plan.action)
        assertEquals(51, plan.messageId)
        assertEquals("answer", plan.payload?.message)
    }

    @Test
    fun `late completion cannot rewrite cancelled failed or completed rows`() {
        for (status in MessageStatus.TERMINAL) {
            val existing = message(id = 60, text = "terminal", status = status)
            val plan = AssistantMessageLifecycle.planCompletion(
                existingMessageId = existing.messageID,
                existingMessage = existing,
                finalPayload = message(text = "late answer"),
            )

            assertEquals(status, AssistantMessageLifecycleAction.KEEP_EXISTING, plan.action)
            assertNull(status, plan.payload)
        }
    }

    @Test
    fun `failure without placeholder plans failed insert`() {
        val plan = AssistantMessageLifecycle.planFailure(
            existingMessageId = null,
            existingMessage = null,
            failurePayload = message(text = "Generation failed"),
            nowEpochMs = 700L,
        )

        assertEquals(AssistantMessageLifecycleAction.INSERT_FAILED, plan.action)
        assertEquals(MessageStatus.FAILED, plan.payload?.status)
        assertEquals(MessageErrorCode.GENERATION_FAILED, plan.payload?.errorCode)
        assertEquals(700L, plan.payload?.updatedAtEpochMs)
    }

    @Test
    fun `cancellation of an in flight row plans one terminal transition`() {
        for (status in MessageStatus.IN_FLIGHT) {
            val existing = message(id = 65, text = "partial", status = status)
            val plan = AssistantMessageLifecycle.planCancellation(
                existingMessageId = existing.messageID,
                existingMessage = existing,
            )

            assertEquals(status, AssistantMessageLifecycleAction.CANCEL_IN_FLIGHT, plan.action)
            assertEquals(status, existing.messageID, plan.messageId)
            assertNull(status, plan.payload)
        }
    }

    @Test
    fun `cancellation cannot rewrite a terminal or missing row`() {
        for (status in MessageStatus.TERMINAL) {
            val existing = message(id = 66, text = "terminal", status = status)
            val plan = AssistantMessageLifecycle.planCancellation(
                existingMessageId = existing.messageID,
                existingMessage = existing,
            )

            assertEquals(status, AssistantMessageLifecycleAction.KEEP_EXISTING, plan.action)
            assertNull(status, plan.payload)
        }
        assertEquals(
            AssistantMessageLifecycleAction.KEEP_EXISTING,
            AssistantMessageLifecycle.planCancellation(
                existingMessageId = null,
                existingMessage = null,
            ).action,
        )
    }

    @Test
    fun `payload overlay preserves authoritative terminal metadata`() {
        val terminal = message(
            id = 71,
            text = "answer",
            status = MessageStatus.COMPLETED,
            createdAt = 100L,
            updatedAt = 900L,
        )
        val payload = message(text = "answer").copy(
            completionTokens = 12,
            tokensPerSecond = 24.0,
            finishReason = "stop",
        )

        val merged = AssistantMessageLifecycle.mergePayloadIntoTerminalMessage(
            terminalMessage = terminal,
            payload = payload,
        )

        assertEquals(71, merged.messageID)
        assertEquals(MessageStatus.COMPLETED, merged.status)
        assertNull(merged.errorCode)
        assertEquals(100L, merged.createdAtEpochMs)
        assertEquals(900L, merged.updatedAtEpochMs)
        assertEquals(12, merged.completionTokens)
        assertEquals(24.0, merged.tokensPerSecond ?: 0.0, 0.0)
        assertEquals("stop", merged.finishReason)
    }

    private fun message(
        id: Int = 0,
        text: String,
        status: String = MessageStatus.COMPLETED,
        createdAt: Long = 100L,
        updatedAt: Long = createdAt,
    ): Message = Message(
        messageID = id,
        chatId = 1,
        message = text,
        isSendbyMe = false,
        status = status,
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = updatedAt,
    )
}
