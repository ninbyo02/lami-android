package io.github.ninbyo02.lami.ui.text

data class PythonCodeInspectionResult(
    val hasWarnings: Boolean,
    val warnings: List<PythonCodeWarning>,
)

data class PythonCodeWarning(
    val blockIndex: Int,
    val lineNumber: Int,
    val type: PythonCodeWarningType,
    val message: String,
)

enum class PythonCodeWarningType {
    POSSIBLE_EMPTY_BLOCK,
    POSSIBLE_INDENT_JUMP,
    POSSIBLE_TOP_LEVEL_DEDENT_AFTER_BLOCK,
    POSSIBLE_FUSED_CODE,
}

object PythonCodeSyntaxInspector {
    private val fencedPythonRegex = Regex("(?s)```[ \\t]*(python|py)[^\\n]*\\n(.*?)\\n```", RegexOption.IGNORE_CASE)
    private val blockStarterRegex = Regex("^(while|for|if|elif|else|try|except|finally|with|class|def)\\b")
    private val continuationHints = listOf(
        "if not game_over and not win_game:",
        "keys = pygame.key.get_pressed()",
        "ball_x +=",
        "ball_y +=",
        "screen.fill(",
        "pygame.display.flip(",
        "clock.tick(",
    )

    fun inspectMarkdown(markdown: String): PythonCodeInspectionResult {
        val warnings = mutableListOf<PythonCodeWarning>()
        fencedPythonRegex.findAll(markdown).forEachIndexed { blockIndex, match ->
            val code = match.groupValues[2]
            warnings += inspectPythonBlock(code, blockIndex)
        }
        return PythonCodeInspectionResult(
            hasWarnings = warnings.isNotEmpty(),
            warnings = warnings,
        )
    }

    private fun inspectPythonBlock(code: String, blockIndex: Int): List<PythonCodeWarning> {
        val lines = code.split("\n")
        val warnings = mutableListOf<PythonCodeWarning>()

        lines.forEachIndexed { i, line ->
            val trimmed = line.trim()
            val indent = line.takeWhile { it == ' ' }.length
            if (trimmed.endsWith(":")) {
                val next = nextEffectiveLine(lines, i + 1)
                if (next != null) {
                    val isNearFenceEnd = (lines.size - 1 - next.index) <= 0
                    if (!isNearFenceEnd && (next.isBlank || next.indent <= indent)) {
                        warnings += PythonCodeWarning(
                            blockIndex,
                            i + 1,
                            PythonCodeWarningType.POSSIBLE_EMPTY_BLOCK,
                            "':' の後に有効なブロック本文が見つからない可能性があります。",
                        )
                    }
                }
            }

            if (trimmed.contains(':') && hasInlineCodeAfterColon(trimmed)) {
                warnings += PythonCodeWarning(
                    blockIndex,
                    i + 1,
                    PythonCodeWarningType.POSSIBLE_FUSED_CODE,
                    "1行内で ':' の後に文が連結されている可能性があります。",
                )
            }
        }

        for (i in 1 until lines.size) {
            val prev = lineInfo(lines[i - 1])
            val cur = lineInfo(lines[i])
            if (prev.isSkippable || cur.isSkippable) continue
            if (!prev.trimmed.endsWith(":") && cur.indent - prev.indent >= 8) {
                warnings += PythonCodeWarning(
                    blockIndex,
                    i + 1,
                    PythonCodeWarningType.POSSIBLE_INDENT_JUMP,
                    "前行が ':' で終わらないのに急なインデント増加があります。",
                )
            }
        }

        lines.forEachIndexed { i, line ->
            val trimmed = line.trim()
            if (!trimmed.endsWith(":")) return@forEachIndexed
            if (!blockStarterRegex.containsMatchIn(trimmed)) return@forEachIndexed
            val next = nextEffectiveLine(lines, i + 1) ?: return@forEachIndexed
            if (next.indent <= lineInfo(line).indent) return@forEachIndexed
            val lookAhead = lines.drop(next.index + 1).map { it.trim() }
            if (lookAhead.any { candidate ->
                    candidate.isNotBlank() && continuationHints.any { hint -> candidate.startsWith(hint) }
                }
            ) {
                val nextTop = nextTopLevelLine(lines, next.index + 1)
                if (nextTop != null && continuationHints.any { nextTop.trimmed.startsWith(it) }) {
                    warnings += PythonCodeWarning(
                        blockIndex,
                        nextTop.index + 1,
                        PythonCodeWarningType.POSSIBLE_TOP_LEVEL_DEDENT_AFTER_BLOCK,
                        "ブロック継続と思われる行がトップレベルに戻っている可能性があります。",
                    )
                }
            }
        }

        return warnings
    }

    private fun hasInlineCodeAfterColon(trimmed: String): Boolean {
        val colonIndex = trimmed.indexOf(':')
        if (colonIndex < 0 || colonIndex == trimmed.lastIndex) return false
        if (trimmed.contains("http://") || trimmed.contains("https://")) return false
        val after = trimmed.substring(colonIndex + 1).trim()
        if (after.isEmpty()) return false
        val before = trimmed.substring(0, colonIndex)
        val isLikelyDictLiteral = !before.contains(' ') && before.endsWith("{")
        if (isLikelyDictLiteral) return false
        if (before.endsWith("[") || before.contains("[")) return false
        return true
    }

    private data class LineInfo(val index: Int = -1, val trimmed: String, val indent: Int, val isBlank: Boolean, val isComment: Boolean) {
        val isSkippable: Boolean = isBlank || isComment
    }

    private fun lineInfo(line: String, index: Int = -1): LineInfo {
        val trimmed = line.trim()
        return LineInfo(
            index = index,
            trimmed = trimmed,
            indent = line.takeWhile { it == ' ' }.length,
            isBlank = trimmed.isEmpty(),
            isComment = trimmed.startsWith("#"),
        )
    }

    private fun nextEffectiveLine(lines: List<String>, start: Int): LineInfo? {
        var sawBlank = false
        for (i in start until lines.size) {
            val info = lineInfo(lines[i], i)
            if (info.isBlank) {
                sawBlank = true
                continue
            }
            if (info.isComment) continue
            return if (sawBlank) info.copy(isBlank = true) else info
        }
        return null
    }

    private fun nextTopLevelLine(lines: List<String>, start: Int): LineInfo? {
        for (i in start until lines.size) {
            val info = lineInfo(lines[i], i)
            if (info.isSkippable) continue
            if (info.indent == 0) return info
        }
        return null
    }
}
