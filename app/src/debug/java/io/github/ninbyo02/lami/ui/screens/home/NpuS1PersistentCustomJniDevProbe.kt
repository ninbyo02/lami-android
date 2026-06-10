package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.npu.Qairt244ModelPathResolver
import io.github.ninbyo02.lami.npu.Qairt244NpuOutputSanitizer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NpuS1PersistentCustomJniDevProbe(
    context: Context,
) : NpuS1PersistentCustomJniProbeRunner {
    private val appContext = context.applicationContext

    override suspend fun run(
        mode: NpuS1PersistentCustomJniProbeMode,
        qualityPromptProfile: NpuS1PersistentCustomJniQualityPromptProfile,
        onUpdate: (NpuS1PersistentCustomJniProbeState) -> Unit,
        isCancelled: () -> Boolean,
    ): NpuS1PersistentCustomJniProbeState = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        val cacheDir = appContext.cacheDir.absolutePath
        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path.orEmpty()
        val modelFile = modelPath.takeIf { it.isNotBlank() }?.let(::File)
        val modelFileSize = modelFile?.takeIf { it.exists() }?.length()?.toString() ?: "unavailable"
        val modelLastModified = modelFile?.takeIf { it.exists() }?.lastModified()?.toString() ?: "unavailable"
        val promptDiagnostics = buildPersistentCustomJniPromptDiagnostics(qualityPromptProfile)
        val holderKey = NpuS1PersistentCustomJniHolderKey(
            modelPath = modelPath.ifBlank { modelResolution.reasonCode },
            modelFileLastModified = modelLastModified,
            modelFileSize = modelFileSize,
            backend = NPU_S1_PERSISTENT_CUSTOM_JNI_BACKEND,
            cacheDir = cacheDir,
            maxTokenBudget = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS.toString(),
            engineConfigVersion = NPU_S1_PERSISTENT_CUSTOM_JNI_ENGINE_CONFIG_VERSION,
        )
        var state = NpuS1PersistentCustomJniProbeState(
            persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_RUNNING,
            runCountRequested = qualityPromptProfile.runCount,
            runCountCompletedOverride = 0,
            startedAtElapsedRealtimeMs = startedAt,
            engineCreateCount = "0",
            engineCloseReached = "false",
            engineCloseSuccess = "unavailable",
            holderKey = holderKey,
            holderGeneration = "unavailable",
            holderReusedCount = "0",
            holderInvalidated = "false",
            holderKeyMismatchDetected = "false",
            holderKeyMismatchReason = "unavailable",
            nativeHolderEntrypointAvailable = "false",
            selectedNativeProbeMode = mode.wireValue,
            selectedQualityPromptProfile = qualityPromptProfile.wireValue,
            modelPath = holderKey.modelPath,
            modelFileSize = modelFileSize,
            modelFileLastModified = modelLastModified,
            backendEvidence = "custom_jni_persistent_holder_probe_requested",
            persistentCustomJniHypothesisResult = "starting",
            promptInputLimitMode = promptDiagnostics.promptInputLimitMode,
            finalPromptText = promptDiagnostics.finalPromptText,
            finalPromptLengthChars = promptDiagnostics.finalPromptLengthChars,
            finalPromptTailPreview = promptDiagnostics.finalPromptTailPreview,
            systemTemplateUsed = promptDiagnostics.systemTemplateUsed,
            hiddenTemplateUsed = promptDiagnostics.hiddenTemplateUsed,
            promptWrapperUsed = promptDiagnostics.promptWrapperUsed,
            promptWrapperFamily = promptDiagnostics.promptWrapperFamily,
            promptProfileHypothesis = promptDiagnostics.promptProfileHypothesis,
            prefillTextOrTokenNote = promptDiagnostics.prefillTextOrTokenNote,
        )
        fun update(next: NpuS1PersistentCustomJniProbeState) {
            state = next
            onUpdate(next)
        }
        update(state)

        if (isCancelled()) {
            return@withContext state.copy(
                persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_CANCELLED,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                persistentCustomJniHypothesisResult = "cancelled",
            ).also(::update)
        }

        if (modelPath.isBlank()) {
            return@withContext state.copy(
                persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_STOPPED,
                finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                holderInvalidated = "true",
                firstFailureStage = "model_resolve",
                firstFailureReason = "model_resolution_failed:${modelResolution.reasonCode}",
                firstFailureExceptionClass = "unavailable",
                firstFailureDiagTail = "persistent_custom_jni_holder_not_started model_resolution=${modelResolution.reasonCode}",
                backendEvidence = "custom_jni_persistent_holder_model_unavailable",
                persistentCustomJniHypothesisResult = "model_resolution_failed",
            ).also(::update)
        }

        val runId = "npu_s1_persistent_custom_jni_${SystemClock.elapsedRealtime()}"
        val nativeResult = Qairt244ShortMultitokenSmoke.runPersistentProbe(
            context = appContext,
            modelPath = modelPath,
            runId = runId,
            prompt = qualityPromptProfile.prompt,
            maxOutputTokens = NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS,
            runCount = qualityPromptProfile.runCount,
            holderKey = holderKey.stableText(),
            nativeProbeMode = mode.wireValue,
            promptValidationMode = qualityPromptProfile.promptValidationMode,
            unsafeDevBypassPromptLengthGate = qualityPromptProfile.unsafeDevBypassPromptLengthGate,
        )
        val parsedState = parsePersistentCustomJniProbeResult(
            result = nativeResult,
            fallbackState = state,
            holderKey = holderKey,
        )
        parsedState.copy(finishedAtElapsedRealtimeMs = SystemClock.elapsedRealtime())
            .also(::update)
    }
}

