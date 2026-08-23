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
    "crash_safety_ready_policy_unblock_allowed"
internal const val NPU_S1_PROMOTION_GATE_TOMBSTONE_COMPARE_HINT =
    "manually_compare_probe_wall_time_with_dumpsys_dropbox_and_data_tombstones"
internal const val NPU_S1_OUTPUT_QUALITY_NATURAL_JAPANESE = "natural_japanese"
internal const val NPU_S1_OUTPUT_QUALITY_TEMPLATE_LEAK = "template_leak"
internal const val NPU_S1_OUTPUT_QUALITY_PUNCTUATION_START = "punctuation_start"
internal const val NPU_S1_OUTPUT_QUALITY_PLACEHOLDER_LEAK = "placeholder_leak"
internal const val NPU_S1_OUTPUT_QUALITY_REPEATED_TEMPLATE_OUTPUT = "repeated_template_output"
internal const val NPU_S1_OUTPUT_QUALITY_DECODE_OFFSET_SUSPECT = "decode_offset_suspect"
internal const val NPU_S1_OUTPUT_QUALITY_FIRST_TOKEN_BOUNDARY_SUSPECT = "first_token_boundary_suspect"
internal const val NPU_S1_OUTPUT_QUALITY_SPECIAL_TOKEN_SUSPECT = "special_token_suspect"
internal const val NPU_S1_OUTPUT_QUALITY_PROMPT_IGNORED_SUSPECT = "prompt_ignored_suspect"
internal const val NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS = "quality_candidate_pass"
internal const val NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL = "quality_candidate_fail"
internal const val NPU_S1_OUTPUT_QUALITY_CANDIDATE_UNKNOWN = "quality_candidate_unknown"
internal const val NPU_S1_OUTPUT_QUALITY_UNKNOWN = "unknown"
internal const val NPU_S1_QUALITY_GATE_STATUS_PASS = "pass"
internal const val NPU_S1_QUALITY_GATE_STATUS_FAIL = "fail"
internal const val NPU_S1_QUALITY_GATE_STATUS_UNKNOWN = "unknown"
internal const val NPU_S1_NORMAL_CHAT_UNBLOCK_READINESS_READY_BUT_BLOCKED_BY_POLICY =
    "ready_but_blocked_by_policy"
internal const val NPU_S1_NORMAL_CHAT_UNBLOCK_READINESS_NOT_READY = "not_ready"
internal const val NPU_S1_TOKEN_DIAGNOSTICS_UNAVAILABLE_NOTE =
    "token_ids_not_exposed_by_current_custom_jni_probe_without_native_rebuild"
internal const val NPU_S1_RECOMMENDED_PROMPT_PROFILE = "gemma_it_user_model"
internal const val NPU_S1_RECOMMENDED_PROMPT_PROFILE_REASON =
    "gemma_it_user_model_produced_natural_japanese_after_end_of_turn_and_leading_gt_sanitization"
internal const val NPU_S1_PROMPT_PROFILE_ALIAS_NOTE =
    "ai_edge_gallery_like_is_currently_duplicate_of_gemma_it_user_model"
internal const val NPU_S1_UNSAFE_PROMPT_PROFILE_NOTE =
    "bos_eos_like_if_supported_by_existing_code_is_unsafe_not_recommended_engine_create_failed"
internal const val NPU_S1_QUALITY_GATE_REQUIRED_RUN_COUNT = 20

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

internal enum class NpuS1PersistentCustomJniQualityPromptProfile(
    val wireValue: String,
    val displayLabel: String,
    val prompt: String,
    val runCount: Int,
    val promptValidationMode: String,
    val unsafeDevBypassPromptLengthGate: Boolean,
    val promptWrapperUsed: String,
    val promptWrapperFamily: String,
    val promptProfileHypothesis: String,
) {
    CURRENT_PROBE_QUALITY(
        wireValue = "current_probe_quality",
        displayLabel = "Current legacy/failing",
        prompt = "こんにちは",
        runCount = NPU_S1_PERSISTENT_CUSTOM_JNI_DEFAULT_COUNT,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
        unsafeDevBypassPromptLengthGate = true,
        promptWrapperUsed = "none",
        promptWrapperFamily = "none",
        promptProfileHypothesis = "baseline_current_probe_prompt",
    ),
    RAW_PROMPT_QUALITY(
        wireValue = "raw_prompt_quality",
        displayLabel = "Raw legacy/failing",
        prompt = "こんにちは",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE,
        unsafeDevBypassPromptLengthGate = false,
        promptWrapperUsed = "none",
        promptWrapperFamily = "none",
        promptProfileHypothesis = "raw_greeting_without_wrapper",
    ),
    SIMPLE_JA_CHAT_QUALITY(
        wireValue = "simple_ja_chat_quality",
        displayLabel = "Who?",
        prompt = "こんにちは。あなたは誰ですか？",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
        unsafeDevBypassPromptLengthGate = true,
        promptWrapperUsed = "none",
        promptWrapperFamily = "none",
        promptProfileHypothesis = "longer_japanese_greeting_question",
    ),
    SIMPLE_JA_ARITHMETIC_QUALITY(
        wireValue = "simple_ja_arithmetic_quality",
        displayLabel = "1+1",
        prompt = "1+1は？",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE,
        unsafeDevBypassPromptLengthGate = false,
        promptWrapperUsed = "none",
        promptWrapperFamily = "none",
        promptProfileHypothesis = "short_arithmetic_prompt_response_check",
    ),
    SHORT_JA_SELF_INTRO_QUALITY(
        wireValue = "short_ja_self_intro_quality",
        displayLabel = "Self intro",
        prompt = "日本語で短く自己紹介してください。",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
        unsafeDevBypassPromptLengthGate = true,
        promptWrapperUsed = "none",
        promptWrapperFamily = "none",
        promptProfileHypothesis = "instruction_prompt_response_check",
    ),
    NO_HIDDEN_TEMPLATE_QUALITY(
        wireValue = "no_hidden_template_quality",
        displayLabel = "No wrapper legacy/failing",
        prompt = "こんにちは",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE,
        unsafeDevBypassPromptLengthGate = false,
        promptWrapperUsed = "none",
        promptWrapperFamily = "none",
        promptProfileHypothesis = "greeting_without_hidden_template_mode",
    ),
    GEMMA_IT_USER_MODEL(
        wireValue = "gemma_it_user_model",
        displayLabel = "Gemma user/model recommended",
        prompt = "<start_of_turn>user\nこんにちは<end_of_turn>\n<start_of_turn>model\n",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
        unsafeDevBypassPromptLengthGate = true,
        promptWrapperUsed = "gemma_it_user_model",
        promptWrapperFamily = "gemma_it",
        promptProfileHypothesis = "gemma_instruction_turn_format_may_align_decode_start",
    ),
    GEMMA_IT_USER_MODEL_FULL_20_QUALITY(
        wireValue = "gemma_it_user_model_full_20_quality",
        displayLabel = "Gemma recommended x20",
        prompt = "<start_of_turn>user\nこんにちは<end_of_turn>\n<start_of_turn>model\n",
        runCount = NPU_S1_QUALITY_GATE_REQUIRED_RUN_COUNT,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
        unsafeDevBypassPromptLengthGate = true,
        promptWrapperUsed = "gemma_it_user_model",
        promptWrapperFamily = "gemma_it",
        promptProfileHypothesis = "gemma_instruction_turn_format_20_run_quality_gate",
    ),
    GEMMA_IT_START_TURN(
        wireValue = "gemma_it_start_turn",
        displayLabel = "Gemma start",
        prompt = "<start_of_turn>user\nこんにちは\n<start_of_turn>model\n",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
        unsafeDevBypassPromptLengthGate = true,
        promptWrapperUsed = "gemma_it_start_turn",
        promptWrapperFamily = "gemma_it",
        promptProfileHypothesis = "start_turn_without_end_turn_boundary_check",
    ),
    AI_EDGE_GALLERY_LIKE(
        wireValue = "ai_edge_gallery_like",
        displayLabel = "Gallery-like alias",
        prompt = "<start_of_turn>user\nこんにちは<end_of_turn>\n<start_of_turn>model\n",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
        unsafeDevBypassPromptLengthGate = true,
        promptWrapperUsed = "ai_edge_gallery_like",
        promptWrapperFamily = "ai_edge_gallery_like",
        promptProfileHypothesis = "gallery_like_turn_wrapper_quality_check",
    ),
    USER_COLON_ASSISTANT_COLON(
        wireValue = "user_colon_assistant_colon",
        displayLabel = "User/Assistant legacy/failing",
        prompt = "User: こんにちは\nAssistant:",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
        unsafeDevBypassPromptLengthGate = true,
        promptWrapperUsed = "user_colon_assistant_colon",
        promptWrapperFamily = "plain_role_colon",
        promptProfileHypothesis = "plain_role_prefix_may_anchor_assistant_answer",
    ),
    ASSISTANT_PREFIX_ONLY(
        wireValue = "assistant_prefix_only",
        displayLabel = "Assistant only legacy/failing",
        prompt = "こんにちは\nAssistant:",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
        unsafeDevBypassPromptLengthGate = true,
        promptWrapperUsed = "assistant_prefix_only",
        promptWrapperFamily = "plain_role_colon",
        promptProfileHypothesis = "assistant_prefix_without_user_role_check",
    ),
    JAPANESE_INSTRUCTION_WITH_ANSWER_PREFIX(
        wireValue = "japanese_instruction_with_answer_prefix",
        displayLabel = "JA answer",
        prompt = "以下に短く自然な日本語で答えてください。\n質問: こんにちは\n回答:",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
        unsafeDevBypassPromptLengthGate = true,
        promptWrapperUsed = "japanese_instruction_with_answer_prefix",
        promptWrapperFamily = "japanese_instruction",
        promptProfileHypothesis = "japanese_answer_prefix_may_reduce_template_leak",
    ),
    NO_BOS_NO_EOS(
        wireValue = "no_bos_no_eos",
        displayLabel = "No BOS/EOS legacy/failing",
        prompt = "こんにちは",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE,
        unsafeDevBypassPromptLengthGate = false,
        promptWrapperUsed = "no_bos_no_eos",
        promptWrapperFamily = "none",
        promptProfileHypothesis = "explicit_no_special_marker_baseline",
    ),
    BOS_EOS_LIKE_IF_SUPPORTED_BY_EXISTING_CODE(
        wireValue = "bos_eos_like_if_supported_by_existing_code",
        displayLabel = "BOS/EOS-like unsafe",
        prompt = "<bos>こんにちは<eos>",
        runCount = 3,
        promptValidationMode = NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE,
        unsafeDevBypassPromptLengthGate = true,
        promptWrapperUsed = "bos_eos_like_if_supported_by_existing_code",
        promptWrapperFamily = "textual_special_marker",
        promptProfileHypothesis = "textual_bos_eos_marker_only_no_token_id_support_without_native_rebuild",
    ),
}

