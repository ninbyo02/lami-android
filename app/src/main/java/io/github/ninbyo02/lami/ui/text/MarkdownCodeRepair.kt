package io.github.ninbyo02.lami.ui.text

import java.util.Locale

object MarkdownCodeRepair {
    fun repair(text: String): String {
        if (!text.contains("```")) return text
        return repairCodeFences(text)
    }

    private data class PythonFenceMatch(
        val bodyStartIndex: Int,
        val fromBareFencePattern: Boolean,
    )

    private data class MergedCommentResult(
        val comments: List<String>,
        val nextIndex: Int,
    )

    private data class SplitCommentCodeResult(
        val line: String,
        val extractedCode: String? = null,
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
        val lines = body.split('\n')
        if (lines.isEmpty()) return body

        val repairedLines = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            var line = lines[index]
            val nextLine = lines.getOrNull(index + 1)
            val split = splitCommentFragmentAndCode(line)
            line = split.line
            if (split.extractedCode != null) {
                repairedLines.add(line)
                repairedLines.add(split.extractedCode)
                index += 1
                continue
            }
            if (line.trimStart().startsWith("#")) {
                val merged = mergeCommentBlocks(lines, index)
                repairedLines.addAll(normalizeCommentLines(merged.comments))
                index = merged.nextIndex
                continue
            }
            repairedLines.add(repairCodeLine(line, nextLine))
            index += 1
        }
        return repairedLines.joinToString("\n")
    }

    private fun repairCodeLine(line: String, nextLine: String?): String {
        var repaired = line
        repaired = repaired.replace(Regex("(?<!\\S)(import\\s+[\\w.]+)import\\s+"), "$1\nimport ")
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
            Regex("(?<=[^\\s=<>!])=(?=[^=\\s])"),
            " = ",
        )
        repaired = repaired.replace(Regex("(?<=\\d),(?=\\d)"), ", ")
        repaired = repaired.replace(Regex("(?<=\\S),(?=\\S)"), ", ")

        if (nextLine != null && isCommentFragment(nextLine.trim())) {
            repaired = repaired.replace(Regex("\\s+#\\s*$"), "")
        }
        return repaired
    }

    private fun mergeCommentBlocks(lines: List<String>, startIndex: Int): MergedCommentResult {
        val comments = mutableListOf<String>()
        var index = startIndex
        while (index < lines.size) {
            val current = lines[index]
            val trimmed = current.trim()
            if (trimmed.isEmpty()) break
            if (trimmed.startsWith("#")) {
                comments.add(current)
                index += 1
                continue
            }
            if (!isCommentFragment(trimmed)) break
            comments.add("# $trimmed")
            index += 1
        }
        return MergedCommentResult(comments = comments, nextIndex = index)
    }

    private fun splitCommentFragmentAndCode(line: String): SplitCommentCodeResult {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("#")) return SplitCommentCodeResult(line = line)
        val match = Regex("^(\\s*#\\s*[^=]+?)([A-Za-z_][A-Za-z0-9_]*\\s*=.*)$").find(line)
        if (match != null) {
            val commentLine = normalizePlainComment(match.groupValues[1])
            val codeLine = match.groupValues[2].trimStart()
            return SplitCommentCodeResult(line = commentLine, extractedCode = codeLine)
        }
        return SplitCommentCodeResult(line = line)
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
            if (!rawContent.contains("---") && isCommentFragment(rawContent)) {
                val mergedContent = StringBuilder(rawContent)
                var cursor = index + 1
                while (cursor < lines.size) {
                    val nextContent = lines[cursor].trim().removePrefix("#").trim()
                    if (nextContent.isEmpty() || nextContent.contains("---") || !isCommentFragment(nextContent)) break
                    mergedContent.append(nextContent)
                    cursor += 1
                }
                rebuilt.add(normalizePlainComment("# ${mergedContent}"))
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
        if (!normalized.trimStart().startsWith("#")) {
            normalized = "# ${normalized.trim()}"
        }
        return normalized
    }

    private fun normalizePlainComment(line: String): String {
        val trimmed = line.trim()
        val content = trimmed.removePrefix("#").trim()
        val merged = content
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\(\\s*"), "（")
            .replace(Regex("\\s*\\)"), "）")
        return "# $merged"
    }

    private fun isCommentFragment(text: String): Boolean {
        if (text.isEmpty()) return false
        if (looksLikeCodeLine(text)) return false
        if (text.length > 18) return false
        return containsJapanese(text) || text.all { it == '-' || it.isWhitespace() }
    }

    private fun containsJapanese(text: String): Boolean {
        return text.any {
            Character.UnicodeBlock.of(it) == Character.UnicodeBlock.HIRAGANA ||
                Character.UnicodeBlock.of(it) == Character.UnicodeBlock.KATAKANA ||
                Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
        }
    }

    private fun looksLikeCodeLine(text: String): Boolean {
        if (text.contains(Regex("[=\\[\\]{}():]"))) return true
        if (text.startsWith("import ")) return true
        if (text.startsWith("from ")) return true
        if (text.startsWith("for ") || text.startsWith("if ") || text.startsWith("while ")) return true
        return false
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

    private fun isFenceLine(line: String): Boolean {
        return line.trimStart(' ', '\t').startsWith("```")
    }
}
