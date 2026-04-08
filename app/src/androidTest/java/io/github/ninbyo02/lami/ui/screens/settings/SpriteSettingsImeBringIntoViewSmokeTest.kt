package io.github.ninbyo02.lami.ui.screens.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.github.ninbyo02.lami.MainActivity
import io.github.ninbyo02.lami.navigation.Routes
import io.github.ninbyo02.lami.navigation.SettingsRoute
import io.github.ninbyo02.lami.ui.TestAppWrapper
import io.github.ninbyo02.lami.ui.theme.OllamaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SpriteSettingsImeBringIntoViewSmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = accessSettingsDataStore(context)
        runBlockingIo {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    @Test
    fun focusingInputs_updatesFocusedFieldAndDoesNotCrashBringIntoView() {
        composeTestRule.setSpriteSettingsContentForTest()
        ensureAnimTabSelected()

        scrollToTestTag("spriteBaseFramesInput")
        composeTestRule.onNodeWithTag("spriteBaseFramesInput")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithTag("spriteBaseFramesInput")
            .performTextReplacement("1,2,3")
        composeTestRule.onNodeWithTag("spriteBaseFramesInput")
            .assertTextContains("1,2,3")

        scrollToTestTag("spriteInsertionIntervalInput")
        composeTestRule.onNodeWithTag("spriteInsertionIntervalInput")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithTag("spriteInsertionIntervalInput")
            .performTextReplacement("120")
        composeTestRule.onNodeWithTag("spriteInsertionIntervalInput")
            .assertTextContains("120")

        composeTestRule.waitForIdle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun accessSettingsDataStore(context: Context): DataStore<Preferences> {
        val settingsClass = Class.forName(
            "io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferencesKt"
        )
        val getter = settingsClass.getDeclaredMethod("getDataStore", Context::class.java)
        getter.isAccessible = true
        return getter.invoke(null, context) as DataStore<Preferences>
    }

    private fun runBlockingIo(block: suspend () -> Unit) {
        runBlocking {
            withContext(Dispatchers.IO) {
                block()
            }
        }
    }

    private fun ensureAnimTabSelected() {
        composeTestRule.onNodeWithTag("spriteTabAnim", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
    }

    private fun scrollToTestTag(tag: String) {
        composeTestRule.onNodeWithTag("spriteAnimList", useUnmergedTree = true)
            .performScrollToNode(hasTestTag(tag))
        composeTestRule.waitForIdle()
    }
}
