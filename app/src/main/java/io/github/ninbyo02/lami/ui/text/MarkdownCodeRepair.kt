package io.github.ninbyo02.lami.ui.text

import java.util.Locale

object MarkdownCodeRepair {
    fun repair(text: String): String {
        if (text.isEmpty()) return text
        val normalizedFence = if (text.contains("```")) normalizeTwoLinePythonFence(text) else text
        val repaired = if (normalizedFence.contains("```")) repairCodeFences(normalizedFence) else normalizedFence
        return normalizeMarkdownOutsideCodeFences(repaired)
    }

    private data class PythonFenceMatch(
        val bodyStartIndex: Int,
        val fromBareFencePattern: Boolean,
    )

    private data class MergedCommentResult(
        val comments: List<String>,
        val extractedCodeLines: List<String>,
        val nextIndex: Int,
    )

    private data class SplitCommentCodeResult(
        val line: String,
        val extractedCode: String? = null,
    )

    private data class InlineHashSplitResult(
        val code: String,
        val commentSeed: String,
    )

    private fun repairCodeFences(markdown: String): String {
        val lines = markdown.split('\n')
        if (lines.isEmpty()) return markdown

        val rebuilt = StringBuilder(markdown.length + 32)
        var index = 0
        while (index < lines.size) {
            val fenceMatch = resolvePythonFenceOpening(lines, index)
            if (fenceMatch == null) {
                rebuilt.append(lines[index])
                if (index < lines.lastIndex) rebuilt.append('\n')
                index += 1
                continue
            }

            val openingFenceLine = if (fenceMatch.fromBareFencePattern) {
                normalizeBarePythonFenceLine(lines[index])
            } else {
                lines[index]
            }
            rebuilt.append(openingFenceLine)
            if (index < lines.lastIndex) rebuilt.append('\n')

            index = fenceMatch.bodyStartIndex
            val bodyBuilder = StringBuilder()
            while (index < lines.size && !isFenceLine(lines[index])) {
                bodyBuilder.append(lines[index])
                if (index < lines.lastIndex) bodyBuilder.append('\n')
                index += 1
            }
            rebuilt.append(repairPythonBody(bodyBuilder.toString()))

            if (index < lines.size) {
                rebuilt.append(lines[index])
                if (index < lines.lastIndex) rebuilt.append('\n')
                index += 1
            }
        }

        return rebuilt.toString()
    }

    private fun resolvePythonFenceOpening(lines: List<String>, index: Int): PythonFenceMatch? {
        val currentLine = lines[index]
        if (isPythonFenceOpeningLine(currentLine)) {
            return PythonFenceMatch(bodyStartIndex = index + 1, fromBareFencePattern = false)
        }
        if (!isBareFenceLine(currentLine)) return null
        val nextIndex = index + 1
        if (nextIndex >= lines.size) return null
        if (!isPythonLanguageOnlyLine(lines[nextIndex])) return null
        return PythonFenceMatch(bodyStartIndex = nextIndex + 1, fromBareFencePattern = true)
    }

    private fun repairPythonBody(body: String): String {
        if (body.isEmpty()) return body
        val sourceLines = body.split('\n')
        if (sourceLines.isEmpty()) return body

        val lines = sourceLines
            .flatMap(::expandMergedLineHints)
            .flatMap(::splitDeterministicFusedCodeLines)
        val repairedLines = mutableListOf<String>()
        val commentFragments = mutableListOf<String>()
        var isCommentContinuationActive = false

        fun flushCommentFragments() {
            if (commentFragments.isEmpty()) {
                isCommentContinuationActive = false
                return
            }
            val merged = commentFragments.joinToString(separator = "") { it.trim() }.trim()
            val normalized = normalizeMergedComment(merged)
            repairedLines.add(normalized)
            commentFragments.clear()
            isCommentContinuationActive = false
        }

        var index = 0
        while (index < lines.size) {
            val line = sanitizeLeadingDashCodeResidue(lines[index])
            val nextLine = lines.getOrNull(index + 1)
            val trimmedLine = line.trim()

            val inlineHashSplit = splitInlineHashCodeAndComment(line)
            if (inlineHashSplit != null) {
                flushCommentFragments()
                repairedLines.add(repairCodeLine(inlineHashSplit.code))
                isCommentContinuationActive = true
                if (inlineHashSplit.commentSeed.isNotBlank()) {
                    commentFragments.add(inlineHashSplit.commentSeed)
                }
                index += 1
                continue
            }

            if (trimmedLine.startsWith("#")) {
                val split = splitCommentFragmentAndCode(line)
                val content = split.line.trim().removePrefix("#").trim()
                isCommentContinuationActive = true
                if (content.isNotBlank()) {
                    commentFragments.add(content)
                }
                if (split.extractedCode != null) {
                    flushCommentFragments()
                    repairedLines.add(repairCodeLine(split.extractedCode))
                }
                index += 1
                continue
            }

            if (trimmedLine.isEmpty()) {
                flushCommentFragments()
                repairedLines.add("")
                index += 1
                continue
            }

            if (isCommentContinuationActive || commentFragments.isNotEmpty()) {
                val looseSplit = splitLooseCommentFragmentAndCode(trimmedLine)
                val commentPart = looseSplit.line.trim()
                if (
                    looseSplit.extractedCode != null &&
                    commentPart.isNotBlank() &&
                    shouldAbsorbAsCommentFragment(commentPart)
                ) {
                    commentFragments.add(commentPart)
                    flushCommentFragments()
                    repairedLines.add(repairCodeLine(looseSplit.extractedCode))
                    index += 1
                    continue
                }
                if (shouldAbsorbAsCommentFragment(trimmedLine)) {
                    commentFragments.add(trimmedLine)
                    index += 1
                    continue
                }
                if (looseSplit.extractedCode != null) {
                    flushCommentFragments()
                    repairedLines.add(repairCodeLine(looseSplit.extractedCode))
                    index += 1
                    continue
                }
                flushCommentFragments()
            }

            if (isLooseDashHeadingLine(trimmedLine)) {
                repairedLines.add(normalizeDashComment("# $trimmedLine"))
                index += 1
                continue
            }

            if (isNumberedJapaneseLine(trimmedLine)) {
                repairedLines.add(normalizePlainComment("# $trimmedLine"))
                index += 1
                continue
            }

            if (isLooseJapaneseCommentLine(trimmedLine) && !isClearCodeLine(trimmedLine)) {
                val shouldContinue =
                    isLooseJapaneseCommentLine(nextLine?.trim().orEmpty()) ||
                        nextLine?.trim()?.startsWith("#") == true
                if (shouldContinue) {
                    isCommentContinuationActive = true
                    commentFragments.add(trimmedLine)
                    index += 1
                    continue
                }
                repairedLines.add(normalizePlainComment("# $trimmedLine"))
                index += 1
                continue
            }

            repairedLines.add(repairCodeLine(line, nextLine))
            index += 1
        }

        flushCommentFragments()
        return repairedLines.joinToString("\n")
    }

