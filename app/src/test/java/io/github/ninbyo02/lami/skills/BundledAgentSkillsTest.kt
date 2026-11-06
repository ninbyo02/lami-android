package io.github.ninbyo02.lami.skills

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BundledAgentSkillsTest {
    private val document = "---\nname: web-search\ndescription: Explicit public search.\n---\nUse web_search."

    @Test fun disabledCatalogDoesNotReadInstructionsOrAnyOtherAssets() {
        val loader = BundledAgentSkills { error("Disabled skills must not be read") }
        assertTrue(loader.activeCatalog(emptySet()).isEmpty())
    }

    @Test fun enabledMetadataActivationAndReferenceStayInsideTheKnownSkillRoot() {
        val reads = mutableListOf<String>()
        val loader = BundledAgentSkills { path ->
            reads += path
            when (path) {
                "skills/web-search/SKILL.md" -> document
                "skills/web-search/references/android.md" -> "Use the native adapter."
                else -> error("Unexpected asset")
            }
        }
        assertEquals(listOf("web-search"), loader.activeCatalog(setOf("web-search")).map { it.name })
        assertEquals("Use web_search.", loader.load("web-search").body)
        assertEquals("Use the native adapter.", loader.readReference("web-search", "references/android.md"))
        assertTrue(reads.all { it.startsWith("skills/web-search/") })
    }

    @Test fun pathTraversalAndForeignNamesAreRejectedBeforeReading() {
        val loader = BundledAgentSkills { error("Invalid paths must not reach assets") }
        listOf("../web-search", "web-search/../other", "/web-search", "unknown").forEach { name ->
            try { loader.load(name); fail("Unknown skill accepted") } catch (_: IllegalArgumentException) { }
        }
        listOf("../SKILL.md", "/references/android.md", "references/../SKILL.md", "references\\android.md",
            "references/%2e%2e/android.md", "https://example.invalid/android.md").forEach { path ->
            try { loader.readReference("web-search", path); fail("Invalid reference accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun missingOrMismatchedAssetsCannotActivate() {
        val missing = BundledAgentSkills { throw IOException("Asset not found") }
        try { missing.load("web-search"); fail("Missing skill accepted") } catch (_: IOException) { }
        val wrongName = BundledAgentSkills { document.replace("name: web-search", "name: another-skill") }
        try { wrongName.load("web-search"); fail("Wrong directory accepted") } catch (_: IllegalArgumentException) { }
        val emptyReference = BundledAgentSkills { " " }
        try { emptyReference.readReference("web-search", "references/android.md"); fail("Empty reference accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
