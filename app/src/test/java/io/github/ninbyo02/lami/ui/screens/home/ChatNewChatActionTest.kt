package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

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
        assertEquals(true, shouldAutoCreateNewChat(false, null, creating))
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
    fun shouldAutoCreateNewChat_returnsFalse_whenSuppressedEvenIfUnresolved() {
        val shouldCreate = shouldAutoCreateNewChat(
            suppressAutoNewChat = true,
            resolvedChatId = null,
            isCreatingChat = false
        )

        assertEquals(false, shouldCreate)
    }

    @Test
    fun shouldAutoCreateNewChat_returnsTrue_whenNotSuppressedAndUnresolved() {
        val shouldCreate = shouldAutoCreateNewChat(
            suppressAutoNewChat = false,
            resolvedChatId = null,
            isCreatingChat = false
        )

        assertEquals(true, shouldCreate)
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
