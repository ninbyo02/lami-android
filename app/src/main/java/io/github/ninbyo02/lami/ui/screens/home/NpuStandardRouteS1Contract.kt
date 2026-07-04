package io.github.ninbyo02.lami.ui.screens.home

import java.util.Locale

internal data class NpuStandardRouteS1Timing(
    val totalMs: Long? = null,
    val decodeMs: Long? = null,
    val ttftMs: Long? = null,
    val outputTokens: Int? = null,
    val tokenCountMode: String = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_UNAVAILABLE,
    val tokensPerSecond: Double? = null,
) {
    val hasAnyValue: Boolean
        get() = totalMs != null ||
            decodeMs != null ||
            ttftMs != null ||
            outputTokens != null ||
            tokensPerSecond != null ||
            tokenCountMode != NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_UNAVAILABLE
}

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

internal data class NpuStandardRouteS1PromptRewrite(
    val originalPrompt: String,
    val finalPromptText: String,
    val arithmeticPromptDetected: Boolean,
    val shortPromptRewriteApplied: Boolean,
    val rewrittenPromptText: String,
    val promptWrapperUsed: String = NpuStandardRouteS1Contract.PROMPT_WRAPPER_USED,
    val selectedPromptProfile: String = NpuStandardRouteS1Contract.PROMPT_WRAPPER_USED,
)

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
            requestedMaxOutputTokens == NpuStandardRoutePreferences.sanitizeMaxOutputTokens(requestedMaxOutputTokens) &&
            effectiveMaxOutputTokens == NpuStandardRoutePreferences.sanitizeMaxOutputTokens(effectiveMaxOutputTokens) &&
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
    val selectedModelName: String = "",
    val selectedModelFile: String = "",
    val npuModelEligible: Boolean? = null,
    val timing: NpuStandardRouteS1Timing = NpuStandardRouteS1Timing(),
    val s2DbReason: String = "",
    val s5TtsDiagnostics: NpuStandardRouteS5TtsDiagnostics? = null,
    val nativeDiagnostics: NpuS1NativeStageDiagnostics = NpuS1NativeStageDiagnostics(),
    val inputPrompt: String = "",
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
        selectedModelName = selectedModelName,
        selectedModelFile = selectedModelFile,
        npuModelEligible = npuModelEligible,
        timing = timing,
        s2DbReason = s2DbReason,
        s5TtsDiagnostics = s5TtsDiagnostics,
        nativeDiagnostics = nativeDiagnostics,
        inputPrompt = inputPrompt,
    ),
) {
    val outputQualityCandidate: NpuS1PersistentCustomJniQualityCandidateResult
        get() = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = rawOutput,
            sanitizedOutput = sanitizedOutput,
            inputPrompt = inputPrompt,
        )

    val outputQualityCandidateStatus: String
        get() = outputQualityCandidate.status

    val outputQualityCandidateReason: String
        get() = outputQualityCandidate.reason

    val preparedOutput: String
        get() = stripLeadingPromptEchoForDisplay(outputQualityCandidate.preparedOutput)

    val usableDisplayOutput: String
        get() = preparedOutput
            .ifBlank { stripLeadingPromptEchoForDisplay(sanitizedOutput) }
            .ifBlank { stripLeadingPromptEchoForDisplay(rawOutput.trim()) }

    val actualDisplayText: String
        get() = stripLeadingPromptEchoForDisplay(usableDisplayOutput)

    val ttsText: String
        get() = NpuStandardRouteS1Contract.ttsTextForOutput(
            userPrompt = inputPrompt,
            actualDisplayText = actualDisplayText,
        )

    private fun stripLeadingPromptEchoForDisplay(text: String): String {
        val prompt = inputPrompt.trim()
        if (prompt.isBlank() || text.isBlank()) return text
        val normalizedText = text
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val lines = normalizedText.lines()
        val firstMeaningfulIndex = lines.indexOfFirst { it.trim().isNotEmpty() }
        if (firstMeaningfulIndex < 0) return normalizedText
        val firstMeaningfulLine = lines[firstMeaningfulIndex].trim().trimStart('>').trim()
        if (firstMeaningfulLine != prompt) return normalizedText
        val remainingLines = lines.drop(firstMeaningfulIndex + 1)
        if (remainingLines.none { it.trim().isNotEmpty() }) return normalizedText
        return remainingLines
            .dropWhile { it.trim().isEmpty() }
            .joinToString("\n")
            .trim()
    }

    val successCriteriaMet: Boolean
        get() {
            val candidatePassed = outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS
            return selection.selectable &&
                status == NpuStandardRouteS1Contract.STATUS_SUCCESS &&
                reason == NpuStandardRouteS1Contract.REASON_SUCCESS &&
                runDecodeReached &&
                npuBackendEvidence == NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE &&
                !fallbackUsed &&
                !timeout &&
                !freshCrash &&
                displayText.isNotBlank() &&
                usableDisplayOutput.isNotBlank() &&
                outputQualityCandidateStatus != NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL &&
                (
                    qualityClassification == NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE ||
                        candidatePassed
                    )
        }

    fun withTiming(timing: NpuStandardRouteS1Timing): NpuStandardRouteS1Result =
        copy(
            timing = timing,
            displayText = NpuStandardRouteS1Contract.displayText(
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
                selectedModelName = selectedModelName,
                selectedModelFile = selectedModelFile,
                npuModelEligible = npuModelEligible,
                timing = timing,
                s2DbReason = s2DbReason,
                s5TtsDiagnostics = s5TtsDiagnostics,
                nativeDiagnostics = nativeDiagnostics,
                inputPrompt = inputPrompt,
            ),
        )
}

