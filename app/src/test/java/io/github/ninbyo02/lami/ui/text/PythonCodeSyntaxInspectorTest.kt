package io.github.ninbyo02.lami.ui.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonCodeSyntaxInspectorTest {
    @Test
    fun inspect_onlyInsidePythonFence() {
        val markdown = """
            ```python
            if x:
            y = 1
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)

        assertTrue(result.hasWarnings)
        assertTrue(result.warnings.any { it.type == PythonCodeWarningType.POSSIBLE_EMPTY_BLOCK })
    }

    @Test
    fun inspect_doesNotWarnOutsideFence() {
        val markdown = """
            if x:
            y = 1
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)
        assertFalse(result.hasWarnings)
    }

    @Test
    fun inspect_detectsPossibleEmptyBlock() {
        val markdown = """
            ```python
            if block['status']:

            block_rect = block['rect']
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)
        assertTrue(result.warnings.any { it.type == PythonCodeWarningType.POSSIBLE_EMPTY_BLOCK })
    }

    @Test
    fun inspect_detectsPossibleIndentJump() {
        val markdown = """
            ```python
            keys = pygame.key.get_pressed()
                    if keys[pygame.K_LEFT]:
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)
        assertTrue(result.warnings.any { it.type == PythonCodeWarningType.POSSIBLE_INDENT_JUMP })
    }

    @Test
    fun inspect_detectsTopLevelDedentAfterWhileTrue() {
        val markdown = """
            ```python
            while True:
                for event in pygame.event.get():
                    pass
            if not game_over and not win_game:
            ball_x += ball_speed_x
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)
        assertTrue(result.warnings.any { it.type == PythonCodeWarningType.POSSIBLE_TOP_LEVEL_DEDENT_AFTER_BLOCK })
    }

    @Test
    fun inspect_detectsFusedInlineCode() {
        val markdown = """
            ```python
            if keys[pygame.K_RIGHT] and paddle_x < SCREEN_WIDTH - paddle_width:paddle_x += paddle_speed
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)
        assertTrue(result.warnings.any { it.type == PythonCodeWarningType.POSSIBLE_FUSED_CODE })
    }

    @Test
    fun inspect_setsLineTextOnWarning() {
        val markdown = """
            ```python
            if keys[pygame.K_RIGHT] and paddle_x < SCREEN_WIDTH - paddle_width:paddle_x += paddle_speed
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)
        val warning = result.warnings.first { it.type == PythonCodeWarningType.POSSIBLE_FUSED_CODE }

        assertTrue(warning.lineText.contains("paddle_x += paddle_speed"))
    }

    @Test
    fun inspect_noWarningsForSimpleValidCode() {
        val markdown = """
            ```python
            x = 1
            if x > 0:
                print(x)
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)
        assertFalse(result.hasWarnings)
    }

    @Test
    fun inspect_ignoresNonPythonFences() {
        val markdown = """
            ```bash
            if x:
            y = 1
            ```

            ```json
            {"k": "v:1"}
            ```

            ```
            if x:
            y = 1
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)
        assertFalse(result.hasWarnings)
    }

    @Test
    fun inspect_doesNotFlagColonsInsideFStrings() {
        val markdown = """
            ```python
            score_text = self.font.render(f"SCORE: {self.score}", True, COLOR_SCORE)
            lives_text = self.font.render(f"LIVES: {'●' * self.lives}", True, (255, 100, 100))
            msg2 = self.font.render(f"Final Score: {self.score}", True, COLOR_SCORE)
            formatted = f"{self.score:05d}"
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)

        assertFalse(result.hasWarnings)
    }

    @Test
    fun inspect_doesNotFlagNonSuiteColons() {
        val markdown = """
            ```python
            label = "Score: 100"
            data = {"name": "LAMI"}
            value: int = 10
            square = lambda x: x * x
            values = items[1:3]
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)

        assertFalse(result.hasWarnings)
    }

    @Test
    fun inspect_ignoresBlankAndCommentLinesBeforeValidBlockBody() {
        val markdown = """
            ```python
            if ready:

                # initialization: safe
                launch()
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)

        assertFalse(result.hasWarnings)
    }

    @Test
    fun inspect_findsSuiteColonAfterColonInsideString() {
        val markdown = """
            ```python
            if value == "a:b":print(value)
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)

        assertTrue(result.warnings.any { it.type == PythonCodeWarningType.POSSIBLE_FUSED_CODE })
    }

    @Test
    fun inspect_doesNotTreatTrailingCommentAsInlineCode() {
        val markdown = """
            ```python
            if ready:  # condition: launch
                launch()
            ```
        """.trimIndent()

        val result = PythonCodeSyntaxInspector.inspectMarkdown(markdown)

        assertFalse(result.hasWarnings)
    }
}
