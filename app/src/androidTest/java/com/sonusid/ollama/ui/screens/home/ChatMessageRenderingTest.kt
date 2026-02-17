package com.sonusid.ollama.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

        composeTestRule.onNodeWithTag("userChatBubble").assertExists()
        composeTestRule.onNodeWithTag("assistantPlainMessage").assertDoesNotExist()
    }

    @Test
    fun assistantMessage_showsPlainMessageTag_withoutBubbleTag() {
        composeTestRule.setContent {
            MaterialTheme {
                PlainAssistantMessage(message = "assistant message")
            }
        }

        composeTestRule.onNodeWithTag("assistantPlainMessage").assertExists()
        composeTestRule.onNodeWithTag("userChatBubble").assertDoesNotExist()
    }
}
