package io.github.ninbyo02.lami.npu

enum class DevOnlyNpuHiddenExperimentalMode {
    HIDDEN_EXPERIMENTAL_256,
    HIDDEN_PER_RUN_ISOLATED_512,
}

enum class DevOnlyNpuExecutionIsolation {
    SEQUENTIAL,
    ACTIVITY_RESTART_ONLY,
    PER_RUN_FORCE_STOP,
}

data class DevOnlyNpuHiddenExperimentalModeGateInput(
    val mode: DevOnlyNpuHiddenExperimentalMode,
    val executionIsolation: DevOnlyNpuExecutionIsolation,
    val maxOutputTokens: Int,
    val hiddenOnly: Boolean = true,
    val normalChatScreenPromotion: Boolean = false,
    val forceStopBeforeEachPrompt: Boolean = false,
    val forceStopAfterEachPrompt: Boolean = false,
    val runDecodeReached: Boolean = false,
    val setMaxOutputTokens512Evidence: Boolean = false,
    val timeout: Boolean = false,
    val freshCrash: Boolean = false,
    val fallbackUsed: Boolean = false,
    val qnnHtpFastRpcEvidence: Boolean = false,
    val engineCloseUniquePtrCleanup: Boolean = false,
    val cleanupEvidence: Boolean = false,
    val processPresentAfter10s: Boolean = false,
    val memoryHighRetained: Boolean = false,
    val codeAwareSanitizer: Boolean = false,
    val codeIndentationPreserved: Boolean = false,
    val codeFenceCompleted: Boolean = false,
    val selectedPathSaved: Boolean = false,
    val assistantMessageListInserted: Boolean = false,
    val db: Boolean = false,
    val tts: Boolean = false,
    val markdown: Boolean = false,
    val streaming: Boolean = false,
)

enum class DevOnlyNpuHiddenExperimentalModeGateReason {
    OK,
    MAX_OUTPUT_TOKENS_ABOVE_512_BLOCKED,
    HIDDEN_ONLY_REQUIRED,
    NORMAL_CHAT_SCREEN_BLOCKED,
    INVALID_256_TOKEN_LIMIT,
    INVALID_512_TOKEN_LIMIT,
    SEQUENTIAL_512_ROLLBACK,
    ACTIVITY_RESTART_ONLY_512_ROLLBACK,
    FORCE_STOP_BEFORE_AFTER_REQUIRED,
    RUN_DECODE_MISSING,
    SET_MAX_OUTPUT_TOKENS_512_MISSING,
    TIMEOUT,
    FRESH_CRASH,
    FALLBACK_USED,
    QNN_EVIDENCE_MISSING,
    ENGINE_CLOSE_MISSING,
    CLEANUP_EVIDENCE_MISSING,
    AFTER_10S_PROCESS_RETAINED,
    MEMORY_HIGH_RETAINED,
    CODE_AWARE_SANITIZER_REQUIRED,
    CODE_INDENTATION_BROKEN,
    CODE_FENCE_INCOMPLETE,
    SELECTED_PATH_SAVED,
    ASSISTANT_MESSAGE_LIST_INSERTED,
    SIDE_EFFECT_INGRESS,
}

data class DevOnlyNpuHiddenExperimentalModeGateResult(
    val allowed: Boolean,
    val reason: DevOnlyNpuHiddenExperimentalModeGateReason,
)

object DevOnlyNpuHiddenExperimentalModeGate {
    fun evaluate(input: DevOnlyNpuHiddenExperimentalModeGateInput): DevOnlyNpuHiddenExperimentalModeGateResult {
        val reason = when {
            input.maxOutputTokens > DevOnlyNpuRouteAdapter.QAIRT244_MAX_OUTPUT_TOKENS_COMPARE_LIMIT ->
                DevOnlyNpuHiddenExperimentalModeGateReason.MAX_OUTPUT_TOKENS_ABOVE_512_BLOCKED
            !input.hiddenOnly -> DevOnlyNpuHiddenExperimentalModeGateReason.HIDDEN_ONLY_REQUIRED
            input.normalChatScreenPromotion -> DevOnlyNpuHiddenExperimentalModeGateReason.NORMAL_CHAT_SCREEN_BLOCKED
            input.assistantMessageListInserted ->
                DevOnlyNpuHiddenExperimentalModeGateReason.ASSISTANT_MESSAGE_LIST_INSERTED
            input.selectedPathSaved -> DevOnlyNpuHiddenExperimentalModeGateReason.SELECTED_PATH_SAVED
            input.db || input.tts || input.markdown || input.streaming ->
                DevOnlyNpuHiddenExperimentalModeGateReason.SIDE_EFFECT_INGRESS
            input.mode == DevOnlyNpuHiddenExperimentalMode.HIDDEN_EXPERIMENTAL_256 ->
                evaluateHiddenExperimental256(input)
            input.mode == DevOnlyNpuHiddenExperimentalMode.HIDDEN_PER_RUN_ISOLATED_512 ->
                evaluateHiddenPerRunIsolated512(input)
            else -> DevOnlyNpuHiddenExperimentalModeGateReason.NORMAL_CHAT_SCREEN_BLOCKED
        }
        return DevOnlyNpuHiddenExperimentalModeGateResult(
            allowed = reason == DevOnlyNpuHiddenExperimentalModeGateReason.OK,
            reason = reason,
        )
    }

