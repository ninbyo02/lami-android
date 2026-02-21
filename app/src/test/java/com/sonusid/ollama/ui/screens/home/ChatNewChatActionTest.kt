package com.sonusid.ollama.ui.screens.home

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatNewChatActionTest {

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
            listOf("create", "resolved:101", "navigate:101", "close"),
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
            listOf("create", "resolved:202", "navigate:202", "close"),
            events
        )
    }
}