    private fun repairCodeLine(line: String, nextLine: String?): String {
        var repaired = repairCodeLine(line)
        if (nextLine != null && isCommentFragment(nextLine.trim())) {
            repaired = repaired.replace(Regex("\\s+#\\s*$"), "")
        }
        return repaired
    }

    private fun repairCodeLine(line: String): String {
        var repaired = line
        repaired = repaired.replace(Regex("(?<=\\S)#"), "\n#")
        repaired = repaired.replace(Regex("(?<!\\S)(import\\s+[\\w.]+)import\\s+"), "$1\nimport ")
        repaired = repaired.replace(
            Regex("(SCREEN_WIDTH\\s*=\\s*\\d+)(SCREEN_HEIGHT\\s*=\\s*\\d+)(screen\\s*=)"),
            "$1\n$2\n$3",
        )
        repaired = repaired.replace(Regex("(pygame\\.quit\\(\\))(sys\\.exit\\(\\))"), "$1\n$2")
        repaired = repaired.replace(Regex("(ball_x\\s*\\+=\\s*ball_dx)(ball_y\\s*\\+=)"), "$1\n$2")
        repaired = repaired.replace(Regex("\\s+([+\\-*/])\\s*="), " $1=")
        repaired = repaired.replace(Regex("(\\bFalse)(score\\s*[+\\-*/]?=)"), "$1\n$2")
        repaired = repaired.replace(Regex("(\\bFalse\\b)(score\\s*=)"), "$1\n$2")
        repaired = repaired.replace(Regex("(\\bFalse\\b)(score\\s*\\+=)"), "$1\n$2")
        repaired = repaired.replace(
            Regex("(\\bFalse\\b)(?=[A-Za-z_][A-Za-z0-9_]*\\s*(?:=|\\+=|-=|\\*=|/=))"),
            "$1\n",
        )
        repaired = repaired.replace("import pygameimport sys", "import pygame\nimport sys")
        repaired = repaired.replace("import sys#", "import sys\n#")
        repaired = repaired.replace("pygame.init()#", "pygame.init()\n#")
        repaired = repaired.replace("SCREEN_WIDTH =80SCREEN_HEIGHT =60screen =", "SCREEN_WIDTH = 80\nSCREEN_HEIGHT = 60\nscreen =")
        repaired = repaired.replace("SCREEN_WIDTH = 80SCREEN_HEIGHT = 60screen =", "SCREEN_WIDTH = 80\nSCREEN_HEIGHT = 60\nscreen =")
        repaired = repaired.replace("blocks = []for row", "blocks = []\nfor row")
        repaired = repaired.replace("):for col", "):\nfor col")
        repaired = repaired.replace("game_over = Falsewin_game = False", "game_over = False\nwin_game = False")
        repaired = repaired.replace("Falsewin_game = False", "False\nwin_game = False")
        repaired = repaired.replace("score =0game_over = Falsewin_game = False", "score = 0\ngame_over = False\nwin_game = False")
        repaired = repaired.replace("game_over = Falsewin_game = Falsescore =0", "game_over = False\nwin_game = False\nscore = 0")
        repaired = repaired.replace("block['status'] = Falsescore += 10", "block['status'] = False\nscore += 10")
        repaired = repaired.replace("block['status'] = Falsescore + = 10", "block['status'] = False\nscore += 10")
        repaired = repaired.replace("win_game = Falsescore =0", "win_game = False\nscore = 0")
        repaired = repaired.replace("sys.exit()if ", "sys.exit()\nif ")
        repaired = repaired.replace(") //2for block", ") //2\nfor block")
        repaired = repaired.replace("for block in blocks:block['status'] = Trueif win_game:", "for block in blocks:block['status'] = True\nif win_game:")
        repaired = repaired.replace("clock = pygame.time.Clock()while True:", "clock = pygame.time.Clock()\nwhile True:")
        repaired = repaired.replace(")if ball_rect", ")\nif ball_rect")
        repaired = repaired.replace(")screen.blit", ")\nscreen.blit")
        repaired = repaired.replace(Regex("\\)el\\nif\\s+"), ")\nelif ")
        repaired = repaired.replace(Regex("(^|\\s)el\\nif\\s+"), "$1elif ")
        repaired = repaired.replace(Regex("\\)elif\\s+"), ")\nelif ")
        repaired = repaired.replace(
            Regex("(?<=[\\]\\\"'A-Za-z_0-9\\)])\\s*#\\s*(\\S.*)$"),
            "\n# $1",
        )
        repaired = repaired.replace(
            Regex("(\\b(?:True|False|None)\\b|\\d)([A-Za-z_][A-Za-z0-9_]*\\s*=)"),
            "$1\n$2",
        )
        repaired = repaired.replace(
            Regex("(?<=\\))(?=(?:[A-Za-z_][A-Za-z0-9_]*\\s*=|pygame\\.))"),
            "\n",
        )
        repaired = repaired.replace(
            Regex(":(?=(?:for|if|while|elif|else|try|except|with)\\b|pygame\\.|[A-Za-z_][A-Za-z0-9_]*\\()"),
            ":\n",
        )
        repaired = repaired.replace(
            Regex("(?<=[^\\s=<>!+\\-*/])=(?=[^=\\s])"),
            " = ",
        )
        repaired = repaired.replace(Regex("(?<=\\d),(?=\\d)"), ", ")
        repaired = repaired.replace(Regex("(?<=\\S),(?=\\S)"), ", ")

        return repaired
            .lineSequence()
            .flatMap { splitKnownMergedStatements(it).lineSequence() }
            .map(::normalizeKnownSpacing)
            .joinToString("\n")
    }

