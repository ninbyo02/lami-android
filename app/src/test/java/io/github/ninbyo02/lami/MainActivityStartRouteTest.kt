package io.github.ninbyo02.lami

import android.content.Intent
import io.github.ninbyo02.lami.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityStartRouteTest {
    @Test
    fun resolveStartRoute_returnsChatRoot_whenRestoredIsNullOrInvalid() {
        val allowed = setOf(
            Routes.HOME,
            "chats",
            Routes.CHAT_ROOT,
            Routes.SETTINGS,
            Routes.ABOUT,
            Routes.NOTICE
        )

        assertEquals(Routes.CHAT_ROOT, resolveStartRoute(restored = null, allowed = allowed))
        assertEquals(Routes.CHAT_ROOT, resolveStartRoute(restored = "unknown", allowed = allowed))
    }

    @Test
    fun assistantLaunchLeavesSettingsAndOtherNonChatScreens() {
        assertFalse(isAssistantChatRoute(Routes.SETTINGS))
        assertFalse(isAssistantChatRoute("chats"))
        assertFalse(isAssistantChatRoute(null))
    }

    @Test
    fun assistantLaunchKeepsAnExistingConversation() {
        assertTrue(isAssistantChatRoute(Routes.CHAT_ROOT))
        assertTrue(isAssistantChatRoute(Routes.HOME))
        assertTrue(isAssistantChatRoute("${Routes.CHAT}/42"))
    }

    @Test
    fun normalStartupNeverRestoresSettingsOrHistory() {
        val allowed = setOf("chats", Routes.SETTINGS, Routes.ABOUT, Routes.CHAT_ROOT)
        for (restored in listOf(Routes.SETTINGS, "chats", Routes.ABOUT)) {
            assertEquals(Routes.CHAT_ROOT, resolveStartRoute(restored, allowed))
        }
        assertEquals("chat/42", resolveStartRoute("chat/42", allowed))
    }

    @Test
    fun launcherReentryRequestsChatWithoutTreatingOtherIntentsAsLauncher() {
        assertTrue(isLauncherChatEntry(Intent.ACTION_MAIN, setOf(Intent.CATEGORY_LAUNCHER)))
        assertFalse(isLauncherChatEntry(Intent.ACTION_MAIN, emptySet()))
        assertFalse(isLauncherChatEntry(Intent.ACTION_ASSIST, setOf(Intent.CATEGORY_LAUNCHER)))
    }
}
