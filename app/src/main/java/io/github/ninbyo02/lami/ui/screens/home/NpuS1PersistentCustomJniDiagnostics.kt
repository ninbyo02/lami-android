package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context

internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_IDLE = "idle"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_RUNNING = "running"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_COMPLETED = "completed"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_STOPPED = "stopped"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_CANCELLED = "cancelled"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_DEFAULT_COUNT = 20
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_BACKEND = "NPU"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_ENGINE_CONFIG_VERSION =
    "persistent_custom_jni_holder_poc_v1"
internal const val NPU_S1_PERSISTENT_CUSTOM_JNI_CLASS_NAME =
    "io.github.ninbyo02.lami.ui.screens.home.NpuS1PersistentCustomJniDevProbe"
internal const val NPU_S1_PROMOTION_GATE_STATUS_PASS = "pass"
internal const val NPU_S1_PROMOTION_GATE_STATUS_FAIL = "fail"
internal const val NPU_S1_PROMOTION_GATE_STATUS_NOT_RUN = "not_run"
internal const val NPU_S1_PROMOTION_GATE_REASON_FULL_20_NOT_RUN = "full_20_not_run"
internal const val NPU_S1_PROMOTION_GATE_REASON_READY_BUT_NORMAL_CHAT_BLOCKED =
    "crash_safety_ready_but_normal_chat_unblock_blocked_by_policy"
internal const val NPU_S1_PROMOTION_GATE_TOMBSTONE_COMPARE_HINT =
    "manually_compare_probe_wall_time_with_dumpsys_dropbox_and_data_tombstones"
internal const val NPU_S1_OUTPUT_QUALITY_NATURAL_JAPANESE = "natural_japanese"
internal const val NPU_S1_OUTPUT_QUALITY_TEMPLATE_LEAK = "template_leak"
internal const val NPU_S1_OUTPUT_QUALITY_PUNCTUATION_START = "punctuation_start"
internal const val NPU_S1_OUTPUT_QUALITY_PLACEHOLDER_LEAK = "placeholder_leak"
internal const val NPU_S1_OUTPUT_QUALITY_UNKNOWN = "unknown"

internal enum class NpuS1PersistentCustomJniProbeMode(
    val wireValue: String,
    val displayLabel: String,
) {
    ENTRYPOINT_ONLY("entrypoint_only", "Entrypoint only"),
    MODEL_ASSETS_ONLY("model_assets_only", "ModelAssets only"),
    ENGINE_SETTINGS_ONLY("engine_settings_only", "EngineSettings only"),
    BEFORE_ENGINE_CREATE("before_engine_create", "Before engine create"),
    ENGINE_CREATE_ONLY("engine_create_only", "Engine create only"),
    EDITABLE_ENGINE_CREATE_ONLY("editable_engine_create_only", "Editable engine create only"),
    EDITABLE_ENGINE_CREATE_ONLY_MINIMAL(
        "editable_engine_create_only_minimal",
        "Editable engine create only minimal",
    ),
    FULL_20("full_20", "Full 20"),
}

internal interface NpuS1PersistentCustomJniProbeRunner {
    suspend fun run(
        mode: NpuS1PersistentCustomJniProbeMode,
        onUpdate: (NpuS1PersistentCustomJniProbeState) -> Unit,
        isCancelled: () -> Boolean,
    ): NpuS1PersistentCustomJniProbeState
}

internal fun createNpuS1PersistentCustomJniProbeRunner(
    context: Context,
): NpuS1PersistentCustomJniProbeRunner? =
    runCatching {
        Class.forName(NPU_S1_PERSISTENT_CUSTOM_JNI_CLASS_NAME)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context.applicationContext) as? NpuS1PersistentCustomJniProbeRunner
    }.getOrNull()

