package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.sonusid.ollama.MainActivity
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.navigation.SettingsRoute
import com.sonusid.ollama.ui.screens.settings.Settings
import com.sonusid.ollama.ui.screens.settings.SpriteSettingsScreen
import com.sonusid.ollama.ui.theme.OllamaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.first

class SpriteSettingsTalkShortPerStateRestoreTest {
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
    fun talkShortInterval_usesPerStateJson_afterRecreate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)
        val expectedSnapshot = PerStateAnimationSnapshot(
            animationKey = "TalkShort",
            intervalMs = 123,
            frames = listOf(0, 1, 2),
        )
        runBlockingIo {
            prefs.saveSpriteAnimationJson(
                SpriteState.TALK_SHORT,
                buildTalkShortPerStateJson(intervalMs = 123, frames = expectedSnapshot.frames)
            )
            prefs.saveLastRoute(SettingsRoute.SpriteSettings.route)
        }

        recreateToSpriteSettings()
        ensureAnimTabSelected()
        selectAnimationType("TalkShort")
        composeTestRule.waitUntilLastSelectedAnimationType(context, "TalkShort")
        composeTestRule.waitUntilSelectedKeyPersisted(context, SpriteState.TALK_SHORT, "TalkShort")
        composeTestRule.waitUntilPerStateAnimationSnapshot(context, SpriteState.TALK_SHORT, expectedSnapshot)

        recreateToSpriteSettings()
        ensureAnimTabSelected()
        selectAnimationType("TalkShort")
        composeTestRule.waitUntilLastSelectedAnimationType(context, "TalkShort")
        composeTestRule.waitUntilSelectedKeyPersisted(context, SpriteState.TALK_SHORT, "TalkShort")
        composeTestRule.waitUntilPerStateAnimationSnapshot(context, SpriteState.TALK_SHORT, expectedSnapshot)
    }

    @Test
    fun talkShortDefaults_usedWhenDataStoreMissing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SettingsPreferences(context)

        runBlockingIo {
            prefs.ensurePerStateAnimationJsonsInitialized()
        }

        val perStateJson = runBlockingIo {
            prefs.spriteAnimationJsonFlow(SpriteState.TALK_SHORT).first()
        }
        assertNotNull("TalkShort の per-state JSON が未生成です", perStateJson)
        val root = JSONObject(perStateJson!!)
        val baseObject = root.getJSONObject("base")
        val baseFramesArray = baseObject.getJSONArray("frames")
        val baseFrames = buildList {
            for (index in 0 until baseFramesArray.length()) {
                add(baseFramesArray.getInt(index))
            }
        }
        assertEquals(listOf(0, 6, 2, 6, 0, 0), baseFrames)
        assertEquals(125, baseObject.getInt("intervalMs"))

        val insertionObject = root.getJSONObject("insertion")
        val patternsArray = insertionObject.getJSONArray("patterns")
        assertEquals(false, insertionObject.getBoolean("enabled"))
        assertEquals(0, patternsArray.length())
        assertTrue("insertion.intervalMs は無効時に省略される", insertionObject.has("intervalMs").not())
        assertEquals(0, insertionObject.getInt("everyNLoops"))
        assertEquals(0, insertionObject.getInt("probabilityPercent"))
        assertEquals(0, insertionObject.getInt("cooldownLoops"))
        assertEquals(false, insertionObject.getBoolean("exclusive"))
    }

    private fun selectAnimationType(label: String) {
        composeTestRule.selectAnimationTypeByAnchor(
            label = label,
            ensureAnimTabSelected = { ensureAnimTabSelected() },
            waitForNodeWithTag = { tag, timeout -> waitForNodeWithTag(tag, timeout) },
            scrollToAnimationDropdownAnchor = { anchorTag -> scrollToAnimationDropdownAnchor(anchorTag) },
        )
        composeTestRule.waitForIdle()
    }

    private fun scrollToAnimationDropdownAnchor(anchorTag: String) {
        waitForNodeWithTag("spriteAnimList")
        val scrolled = runCatching {
            composeTestRule.onAllNodes(hasScrollAction(), useUnmergedTree = true)[0]
                .performScrollToNode(hasTestTag(anchorTag))
            true
        }.getOrDefault(false)
        if (scrolled) {
            return
        }
        val startTime = System.currentTimeMillis()
        val maxIndex = 30
        val listNode = composeTestRule.onNodeWithTag("spriteAnimList")
        for (index in 0..maxIndex) {
            runCatching { listNode.performScrollToIndex(index) }
            composeTestRule.waitForIdle()
            if (hasNodeWithTag(anchorTag)) {
                return
            }
            if (System.currentTimeMillis() - startTime > 10_000) {
                return
            }
        }
    }

    private fun ensureAnimTabSelected() {
        waitForNodeWithTag("spriteTabAnim")
        val tabNode = composeTestRule.onNodeWithTag("spriteTabAnim", useUnmergedTree = true)
            .fetchSemanticsNode()
        val isSelected = tabNode.config.contains(SemanticsProperties.Selected) &&
            tabNode.config[SemanticsProperties.Selected] == true
        if (!isSelected) {
            composeTestRule.onNodeWithTag("spriteTabAnim", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
        }
    }

    private fun <T> runBlockingIo(block: suspend () -> T): T =
        runBlocking {
            withContext(Dispatchers.IO) {
                block()
            }
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

    private fun recreateToSpriteSettings() {
        composeTestRule.activityRule.scenario.recreate()
        setSpriteSettingsContent()
        waitForNodeWithTag("spriteTabAnim")
    }

    private fun setSpriteSettingsContent() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
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
        composeTestRule.waitForIdle()
    }

    private fun waitForNodeWithTag(tag: String, timeoutMillis: Long = 5_000) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                hasNodeWithTag(tag)
            }
        } catch (error: AssertionError) {
            val tags = dumpSemanticsTags()
            throw AssertionError("タグが見つかりません: $tag。現在のタグ一覧: $tags", error)
        }
    }

    private fun dumpSemanticsTags(): String {
        val rootNodes: List<SemanticsNode> = composeTestRule
            .onAllNodes(isRoot(), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val tags: MutableSet<String> = mutableSetOf()
        rootNodes.forEach { node: SemanticsNode ->
            collectTestTags(node, tags)
        }
        val sortedTags = tags.toList().sorted()
        return if (sortedTags.isEmpty()) {
            "<none>"
        } else {
            sortedTags.joinToString()
        }
    }

    private fun hasNodeWithTag(tag: String): Boolean {
        val unmergedCount = runCatching {
            composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
        }.getOrDefault(0)
        if (unmergedCount > 0) {
            return true
        }
        val mergedCount = runCatching {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size
        }.getOrDefault(0)
        return mergedCount > 0
    }

    private fun collectTestTags(node: SemanticsNode, tags: MutableSet<String>) {
        val config: SemanticsConfiguration = node.config
        val tag: String? = if (config.contains(SemanticsProperties.TestTag)) {
            config[SemanticsProperties.TestTag]
        } else {
            null
        }
        if (tag != null) {
            tags.add(tag)
        }
        node.children.forEach { child: SemanticsNode ->
            collectTestTags(child, tags)
        }
    }

    private fun buildTalkShortPerStateJson(intervalMs: Int, frames: List<Int>): String {
        val baseObject = JSONObject()
            .put("frames", JSONArray(frames))
            .put("intervalMs", intervalMs)
        val insertionObject = JSONObject()
            .put("enabled", false)
            .put("patterns", JSONArray())
            .put("intervalMs", 130)
            .put("everyNLoops", 1)
            .put("probabilityPercent", 50)
            .put("cooldownLoops", 0)
            .put("exclusive", false)
        return JSONObject()
            .put("animationKey", "TalkShort")
            .put("base", baseObject)
            .put("insertion", insertionObject)
            .toString()
    }

}
