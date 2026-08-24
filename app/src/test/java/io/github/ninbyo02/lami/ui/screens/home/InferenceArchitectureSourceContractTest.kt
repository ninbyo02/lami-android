package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceArchitectureSourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `ChatScreen delegates lifecycle and stats decisions to extracted owners`() {
        val chat = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()

        assertTrue(chat.contains("AssistantMessageLifecycle.planPlaceholder("))
        assertTrue(chat.contains("AssistantMessageLifecycle.planCompletion("))
        assertTrue(chat.contains("AssistantMessageLifecycle.planFailure("))
        assertTrue(chat.contains("InferenceStatsFactory.fromLocalTrace("))
        assertTrue(chat.contains("InferenceStatsFactory.fromNpuStandardRoute("))
        assertFalse(chat.contains("private fun buildLocalInferenceStatsFromTrace("))
        assertFalse(chat.contains("private fun buildNpuStandardRouteInferenceStats("))
        assertFalse(chat.contains("internal fun createAssistantMessage("))
    }

    @Test
    fun `terminal assistant rows have no overwrite payload in lifecycle plans`() {
        val lifecycle = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/AssistantMessageLifecycle.kt",
        ).readText()

        assertTrue(lifecycle.contains("existingMessage.status !in MessageStatus.IN_FLIGHT"))
        assertTrue(lifecycle.contains("AssistantMessageLifecycleAction.KEEP_EXISTING"))
        assertTrue(lifecycle.contains("mergePayloadIntoTerminalMessage"))
    }
}
