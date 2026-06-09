package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuS1PersistentCustomJniDiagnosticsTest {
    @Test
    fun `promotion gate passes for full 20 crash safety success`() {
        val state = full20SuccessState()

        val gate = evaluateNpuS1PromotionGate(state)
        val text = formatNpuS1PersistentCustomJniDiagnosticsForDev(state)

        assertEquals(NPU_S1_PROMOTION_GATE_STATUS_PASS, gate.status)
        assertEquals(
            NPU_S1_PROMOTION_GATE_REASON_READY_BUT_NORMAL_CHAT_BLOCKED,
            gate.reason,
        )
        assertFalse(gate.normalChatUnblockAllowed)
        assertTrue(text.contains("npu_s1_promotion_gate_status=pass"))
        assertTrue(text.contains("npu_s1_promotion_gate_full_20_required=true"))
        assertTrue(text.contains("npu_s1_promotion_gate_quality_required=false"))
        assertTrue(text.contains("npu_s1_promotion_gate_normal_chat_unblock_allowed=false"))
        assertTrue(text.contains("npu_s1_promotion_gate_engine_create=pass"))
        assertTrue(text.contains("npu_s1_promotion_gate_decode_20=pass"))
        assertTrue(text.contains("npu_s1_promotion_gate_crash_safety=pass"))
        assertTrue(text.contains("npu_s1_promotion_gate_output_quality=suspect"))
        assertTrue(text.contains("npu_s1_promotion_gate_normal_chat_unblock=blocked_by_policy"))
        assertTrue(text.contains("npu_s1_promotion_gate_tombstone_manual_check=required"))
    }

    @Test
    fun `output quality flags punctuation prefix`() {
        val quality = classifyNpuS1PersistentCustomJniOutputQuality("。お元気ですか。")

        assertTrue(quality.startsWithPunctuation)
        assertEquals(NPU_S1_OUTPUT_QUALITY_PUNCTUATION_START, quality.qualityClassification)
        assertTrue(quality.reason.contains("starts_with_punctuation"))
    }

    @Test
    fun `output quality flags business template phrase`() {
        val quality = classifyNpuS1PersistentCustomJniOutputQuality(
            "いつもお世話になっております。山田です。",
        )

        assertTrue(quality.containsBusinessPhrase)
        assertEquals(NPU_S1_OUTPUT_QUALITY_TEMPLATE_LEAK, quality.qualityClassification)
        assertTrue(quality.reason.contains("business_template_phrase"))
    }

    @Test
    fun `output quality flags square bracket placeholder`() {
        val quality = classifyNpuS1PersistentCustomJniOutputQuality(
            "いつもお世話になっております。[あなたの名前]です。",
        )

        assertTrue(quality.containsPlaceholder)
        assertEquals(NPU_S1_OUTPUT_QUALITY_PLACEHOLDER_LEAK, quality.qualityClassification)
        assertTrue(quality.reason.contains("placeholder_leak"))
    }

    @Test
    fun `full 20 success with suspect quality keeps normal chat blocked`() {
        val state = full20SuccessState(
            records = successRecords(
                rawOutput = "。お元気ですか。いつもお世話になっております。[あなたの名前]です。",
                qualityClassification = NPU_S1_OUTPUT_QUALITY_PLACEHOLDER_LEAK,
            ),
        )

        val gate = evaluateNpuS1PromotionGate(state)
        val text = formatNpuS1PersistentCustomJniDiagnosticsForDev(state)

        assertEquals(NPU_S1_PROMOTION_GATE_STATUS_PASS, gate.status)
        assertFalse(gate.normalChatUnblockAllowed)
        assertTrue(text.contains("npu_s1_promotion_gate_output_quality=suspect"))
        assertTrue(text.contains("npu_s1_promotion_gate_normal_chat_unblock=blocked_by_policy"))
    }

    @Test
    fun `promotion gate is not run before full 20 evidence exists`() {
        val gate = evaluateNpuS1PromotionGate(NpuS1PersistentCustomJniProbeState())

        assertEquals(NPU_S1_PROMOTION_GATE_STATUS_NOT_RUN, gate.status)
        assertEquals(NPU_S1_PROMOTION_GATE_REASON_FULL_20_NOT_RUN, gate.reason)
    }

    @Test
    fun `promotion gate fails when success count is short`() {
        val state = full20SuccessState(
            records = successRecords(count = 19),
            decodeSuccessCount = "19",
        )

        val gate = evaluateNpuS1PromotionGate(state)

        assertEquals(NPU_S1_PROMOTION_GATE_STATUS_FAIL, gate.status)
        assertTrue(gate.reason.contains("success_count_not_run_count_requested"))
        assertTrue(gate.reason.contains("decode_success_count_not_run_count_requested"))
    }

    @Test
    fun `promotion gate fails without QNN HTP V79 backend evidence`() {
        val state = full20SuccessState(backendEvidence = "QNN_HTP_unknown")

        val gate = evaluateNpuS1PromotionGate(state)

        assertEquals(NPU_S1_PROMOTION_GATE_STATUS_FAIL, gate.status)
        assertTrue(gate.reason.contains("backend_evidence_missing_QNN_HTP_V79"))
    }

    @Test
    fun `summary includes holder key and model update fields`() {
        val state = NpuS1PersistentCustomJniProbeState(
            persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_STOPPED,
            engineCreateCount = "0",
            decodeAttemptCount = "7",
            decodeSuccessCount = "6",
            holderKey = NpuS1PersistentCustomJniHolderKey(
                modelPath = "/data/user/0/io.github.ninbyo02.lami/files/local_models/model.litertlm",
                modelFileLastModified = "1700000000000",
                modelFileSize = "123456",
                backend = "NPU",
                cacheDir = "/data/user/0/io.github.ninbyo02.lami/cache",
                maxTokenBudget = "32",
                engineConfigVersion = "persistent_custom_jni_holder_poc_v1",
            ),
            holderInvalidated = "true",
            nativeHolderEntrypointAvailable = "false",
            selectedNativeProbeMode = "before_engine_create",
            lastNativeStage = "before_engine_create",
            nativeEntrypointReached = "true",
            modelAssetsCreateReached = "true",
            modelAssetsCreateReturned = "true",
            engineSettingsCreateReached = "true",
            engineSettingsCreateReturned = "true",
            engineCreateReached = "false",
            engineCreateReturned = "false",
            sessionCreateReached = "false",
            prefillReached = "false",
            decodeReached = "false",
            nativeDiagFlushCount = "4",
            nativeResultFlushCount = "5",
            engineCreateModelPath = "/data/user/0/io.github.ninbyo02.lami/files/local_models/model.litertlm",
            engineCreateNativeLibraryDir = "/data/app/lib/arm64",
            engineCreateCacheDir = "/data/user/0/io.github.ninbyo02.lami/cache",
            engineCreateBackend = "NPU",
            engineCreatePromptInputLimitMode = "unsafe_dev_bypass_hidden_template_experiment",
            engineCreateRequestedMaxOutputTokens = "32",
            engineCreateEffectiveMaxOutputTokens = "32",
            engineCreateMaxTokenBudget = "32",
            engineCreateSettingsSource =
                "EngineSettings::CreateDefault(model_assets,NPU)+SetCacheDir(cache_dir)+SetLitertDispatchLibDir(native_library_dir)",
            engineCreateAssetsSource = "ModelAssets::Create(model_path)",
            engineCreateMatchesEditablePromptPath = "true",
            engineCreateMatchesEditablePromptSettings = "true",
            editablePromptEngineCreateSignature = "model_path=/model;backend=NPU",
            persistentEngineCreateSignature = "model_path=/model;backend=NPU",
            engineCreateMinimalPath = "true",
            persistentHolderUsed = "false",
            persistentCustomJniHypothesisResult = "native_holder_entrypoint_not_available",
            promptInputLimitMode = "unsafe_dev_bypass_hidden_template_experiment",
            finalPromptText = "こんにちは",
            finalPromptLengthChars = "5",
            finalPromptTailPreview = "こんにちは",
            systemTemplateUsed = "false",
            hiddenTemplateUsed = "false",
            promptWrapperUsed = "none",
            prefillTextOrTokenNote = "native_RunPrefill_receives_final_prompt_text",
            firstOutputChars = "。お元気ですか。",
            outputPrefixClassification = NPU_S1_OUTPUT_QUALITY_PUNCTUATION_START,
            outputQualityReason = "starts_with_punctuation",
            outputRepeatsSameAcrossRuns = "false",
            outputLooksBusinessTemplate = "false",
            outputStartsWithPunctuation = "true",
            outputContainsPlaceholder = "false",
        )

        val text = formatNpuS1PersistentCustomJniDiagnosticsForDev(state)

        assertTrue(text.contains("[DEV診断: NPU S1 persistent custom JNI summary]"))
        assertTrue(text.contains("decode_attempt_count=7"))
        assertTrue(text.contains("decode_success_count=6"))
        assertTrue(text.contains("holder_key_model_path=/data/user/0/io.github.ninbyo02.lami/files/local_models/model.litertlm"))
        assertTrue(text.contains("holder_key_model_file_last_modified=1700000000000"))
        assertTrue(text.contains("holder_key_model_file_size=123456"))
        assertTrue(text.contains("holder_key_backend=NPU"))
        assertTrue(text.contains("holder_key_cache_dir=/data/user/0/io.github.ninbyo02.lami/cache"))
        assertTrue(text.contains("holder_key_max_token_budget=32"))
        assertTrue(text.contains("holder_key_engine_config_version=persistent_custom_jni_holder_poc_v1"))
        assertTrue(text.contains("native_holder_entrypoint_available=false"))
        assertTrue(text.contains("selected_native_probe_mode=before_engine_create"))
        assertTrue(text.contains("last_native_stage=before_engine_create"))
        assertTrue(text.contains("native_entrypoint_reached=true"))
        assertTrue(text.contains("model_assets_create_reached=true"))
        assertTrue(text.contains("model_assets_create_returned=true"))
        assertTrue(text.contains("engine_settings_create_reached=true"))
        assertTrue(text.contains("engine_settings_create_returned=true"))
        assertTrue(text.contains("engine_create_reached=false"))
        assertTrue(text.contains("engine_create_returned=false"))
        assertTrue(text.contains("session_create_reached=false"))
        assertTrue(text.contains("prefill_reached=false"))
        assertTrue(text.contains("decode_reached=false"))
        assertTrue(text.contains("native_diag_flush_count=4"))
        assertTrue(text.contains("native_result_flush_count=5"))
        assertTrue(text.contains("engine_create_model_path=/data/user/0/io.github.ninbyo02.lami/files/local_models/model.litertlm"))
        assertTrue(text.contains("engine_create_native_library_dir=/data/app/lib/arm64"))
        assertTrue(text.contains("engine_create_cache_dir=/data/user/0/io.github.ninbyo02.lami/cache"))
        assertTrue(text.contains("engine_create_backend=NPU"))
        assertTrue(text.contains("engine_create_prompt_input_limit_mode=unsafe_dev_bypass_hidden_template_experiment"))
        assertTrue(text.contains("engine_create_requested_max_output_tokens=32"))
        assertTrue(text.contains("engine_create_effective_max_output_tokens=32"))
        assertTrue(text.contains("engine_create_max_token_budget=32"))
        assertTrue(text.contains("engine_create_settings_source=EngineSettings::CreateDefault"))
        assertTrue(text.contains("engine_create_assets_source=ModelAssets::Create(model_path)"))
        assertTrue(text.contains("engine_create_matches_editable_prompt_path=true"))
        assertTrue(text.contains("engine_create_matches_editable_prompt_settings=true"))
        assertTrue(text.contains("editable_prompt_engine_create_signature=model_path=/model;backend=NPU"))
        assertTrue(text.contains("persistent_engine_create_signature=model_path=/model;backend=NPU"))
        assertTrue(text.contains("engine_create_minimal_path=true"))
        assertTrue(text.contains("persistent_holder_used=false"))
        assertTrue(text.contains("persistent_custom_jni_hypothesis_result=native_holder_entrypoint_not_available"))
        assertTrue(text.contains("prompt_input_limit_mode=unsafe_dev_bypass_hidden_template_experiment"))
        assertTrue(text.contains("final_prompt_text=こんにちは"))
        assertTrue(text.contains("final_prompt_length_chars=5"))
        assertTrue(text.contains("final_prompt_tail_preview=こんにちは"))
        assertTrue(text.contains("system_template_used=false"))
        assertTrue(text.contains("hidden_template_used=false"))
        assertTrue(text.contains("prompt_wrapper_used=none"))
        assertTrue(text.contains("prefill_text_or_token_note=native_RunPrefill_receives_final_prompt_text"))
        assertTrue(text.contains("first_output_chars=。お元気ですか。"))
        assertTrue(text.contains("output_prefix_classification=punctuation_start"))
        assertTrue(text.contains("output_quality_reason=starts_with_punctuation"))
        assertTrue(text.contains("output_repeats_same_across_runs=false"))
        assertTrue(text.contains("output_looks_business_template=false"))
        assertTrue(text.contains("output_starts_with_punctuation=true"))
        assertTrue(text.contains("output_contains_placeholder=false"))
    }

    @Test
    fun `details include requested decode and failure fields`() {
        val state = NpuS1PersistentCustomJniProbeState(
            persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_STOPPED,
            records = listOf(
                NpuS1PersistentCustomJniRunRecord(
                    runIndex = 7,
                    status = "failure",
                    reason = "adapter_failure:LiteRtLmJniException",
                    sessionCreated = "true",
                    sessionClosed = "true",
                    prefillStarted = "true",
                    prefillFinished = "true",
                    decodeStarted = "true",
                    decodeFinished = "false",
                    rawOutput = "。お元気ですか。いつもお世話になっております。[あなたの名前]です。",
                    sanitizedOutput = "。お元気ですか。いつもお世話になっております。[あなたの名前]です。",
                    outputPrefix20Chars = "。お元気ですか。いつもお世話に",
                    startsWithPunctuation = "true",
                    containsBusinessPhrase = "true",
                    containsPlaceholder = "true",
                    qualityClassification = NPU_S1_OUTPUT_QUALITY_PLACEHOLDER_LEAK,
                    prefillMs = 42,
                    cleanupMs = 3,
                    failureStage = "decode",
                    failureExceptionClass = "LiteRtLmJniException",
                    failureExceptionMessage = "engine-create-failed:INTERNAL",
                    nativeDiagTail = "before EngineFactory::CreateDefault",
                ),
            ),
        )

        val text = formatNpuS1PersistentCustomJniDiagnosticsForDev(state)

        assertTrue(text.contains("[DEV診断: NPU S1 persistent custom JNI details]"))
        assertTrue(text.contains("run_index=7"))
        assertTrue(text.contains("session_created=true"))
        assertTrue(text.contains("session_closed=true"))
        assertTrue(text.contains("prefill_started=true"))
        assertTrue(text.contains("prefill_finished=true"))
        assertTrue(text.contains("decode_started=true"))
        assertTrue(text.contains("decode_finished=false"))
        assertTrue(text.contains("output_prefix_20_chars=。お元気ですか。いつもお世話に"))
        assertTrue(text.contains("starts_with_punctuation=true"))
        assertTrue(text.contains("contains_business_phrase=true"))
        assertTrue(text.contains("contains_placeholder=true"))
        assertTrue(text.contains("quality_classification=placeholder_leak"))
        assertTrue(text.contains("prefill_ms=42"))
        assertTrue(text.contains("cleanup_ms=3"))
        assertTrue(text.contains("failure_stage=decode"))
        assertTrue(text.contains("failure_exception_class=LiteRtLmJniException"))
        assertTrue(text.contains("native_diag_tail=before EngineFactory::CreateDefault"))
    }

    @Test
    fun `unavailable values are not coerced to zero or false`() {
        val text = formatNpuS1PersistentCustomJniDiagnosticsForDev(
            NpuS1PersistentCustomJniProbeState(),
        )

        assertTrue(text.contains("engine_create_count=unavailable"))
        assertTrue(text.contains("engine_close_reached=unavailable"))
        assertTrue(text.contains("holder_generation=unavailable"))
        assertTrue(text.contains("selected_native_probe_mode=full_20"))
        assertTrue(text.contains("last_native_stage=unavailable"))
        assertTrue(text.contains("native_entrypoint_reached=unavailable"))
        assertTrue(text.contains("model_assets_create_reached=unavailable"))
        assertTrue(text.contains("engine_settings_create_reached=unavailable"))
        assertTrue(text.contains("engine_create_reached=unavailable"))
        assertTrue(text.contains("session_create_reached=unavailable"))
        assertTrue(text.contains("prefill_reached=unavailable"))
        assertTrue(text.contains("decode_reached=unavailable"))
        assertTrue(text.contains("native_diag_flush_count=unavailable"))
        assertTrue(text.contains("native_result_flush_count=unavailable"))
        assertTrue(text.contains("engine_create_model_path=unavailable"))
        assertTrue(text.contains("engine_create_matches_editable_prompt_settings=unavailable"))
        assertTrue(text.contains("editable_prompt_engine_create_signature=unavailable"))
        assertTrue(text.contains("persistent_engine_create_signature=unavailable"))
        assertTrue(text.contains("engine_create_minimal_path=unavailable"))
        assertTrue(text.contains("persistent_holder_used=unavailable"))
        assertTrue(text.contains("prompt_input_limit_mode=unavailable"))
        assertTrue(text.contains("final_prompt_text=unavailable"))
        assertTrue(text.contains("output_prefix_classification=unavailable"))
        assertTrue(text.contains("output_repeats_same_across_runs=unavailable"))
        assertTrue(text.contains("output_contains_placeholder=unavailable"))
        assertTrue(text.contains("records=empty"))
    }

    @Test
    fun `append helper adds custom JNI diagnostics to existing copy`() {
        val text = appendNpuS1PersistentCustomJniDiagnosticsForDev(
            text = "base",
            state = NpuS1PersistentCustomJniProbeState(engineCreateCount = "1"),
        )

        assertTrue(text.startsWith("base"))
        assertTrue(text.contains("[DEV診断: NPU S1 persistent custom JNI summary]"))
        assertTrue(text.contains("engine_create_count=1"))
    }

    private fun full20SuccessState(
        backendEvidence: String = "QNN_HTP_V79_FastRPC_native_diag_persistent_holder",
        records: List<NpuS1PersistentCustomJniRunRecord> = successRecords(),
        decodeSuccessCount: String = "20",
    ): NpuS1PersistentCustomJniProbeState =
        NpuS1PersistentCustomJniProbeState(
            persistentCustomJniStatus = NPU_S1_PERSISTENT_CUSTOM_JNI_STATUS_COMPLETED,
            runCountRequested = 20,
            engineCreateCount = "1",
            decodeAttemptCount = "20",
            decodeSuccessCount = decodeSuccessCount,
            engineCloseReached = "true",
            engineCloseSuccess = "true",
            selectedNativeProbeMode = NpuS1PersistentCustomJniProbeMode.FULL_20.wireValue,
            backendEvidence = backendEvidence,
            persistentCustomJniHypothesisResult = "engine_create_once_20_runs_success",
            promotionGateFreshCrash = "false",
            promotionGateTimeout = "false",
            promotionGateFallback = "false",
            records = records,
        )

    private companion object {
        fun successRecords(
            count: Int = 20,
            rawOutput: String = "",
            qualityClassification: String = "unavailable",
        ): List<NpuS1PersistentCustomJniRunRecord> =
            (1..count).map { index ->
                val quality = classifyNpuS1PersistentCustomJniOutputQuality(rawOutput)
                NpuS1PersistentCustomJniRunRecord(
                    runIndex = index,
                    status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                    reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                    sessionCreated = "true",
                    sessionClosed = "true",
                    prefillStarted = "true",
                    prefillFinished = "true",
                    decodeStarted = "true",
                    decodeFinished = "true",
                    rawOutput = rawOutput,
                    sanitizedOutput = rawOutput,
                    outputPrefix20Chars = quality.outputPrefix20Chars,
                    startsWithPunctuation = quality.startsWithPunctuation.toString(),
                    containsBusinessPhrase = quality.containsBusinessPhrase.toString(),
                    containsPlaceholder = quality.containsPlaceholder.toString(),
                    qualityClassification = qualityClassification,
                )
            }
    }
}
