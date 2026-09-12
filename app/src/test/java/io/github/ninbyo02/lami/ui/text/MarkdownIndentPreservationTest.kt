package io.github.ninbyo02.lami.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownIndentPreservationTest {
    private fun fence(body: String, language: String = "python") = "```$language\n$body\n```"

    @Test fun `valid event suites preserve their original indentation including tabs`() {
        for (unit in listOf("  ", "    ", "\t")) {
            val body = listOf("for event in pygame.event.get():", unit + "if event.type == pygame.QUIT:",
                unit.repeat(2) + "pygame.quit()", unit.repeat(2) + "sys.exit()").joinToString("\n")
            assertEquals(fence(body), MarkdownCodeRepair.repair(fence(body)))
        }
    }

    @Test fun `valid nested function and event loop survive all repair stages`() {
        val body = listOf("def main():", "    while True:", "        for event in pygame.event.get():",
            "            if event.type == pygame.QUIT:", "                pygame.quit()", "                sys.exit()").joinToString("\n")
        assertEquals(fence(body), MarkdownCodeRepair.repair(fence(body)))
    }

    @Test fun `nested comment keeps its indentation without swallowing subsequent code`() {
        val body = "def main():\n    # Handle events\n    for event in pygame.event.get():\n        if event.type == pygame.QUIT:\n            pygame.quit()"
        assertEquals(fence(body), MarkdownCodeRepair.repair(fence(body)))
    }

    @Test fun `valid dedent after a quit suite remains outside that suite`() {
        val body = "for event in pygame.event.get():\n    if event.type == pygame.QUIT:\n        pygame.quit()\n    sys.exit()"
        assertEquals(fence(body), MarkdownCodeRepair.repair(fence(body)))
    }

    @Test fun `flat representative for suite is repaired relative to its parent`() {
        val input = fence("for event in pygame.event.get():\nif event.type == pygame.QUIT:\npygame.quit()\nsys.exit()")
        val expected = fence("for event in pygame.event.get():\n    if event.type == pygame.QUIT:\n        pygame.quit()\n        sys.exit()")
        assertEquals(expected, MarkdownCodeRepair.repair(input))
        assertEquals(expected, MarkdownCodeRepair.repair(expected))
    }

    @Test fun `flat while representative retains the existing repair and is idempotent`() {
        val input = fence("while True:\n# 1.イベント処理\nfor event in pygame.event.get():\nif event.type == pygame.QUIT:\npygame.quit()\nsys.exit()")
        val expected = fence("while True:\n    # 1.イベント処理\n    for event in pygame.event.get():\n        if event.type == pygame.QUIT:\n            pygame.quit()\n            sys.exit()")
        assertEquals(expected, MarkdownCodeRepair.repair(input))
        assertEquals(expected, MarkdownCodeRepair.repair(expected))
    }

    @Test fun `missing suite inside a function uses the existing parent offset`() {
        val input = fence("def main():\n    for event in pygame.event.get():\n    if event.type == pygame.QUIT:\n    pygame.quit()\n    sys.exit()")
        val expected = fence("def main():\n    for event in pygame.event.get():\n        if event.type == pygame.QUIT:\n            pygame.quit()\n            sys.exit()")
        assertEquals(expected, MarkdownCodeRepair.repair(input))
    }

    @Test fun `unfenced code and other fenced languages remain unchanged`() {
        val body = "for event in pygame.event.get():\nif event.type == pygame.QUIT:\npygame.quit()"
        assertEquals(body, MarkdownCodeRepair.repair(body))
        assertEquals(fence(body, "text"), MarkdownCodeRepair.repair(fence(body, "text")))
    }
}