internal data class NpuS1PersistentCustomJniHolderKey(
    val modelPath: String = "unavailable",
    val modelFileLastModified: String = "unavailable",
    val modelFileSize: String = "unavailable",
    val backend: String = NPU_S1_PERSISTENT_CUSTOM_JNI_BACKEND,
    val cacheDir: String = "unavailable",
    val maxTokenBudget: String = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS.toString(),
    val engineConfigVersion: String = NPU_S1_PERSISTENT_CUSTOM_JNI_ENGINE_CONFIG_VERSION,
) {
    fun stableText(): String = listOf(
        "model_path=$modelPath",
        "model_file_last_modified=$modelFileLastModified",
        "model_file_size=$modelFileSize",
        "backend=$backend",
        "cache_dir=$cacheDir",
        "max_token_budget=$maxTokenBudget",
        "engine_config_version=$engineConfigVersion",
    ).joinToString(";")
}

internal data class NpuS1PersistentCustomJniRunRecord(
    val runIndex: Int,
    val status: String,
    val reason: String,
    val sessionCreated: String = "unavailable",
    val sessionClosed: String = "unavailable",
    val prefillStarted: String = "unavailable",
    val prefillFinished: String = "unavailable",
    val decodeStarted: String = "unavailable",
    val decodeFinished: String = "unavailable",
    val rawOutput: String = "",
    val sanitizedOutput: String = "",
    val outputPrefix20Chars: String = "unavailable",
    val startsWithPunctuation: String = "unavailable",
    val containsBusinessPhrase: String = "unavailable",
    val containsPlaceholder: String = "unavailable",
    val qualityClassification: String = "unavailable",
    val totalMs: Long? = null,
    val prefillMs: Long? = null,
    val decodeMs: Long? = null,
    val cleanupMs: Long? = null,
    val failureStage: String = "unavailable",
    val failureExceptionClass: String = "unavailable",
    val failureExceptionMessage: String = "unavailable",
    val nativeDiagTail: String = "unavailable",
)