private fun parsePersistentCustomJniProbeResult(
    result: Qairt244PersistentProbeResult,
    fallbackState: NpuS1PersistentCustomJniProbeState,
    holderKey: NpuS1PersistentCustomJniHolderKey,
): NpuS1PersistentCustomJniProbeState {
    if (result.resultText.isBlank()) {
        val entrypointMissing = result.throwableClass.endsWith("UnsatisfiedLinkError") ||
            result.throwableClass == UnsatisfiedLinkError::class.java.name
        return fallbackState.copy(
            persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_STOPPED,
            nativeHolderEntrypointAvailable = if (entrypointMissing) "false" else "unavailable",
            holderInvalidated = "true",
            firstFailureStage = if (entrypointMissing) "native_holder_entrypoint" else "native_call",
            firstFailureReason = if (entrypointMissing) {
                "native_persistent_holder_entrypoint_not_available"
            } else {
                "native_persistent_probe_failed:${result.throwableClass}"
            },
            firstFailureExceptionClass = result.throwableClass.substringAfterLast('.'),
            firstFailureDiagTail = result.throwableMessage,
            backendEvidence = if (entrypointMissing) {
                "custom_jni_persistent_holder_entrypoint_missing"
            } else {
                "custom_jni_persistent_holder_native_call_failed_without_result"
            },
            persistentCustomJniHypothesisResult = if (entrypointMissing) {
                "native_holder_entrypoint_not_available"
            } else {
                "native_persistent_probe_failed_without_result"
            },
        )
    }

    val parsed = parsePersistentCustomJniKeyValueLines(result.resultText)
    val summary = parsed.summary
    val recordOutputs = parsed.records.map { values ->
        val rawOutput = values["raw_output"].orEmpty()
        val sanitized = values["sanitized_output"]
            ?.takeIf { it.isNotBlank() }
            ?: Qairt244NpuOutputSanitizer.sanitize(
                rawOutput = rawOutput,
                prompt = fallbackState.finalPromptText.takeIf { it != "unavailable" } ?: qualityPromptDefault(),
            ).sanitizedOutput
        rawOutput.ifBlank { sanitized }
    }
    val repeatedOutput = recordOutputs.filter { it.isNotBlank() }.distinct().size == 1 && recordOutputs.size > 1
    val records = parsed.records.map { values ->
        val rawOutput = values["raw_output"].orEmpty()
        val sanitized = values["sanitized_output"]
            ?.takeIf { it.isNotBlank() }
            ?: Qairt244NpuOutputSanitizer.sanitize(
                rawOutput = rawOutput,
                prompt = fallbackState.finalPromptText.takeIf { it != "unavailable" } ?: qualityPromptDefault(),
            ).sanitizedOutput
        val outputForDiagnostics = rawOutput.ifBlank { sanitized }
        val quality = classifyNpuS1PersistentCustomJniOutputQuality(
            output = outputForDiagnostics,
            prompt = fallbackState.finalPromptText.takeIf { it != "unavailable" }.orEmpty(),
            outputEqualsAcrossRuns = repeatedOutput,
        )
        val boundary = buildNpuS1PersistentCustomJniTokenBoundaryDiagnostics(outputForDiagnostics)
        val nativeQuality = values["quality_classification"].orUnavailable()
        val qualityClassification = if (quality.qualityClassification != NPU_S1_OUTPUT_QUALITY_NATURAL_JAPANESE) {
            quality.qualityClassification
        } else {
            nativeQuality
        }
        NpuS1PersistentCustomJniRunRecord(
            runIndex = values["run_index"]?.toIntOrNull() ?: 0,
            status = values["status"].orEmpty().ifBlank { "unavailable" },
            reason = values["reason"].orEmpty().ifBlank { "unavailable" },
            sessionCreated = values["session_created"].orUnavailable(),
            sessionClosed = values["session_closed"].orUnavailable(),
            prefillStarted = values["prefill_started"].orUnavailable(),
            prefillFinished = values["prefill_finished"].orUnavailable(),
            decodeStarted = values["decode_started"].orUnavailable(),
            decodeFinished = values["decode_finished"].orUnavailable(),
            rawOutput = rawOutput,
            sanitizedOutput = sanitized,
            outputPrefix20Chars = quality.outputPrefix20Chars,
            startsWithPunctuation = quality.startsWithPunctuation.toString(),
            containsBusinessPhrase = quality.containsBusinessPhrase.toString(),
            containsPlaceholder = quality.containsPlaceholder.toString(),
            outputOnlyNewline = quality.outputOnlyNewline.toString(),
            outputEmpty = quality.outputEmpty.toString(),
            prefillInputText = fallbackState.finalPromptText,
            prefillInputChars = fallbackState.finalPromptLengthChars,
            decodeFirstChunkText = boundary.decodeFirstChunkText,
            decodeFirstChunkChars = boundary.decodeFirstChunkChars,
            decodeFirstNonEmptyChunkText = boundary.decodeFirstNonEmptyChunkText,
            decodeFirstNonEmptyChunkChars = boundary.decodeFirstNonEmptyChunkChars,
            outputFirst1Char = boundary.outputFirst1Char,
            outputFirst5Chars = boundary.outputFirst5Chars,
            outputFirst20Chars = boundary.outputFirst20Chars,
            outputLast20Chars = boundary.outputLast20Chars,
            outputLengthChars = boundary.outputLengthChars,
            outputNewlineCount = boundary.outputNewlineCount,
            outputLeadingPunctuationCount = boundary.outputLeadingPunctuationCount,
            outputTrimmedFirstChars = boundary.outputTrimmedFirstChars,
            outputAfterLstripFirstChars = boundary.outputAfterLstripFirstChars,
            outputEqualsAcrossRuns = repeatedOutput.toString(),
            prefillTokenCount = values["prefill_token_count"].orUnavailable(),
            decodeTokenCount = values["decode_token_count"].orUnavailable(),
            firstOutputTokenId = values["first_output_token_id"].orUnavailable(),
            firstOutputTokenText = values["first_output_token_text"].orUnavailable(),
            first5OutputTokenIds = values["first_5_output_token_ids"].orUnavailable(),
            first5OutputTokenTexts = values["first_5_output_token_texts"].orUnavailable(),
            eosSeen = values["eos_seen"].orUnavailable(),
            bosSeenInOutput = values["bos_seen_in_output"].orUnavailable(),
            specialTokenSeenInOutput = values["special_token_seen_in_output"].orUnavailable(),
            tokenDiagnosticsNote = values["token_diagnostics_note"]
                ?: NPU_S1_TOKEN_DIAGNOSTICS_UNAVAILABLE_NOTE,
            qualityClassification = qualityClassification,
            totalMs = values["total_ms"].toNullableNonNegativeLong(),
            prefillMs = values["prefill_ms"].toNullableNonNegativeLong(),
            decodeMs = values["decode_ms"].toNullableNonNegativeLong(),
            cleanupMs = values["cleanup_ms"].toNullableNonNegativeLong(),
            failureStage = values["failure_stage"].orUnavailable(),
            failureExceptionClass = values["failure_exception_class"].orUnavailable(),
            failureExceptionMessage = values["failure_exception_message"].orUnavailable(),
            nativeDiagTail = values["native_diag_tail"]
                ?: result.diagText.lineSequence().lastOrNull().orUnavailable(),
        )
    }
    val qualitySummary = buildPersistentCustomJniQualitySummary(records)
    return fallbackState.copy(
        persistentCustomJniStatus = summary["persistent_custom_jni_status"].orUnavailable(),
        runCountCompletedOverride = summary["run_count_completed"]?.toIntOrNull(),
        engineCreateCount = summary["engine_create_count"].orUnavailable(),
        decodeAttemptCount = summary["decode_attempt_count"].orUnavailable(),
        decodeSuccessCount = summary["decode_success_count"].orUnavailable(),
        engineCloseReached = summary["engine_close_reached"].orUnavailable(),
        engineCloseSuccess = summary["engine_close_success"].orUnavailable(),
        holderKey = holderKey,
        holderGeneration = summary["holder_generation"].orUnavailable(),
        holderReusedCount = summary["holder_reused_count"].orUnavailable(),
        holderInvalidated = summary["holder_invalidated"].orUnavailable(),
        holderKeyMismatchDetected = summary["holder_key_mismatch_detected"].orUnavailable(),
        holderKeyMismatchReason = summary["holder_key_mismatch_reason"].orUnavailable(),
        nativeHolderEntrypointAvailable = summary["native_holder_entrypoint_available"].orUnavailable(),
        selectedNativeProbeMode = summary["selected_native_probe_mode"].orUnavailable(),
        selectedQualityPromptProfile = summary["selected_quality_prompt_profile"]
            ?: fallbackState.selectedQualityPromptProfile,
        lastNativeStage = summary["last_native_stage"].orUnavailable(),
        nativeEntrypointReached = summary["native_entrypoint_reached"].orUnavailable(),
        modelAssetsCreateReached = summary["model_assets_create_reached"].orUnavailable(),
        modelAssetsCreateReturned = summary["model_assets_create_returned"].orUnavailable(),
        engineSettingsCreateReached = summary["engine_settings_create_reached"].orUnavailable(),
        engineSettingsCreateReturned = summary["engine_settings_create_returned"].orUnavailable(),
        engineCreateReached = summary["engine_create_reached"].orUnavailable(),
        engineCreateReturned = summary["engine_create_returned"].orUnavailable(),
        sessionCreateReached = summary["session_create_reached"].orUnavailable(),
        prefillReached = summary["prefill_reached"].orUnavailable(),
        decodeReached = summary["decode_reached"].orUnavailable(),
        nativeDiagFlushCount = summary["native_diag_flush_count"].orUnavailable(),
        nativeResultFlushCount = summary["native_result_flush_count"].orUnavailable(),
        engineCreateModelPath = summary["engine_create_model_path"].orUnavailable(),
        engineCreateNativeLibraryDir = summary["engine_create_native_library_dir"].orUnavailable(),
        engineCreateCacheDir = summary["engine_create_cache_dir"].orUnavailable(),
        engineCreateBackend = summary["engine_create_backend"].orUnavailable(),
        engineCreatePromptInputLimitMode = summary["engine_create_prompt_input_limit_mode"].orUnavailable(),
        engineCreateRequestedMaxOutputTokens = summary["engine_create_requested_max_output_tokens"].orUnavailable(),
        engineCreateEffectiveMaxOutputTokens = summary["engine_create_effective_max_output_tokens"].orUnavailable(),
        engineCreateMaxTokenBudget = summary["engine_create_max_token_budget"].orUnavailable(),
        engineCreateSettingsSource = summary["engine_create_settings_source"].orUnavailable(),
        engineCreateAssetsSource = summary["engine_create_assets_source"].orUnavailable(),
        engineCreateMatchesEditablePromptPath = summary["engine_create_matches_editable_prompt_path"].orUnavailable(),
        engineCreateMatchesEditablePromptSettings =
            summary["engine_create_matches_editable_prompt_settings"].orUnavailable(),
        editablePromptEngineCreateSignature = summary["editable_prompt_engine_create_signature"].orUnavailable(),
        persistentEngineCreateSignature = summary["persistent_engine_create_signature"].orUnavailable(),
        engineCreateMinimalPath = summary["engine_create_minimal_path"].orUnavailable(),
        persistentHolderUsed = summary["persistent_holder_used"].orUnavailable(),
        firstFailureRunIndex = summary["first_failure_run_index"]?.toIntOrNull(),
        firstFailureStage = summary["first_failure_stage"].orUnavailable(),
        firstFailureReason = summary["first_failure_reason"].orUnavailable(),
        firstFailureExceptionClass = summary["first_failure_exception_class"].orUnavailable(),
        firstFailureDiagTail = summary["first_failure_diag_tail"]
            ?.takeIf { it != "unavailable" }
            ?: result.diagText.lineSequence().lastOrNull().orUnavailable(),
        backendEvidence = summary["backend_evidence"].orUnavailable(),
        persistentCustomJniHypothesisResult = summary["persistent_custom_jni_hypothesis_result"].orUnavailable(),
        promptInputLimitMode = summary["prompt_input_limit_mode"] ?: fallbackState.promptInputLimitMode,
        finalPromptText = summary["final_prompt_text"] ?: fallbackState.finalPromptText,
        finalPromptLengthChars = summary["final_prompt_length_chars"] ?: fallbackState.finalPromptLengthChars,
        finalPromptTailPreview = summary["final_prompt_tail_preview"] ?: fallbackState.finalPromptTailPreview,
        systemTemplateUsed = summary["system_template_used"] ?: fallbackState.systemTemplateUsed,
        hiddenTemplateUsed = summary["hidden_template_used"] ?: fallbackState.hiddenTemplateUsed,
        promptWrapperUsed = summary["prompt_wrapper_used"] ?: fallbackState.promptWrapperUsed,
        promptWrapperFamily = summary["prompt_wrapper_family"] ?: fallbackState.promptWrapperFamily,
        promptProfileHypothesis = summary["prompt_profile_hypothesis"] ?: fallbackState.promptProfileHypothesis,
        prefillTextOrTokenNote = summary["prefill_text_or_token_note"] ?: fallbackState.prefillTextOrTokenNote,
        firstOutputChars = qualitySummary.firstOutputChars,
        outputPrefixClassification = qualitySummary.outputPrefixClassification,
        outputQualityReason = qualitySummary.outputQualityReason,
        outputRepeatsSameAcrossRuns = qualitySummary.outputRepeatsSameAcrossRuns,
        outputLooksBusinessTemplate = qualitySummary.outputLooksBusinessTemplate,
        outputStartsWithPunctuation = qualitySummary.outputStartsWithPunctuation,
        outputContainsPlaceholder = qualitySummary.outputContainsPlaceholder,
        outputOnlyNewline = qualitySummary.outputOnlyNewline,
        outputEmpty = qualitySummary.outputEmpty,
        outputEqualsAcrossRuns = qualitySummary.outputRepeatsSameAcrossRuns,
        outputQualityCandidateStatus = qualitySummary.outputQualityCandidateStatus,
        outputQualityCandidateReason = qualitySummary.outputQualityCandidateReason,
        outputQualityCandidatePreparedOutput = qualitySummary.outputQualityCandidatePreparedOutput,
        outputQualityCandidateLeadingGreaterThanRemoved =
            qualitySummary.outputQualityCandidateLeadingGreaterThanRemoved,
        outputQualityCandidateEndOfTurnRemoved = qualitySummary.outputQualityCandidateEndOfTurnRemoved,
        outputQualityCandidateAssistantRepetition = qualitySummary.outputQualityCandidateAssistantRepetition,
        outputQualityCandidateQaContinuation = qualitySummary.outputQualityCandidateQaContinuation,
        tokenDiagnosticsNote = NPU_S1_TOKEN_DIAGNOSTICS_UNAVAILABLE_NOTE,
        records = records,
    )
}

