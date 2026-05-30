package io.github.ninbyo02.lami.ui.screens.home

import java.security.MessageDigest

internal fun interface NpuStandardRouteS1Provider {
    fun invoke(
        userPrompt: String,
        trace: (String) -> Unit,
    ): NpuStandardRouteS1RawResult
}

internal fun buildNpuRealPromptHandoffTrace(
    stage: String,
    userPrompt: String,
): String = buildString {
    append("NPU_REAL_PROMPT ")
    append(stage)
    append("_prompt_hash=")
    append(npuRealPromptHash(userPrompt))
    append(" ")
    append(stage)
    append("_prompt_length=")
    append(userPrompt.length)
    append(" ")
    append(stage)
    append("_prompt_code_points=")
    append(userPrompt.codePointCount(0, userPrompt.length))
    append(" ")
    append(stage)
    append("_prompt_preview=")
    append(npuRealPromptPreview(userPrompt))
}

internal fun buildNpuRealPromptResultTrace(
    status: String,
    reason: String,
    rawOutput: String,
    sanitizedOutput: String,
    qualityClassification: String,
    runDecodeReached: Boolean,
    fallbackUsed: Boolean,
    timeout: Boolean,
    freshCrash: Boolean,
): String = buildString {
    append("NPU_REAL_PROMPT status=")
    append(status)
    append(" reason=")
    append(reason)
    append(" raw_output_hash=")
    append(npuRealPromptHash(rawOutput))
    append(" raw_output_length=")
    append(rawOutput.length)
    append(" raw_output_preview=")
    append(npuRealPromptPreview(rawOutput))
    append(" sanitized_output_hash=")
    append(npuRealPromptHash(sanitizedOutput))
    append(" sanitized_output_length=")
    append(sanitizedOutput.length)
    append(" sanitized_output_preview=")
    append(npuRealPromptPreview(sanitizedOutput))
    append(" quality_classification=")
    append(qualityClassification)
    append(" run_decode_reached=")
    append(runDecodeReached)
    append(" fallback_used=")
    append(fallbackUsed)
    append(" timeout=")
    append(timeout)
    append(" fresh_crash=")
    append(freshCrash)
}

internal fun npuRealPromptHash(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }.take(12)
}

internal fun npuRealPromptPreview(text: String): String =
    text.map { char -> if (char.isWhitespace()) ' ' else char }
        .joinToString(separator = "")
        .trim()
        .take(12)
        .ifBlank { "-" }