internal object NpuStandardRouteS1Contract {
    const val ROUTE_TYPE = "standard_chat_screen_s1_npu_display_only"
    const val ROUTE_TYPE_S2_DB_SAVE = "standard_chat_screen_s2_npu_db_save"
    const val ROUTE_TYPE_S3_MARKDOWN = "standard_chat_screen_s3_markdown"
    const val PROMPT_TAIL_VARIANT = "gemma_it_user_model"
    const val PROMPT_WRAPPER_USED = "gemma_it_user_model"
    const val MAX_OUTPUT_TOKENS = 32
    const val NPU_BACKEND_EVIDENCE = "QNN_HTP_V79_FastRPC_native_diag"
    const val QUALITY_NATURAL_JAPANESE = "natural_japanese"
    const val QUALITY_MIXED_LANGUAGE = "mixed_language"
    const val QUALITY_QUESTION_ECHO = "question_echo"
    const val QUALITY_ASSISTANT_STUB = "assistant_stub"
    const val QUALITY_ROLE_CONTAMINATION = "role_contamination"
    const val QUALITY_TEMPLATE_ARTIFACT = "template_artifact"
    const val STATUS_SUCCESS = "success"
    const val STATUS_BLOCKED = "blocked"
    const val REASON_SUCCESS = "success"
    const val REASON_COMPLETED_ROUTE_KILL_SWITCH_DISABLED = "kill_switch_disabled"
    const val NPU_BACKEND_EVIDENCE_COMPLETED_ROUTE_KILL_SWITCH_BLOCKED =
        "NPU_completed_route_kill_switch_blocked"
    const val REASON_EMPTY_AFTER_SANITIZE = "empty_after_sanitize"
    const val REASON_MIXED_LANGUAGE = "mixed_language"
    const val REASON_QUESTION_ECHO = "question_echo"
    const val REASON_ASSISTANT_STUB = "assistant_stub"
    const val REASON_RAW_ROLE_CONTAMINATION = "raw_role_contamination"
    const val REASON_MODEL_NOT_NPU_COMPATIBLE = "model_not_npu_compatible"
    const val FALLBACK_SAFE_GREETING = "safe_greeting_fallback"
    const val TOKEN_COUNT_MODE_UNAVAILABLE = "unavailable"
    const val TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS = "estimated_code_points"
    const val TOKEN_COUNT_MODE_NATIVE_REPORTED = "native_reported"
    const val MODEL_NOT_NPU_COMPATIBLE_MESSAGE =
        "このモデルはNPU専用モデルではありません。NPU検証には Qualcomm / sm8750 版のモデルを選択してください。Generic版はCPU/GPU経路で実行してください。"