internal interface NpuS1PersistentCustomJniProbeRunner {
    suspend fun run(
        mode: NpuS1PersistentCustomJniProbeMode,
        qualityPromptProfile: NpuS1PersistentCustomJniQualityPromptProfile,
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
    val outputOnlyNewline: String = "unavailable",
    val outputEmpty: String = "unavailable",
    val outputQualityCandidateStatus: String = "unavailable",
    val outputQualityCandidateReason: String = "unavailable",
    val prefillInputText: String = "unavailable",
    val prefillInputChars: String = "unavailable",
    val decodeFirstChunkText: String = "unavailable",
    val decodeFirstChunkChars: String = "unavailable",
    val decodeFirstNonEmptyChunkText: String = "unavailable",
    val decodeFirstNonEmptyChunkChars: String = "unavailable",
    val outputFirst1Char: String = "unavailable",
    val outputFirst5Chars: String = "unavailable",
    val outputFirst20Chars: String = "unavailable",
    val outputLast20Chars: String = "unavailable",
    val outputLengthChars: String = "unavailable",
    val outputNewlineCount: String = "unavailable",
    val outputLeadingPunctuationCount: String = "unavailable",
    val outputTrimmedFirstChars: String = "unavailable",
    val outputAfterLstripFirstChars: String = "unavailable",
    val outputEqualsAcrossRuns: String = "unavailable",
    val prefillTokenCount: String = "unavailable",
    val decodeTokenCount: String = "unavailable",
    val firstOutputTokenId: String = "unavailable",
    val firstOutputTokenText: String = "unavailable",
    val first5OutputTokenIds: String = "unavailable",
    val first5OutputTokenTexts: String = "unavailable",
    val eosSeen: String = "unavailable",
    val bosSeenInOutput: String = "unavailable",
    val specialTokenSeenInOutput: String = "unavailable",
    val tokenDiagnosticsNote: String = NPU_S1_TOKEN_DIAGNOSTICS_UNAVAILABLE_NOTE,
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
    val selectedQualityPromptProfile: String =
        NpuS1PersistentCustomJniQualityPromptProfile.CURRENT_PROBE_QUALITY.wireValue,
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
    val promptWrapperFamily: String = "unavailable",
    val promptProfileHypothesis: String = "unavailable",
    val prefillTextOrTokenNote: String = "unavailable",
    val firstOutputChars: String = "unavailable",
    val outputPrefixClassification: String = "unavailable",
    val outputQualityReason: String = "unavailable",
    val outputRepeatsSameAcrossRuns: String = "unavailable",
    val outputLooksBusinessTemplate: String = "unavailable",
    val outputStartsWithPunctuation: String = "unavailable",
    val outputContainsPlaceholder: String = "unavailable",
    val outputOnlyNewline: String = "unavailable",
    val outputEmpty: String = "unavailable",
    val outputEqualsAcrossRuns: String = "unavailable",
    val tokenDiagnosticsNote: String = NPU_S1_TOKEN_DIAGNOSTICS_UNAVAILABLE_NOTE,
    val prefillTokenCount: String = "unavailable",
    val decodeTokenCount: String = "unavailable",
    val firstOutputTokenId: String = "unavailable",
    val firstOutputTokenText: String = "unavailable",
    val first5OutputTokenIds: String = "unavailable",
    val first5OutputTokenTexts: String = "unavailable",
    val eosSeen: String = "unavailable",
    val bosSeenInOutput: String = "unavailable",
    val specialTokenSeenInOutput: String = "unavailable",
    val qualityComparisonPromptSet: String =
        NpuS1PersistentCustomJniQualityPromptProfile.entries.joinToString(",") { it.wireValue },
    val recommendedPromptProfile: String = NPU_S1_RECOMMENDED_PROMPT_PROFILE,
    val recommendedPromptProfileReason: String = NPU_S1_RECOMMENDED_PROMPT_PROFILE_REASON,
    val promptProfileAliasNote: String = NPU_S1_PROMPT_PROFILE_ALIAS_NOTE,
    val unsafePromptProfileNote: String = NPU_S1_UNSAFE_PROMPT_PROFILE_NOTE,
    val outputQualityCandidateStatus: String = NPU_S1_OUTPUT_QUALITY_CANDIDATE_UNKNOWN,
    val outputQualityCandidateReason: String = "unavailable",
    val outputQualityCandidatePreparedOutput: String = "unavailable",
    val outputQualityCandidateLeadingGreaterThanRemoved: String = "unavailable",
    val outputQualityCandidateEndOfTurnRemoved: String = "unavailable",
    val outputQualityCandidateAssistantRepetition: String = "unavailable",
    val outputQualityCandidateQaContinuation: String = "unavailable",
    val firstQualityFailureRunIndex: String = "unavailable",
    val firstQualityFailureReason: String = "unavailable",
    val failedQualityRunCount: String = "unavailable",
    val qualityGateAllRunsPassed: String = "unavailable",
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

internal data class NpuS1QualityGateResult(
    val status: String,
    val reason: String,
    val promptProfile: String,
    val runCountRequired: Int = NPU_S1_QUALITY_GATE_REQUIRED_RUN_COUNT,
    val runCountCompleted: Int = 0,
    val allRunsPassed: Boolean = false,
    val twentyRunStatus: String = "not_run",
    val firstQualityFailureRunIndex: String = "unavailable",
    val firstQualityFailureReason: String = "unavailable",
    val failedQualityRunCount: String = "unavailable",
)

internal data class NpuS1NormalChatUnblockReadinessResult(
    val status: String,
    val reason: String,
    val requiredProfile: String =
        NpuS1PersistentCustomJniQualityPromptProfile.GEMMA_IT_USER_MODEL_FULL_20_QUALITY.wireValue,
    val required20RunGate: Boolean = true,
    val policyAllowed: Boolean = NpuStandardRouteS1ProviderSelector.NORMAL_CHAT_NATIVE_ROUTE_UNBLOCK_ALLOWED,
)

internal data class NpuS1PersistentCustomJniOutputQualityDiagnostics(
    val outputPrefix20Chars: String,
    val startsWithPunctuation: Boolean,
    val containsBusinessPhrase: Boolean,
    val containsPlaceholder: Boolean,
    val outputOnlyNewline: Boolean,
    val outputEmpty: Boolean,
    val qualityClassification: String,
    val reason: String,
)

internal data class NpuS1PersistentCustomJniQualityCandidateResult(
    val status: String,
    val reason: String,
    val preparedOutput: String,
    val leadingGreaterThanRemoved: Boolean,
    val endOfTurnRemoved: Boolean,
    val placeholderLeak: Boolean,
    val businessTemplateLeak: Boolean,
    val assistantRepetition: Boolean,
    val qaContinuation: Boolean,
    val outputEmpty: Boolean,
    val outputOnlyNewline: Boolean,
    val arithmeticTailLeakDetected: Boolean = false,
    val arithmeticTailLeakIgnoredForDisplay: Boolean = false,
)

internal data class NpuS1PersistentCustomJniTokenBoundaryDiagnostics(
    val decodeFirstChunkText: String,
    val decodeFirstChunkChars: String,
    val decodeFirstNonEmptyChunkText: String,
    val decodeFirstNonEmptyChunkChars: String,
    val outputFirst1Char: String,
    val outputFirst5Chars: String,
    val outputFirst20Chars: String,
    val outputLast20Chars: String,
    val outputLengthChars: String,
    val outputNewlineCount: String,
    val outputLeadingPunctuationCount: String,
    val outputTrimmedFirstChars: String,
    val outputAfterLstripFirstChars: String,
)

internal fun classifyNpuS1PersistentCustomJniOutputQuality(
    output: String,
    prompt: String = "",
    outputEqualsAcrossRuns: Boolean = false,
): NpuS1PersistentCustomJniOutputQualityDiagnostics {
    val visibleOutput = output.trimStart()
    val startsWithPunctuation = visibleOutput.firstOrNull()?.let(::isSuspiciousJapanesePrefixPunctuation) == true
    val containsBusinessPhrase = listOf(
        "いつもお世話になっております",
        "お世話になっております",
        "よろしくお願いいたします",
    ).any(output::contains)
    val containsPlaceholder = Regex("""\[[^\]]+\]""").containsMatchIn(output)
    val outputEmpty = output.isEmpty()
    val outputOnlyNewline = output.isNotEmpty() && output.all { it == '\n' || it == '\r' }
    val promptIgnoredSuspect = prompt.isNotBlank() &&
        prompt.length <= 16 &&
        containsPlaceholder
    val classification = when {
        outputEmpty || outputOnlyNewline -> NPU_S1_OUTPUT_QUALITY_UNKNOWN
        outputEqualsAcrossRuns && (containsPlaceholder || containsBusinessPhrase) ->
            NPU_S1_OUTPUT_QUALITY_REPEATED_TEMPLATE_OUTPUT
        promptIgnoredSuspect -> NPU_S1_OUTPUT_QUALITY_PROMPT_IGNORED_SUSPECT
        containsPlaceholder -> NPU_S1_OUTPUT_QUALITY_PLACEHOLDER_LEAK
        containsBusinessPhrase -> NPU_S1_OUTPUT_QUALITY_TEMPLATE_LEAK
        startsWithPunctuation -> NPU_S1_OUTPUT_QUALITY_PUNCTUATION_START
        output.isBlank() -> NPU_S1_OUTPUT_QUALITY_UNKNOWN
        else -> NPU_S1_OUTPUT_QUALITY_NATURAL_JAPANESE
    }
    val reasons = buildList {
        if (startsWithPunctuation) add("starts_with_punctuation")
        if (startsWithPunctuation) add("first_token_boundary_suspect")
        if (containsBusinessPhrase) add("business_template_phrase")
        if (containsPlaceholder) add("placeholder_leak")
        if (promptIgnoredSuspect) add("prompt_ignored_suspect")
        if (outputEqualsAcrossRuns) add("repeated_template_output")
        if (startsWithPunctuation && containsPlaceholder) add("decode_offset_suspect")
        if (outputOnlyNewline) add("newline_only")
        if (outputEmpty) add("empty_output")
    }
    return NpuS1PersistentCustomJniOutputQualityDiagnostics(
        outputPrefix20Chars = output.take(20),
        startsWithPunctuation = startsWithPunctuation,
        containsBusinessPhrase = containsBusinessPhrase,
        containsPlaceholder = containsPlaceholder,
        outputOnlyNewline = outputOnlyNewline,
        outputEmpty = outputEmpty,
        qualityClassification = classification,
        reason = reasons.ifEmpty { listOf("no_quality_issue_detected") }.joinToString("+"),
    )
}

internal fun evaluateNpuS1PersistentCustomJniQualityCandidate(
    rawOutput: String,
    sanitizedOutput: String,
    inputPrompt: String = "",
): NpuS1PersistentCustomJniQualityCandidateResult {
    val initial = evaluateNpuS1PersistentCustomJniQualityCandidateCore(
        rawOutput = rawOutput,
        sanitizedOutput = sanitizedOutput,
        inputPrompt = inputPrompt,
    )
    if (initial.status == NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS) return initial

    val repair = extractNpuS1RepairableTurnBody(rawOutput) ?: return initial
    val repaired = evaluateNpuS1PersistentCustomJniQualityCandidateCore(
        rawOutput = repair.text,
        sanitizedOutput = repair.text,
        inputPrompt = inputPrompt,
    )
    if (repaired.status != NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS || repaired.preparedOutput.isBlank()) {
        return initial
    }
    return repaired.copy(reason = repair.successReason)
}

private data class NpuS1RepairableTurnBody(
    val text: String,
    val successReason: String,
)

private val npuS1CompleteTurnMarker = Regex(
    """<\s*/?\s*(?:start_of_turn|end_of_turn)\s*>""",
    RegexOption.IGNORE_CASE,
)

private val npuS1ModelTurnMarker = Regex(
    """<\s*start_of_turn\s*>\s*model\s*>?""",
    RegexOption.IGNORE_CASE,
)

private val npuS1UserTurnMarker = Regex(
    """<\s*start_of_turn\s*>\s*user\s*>?""",
    RegexOption.IGNORE_CASE,
)

private fun extractNpuS1RepairableTurnBody(rawOutput: String): NpuS1RepairableTurnBody? {
    val modelMarker = npuS1ModelTurnMarker.find(rawOutput)
    if (modelMarker != null) {
        val bodyStart = modelMarker.range.last + 1
        val nextMarker = npuS1CompleteTurnMarker.find(rawOutput, bodyStart)
        val bodyEnd = nextMarker?.range?.first ?: rawOutput.length
        val body = rawOutput.substring(bodyStart, bodyEnd).trim()
        if (body.isNotBlank()) {
            return NpuS1RepairableTurnBody(
                text = body,
                successReason = "natural_japanese_after_model_turn_extraction_and_revalidation",
            )
        }
    }

    val userMarker = npuS1UserTurnMarker.find(rawOutput) ?: return null
    val prefix = rawOutput.substring(0, userMarker.range.first).trim()
    if (prefix.isBlank()) return null
    return NpuS1RepairableTurnBody(
        text = prefix,
        successReason = "natural_japanese_after_tail_turn_leak_prefix_revalidation",
    )
}

private fun evaluateNpuS1PersistentCustomJniQualityCandidateCore(
    rawOutput: String,
    sanitizedOutput: String,
    inputPrompt: String = "",
): NpuS1PersistentCustomJniQualityCandidateResult {
    val rawRecoveryAllowed = sanitizedOutput.isBlank() &&
        hasSafeNpuS1EndOfTurnVariant(rawOutput) &&
        !containsNpuS1Hangul(rawOutput)
    val source = when {
        sanitizedOutput.isNotBlank() -> sanitizedOutput
        rawRecoveryAllowed -> rawOutput
        else -> ""
    }
    val cleanupSource = removeSafeNpuS1EndOfTurnVariants(source)
    val cleanupRaw = removeSafeNpuS1EndOfTurnVariants(rawOutput)
    val cleanupSanitized = removeSafeNpuS1EndOfTurnVariants(sanitizedOutput)
    val arithmeticPrompt = isNpuS1ArithmeticPrompt(inputPrompt)
    val trimmedStart = cleanupSource.trimStart()
    val preparedBase = trimmedStart.removePrefix(">").trimStart().trimEnd()
    val preparedWithoutPromptEcho = removeNpuS1LeadingPromptEcho(
        text = preparedBase,
        inputPrompt = inputPrompt,
    )
    val prepared = if (arithmeticPrompt) {
        extractNpuS1ArithmeticPreparedAnswer(preparedWithoutPromptEcho)
    } else {
        preparedWithoutPromptEcho
    }
    val leadingGreaterThanRemoved = cleanupRaw.trimStart().startsWith(">") || trimmedStart.startsWith(">")
    val endOfTurnRemoved = hasSafeNpuS1EndOfTurnVariant(rawOutput) ||
        hasSafeNpuS1EndOfTurnVariant(source) ||
        hasSafeNpuS1EndOfTurnVariant(sanitizedOutput)
    val qualityCheckText = listOf(rawOutput, sanitizedOutput, prepared).joinToString("\n")
    val visibleOutputCheckText = listOf(cleanupSanitized, prepared).joinToString("\n")
    val unsafeRawCheckText = cleanupRaw
    val placeholderLeak = Regex("""\[[^\]]+\]""").containsMatchIn(qualityCheckText)
    val businessTemplateLeak = listOf(
        "いつもお世話になっております",
        "お世話になっております",
        "よろしくお願いいたします",
    ).any(qualityCheckText::contains)
    val assistantRepetition = Regex("""(?i)(Assistant\s*:.*){2,}""").containsMatchIn(qualityCheckText) ||
        qualityCheckText.contains("Assistant: Assistant:", ignoreCase = true)
    val qaContinuation = listOf("質問:", "回答:", "Q:", "A:").any(qualityCheckText::contains)
    val specialTokenLeak = containsNpuS1SpecialTurnMarker(visibleOutputCheckText)
    val rawUnclosedSpecialToken = containsNpuS1UnclosedSpecialTurnMarker(unsafeRawCheckText)
    val rawUnexpectedStartTurn = unsafeRawCheckText.contains("<start_of_turn", ignoreCase = true) ||
        unsafeRawCheckText.contains("< start_of_turn", ignoreCase = true)
    val userTurnLeak = containsNpuS1UserTurnLeak(qualityCheckText)
    val arithmeticTailLeakDetected = arithmeticPrompt &&
        isNpuS1SingleArithmeticAnswer(prepared) &&
        (specialTokenLeak || rawUnexpectedStartTurn || userTurnLeak) &&
        isNpuS1ArithmeticLeakAfterPreparedAnswer(
            rawOutput = rawOutput,
            sanitizedOutput = sanitizedOutput,
            prepared = prepared,
        )
    val arithmeticTailLeakIgnoredForDisplay = arithmeticTailLeakDetected
    val promptRepetitionOnly = isNpuS1PromptRepetitionOnly(
        prompt = inputPrompt,
        output = prepared,
    )
    val arithmeticAnswerMissing = arithmeticPrompt && !containsNpuS1ArithmeticAnswerTwo(prepared)
    val bulletListRequired = inputPrompt.contains("箇条書き")
    val normalizedBulletPrompt = inputPrompt.map { char ->
        if (char in '０'..'９') ('0'.code + (char.code - '０'.code)).toChar() else char
    }.joinToString("")
    val explicitRequiredBulletCountToken = Regex("""(?<!\d)(\d+)\s*(?:つ|個|項目)""")
        .find(normalizedBulletPrompt)
        ?.groupValues
        ?.getOrNull(1)
    val explicitRequiredBulletCount = explicitRequiredBulletCountToken?.toIntOrNull()
    val explicitRequiredBulletCountInvalid = explicitRequiredBulletCountToken != null &&
        (explicitRequiredBulletCount == null || explicitRequiredBulletCount !in 1..100)
    val minimumBulletCount = 2
    val structuredTaskListRequested = bulletListRequired ||
        (explicitRequiredBulletCountToken != null && NPU_S1_TASK_LIST_INTENT_MARKERS.any(inputPrompt::contains))
    val selfIntroTemplateLeak = qualityCheckText.contains("**自己紹介") ||
        qualityCheckText.contains("---") ||
        (qualityCheckText.contains("〇〇") && !structuredTaskListRequested)
    val bulletItemLines = prepared.lines().filter(NPU_S1_BULLET_ITEM_LINE_PATTERN::matches)
    val bulletItemCount = bulletItemLines.size
    val repetitivePlaceholderOutput =
        NPU_S1_REPETITIVE_CIRCLE_PATTERN.containsMatchIn(prepared) ||
            bulletItemLines.any(::containsNpuS1UnresolvedCirclePlaceholder)
    val bulletListRequirementNotMet = when {
        !bulletListRequired -> false
        explicitRequiredBulletCountInvalid -> true
        explicitRequiredBulletCount != null -> bulletItemCount != explicitRequiredBulletCount
        else -> bulletItemCount < minimumBulletCount
    }
    val outputEmpty = prepared.isEmpty()
    val outputOnlyNewline = source.isNotEmpty() && source.all { it == '\n' || it == '\r' }
    val preparedBlank = prepared.isBlank()
    val preparedLiteralNewlineOnly = prepared == "\\n"
    val failedReasons = buildList {
        if (rawOutput.isBlank()) add("raw_output_empty")
        if (sanitizedOutput.isBlank() && prepared.isBlank()) add("sanitized_output_empty")
        if (outputEmpty) add("prepared_output_empty")
        if (preparedBlank && !outputEmpty) add("prepared_output_blank")
        if (outputOnlyNewline) add("output_only_newline")
        if (preparedLiteralNewlineOnly) add("prepared_output_literal_newline_only")
        if (placeholderLeak) add("placeholder_leak")
        if (businessTemplateLeak) add("business_template_leak")
        if (assistantRepetition) add("assistant_repetition")
        if (qaContinuation) add("qa_continuation")
        if (selfIntroTemplateLeak) add("self_intro_template_leak")
        if (repetitivePlaceholderOutput) add("repetitive_placeholder_output")
        if (specialTokenLeak && !arithmeticTailLeakIgnoredForDisplay) add("special_token_leak")
        if (rawUnclosedSpecialToken) add("raw_unclosed_special_token")
        if (rawUnexpectedStartTurn && !arithmeticTailLeakIgnoredForDisplay) add("raw_unexpected_start_turn")
        if (userTurnLeak && !arithmeticTailLeakIgnoredForDisplay) add("user_turn_leak")
        if (promptRepetitionOnly) add("prompt_repetition_only")
        if (arithmeticAnswerMissing) add("arithmetic_answer_missing")
        if (bulletListRequirementNotMet) add("bullet_list_requirement_not_met")
    }
    return NpuS1PersistentCustomJniQualityCandidateResult(
        status = if (failedReasons.isEmpty()) {
            NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS
        } else {
            NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL
        },
        reason = when {
            failedReasons.isNotEmpty() -> failedReasons.joinToString("+")
            arithmeticTailLeakIgnoredForDisplay ->
                "natural_japanese_after_arithmetic_answer_extraction_with_tail_leak_cleanup"
            else -> "natural_japanese_after_safe_leading_gt_and_end_of_turn_cleanup"
        },
        preparedOutput = prepared,
        leadingGreaterThanRemoved = leadingGreaterThanRemoved,
        endOfTurnRemoved = endOfTurnRemoved,
        placeholderLeak = placeholderLeak,
        businessTemplateLeak = businessTemplateLeak,
        assistantRepetition = assistantRepetition,
        qaContinuation = qaContinuation,
        outputEmpty = outputEmpty,
        outputOnlyNewline = outputOnlyNewline,
        arithmeticTailLeakDetected = arithmeticTailLeakDetected,
        arithmeticTailLeakIgnoredForDisplay = arithmeticTailLeakIgnoredForDisplay,
    )
}

private fun containsNpuS1Hangul(text: String): Boolean =
    text.codePoints().anyMatch { codePoint ->
        Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL
    }

private fun removeSafeNpuS1EndOfTurnVariants(text: String): String {
    if (!hasSafeNpuS1EndOfTurnVariant(text)) return text
    val withoutTokenLines = text
        .lines()
        .filterNot { line -> NPU_S1_SAFE_END_OF_TURN_LINE_PATTERN.matches(line.trim()) }
        .joinToString("\n")
    return NPU_S1_SAFE_END_OF_TURN_TRAILING_PATTERN
        .replace(withoutTokenLines, "")
        .trimEnd()
}

private fun hasSafeNpuS1EndOfTurnVariant(text: String): Boolean =
    NPU_S1_SAFE_END_OF_TURN_ANY_PATTERN.containsMatchIn(text)

private val NPU_S1_SAFE_END_OF_TURN_ANY_PATTERN =
    Regex("""</?\s*end_of_turn\s*>?""", RegexOption.IGNORE_CASE)

private val NPU_S1_SAFE_END_OF_TURN_LINE_PATTERN =
    Regex("""</?\s*end_of_turn\s*>?""", RegexOption.IGNORE_CASE)

private val NPU_S1_SAFE_END_OF_TURN_TRAILING_PATTERN =
    Regex("""(?:\s*</?\s*end_of_turn\s*>?\s*)+$""", RegexOption.IGNORE_CASE)

private val NPU_S1_BULLET_ITEM_LINE_PATTERN =
    Regex("""^\s*(?:[-*+]\s+|\d+[.)]\s+|・\s*)\S.*$""")

private val NPU_S1_TASK_LIST_INTENT_MARKERS =
    listOf("やること", "予定", "タスク", "TODO", "todo")

private fun containsNpuS1UnresolvedCirclePlaceholder(text: String): Boolean =
    NPU_S1_SHORT_CIRCLE_PLACEHOLDER_PATTERN.findAll(text).any { match ->
        val suffix = text.substring(match.range.last + 1)
        NPU_S1_MEANINGFUL_CIRCLE_NAME_SUFFIXES.none(suffix::startsWith)
    }

private val NPU_S1_SHORT_CIRCLE_PLACEHOLDER_PATTERN = Regex("〇{2,}")

private val NPU_S1_MEANINGFUL_CIRCLE_NAME_SUFFIXES = listOf(
    "株式会社",
    "有限会社",
    "合同会社",
)

private val NPU_S1_REPETITIVE_CIRCLE_PATTERN =
    Regex("〇{8,}")

private fun containsNpuS1SpecialTurnMarker(text: String): Boolean =
    Regex("""</?\s*(?:start|end)_of_turn>?""", RegexOption.IGNORE_CASE).containsMatchIn(text)

private fun containsNpuS1UnclosedSpecialTurnMarker(text: String): Boolean =
    Regex("""</?\s*(?:start|end)_of_turn(?!>)""", RegexOption.IGNORE_CASE).containsMatchIn(text)

private fun containsNpuS1UserTurnLeak(text: String): Boolean =
    Regex("""<\s*start_of_turn\s*>\s*user""", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
        text.contains("ユーザー:", ignoreCase = true) ||
        text.contains("ユーザー：", ignoreCase = true)

private fun isNpuS1PromptRepetitionOnly(
    prompt: String,
    output: String,
): Boolean {
    val normalizedPrompt = normalizeNpuS1QualityComparisonText(prompt)
    val normalizedOutput = normalizeNpuS1QualityComparisonText(output)
    return normalizedPrompt.isNotBlank() &&
        normalizedOutput.isNotBlank() &&
        normalizedOutput == normalizedPrompt
}

private fun removeNpuS1LeadingPromptEcho(
    text: String,
    inputPrompt: String,
): String {
    val normalizedPrompt = normalizeNpuS1QualityComparisonText(inputPrompt)
    if (normalizedPrompt.isBlank() || text.isBlank()) return text
    val lines = text.lines()
    val firstMeaningfulIndex = lines.indexOfFirst { it.trim().isNotEmpty() }
    if (firstMeaningfulIndex < 0) return text
    val firstMeaningfulLine = lines[firstMeaningfulIndex].trim().trimStart('>').trim()
    if (normalizeNpuS1QualityComparisonText(firstMeaningfulLine) != normalizedPrompt) return text
    val remainingLines = lines.drop(firstMeaningfulIndex + 1)
    if (remainingLines.none { it.trim().isNotEmpty() }) return text
    return remainingLines
        .dropWhile { it.trim().isEmpty() }
        .joinToString("\n")
        .trim()
}

private fun isNpuS1ArithmeticPrompt(prompt: String): Boolean =
    normalizeNpuS1ArithmeticText(prompt) in setOf(
        "1+1",
        "1+1?",
        "1+1は",
        "1+1は?",
    )

private fun containsNpuS1ArithmeticAnswerTwo(output: String): Boolean =
    output.any { it == '2' || it == '２' }

private fun isNpuS1SingleArithmeticAnswer(output: String): Boolean =
    output.trim() in setOf("2", "２")

private fun isNpuS1ArithmeticLeakAfterPreparedAnswer(
    rawOutput: String,
    sanitizedOutput: String,
    prepared: String,
): Boolean {
    val rawTailLeakSafe = isNpuS1LeakAfterPreparedAnswer(rawOutput, prepared)
    val sanitizedTailLeakSafe = isNpuS1LeakAfterPreparedAnswer(sanitizedOutput, prepared)
    return rawTailLeakSafe && sanitizedTailLeakSafe
}

private fun isNpuS1LeakAfterPreparedAnswer(
    output: String,
    prepared: String,
): Boolean {
    if (output.isBlank()) return true
    val answerIndex = output.indexOf(prepared)
    if (answerIndex < 0) return false
    val firstLeakIndex = firstNpuS1TailLeakIndex(output)
    return firstLeakIndex == null || firstLeakIndex > answerIndex
}

private fun firstNpuS1TailLeakIndex(text: String): Int? =
    listOfNotNull(
        Regex("""</?\s*start_of_turn>?""", RegexOption.IGNORE_CASE).find(text)?.range?.first,
        Regex("""<\s*start_of_turn\s*>\s*user""", RegexOption.IGNORE_CASE).find(text)?.range?.first,
        text.indexOf("ユーザー:", ignoreCase = true).takeIf { it >= 0 },
        text.indexOf("ユーザー：", ignoreCase = true).takeIf { it >= 0 },
    ).minOrNull()

private fun extractNpuS1ArithmeticPreparedAnswer(output: String): String {
    val answerMatch = NPU_S1_ARITHMETIC_ANSWER_PREFIX_PATTERN
        .findAll(output)
        .lastOrNull()
    val candidate = answerMatch
        ?.let { match -> output.substring(match.range.last + 1) }
        ?: output
    val firstAnswerLine = candidate
        .lines()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: ""
    return NPU_S1_ARITHMETIC_ANSWER_TWO_PATTERN
        .find(firstAnswerLine)
        ?.value
        ?: firstAnswerLine
}

private val NPU_S1_ARITHMETIC_ANSWER_PREFIX_PATTERN =
    Regex("""(?:^|\n)\s*(?:答え|回答|Answer)\s*[:：]\s*""", RegexOption.IGNORE_CASE)

private val NPU_S1_ARITHMETIC_ANSWER_TWO_PATTERN =
    Regex("""[2２]""")

private fun normalizeNpuS1QualityComparisonText(text: String): String =
    normalizeNpuS1ArithmeticText(text)
        .replace("。", "")
        .replace(".", "")

private fun normalizeNpuS1ArithmeticText(text: String): String =
    text
        .filterNot { it.isWhitespace() }
        .replace('１', '1')
        .replace('２', '2')
        .replace('＋', '+')
        .replace('？', '?')

internal fun buildNpuS1PersistentCustomJniTokenBoundaryDiagnostics(
    output: String,
): NpuS1PersistentCustomJniTokenBoundaryDiagnostics {
    val firstNonEmptyLine = output.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    val trimmed = output.trimStart()
    return NpuS1PersistentCustomJniTokenBoundaryDiagnostics(
        decodeFirstChunkText = output.take(40),
        decodeFirstChunkChars = output.take(40).length.toString(),
        decodeFirstNonEmptyChunkText = firstNonEmptyLine.take(40),
        decodeFirstNonEmptyChunkChars = firstNonEmptyLine.take(40).length.toString(),
        outputFirst1Char = output.take(1),
        outputFirst5Chars = output.take(5),
        outputFirst20Chars = output.take(20),
        outputLast20Chars = output.takeLast(20),
        outputLengthChars = output.length.toString(),
        outputNewlineCount = output.count { it == '\n' }.toString(),
        outputLeadingPunctuationCount = trimmed
            .takeWhile(::isSuspiciousJapanesePrefixPunctuation)
            .length
            .toString(),
        outputTrimmedFirstChars = output.trim().take(20),
        outputAfterLstripFirstChars = trimmed.take(20),
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
            normalChatUnblockAllowed = true,
        )
    } else {
        NpuS1PromotionGateResult(
            status = NPU_S1_PROMOTION_GATE_STATUS_FAIL,
            reason = failedReasons.joinToString("+"),
        )
    }
}

internal fun evaluateNpuS1QualityGate(
    state: NpuS1PersistentCustomJniProbeState,
): NpuS1QualityGateResult {
    if (
        state.outputQualityCandidateStatus == NPU_S1_OUTPUT_QUALITY_CANDIDATE_UNKNOWN ||
        state.outputQualityCandidateStatus == "unavailable"
    ) {
        return NpuS1QualityGateResult(
            status = NPU_S1_QUALITY_GATE_STATUS_UNKNOWN,
            reason = "quality_candidate_not_run",
            promptProfile = state.selectedQualityPromptProfile,
            runCountCompleted = state.runCountCompleted,
            firstQualityFailureRunIndex = state.firstQualityFailureRunIndex,
            firstQualityFailureReason = state.firstQualityFailureReason,
            failedQualityRunCount = state.failedQualityRunCount,
        )
    }

    val failedReasons = buildList {
        if (
            state.selectedQualityPromptProfile !=
            NpuS1PersistentCustomJniQualityPromptProfile.GEMMA_IT_USER_MODEL_FULL_20_QUALITY.wireValue
        ) {
            add("prompt_profile_not_gemma_it_user_model_full_20_quality")
        }
        if (state.runCountCompleted != NPU_S1_QUALITY_GATE_REQUIRED_RUN_COUNT) add("run_count_completed_not_20")
        if (state.successCount != NPU_S1_QUALITY_GATE_REQUIRED_RUN_COUNT) add("success_count_not_20")
        if (state.failureCount != 0) add("failure_count_not_zero")
        if (state.decodeSuccessCount != NPU_S1_QUALITY_GATE_REQUIRED_RUN_COUNT.toString()) {
            add("decode_success_count_not_20")
        }
        if (state.engineCloseReached != "true") add("engine_close_reached_not_true")
        if (state.engineCloseSuccess != "true") add("engine_close_success_not_true")
        if (state.outputQualityCandidateStatus != NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS) {
            add("quality_candidate_not_pass")
        }
        if (state.qualityGateAllRunsPassed != "true") add("quality_gate_all_runs_not_passed")
        if (state.failedQualityRunCount != "0") add("failed_quality_run_count_not_zero")
        if (state.outputEmpty != "false") add("output_empty_not_false")
        if (state.outputOnlyNewline != "false") add("output_only_newline_not_false")
        if (state.outputContainsPlaceholder != "false") add("output_contains_placeholder_not_false")
        if (state.outputLooksBusinessTemplate != "false") add("output_looks_business_template_not_false")
        if (state.outputQualityCandidateAssistantRepetition != "false") {
            add("assistant_repetition_not_false")
        }
        if (state.outputQualityCandidateQaContinuation != "false") {
            add("qa_continuation_not_false")
        }
    }

    return if (failedReasons.isEmpty()) {
        NpuS1QualityGateResult(
            status = NPU_S1_QUALITY_GATE_STATUS_PASS,
            reason = "gemma_it_user_model_full_20_quality_candidate_pass",
            promptProfile = state.selectedQualityPromptProfile,
            runCountCompleted = state.runCountCompleted,
            allRunsPassed = true,
            twentyRunStatus = "pass",
            firstQualityFailureRunIndex = state.firstQualityFailureRunIndex,
            firstQualityFailureReason = state.firstQualityFailureReason,
            failedQualityRunCount = state.failedQualityRunCount,
        )
    } else {
        NpuS1QualityGateResult(
            status = NPU_S1_QUALITY_GATE_STATUS_FAIL,
            reason = failedReasons.joinToString("+"),
            promptProfile = state.selectedQualityPromptProfile,
            runCountCompleted = state.runCountCompleted,
            allRunsPassed = state.qualityGateAllRunsPassed == "true",
            twentyRunStatus = "fail",
            firstQualityFailureRunIndex = state.firstQualityFailureRunIndex,
            firstQualityFailureReason = state.firstQualityFailureReason,
            failedQualityRunCount = state.failedQualityRunCount,
        )
    }
}

internal fun evaluateNpuS1NormalChatUnblockReadiness(
    promotionGate: NpuS1PromotionGateResult,
    qualityGate: NpuS1QualityGateResult,
): NpuS1NormalChatUnblockReadinessResult {
    val failedReasons = buildList {
        if (promotionGate.status != NPU_S1_PROMOTION_GATE_STATUS_PASS) {
            add("promotion_gate_not_pass")
        }
        if (qualityGate.status != NPU_S1_QUALITY_GATE_STATUS_PASS) {
            add("quality_gate_not_pass")
        }
        if (
            qualityGate.promptProfile !=
            NpuS1PersistentCustomJniQualityPromptProfile.GEMMA_IT_USER_MODEL_FULL_20_QUALITY.wireValue
        ) {
            add("required_profile_not_gemma_it_user_model_full_20_quality")
        }
        if (qualityGate.twentyRunStatus != "pass") {
            add("quality_20_run_status_not_pass")
        }
    }
    if (failedReasons.isNotEmpty()) {
        return NpuS1NormalChatUnblockReadinessResult(
            status = NPU_S1_NORMAL_CHAT_UNBLOCK_READINESS_NOT_READY,
            reason = failedReasons.joinToString("+"),
        )
    }
    return if (NpuStandardRouteS1ProviderSelector.NORMAL_CHAT_NATIVE_ROUTE_UNBLOCK_ALLOWED) {
        NpuS1NormalChatUnblockReadinessResult(
            status = "ready_and_policy_allowed",
            reason = "final_gates_pass_and_policy_allows_unblock",
        )
    } else {
        NpuS1NormalChatUnblockReadinessResult(
            status = NPU_S1_NORMAL_CHAT_UNBLOCK_READINESS_READY_BUT_BLOCKED_BY_POLICY,
            reason = "final_gates_pass_but_normal_chat_native_route_unblock_policy_false",
        )
    }
}

internal fun formatNpuS1PersistentCustomJniDiagnosticsForDev(
    state: NpuS1PersistentCustomJniProbeState,
): String = buildString {
    val promotionGate = evaluateNpuS1PromotionGate(state)
    val qualityGate = evaluateNpuS1QualityGate(state)
    val normalChatUnblockReadiness = evaluateNpuS1NormalChatUnblockReadiness(
        promotionGate = promotionGate,
        qualityGate = qualityGate,
    )
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
    appendLine("selected_quality_prompt_profile=${state.selectedQualityPromptProfile}")
    appendLine("quality_comparison_prompt_set=${state.qualityComparisonPromptSet}")
    appendLine("npu_s1_recommended_prompt_profile=${state.recommendedPromptProfile}")
    appendLine(
        "npu_s1_recommended_prompt_profile_reason=" +
            escapePersistentCustomJniCopyValue(state.recommendedPromptProfileReason),
    )
    appendLine("npu_s1_prompt_profile_alias_note=${escapePersistentCustomJniCopyValue(state.promptProfileAliasNote)}")
    appendLine("npu_s1_unsafe_prompt_profile_note=${escapePersistentCustomJniCopyValue(state.unsafePromptProfileNote)}")
    appendLine("npu_s1_quality_gate_status=${qualityGate.status}")
    appendLine("npu_s1_quality_gate_reason=${qualityGate.reason}")
    appendLine("npu_s1_quality_gate_prompt_profile=${qualityGate.promptProfile}")
    appendLine("npu_s1_quality_gate_run_count_required=${qualityGate.runCountRequired}")
    appendLine("npu_s1_quality_gate_run_count_completed=${qualityGate.runCountCompleted}")
    appendLine("npu_s1_quality_gate_all_runs_passed=${qualityGate.allRunsPassed}")
    appendLine("npu_s1_quality_gate_20_run_status=${qualityGate.twentyRunStatus}")
    appendLine("first_quality_failure_run_index=${qualityGate.firstQualityFailureRunIndex}")
    appendLine("first_quality_failure_reason=${escapePersistentCustomJniCopyValue(qualityGate.firstQualityFailureReason)}")
    appendLine("failed_quality_run_count=${qualityGate.failedQualityRunCount}")
    appendLine("npu_s1_normal_chat_unblock_readiness_status=${normalChatUnblockReadiness.status}")
    appendLine("npu_s1_normal_chat_unblock_readiness_reason=${normalChatUnblockReadiness.reason}")
    appendLine("npu_s1_normal_chat_unblock_required_profile=${normalChatUnblockReadiness.requiredProfile}")
    appendLine("npu_s1_normal_chat_unblock_required_20_run_gate=${normalChatUnblockReadiness.required20RunGate}")
    appendLine("npu_s1_normal_chat_unblock_policy_allowed=${normalChatUnblockReadiness.policyAllowed}")
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
    appendLine("prompt_wrapper_family=${state.promptWrapperFamily}")
    appendLine("prompt_profile_hypothesis=${state.promptProfileHypothesis}")
    appendLine("prefill_text_or_token_note=${escapePersistentCustomJniCopyValue(state.prefillTextOrTokenNote)}")
    appendLine("first_output_chars=${escapePersistentCustomJniCopyValue(state.firstOutputChars)}")
    appendLine("output_prefix_classification=${state.outputPrefixClassification}")
    appendLine("output_quality_reason=${state.outputQualityReason}")
    appendLine("output_repeats_same_across_runs=${state.outputRepeatsSameAcrossRuns}")
    appendLine("output_equals_across_runs=${state.outputEqualsAcrossRuns}")
    appendLine("output_looks_business_template=${state.outputLooksBusinessTemplate}")
    appendLine("output_starts_with_punctuation=${state.outputStartsWithPunctuation}")
    appendLine("output_contains_placeholder=${state.outputContainsPlaceholder}")
    appendLine("output_only_newline=${state.outputOnlyNewline}")
    appendLine("output_empty=${state.outputEmpty}")
    appendLine("output_quality_candidate_status=${state.outputQualityCandidateStatus}")
    appendLine("output_quality_candidate_reason=${state.outputQualityCandidateReason}")
    appendLine(
        "output_quality_candidate_prepared_output=" +
            escapePersistentCustomJniCopyValue(state.outputQualityCandidatePreparedOutput),
    )
    appendLine(
        "output_quality_candidate_leading_greater_than_removed=" +
            state.outputQualityCandidateLeadingGreaterThanRemoved,
    )
    appendLine("output_quality_candidate_end_of_turn_removed=${state.outputQualityCandidateEndOfTurnRemoved}")
    appendLine("output_quality_candidate_assistant_repetition=${state.outputQualityCandidateAssistantRepetition}")
    appendLine("output_quality_candidate_qa_continuation=${state.outputQualityCandidateQaContinuation}")
    appendLine("prefill_token_count=${state.prefillTokenCount}")
    appendLine("decode_token_count=${state.decodeTokenCount}")
    appendLine("first_output_token_id=${state.firstOutputTokenId}")
    appendLine("first_output_token_text=${state.firstOutputTokenText}")
    appendLine("first_5_output_token_ids=${state.first5OutputTokenIds}")
    appendLine("first_5_output_token_texts=${state.first5OutputTokenTexts}")
    appendLine("eos_seen=${state.eosSeen}")
    appendLine("bos_seen_in_output=${state.bosSeenInOutput}")
    appendLine("special_token_seen_in_output=${state.specialTokenSeenInOutput}")
    appendLine("token_diagnostics_note=${state.tokenDiagnosticsNote}")
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
    appendLine(
        "npu_s1_promotion_gate_normal_chat_unblock=" +
            if (NpuStandardRouteS1ProviderSelector.NORMAL_CHAT_NATIVE_ROUTE_UNBLOCK_ALLOWED) {
                "policy_allowed"
            } else {
                "blocked_by_policy"
            },
    )
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
            appendLine("prefill_input_text=${escapePersistentCustomJniCopyValue(record.prefillInputText)}")
            appendLine("prefill_input_chars=${record.prefillInputChars}")
            appendLine("decode_first_chunk_text=${escapePersistentCustomJniCopyValue(record.decodeFirstChunkText)}")
            appendLine("decode_first_chunk_chars=${record.decodeFirstChunkChars}")
            appendLine(
                "decode_first_non_empty_chunk_text=" +
                    escapePersistentCustomJniCopyValue(record.decodeFirstNonEmptyChunkText),
            )
            appendLine("decode_first_non_empty_chunk_chars=${record.decodeFirstNonEmptyChunkChars}")
            appendLine("raw_output=${escapePersistentCustomJniCopyValue(record.rawOutput)}")
            appendLine("sanitized_output=${escapePersistentCustomJniCopyValue(record.sanitizedOutput)}")
            appendLine("output_prefix_20_chars=${escapePersistentCustomJniCopyValue(record.outputPrefix20Chars)}")
            appendLine("output_first_1_char=${escapePersistentCustomJniCopyValue(record.outputFirst1Char)}")
            appendLine("output_first_5_chars=${escapePersistentCustomJniCopyValue(record.outputFirst5Chars)}")
            appendLine("output_first_20_chars=${escapePersistentCustomJniCopyValue(record.outputFirst20Chars)}")
            appendLine("output_last_20_chars=${escapePersistentCustomJniCopyValue(record.outputLast20Chars)}")
            appendLine("output_length_chars=${record.outputLengthChars}")
            appendLine("output_newline_count=${record.outputNewlineCount}")
            appendLine("output_leading_punctuation_count=${record.outputLeadingPunctuationCount}")
            appendLine("output_trimmed_first_chars=${escapePersistentCustomJniCopyValue(record.outputTrimmedFirstChars)}")
            appendLine(
                "output_after_lstrip_first_chars=" +
                    escapePersistentCustomJniCopyValue(record.outputAfterLstripFirstChars),
            )
            appendLine("output_equals_across_runs=${record.outputEqualsAcrossRuns}")
            appendLine("starts_with_punctuation=${record.startsWithPunctuation}")
            appendLine("contains_business_phrase=${record.containsBusinessPhrase}")
            appendLine("contains_placeholder=${record.containsPlaceholder}")
            appendLine("output_only_newline=${record.outputOnlyNewline}")
            appendLine("output_empty=${record.outputEmpty}")
            appendLine("output_quality_candidate_status=${record.outputQualityCandidateStatus}")
            appendLine("output_quality_candidate_reason=${record.outputQualityCandidateReason}")
            appendLine("prefill_token_count=${record.prefillTokenCount}")
            appendLine("decode_token_count=${record.decodeTokenCount}")
            appendLine("first_output_token_id=${record.firstOutputTokenId}")
            appendLine("first_output_token_text=${record.firstOutputTokenText}")
            appendLine("first_5_output_token_ids=${record.first5OutputTokenIds}")
            appendLine("first_5_output_token_texts=${record.first5OutputTokenTexts}")
            appendLine("eos_seen=${record.eosSeen}")
            appendLine("bos_seen_in_output=${record.bosSeenInOutput}")
            appendLine("special_token_seen_in_output=${record.specialTokenSeenInOutput}")
            appendLine("token_diagnostics_note=${record.tokenDiagnosticsNote}")
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
