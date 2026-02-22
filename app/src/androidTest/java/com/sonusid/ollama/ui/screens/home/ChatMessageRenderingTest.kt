package com.sonusid.ollama.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import org.junit.Rule
import org.junit.Test

class ChatMessageRenderingTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun userMessage_showsBubbleTag() {
        composeTestRule.setContent {
            MaterialTheme {
                ChatBubble(
                    message = "user message",
                    isSentByMe = true,
                )
            }
        }

        assert(composeTestRule.onAllNodesWithTag("userChatBubble").fetchSemanticsNodes().isNotEmpty())
        assert(composeTestRule.onAllNodesWithTag("assistantPlainMessage").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun assistantMessage_showsPlainMessageTag_withoutBubbleTag() {
        composeTestRule.setContent {
            MaterialTheme {
                PlainAssistantMessage(message = "assistant message")
            }
        }

        assert(composeTestRule.onAllNodesWithTag("assistantPlainMessage").fetchSemanticsNodes().isNotEmpty())
        assert(composeTestRule.onAllNodesWithTag("userChatBubble").fetchSemanticsNodes().isEmpty())
    }
}
