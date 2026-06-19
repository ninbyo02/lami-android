package io.github.ninbyo02.lami.npu

import java.security.MessageDigest

object DevOnlyNpuOneTurnConversationMatrix {
    val prompts: List<String> = listOf(
        "こんばんは",
        "こんばんは。",
        "こんばんわ",
        "こんばんは！",
        "こんばんは？",
        "こんにちは",
        "おはよう",
        "ありがとう",
        "ハロー",
        "あ",
        "q",
        "え",
    )

    suspend fun run(
        entry: DevOnlyNpuOneTurnConversationEntry,
        baseRequest: DevOnlyNpuOneTurnConversationRequest,
    ): String {
        val rows = prompts.mapIndexed { index, prompt ->
            val request = baseRequest.copy(userPrompt = prompt)
            val display = entry.run(request)
            buildRow(
                index = index + 1,
                request = request,
                display = display,
            )
        }
        return buildHeader(baseRequest = baseRequest, status = "success")
            .plus(rows.flatten())
            .joinToString(separator = "\n", postfix = "\n")
    }

    fun buildRow(
        index: Int,
        request: DevOnlyNpuOneTurnConversationRequest,
        display: DevOnlyNpuOneTurnConversationDisplay,
    ): List<String> {
        val requestPrompt = DevOnlyNpuOneTurnConversationContract.buildRawDialogTailPrompt(
            contextText = request.contextText,
            userPrompt = request.userPrompt,
            promptTailVariant = request.promptTailVariant,
        )
        val rawOutput = display.rawOutput.ifBlank { display.rawOutputFirst200Chars }
        return listOf(
            "case_index=$index",
            "input_hash=${hash(request.userPrompt)}",
            "input_length=${request.userPrompt.length}",
            "input_code_points=${codePoints(request.userPrompt)}",
            "input_preview=${preview(request.userPrompt)}",
            "request_prompt_hash=${hash(requestPrompt)}",
            "request_prompt_length=${requestPrompt.length}",
            "request_prompt_code_points=${codePoints(requestPrompt)}",
            "request_prompt_preview=${preview(requestPrompt)}",
            "raw_output_hash=${hash(rawOutput)}",
            "raw_output_length=${display.rawLen}",
            "raw_output_code_points=${codePoints(rawOutput)}",
            "raw_output_preview=${preview(rawOutput)}",
            "sanitized_output_hash=${hash(display.output)}",
            "sanitized_output_length=${display.sanitizedLen}",
            "sanitized_output_code_points=${codePoints(display.output)}",
            "sanitized_output_preview=${preview(display.output)}",
            "quality_classification=${display.quality}",
            "reason=${display.reason}",
            "run_decode_reached=${display.decodeReached}",
            "timeout=${display.timeout}",
            "fallback=${display.fallback}",
            "fresh_crash=${display.freshCrash}",
            "stop_reason=${display.stopReason.ifBlank { "unknown" }}",
            "finish_reason=${display.finishReason.ifBlank { "unknown" }}",
            "eos_detected=${display.eosDetected.ifBlank { "unknown" }}",
            "output_token_count=${display.outputTokenCount.ifBlank { "unavailable" }}",
            "prompt_token_count=${display.promptTokenCount.ifBlank { "unavailable" }}",
            "case_end=true",
        )
    }

    fun buildHeader(
        baseRequest: DevOnlyNpuOneTurnConversationRequest,
        status: String,
    ): List<String> = listOf(
        "DEV ONLY NPU ONE TURN MATRIX",
        "status=$status",
        "prompt_count=${prompts.size}",
        "max_output_tokens=${baseRequest.maxOutputTokens}",
        "prompt_tail_variant=${baseRequest.promptTailVariant}",
        "raw_dialog_tail_template=unchanged",
        "prompt_and_output_policy=hash_length_code_points_preview_only",
    )

    fun failureText(
        reason: String,
        throwable: Throwable,
        baseRequest: DevOnlyNpuOneTurnConversationRequest,
    ): String = buildHeader(baseRequest = baseRequest, status = "failure")
        .plus(
            listOf(
                "reason=$reason:${throwable.javaClass.simpleName}",
                "message_preview=${preview(throwable.message.orEmpty())}",
            ),
        )
        .joinToString(separator = "\n", postfix = "\n")

    private fun codePoints(value: String): Int = value.codePointCount(0, value.length)

    private fun preview(value: String): String {
        val normalized = value.map { char -> if (char.isWhitespace()) ' ' else char }
            .joinToString(separator = "")
            .trim()
        if (normalized.isBlank()) return "-"
        return if (normalized.length <= PREVIEW_LIMIT) {
            normalized
        } else {
            normalized.take(PREVIEW_LIMIT) + "..."
        }
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }.take(12)
    }

    private const val PREVIEW_LIMIT = 32
}