internal data class NpuS1PersistentCustomJniProbeState(
    val persistentCustomJniStatus: String = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_IDLE,
    val runCountRequested: Int = NPU_S1_PERSISTENT_CUSTOM_JNI_DEFAULT_COUNT,
    val runCountCompletedOverride: Int? = null,
    val startedAtElapsedRealtimeMs: Long? = null,
    val finishedAtElapsedRealtimeMs: Long? = null,
    val engineCreateCount: String = "unavailable",
    val decodeAttemptCount: String = "unavailable",
    val decodeSuccessCount: String = "unavailable",
    val engineCloseReached: String = "unavailable",
    val engineCloseSuccess: String = "unavailable",
    val holderKey: NpuS1PersistentCustomJniHolderKey = NpuS1PersistentCustomJniHolderKey(),
    val holderGeneration: String = "unavailable",
    val holderReusedCount: String = "unavailable",
    val holderInvalidated: String = "unavailable",
    val holderKeyMismatchDetected: String = "unavailable",
    val holderKeyMismatchReason: String = "unavailable",
    val nativeHolderEntrypointAvailable: String = "unavailable",
    val selectedNativeProbeMode: String = NpuS1PersistentCustomJniProbeMode.FULL_20.wireValue,
    val lastNativeStage: String = "unavailable",
    val nativeEntrypointReached: String = "unavailable",
    val modelAssetsCreateReached: String = "unavailable",
    val modelAssetsCreateReturned: String = "unavailable",
    val engineSettingsCreateReached: String = "unavailable",
    val engineSettingsCreateReturned: String = "unavailable",
    val engineCreateReached: String = "unavailable",
    val engineCreateReturned: String = "unavailable",
    val sessionCreateReached: String = "unavailable",
    val prefillReached: String = "unavailable",
    val decodeReached: String = "unavailable",
    val nativeDiagFlushCount: String = "unavailable",
    val nativeResultFlushCount: String = "unavailable",
    val engineCreateModelPath: String = "unavailable",
    val engineCreateNativeLibraryDir: String = "unavailable",
    val engineCreateCacheDir: String = "unavailable",
    val engineCreateBackend: String = "unavailable",
    val engineCreatePromptInputLimitMode: String = "unavailable",
    val engineCreateRequestedMaxOutputTokens: String = "unavailable",
    val engineCreateEffectiveMaxOutputTokens: String = "unavailable",
    val engineCreateMaxTokenBudget: String = "unavailable",
    val engineCreateSettingsSource: String = "unavailable",
    val engineCreateAssetsSource: String = "unavailable",
    val engineCreateMatchesEditablePromptPath: String = "unavailable",
    val engineCreateMatchesEditablePromptSettings: String = "unavailable",
    val editablePromptEngineCreateSignature: String = "unavailable",
    val persistentEngineCreateSignature: String = "unavailable",
    val engineCreateMinimalPath: String = "unavailable",
    val persistentHolderUsed: String = "unavailable",
    val firstFailureRunIndex: Int? = null,
    val firstFailureStage: String = "unavailable",
    val firstFailureReason: String = "unavailable",
    val firstFailureExceptionClass: String = "unavailable",
    val firstFailureDiagTail: String = "unavailable",
    val modelPath: String = "unavailable",
    val modelFileSize: String = "unavailable",
    val modelFileLastModified: String = "unavailable",
    val backendEvidence: String = "unavailable",
    val persistentCustomJniHypothesisResult: String = "unavailable",
    val promptInputLimitMode: String = "unavailable",
    val finalPromptText: String = "unavailable",
    val finalPromptLengthChars: String = "unavailable",
    val finalPromptTailPreview: String = "unavailable",
    val systemTemplateUsed: String = "unavailable",
    val hiddenTemplateUsed: String = "unavailable",
    val promptWrapperUsed: String = "unavailable",
    val prefillTextOrTokenNote: String = "unavailable",
    val firstOutputChars: String = "unavailable",
    val outputPrefixClassification: String = "unavailable",
    val outputQualityReason: String = "unavailable",
    val outputRepeatsSameAcrossRuns: String = "unavailable",
    val outputLooksBusinessTemplate: String = "unavailable",
    val outputStartsWithPunctuation: String = "unavailable",
    val outputContainsPlaceholder: String = "unavailable",
    val promotionGateFreshCrash: String = "false",
    val promotionGateTimeout: String = "false",
    val promotionGateFallback: String = "false",
    val promotionGateTombstoneManualCheck: String = "required",
    val records: List<NpuS1PersistentCustomJniRunRecord> = emptyList(),
) {
    val runCountCompleted: Int
        get() = runCountCompletedOverride ?: records.size

    val successCount: Int
        get() = records.count { it.status == NpuStandardRouteS1Contract.STATUS_SUCCESS }

    val failureCount: Int
        get() = records.count { it.status != NpuStandardRouteS1Contract.STATUS_SUCCESS }
}

internal data class NpuS1PromotionGateResult(
    val status: String,
    val reason: String,
    val full20Required: Boolean = true,
    val qualityRequired: Boolean = false,
    val normalChatUnblockAllowed: Boolean = false,
    val tombstoneManualCheckRequired: Boolean = true,
    val tombstoneCompareHint: String = NPU_S1_PROMOTION_GATE_TOMBSTONE_COMPARE_HINT,
)

internal data class NpuS1PersistentCustomJniOutputQualityDiagnostics(
    val outputPrefix20Chars: String,
    val startsWithPunctuation: Boolean,
    val containsBusinessPhrase: Boolean,
    val containsPlaceholder: Boolean,
    val qualityClassification: String,
    val reason: String,
)