private data class PersistentCustomJniPromptDiagnostics(
    val promptInputLimitMode: String,
    val finalPromptText: String,
    val finalPromptLengthChars: String,
    val finalPromptTailPreview: String,
    val systemTemplateUsed: String,
    val hiddenTemplateUsed: String,
    val promptWrapperUsed: String,
    val promptWrapperFamily: String,
    val promptProfileHypothesis: String,
    val prefillTextOrTokenNote: String,
)

private fun buildPersistentCustomJniPromptDiagnostics(
    qualityPromptProfile: NpuS1PersistentCustomJniQualityPromptProfile,
): PersistentCustomJniPromptDiagnostics {
    val validation = when (qualityPromptProfile.promptValidationMode) {
        NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE ->
            NpuDiagnosticPromptValidator.validateUtf8InternalIntent(qualityPromptProfile.prompt)
        NpuDiagnosticPromptValidator.UTF8_HIDDEN_EXPERIMENTAL_MODE ->
            NpuDiagnosticPromptValidator.validateUtf8HiddenExperimental(qualityPromptProfile.prompt)
        NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE ->
            NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(qualityPromptProfile.prompt)
        else -> NpuDiagnosticPromptValidator.validateAsciiDiagnostic(qualityPromptProfile.prompt)
    }
    val promptInputLimitMode = if (
        qualityPromptProfile.unsafeDevBypassPromptLengthGate &&
        validation.promptInputLimitMode == NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_INPUT_LIMIT_MODE
    ) {
        "unsafe_dev_bypass_hidden_template_experiment"
    } else {
        validation.promptInputLimitMode
    }
    return PersistentCustomJniPromptDiagnostics(
        promptInputLimitMode = promptInputLimitMode,
        finalPromptText = validation.normalizedPrompt,
        finalPromptLengthChars = validation.normalizedPrompt.length.toString(),
        finalPromptTailPreview = validation.normalizedPrompt.takeLast(PROMPT_TAIL_PREVIEW_CHARS),
        systemTemplateUsed = "false",
        hiddenTemplateUsed = "false",
        promptWrapperUsed = qualityPromptProfile.promptWrapperUsed,
        promptWrapperFamily = qualityPromptProfile.promptWrapperFamily,
        promptProfileHypothesis = qualityPromptProfile.promptProfileHypothesis,
        prefillTextOrTokenNote = "native_RunPrefill_receives_final_prompt_text",
    )
}

