package io.github.ninbyo02.lami.skills.web

import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.skills.AgentSkillDefinition
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class WebSearchManagerTest {
    private class Preferences(var value: Boolean = false) : WebSearchPreferenceStore {
        override fun enabled() = value
        override fun saveEnabled(enabled: Boolean) { value = enabled }
    }
    private val definition = AgentSkillDefinition("web-search", "Web search", "Native adapter instructions")
    private fun client(block: suspend (String) -> WebSearchResponse) = object : WebSearchClient {
        override suspend fun search(query: String) = block(query)
    }
    private fun response(query: String) = WebSearchResponse(query, listOf(WebSearchHit("Title", "https://example.com/", "Snippet")))

    @Test fun defaultOffDoesNotLoadSkillOrSendAnythingAndReturnsLocalSettingReply() = runBlocking {
        var activation = 0; var calls = 0
        val manager = WebSearchManager(Preferences(), client { calls++; response(it) }, { activation++; definition }, { error("must not read") })
        assertFalse(manager.enabled.value)
        val result = manager.search("LLM")
        assertTrue(result.reply.contains("OFF"))
        assertEquals(0, activation); assertEquals(0, calls)
        var commits = 0
        assertTrue(manager.commitIfCurrent(result) { commits++ })
        assertEquals(1, commits)
    }

    @Test fun enablingPersistsButDoesNotSearchUntilExplicitDispatch() = runBlocking {
        val preferences = Preferences()
        var calls = 0
        val manager = WebSearchManager(preferences, client { calls++; response(it) }, { definition }, { "reference" })
        manager.setEnabled(true)
        assertTrue(preferences.value); assertTrue(manager.enabled.value); assertEquals(0, calls)
        manager.setEnabled(false)
        assertFalse(preferences.value); assertFalse(manager.enabled.value)
    }

    @Test fun enabledDispatchActivatesBothResourcesAndSendsOnlyQueryOnce() = runBlocking {
        val events = mutableListOf<String>()
        val manager = WebSearchManager(Preferences(true), client { events += "query=$it"; response(it) },
            { events += "skill"; definition }, { events += "reference"; "android instructions" })
        val result = manager.search("LLM 最新情報")
        assertEquals(listOf("skill", "reference", "query=LLM 最新情報"), events)
        assertEquals(definition, result.skill)
        assertTrue(result.reply.contains("出典:"))
    }

    @Test fun missingMalformedOrMismatchedSkillAndMissingReferenceFailBeforeNetwork() = runBlocking {
        var calls = 0
        val loaders = listOf<() -> AgentSkillDefinition>({ throw IOException("private/path") },
            { definition.copy(name = "other") }, { definition.copy(body = "") })
        for (load in loaders) {
            val manager = WebSearchManager(Preferences(true), client { calls++; response(it) }, load, { "reference" })
            assertTrue(manager.search("LLM").reply.contains("実行していません"))
        }
        val manager = WebSearchManager(Preferences(true), client { calls++; response(it) }, { definition }, { "" })
        assertTrue(manager.search("LLM").reply.contains("実行していません"))
        assertEquals(0, calls)
    }

    @Test fun offCancelsActiveSearchAndPreventsLateResultEvenIfClientIgnoresCancellation() = runBlocking {
        withTimeout(5000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var completed = false
            val manager = WebSearchManager(Preferences(true), client { query ->
                started.complete(Unit)
                withContext(NonCancellable) { release.await() }
                response(query)
            }, { definition }, { "reference" })
            val job = launch { manager.search("private query"); completed = true }
            started.await()
            manager.setEnabled(false)
            release.complete(Unit)
            job.join()
            assertTrue(job.isCancelled)
            assertFalse(completed)
            assertFalse(manager.enabled.value)
        }
    }

    @Test fun offAndReenableCannotCommitAnAlreadyReturnedOldResult() = runBlocking {
        val manager = WebSearchManager(Preferences(true), client { response(it) }, { definition }, { "reference" })
        val stale = manager.search("LLM")
        manager.setEnabled(false)
        manager.setEnabled(true)
        var committed = false
        assertFalse(manager.commitIfCurrent(stale) { committed = true })
        assertFalse(committed)
    }

    @Test fun invalidQueryAndTransportExceptionsNeverRetryOrExposeExceptionDetails() = runBlocking {
        var calls = 0
        val manager = WebSearchManager(Preferences(true), client { calls++; throw IOException("private-query-and-token") }, { definition }, { "reference" })
        assertEquals(WebSearchIntentParser.REJECTED_REPLY, manager.search("bad\nquery").reply)
        assertEquals(0, calls)
        val result = manager.search("LLM")
        assertFalse(result.reply.contains("private-query"))
        assertTrue(result.reply.contains("自動切替は行っていません"))
        assertEquals(1, calls)
    }

    private open class Store : WebSearchChatStore {
        val messages = mutableListOf<Message>()
        val replies = mutableListOf<String>()
        var cancelled = false
        override suspend fun insertUser(message: Message) { messages += message }
        override suspend fun insertAssistant(message: Message): Long { messages += message; return 1 }
        override suspend fun complete(id: Int, reply: String) { replies += reply }
        override suspend fun cancel(id: Int) { cancelled = true }
        override suspend fun fail(id: Int) { error("unexpected failure") }
    }

    @Test fun bridgeTagsBothTurnsAndOffOrRejectedRequestsNeverInvokeTransport() = runBlocking {
        var calls = 0
        val manager = WebSearchManager(Preferences(), client { calls++; response(it) }, { error("disabled") }, { error("disabled") })
        val store = Store()
        runWebSearchChatTurn(manager, store, 1, "/web LLM", WebSearchIntent.Search("LLM"))
        runWebSearchChatTurn(manager, store, 1, "「/web LLM」", WebSearchIntent.Rejected)
        assertEquals(0, calls)
        assertEquals(4, store.messages.size)
        assertTrue(store.messages.all(WebSearchMessagePolicy::isSearchTurn))
        assertTrue(store.replies[0].contains("OFF"))
        assertEquals(WebSearchIntentParser.REJECTED_REPLY, store.replies[1])
    }

    @Test fun bridgeCancellationTerminatesPlaceholderWithoutWritingSearchData() = runBlocking {
        withTimeout(5000) {
            val started = CompletableDeferred<Unit>()
            val manager = WebSearchManager(Preferences(true), client {
                started.complete(Unit); awaitCancellation()
            }, { definition }, { "reference" })
            val store = Store()
            val job = launch { runWebSearchChatTurn(manager, store, 1, "/web LLM", WebSearchIntent.Search("LLM")) }
            started.await()
            manager.setEnabled(false)
            job.join()
            assertTrue(store.cancelled)
            assertTrue(store.replies.isEmpty())
        }
    }

    @Test fun cancellationAfterAssistantInsertButBeforeIdReturnStillTerminatesThatPlaceholder() = runBlocking {
        withTimeout(5000) {
            val inserted = CompletableDeferred<Unit>()
            val finishTitleWork = CompletableDeferred<Unit>()
            var calls = 0
            val manager = WebSearchManager(Preferences(true), client { calls++; response(it) }, { definition }, { "reference" })
            val store = object : Store() {
                override suspend fun insertAssistant(message: Message): Long {
                    val id = super.insertAssistant(message)
                    inserted.complete(Unit)
                    finishTitleWork.await()
                    return id
                }
            }
            val job = launch { runWebSearchChatTurn(manager, store, 1, "/web LLM", WebSearchIntent.Search("LLM")) }
            try {
                inserted.await()
                job.cancel()
            } finally {
                finishTitleWork.complete(Unit)
            }
            job.join()
            assertTrue(job.isCancelled)
            assertTrue(store.cancelled)
            assertEquals(2, store.messages.size)
            assertTrue(store.replies.isEmpty())
            assertEquals(0, calls)
        }
    }
}
