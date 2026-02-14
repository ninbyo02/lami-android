package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.sonusid.ollama.MainActivity
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.navigation.SettingsRoute
import com.sonusid.ollama.ui.TestAppWrapper
import com.sonusid.ollama.ui.theme.OllamaTheme
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
        setSpriteSettingsContent()
        ensureAnimTabSelected()

        scrollToTestTag("spriteBaseFramesInput")
        composeTestRule.onNodeWithTag("spriteBaseFramesInput")
            .assertIsDisplayed()
            .performClick()
        waitForFocusedField("baseFrames")

        scrollToTestTag("spriteInsertionIntervalInput")
        composeTestRule.onNodeWithTag("spriteInsertionIntervalInput")
            .assertIsDisplayed()
            .performClick()
        waitForFocusedField("insertionInterval")

        composeTestRule.waitForIdle()
    }

    private fun waitForFocusedField(expectedField: String) {
        val expectedText = "focusedField=$expectedField"
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeTestRule.onNodeWithTag("spriteImeDebugFocusedField", useUnmergedTree = true)
                    .assertTextContains(expectedText)
            }.isSuccess
        }
        composeTestRule.onNodeWithTag("spriteImeDebugFocusedField", useUnmergedTree = true)
            .assertTextContains(expectedText)
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

    private fun runBlockingIo(block: suspend () -> Unit) {
        runBlocking {
            withContext(Dispatchers.IO) {
                block()
            }
        }
    }

    private fun setSpriteSettingsContent() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                TestAppWrapper {
                    val navController = rememberNavController()
                    OllamaTheme(dynamicColor = false) {
                        NavHost(
                            navController = navController,
                            startDestination = SettingsRoute.SpriteSettings.route
                        ) {
                            composable(SettingsRoute.SpriteSettings.route) {
                                SpriteSettingsScreen(navController)
                            }
                            composable(Routes.SETTINGS) {
                                Settings(navController)
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
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
