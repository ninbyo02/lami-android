package com.sonusid.ollama.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.navigation.SettingsRoute
import com.sonusid.ollama.ui.theme.OllamaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpriteSettingsScreenDiscardDialogTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backWithoutChanges_doesNotShowDiscardDialog() {
        setSpriteSettingsContent()
        waitForStableInputs()

        composeTestRule.onNodeWithContentDescription("戻る").performClick()
        composeTestRule.waitForIdle()

        val count = composeTestRule
            .onAllNodesWithText("編集内容を破棄しますか？")
            .fetchSemanticsNodes()
            .size

        assertEquals(0, count)
    }

    @Test
    fun systemBackWithUnsavedChanges_showsDiscardDialog() {
        setSpriteSettingsContent()
        waitForStableInputs()
        updateBaseIntervalBy(1)

        composeTestRule.onNodeWithContentDescription("戻る").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("編集内容を破棄しますか？").assertIsDisplayed()
    }

    @Test
    fun backAfterSavingChanges_doesNotShowDiscardDialog() {
        setSpriteSettingsContent()
        waitForStableInputs()
        updateBaseIntervalBy(1)

        composeTestRule.onNodeWithContentDescription("保存").performClick()
        composeTestRule.waitForIdle()
        waitForStableInputs()
        composeTestRule.onNodeWithContentDescription("戻る").performClick()
        composeTestRule.waitForIdle()

        val count = composeTestRule
            .onAllNodesWithText("編集内容を破棄しますか？")
            .fetchSemanticsNodes()
            .size

        assertEquals(0, count)
    }

    private fun setSpriteSettingsContent() {
        composeTestRule.setContent {
            OllamaTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = SettingsRoute.SpriteSettings.route
                ) {
                    composable(SettingsRoute.SpriteSettings.route) {
                        SpriteSettingsScreen(navController)
                    }
                    composable(Routes.SETTINGS) {
                        Text("Settings")
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun waitForStableInputs() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            val nodes = composeTestRule.onAllNodesWithTag("spriteBaseIntervalInput")
                .fetchSemanticsNodes()
            if (nodes.isEmpty()) {
                return@waitUntil false
            }
            val text = nodes.first()
                .config[SemanticsProperties.EditableText]
                .text
            text.trim().toIntOrNull() != null
        }
    }

    private fun updateBaseIntervalBy(delta: Int) {
        val intervalNode = composeTestRule.onNodeWithTag("spriteBaseIntervalInput")
        val currentText = intervalNode.fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
        val currentValue = currentText.toIntOrNull() ?: 0
        val nextValue = (currentValue + delta).toString()
        intervalNode.performClick()
        intervalNode.performTextClearance()
        intervalNode.performTextInput(nextValue)
        composeTestRule.waitForIdle()
    }
}
