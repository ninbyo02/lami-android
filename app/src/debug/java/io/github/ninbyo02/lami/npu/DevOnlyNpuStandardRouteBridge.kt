package io.github.ninbyo02.lami.npu

internal fun DevOnlyNpuOneTurnConversationRequest.toStandardRouteNativeRequest():
    NpuStandardRouteNativeRequest = NpuStandardRouteNativeRequest(
    userPrompt = userPrompt,
    contextText = contextText,
    unsafeDevBypassPromptLengthGate = unsafeDevBypassPromptLengthGate,
    maxOutputTokens = maxOutputTokens,
    promptTailVariant = promptTailVariant,
    timeoutMs = timeoutMs,
)

internal fun NpuStandardRouteNativeDisplay.toDevOnlyConversationDisplay():
    DevOnlyNpuOneTurnConversationDisplay = DevOnlyNpuOneTurnConversationDisplay(
    text = text,
    output = output,
    status = status,
    reason = reason,
    nativeReached = nativeReached,
    decodeReached = decodeReached,
    npuEvidence = npuEvidence,
    fallback = fallback,
    freshCrash = freshCrash,
    timeout = timeout,
    requestedMaxOutputTokens = requestedMaxOutputTokens,
    effectiveMaxOutputTokens = effectiveMaxOutputTokens,
    nativeMaxOutputTokensLimit = nativeMaxOutputTokensLimit,
    rawLen = rawLen,
    sanitizedLen = sanitizedLen,
    quality = quality,
    controlCharSummary = controlCharSummary,
    rawOutputFirst200Chars = rawOutputFirst200Chars,
    rawOutputLast200Chars = rawOutputLast200Chars,
    rawUnicodeSummary = rawUnicodeSummary,
    sanitizerApplied = sanitizerApplied,
    removedTemplateTokenCount = removedTemplateTokenCount,
    removedPromptEcho = removedPromptEcho,
    replacementCharCount = replacementCharCount,
    outputContainsControlChars = outputContainsControlChars,
    rawOutput = rawOutput,
    stopReason = stopReason,
    finishReason = finishReason,
    eosDetected = eosDetected,
    outputTokenCount = outputTokenCount,
    promptTokenCount = promptTokenCount,
    prefillMs = prefillMs,
    nativeDecodeMs = nativeDecodeMs,
    nativeDiagnostics = nativeDiagnostics,
)
