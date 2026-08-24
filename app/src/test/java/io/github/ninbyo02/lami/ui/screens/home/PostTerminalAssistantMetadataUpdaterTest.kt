package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageErrorCode
import io.github.ninbyo02.lami.db.entity.MessageStatus
import io.github.ninbyo02.lami.ui.model.InferenceStats
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

class PostTerminalAssistantMetadataUpdaterTest {
    @Test
    fun `inference stats enrich a completed row without changing body or lifecycle`() = runBlocking {
        val original = message(
            id = 11,
            chatId = 7,
            text = "final answer",
            status = MessageStatus.COMPLETED,
        ).copy(
            attachmentUriString = "content://one",
            attachmentUriStringsJson = "[\"content://one\"]",
        )
        val store = FakePostTerminalAssistantMetadataStore(original)
        val updater = PostTerminalAssistantMetadataUpdater(store)
        val stats = InferenceStats(
            modelName = "local-model",
            inputTokens = 4,
            outputTokens = 9,
            tokensPerSecond = 18.0,
            tokenCountMode = "tokenizer_recount",
            notes = "post-terminal recount",
            generationTimeMs = 500L,
            decodeDurationMs = 500L,
            totalDurationMs = 700L,
            generationDurationNs = 500_000_000L,
            evalDurationNs = 700_000_000L,
            modelLoadDurationNs = 100_000_000L,
            promptEvalDurationNs = 80_000_000L,
            finishReason = "stop",
            timeToFirstTokenMs = 120L,
            imageInputCount = 1,
        )

        val result = updater.update(
            messageId = original.messageID,
            expectedChatId = original.chatId,
            expectedMessage = original.message,
            patch = PostTerminalAssistantMetadataPatch.fromInferenceStats(
                stats = stats,
                localSourceSummary = "route_family=npu_quality_fallback",
            ),
        )

        assertEquals(PostTerminalAssistantMetadataUpdateOutcome.APPLIED, result.outcome)
        assertTrue(result.accepted)
        val updated = requireNotNull(store.messages[original.messageID])
        assertLifecycleAndBodyUnchanged(original = original, updated = updated)
        assertEquals(9, updated.completionTokens)
        assertEquals(4, updated.inputTokens)
        assertEquals(13, updated.totalTokens)
        assertEquals(18.0, updated.tokensPerSecond ?: 0.0, 0.0)
        assertEquals("tokenizer_recount", updated.tokenCountMode)
        assertEquals("post-terminal recount", updated.inferenceNotes)
        assertEquals(500L, updated.generationTimeMs)
        assertEquals(500L, updated.decodeDurationMs)
        assertEquals(700L, updated.totalDurationMs)
        assertEquals("local-model", updated.modelName)
        assertEquals("stop", updated.finishReason)
        assertEquals("route_family=npu_quality_fallback", updated.localSourceSummary)
        assertEquals(120L, updated.timeToFirstTokenMs)
        assertEquals(1, updated.imageInputCount)
    }

    @Test
    fun `summary-only enrichment preserves failure status error and existing metrics`() = runBlocking {
        val original = message(
            id = 12,
            chatId = 8,
            text = "Generation failed",
            status = MessageStatus.FAILED,
            errorCode = MessageErrorCode.GENERATION_FAILED,
        ).copy(
            completionTokens = 3,
            tokensPerSecond = 6.0,
            localSourceSummary = "before",
        )
        val store = FakePostTerminalAssistantMetadataStore(original)
        val updater = PostTerminalAssistantMetadataUpdater(store)

        val result = updater.update(
            messageId = original.messageID,
            expectedChatId = original.chatId,
            expectedMessage = original.message,
            patch = PostTerminalAssistantMetadataPatch(localSourceSummary = "after"),
        )

        assertEquals(PostTerminalAssistantMetadataUpdateOutcome.APPLIED, result.outcome)
        val updated = requireNotNull(store.messages[original.messageID])
        assertLifecycleAndBodyUnchanged(original = original, updated = updated)
        assertEquals(3, updated.completionTokens)
        assertEquals(6.0, updated.tokensPerSecond ?: 0.0, 0.0)
        assertEquals("after", updated.localSourceSummary)
    }

    @Test
    fun `identical metadata returns unchanged without a persistence write`() = runBlocking {
        val original = message(id = 13, chatId = 9, text = "answer").copy(
            localSourceSummary = "same",
        )
        val store = FakePostTerminalAssistantMetadataStore(original)
        val updater = PostTerminalAssistantMetadataUpdater(store)

        val result = updater.update(
            messageId = original.messageID,
            expectedChatId = original.chatId,
            expectedMessage = original.message,
            patch = PostTerminalAssistantMetadataPatch(localSourceSummary = "same"),
        )

        assertEquals(PostTerminalAssistantMetadataUpdateOutcome.UNCHANGED, result.outcome)
        assertTrue(result.accepted)
        assertEquals(0, store.updateCount)
    }

