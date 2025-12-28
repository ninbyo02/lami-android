package com.sonusid.ollama

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sonusid.ollama.ui.screens.debug.SpriteBoxCountKey
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpriteDebugNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun openCanvasFromSettings_andVerifySpriteSheetOverlay() {
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("settings")
            .assertIsDisplayed()
            .performClick()

        composeTestRule
            .onNode(
                hasText("Sprite Debug") and hasContentDescription("Sprite Debug Tools") and hasClickAction()
            )
            .assertIsDisplayed()
            .performClick()

        composeTestRule
            .onNodeWithText("キャンバスビューを開く")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithContentDescription("Sprite sheet").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("調整").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Sprite sheet").assertIsDisplayed()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasContentDescription("Sprite sheet overlay")).fetchSemanticsNodes().isNotEmpty()
        }

        val overlayNode = composeTestRule.onNode(hasContentDescription("Sprite sheet overlay"))
        overlayNode.assertIsDisplayed()
        overlayNode.assert(SemanticsMatcher.expectValue(SpriteBoxCountKey, overlayNode.fetchSemanticsNode().config[SpriteBoxCountKey]))

        val spriteBoxCount = overlayNode.fetchSemanticsNode().config[SpriteBoxCountKey]
        assertTrue(spriteBoxCount > 0, "Sprite overlay should render at least one bounding box")
    }
}