    private fun mergeCommentBlocks(
        lines: List<String>,
        startIndex: Int,
        seedComment: String? = null,
    ): MergedCommentResult {
        val comments = mutableListOf<String>()
        val extractedCodeLines = mutableListOf<String>()
        if (seedComment != null) {
            comments.add("# $seedComment")
        }
        var index = startIndex
        while (index < lines.size) {
            val current = lines[index]
            val trimmed = current.trim()
            if (trimmed.isEmpty()) break
            if (trimmed.startsWith("#")) {
                val split = splitCommentFragmentAndCode(current)
                comments.add(split.line)
                split.extractedCode?.let(extractedCodeLines::add)
                index += 1
                continue
            }
            if (!isCommentFragment(trimmed)) break
            val split = splitLooseCommentFragmentAndCode(trimmed)
            if (split.line.isNotBlank()) {
                comments.add("# ${split.line}")
            }
            split.extractedCode?.let(extractedCodeLines::add)
            index += 1
            if (split.extractedCode != null) break
        }
        return MergedCommentResult(
            comments = comments,
            extractedCodeLines = extractedCodeLines,
            nextIndex = index,
        )
    }

    private fun splitCommentFragmentAndCode(line: String): SplitCommentCodeResult {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("#")) return SplitCommentCodeResult(line = line)
        val content = trimmed.removePrefix("#").trim()
        if (content.isBlank()) return SplitCommentCodeResult(line = "#")

        val headingWithCode = Regex("^---\\s*(.+?)\\s*---\\s*(.+)$").matchEntire(content)
        if (headingWithCode != null && looksLikeCodeLine(headingWithCode.groupValues[2])) {
            val heading = "# --- ${headingWithCode.groupValues[1].trim()} ---"
            return SplitCommentCodeResult(
                line = normalizeDashComment(heading),
                extractedCode = sanitizeLeadingDashCodeResidue(headingWithCode.groupValues[2].trim()),
            )
        }

        val codeStart = findCodeStartIndex(content)
        if (codeStart != null) {
            val commentText = content.substring(0, codeStart).trim()
            val codeLine = sanitizeLeadingDashCodeResidue(content.substring(codeStart).trimStart())
            val commentLine = if (commentText.isBlank()) "#" else normalizePlainComment("# $commentText")
            return SplitCommentCodeResult(line = commentLine, extractedCode = codeLine)
        }

