package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SpriteSettingsReadyPerStateOverrideTest {
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
    fun readyInterval_usesPerStateJson_afterRecreate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        runBlocking {
            prefs.saveReadyAnimationSettings(
                ReadyAnimationSettings(
                    frameSequence = listOf(0, 0, 0, 0),
                    intervalMs = 180,
                )
            )
            prefs.saveSpriteAnimationJson(
                SpriteState.READY,
                buildReadyPerStateJson(intervalMs = 90)
            )
        }

        setSpriteSettingsContent()
        assertIntervalInputText(expected = "90")

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        assertIntervalInputText(expected = "90")
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

    private fun assertIntervalInputText(expected: String) {
        ensureAnimTabSelected()
        waitForIntervalInput()
        val text = composeTestRule.onNodeWithTag("spriteBaseIntervalInput")
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
        assertEquals("READY interval input should match per-state JSON", expected, text.trim())
    }

    private fun waitForIntervalInput() {
        waitUntilOrFail(timeoutMs = 10_000) {
            composeTestRule.onAllNodesWithTag("spriteBaseIntervalInput")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("spriteBaseIntervalInput").assertIsDisplayed()
    }

    private fun waitUntilOrFail(timeoutMs: Long, condition: () -> Boolean) {
        val maxFrames = (timeoutMs / 16L).toInt().coerceAtLeast(1)
        repeat(maxFrames) {
            composeTestRule.waitForIdle()
            var conditionMet = false
            composeTestRule.runOnIdle {
                conditionMet = condition()
            }
            if (conditionMet) {
                return
            }
            composeTestRule.mainClock.advanceTimeByFrame()
        }
        fail("条件を満たすまで待機しましたが、${timeoutMs}ms 以内に成立しませんでした。")
    }

    private fun ensureAnimTabSelected() {
        composeTestRule.onNodeWithTag("spriteTabAnim").performClick()
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

    private fun buildReadyPerStateJson(intervalMs: Int): String {
        val baseObject = JSONObject()
            .put("frames", JSONArray(listOf(0, 0, 0, 0)))
            .put("intervalMs", intervalMs)
        val insertionObject = JSONObject()
            .put("enabled", false)
            .put("patterns", JSONArray())
            .put("intervalMs", 110)
            .put("everyNLoops", 1)
            .put("probabilityPercent", 50)
            .put("cooldownLoops", 0)
            .put("exclusive", false)
        return JSONObject()
            .put("animationKey", "Ready")
            .put("base", baseObject)
            .put("insertion", insertionObject)
            .toString()
    }
}
