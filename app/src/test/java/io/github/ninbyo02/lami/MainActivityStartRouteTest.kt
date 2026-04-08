package io.github.ninbyo02.lami

import io.github.ninbyo02.lami.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityStartRouteTest {
    @Test
    fun resolveStartRoute_returnsChatRoot_whenRestoredIsNullOrInvalid() {
        val allowed = setOf(
            Routes.HOME,
            Routes.CHATS,
            Routes.CHAT_ROOT,
            Routes.SETTINGS,
            Routes.ABOUT,
            Routes.NOTICE
        )

        assertEquals(Routes.CHAT_ROOT, resolveStartRoute(restored = null, allowed = allowed))
        assertEquals(Routes.CHAT_ROOT, resolveStartRoute(restored = "unknown", allowed = allowed))
    }
}
