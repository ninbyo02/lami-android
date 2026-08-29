package io.github.ninbyo02.lami.npu

import io.github.ninbyo02.lami.ui.screens.home.NpuS1NativeStageDiagnostics
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1Contract

internal data class NpuStandardRouteNativeRequest(
    val userPrompt: String,
    val contextText: String = "",
    val selectedModelFile: String? = null,
    val unsafeDevBypassPromptLengthGate: Boolean = true,
    val maxOutputTokens: Int,
    val promptTailVariant: String = NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT,
    val timeoutMs: Long = NpuStandardRouteNativeContract.TIMEOUT_MS,
)

internal data class NpuStandardRouteNativeDisplay(
    val text: String,
    val output: String,
    val status: String,
    val reason: String,
    val nativeReached: Boolean,
    val decodeReached: Boolean,
    val npuEvidence: String,
    val fallback: Boolean,
    val freshCrash: Boolean,
    val timeout: Boolean,
    val requestedMaxOutputTokens: Int,
    val effectiveMaxOutputTokens: Int,
    val nativeMaxOutputTokensLimit: String,
    val rawLen: Int,
    val sanitizedLen: Int,
    val quality: String,
    val controlCharSummary: String,
    val rawOutputFirst200Chars: String,
    val rawOutputLast200Chars: String,
    val rawUnicodeSummary: String,
    val sanitizerApplied: String,
    val removedTemplateTokenCount: String,
    val removedPromptEcho: String,
    val replacementCharCount: String,
    val outputContainsControlChars: String,
    val rawOutput: String = "",
    val stopReason: String = "",
    val finishReason: String = "",
    val eosDetected: String = "",
    val outputTokenCount: String = "",
    val promptTokenCount: String = "",
    val prefillMs: Long? = null,
    val nativeDecodeMs: Long? = null,
    val nativeDiagnostics: NpuS1NativeStageDiagnostics = NpuS1NativeStageDiagnostics(),
)

internal object NpuStandardRouteNativeContract {
    const val TIMEOUT_MS = 60_000L
    private const val JAPANESE_ONLY_TAIL_INSTRUCTION = "必ず日本語だけで短く返答してください。"

    fun buildPrompt(
        contextText: String,
        userPrompt: String,
        promptTailVariant: String = NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT,
    ): String {
        require(promptTailVariant == NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT) {
            "unsupported_npu_prompt_tail_variant:$promptTailVariant"
        }
        val normalizedContext = contextText.trim()
        val head = if (normalizedContext.isBlank()) "" else "$normalizedContext\n\n"
        return "$head$JAPANESE_ONLY_TAIL_INSTRUCTION\n" +
            "ユーザー: ${userPrompt.trim()}\n" +
            "アシスタント:"
    }
}
