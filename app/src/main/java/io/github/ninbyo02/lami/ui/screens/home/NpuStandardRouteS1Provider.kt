package io.github.ninbyo02.lami.ui.screens.home

import java.security.MessageDigest

internal fun interface NpuStandardRouteS1Provider {
    fun invoke(
        userPrompt: String,
        maxOutputTokens: Int,
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
    maxOutputTokens: Int,
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
    append(" max_output_tokens=")
    append(maxOutputTokens)
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

internal fun buildNpuStandardRouteS1DevTraceText(
    input: String,
    result: NpuStandardRouteS1Result,
    maxOutputTokens: Int = result.selection.effectiveMaxOutputTokens,
): String = listOf(
    "max_output_tokens=$maxOutputTokens",
    "input_hash=${npuRealPromptHash(input)}",
    "input_prompt=${npuStandardRouteS1DevPreview(input)}",
    "input_preview=${npuStandardRouteS1DevPreview(input)}",
    "input_length=${input.length}",
    "input_code_points=${input.codePointCount(0, input.length)}",
    "raw_output_hash=${npuRealPromptHash(result.rawOutput)}",
    "raw_output_preview=${npuStandardRouteS1DevPreview(result.rawOutput)}",
    "raw_output_length=${result.rawOutput.length}",
    "raw_output_code_points=${result.rawOutput.codePointCount(0, result.rawOutput.length)}",
    "sanitized_output_hash=${npuRealPromptHash(result.sanitizedOutput)}",
    "sanitized_output_preview=${npuStandardRouteS1DevPreview(result.sanitizedOutput)}",
    "sanitized_output_length=${result.sanitizedOutput.length}",
    "sanitized_output_code_points=${result.sanitizedOutput.codePointCount(0, result.sanitizedOutput.length)}",
    "status=${result.status}",
    "reason=${result.reason}",
    "quality_classification=${result.qualityClassification}",
    "run_decode_reached=${result.runDecodeReached}",
    "timeout=${result.timeout}",
    "fallback=${result.fallbackUsed}",
    "fresh_crash=${result.freshCrash}",
).joinToString("\n")

internal fun buildNpuStandardRouteS1DiagnosticCopyText(
    input: String,
    result: NpuStandardRouteS1Result,
    maxOutputTokens: Int = result.selection.effectiveMaxOutputTokens,
): String = listOf(
    "input_prompt=${npuStandardRouteS1EscapeCopyValue(input)}",
    "max_output_tokens=$maxOutputTokens",
    "raw_output=${npuStandardRouteS1EscapeCopyValue(result.rawOutput)}",
    "sanitized_output=${npuStandardRouteS1EscapeCopyValue(result.sanitizedOutput)}",
    "status=${result.status}",
    "reason=${result.reason}",
    "quality_classification=${result.qualityClassification}",
    "run_decode_reached=${result.runDecodeReached}",
    "timeout=${result.timeout}",
    "fallback=${result.fallbackUsed}",
    "fresh_crash=${result.freshCrash}",
).joinToString("\n")

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

internal fun npuStandardRouteS1DevPreview(text: String): String {
    val normalized = text.map { char -> if (char.isWhitespace()) ' ' else char }
        .joinToString(separator = "")
        .trim()
    if (normalized.isBlank()) return "-"
    return if (normalized.length <= NPU_STANDARD_ROUTE_S1_DEV_PREVIEW_LIMIT) {
        normalized
    } else {
        normalized.take(NPU_STANDARD_ROUTE_S1_DEV_PREVIEW_LIMIT) + "..."
    }
}

internal fun npuStandardRouteS1EscapeCopyValue(text: String): String =
    text.replace("\\", "\\\\").replace("\n", "\\n")

private const val NPU_STANDARD_ROUTE_S1_DEV_PREVIEW_LIMIT = 32