internal fun classifyNpuS1PersistentCustomJniOutputQuality(
    output: String,
): NpuS1PersistentCustomJniOutputQualityDiagnostics {
    val visibleOutput = output.trimStart()
    val startsWithPunctuation = visibleOutput.firstOrNull()?.let(::isSuspiciousJapanesePrefixPunctuation) == true
    val containsBusinessPhrase = listOf(
        "いつもお世話になっております",
        "お世話になっております",
        "よろしくお願いいたします",
    ).any(output::contains)
    val containsPlaceholder = Regex("""\[[^\]]+\]""").containsMatchIn(output)
    val classification = when {
        containsPlaceholder -> NPU_S1_OUTPUT_QUALITY_PLACEHOLDER_LEAK
        containsBusinessPhrase -> NPU_S1_OUTPUT_QUALITY_TEMPLATE_LEAK
        startsWithPunctuation -> NPU_S1_OUTPUT_QUALITY_PUNCTUATION_START
        output.isBlank() -> NPU_S1_OUTPUT_QUALITY_UNKNOWN
        else -> NPU_S1_OUTPUT_QUALITY_NATURAL_JAPANESE
    }
    val reasons = buildList {
        if (startsWithPunctuation) add("starts_with_punctuation")
        if (containsBusinessPhrase) add("business_template_phrase")
        if (containsPlaceholder) add("placeholder_leak")
        if (output.isBlank()) add("empty_output")
    }
    return NpuS1PersistentCustomJniOutputQualityDiagnostics(
        outputPrefix20Chars = output.take(20),
        startsWithPunctuation = startsWithPunctuation,
        containsBusinessPhrase = containsBusinessPhrase,
        containsPlaceholder = containsPlaceholder,
        qualityClassification = classification,
        reason = reasons.ifEmpty { listOf("no_quality_issue_detected") }.joinToString("+"),
    )
}

private fun isSuspiciousJapanesePrefixPunctuation(char: Char): Boolean =
    char in setOf('。', '、', '.', ',', '．', '，', '！', '？', '!', '?', ')', '）', ']', '］', '」', '』')

internal fun evaluateNpuS1PromotionGate(
    state: NpuS1PersistentCustomJniProbeState,
): NpuS1PromotionGateResult {
    val full20Selected = state.selectedNativeProbeMode == NpuS1PersistentCustomJniProbeMode.FULL_20.wireValue
    val hasRunEvidence = state.runCountCompleted > 0 ||
        state.engineCreateCount != "unavailable" ||
        state.decodeSuccessCount != "unavailable"
    if (!full20Selected || !hasRunEvidence) {
        return NpuS1PromotionGateResult(
            status = NPU_S1_PROMOTION_GATE_STATUS_NOT_RUN,
            reason = NPU_S1_PROMOTION_GATE_REASON_FULL_20_NOT_RUN,
        )
    }

    val requested = state.runCountRequested
    val failedReasons = buildList {
        if (state.persistentCustomJniStatus != NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_COMPLETED) {
            add("persistent_custom_jni_status_not_completed")
        }
        if (!state.backendEvidence.contains("QNN_HTP_V79")) {
            add("backend_evidence_missing_QNN_HTP_V79")
        }
        if (state.successCount != requested) add("success_count_not_run_count_requested")
        if (state.failureCount != 0) add("failure_count_not_zero")
        if (state.decodeSuccessCount != requested.toString()) add("decode_success_count_not_run_count_requested")
        if (state.decodeAttemptCount != requested.toString()) add("decode_attempt_count_not_run_count_requested")
        if (state.engineCreateCount != "1") add("engine_create_count_not_one")
        if (state.engineCloseReached != "true") add("engine_close_reached_not_true")
        if (state.engineCloseSuccess != "true") add("engine_close_success_not_true")
        if (state.promotionGateFreshCrash != "false") add("fresh_crash_not_false")
        if (state.promotionGateTimeout != "false") add("timeout_not_false")
        if (state.promotionGateFallback != "false") add("fallback_not_false")
    }

    return if (failedReasons.isEmpty()) {
        NpuS1PromotionGateResult(
            status = NPU_S1_PROMOTION_GATE_STATUS_PASS,
            reason = NPU_S1_PROMOTION_GATE_REASON_READY_BUT_NORMAL_CHAT_BLOCKED,
        )
    } else {
        NpuS1PromotionGateResult(
            status = NPU_S1_PROMOTION_GATE_STATUS_FAIL,
            reason = failedReasons.joinToString("+"),
        )
    }
}