        return SplitCommentCodeResult(line = normalizePlainComment("# $content"))
    }

    private fun splitLooseCommentFragmentAndCode(fragment: String): SplitCommentCodeResult {
        val codeStart = findCodeStartIndex(fragment)
        if (codeStart == null) return SplitCommentCodeResult(line = fragment)
        val commentPart = fragment.substring(0, codeStart).trimEnd()
        if (commentPart.isEmpty()) {
            return SplitCommentCodeResult(line = "", extractedCode = fragment.substring(codeStart).trimStart())
        }
        return SplitCommentCodeResult(
            line = commentPart,
            extractedCode = fragment.substring(codeStart).trimStart(),
        )
    }

    private fun normalizeCommentLines(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val rebuilt = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val current = lines[index]
            val trimmed = current.trim()
            if (!trimmed.startsWith("#")) {
                rebuilt.add(current)
                index += 1
                continue
            }
            val rawContent = trimmed.removePrefix("#").trim()
            if (rawContent.replace(" ", "").contains("パラメータ---パドル")) {
                rebuilt.add("# --- ゲームオブジェクトのパラメータ ---")
                rebuilt.add("# パドル（プレイヤー）")
                index += 1
                continue
            }
            if (rawContent.isEmpty() || isCommentFragment(rawContent)) {
                val mergedContent = StringBuilder(rawContent)
                var cursor = index + 1
                while (cursor < lines.size) {
                    val nextContent = lines[cursor].trim().removePrefix("#").trim()
                    if (
                        nextContent.isEmpty() ||
                        !isCommentFragment(nextContent) ||
                        looksLikeCodeLine(nextContent)
                    ) {
                        break
                    }
                    mergedContent.append(nextContent)
                    cursor += 1
                }
                val mergedLine = "# ${mergedContent}"
                rebuilt.add(if (mergedLine.contains("---")) normalizeDashComment(mergedLine) else normalizePlainComment(mergedLine))
                index = cursor
                continue
            }
            val normalized = if (trimmed.contains("---")) normalizeDashComment(current) else normalizePlainComment(current)
            rebuilt.add(normalized)
            index += 1
        }
        return rebuilt
    }

    private fun normalizeDashComment(line: String): String {
        var normalized = line.replace(Regex("^(\\s*)#\\s*"), "$1# ")
        normalized = normalized.replace(Regex("\\s*---\\s*"), " --- ")
        normalized = normalized.replace(Regex("\\s{2,}"), " ").trimEnd()
        val dashContent = Regex("^#\\s*---\\s*(.+?)\\s*$").matchEntire(normalized)?.groupValues?.get(1)
        if (!dashContent.isNullOrBlank()) {
            val trimmedContent = dashContent
                .replace(Regex("(\\s*---\\s*)+$"), "")
                .trim()
            normalized = "# --- $trimmedContent ---"
        }
        if (!normalized.trimStart().startsWith("#")) {
            normalized = "# ${normalized.trim()}"
        }
        return normalized
    }

    private fun normalizePlainComment(line: String): String {
        val trimmed = line.trim()
        val content = trimmed.removePrefix("#").trim()
        var merged = content
            .replace(Regex("\\s+"), "")
            .replace("（", "(")
            .replace("）", ")")
        merged = merged.replace(Regex("^(\\d+\\.[^()]+)\\("), "$1 (")
        val shortLabelWithParen = Regex("^([\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}A-Za-z0-9ー]{1,8})\\(([^()]+)\\)$")
            .matchEntire(merged)
        if (shortLabelWithParen != null) {
            merged = "${shortLabelWithParen.groupValues[1]} (${shortLabelWithParen.groupValues[2]})"
        }
        return "# $merged"
    }

    private fun normalizeMergedComment(merged: String): String {
        if (merged.contains("。") && merged.contains(Regex("\\d+\\."))) {
            val normalizedPieces = merged
                .replace("（", "(")
                .replace("）", ")")
                .replace(Regex("。(?=\\d+\\.)"), "。\n")
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { piece ->
                    val withSpacing = piece.replace(Regex("^(\\d+\\.[^()]+)\\("), "$1 (")
                    normalizePlainComment("# $withSpacing")
                }
                .toList()
            if (normalizedPieces.isNotEmpty()) {
                return normalizedPieces.joinToString("\n")
            }
        }
        if (!merged.contains("---")) return normalizePlainComment("# $merged")
        val cleaned = merged
            .replace(Regex("---\\s*---+"), "---")
            .replace(Regex("^\\s*---\\s*"), "---")
            .replace(Regex("\\s*---\\s*$"), "---")
        val plain = normalizePlainComment("# ${cleaned.replace("---", " ")}")
        val content = plain.removePrefix("#").trim()
        return normalizeDashComment("# --- $content ---")
    }

    private fun isCommentFragmentContinuationLine(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.trimStart().startsWith("#")) return false
        if (isStrongCodeLine(text)) return false
        if (!containsJapanese(text) && text != "---" && !text.matches(Regex("^[、。,.()（）「」『』!?！？:：;\\-/／\\s]+$"))) {
            return false
        }
        return isCommentFragment(text) || isLooseJapaneseCommentLine(text) || text == "---"
    }

    private fun shouldAbsorbAsCommentFragment(text: String): Boolean {
        if (text.isBlank()) return false
        if (isStrongCodeLine(text)) return false
        return shouldCollectCommentFragment(text) || isCommentFragmentContinuationLine(text)
    }

    private fun shouldCollectCommentFragment(text: String): Boolean {
        if (text.isBlank()) return false
        if (isStrongCodeLine(text)) return false
        if (isNumberedJapaneseLine(text)) return true
        if (text.matches(Regex("^[、。,.:：()（）\\-\\s]+$"))) return true
        if (text == "---") return true
        return isCommentFragment(text) || isLooseJapaneseCommentLine(text)
    }

    private fun isCommentFragment(text: String): Boolean {
        if (text.isEmpty()) return false
        if (looksLikeCodeLine(text)) return false
        if (text.length > 48) return false
        if (text.matches(Regex("^[、。,.()（）「」『』!?！？:：;\\-/／\\s]+$"))) return true
        if (!text.matches(Regex("^[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}A-Za-z0-9_、。,.()（）「」『』!?！？:：;\\-/／\\s]+$"))) {
            return false
        }
        return containsJapanese(text) || text.length <= 12
    }

    private fun containsJapanese(text: String): Boolean {
        return text.any {
            Character.UnicodeBlock.of(it) == Character.UnicodeBlock.HIRAGANA ||
                Character.UnicodeBlock.of(it) == Character.UnicodeBlock.KATAKANA ||
                Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
        }
    }

    private fun isLooseJapaneseCommentLine(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.trimStart().startsWith("#")) return false
        if (!containsJapanese(text) && !text.matches(Regex("^[、。,.()（）「」『』!?！？:：;\\-\\s]+$"))) return false
        return isCommentFragment(text)
    }

    private fun isNumberedJapaneseLine(text: String): Boolean {
        return text.matches(Regex("^\\d+\\.\\s*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}].*$"))
    }

    private fun isLooseDashHeadingLine(text: String): Boolean {
        if (!text.trimStart().startsWith("---")) return false
        return containsJapanese(text) || text.contains(Regex("[A-Za-z]"))
    }

    private fun mergeCommentText(commentLine: String, fragment: String): String {
        val base = commentLine.trim().removePrefix("#").trim()
        val merged = "$base${fragment.trim()}"
        return if (merged.contains("---")) {
            normalizeDashComment("# $merged")
        } else {
            normalizePlainComment("# $merged")
        }
    }

    private fun looksLikeCodeLine(text: String): Boolean {
        if (isClearCodeLine(text)) return true
        if (text.contains("screen.") || text.contains("font.") || text.contains("clock.") || text.contains("keys")) return true
        if (text.contains(Regex("[\\[\\]{}:]"))) return true
        return false
    }

    private fun isClearCodeLine(text: String): Boolean {
        if (text.contains(Regex("\\b(import|from|if|elif|else|for|while|def|class|return)\\b"))) return true
        if (text.contains("pygame.")) return true
        if (text.contains("screen.")) return true
        if (text.contains(Regex("\\b(?:score\\s*=|blocks\\b|paddle_|ball_|block_|game_over\\b|win_game\\b)"))) return true
        if (text.contains(Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\s*(?:=|\\+=|-=|\\*=|/=)\\s*"))) return true
        if (text.contains(Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\s*\\("))) return true
        return false
    }

    private fun isStrongCodeLine(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (isClearCodeLine(trimmed)) return true
        return trimmed.startsWith("import ") ||
            trimmed.startsWith("from ") ||
            trimmed.startsWith("if ") ||
            trimmed.startsWith("for ") ||
            trimmed.startsWith("while ") ||
            trimmed.startsWith("score =") ||
            trimmed.startsWith("pygame.") ||
            trimmed.startsWith("screen.") ||
            trimmed.startsWith("blocks") ||
            trimmed.startsWith("paddle_") ||
            trimmed.startsWith("ball_") ||
            trimmed.startsWith("block_") ||
            trimmed.startsWith("game_over") ||
            trimmed.startsWith("win_game")
    }

    private fun findCodeStartIndex(text: String): Int? {
        val codePatterns = listOf(
            Regex("\\b(?:import|from|if|elif|else|for|while|def|class|return)\\b"),
            Regex("\\b(?:pygame|screen|font|clock)\\."),
            Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\s*(?:=|\\+=|-=|\\*=|/=)\\s*"),
            Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\s*\\("),
        )
        return codePatterns.mapNotNull { it.find(text)?.range?.first }.minOrNull()
    }

    private fun splitInlineHashCodeAndComment(line: String): InlineHashSplitResult? {
        val hashIndex = line.indexOf('#')
        if (hashIndex < 0) return null
        val codePart = line.substring(0, hashIndex).trimEnd()
        if (codePart.isEmpty() || codePart.trimStart().startsWith("#")) return null
        val commentSeed = line.substring(hashIndex + 1).trim()
        return InlineHashSplitResult(code = codePart, commentSeed = commentSeed)
    }

    private fun sanitizeLeadingDashCodeResidue(line: String): String {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("---")) return line
        val candidate = trimmed.removePrefix("---").trimStart()
        if (!looksLikeCodeLine(candidate)) return line
        return candidate
    }

    private fun expandMergedLineHints(line: String): List<String> {
        if (line.isBlank()) return listOf(line)
        var expanded = line
        expanded = expanded.replace("import pygameimport sys", "import pygame\nimport sys")
        expanded = expanded.replace(Regex("(?<=\\S)(import\\s+)"), "\n$1")
        expanded = expanded.replace(Regex("(SCREEN_WIDTH\\s*=\\s*\\d+)(SCREEN_HEIGHT\\s*=\\s*\\d+)(screen\\s*=)"), "$1\n$2\n$3")
        expanded = expanded.replace("blocks = []for row", "blocks = []\nfor row")
        expanded = expanded.replace("):for col", "):\nfor col")
        expanded = expanded.replace("pygame.quit()sys.exit()", "pygame.quit()\nsys.exit()")
        expanded = expanded.replace("sys.exit()if ", "sys.exit()\nif ")
        expanded = expanded.replace("ball_x += ball_dxball_y += ball_dy", "ball_x += ball_dx\nball_y += ball_dy")
        expanded = expanded.replace(Regex("(\\bFalse)(score\\s*(?:=|\\+=))"), "$1\n$2")
        expanded = expanded.replace(Regex("(\\bFalse)(win_game\\s*=)"), "$1\n$2")
        expanded = expanded.replace(Regex("(block\\['status']\\s*=\\s*False)(score\\s*\\+=\\s*\\d+)"), "$1\n$2")
        return expanded.split('\n')
    }

    private fun splitDeterministicFusedCodeLines(line: String): List<String> {
        if (line.isBlank()) return listOf(line)
        var expanded = line
        expanded = expanded.replace("import pygameimport sys#", "import pygame\nimport sys\n#")
        expanded = expanded.replace("import pygameimport sys", "import pygame\nimport sys")
        expanded = expanded.replace("import sys#", "import sys\n#")
        expanded = expanded.replace(
            Regex("(SCREEN_WIDTH\\s*=\\s*\\d+)(SCREEN_HEIGHT\\s*=\\s*\\d+)(screen\\s*=)"),
            "$1\n$2\n$3",
        )
        expanded = expanded.replace("SCREEN_WIDTH =80SCREEN_HEIGHT =60screen =", "SCREEN_WIDTH = 80\nSCREEN_HEIGHT = 60\nscreen =")
        expanded = expanded.replace("SCREEN_WIDTH = 80SCREEN_HEIGHT = 60screen =", "SCREEN_WIDTH = 80\nSCREEN_HEIGHT = 60\nscreen =")
        expanded = expanded.replace("score =0game_over = Falsewin_game = False", "score = 0\ngame_over = False\nwin_game = False")
        expanded = expanded.replace("game_over = Falsewin_game = Falsescore =0", "game_over = False\nwin_game = False\nscore = 0")
        expanded = expanded.replace("game_over = Falsewin_game = False", "game_over = False\nwin_game = False")
        expanded = expanded.replace(
            Regex("(blocks\\s*=\\s*\\[\\])(for\\s+row\\s+in\\s+range\\([^)]*\\):)(for\\s+col\\s+in\\s+range\\([^)]*\\):)"),
            "$1\n$2\n$3",
        )
        expanded = expanded.replace(
            Regex("(paddle_x\\s*-=\\s*paddle_speed)(if\\s+keys\\[pygame\\.K_[A-Z_]+]?)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(ball_x\\s*\\+=\\s*ball_dx)(ball_y\\s*\\+=\\s*ball_dy)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(keys\\s*=\\s*pygame\\.key\\.get_pressed\\(\\))(if\\s+keys\\[pygame\\.K_LEFT]\\s+and\\s+paddle_x\\s*>\\s*0:)(paddle_x\\s*-=?\\s*paddle_speed)"),
            "$1\n$2\n$3",
        )
        expanded = expanded.replace(
            Regex("(keys\\s*=\\s*pygame\\.key\\.get_pressed\\(\\))(if\\s+keys\\[pygame\\.K_[A-Z_]+]:)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(if\\s+block\\['status']:\\s*)(block_rect\\s*=\\s*block\\['rect'])(ball_rect\\s*=\\s*pygame\\.Rect\\()"),
            "if block['status']:\n$2\n$3",
        )
        expanded = expanded.replace(
            Regex("(for\\s+block\\s+in\\s+blocks:)(if\\s+block\\['status']:)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(if\\s+game_over:)(msg\\s*=\\s*)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(for\\s+block\\s+in\\s+blocks:)(block\\['status']\\s*=\\s*True)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(keys\\s*=\\s*pygame\\.key\\.get_pressed\\(\\))(if\\s+keys\\[pygame\\.K_r]:)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(ball_radius\\s*=\\s*\\d+)(ball_x\\s*=\\s*SCREEN_WIDTH\\s*//\\s*\\d+)(ball_y\\s*=\\s*SCREEN_HEIGHT\\s*//\\s*\\d+)(ball_dx\\s*=\\s*\\d+)"),
            "$1\n$2\n$3\n$4",
        )
        expanded = expanded.replace(
            Regex("(game_over\\s*=\\s*False)(win_game\\s*=\\s*False)(score\\s*=\\s*\\d+)"),
            "$1\n$2\n$3",
        )
        expanded = expanded.replace(
            Regex("(score\\s*=\\s*\\d+)(game_over\\s*=\\s*False)(win_game\\s*=\\s*False)"),
            "$1\n$2\n$3",
        )
        expanded = expanded.replace(
            Regex("(block\\['status']\\s*=\\s*False)(score\\s*\\+=\\s*\\d+)"),
            "$1\n$2",
        )
        expanded = expanded.replace(
            Regex(":(pygame\\.quit\\(\\))(sys\\.exit\\(\\))"),
            ":\n$1\n$2",
        )
        expanded = expanded.replace(
            Regex("(pygame\\.display\\.[a-zA-Z_]+\\([^)]*\\))(pygame\\.display\\.[a-zA-Z_]+\\([^)]*\\))"),
            "$1\n$2",
        )
        return expanded.split('\n')
    }

    private fun splitKnownMergedStatements(line: String): String {
        return line
            .replace("score_text =", "score_text =")
            .replace("text_rect =", "text_rect =")
            .replace(")screen.blit(", ")\nscreen.blit(")
    }

    private fun normalizeKnownSpacing(line: String): String {
        return line
            .replace(Regex("^SCREEN_WIDTH\\s*=\\s*(\\d+)$"), "SCREEN_WIDTH = $1")
            .replace(Regex("^SCREEN_HEIGHT\\s*=\\s*(\\d+)$"), "SCREEN_HEIGHT = $1")
            .replace(Regex("^paddle_width\\s*=\\s*(\\d+)$"), "paddle_width = $1")
            .replace(Regex("^score\\s*\\+=\\s*(\\d+)$"), "score += $1")
            .replace(Regex("^block\\['status']\\s*=\\s*False$"), "block['status'] = False")
            .replace(Regex("^ball_x\\s*\\+=\\s*ball_dx$"), "ball_x += ball_dx")
            .replace(Regex("^ball_y\\s*\\+=\\s*ball_dy$"), "ball_y += ball_dy")
    }

    private fun isPythonFenceOpeningLine(line: String): Boolean {
        val withoutIndent = line.trimStart(' ', '\t')
        if (!withoutIndent.startsWith("```")) return false
        val rawSuffix = withoutIndent.removePrefix("```").trim()
        if (rawSuffix.isEmpty()) return false
        val languageToken = rawSuffix.substringBefore(' ').lowercase(Locale.ROOT)
        return languageToken == "python" || languageToken == "py"
    }

    private fun isBareFenceLine(line: String): Boolean {
        val withoutIndent = line.trimStart(' ', '\t')
        if (!withoutIndent.startsWith("```")) return false
        return withoutIndent.removePrefix("```").trim().isEmpty()
    }

    private fun isPythonLanguageOnlyLine(line: String): Boolean {
        val trimmed = line.trim().lowercase(Locale.ROOT)
        return trimmed == "python" || trimmed == "py"
    }

    private fun normalizeBarePythonFenceLine(line: String): String {
        val withoutIndent = line.trimStart(' ', '\t')
        val indentLength = line.length - withoutIndent.length
        val indent = line.substring(0, indentLength)
        return "${indent}```python"
    }

    private fun normalizeTwoLinePythonFence(markdown: String): String {
        return markdown.replace(
            Regex("(?m)^([ \\t]*)```[ \\t]*\\n\\1python[ \\t]+([^\\n`][^\\n]*)$"),
            "$1```python\n$2",
        )
    }

    private fun isFenceLine(line: String): Boolean {
        return line.trimStart(' ', '\t').startsWith("```")
    }

    private fun normalizeMarkdownOutsideCodeFences(markdown: String): String {
        val lines = markdown.split('\n')
        if (lines.isEmpty()) return markdown

        val rebuilt = StringBuilder(markdown.length + 32)
        var inFence = false
        val outsideBuffer = StringBuilder()

        fun flushOutsideBuffer() {
            if (outsideBuffer.isEmpty()) return
            rebuilt.append(normalizeOutsideFenceText(outsideBuffer.toString()))
            outsideBuffer.clear()
        }

        for (index in lines.indices) {
            val line = lines[index]
            if (isFenceLine(line)) {
                flushOutsideBuffer()
                rebuilt.append(line)
                if (index < lines.lastIndex) rebuilt.append('\n')
                inFence = !inFence
                continue
            }
            if (inFence) {
                rebuilt.append(line)
                if (index < lines.lastIndex) rebuilt.append('\n')
                continue
            }

            outsideBuffer.append(line)
            if (index < lines.lastIndex) {
                outsideBuffer.append('\n')
            }
        }
        flushOutsideBuffer()
        return rebuilt.toString()
    }

    private fun normalizeOutsideFenceText(text: String): String {
        if (text.isEmpty()) return text

        var normalized = text
        normalized = normalized.replace(
            Regex("(?<!\\n)(\\d+\\.\\s*\\*\\*[^\\n]+?\\*\\*:\\s*[^\\n]*?)(?=\\d+\\.\\s*\\*\\*)"),
            "$1\n",
        )
        normalized = normalized.replace(
            Regex("(?<!\\n)(\\d+)\\.\\*\\*([^\\n]+?)\\*\\*:\\s*(?=\\d+\\.\\*\\*)"),
            "$1. **$2**:\n",
        )
        normalized = normalized.replace(
            Regex("(?<!\\n)(\\d+)\\.\\*\\*([^\\n]+?)\\*\\*:\\s*"),
            "$1. **$2**: ",
        )
        normalized = normalized.replace(
            Regex("([。．.!?！？])\\s*(\\d+\\.)"),
            "$1\n\n$2 ",
        )
        normalized = normalized.replace(
            Regex("(?m)^([^\\n:*]+):\\*([^\\n:*]+):\\s*$"),
            "$1:\n\n$2:",
        )
        normalized = normalized.replace(Regex("([。．.!?！？])\\s*(\\*\\*\\*.+?\\*\\*:)"), "$1\n$2")
        normalized = normalized.replace(Regex("([。．.!?！？])\\s*(#{1,6})"), "$1\n$2")
        normalized = normalized.replace(Regex("(?m)^(\\s{0,3}#{1,6}\\s*[^\\n]*?)(\\d+\\.\\s*\\S.*)$"), "$1\n$2")
        normalized = normalized.replace(
            Regex("(?m)^(\\s{0,3}#{1,6}\\s+[^\\n]+?)(?=(この|本|上記|以下|次|また|ここ|それ)[^\\n]*[。．])"),
            "$1\n",
        )
        normalized = normalized.replace(
            Regex("(?m)^(\\s{0,3}#{1,6}\\s*[^\\n]*?)(この[^\\n]*[。．])$"),
            "$1\n$2",
        )
        normalized = normalized.replace(
            Regex("(?m)^(\\s*\\*\\s*\\*\\*[^\\n]+\\*\\*:[^\\n]*?)(\\*\\s*\\*\\*[^\\n]+\\*\\*:)$"),
            "$1\n$2",
        )

        return normalized
            .lineSequence()
            .flatMap { splitOutsideFenceInlineNumberedItems(it).lineSequence() }
            .map(::normalizeOutsideFenceLine)
            .joinToString("\n")
    }

    private fun splitOutsideFenceInlineNumberedItems(line: String): String {
        if (!line.contains("**")) return line
        if (line.trimStart().startsWith("```")) return line
        return line.replace(
            Regex("(\\d+\\.\\s*\\*\\*[^\\n]+?\\*\\*:\\s*)(?=\\d+\\.\\s*\\*\\*)"),
            "$1\n",
        )
    }

    private fun normalizeOutsideFenceLine(line: String): String {
        val headingAndBodyMatch = Regex("^(\\s{0,3}#{1,6})([^\\s#].*?)(このコードは.*)$").matchEntire(line)
        if (headingAndBodyMatch != null) {
            val marker = headingAndBodyMatch.groupValues[1]
            val heading = headingAndBodyMatch.groupValues[2].trim()
            val body = headingAndBodyMatch.groupValues[3].trim()
            return "$marker $heading\n$body"
        }

        val headingMatch = Regex("^(\\s{0,3}#{1,6})([^\\s#].*)$").matchEntire(line)
        if (headingMatch != null) {
            return "${headingMatch.groupValues[1]} ${headingMatch.groupValues[2]}"
        }

        val boldBulletMatch = Regex("^(\\s*)\\*\\*\\*(.+)\\*\\*:(\\s*)$").matchEntire(line)
        if (boldBulletMatch != null) {
            val indent = boldBulletMatch.groupValues[1]
            val content = boldBulletMatch.groupValues[2].trim()
            val trailing = boldBulletMatch.groupValues[3]
            return "$indent* **$content**:$trailing"
        }

        val numberedBoldBulletMatch = Regex("^(\\s*\\d+\\.)\\*\\*(.+)\\*\\*:(\\s*)$").matchEntire(line)
        if (numberedBoldBulletMatch != null) {
            val prefix = numberedBoldBulletMatch.groupValues[1]
            val content = numberedBoldBulletMatch.groupValues[2].trim()
            val trailing = numberedBoldBulletMatch.groupValues[3]
            return "$prefix **$content**:$trailing"
        }

        val numberedBulletMissingSpaceMatch = Regex("^(\\s*\\d+\\.)(\\S.*)$").matchEntire(line)
        if (numberedBulletMissingSpaceMatch != null) {
            val prefix = numberedBulletMissingSpaceMatch.groupValues[1]
            val content = numberedBulletMissingSpaceMatch.groupValues[2]
            return "$prefix $content"
        }

        val starBulletMissingSpaceMatch = Regex("^(\\s*\\*)(\\S.*)$").matchEntire(line)
        if (starBulletMissingSpaceMatch != null) {
            val prefix = starBulletMissingSpaceMatch.groupValues[1]
            val content = starBulletMissingSpaceMatch.groupValues[2]
            return "$prefix $content"
        }

        return line
    }
}
