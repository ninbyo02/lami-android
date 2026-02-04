package com.sonusid.ollama

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sonusid.ollama.ui.screens.spriteeditor.SpriteEditorScreen
import com.sonusid.ollama.ui.theme.OllamaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteEditorModeApplyDialogTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setEditorContent() {
        composeTestRule.setContent {
            val navController = rememberNavController()
            OllamaTheme {
                SpriteEditorScreen(navController)
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("spriteEditorModeLabel")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun modeSelector_updatesModeLabel() {
        setEditorContent()

        composeTestRule.onNodeWithTag("spriteEditorMode32").performClick()
        composeTestRule.onNodeWithTag("spriteEditorModeLabel")
            .assertTextEquals("Mode: 32x32")
    }

    @Test
    fun applyDialog_showsModeAndRect_andCancelCloses() {
        setEditorContent()

        composeTestRule.onNodeWithTag("spriteEditorApply").performClick()
        composeTestRule.onNodeWithTag("spriteEditorApplyModeText")
            .assertTextEquals("Mode: 96x96")
        composeTestRule.onNodeWithTag("spriteEditorApplyRectDialogText")
            .assertTextEquals("Apply rect: (0, 0, 96, 96)")

        composeTestRule.onNodeWithTag("spriteEditorApplyCancel").performClick()
        composeTestRule.onNodeWithTag("spriteEditorApplyDialog").assertDoesNotExist()
    }
}
