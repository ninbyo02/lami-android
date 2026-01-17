package com.sonusid.ollama.ui.screens.settings

import androidx.activity.ComponentActivity
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.navigation.SettingsRoute
import com.sonusid.ollama.ui.theme.OllamaTheme
import androidx.test.espresso.Espresso
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SpriteSettingsScreenDiscardDialogTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = accessSettingsDataStore(context)
        runBlocking {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    @Test
    fun back_withoutDirty_doesNotShowDiscardDialog() {
        setSpriteSettingsContent()
        waitForIntervalInput()

        openDiscardDialogByTopBack()
        assertDiscardDialogNotShown()
    }

    @Test
    fun back_dirty_adjust_boxSize_unsaved_showsDiscardDialog() {
        setSpriteSettingsContent()
        waitForIntervalInput()
        switchToAdjustTab()
        makeAdjustDirty()

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

        switchToAdjustTab()

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
        switchToAdjustTab()

        openDiscardDialogBySystemBack()
        waitForSettingsScreen()
        // 保存済みのため Back 後に破棄ダイアログは出ない。
        // 戻り先画面の差異に依存せず、ダイアログ非表示が安定したことを成功条件とする。
        composeTestRule
            .onNodeWithText("編集内容を破棄しますか？")
            .assertDoesNotExist()
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
    fun pressBack_dirty_adjust_boxSize_showsDiscardDialog() {
        setSpriteSettingsContent()
        waitForIntervalInput()
        switchToAdjustTab()
        makeAdjustDirty()

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
                        Text("Settings", modifier = Modifier.testTag("settingsScreenRoot"))
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun waitForIntervalInput() {
        ensureAnimTabSelected()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("spriteBaseIntervalInput")
                .fetchSemanticsNodes().isNotEmpty()
        }
        val intervalNodes = composeTestRule.onAllNodesWithTag("spriteBaseIntervalInput")
            .fetchSemanticsNodes()
        assertTrue("Interval input should be shown", intervalNodes.isNotEmpty())
        val text = intervalNodes.first()
            .config[SemanticsProperties.EditableText]
            .text
        assertTrue("Interval input should be numeric", text.trim().toIntOrNull() != null)
    }

    private fun ensureAnimTabSelected() {
        composeTestRule.onNodeWithTag("spriteTabAnim").performClick()
        composeTestRule.waitForIdle()
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
        waitForDiscardDialogShown()
    }

    private fun assertDiscardDialogNotShown() {
        waitForDiscardDialogNotShown()
        val nodes = composeTestRule.onAllNodesWithText(DISCARD_TITLE, useUnmergedTree = true)
            .fetchSemanticsNodes()
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

    private fun switchToAdjustTab() {
        composeTestRule.onNodeWithTag("spriteTabAdjust").performClick()
        waitForAdjustReady()
    }

    private fun makeAdjustDirty() {
        val tagPriority = listOf(
            "spriteAdjustSizeIncrease",
            "spriteAdjustSizeDecrease",
            "spriteAdjustMoveRight"
        )
        var clicked = false
        for (tag in tagPriority) {
            val nodes = composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
                clicked = true
                break
            }
        }
        assertTrue("Adjust dirty trigger should be available", clicked)
        composeTestRule.waitForIdle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun accessSettingsDataStore(context: Context): DataStore<Preferences> {
        val settingsClass = Class.forName(
            "com.sonusid.ollama.ui.screens.settings.SettingsPreferencesKt"
        )
        val getter = settingsClass.getDeclaredMethod("getDataStore", Context::class.java)
        getter.isAccessible = true
        return getter.invoke(null, context) as DataStore<Preferences>
    }

    private fun waitForDiscardDialogShown(timeoutMillis: Long = 15_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule.onAllNodesWithText(DISCARD_TITLE, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(DISCARD_TITLE, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun waitForDiscardDialogNotShown(timeoutMillis: Long = 15_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule.onAllNodesWithText(DISCARD_TITLE, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForAdjustReady(timeoutMillis: Long = 15_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule.onAllNodesWithTag("spriteAdjustPanel", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("spriteAdjustPanel", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun waitForSettingsScreen(timeoutMillis: Long = 15_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule.onAllNodesWithTag(
                "settingsScreenRoot",
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("settingsScreenRoot", useUnmergedTree = true).assertIsDisplayed()
    }

    private companion object {
        private const val DISCARD_TITLE = "編集内容を破棄しますか？"
    }
}