internal fun formatNpuS1PersistentCustomJniDiagnosticsForDev(
    state: NpuS1PersistentCustomJniProbeState,
): String = buildString {
    val promotionGate = evaluateNpuS1PromotionGate(state)
    appendLine("[DEV診断: NPU S1 persistent custom JNI summary]")
    appendLine("persistent_custom_jni_status=${state.persistentCustomJniStatus}")
    appendLine("run_count_requested=${state.runCountRequested}")
    appendLine("run_count_completed=${state.runCountCompleted}")
    appendLine("success_count=${state.successCount}")
    appendLine("failure_count=${state.failureCount}")
    appendLine("engine_create_count=${state.engineCreateCount}")
    appendLine("decode_attempt_count=${state.decodeAttemptCount}")
    appendLine("decode_success_count=${state.decodeSuccessCount}")
    appendLine("engine_close_reached=${state.engineCloseReached}")
    appendLine("engine_close_success=${state.engineCloseSuccess}")
    appendLine("holder_key=${escapePersistentCustomJniCopyValue(state.holderKey.stableText())}")
    appendLine("holder_key_model_path=${escapePersistentCustomJniCopyValue(state.holderKey.modelPath)}")
    appendLine("holder_key_model_file_last_modified=${state.holderKey.modelFileLastModified}")
    appendLine("holder_key_model_file_size=${state.holderKey.modelFileSize}")
    appendLine("holder_key_backend=${state.holderKey.backend}")
    appendLine("holder_key_cache_dir=${escapePersistentCustomJniCopyValue(state.holderKey.cacheDir)}")
    appendLine("holder_key_max_token_budget=${state.holderKey.maxTokenBudget}")
    appendLine("holder_key_engine_config_version=${state.holderKey.engineConfigVersion}")
    appendLine("holder_generation=${state.holderGeneration}")
    appendLine("holder_reused_count=${state.holderReusedCount}")
    appendLine("holder_invalidated=${state.holderInvalidated}")
    appendLine("holder_key_mismatch_detected=${state.holderKeyMismatchDetected}")
    appendLine("holder_key_mismatch_reason=${escapePersistentCustomJniCopyValue(state.holderKeyMismatchReason)}")
    appendLine("native_holder_entrypoint_available=${state.nativeHolderEntrypointAvailable}")
    appendLine("selected_native_probe_mode=${state.selectedNativeProbeMode}")
    appendLine("last_native_stage=${state.lastNativeStage}")
    appendLine("native_entrypoint_reached=${state.nativeEntrypointReached}")
    appendLine("model_assets_create_reached=${state.modelAssetsCreateReached}")
    appendLine("model_assets_create_returned=${state.modelAssetsCreateReturned}")
    appendLine("engine_settings_create_reached=${state.engineSettingsCreateReached}")
    appendLine("engine_settings_create_returned=${state.engineSettingsCreateReturned}")
    appendLine("engine_create_reached=${state.engineCreateReached}")
    appendLine("engine_create_returned=${state.engineCreateReturned}")
    appendLine("session_create_reached=${state.sessionCreateReached}")
    appendLine("prefill_reached=${state.prefillReached}")
    appendLine("decode_reached=${state.decodeReached}")
    appendLine("native_diag_flush_count=${state.nativeDiagFlushCount}")
    appendLine("native_result_flush_count=${state.nativeResultFlushCount}")
    appendLine("engine_create_model_path=${escapePersistentCustomJniCopyValue(state.engineCreateModelPath)}")
    appendLine("engine_create_native_library_dir=${escapePersistentCustomJniCopyValue(state.engineCreateNativeLibraryDir)}")
    appendLine("engine_create_cache_dir=${escapePersistentCustomJniCopyValue(state.engineCreateCacheDir)}")
    appendLine("engine_create_backend=${state.engineCreateBackend}")
    appendLine("engine_create_prompt_input_limit_mode=${state.engineCreatePromptInputLimitMode}")
    appendLine("engine_create_requested_max_output_tokens=${state.engineCreateRequestedMaxOutputTokens}")
    appendLine("engine_create_effective_max_output_tokens=${state.engineCreateEffectiveMaxOutputTokens}")
    appendLine("engine_create_max_token_budget=${state.engineCreateMaxTokenBudget}")
    appendLine("engine_create_settings_source=${escapePersistentCustomJniCopyValue(state.engineCreateSettingsSource)}")
    appendLine("engine_create_assets_source=${escapePersistentCustomJniCopyValue(state.engineCreateAssetsSource)}")
    appendLine("engine_create_matches_editable_prompt_path=${state.engineCreateMatchesEditablePromptPath}")
    appendLine("engine_create_matches_editable_prompt_settings=${state.engineCreateMatchesEditablePromptSettings}")
    appendLine("editable_prompt_engine_create_signature=${escapePersistentCustomJniCopyValue(state.editablePromptEngineCreateSignature)}")
    appendLine("persistent_engine_create_signature=${escapePersistentCustomJniCopyValue(state.persistentEngineCreateSignature)}")
    appendLine("engine_create_minimal_path=${state.engineCreateMinimalPath}")
    appendLine("persistent_holder_used=${state.persistentHolderUsed}")
    appendLine("first_failure_run_index=${formatPersistentCustomJniValue(state.firstFailureRunIndex)}")
    appendLine("first_failure_stage=${state.firstFailureStage}")
    appendLine("first_failure_reason=${escapePersistentCustomJniCopyValue(state.firstFailureReason)}")
    appendLine("first_failure_exception_class=${state.firstFailureExceptionClass}")
    appendLine("first_failure_diag_tail=${escapePersistentCustomJniCopyValue(state.firstFailureDiagTail)}")
    appendLine("model_path=${escapePersistentCustomJniCopyValue(state.modelPath)}")
    appendLine("model_file_size=${state.modelFileSize}")
    appendLine("model_file_last_modified=${state.modelFileLastModified}")
    appendLine("backend_evidence=${escapePersistentCustomJniCopyValue(state.backendEvidence)}")
    appendLine("persistent_custom_jni_hypothesis_result=${state.persistentCustomJniHypothesisResult}")
    appendLine("prompt_input_limit_mode=${state.promptInputLimitMode}")
    appendLine("final_prompt_text=${escapePersistentCustomJniCopyValue(state.finalPromptText)}")
    appendLine("final_prompt_length_chars=${state.finalPromptLengthChars}")
    appendLine("final_prompt_tail_preview=${escapePersistentCustomJniCopyValue(state.finalPromptTailPreview)}")
    appendLine("system_template_used=${state.systemTemplateUsed}")
    appendLine("hidden_template_used=${state.hiddenTemplateUsed}")
    appendLine("prompt_wrapper_used=${state.promptWrapperUsed}")
    appendLine("prefill_text_or_token_note=${escapePersistentCustomJniCopyValue(state.prefillTextOrTokenNote)}")
    appendLine("first_output_chars=${escapePersistentCustomJniCopyValue(state.firstOutputChars)}")
    appendLine("output_prefix_classification=${state.outputPrefixClassification}")
    appendLine("output_quality_reason=${state.outputQualityReason}")
    appendLine("output_repeats_same_across_runs=${state.outputRepeatsSameAcrossRuns}")
    appendLine("output_looks_business_template=${state.outputLooksBusinessTemplate}")
    appendLine("output_starts_with_punctuation=${state.outputStartsWithPunctuation}")
    appendLine("output_contains_placeholder=${state.outputContainsPlaceholder}")
    appendLine("npu_s1_promotion_gate_status=${promotionGate.status}")
    appendLine("npu_s1_promotion_gate_reason=${promotionGate.reason}")
    appendLine("npu_s1_promotion_gate_full_20_required=${promotionGate.full20Required}")
    appendLine("npu_s1_promotion_gate_quality_required=${promotionGate.qualityRequired}")
    appendLine("npu_s1_promotion_gate_normal_chat_unblock_allowed=${promotionGate.normalChatUnblockAllowed}")
    appendLine("npu_s1_promotion_gate_fresh_crash=${state.promotionGateFreshCrash}")
    appendLine("npu_s1_promotion_gate_timeout=${state.promotionGateTimeout}")
    appendLine("npu_s1_promotion_gate_fallback=${state.promotionGateFallback}")
    appendLine("npu_s1_promotion_gate_tombstone_manual_check=${state.promotionGateTombstoneManualCheck}")
    appendLine("npu_s1_promotion_gate_tombstone_compare_hint=${promotionGate.tombstoneCompareHint}")
    appendLine("npu_s1_promotion_gate_engine_create=pass".takeIf { state.engineCreateCount == "1" } ?: "npu_s1_promotion_gate_engine_create=fail")
    appendLine(
        "npu_s1_promotion_gate_decode_20=pass".takeIf {
            state.decodeSuccessCount == state.runCountRequested.toString() && state.failureCount == 0
        } ?: "npu_s1_promotion_gate_decode_20=fail",
    )
    appendLine(
        "npu_s1_promotion_gate_crash_safety=pass".takeIf {
            promotionGate.status == NPU_S1_PROMOTION_GATE_STATUS_PASS
        } ?: "npu_s1_promotion_gate_crash_safety=fail",
    )
    appendLine("npu_s1_promotion_gate_output_quality=suspect")
    appendLine("npu_s1_promotion_gate_normal_chat_unblock=blocked_by_policy")
    appendLine()
    appendLine("[DEV診断: NPU S1 persistent custom JNI details]")
    if (state.records.isEmpty()) {
        appendLine("records=empty")
    } else {
        state.records.forEach { record ->
            appendLine("run_index=${record.runIndex}")
            appendLine("status=${record.status}")
            appendLine("reason=${escapePersistentCustomJniCopyValue(record.reason)}")
            appendLine("session_created=${record.sessionCreated}")
            appendLine("session_closed=${record.sessionClosed}")
            appendLine("prefill_started=${record.prefillStarted}")
            appendLine("prefill_finished=${record.prefillFinished}")
            appendLine("decode_started=${record.decodeStarted}")
            appendLine("decode_finished=${record.decodeFinished}")
            appendLine("raw_output=${escapePersistentCustomJniCopyValue(record.rawOutput)}")
            appendLine("sanitized_output=${escapePersistentCustomJniCopyValue(record.sanitizedOutput)}")
            appendLine("output_prefix_20_chars=${escapePersistentCustomJniCopyValue(record.outputPrefix20Chars)}")
            appendLine("starts_with_punctuation=${record.startsWithPunctuation}")
            appendLine("contains_business_phrase=${record.containsBusinessPhrase}")
            appendLine("contains_placeholder=${record.containsPlaceholder}")
            appendLine("quality_classification=${record.qualityClassification}")
            appendLine("total_ms=${formatPersistentCustomJniValue(record.totalMs)}")
            appendLine("prefill_ms=${formatPersistentCustomJniValue(record.prefillMs)}")
            appendLine("decode_ms=${formatPersistentCustomJniValue(record.decodeMs)}")
            appendLine("cleanup_ms=${formatPersistentCustomJniValue(record.cleanupMs)}")
            appendLine("failure_stage=${record.failureStage}")
            appendLine("failure_exception_class=${record.failureExceptionClass}")
            appendLine("failure_exception_message=${escapePersistentCustomJniCopyValue(record.failureExceptionMessage)}")
            appendLine("native_diag_tail=${escapePersistentCustomJniCopyValue(record.nativeDiagTail)}")
        }
    }
}.trimEnd()

internal fun appendNpuS1PersistentCustomJniDiagnosticsForDev(
    text: String,
    state: NpuS1PersistentCustomJniProbeState,
): String = listOf(
    text,
    formatNpuS1PersistentCustomJniDiagnosticsForDev(state),
).filter { it.isNotBlank() }.joinToString("\n\n")

private fun formatPersistentCustomJniValue(value: Any?): String = value?.toString() ?: "unavailable"

private fun escapePersistentCustomJniCopyValue(text: String): String =
    text.replace("\\", "\\\\").replace("\n", "\\n")
