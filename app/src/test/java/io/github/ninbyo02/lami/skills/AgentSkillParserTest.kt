package io.github.ninbyo02.lami.skills

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AgentSkillParserTest {
    private fun document(fields: String, body: String = "Search only when asked.") = "---\n$fields\n---\n$body"

    private fun rejected(text: String) {
        try { AgentSkillParser.parse(text); fail("Invalid skill was accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun standardYamlSupportsFoldedDescriptionQuotedPunctuationAndStringMetadata() {
        val skill = AgentSkillParser.parse(document("""
            name: web-search
            description: >-
              Search public pages:
              only when requested.
            license: Apache-2.0
            compatibility: "Requires a native web_search tool: HTTPS access"
            allowed-tools: "web_search"
            metadata:
              version: "0.1.0"
              author: "Example: team"
        """.trimIndent(), "## Rules\n\n    preserve_code_indentation()"))
        assertEquals("web-search", skill.name)
        assertEquals("Search public pages: only when requested.", skill.description)
        assertEquals("0.1.0", skill.metadata["version"])
        assertEquals("Example: team", skill.metadata["author"])
        assertEquals("Apache-2.0", skill.license)
        assertEquals("web_search", skill.allowedTools)
        assertTrue(skill.body.contains("    preserve_code_indentation()"))
    }

    @Test fun bomAndCrLfDocumentsRemainReadable() {
        val skill = AgentSkillParser.parse("\uFEFF" + document("name: web-search\ndescription: Search.").replace("\n", "\r\n"))
        assertEquals("web-search", skill.name)
        assertFalse(skill.body.contains('\r'))
    }

    @Test fun requiredFieldsAndStandardLimitsAreEnforced() {
        listOf(
            document("name: web-search"),
            document("description: Search."),
            document("name: web-search\ndescription: ''"),
            document("name: Web-Search\ndescription: Search."),
            document("name: web--search\ndescription: Search."),
            document("name: ${"x".repeat(65)}\ndescription: Search."),
            document("name: web-search\ndescription: ${"x".repeat(1025)}"),
            document("name: web-search\ndescription: Search.\ncompatibility: ${"x".repeat(501)}"),
            document("name: web-search\ndescription: Search.\nmetadata:\n  version: 1"),
            document("name: web-search\ndescription: Search.", "  "),
            "name: web-search\ndescription: Search.",
            "---\nname: web-search\ndescription: Search.",
        ).forEach(::rejected)
    }

    @Test fun unsafeTagsDuplicateKeysAliasesAndDeepYamlAreRejected() {
        listOf(
            document("name: web-search\ndescription: Search.\nextra: !!java.net.URL ['https://example.invalid']"),
            document("name: web-search\ndescription: First.\ndescription: Second."),
            document("name: web-search\ndescription: Search.\nmetadata: &a {version: '1'}\nextra: *a"),
            document("name: web-search\ndescription: Search.\nextra: ${"[".repeat(20)}0${"]".repeat(20)}"),
            document("name: web-search\ndescription: Search.\nextra: ${"x".repeat(17_000)}"),
            document("name: web-search\ndescription: Search.", "x".repeat(129 * 1024)),
        ).forEach(::rejected)
    }

    @Test fun activationBodyRemainsSeparateFromMetadata() {
        val skill = AgentSkillParser.parse(document("name: web-search\ndescription: Search.", "---\nThis is body Markdown, not another metadata document."))
        assertTrue(skill.metadata.isEmpty())
        assertTrue(skill.body.startsWith("---\n"))
    }

    @Test fun canonicalBundledDocumentCanBeActivated() {
        val relative = "skills/web-search/SKILL.md"
        val source = listOf(File(relative), File("../$relative")).firstOrNull { it.isFile }
            ?: error("Canonical web-search skill missing from the checkout")
        val definition = AgentSkillParser.parse(source.readText(Charsets.UTF_8))
        assertEquals("web-search", definition.name)
        assertEquals("0.1.0", definition.metadata["version"])
        assertTrue(definition.body.isNotBlank())
    }
}
