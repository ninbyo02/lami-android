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
            "output_contains_control_chars" to controlCounts.isNotEmpty().toString(),
            "output_unicode_summary" to unicodeSummary(
                output = output,
                codePoints = codePoints,
                replacementCount = replacementCount,
                controlCounts = controlCounts,
                circleCount = circleCount,
                questionMarkCount = questionMarkCount,
            ),
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

    private const val REPLACEMENT_CHAR_CODE_POINT = 0xFFFD
    private const val WHITE_CIRCLE_CODE_POINT = 0x3007
    private const val QUESTION_MARK_CODE_POINT = 0x003F
    private const val FULLWIDTH_QUESTION_MARK_CODE_POINT = 0xFF1F
}
