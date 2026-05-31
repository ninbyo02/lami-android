package io.github.ninbyo02.lami.npu

internal object Qairt244OutputUnicodeDiagnostics {
    private val eosStopMarkers = listOf(
        "eos",
        "end_of_sequence",
        "end_of_text",
        "end_of_turn",
        "stop_token",
    )
    private val eosTextMarkers = listOf(
        "<eos>",
        "</s>",
        "<end_of_turn>",
        "<|endoftext|>",
        "<|eot_id|>",
    )

    fun buildFields(
        output: String,
        values: Map<String, String> = emptyMap(),
    ): List<Pair<String, String>> {
        val finishReason = values["finish_reason"].orEmpty()
        val stopReason = values["stop_reason"].orEmpty()
        val outputTokenCount = values["output_token_count"].orEmpty().ifBlank { "unavailable" }
        val codePoints = output.codePointValues()
        val replacementCount = codePoints.count { it == REPLACEMENT_CHAR_CODE_POINT }
        val controlCounts = codePoints
            .filter { Character.isISOControl(it) }
            .groupingBy { it }
            .eachCount()
        val circleCount = codePoints.count { it == WHITE_CIRCLE_CODE_POINT }
        val questionMarkCount = codePoints.count {
            it == QUESTION_MARK_CODE_POINT || it == FULLWIDTH_QUESTION_MARK_CODE_POINT
        }
        val qualityClassification = classifyQuality(output = output, codePoints = codePoints)
        val eosDetected = eosDetected(
            output = output,
            finishReason = finishReason,
            stopReason = stopReason,
        )

        return listOf(
            "output_token_count" to outputTokenCount,
            "finish_reason" to finishReason.ifBlank { "not_exposed_by_lower_level_entrypoint" },
            "stop_reason" to stopReason,
            "eos_detected" to eosDetected.toString(),
            "output_contains_replacement_chars" to (replacementCount > 0).toString(),
            "replacement_char_count" to replacementCount.toString(),
            "output_contains_control_chars" to controlCounts.isNotEmpty().toString(),
            "output_unicode_summary" to unicodeSummary(
                output = output,
                codePoints = codePoints,
                replacementCount = replacementCount,
                controlCounts = controlCounts,
                circleCount = circleCount,
                questionMarkCount = questionMarkCount,
            ),
            "quality_classification" to qualityClassification,
            "output_first_200_chars" to output.takeCodePoints(200),
            "output_last_200_chars" to output.takeLastCodePoints(200),
        )
    }

    fun buildFieldsFromExistingValues(values: Map<String, String>): List<Pair<String, String>> =
        buildFields(
            output = values["raw_native_output"].orEmpty()
                .ifBlank { values["adapter_output"].orEmpty() }
                .ifBlank { values["output"].orEmpty() },
            values = values,
        )

    fun toEscapedLines(
        fields: List<Pair<String, String>>,
        escapeValue: (String) -> String,
    ): List<String> = fields.map { (key, value) -> "$key=${escapeValue(value)}" }

    private fun eosDetected(
        output: String,
        finishReason: String,
        stopReason: String,
    ): Boolean {
        val finishStopText = "$finishReason $stopReason"
        return eosStopMarkers.any { marker -> finishStopText.contains(marker, ignoreCase = true) } ||
            eosTextMarkers.any { marker -> output.contains(marker, ignoreCase = true) }
    }

    private fun classifyQuality(output: String, codePoints: List<Int>): String {
        val trimmed = output.trim()
        val trimmedCodePoints = trimmed.codePointValues()
        if (trimmed.isEmpty()) return "empty_output"
        if (trimmedCodePoints.size == 1 && trimmedCodePoints.first().isQuestionMark()) {
            return "single_question_mark"
        }

        val circleCount = codePoints.count { it == WHITE_CIRCLE_CODE_POINT }
        if (circleCount >= 3 && circleCount * 5 >= codePoints.size * 4) {
            return "repetitive_circles"
        }

        if (containsTemplateArtifact(output)) return "template_artifact"

        val japaneseSpecificCount = codePoints.count { it.isJapaneseSpecificCodePoint() }
        val japaneseTextCount = codePoints.count { it.isJapaneseTextCodePoint() }
        if (japaneseSpecificCount == 0 || japaneseTextCount == 0) return "mixed_language"
        if (codePoints.any { it.isNonJapaneseScriptCodePoint() }) return "mixed_language"

        val latinWords = LATIN_WORD_PATTERN.findAll(trimmed).map { it.value }.toList()
        if (latinWords.any { !it.isAllowedJapaneseInlineLatinTerm() }) return "mixed_language"

        return "natural_japanese"
    }

