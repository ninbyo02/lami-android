package io.github.ninbyo02.lami.npu

internal object Qairt244NpuOutputSanitizer {
    private val templateTokenPatterns = listOf(
        Regex("<start_of_turn>\\s*(?:user|model)?", RegexOption.IGNORE_CASE),
        Regex("</\\s*start_of_turn\\s*>?", RegexOption.IGNORE_CASE),
        Regex("<end_of_turn>", RegexOption.IGNORE_CASE),
        Regex("<\\s*end_of_turn\\s*>?", RegexOption.IGNORE_CASE),
        Regex("</\\s*end_of_turn\\s*>?", RegexOption.IGNORE_CASE),
        Regex("<end\\b[^\\n>]*(?:>|$)", RegexOption.IGNORE_CASE),
    )
    private val roleLinePattern = Regex("^(?:user|model|assistant|ユーザー|アシスタント)\\s*:?\\s*$", RegexOption.IGNORE_CASE)
    private val userPrefixPattern = Regex("^(?:user|ユーザー)\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val assistantPrefixPattern = Regex("^(?:assistant|model|アシスタント)\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val turnBoundaryPattern = Regex("^-{3,}\\s*$")
    private val codeFencePattern = Regex("^```[A-Za-z0-9_+.#-]*\\s*$")

    data class Result(
        val rawOutput: String,
        val sanitizedOutput: String,
        val sanitizerApplied: Boolean,
        val removedTemplateTokenCount: Int,
        val removedPromptEcho: Boolean,
        val codeBlockDetected: Boolean,
        val codeFenceCompleted: Boolean,
    )

    fun sanitize(rawOutput: String, prompt: String): Result {
        val normalizedRaw = rawOutput
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
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
        var inCodeBlock = false
        var codeBlockDetected = false
        var codeFenceCompleted = false
        for (sourceLine in withoutTemplateTokens.lines()) {
            if (inCodeBlock) {
                val codeLine = sourceLine.trimEnd()
                keptLines += codeLine
                if (isCodeFence(codeLine)) {
                    inCodeBlock = false
                }
                continue
            }

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
            if (
                isPromptEcho(line, promptEcho) &&
                !naturalTextStarted &&
                !isStandaloneGreetingResponse(
                    line = line,
                    prompt = promptEcho,
                    rawWithoutTemplateTokens = withoutTemplateTokens,
                    removedTemplateTokenCount = removedTemplateTokenCount,
                )
            ) {
                removedPromptEcho = true
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
            if (isCodeFence(assistantText)) {
                keptLines += assistantText
                naturalTextStarted = true
                codeBlockDetected = true
                inCodeBlock = true
                continue
            }
            if (!naturalTextStarted && isLeadingNonJapaneseDrift(assistantText, promptEcho)) {
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

        if (inCodeBlock) {
            keptLines += "```"
            codeFenceCompleted = true
        }

        val sanitizedBeforeJapaneseSpaceNormalization = keptLines
            .joinToString("\n")
            .trim()
        val withoutLeadingPromptEcho = stripLeadingPromptEcho(
            value = sanitizedBeforeJapaneseSpaceNormalization,
            prompt = promptEcho,
        )
        if (withoutLeadingPromptEcho != sanitizedBeforeJapaneseSpaceNormalization) {
            removedPromptEcho = true
        }
        val sanitized = normalizeJapaneseInternalSpaces(withoutLeadingPromptEcho)
        val sanitizerApplied = sanitized != rawOutput ||
            removedTemplateTokenCount > 0 ||
            removedPromptEcho ||
            codeFenceCompleted
        return Result(
            rawOutput = rawOutput,
            sanitizedOutput = sanitized,
            sanitizerApplied = sanitizerApplied,
            removedTemplateTokenCount = removedTemplateTokenCount,
            removedPromptEcho = removedPromptEcho,
            codeBlockDetected = codeBlockDetected,
            codeFenceCompleted = codeFenceCompleted,
        )
    }

    private fun isCodeFence(line: String): Boolean =
        codeFencePattern.matches(line.trim())

    private fun stripLeadingPromptEcho(value: String, prompt: String): String {
        if (prompt.isEmpty() || value.isBlank()) return value
        val lines = value.lines()
        val firstMeaningfulIndex = lines.indexOfFirst { it.trim().isNotEmpty() }
        if (firstMeaningfulIndex < 0) return value
        val firstMeaningfulLine = lines[firstMeaningfulIndex].trim().trimStart('>').trim()
        if (!isPromptEcho(firstMeaningfulLine, prompt)) return value
        val remainingLines = lines.drop(firstMeaningfulIndex + 1)
        if (remainingLines.none { it.trim().isNotEmpty() }) return value
        return remainingLines
            .dropWhile { it.trim().isEmpty() }
            .joinToString("\n")
            .trim()
    }

    fun normalizeJapaneseInternalSpaces(value: String): String {
        if (' ' !in value) return value
        val normalized = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            if (
                codePoint == HALF_WIDTH_SPACE_CODE_POINT &&
                normalized.lastCodePointOrNull()?.isJapaneseTextCodePoint() == true &&
                value.nextCodePointOrNull(index + Character.charCount(codePoint))?.isJapaneseTextCodePoint() == true
            ) {
                index += Character.charCount(codePoint)
                continue
            }
            normalized.appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
        }
        return normalized.toString()
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

    private fun isStandaloneGreetingResponse(
        line: String,
        prompt: String,
        rawWithoutTemplateTokens: String,
        removedTemplateTokenCount: Int,
    ): Boolean {
        if (removedTemplateTokenCount > 0) return false
        if (line != prompt || prompt !in standaloneGreetingResponses) return false
        if (rawWithoutTemplateTokens.trim().trimStart('>').trim() != prompt) return false
        val meaningfulLines = rawWithoutTemplateTokens.lines()
            .map { it.trim().trimStart('>').trim() }
            .filter { it.isNotEmpty() && !roleLinePattern.matches(it) }
        return meaningfulLines.size == 1 && meaningfulLines.first() == prompt
    }

    private fun isLeadingNonJapaneseDrift(line: String, prompt: String): Boolean {
        if (!containsJapanese(prompt) || containsJapanese(line)) return false
        val normalized = line.trim().lowercase()
        val englishPrelude = normalized.startsWith("sure,") ||
            normalized.startsWith("sure.") ||
            normalized.startsWith("here is") ||
            normalized.startsWith("here's") ||
            normalized.startsWith("of course") ||
            normalized.startsWith("certainly") ||
            normalized.startsWith("the answer is")
        val multilingualDrift = line.any { char ->
            Character.isLetter(char) && !char.isLatinLetter()
        }
        return englishPrelude || multilingualDrift
    }

    private fun containsJapanese(value: String): Boolean =
        value.any { char ->
            val block = Character.UnicodeBlock.of(char)
            block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
        }

    private fun Char.isLatinLetter(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z'

    private fun StringBuilder.lastCodePointOrNull(): Int? {
        if (isEmpty()) return null
        return codePointBefore(length)
    }

    private fun String.nextCodePointOrNull(index: Int): Int? =
        if (index in indices) codePointAt(index) else null

    private fun Int.isJapaneseTextCodePoint(): Boolean =
        this in 0x3040..0x309F ||
            this in 0x30A0..0x30FF ||
            this in 0x3400..0x4DBF ||
            this in 0x4E00..0x9FFF ||
            this in 0xF900..0xFAFF

    private val standaloneGreetingResponses = setOf(
        "こんにちは",
        "こんにちは。",
        "こんばんは",
        "こんばんは。",
        "おはよう",
        "おはよう。",
        "ありがとう",
        "ありがとう。",
    )

    private const val HALF_WIDTH_SPACE_CODE_POINT = 0x0020
}
