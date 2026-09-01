package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.InferenceTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ChatSendAvailabilityTest {
    @Test
    fun `empty chat uses normal copy when selected local route has a model`() {
        assertEquals(
            EmptyChatUiState("ラミィがお手伝いします", "今日は何をしましょうか？", null, false),
            resolveEmptyChatUiState(
                selectedInferenceTarget = InferenceTarget.LOCAL,
                selectedServerModel = null,
                selectedLocalModelPath = "/models/gemma.litertlm",
                serverUrl = null,
            ),
        )
    }

    @Test
    fun `empty chat asks for model and uses offline loop only for unavailable selected local route`() {
        assertEquals(
            EmptyChatUiState(
                "モデルの準備が必要です",
                "設定から使用するモデルを選んでください",
                "モデルを選択",
                true,
            ),
            resolveEmptyChatUiState(
                selectedInferenceTarget = InferenceTarget.LOCAL,
                selectedServerModel = "remote-model-is-irrelevant",
                selectedLocalModelPath = null,
                serverUrl = "http://localhost:11434",
            ),
        )
    }

    @Test
    fun `empty chat asks for server setup and uses offline loop only for unavailable selected server route`() {
        assertEquals(
            EmptyChatUiState(
                "接続先の設定が必要です",
                "使用するAIサーバーを設定してください",
                "接続先を設定",
                true,
            ),
            resolveEmptyChatUiState(
                selectedInferenceTarget = InferenceTarget.SERVER,
                selectedServerModel = null,
                selectedLocalModelPath = "/models/local-is-irrelevant.litertlm",
                serverUrl = "",
            ),
        )
    }

    @Test
    fun `empty chat keeps normal state for configured selected server route`() {
        assertFalse(
            resolveEmptyChatUiState(
                selectedInferenceTarget = InferenceTarget.SERVER,
                selectedServerModel = "llama3",
                selectedLocalModelPath = null,
                serverUrl = "http://localhost:11434",
            ).useOfflineLoop,
        )
    }

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
    fun `TTS text keeps one-character Japanese answers`() {
        assertEquals("赤", sanitizeAssistantTextForTts("赤"))
        assertEquals("青", sanitizeAssistantTextForTts("青"))
    }

    @Test
    fun `TTS text still rejects a lone punctuation mark`() {
        assertEquals("", sanitizeAssistantTextForTts("。"))
    }

    @Test
    fun `successful NPU delivery has exactly one TTS owner for automatic and explicit routes`() {
        val automaticSuccess = resolveNpuStandardRouteTtsOwnership(
            phaseTtsEligible = true,
            legacyTtsEligible = true,
        )
        val explicitNpuSuccess = resolveNpuStandardRouteTtsOwnership(
            phaseTtsEligible = true,
            legacyTtsEligible = false,
        )
        val npuFailureOrFallback = resolveNpuStandardRouteTtsOwnership(
            phaseTtsEligible = false,
            legacyTtsEligible = false,
        )
        val suppressed = resolveNpuStandardRouteTtsOwnership(
            phaseTtsEligible = false,
            legacyTtsEligible = false,
        )

        assertEquals(1, automaticSuccess.ownerCount)
        assertTrue(automaticSuccess.phaseOwner)
        assertFalse(automaticSuccess.legacyOwner)
        assertEquals(1, explicitNpuSuccess.ownerCount)
        assertTrue(explicitNpuSuccess.phaseOwner)
        assertFalse(explicitNpuSuccess.legacyOwner)
        assertEquals(0, npuFailureOrFallback.ownerCount)
        assertEquals(0, suppressed.ownerCount)
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
    fun `transient assistant row remains visible from memory when streaming placeholder exists`() {
        assertTrue(
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

    @Test
    fun `empty persisted chat with pending first user uses new conversation padding on first render`() {
        assertTrue(
            resolveUseNewConversationTopPadding(
                storedModePresent = false,
                storedModeIsNewConversation = false,
                persistedMessagesEmpty = true,
                firstPersistedMessageIsUser = false,
                pendingFirstUserVisible = true,
            ),
        )
    }

    @Test
    fun `pending replacement by persisted first user keeps identical padding mode`() {
        val pendingMode = resolveUseNewConversationTopPadding(
            storedModePresent = false,
            storedModeIsNewConversation = false,
            persistedMessagesEmpty = true,
            firstPersistedMessageIsUser = false,
            pendingFirstUserVisible = true,
        )
        val persistedMode = resolveUseNewConversationTopPadding(
            storedModePresent = false,
            storedModeIsNewConversation = false,
            persistedMessagesEmpty = false,
            firstPersistedMessageIsUser = true,
            pendingFirstUserVisible = false,
        )

        assertEquals(pendingMode, persistedMode)
        assertTrue(persistedMode)
    }

    @Test
    fun `stored existing conversation padding remains existing`() {
        assertFalse(
            resolveUseNewConversationTopPadding(
                storedModePresent = true,
                storedModeIsNewConversation = false,
                persistedMessagesEmpty = false,
                firstPersistedMessageIsUser = true,
                pendingFirstUserVisible = true,
            ),
        )
        assertFalse(
            resolveUseNewConversationTopPadding(
                storedModePresent = false,
                storedModeIsNewConversation = false,
                persistedMessagesEmpty = false,
                firstPersistedMessageIsUser = false,
                pendingFirstUserVisible = false,
            ),
        )
    }

    @Test
    fun `new chat consumes retained runtime model state instead of transient settings initial values`() {
        val viewModelSource = File(
            "src/main/java/io/github/ninbyo02/lami/viewmodels/OllamaViewModel.kt",
        ).readText()
        val chatScreenSource = File(
            "src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()

        assertTrue(
            "The activity-scoped ViewModel must retain model availability across chat routes",
            viewModelSource.contains("val localBaseModelFilePath = settingsPreferences.localBaseModelFilePathFlow.stateIn"),
        )
        assertTrue(
            "New chat must collect retained runtime availability rather than restart with null",
            chatScreenSource.contains("viewModel.localBaseModelFilePath.collectAsState()"),
        )
        assertFalse(
            "A conversation route must not recreate the status-critical model flow with initial = null",
            chatScreenSource.contains(
                "settingsPreferences.localBaseModelFilePathFlow.collectAsState(initial = null)",
            ),
        )
    }
}
