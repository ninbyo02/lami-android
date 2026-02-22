package com.sonusid.ollama.ui.screens.home

// 実行コマンド: ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sonusid.ollama.ui.screens.home.ChatThreadSwitchNoFlashTest

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sonusid.ollama.MainActivity
import com.sonusid.ollama.db.ChatDatabase
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.ui.screens.settings.SettingsPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatThreadSwitchNoFlashTest {

    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val seedRule = object : ExternalResource() {
        override fun before() {
            seedChatsAndMessages()
        }
    }

    @get:Rule
    val ruleChain: TestRule = RuleChain.outerRule(seedRule).around(composeTestRule)

    private val forbiddenTexts = listOf(
        "Preparing chat...",
        "Creating new chat...",
        "最初のメッセージを送信して会話を始めましょう",
    )

    private val chatATitle = "Thread A unique title"
    private val chatBTitle = "Thread B unique title"
    private val messageAUnique = "MSG_A_UNIQUE"
    private val messageBUnique = "MSG_B_UNIQUE"

    private fun seedChatsAndMessages() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = ChatDatabase.getDatabase(context)
        val settingsPreferences = SettingsPreferences(context)

        runBlocking {
            database.clearAllTables()
            settingsPreferences.clearAllPreferencesForTest()

            val chatAId = database.chatDao().insertChat(Chat(title = chatATitle)).toInt()
            val chatBId = database.chatDao().insertChat(Chat(title = chatBTitle)).toInt()

            database.messageDao().insertMessage(
                Message(chatId = chatAId, message = "hello $messageAUnique", isSendbyMe = true)
            )
            database.messageDao().insertMessage(
                Message(chatId = chatBId, message = "hello $messageBUnique", isSendbyMe = true)
            )

            settingsPreferences.saveLastRoute(Routes.chat(chatAId))
        }
    }

    @Test
    fun switchingExistingThreadFromDrawer_neverShowsPreparingOrNewChatPlaceholders() {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(messageAUnique, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription("チャット一覧").performClick()
        composeTestRule.onNodeWithText(chatBTitle).assertExists()

        val maxFrames = 180
        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.onNodeWithText(chatBTitle).performClick()

            var switchedToB = false
            for (frame in 0..maxFrames) {
                assertForbiddenTextsDoNotExist(frame)

                if (isThreadBVisible()) {
                    switchedToB = true
                    break
                }

                composeTestRule.mainClock.advanceTimeByFrame()
                composeTestRule.waitForIdle()
            }

            assertTrue(
                "Thread B への切替が ${maxFrames} フレーム以内に完了しませんでした。",
                switchedToB
            )
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }

        composeTestRule.onNodeWithText(messageBUnique, substring = true).assertExists()
        assertForbiddenTextsDoNotExist(frame = -1)
    }

    private fun isThreadBVisible(): Boolean {
        return composeTestRule.onAllNodesWithText(messageBUnique, substring = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    private fun assertForbiddenTextsDoNotExist(frame: Int) {
        forbiddenTexts.forEach { forbiddenText ->
            val nodes = composeTestRule.onAllNodesWithText(forbiddenText)
                .fetchSemanticsNodes()
            assertTrue(
                "forbidden text was shown at frame=$frame: '$forbiddenText' (count=${nodes.size})",
                nodes.isEmpty()
            )
        }
    }
}
