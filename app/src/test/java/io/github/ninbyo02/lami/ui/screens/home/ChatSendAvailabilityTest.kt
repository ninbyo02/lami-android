package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.InferenceTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatSendAvailabilityTest {
    @Test
    fun `TTS text removes sparkle and emoji but keeps readable Japanese`() {
        assertEquals(
            "こんにちは！何かお手伝いできることや、お話ししたいことはありますか？お気軽にご質問くださいね。",
            sanitizeAssistantTextForTts(
                "こんにちは！ ✨ 何かお手伝いできることや、お話ししたいことはありますか？お気軽にご質問くださいね。😊"
            )
        )
    }

    @Test
    fun `transient assistant row is hidden after server response is finalized`() {
        assertFalse(
            shouldShowTransientAssistantRow(
                currentChatId = 1,
                isInferenceRunning = false,
                streamingAssistantMessageId = null,
                streamingResponseText = "こんにちは！何かお手伝いできますか？",
                lastPersistedStreamingAssistantText = null,
            )
        )
    }

    @Test
    fun `transient assistant row is shown while server stream has no persisted placeholder`() {
        assertTrue(
            shouldShowTransientAssistantRow(
                currentChatId = 1,
                isInferenceRunning = true,
                streamingAssistantMessageId = null,
                streamingResponseText = "こんにちは！",
                lastPersistedStreamingAssistantText = null,
            )
        )
    }

    @Test
    fun `transient assistant row is hidden when streaming placeholder already exists`() {
        assertFalse(
            shouldShowTransientAssistantRow(
                currentChatId = 1,
                isInferenceRunning = true,
                streamingAssistantMessageId = 42,
                streamingResponseText = "こんにちは！",
                lastPersistedStreamingAssistantText = null,
            )
        )
    }

    @Test
    fun `pending local user message is hidden once persisted user message matches`() {
        assertFalse(
            shouldShowPendingLocalUserMessage(
                currentChatId = 1,
                pendingLocalUserMessageText = "こんにちは",
                latestPersistedUserMessageText = "こんにちは",
            )
        )
    }

    @Test
    fun `stable chat key survives pending row replacement with persisted user row`() {
        val pending = io.github.ninbyo02.lami.db.entity.Message(
            chatId = 7,
            message = "こんにちは",
            isSendbyMe = true,
        )
        val persisted = pending.copy(messageID = 42)

        assertEquals(
            stableChatMessageKey(listOf(pending), 0),
            stableChatMessageKey(listOf(persisted), 0),
        )
    }

    @Test
    fun `stable chat key distinguishes repeated identical user messages`() {
        val messages = listOf(
            io.github.ninbyo02.lami.db.entity.Message(
                messageID = 41,
                chatId = 7,
                message = "こんにちは",
                isSendbyMe = true,
            ),
            io.github.ninbyo02.lami.db.entity.Message(
                messageID = 42,
                chatId = 7,
                message = "こんにちは",
                isSendbyMe = true,
            ),
        )

        assertNotEquals(
            stableChatMessageKey(messages, 0),
            stableChatMessageKey(messages, 1),
        )
    }

    @Test
    fun `first user row wins over assistant tail even when both append together`() {
        val messages = listOf(
            io.github.ninbyo02.lami.db.entity.Message(
                chatId = 7,
                message = "こんにちは",
                isSendbyMe = true,
            ),
            io.github.ninbyo02.lami.db.entity.Message(
                chatId = 7,
                message = "こんにちは！",
                isSendbyMe = false,
            ),
        )

        assertEquals(
            ChatScrollDecision.Item(0),
            resolveChatAppendScrollDecision(
                previousMessages = emptyList(),
                currentMessages = messages,
                isNearBottom = true,
                autoFollowEnabled = true,
            ),
        )
    }

    @Test
    fun `pending replacement with persisted row does not scroll`() {
        val pending = io.github.ninbyo02.lami.db.entity.Message(
            chatId = 7,
            message = "こんにちは",
            isSendbyMe = true,
        )
        val persisted = pending.copy(messageID = 42)

        assertEquals(
            ChatScrollDecision.None,
            resolveChatAppendScrollDecision(
                previousMessages = listOf(pending),
                currentMessages = listOf(persisted),
                isNearBottom = true,
                autoFollowEnabled = true,
            ),
        )
    }

    @Test
    fun `assistant only append follows tail near bottom`() {
        val user = io.github.ninbyo02.lami.db.entity.Message(
            chatId = 7,
            message = "こんにちは",
            isSendbyMe = true,
        )
        val assistant = io.github.ninbyo02.lami.db.entity.Message(
            chatId = 7,
            message = "こんにちは！",
            isSendbyMe = false,
        )

        assertEquals(
            ChatScrollDecision.Item(1),
            resolveChatAppendScrollDecision(
                previousMessages = listOf(user),
                currentMessages = listOf(user, assistant),
                isNearBottom = true,
                autoFollowEnabled = true,
            ),
        )
    }

    @Test
    fun `pending local user message is shown immediately before database insert is observed`() {
        assertTrue(
            shouldShowPendingLocalUserMessage(
                currentChatId = 1,
                pendingLocalUserMessageText = "こんにちは",
                latestPersistedUserMessageText = "前のメッセージ",
            )
        )
    }


    @Test
    fun `local responding placeholder waits for delayed gate like server transient row`() {
        assertFalse(
            shouldShowLocalRespondingPlaceholder(
                isLocalRunning = true,
                localStopRequested = false,
                streamingAssistantMessageId = null,
                localStreamingResponseText = null,
                showDelayedPlaceholder = false,
            )
        )
    }

    @Test
    fun `local responding placeholder stays hidden after delayed gate to match server route`() {
        assertFalse(
            shouldShowLocalRespondingPlaceholder(
                isLocalRunning = true,
                localStopRequested = false,
                streamingAssistantMessageId = null,
                localStreamingResponseText = null,
                showDelayedPlaceholder = true,
            )
        )
    }

    @Test
    fun `local responding placeholder is hidden once streaming text appears`() {
        assertFalse(
            shouldShowLocalRespondingPlaceholder(
                isLocalRunning = true,
                localStopRequested = false,
                streamingAssistantMessageId = null,
                localStreamingResponseText = "回答中",
                showDelayedPlaceholder = true,
            )
        )
    }

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
