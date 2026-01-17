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
import androidx.test.espresso.Espresso
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SpriteSettingsScreenDiscardDialogTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun back_withoutDirty_doesNotShowDiscardDialog() {
        setSpriteSettingsContent()
        waitForIntervalInput()

        openDiscardDialogByTopBack()
        assertDiscardDialogNotShown()
    }

    @Test
    fun back_dirty_baseInterval_unsaved_showsDiscardDialog() {
        setSpriteSettingsContent()
        waitForIntervalInput()
        makeAnimDirtyByChangingInterval()

        openDiscardDialogByTopBack()
        assertDiscardDialogShown()
    }

    @Test
    fun back_dirty_baseInterval_saved_doesNotShowDiscardDialog() {
        setSpriteSettingsContent()
        waitForIntervalInput()
        makeAnimDirtyByChangingInterval()

        composeTestRule.onNodeWithContentDescription("保存").performClick()
        composeTestRule.waitForIdle()
        waitForIntervalInput()

        openDiscardDialogByTopBack()
        assertDiscardDialogNotShown()
    }

    @Test
    fun back_dirty_baseInterval_switchToAdjust_unsaved_showsDiscardDialog() {
        setSpriteSettingsContent()
        waitForIntervalInput()
        makeAnimDirtyByChangingInterval()

        navigateToAdjustTab()

        openDiscardDialogByTopBack()
        assertDiscardDialogShown()
    }

    @Test
    fun back_dirty_baseInterval_switchToAdjust_saved_doesNotShowDiscardDialog() {
        setSpriteSettingsContent()
        waitForIntervalInput()
        makeAnimDirtyByChangingInterval()

        composeTestRule.onNodeWithContentDescription("保存").performClick()
        composeTestRule.waitForIdle()
        navigateToAdjustTab()

        openDiscardDialogByTopBack()
        assertDiscardDialogNotShown()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun pressBack_imeFocused_withoutDirty_doesNotShowDiscardDialog() {
        setSpriteSettingsContent()
        waitForIntervalInput()

        focusIntervalInput()
        openDiscardDialogBySystemBack()
        assertDiscardDialogNotShown()
    }

    @Test
    fun pressBack_imeFocused_dirty_baseInterval_showsDiscardDialog() {
        setSpriteSettingsContent()
        waitForIntervalInput()
        makeAnimDirtyByChangingInterval()

        openDiscardDialogBySystemBack()
        assertDiscardDialogShown()
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

    private fun waitForIntervalInput() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            val nodes = composeTestRule.onAllNodesWithText(DISCARD_TITLE).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                return@waitUntil false
            }
            val intervalNodes = composeTestRule.onAllNodesWithTag("spriteBaseIntervalInput")
                .fetchSemanticsNodes()
            if (intervalNodes.isEmpty()) {
                return@waitUntil false
            }
            val text = intervalNodes.first()
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

    private fun assertDiscardDialogShown() {
        composeTestRule.onNodeWithText(DISCARD_TITLE).assertIsDisplayed()
    }

    private fun assertDiscardDialogNotShown() {
        val nodes = composeTestRule.onAllNodesWithText(DISCARD_TITLE).fetchSemanticsNodes()
        assertTrue("Discard dialog should not be shown", nodes.isEmpty())
    }

    private fun openDiscardDialogByTopBack() {
        composeTestRule.onNodeWithContentDescription("戻る").performClick()
        composeTestRule.waitForIdle()
    }

    private fun openDiscardDialogBySystemBack() {
        Espresso.pressBack()
        composeTestRule.waitForIdle()
    }

    private fun focusIntervalInput() {
        composeTestRule.onNodeWithTag("spriteBaseIntervalInput").performClick()
        composeTestRule.waitForIdle()
    }

    private fun makeAnimDirtyByChangingInterval(delta: Int = 1) {
        updateBaseIntervalBy(delta)
    }

    private fun navigateToAdjustTab() {
        composeTestRule.onNodeWithTag("spriteTabAdjust").performClick()
        composeTestRule.waitForIdle()
    }

    private companion object {
        private const val DISCARD_TITLE = "編集内容を破棄しますか？"
    }
}