    private fun containsTemplateArtifact(output: String): Boolean {
        val lower = output.lowercase()
        return listOf(
            "<|im_start|>",
            "<|im_end|>",
            "<|start_header_id|>",
            "<|end_header_id|>",
            "<start_of_turn>",
            "<end_of_turn>",
            "[inst]",
            "[/inst]",
            "### system",
            "### user",
            "### assistant",
            "system:",
            "user:",
            "assistant:",
        ).any { lower.contains(it) }
    }

    private fun unicodeSummary(
        output: String,
        codePoints: List<Int>,
        replacementCount: Int,
        controlCounts: Map<Int, Int>,
        circleCount: Int,
        questionMarkCount: Int,
    ): String {
        val firstCodePoints = codePoints.take(32).joinToString(" ") { codePointLabel(it) }
        val controlSummary = controlCounts.entries
            .sortedBy { it.key }
            .joinToString(",") { "${codePointLabel(it.key)}x${it.value}" }
            .ifBlank { "none" }
        val classification = when {
            output.isEmpty() -> "empty_output"
            codePoints.size == 1 && (codePoints.first() == QUESTION_MARK_CODE_POINT ||
                codePoints.first() == FULLWIDTH_QUESTION_MARK_CODE_POINT) -> "single_question_mark_output"
            codePoints.all { it == WHITE_CIRCLE_CODE_POINT } -> "white_circle_code_points"
            replacementCount > 0 -> "contains_unicode_replacement_char"
            else -> "unicode_decoded_string"
        }
        return listOf(
            "utf16_length=${output.length}",
            "code_point_count=${codePoints.size}",
            "utf8_byte_count=${output.toByteArray(Charsets.UTF_8).size}",
            "classification=$classification",
            "replacement_char_count=$replacementCount",
            "control_chars=$controlSummary",
            "white_circle_u3007_count=$circleCount",
            "question_mark_count=$questionMarkCount",
            "first_code_points=${firstCodePoints.ifBlank { "none" }}",
        ).joinToString(";")
    }

    private fun String.codePointValues(): List<Int> {
        val result = mutableListOf<Int>()
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            result += codePoint
            index += Character.charCount(codePoint)
        }
        return result
    }

    private fun String.takeCodePoints(maxCodePoints: Int): String {
        if (maxCodePoints <= 0 || isEmpty()) return ""
        var index = 0
        var count = 0
        while (index < length && count < maxCodePoints) {
            val codePoint = codePointAt(index)
            index += Character.charCount(codePoint)
            count += 1
        }
        return substring(0, index)
    }

    private fun String.takeLastCodePoints(maxCodePoints: Int): String {
        if (maxCodePoints <= 0 || isEmpty()) return ""
        var index = length
        var count = 0
        while (index > 0 && count < maxCodePoints) {
            val codePoint = codePointBefore(index)
            index -= Character.charCount(codePoint)
            count += 1
        }
        return substring(index)
    }

    private fun codePointLabel(codePoint: Int): String =
        "U+${codePoint.toString(16).uppercase().padStart(4, '0')}"

    private fun Int.isQuestionMark(): Boolean =
        this == QUESTION_MARK_CODE_POINT || this == FULLWIDTH_QUESTION_MARK_CODE_POINT

    private fun Int.isJapaneseTextCodePoint(): Boolean =
        isJapaneseSpecificCodePoint() || isCjkUnifiedIdeograph()

    private fun Int.isJapaneseSpecificCodePoint(): Boolean =
        this in 0x3040..0x309F ||
            this in 0x30A0..0x30FF

    private fun Int.isCjkUnifiedIdeograph(): Boolean =
        this in 0x3400..0x4DBF ||
            this in 0x4E00..0x9FFF ||
            this in 0xF900..0xFAFF

    private fun Int.isNonJapaneseScriptCodePoint(): Boolean =
        this in 0x0900..0x097F ||
            this in 0xAC00..0xD7AF ||
            this in 0x1100..0x11FF ||
            this in 0x3130..0x318F

    private fun String.isAllowedJapaneseInlineLatinTerm(): Boolean {
        val normalized = trim('.', ',', ':', ';', '!', '?', '-', '_').lowercase()
        if (normalized.isBlank()) return true
        return normalized in allowedJapaneseInlineLatinTerms ||
            (all { it.isUpperCase() || it.isDigit() } && length in 2..4)
    }

    private const val REPLACEMENT_CHAR_CODE_POINT = 0xFFFD
    private const val WHITE_CIRCLE_CODE_POINT = 0x3007
    private const val QUESTION_MARK_CODE_POINT = 0x003F
    private const val FULLWIDTH_QUESTION_MARK_CODE_POINT = 0xFF1F
    private val LATIN_WORD_PATTERN = Regex("[A-Za-z][A-Za-z0-9+#._-]*")
    private val allowedJapaneseInlineLatinTerms = setOf(
        "ai",
        "android",
        "api",
        "cpu",
        "db",
        "gpu",
        "java",
        "javascript",
        "kotlin",
        "npu",
        "python",
        "qairt",
        "qnn",
        "sql",
        "ui",
    )
}