    @Test
    fun `invalid missing and mismatched identities are rejected before write`() = runBlocking {
        val original = message(id = 14, chatId = 10, text = "answer")
        val store = FakePostTerminalAssistantMetadataStore(original)
        val updater = PostTerminalAssistantMetadataUpdater(store)
        val patch = PostTerminalAssistantMetadataPatch(localSourceSummary = "metadata")

        assertEquals(
            PostTerminalAssistantMetadataUpdateOutcome.INVALID_MESSAGE_ID,
            updater.update(0, original.chatId, original.message, patch).outcome,
        )
        assertEquals(
            PostTerminalAssistantMetadataUpdateOutcome.MESSAGE_MISSING,
            updater.update(404, original.chatId, original.message, patch).outcome,
        )
        assertEquals(
            PostTerminalAssistantMetadataUpdateOutcome.CHAT_MISMATCH,
            updater.update(original.messageID, 999, original.message, patch).outcome,
        )
        assertEquals(
            PostTerminalAssistantMetadataUpdateOutcome.TEXT_MISMATCH,
            updater.update(original.messageID, original.chatId, "other", patch).outcome,
        )
        assertEquals(0, store.updateCount)
    }

    @Test
    fun `user and in-flight messages are rejected before write`() = runBlocking {
        val user = message(id = 15, chatId = 11, text = "user", isSendbyMe = true)
        val generating = message(
            id = 16,
            chatId = 11,
            text = "partial",
            status = MessageStatus.GENERATING,
        )
        val store = FakePostTerminalAssistantMetadataStore(user, generating)
        val updater = PostTerminalAssistantMetadataUpdater(store)
        val patch = PostTerminalAssistantMetadataPatch(localSourceSummary = "metadata")

        assertEquals(
            PostTerminalAssistantMetadataUpdateOutcome.NOT_ASSISTANT,
            updater.update(user.messageID, user.chatId, user.message, patch).outcome,
        )
        assertEquals(
            PostTerminalAssistantMetadataUpdateOutcome.NOT_TERMINAL,
            updater.update(generating.messageID, generating.chatId, generating.message, patch).outcome,
        )
        assertEquals(0, store.updateCount)
    }

    @Test
    fun `stats patch derives total tokens model and millisecond duration`() {
        val patch = PostTerminalAssistantMetadataPatch.fromInferenceStats(
            stats = InferenceStats(
                model = "legacy-model",
                inputTokens = 5,
                completionTokens = 7,
                inferenceTimeSec = 1.25,
                localSourceSummary = "stats-summary",
            ),
        )

        assertEquals("legacy-model", patch.modelName)
        assertEquals(7, patch.completionTokens)
        assertEquals(12, patch.totalTokens)
        assertEquals(1_250L, patch.generationTimeMs)
        assertEquals("stats-summary", patch.localSourceSummary)
    }

    @Test
    fun `post-terminal writes are serialized`() = runBlocking {
        val first = message(id = 21, chatId = 12, text = "first")
        val second = message(id = 22, chatId = 12, text = "second")
        val store = FakePostTerminalAssistantMetadataStore(first, second).apply {
            updateDelayMs = 40L
        }
        val updater = PostTerminalAssistantMetadataUpdater(store)

        coroutineScope {
            listOf(
                async {
                    updater.update(
                        messageId = first.messageID,
                        expectedChatId = first.chatId,
                        expectedMessage = first.message,
                        patch = PostTerminalAssistantMetadataPatch(localSourceSummary = "one"),
                    )
                },
                async {
                    updater.update(
                        messageId = second.messageID,
                        expectedChatId = second.chatId,
                        expectedMessage = second.message,
                        patch = PostTerminalAssistantMetadataPatch(localSourceSummary = "two"),
                    )
                },
            ).awaitAll()
        }

        assertEquals(1, store.maxConcurrentUpdates)
        assertEquals("one", store.messages[first.messageID]?.localSourceSummary)
        assertEquals("two", store.messages[second.messageID]?.localSourceSummary)
    }

    private class FakePostTerminalAssistantMetadataStore(
        vararg initialMessages: Message,
    ) : PostTerminalAssistantMetadataStore {
        val messages = initialMessages.associateBy { it.messageID }.toMutableMap()
        var updateCount = 0
        var updateDelayMs = 0L
        var activeUpdates = 0
        var maxConcurrentUpdates = 0

        override suspend fun getMessageById(messageId: Int): Message? = messages[messageId]

        override suspend fun updateMessage(message: Message) {
            activeUpdates += 1
            maxConcurrentUpdates = maxOf(maxConcurrentUpdates, activeUpdates)
            try {
                if (updateDelayMs > 0L) delay(updateDelayMs)
                messages[message.messageID] = message
                updateCount += 1
            } finally {
                activeUpdates -= 1
            }
        }
    }

    private fun message(
        id: Int,
        chatId: Int,
        text: String,
        status: String = MessageStatus.COMPLETED,
        errorCode: String? = null,
        isSendbyMe: Boolean = false,
    ): Message = Message(
        messageID = id,
        chatId = chatId,
        message = text,
        isSendbyMe = isSendbyMe,
        createdAtEpochMs = 100L,
        status = status,
        errorCode = errorCode,
        updatedAtEpochMs = 200L,
    )

    private fun assertLifecycleAndBodyUnchanged(original: Message, updated: Message) {
        assertEquals(original.messageID, updated.messageID)
        assertEquals(original.chatId, updated.chatId)
        assertEquals(original.message, updated.message)
        assertEquals(original.isSendbyMe, updated.isSendbyMe)
        assertEquals(original.attachmentUriString, updated.attachmentUriString)
        assertEquals(original.attachmentUriStringsJson, updated.attachmentUriStringsJson)
        assertEquals(original.createdAtEpochMs, updated.createdAtEpochMs)
        assertEquals(original.status, updated.status)
        assertEquals(original.errorCode, updated.errorCode)
        assertEquals(original.updatedAtEpochMs, updated.updatedAtEpochMs)
    }
}
