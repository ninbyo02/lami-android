package io.github.ninbyo02.lami.npu

internal object Qairt244NpuOutputSanitizer {
    private val templateTokenPatterns = listOf(
        Regex("<start_of_turn>\\s*(?:user|model)?", RegexOption.IGNORE_CASE),
        Regex("<end_of_turn>", RegexOption.IGNORE_CASE),
        Regex("<end\\b[^\\n>]*(?:>|$)", RegexOption.IGNORE_CASE),
    )
    private val roleLinePattern = Regex("^(?:user|model|assistant|ユーザー|アシスタント)\\s*:?\\s*$", RegexOption.IGNORE_CASE)
    private val userPrefixPattern = Regex("^(?:user|ユーザー)\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val assistantPrefixPattern = Regex("^(?:assistant|model|アシスタント)\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val turnBoundaryPattern = Regex("^-{3,}\\s*$")

    data class Result(
        val rawOutput: String,
        val sanitizedOutput: String,
        val sanitizerApplied: Boolean,
        val removedTemplateTokenCount: Int,
        val removedPromptEcho: Boolean,
    )

    fun sanitize(rawOutput: String, prompt: String): Result {
        val normalizedRaw = rawOutput.replace("\r\n", "\n").replace('\r', '\n')
        var removedTemplateTokenCount = 0
        var withoutTemplateTokens = normalizedRaw
        templateTokenPatterns.forEach { pattern ->
            val matches = pattern.findAll(withoutTemplateTokens).count()
            if (matches > 0) {
                removedTemplateTokenCount += matches
                withoutTemplateTokens = pattern.replace(withoutTemplateTokens, "")
            }
        }

        val promptEcho = prompt.trim()
        var removedPromptEcho = false
        val keptLines = mutableListOf<String>()
        var naturalTextStarted = false
        val seenAssistantLines = mutableSetOf<String>()
        for (sourceLine in withoutTemplateTokens.lines()) {
            val line = sourceLine.trim().trimStart('>').trim()
            if (line.isEmpty()) {
                if (sourceLine.trim().isNotEmpty()) {
                    removedTemplateTokenCount += 1
                }
                if (keptLines.isNotEmpty() && keptLines.last().isNotEmpty()) {
                    keptLines += ""
                }
                continue
            }
            if (roleLinePattern.matches(line)) {
                removedTemplateTokenCount += 1
                continue
            }
            if (isPromptEcho(line, promptEcho) && !naturalTextStarted) {
                removedPromptEcho = true
                continue
            }
            if (!naturalTextStarted && isLeadingNonJapaneseDrift(line, promptEcho)) {
                removedTemplateTokenCount += 1
                continue
            }
            if (userPrefixPattern.containsMatchIn(line)) {
                if (naturalTextStarted) break
                val withoutUserPrefix = userPrefixPattern.replace(line, "").trim()
                if (isPromptEcho(withoutUserPrefix, promptEcho)) {
                    removedPromptEcho = true
                }
                continue
            }
            if (turnBoundaryPattern.matches(line) && naturalTextStarted) break

            val assistantText = assistantPrefixPattern.replace(line, "").trim()
            if (assistantText.isEmpty()) {
                removedTemplateTokenCount += 1
                continue
            }
            val normalizedAssistantText = assistantText.replace(Regex("\\s+"), " ")
            if (!seenAssistantLines.add(normalizedAssistantText)) {
                removedTemplateTokenCount += 1
                continue
            }
            keptLines += assistantText
            naturalTextStarted = true
        }

        val sanitized = keptLines
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        val sanitizerApplied = sanitized != rawOutput ||
            removedTemplateTokenCount > 0 ||
            removedPromptEcho
        return Result(
            rawOutput = rawOutput,
            sanitizedOutput = sanitized,
            sanitizerApplied = sanitizerApplied,
            removedTemplateTokenCount = removedTemplateTokenCount,
            removedPromptEcho = removedPromptEcho,
        )
    }

    private fun isPromptEcho(line: String, prompt: String): Boolean {
        if (prompt.isEmpty()) return false
        val normalizedLine = line
            .removePrefix(">")
            .trim()
            .removeSurrounding("(", ")")
            .trim()
        return normalizedLine == prompt
    }

    private fun isLeadingNonJapaneseDrift(line: String, prompt: String): Boolean {
        if (!containsJapanese(prompt) || containsJapanese(line)) return false
        return line.any { Character.isLetter(it) }
    }

    private fun containsJapanese(value: String): Boolean =
        value.any { char ->
            val block = Character.UnicodeBlock.of(char)
            block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
        }
}
