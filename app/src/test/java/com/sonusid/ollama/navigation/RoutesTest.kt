package io.github.ninbyo02.lami.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun chat_withId_route_is_compatible() {
        assertEquals("chat/123", Routes.chat(123))
    }

    @Test
    fun chat_new_route_is_chat_root() {
        assertEquals("chat", Routes.chatNew())
    }

    @Test
    fun chat_with_id_route_pattern_is_defined() {
        assertEquals("chat/{chatId}", Routes.CHAT_WITH_ID_ROUTE)
    }
}
