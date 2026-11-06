package io.github.ninbyo02.lami.skills.web

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninbyo02.lami.skills.BundledAgentSkills
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Small Android checks for real packaged assets, persistent settings and the native adapter. */
@RunWith(AndroidJUnit4::class)
class WebSearchSkillInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After fun leaveSearchOff() = runBlocking {
        WebSearchManager.get(context).setEnabled(false)
    }

    @Test fun packagedSkillAndReferenceActivateWithoutSendingData() = runBlocking {
        val skills = BundledAgentSkills(context)
        assertTrue(skills.activeCatalog(emptySet()).isEmpty())
        val definition = skills.activeCatalog(setOf("web-search")).single()
        assertEquals("web-search", definition.name)
        assertEquals("0.1.0", definition.metadata["version"])
        assertTrue(skills.readReference(definition.name, "references/android.md").isNotBlank())

        var calls = 0
        val manager = WebSearchManager(WebSearchPreferences(context), object : WebSearchClient {
            override suspend fun search(query: String): WebSearchResponse {
                calls++
                return WebSearchResponse(query, emptyList())
            }
        }, { skills.load("web-search") }, { skills.readReference("web-search", "references/android.md") })
        manager.setEnabled(false)
        assertTrue(manager.search("Kotlin").reply.contains("OFF"))
        assertEquals(0, calls)
        manager.setEnabled(true)
        assertTrue(manager.search("Kotlin").reply.isNotBlank())
        assertEquals(1, calls)
        manager.setEnabled(false)
    }

    @Test fun dedicatedPreferencesDefaultOffAndSurviveNewInstances() {
        val preferences = context.getSharedPreferences("web_search_skill_v1", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().commit())
        assertFalse(WebSearchPreferences(context).enabled())
        WebSearchPreferences(context).saveEnabled(true)
        assertTrue(WebSearchPreferences(context).enabled())
        WebSearchPreferences(context).saveEnabled(false)
        assertFalse(WebSearchPreferences(context).enabled())
    }

    @Test fun settingsSwitchPersistsOnAndOffWithoutStartingSearch() {
        val manager = WebSearchManager.get(context)
        runBlocking { manager.setEnabled(false) }
        compose.setContent { MaterialTheme { WebSearchSettingsCard() } }
        compose.onNode(isToggleable()).assertIsOff().performClick()
        compose.waitUntil { manager.enabled.value && WebSearchPreferences(context).enabled() }
        compose.onNode(isToggleable()).assertIsOn().performClick()
        compose.waitUntil { !manager.enabled.value && !WebSearchPreferences(context).enabled() }
        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test fun liveDuckDuckGoSearch_onlyWhenExplicitlyRequestedByTestRunner() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("lami.webSearchLiveSmoke") == "true")
        val response = DuckDuckGoWebSearchClient().search("Kotlin Android documentation")
        assertEquals("Kotlin Android documentation", response.query)
        assertTrue(response.hits.isNotEmpty())
        assertTrue(response.hits.size <= 5)
        assertTrue(response.hits.all { it.title.isNotBlank() && it.url.startsWith("http") })
    }
}