private data class PersistentCustomJniQualitySummary(
    val firstOutputChars: String,
    val outputPrefixClassification: String,
    val outputQualityReason: String,
    val outputRepeatsSameAcrossRuns: String,
    val outputLooksBusinessTemplate: String,
    val outputStartsWithPunctuation: String,
    val outputContainsPlaceholder: String,
    val outputOnlyNewline: String,
    val outputEmpty: String,
    val outputQualityCandidateStatus: String,
    val outputQualityCandidateReason: String,
    val outputQualityCandidatePreparedOutput: String,
    val outputQualityCandidateLeadingGreaterThanRemoved: String,
    val outputQualityCandidateEndOfTurnRemoved: String,
    val outputQualityCandidateAssistantRepetition: String,
    val outputQualityCandidateQaContinuation: String,
)

private fun buildPersistentCustomJniQualitySummary(
    records: List<NpuS1PersistentCustomJniRunRecord>,
): PersistentCustomJniQualitySummary {
    if (records.isEmpty()) {
        return PersistentCustomJniQualitySummary(
            firstOutputChars = "unavailable",
            outputPrefixClassification = "unavailable",
            outputQualityReason = "unavailable",
            outputRepeatsSameAcrossRuns = "unavailable",
            outputLooksBusinessTemplate = "unavailable",
            outputStartsWithPunctuation = "unavailable",
            outputContainsPlaceholder = "unavailable",
            outputOnlyNewline = "unavailable",
            outputEmpty = "unavailable",
            outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_UNKNOWN,
            outputQualityCandidateReason = "unavailable",
            outputQualityCandidatePreparedOutput = "unavailable",
            outputQualityCandidateLeadingGreaterThanRemoved = "unavailable",
            outputQualityCandidateEndOfTurnRemoved = "unavailable",
            outputQualityCandidateAssistantRepetition = "unavailable",
            outputQualityCandidateQaContinuation = "unavailable",
        )
    }
    val outputs = records.map { it.rawOutput.ifBlank { it.sanitizedOutput } }
    val distinctNonBlankOutputs = outputs.filter { it.isNotBlank() }.distinct()
    val repeatedOutput = distinctNonBlankOutputs.size == 1 && records.size > 1
    val firstQuality = classifyNpuS1PersistentCustomJniOutputQuality(
        output = outputs.firstOrNull().orEmpty(),
        outputEqualsAcrossRuns = repeatedOutput,
    )
    val hasBusinessTemplate = records.any { it.containsBusinessPhrase == "true" }
    val startsWithPunctuation = records.any { it.startsWithPunctuation == "true" }
    val containsPlaceholder = records.any { it.containsPlaceholder == "true" }
    val outputOnlyNewline = records.any { it.outputOnlyNewline == "true" }
    val outputEmpty = records.any { it.outputEmpty == "true" }
    val firstRecord = records.first()
    val candidate = evaluateNpuS1PersistentCustomJniQualityCandidate(
        rawOutput = firstRecord.rawOutput,
        sanitizedOutput = firstRecord.sanitizedOutput,
    )
    val reasons = buildList {
        if (startsWithPunctuation) add("starts_with_punctuation")
        if (startsWithPunctuation) add("first_token_boundary_suspect")
        if (hasBusinessTemplate) add("business_template_phrase")
        if (containsPlaceholder) add("placeholder_leak")
        if (containsPlaceholder) add("prompt_ignored_suspect")
        if (repeatedOutput) add("repeated_template_output")
        if (startsWithPunctuation && containsPlaceholder) add("decode_offset_suspect")
        if (outputOnlyNewline) add("newline_only")
        if (outputEmpty) add("empty_output")
    }.ifEmpty { listOf(firstQuality.reason) }
    return PersistentCustomJniQualitySummary(
        firstOutputChars = outputs.firstOrNull().orEmpty().take(40),
        outputPrefixClassification = firstQuality.qualityClassification,
        outputQualityReason = reasons.joinToString("+"),
        outputRepeatsSameAcrossRuns = repeatedOutput.toString(),
        outputLooksBusinessTemplate = hasBusinessTemplate.toString(),
        outputStartsWithPunctuation = startsWithPunctuation.toString(),
        outputContainsPlaceholder = containsPlaceholder.toString(),
        outputOnlyNewline = outputOnlyNewline.toString(),
        outputEmpty = outputEmpty.toString(),
        outputQualityCandidateStatus = candidate.status,
        outputQualityCandidateReason = candidate.reason,
        outputQualityCandidatePreparedOutput = candidate.preparedOutput,
        outputQualityCandidateLeadingGreaterThanRemoved = candidate.leadingGreaterThanRemoved.toString(),
        outputQualityCandidateEndOfTurnRemoved = candidate.endOfTurnRemoved.toString(),
        outputQualityCandidateAssistantRepetition = candidate.assistantRepetition.toString(),
        outputQualityCandidateQaContinuation = candidate.qaContinuation.toString(),
    )
}

