package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import io.github.ninbyo02.lami.db.entity.Message

class ChatNewChatActionTest {
    @Test
    fun cancelledCreationClearsProgressAndAllowsRetry() = runTest {
        var creating = false
        var resolved: Int? = null
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = launch {
            createChatWithProgress(
                setCreating = { creating = it },
                createChat = {
                    entered.complete(Unit)
                    kotlinx.coroutines.awaitCancellation()
                },
                onCreated = { resolved = it },
            )
        }
        entered.await()
        assertEquals(true, creating)
        job.cancel()
        job.join()
        assertEquals(false, creating)
        assertEquals(null, resolved)
        createChatWithProgress({ creating = it }, { 42 }, { resolved = it })
        assertEquals(42, resolved)
        assertEquals(false, creating)
    }

    @Test
    fun failedCreationClearsProgressWithoutResolvingChat() = runTest {
        var creating = false
        var resolved: Int? = null
        val failure = IllegalStateException("insert failed")
        val result = runCatching {
            createChatWithProgress({ creating = it }, { throw failure }, { resolved = it })
        }
        assertEquals(failure, result.exceptionOrNull())
        assertEquals(false, creating)
        assertEquals(null, resolved)
    }


    @Test
    fun draftChatRendersImmediatelyWithoutStoredMessages() {
        assertEquals(true, shouldRenderChatMessages(null, emptyList()))
        assertEquals(false, shouldRenderChatMessages(null, listOf(Message(chatId = 42, message = "old", isSendbyMe = false))))
        assertEquals(false, shouldRenderChatMessages(43, listOf(Message(chatId = 42, message = "old", isSendbyMe = false))))
    }

    @Test
    fun firstSendCreatesAChatAndFurtherSendsReuseIt() = runTest {
        var createdCount = 0
        var chatId: Int? = null
        val create: suspend () -> Int = { createdCount++; 101 }
        chatId = resolveFirstSendChatId(chatId, create) { chatId = it }
        assertEquals(101, chatId)
        assertEquals(101, resolveFirstSendChatId(chatId, create) { error("existing chat must not be replaced") })
        assertEquals(1, createdCount)
    }

    @Test
    fun failedCreationDoesNotResolveAnInvalidChat() = runTest {
        var resolved = false
        val failure = runCatching {
            resolveFirstSendChatId(null, { error("storage failure") }) { resolved = true }
        }
        assertEquals(true, failure.isFailure)
        assertEquals(false, resolved)
    }

    @Test
    fun existingChatDisplaysOnlyItsOwnMessages() {
        assertEquals(true, shouldRenderChatMessages(42,
            listOf(Message(chatId = 42, message = "current", isSendbyMe = false)))
        )
    }

    @Test
    fun closeThenNavigate_executesCloseBeforeNavigate() = runTest {
        val events = mutableListOf<String>()

        closeThenNavigate(
            closeDrawer = {
                events += "close"
            },
            navigate = {
                events += "navigate"
            }
        )

        assertEquals(listOf("close", "navigate"), events)
    }

    @Test
    fun closeThenNavigate_keepsNavigation_whenCloseFails() = runTest {
        val events = mutableListOf<String>()

        closeThenNavigate(
            closeDrawer = {
                events += "close"
                error("close failed")
            },
            navigate = {
                events += "navigate"
            }
        )

        assertEquals(listOf("close", "navigate"), events)
    }

    @Test
    fun createAndNavigateToNewChat_executesInOrder_andClosesDrawer() = runTest {
        val events = mutableListOf<String>()
        var resolvedChatId: Int? = null

        createAndNavigateToNewChat(
            createNewChat = {
                events += "create"
                101
            },
            onChatResolved = { chatId ->
                events += "resolved:$chatId"
                resolvedChatId = chatId
            },
            navigateToChat = { chatId ->
                events += "navigate:$chatId"
            },
            closeDrawer = {
                events += "close"
            }
        )

        assertEquals(101, resolvedChatId)
        assertEquals(
            listOf("create", "resolved:101", "close", "navigate:101"),
            events
        )
    }

    @Test
    fun createAndNavigateToNewChat_keepsNavigation_whenCloseDrawerFails() = runTest {
        val events = mutableListOf<String>()

        createAndNavigateToNewChat(
            createNewChat = {
                events += "create"
                202
            },
            onChatResolved = { chatId ->
                events += "resolved:$chatId"
            },
            navigateToChat = { chatId ->
                events += "navigate:$chatId"
            },
            closeDrawer = {
                events += "close"
                error("drawer close failed")
            }
        )

        assertEquals(
            listOf("create", "resolved:202", "close", "navigate:202"),
            events
        )
    }
}
