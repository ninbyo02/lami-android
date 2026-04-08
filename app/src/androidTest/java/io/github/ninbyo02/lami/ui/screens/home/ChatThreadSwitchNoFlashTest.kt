package io.github.ninbyo02.lami.ui.screens.home

// 実行コマンド: ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.ninbyo02.lami.ui.screens.home.ChatThreadSwitchNoFlashTest

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ninbyo02.lami.MainActivity
import io.github.ninbyo02.lami.db.ChatDatabase
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.Message
import io.github.ninbyo02.lami.navigation.Routes
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import io.github.ninbyo02.lami.util.RuntimeFlags
import android.os.SystemClock
import kotlinx.coroutines.runBlocking
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
            RuntimeFlags.isUiTest = true
            seedChatsAndMessages()
        }

        override fun after() {
            RuntimeFlags.isUiTest = false
        }
    }

    @get:Rule
    val ruleChain: TestRule = RuleChain.outerRule(seedRule).around(composeTestRule)

    private val placeholders = listOf(
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
        composeTestRule.waitUntilAtLeastOneExistsByText(messageAUnique, timeoutMs = 30_000, substring = true)

        composeTestRule.onNodeWithContentDescription("チャット一覧").performClick()

        // 既存のチャットBへ切り替え
        composeTestRule.onNodeWithText(chatBTitle).performClick()

        // まず「遷移完了」を明示的に待つ（teardown競合で lifecycle crash を避ける）
        composeTestRule.waitUntilAtLeastOneExistsByText(chatBTitle, timeoutMs = 30_000, substring = false)
        composeTestRule.waitUntilAtLeastOneExistsByText(messageBUnique, timeoutMs = 30_000, substring = true)

        // 遷移完了後、短い監視窓で placeholder が出ていないことを保証
        composeTestRule.assertNeverShownWithinWindow(placeholders, windowMs = 1_500L, stepMs = 50L)

    }

    private fun ComposeTestRule.waitUntilAtLeastOneExistsByText(
        text: String,
        timeoutMs: Long,
        substring: Boolean,
    ) {
        waitUntil(timeoutMs) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeTestRule.assertNeverShownWithinWindow(
        texts: List<String>,
        windowMs: Long,
        stepMs: Long,
    ) {
        val startAt = SystemClock.uptimeMillis()
        val deadline = startAt + windowMs

        while (SystemClock.uptimeMillis() <= deadline) {
            texts.forEach { text ->
                val found = onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty()
                if (found) {
                    val elapsedMs = SystemClock.uptimeMillis() - startAt
                    throw AssertionError("placeholder was shown: text=$text elapsedMs=$elapsedMs windowMs=$windowMs")
                }
            }
            Thread.sleep(stepMs)
        }
    }
}