    fun rewritePromptForNative(userPrompt: String): NpuStandardRouteS1PromptRewrite {
        val normalizedPrompt = userPrompt.trim()
        val arithmeticPromptDetected = isShortArithmeticPrompt(normalizedPrompt)
        val rewrittenPrompt = if (arithmeticPromptDetected) {
            "次の計算に日本語で答えてください。答えだけ簡潔に書いてください。\n" +
                "問題: $normalizedPrompt\n" +
                "答え:"
        } else {
            normalizedPrompt
        }
        return NpuStandardRouteS1PromptRewrite(
            originalPrompt = normalizedPrompt,
            finalPromptText = "<start_of_turn>user\n$rewrittenPrompt<end_of_turn>\n<start_of_turn>model",
            arithmeticPromptDetected = arithmeticPromptDetected,
            shortPromptRewriteApplied = arithmeticPromptDetected,
            rewrittenPromptText = rewrittenPrompt,
        )
    }

    fun buildPromptWrapperText(userPrompt: String): String =
        rewritePromptForNative(userPrompt).finalPromptText

    fun finalPromptTail(userPrompt: String): String =
        buildPromptWrapperText(userPrompt).takeLast(200)

    fun ttsTextForOutput(
        userPrompt: String,
        actualDisplayText: String,
    ): String {
        val normalizedDisplayText = actualDisplayText.trim()
        return if (
            isShortArithmeticPrompt(userPrompt.trim()) &&
            normalizedDisplayText in setOf("2", "２")
        ) {
            "${normalizedDisplayText}です。"
        } else {
            actualDisplayText
        }
    }

    private fun isShortArithmeticPrompt(prompt: String): Boolean =
        normalizeArithmeticPrompt(prompt) in setOf(
            "1+1",
            "1+1は",
            "1+1は?",
        )