private data class ParsedPersistentCustomJniProbeResult(
    val summary: Map<String, String>,
    val records: List<Map<String, String>>,
)

private fun parsePersistentCustomJniKeyValueLines(text: String): ParsedPersistentCustomJniProbeResult {
    val summary = linkedMapOf<String, String>()
    val records = mutableListOf<Map<String, String>>()
    var currentRecord: MutableMap<String, String>? = null
    var inDetails = false
    text.lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .forEach { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            if (key == "details_begin") {
                inDetails = true
                return@forEach
            }
            if (inDetails || key == "run_index") {
                if (key == "run_index") {
                    currentRecord?.let { records += it.toMap() }
                    currentRecord = linkedMapOf()
                    inDetails = true
                }
                currentRecord?.put(key, value)
            } else {
                summary[key] = value
            }
        }
    currentRecord?.let { records += it.toMap() }
    return ParsedPersistentCustomJniProbeResult(summary = summary, records = records)
}

private fun String?.orUnavailable(): String = this?.takeIf { it.isNotBlank() } ?: "unavailable"

private fun String?.toNullableNonNegativeLong(): Long? =
    this?.toLongOrNull()?.takeIf { it >= 0L }

private fun qualityPromptDefault(): String =
    NpuS1PersistentCustomJniQualityPromptProfile.CURRENT_PROBE_QUALITY.prompt

private const val PROMPT_TAIL_PREVIEW_CHARS = 160
