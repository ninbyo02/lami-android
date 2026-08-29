package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import androidx.room.Room
import io.github.ninbyo02.lami.db.ChatDatabase
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.db.repository.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NpuStandardRouteAutomatedConversationIntegrationTest {
    private val databaseName = "npu-standard-route-automated-conversation.db"
    private lateinit var context: Context
    private var database: ChatDatabase? = null

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun threeTurnsPersistOnceRestoreInOrderAndFeedBoundedNpuHistory() = runTest {
        var repository = openRepository()
        val chatId = repository.newChat(Chat(title = "自動会話テスト"))
        val spoken = mutableListOf<String>()
        val turns = listOf(
            "私の名前は青葉です。名前だけ答えてください。" to "青葉",
            "好きな色は赤です。色だけ答えてください。" to "赤",
            "好きな色を青に訂正します。今の色だけ答えてください。" to "**青**",
        )

        turns.forEach { (prompt, output) ->
            val s1Result = successfulS1(prompt = prompt, output = output)
            val dbMapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(prompt, s1Result)
            assertTrue(shouldPersistNpuStandardRouteS2Db(enabled = true, mapping = dbMapping))
            val saveCandidate = requireNotNull(dbMapping.saveCandidate)
            repository.insert(
                Message(chatId = chatId, message = saveCandidate.userMessage.text, isSendbyMe = true),
            )

            val markdownMapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
                s1Result = s1Result,
                finalizeMarkdown = { it.replace("\\n", "\n") },
            )
            assertTrue(shouldRenderNpuStandardRouteS3Markdown(enabled = true, mapping = markdownMapping))
            val finalText = requireNotNull(markdownMapping.markdownCandidate).finalizedText
            repository.insertAssistantMessageAndAutoTitle(
                Message(chatId = chatId, message = finalText, isSendbyMe = false),
            )

            val ttsMapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
                s1Result = s1Result,
                finalAssistantText = finalText,
                ttsEnabled = true,
                sanitizeForTts = { it.replace("**", "") },
            )
            assertTrue(
                shouldSpeakNpuStandardRouteS5Tts(
                    enabled = true,
                    mapping = ttsMapping,
                    ttsEnabled = true,
                    streamingActive = false,
                    assistantId = 1,
                    suppressedForAssistant = false,
                    inCooldown = false,
                ),
            )
            spoken += requireNotNull(ttsMapping.ttsCandidate).speakText
        }

        val beforeRestart = repository.getMessages(chatId).first()
        assertConversation(beforeRestart)
        assertEquals(listOf("青葉", "赤", "青"), spoken)

        database?.close()
        database = null
        repository = openRepository()
        val restored = repository.getMessages(chatId).first()
        assertConversation(restored)
        assertEquals(beforeRestart.map { it.messageID }, restored.map { it.messageID })

        val history = restored.map { message ->
            LocalConversationTurn(
                role = if (message.isSendbyMe) LocalConversationRole.USER else LocalConversationRole.MODEL,
                text = message.message,
            )
        }
        val request = RealNpuStandardRouteS1Provider.request(
            userPrompt = "今の好きな色は？色だけ答えてください。",
            contextText = LocalConversationHistoryPolicy.npuContext(history),
            maxOutputTokens = 32,
        )
        val finalInput = io.github.ninbyo02.lami.npu.NpuStandardRouteNativeContract.buildPrompt(
            contextText = request.contextText,
            userPrompt = request.userPrompt,
            promptTailVariant = request.promptTailVariant,
        )

        assertTrue(request.contextText.startsWith("ユーザー:"))
        assertTrue(request.contextText.contains("青に訂正"))
        assertFalse(request.contextText.contains("アシスタント:"))
        assertFalse(request.contextText.contains("私の名前は青葉"))
        assertTrue(
            finalInput.codePointCount(0, finalInput.length) <=
                RealNpuStandardRouteS1Provider.NATIVE_MAX_INPUT_CODE_POINTS,
        )
    }

    private fun openRepository(): ChatRepository {
        database = Room.databaseBuilder(context, ChatDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        val opened = requireNotNull(database)
        return ChatRepository(opened.messageDao(), opened.chatDao())
    }

    private fun successfulS1(prompt: String, output: String): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                reason = "success",
                rawOutput = output,
                sanitizedOutput = output,
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
                inputPrompt = prompt,
            ),
        )

    private fun assertConversation(messages: List<Message>) {
        assertEquals(6, messages.size)
        assertEquals(listOf(true, false, true, false, true, false), messages.map { it.isSendbyMe })
        assertEquals(
            listOf(
                "私の名前は青葉です。名前だけ答えてください。",
                "青葉",
                "好きな色は赤です。色だけ答えてください。",
                "赤",
                "好きな色を青に訂正します。今の色だけ答えてください。",
                "**青**",
            ),
            messages.map { it.message },
        )
        assertEquals(messages.map { it.messageID }.sorted(), messages.map { it.messageID })
        assertEquals(messages.size, messages.map { it.messageID }.distinct().size)
    }
}