    private fun normalizeArithmeticPrompt(prompt: String): String =
        prompt
            .filterNot { it.isWhitespace() }
            .replace('１', '1')
            .replace('＋', '+')
            .replace('？', '?')

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
        selectedModelName: String = "",
        selectedModelFile: String = "",
        npuModelEligible: Boolean? = null,
        timing: NpuStandardRouteS1Timing = NpuStandardRouteS1Timing(),
        s2DbReason: String = "",
        s5TtsDiagnostics: NpuStandardRouteS5TtsDiagnostics? = null,
        nativeDiagnostics: NpuS1NativeStageDiagnostics = NpuS1NativeStageDiagnostics(),
        inputPrompt: String = "",
    ): String {
        val sideEffects = selection.sideEffects
        val promptRewrite = rewritePromptForNative(inputPrompt)
        val outputQualityCandidate = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = rawOutput,
            sanitizedOutput = sanitizedOutput,
            inputPrompt = inputPrompt,
        )
        val maxOutputTokensClamped = selection.requestedMaxOutputTokens != selection.effectiveMaxOutputTokens
        val maxOutputTokensClampReason = if (maxOutputTokensClamped) {
            NpuStandardRoutePreferences.MAX_OUTPUT_TOKENS_CLAMP_REASON_NATIVE_LIMIT
        } else {
            NpuStandardRoutePreferences.MAX_OUTPUT_TOKENS_CLAMP_REASON_NONE
        }
        return listOfNotNull(
            "NPU プレビュー診断",
            "route_id=NPU_STANDARD_ROUTE_S1",
            "NPU STANDARD ROUTE S1",
            "[DEV診断: NPU Standard Route S1 Timing]".takeIf { timing.hasAnyValue },
            "npu_s1_total_ms=${formatTimingMs(timing.totalMs)}".takeIf { timing.hasAnyValue },
            "npu_s1_decode_ms=${formatTimingMs(timing.decodeMs)}".takeIf { timing.hasAnyValue },
            "npu_s1_ttft_ms=${formatTimingMs(timing.ttftMs)}".takeIf { timing.hasAnyValue },
            "npu_s1_output_tokens=${timing.outputTokens?.toString() ?: "n/a"}".takeIf { timing.hasAnyValue },
            "npu_s1_token_count_mode=${timing.tokenCountMode}".takeIf { timing.hasAnyValue },
            "npu_s1_tokens_per_second=${formatTokensPerSecond(timing.tokensPerSecond)}".takeIf { timing.hasAnyValue },
            "route_type=${selection.routeType}",
            "standard_route_connected=true",
            s2DbReason.takeIf { it.isNotBlank() }?.let { "s2_db_reason=$it" },
            selectedModelName.takeIf { it.isNotBlank() }?.let { "selected_model_name=$it" },
            selectedModelFile.takeIf { it.isNotBlank() }?.let { "selected_model_file=$it" },
            npuModelEligible?.let { "npu_model_eligible=$it" },
            "status=$status",
            "reason=$reason",
            "native_error_class=${nativeDiagnostics.nativeErrorClass}".takeIf { shouldShowNpuS1NativeFailureDiagnostics(status, nativeDiagnostics) },
            "native_error_message=${nativeDiagnostics.nativeErrorMessage}".takeIf { shouldShowNpuS1NativeFailureDiagnostics(status, nativeDiagnostics) },
            "native_error_stage=${nativeDiagnostics.nativeErrorStage}".takeIf { shouldShowNpuS1NativeFailureDiagnostics(status, nativeDiagnostics) },
            "native_error_source=${nativeDiagnostics.nativeErrorSource}".takeIf { shouldShowNpuS1NativeFailureDiagnostics(status, nativeDiagnostics) },
            "native_link_failure_detected=${nativeDiagnostics.nativeLinkFailureDetected}".takeIf { shouldShowNpuS1NativeFailureDiagnostics(status, nativeDiagnostics) },
            "native_link_failure_library=${nativeDiagnostics.nativeLinkFailureLibrary}".takeIf { shouldShowNpuS1NativeFailureDiagnostics(status, nativeDiagnostics) },
            "native_load_order=${nativeDiagnostics.nativeLoadOrder}".takeIf { shouldShowNpuS1NativeFailureDiagnostics(status, nativeDiagnostics) },
            "java_library_path=${nativeDiagnostics.javaLibraryPath}".takeIf { shouldShowNpuS1NativeFailureDiagnostics(status, nativeDiagnostics) },
            "supported_abis=${nativeDiagnostics.supportedAbis}".takeIf { shouldShowNpuS1NativeFailureDiagnostics(status, nativeDiagnostics) },
            "normal_chat_native_route_blocked=${reason == NpuStandardRouteS1ProviderSelector.REASON_NATIVE_ROUTE_BLOCKED_FOR_NORMAL_CHAT}",
            "prompt_tail_variant=${selection.promptTailVariant}",
            "prompt_wrapper_used=$PROMPT_WRAPPER_USED",
            "selected_prompt_profile=${promptRewrite.selectedPromptProfile}",
            "arithmetic_prompt_detected=${promptRewrite.arithmeticPromptDetected}",
            "short_prompt_rewrite_applied=${promptRewrite.shortPromptRewriteApplied}",
            "rewritten_prompt_tail=${promptRewrite.rewrittenPromptText.takeLast(120)}",
            "final_prompt_tail=${promptRewrite.finalPromptText.takeLast(160)}",
            "requested_max_output_tokens=${selection.requestedMaxOutputTokens}",
            "effective_max_output_tokens=${selection.effectiveMaxOutputTokens}",
            "max_output_tokens=${selection.effectiveMaxOutputTokens}",
            "max_output_tokens_clamped=$maxOutputTokensClamped",
            "max_output_tokens_clamp_limit=${NpuStandardRoutePreferences.NATIVE_MAX_OUTPUT_TOKENS_LIMIT}",
            "max_output_tokens_clamp_reason=$maxOutputTokensClampReason",
            "app_requested_max_output_tokens=${selection.requestedMaxOutputTokens}",
            "native_requested_max_output_tokens=${selection.effectiveMaxOutputTokens}",
            "native_effective_max_output_tokens=${selection.effectiveMaxOutputTokens}",
            "run_decode_reached=$runDecodeReached",
            "npu_backend_evidence=$npuBackendEvidence",
            "fallback_used=$fallbackUsed",
            "timeout=$timeout",
            "fresh_crash=$freshCrash",
            "raw_output=$rawOutput",
            "sanitized_output=$sanitizedOutput",
            "quality_classification=$qualityClassification",
            "output_quality_candidate_status=${outputQualityCandidate.status}",
            "output_quality_candidate_reason=${outputQualityCandidate.reason}",
            "output_quality_candidate_prepared_output=${outputQualityCandidate.preparedOutput}",
            s5TtsDiagnostics?.let { "s5_tts_reason=${it.reason}" },
            s5TtsDiagnostics?.let { "tts_requested=${it.requested}" },
            s5TtsDiagnostics?.let { "tts_started=${it.started}" },
            s5TtsDiagnostics?.let { "tts_completed=${it.completed}" },
            s5TtsDiagnostics?.let { "tts_skipped=${it.skipped}" },
            s5TtsDiagnostics?.let { "tts_exception_class=${it.exceptionClass}" },
            s5TtsDiagnostics?.let { "tts_exception_message=${it.exceptionMessage}" },
            s5TtsDiagnostics?.let { "tts_text_length=${it.textLength}" },
            s5TtsDiagnostics?.let { "tts_input_source=${it.inputSource}" },
            "db=${sideEffects.db}",
            "tts=${sideEffects.tts}",
            "markdown=${sideEffects.markdown}",
            "streaming=${sideEffects.streaming}",
            "backend_npu_persisted=${sideEffects.backendNpuPersisted}",
            "conversation_history_saved=${sideEffects.conversationHistorySaved}",
        ).joinToString("\n")
    }

    fun estimateOutputTokensFromText(text: String): Int? {
        val normalized = text.trim()
        if (normalized.isBlank()) return null
        return normalized.codePointCount(0, normalized.length).coerceAtLeast(1)
    }

    fun tokensPerSecond(
        outputTokens: Int?,
        decodeMs: Long?,
    ): Double? {
        if (outputTokens == null || outputTokens <= 0 || decodeMs == null || decodeMs <= 0L) return null
        return outputTokens / (decodeMs / 1000.0)
    }

    fun formatTimingMs(value: Long?): String = value?.coerceAtLeast(0L)?.toString() ?: "n/a"

    fun formatTokensPerSecond(value: Double?): String =
        value?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            String.format(Locale.US, "%.1f", it)
        } ?: "n/a"

    private fun shouldShowNpuS1NativeFailureDiagnostics(
        status: String,
        nativeDiagnostics: NpuS1NativeStageDiagnostics,
    ): Boolean = status != STATUS_SUCCESS && (
        nativeDiagnostics.nativeErrorClass.isNotBlank() ||
            nativeDiagnostics.nativeErrorMessage.isNotBlank() ||
            nativeDiagnostics.nativeLinkFailureDetected.isNotBlank() ||
            nativeDiagnostics.nativeDiagTail.isNotBlank()
        )
}