    private fun evaluateHiddenExperimental256(
        input: DevOnlyNpuHiddenExperimentalModeGateInput,
    ): DevOnlyNpuHiddenExperimentalModeGateReason {
        return if (input.maxOutputTokens ==
            DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_EXPERIMENTAL_BASELINE_CANDIDATE_TOKENS
        ) {
            DevOnlyNpuHiddenExperimentalModeGateReason.OK
        } else {
            DevOnlyNpuHiddenExperimentalModeGateReason.INVALID_256_TOKEN_LIMIT
        }
    }

    private fun evaluateHiddenPerRunIsolated512(
        input: DevOnlyNpuHiddenExperimentalModeGateInput,
    ): DevOnlyNpuHiddenExperimentalModeGateReason = when {
        input.maxOutputTokens != DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS ->
            DevOnlyNpuHiddenExperimentalModeGateReason.INVALID_512_TOKEN_LIMIT
        input.executionIsolation == DevOnlyNpuExecutionIsolation.SEQUENTIAL ->
            DevOnlyNpuHiddenExperimentalModeGateReason.SEQUENTIAL_512_ROLLBACK
        input.executionIsolation == DevOnlyNpuExecutionIsolation.ACTIVITY_RESTART_ONLY ->
            DevOnlyNpuHiddenExperimentalModeGateReason.ACTIVITY_RESTART_ONLY_512_ROLLBACK
        !input.forceStopBeforeEachPrompt || !input.forceStopAfterEachPrompt ->
            DevOnlyNpuHiddenExperimentalModeGateReason.FORCE_STOP_BEFORE_AFTER_REQUIRED
        !input.runDecodeReached -> DevOnlyNpuHiddenExperimentalModeGateReason.RUN_DECODE_MISSING
        !input.setMaxOutputTokens512Evidence ->
            DevOnlyNpuHiddenExperimentalModeGateReason.SET_MAX_OUTPUT_TOKENS_512_MISSING
        input.timeout -> DevOnlyNpuHiddenExperimentalModeGateReason.TIMEOUT
        input.freshCrash -> DevOnlyNpuHiddenExperimentalModeGateReason.FRESH_CRASH
        input.fallbackUsed -> DevOnlyNpuHiddenExperimentalModeGateReason.FALLBACK_USED
        !input.qnnHtpFastRpcEvidence -> DevOnlyNpuHiddenExperimentalModeGateReason.QNN_EVIDENCE_MISSING
        !input.engineCloseUniquePtrCleanup -> DevOnlyNpuHiddenExperimentalModeGateReason.ENGINE_CLOSE_MISSING
        !input.cleanupEvidence -> DevOnlyNpuHiddenExperimentalModeGateReason.CLEANUP_EVIDENCE_MISSING
        input.processPresentAfter10s -> DevOnlyNpuHiddenExperimentalModeGateReason.AFTER_10S_PROCESS_RETAINED
        input.memoryHighRetained -> DevOnlyNpuHiddenExperimentalModeGateReason.MEMORY_HIGH_RETAINED
        !input.codeAwareSanitizer -> DevOnlyNpuHiddenExperimentalModeGateReason.CODE_AWARE_SANITIZER_REQUIRED
        !input.codeIndentationPreserved -> DevOnlyNpuHiddenExperimentalModeGateReason.CODE_INDENTATION_BROKEN
        !input.codeFenceCompleted -> DevOnlyNpuHiddenExperimentalModeGateReason.CODE_FENCE_INCOMPLETE
        else -> DevOnlyNpuHiddenExperimentalModeGateReason.OK
    }
}
