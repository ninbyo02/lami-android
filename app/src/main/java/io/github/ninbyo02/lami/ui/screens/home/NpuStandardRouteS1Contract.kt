package io.github.ninbyo02.lami.ui.screens.home

internal data class NpuStandardRouteS1SideEffects(
    val db: Boolean = false,
    val tts: Boolean = false,
    val markdown: Boolean = false,
    val streaming: Boolean = false,
    val backendNpuPersisted: Boolean = false,
    val conversationHistorySaved: Boolean = false,
) {
    val allDisconnected: Boolean
        get() = !db &&
            !tts &&
            !markdown &&
            !streaming &&
            !backendNpuPersisted &&
            !conversationHistorySaved
}

internal data class NpuStandardRouteS1Selection(
    val enabled: Boolean = false,
    val routeType: String = NpuStandardRouteS1Contract.ROUTE_TYPE,
    val promptTailVariant: String = NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT,
    val requestedMaxOutputTokens: Int = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS,
    val effectiveMaxOutputTokens: Int = requestedMaxOutputTokens,
    val sideEffects: NpuStandardRouteS1SideEffects = NpuStandardRouteS1SideEffects(),
) {
    val selectable: Boolean
        get() = enabled &&
            routeType == NpuStandardRouteS1Contract.ROUTE_TYPE &&
            promptTailVariant == NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT &&
            requestedMaxOutputTokens == NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS &&
            effectiveMaxOutputTokens == NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS &&
            sideEffects.allDisconnected
}

internal data class NpuStandardRouteS1Result(
    val selection: NpuStandardRouteS1Selection = NpuStandardRouteS1Selection(enabled = true),
    val status: String,
    val reason: String,
    val rawOutput: String,
    val sanitizedOutput: String,
    val qualityClassification: String,
    val runDecodeReached: Boolean,
    val npuBackendEvidence: String,
    val fallbackUsed: Boolean,
    val timeout: Boolean,
    val freshCrash: Boolean,
    val displayText: String = NpuStandardRouteS1Contract.displayText(
        selection = selection,
        status = status,
        reason = reason,
        rawOutput = rawOutput,
        sanitizedOutput = sanitizedOutput,
        qualityClassification = qualityClassification,
        runDecodeReached = runDecodeReached,
        npuBackendEvidence = npuBackendEvidence,
        fallbackUsed = fallbackUsed,
        timeout = timeout,
        freshCrash = freshCrash,
    ),
) {
    val successCriteriaMet: Boolean
        get() = selection.selectable &&
            status == NpuStandardRouteS1Contract.STATUS_SUCCESS &&
            reason == NpuStandardRouteS1Contract.REASON_SUCCESS &&
            runDecodeReached &&
            npuBackendEvidence == NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE &&
            !fallbackUsed &&
            !timeout &&
            !freshCrash &&
            sanitizedOutput.isNotBlank() &&
            qualityClassification == NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE
}

internal object NpuStandardRouteS1Contract {
    const val ROUTE_TYPE = "standard_chat_screen_s1_npu_display_only"
    const val PROMPT_TAIL_VARIANT = "raw_dialog_tail_variant_b"
    const val MAX_OUTPUT_TOKENS = 32
    const val NPU_BACKEND_EVIDENCE = "QNN_HTP_V79_FastRPC_native_diag"
    const val QUALITY_NATURAL_JAPANESE = "natural_japanese"
    const val STATUS_SUCCESS = "success"
    const val REASON_SUCCESS = "success"

    fun displayText(
        selection: NpuStandardRouteS1Selection,
        status: String,
        reason: String,
        rawOutput: String,
        sanitizedOutput: String,
        qualityClassification: String,
        runDecodeReached: Boolean,
        npuBackendEvidence: String,
        fallbackUsed: Boolean,
        timeout: Boolean,
        freshCrash: Boolean,
    ): String {
        val sideEffects = selection.sideEffects
        return listOf(
            "NPU STANDARD ROUTE S1",
            "route_type=${selection.routeType}",
            "standard_route_connected=true",
            "status=$status",
            "reason=$reason",
            "prompt_tail_variant=${selection.promptTailVariant}",
            "requested_max_output_tokens=${selection.requestedMaxOutputTokens}",
            "effective_max_output_tokens=${selection.effectiveMaxOutputTokens}",
            "max_output_tokens=${selection.effectiveMaxOutputTokens}",
            "run_decode_reached=$runDecodeReached",
            "npu_backend_evidence=$npuBackendEvidence",
            "fallback_used=$fallbackUsed",
            "timeout=$timeout",
            "fresh_crash=$freshCrash",
            "raw_output=$rawOutput",
            "sanitized_output=$sanitizedOutput",
            "quality_classification=$qualityClassification",
            "db=${sideEffects.db}",
            "tts=${sideEffects.tts}",
            "markdown=${sideEffects.markdown}",
            "streaming=${sideEffects.streaming}",
            "backend_npu_persisted=${sideEffects.backendNpuPersisted}",
            "conversation_history_saved=${sideEffects.conversationHistorySaved}",
        ).joinToString("\n")
    }
}
