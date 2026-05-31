package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.InferenceTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatSendAvailabilityTest {
    @Test
    fun `local backend with local model ignores missing server model`() {
        val availability = resolveChatSendAvailability(
            selectedInferenceTarget = InferenceTarget.LOCAL,
            selectedServerModel = null,
            selectedLocalModelPath = "/models/gemma.litertlm",
            serverUrl = "http://localhost:13511",
            hasPromptText = true,
            hasImageInput = false,
            isInferenceRunning = false,
        )

        assertTrue(availability.enabled)
    }

    @Test
    fun `local backend with local model ignores empty server URL`() {
        val availability = resolveChatSendAvailability(
            selectedInferenceTarget = InferenceTarget.LOCAL,
            selectedServerModel = "server-model",
            selectedLocalModelPath = "/models/gemma.litertlm",
            serverUrl = "",
            hasPromptText = true,
            hasImageInput = false,
            isInferenceRunning = false,
        )

        assertTrue(availability.enabled)
    }

    @Test
    fun `local backend with local model ignores server disconnected state`() {
        val availability = resolveChatSendAvailability(
            selectedInferenceTarget = InferenceTarget.LOCAL,
            selectedServerModel = null,
            selectedLocalModelPath = "/models/gemma.litertlm",
            serverUrl = "http://127.0.0.1:1",
            hasPromptText = true,
            hasImageInput = false,
            isInferenceRunning = false,
        )

        assertTrue(availability.enabled)
    }

    @Test
    fun `server backend requires server model`() {
        val availability = resolveChatSendAvailability(
            selectedInferenceTarget = InferenceTarget.SERVER,
            selectedServerModel = null,
            selectedLocalModelPath = "/models/gemma.litertlm",
            serverUrl = "http://localhost:13511",
            hasPromptText = true,
            hasImageInput = false,
            isInferenceRunning = false,
        )

        assertFalse(availability.enabled)
        assertEquals(ChatSendBlockedReason.SERVER_MODEL_MISSING, availability.blockedReason)
    }

    @Test
    fun `server backend with empty server list is unavailable`() {
        val availability = resolveChatSendAvailability(
            selectedInferenceTarget = InferenceTarget.SERVER,
            selectedServerModel = "server-model",
            selectedLocalModelPath = "/models/gemma.litertlm",
            serverUrl = "",
            hasPromptText = true,
            hasImageInput = false,
            isInferenceRunning = false,
        )

        assertFalse(availability.enabled)
        assertEquals(ChatSendBlockedReason.SERVER_MISSING, availability.blockedReason)
        assertEquals("サーバーを追加してください", chatSendBlockedSnackbarMessage(availability.blockedReason))
    }
}
