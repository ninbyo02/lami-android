package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.entity.MessageStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostResponseTokenStatsUpdaterTest {
    private class Store : PostTerminalAssistantMetadataStore {
        var row: Message? = Message(messageID = 1, chatId = 2, message = "answer", isSendbyMe = false,
            status = MessageStatus.COMPLETED)
        var writes = 0
        override suspend fun getMessageById(messageId: Int) = row?.takeIf { it.messageID == messageId }
        override suspend fun updateMessage(message: Message) { row = message; writes++ }
    }
    private fun counted() = LocalInferenceTrace(measuredTokenSnapshot = LocalInferenceMeasuredTokenSnapshot(
        inputTokens = 3, outputTokens = 5, totalTokens = 8, decodeDurationMs = 100,
        totalDurationMs = 200, tokenCountMode = "mediapipe_tokenizer_recount",
    ))
    private fun PostResponseTokenStatsUpdater.submit(trace: LocalInferenceTrace = LocalInferenceTrace()) =
        scheduleLocalTokenizerStatsUpdate(1, 2, "answer", "question", trace, "model", 200, "route_family=local_gpu")

    @Test fun countsThenPublishesOnlyGuardedMetadata() = runTest {
        val store = Store(); val before = store.row!!; var published = 0
        val updater = PostResponseTokenStatsUpdater(this, PostTerminalAssistantMetadataUpdater(store),
            recount = { assertEquals("question", it.prompt); counted() },
            metadataDispatcher = StandardTestDispatcher(testScheduler),
            onStatsUpdated = { id, stats -> assertEquals(1, id); assertEquals(5, stats.outputTokens); published++ })
        updater.submit()
        advanceUntilIdle()
        assertEquals(1, store.writes); assertEquals(1, published)
        assertEquals(before.message, store.row!!.message); assertEquals(before.status, store.row!!.status)
        assertEquals(200L, store.row!!.generationTimeMs)
    }

    @Test fun deletedOrEditedResponseCannotReceiveLateStatistics() = runTest {
        for (deleted in listOf(true, false)) {
            val store = Store(); val release = CompletableDeferred<Unit>(); var published = false
            val updater = PostResponseTokenStatsUpdater(this, PostTerminalAssistantMetadataUpdater(store),
                recount = { release.await(); counted() }, metadataDispatcher = StandardTestDispatcher(testScheduler),
                onStatsUpdated = { _, _ -> published = true })
            updater.submit(); runCurrent()
            store.row = if (deleted) null else store.row!!.copy(message = "edited")
            release.complete(Unit); advanceUntilIdle()
            assertEquals(0, store.writes); assertFalse(published)
        }
    }

    @Test fun ownerCancellationPreventsLatePublication() = runTest {
        val store = Store(); val owner = Job(); var cleaned = false
        val updater = PostResponseTokenStatsUpdater(CoroutineScope(StandardTestDispatcher(testScheduler) + owner),
            PostTerminalAssistantMetadataUpdater(store),
            recount = { try { awaitCancellation() } finally { cleaned = true } },
            metadataDispatcher = StandardTestDispatcher(testScheduler),
            onStatsUpdated = { _, _ -> error("cancelled owner published") })
        updater.submit(); runCurrent(); owner.cancelAndJoin(); advanceUntilIdle()
        assertTrue(cleaned); assertEquals(0, store.writes)
    }

    @Test fun fallbackCountsAnswerButGuardsFullPersistedTextAndKeepsRoute() = runTest {
        val store = Store()
        val fullText = "GPU fallback\nanswer"
        store.row = store.row!!.copy(message = fullText)
        var published = 0
        val updater = PostResponseTokenStatsUpdater(this, PostTerminalAssistantMetadataUpdater(store),
            recount = { assertEquals("answer", it.response); counted() },
            metadataDispatcher = StandardTestDispatcher(testScheduler),
            onStatsUpdated = { _, _ -> published++ })
        updater.scheduleNpuFallbackTokenizerStatsUpdate(1, 2, fullText, "question", "answer", "GPU", LocalInferenceTrace(), "model")
        advanceUntilIdle()
        assertEquals(1, published); assertEquals(1, store.writes)
        assertEquals(fullText, store.row!!.message)
        assertTrue(store.row!!.localSourceSummary!!.contains("fallback_path=NPU,GPU"))
    }

    @Test fun standaloneEligibilityIsLimitedToExplicitSuccessfulGpu() = runTest {
        for ((requested, applied) in listOf("GPU" to "GPU", "CPU" to "CPU", "NPU" to "GPU", "GPU" to "CPU", null to null)) {
            val store = Store(); var eligible: Boolean? = null
            val updater = PostResponseTokenStatsUpdater(this, PostTerminalAssistantMetadataUpdater(store),
                recount = { eligible = it.allowStandaloneGpu; counted() }, metadataDispatcher = StandardTestDispatcher(testScheduler),
                onStatsUpdated = { _, _ -> })
            updater.submit(LocalInferenceTrace(requestedPreferredBackend = requested, appliedPreferredBackend = applied))
            advanceUntilIdle()
            assertEquals(requested == "GPU" && applied == "GPU", eligible)
        }
    }

    @Test fun npuFallbackNeverOptsIntoStandaloneEvenWithGpuTrace() = runTest {
        val store = Store(); var called = false
        val updater = PostResponseTokenStatsUpdater(this, PostTerminalAssistantMetadataUpdater(store),
            recount = { assertFalse(it.allowStandaloneGpu); called = true; counted() },
            metadataDispatcher = StandardTestDispatcher(testScheduler), onStatsUpdated = { _, _ -> })
        updater.scheduleNpuFallbackTokenizerStatsUpdate(1, 2, "answer", "question", "answer", "GPU",
            LocalInferenceTrace(requestedPreferredBackend = "GPU", appliedPreferredBackend = "GPU"), "model")
        advanceUntilIdle(); assertTrue(called)
    }

    @Test fun completeMeasuredCountsDoNotScheduleAnotherCounter() = runTest {
        val store = Store()
        val updater = PostResponseTokenStatsUpdater(this, PostTerminalAssistantMetadataUpdater(store),
            recount = { error("already measured") }, metadataDispatcher = StandardTestDispatcher(testScheduler),
            onStatsUpdated = { _, _ -> error("unnecessary update") })
        updater.submit(counted()); advanceUntilIdle(); assertEquals(0, store.writes)
    }
}
