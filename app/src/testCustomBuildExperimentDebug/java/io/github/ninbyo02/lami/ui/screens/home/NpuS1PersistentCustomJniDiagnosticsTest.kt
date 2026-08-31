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
        assertTrue(gate.normalChatUnblockAllowed)
        assertTrue(text.contains("npu_s1_promotion_gate_status=pass"))
        assertTrue(text.contains("npu_s1_promotion_gate_full_20_required=true"))
        assertTrue(text.contains("npu_s1_promotion_gate_quality_required=false"))
        assertTrue(text.contains("npu_s1_promotion_gate_normal_chat_unblock_allowed=true"))
        assertTrue(text.contains("npu_s1_promotion_gate_engine_create=pass"))
        assertTrue(text.contains("npu_s1_promotion_gate_decode_20=pass"))
        assertTrue(text.contains("npu_s1_promotion_gate_crash_safety=pass"))
        assertTrue(text.contains("npu_s1_promotion_gate_output_quality=suspect"))
        assertTrue(text.contains("npu_s1_promotion_gate_normal_chat_unblock=policy_allowed"))
        assertTrue(text.contains("npu_s1_promotion_gate_tombstone_manual_check=required"))
    }

    @Test
    fun `output quality flags punctuation prefix`() {
        val quality = classifyNpuS1PersistentCustomJniOutputQuality("。お元気ですか。")

        assertTrue(quality.startsWithPunctuation)
        assertEquals(NPU_S1_OUTPUT_QUALITY_PUNCTUATION_START, quality.qualityClassification)
        assertTrue(quality.reason.contains("starts_with_punctuation"))
        assertTrue(quality.reason.contains("first_token_boundary_suspect"))
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
    fun `output quality flags repeated template output`() {
        val quality = classifyNpuS1PersistentCustomJniOutputQuality(
            output = "。お元気ですか。いつもお世話になっております。[あなたの名前]です。",
            outputEqualsAcrossRuns = true,
        )

        assertEquals(NPU_S1_OUTPUT_QUALITY_REPEATED_TEMPLATE_OUTPUT, quality.qualityClassification)
        assertTrue(quality.reason.contains("repeated_template_output"))
    }

    @Test
    fun `output quality prioritizes greeting mismatch while retaining prompt ignored reason`() {
        val quality = classifyNpuS1PersistentCustomJniOutputQuality(
            output = "。お元気ですか。いつもお世話になっております。[あなたの名前]です。",
            prompt = "こんにちは",
        )

        assertEquals(NpuStandardRouteS1Contract.QUALITY_MIXED_LANGUAGE, quality.qualityClassification)
        assertTrue(quality.reason.contains("prompt_ignored_suspect"))
        assertTrue(quality.reason.contains("greeting_response_mismatch"))
    }

    @Test
    fun `token boundary diagnostics derive text spans without token ids`() {
        val diagnostics = buildNpuS1PersistentCustomJniTokenBoundaryDiagnostics("。\n\nお元気ですか。")

        assertEquals("。", diagnostics.outputFirst1Char)
        assertEquals("。\n\nお元", diagnostics.outputFirst5Chars)
        assertEquals("。\n\nお元気ですか。", diagnostics.outputFirst20Chars)
        assertEquals("1", diagnostics.outputLeadingPunctuationCount)
        assertEquals("2", diagnostics.outputNewlineCount)
        assertEquals("。\n\nお元気ですか。", diagnostics.outputAfterLstripFirstChars)
    }

    @Test
    fun `output quality flags empty and newline only output`() {
        val empty = classifyNpuS1PersistentCustomJniOutputQuality("")
        val newlineOnly = classifyNpuS1PersistentCustomJniOutputQuality("\n")

        assertTrue(empty.outputEmpty)
        assertTrue(empty.reason.contains("empty_output"))
        assertTrue(newlineOnly.outputOnlyNewline)
        assertTrue(newlineOnly.reason.contains("newline_only"))
    }

    @Test
    fun `wrapper prompt profiles expose final prompt metadata`() {
        val profile = NpuS1PersistentCustomJniQualityPromptProfile.GEMMA_IT_USER_MODEL

        assertEquals("gemma_it_user_model", profile.wireValue)
        assertEquals(3, profile.runCount)
        assertEquals("gemma_it_user_model", profile.promptWrapperUsed)
        assertEquals("gemma_it", profile.promptWrapperFamily)
        assertTrue(profile.prompt.contains("<start_of_turn>user"))
        assertTrue(profile.promptProfileHypothesis.contains("gemma"))
    }

    @Test
    fun `quality candidate passes gemma user model observed output`() {
        val result = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = ">こんにちは！何かお手伝いできることはありますか？<end_of_turn>",
            sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？",
        )

        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS, result.status)
        assertEquals("こんにちは！何かお手伝いできることはありますか？", result.preparedOutput)
        assertFalse(result.placeholderLeak)
        assertFalse(result.businessTemplateLeak)
        assertFalse(result.assistantRepetition)
        assertFalse(result.qaContinuation)
        assertTrue(result.leadingGreaterThanRemoved)
        assertTrue(result.endOfTurnRemoved)
    }

    @Test
    fun `quality candidate fails known bad profile outputs`() {
        val repeatedTemplate = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "。いつもお世話になっております。[あなたの名前]です。",
            sanitizedOutput = "。いつもお世話になっております。[あなたの名前]です。",
        )
        val assistantRepetition = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "Assistant: Assistant: こんにちは",
            sanitizedOutput = "Assistant: Assistant: こんにちは",
        )
        val qaContinuation = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "質問: こんにちは\n回答: こんにちは\n質問:",
            sanitizedOutput = "質問: こんにちは\n回答: こんにちは\n質問:",
        )
        val newlineOnly = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "\n",
            sanitizedOutput = "\n",
        )
        val empty = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "",
            sanitizedOutput = "",
        )
        val literalNewline = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "\\n",
            sanitizedOutput = "\\n",
        )
        val whitespaceOnly = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "   ",
            sanitizedOutput = "   ",
        )
        val selfIntroTemplate = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "〇〇、---\n**自己紹介（日本語）**",
            sanitizedOutput = "〇〇、---\n**自己紹介（日本語）**",
        )

        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, repeatedTemplate.status)
        assertTrue(repeatedTemplate.reason.contains("placeholder_leak"))
        assertTrue(repeatedTemplate.reason.contains("business_template_leak"))
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, assistantRepetition.status)
        assertTrue(assistantRepetition.reason.contains("assistant_repetition"))
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, qaContinuation.status)
        assertTrue(qaContinuation.reason.contains("qa_continuation"))
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, newlineOnly.status)
        assertTrue(newlineOnly.reason.contains("raw_output_empty"))
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, empty.status)
        assertTrue(empty.reason.contains("raw_output_empty"))
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, literalNewline.status)
        assertTrue(literalNewline.reason.contains("prepared_output_literal_newline_only"))
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, whitespaceOnly.status)
        assertTrue(whitespaceOnly.reason.contains("prepared_output_empty"))
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, selfIntroTemplate.status)
        assertTrue(selfIntroTemplate.reason.contains("self_intro_template_leak"))
    }

    @Test
    fun `quality candidate fails special token and user turn leaks`() {
        val startTurn = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "<start_of_turn>user\n１＋１は？",
            sanitizedOutput = "<start_of_turn>user\n１＋１は？",
            inputPrompt = "１＋１は？",
        )
        val unclosedEndTurn = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "１＋１は？<end_of_turn",
            sanitizedOutput = "１＋１は？<end_of_turn",
            inputPrompt = "１＋１は？",
        )
        val spacedEndTurnAnswer = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = ">\n2\n< end_of_turn>",
            sanitizedOutput = "2\n< end_of_turn>",
            inputPrompt = "１＋１は？",
        )
        val closingEndTurnAnswer = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = ">答え:2</end_of_turn>",
            sanitizedOutput = "答え:2</end_of_turn>",
            inputPrompt = "１＋１は",
        )
        val problemAnswer = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = ">問題: 1+1は\n答え:2</end_of_turn>",
            sanitizedOutput = "問題: 1+1は\n答え:2</end_of_turn>",
            inputPrompt = "1+1は",
        )

        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, startTurn.status)
        assertTrue(startTurn.reason.contains("special_token_leak"))
        assertTrue(startTurn.reason.contains("user_turn_leak"))
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, unclosedEndTurn.status)
        assertFalse(unclosedEndTurn.reason.contains("special_token_leak"))
        assertTrue(unclosedEndTurn.reason.contains("prompt_repetition_only"))
        assertTrue(unclosedEndTurn.reason.contains("arithmetic_answer_missing"))
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS, spacedEndTurnAnswer.status)
        assertEquals("2", spacedEndTurnAnswer.preparedOutput)
        assertTrue(spacedEndTurnAnswer.endOfTurnRemoved)
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS, closingEndTurnAnswer.status)
        assertEquals("2", closingEndTurnAnswer.preparedOutput)
        assertTrue(closingEndTurnAnswer.endOfTurnRemoved)
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS, problemAnswer.status)
        assertEquals("2", problemAnswer.preparedOutput)
    }

    @Test
    fun `quality candidate allows arithmetic answer before tail turn leak`() {
        val arithmeticTailLeak = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = ">2</start_of_turn>\n<end_of_turn>\n<start_of_turn>user>次の計算に日本語で",
            sanitizedOutput = "2</start_of_turn>\n\n次の計算に日本語で",
            inputPrompt = "1+1は？",
        )
        val nonArithmeticTailLeak = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = ">こんにちは</start_of_turn>\n<start_of_turn>user>こんにちは",
            sanitizedOutput = "こんにちは</start_of_turn>\nこんにちは",
            inputPrompt = "こんにちは",
        )

        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS, arithmeticTailLeak.status)
        assertEquals("2", arithmeticTailLeak.preparedOutput)
        assertEquals(
            "natural_japanese_after_arithmetic_answer_extraction_with_tail_leak_cleanup",
            arithmeticTailLeak.reason,
        )
        assertTrue(arithmeticTailLeak.arithmeticTailLeakDetected)
        assertTrue(arithmeticTailLeak.arithmeticTailLeakIgnoredForDisplay)

        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, nonArithmeticTailLeak.status)
        assertTrue(nonArithmeticTailLeak.reason.contains("special_token_leak"))
        assertFalse(nonArithmeticTailLeak.arithmeticTailLeakIgnoredForDisplay)
    }

    @Test
    fun `quality candidate keeps answer prefix for non arithmetic prompts`() {
        val result = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = ">答え: 私はLamiです。</end_of_turn>",
            sanitizedOutput = "答え: 私はLamiです。</end_of_turn>",
            inputPrompt = "あなたは誰ですか？",
        )

        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS, result.status)
        assertEquals("答え: 私はLamiです。", result.preparedOutput)
        assertTrue(result.endOfTurnRemoved)
    }

    @Test
    fun `quality candidate handles minimal arithmetic prompts`() {
        val echoedQuestion = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "１＋１は？",
            sanitizedOutput = "１＋１は？",
            inputPrompt = "１＋１は？",
        )
        val answeredQuestion = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "１＋１は２です",
            sanitizedOutput = "１＋１は２です",
            inputPrompt = "１＋１は？",
        )
        val answeredNoQuestion = evaluateNpuS1PersistentCustomJniQualityCandidate(
            rawOutput = "2です",
            sanitizedOutput = "2です",
            inputPrompt = "1+1",
        )

        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, echoedQuestion.status)
        assertTrue(echoedQuestion.reason.contains("prompt_repetition_only"))
        assertTrue(echoedQuestion.reason.contains("arithmetic_answer_missing"))
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS, answeredQuestion.status)
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS, answeredNoQuestion.status)
    }

    @Test
    fun `quality gate passes for gemma user model x20 candidate output`() {
        val state = full20SuccessState().copy(
            selectedQualityPromptProfile =
                NpuS1PersistentCustomJniQualityPromptProfile.GEMMA_IT_USER_MODEL_FULL_20_QUALITY.wireValue,
            outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS,
            outputQualityCandidateReason = "natural_japanese_after_safe_leading_gt_and_end_of_turn_cleanup",
            outputEmpty = "false",
            outputOnlyNewline = "false",
            outputContainsPlaceholder = "false",
            outputLooksBusinessTemplate = "false",
            outputQualityCandidateAssistantRepetition = "false",
            outputQualityCandidateQaContinuation = "false",
            firstQualityFailureRunIndex = "unavailable",
            firstQualityFailureReason = "unavailable",
            failedQualityRunCount = "0",
            qualityGateAllRunsPassed = "true",
        )

        val gate = evaluateNpuS1QualityGate(state)
        val text = formatNpuS1PersistentCustomJniDiagnosticsForDev(state)

        assertEquals(NPU_S1_QUALITY_GATE_STATUS_PASS, gate.status)
        assertEquals("gemma_it_user_model_full_20_quality_candidate_pass", gate.reason)
        assertEquals("gemma_it_user_model_full_20_quality", gate.promptProfile)
        assertTrue(text.contains("npu_s1_quality_gate_status=pass"))
        assertTrue(text.contains("npu_s1_quality_gate_reason=gemma_it_user_model_full_20_quality_candidate_pass"))
        assertTrue(text.contains("npu_s1_quality_gate_prompt_profile=gemma_it_user_model_full_20_quality"))
        assertTrue(text.contains("npu_s1_quality_gate_run_count_required=20"))
        assertTrue(text.contains("npu_s1_quality_gate_run_count_completed=20"))
        assertTrue(text.contains("npu_s1_quality_gate_all_runs_passed=true"))
        assertTrue(text.contains("npu_s1_quality_gate_20_run_status=pass"))
        assertTrue(text.contains("failed_quality_run_count=0"))
        assertTrue(text.contains("npu_s1_normal_chat_unblock_readiness_status=ready_and_policy_allowed"))
        assertTrue(
            text.contains(
                "npu_s1_normal_chat_unblock_readiness_reason=" +
                    "final_gates_pass_and_policy_allows_unblock",
            ),
        )
        assertTrue(text.contains("npu_s1_normal_chat_unblock_required_profile=gemma_it_user_model_full_20_quality"))
        assertTrue(text.contains("npu_s1_normal_chat_unblock_required_20_run_gate=true"))
        assertTrue(text.contains("npu_s1_normal_chat_unblock_policy_allowed=true"))
    }

    @Test
    fun `normal chat route reaches native provider when final readiness is ready and policy true`() {
        val state = full20SuccessState().copy(
            selectedQualityPromptProfile =
                NpuS1PersistentCustomJniQualityPromptProfile.GEMMA_IT_USER_MODEL_FULL_20_QUALITY.wireValue,
            outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS,
            outputEmpty = "false",
            outputOnlyNewline = "false",
            outputContainsPlaceholder = "false",
            outputLooksBusinessTemplate = "false",
            outputQualityCandidateAssistantRepetition = "false",
            outputQualityCandidateQaContinuation = "false",
            failedQualityRunCount = "0",
            qualityGateAllRunsPassed = "true",
        )
        val promotionGate = evaluateNpuS1PromotionGate(state)
        val qualityGate = evaluateNpuS1QualityGate(state)
        val readiness = evaluateNpuS1NormalChatUnblockReadiness(
            promotionGate = promotionGate,
            qualityGate = qualityGate,
        )
        val provider = NpuStandardRouteS1ProviderSelector.defaultProviderWithPromotionGate(
            s1GateEnabled = true,
            promotionGate = promotionGate,
        )
        val result = provider.invoke(
            userPrompt = "こんにちは",
            maxOutputTokens = 32,
            trace = {},
        )

        assertEquals("ready_and_policy_allowed", readiness.status)
        assertTrue(readiness.policyAllowed)
        assertTrue(NpuStandardRouteS1ProviderSelector.NORMAL_CHAT_NATIVE_ROUTE_UNBLOCK_ALLOWED)
        assertEquals(RealNpuStandardRouteS1Provider.REASON_NATIVE_ENTRY_UNAVAILABLE, result.reason)
        assertEquals("failure", result.status)
    }

    @Test
    fun `quality gate fails for gemma user model short comparison even when candidate passes`() {
        val state = full20SuccessState(records = successRecords(count = 3)).copy(
            runCountRequested = 3,
            runCountCompletedOverride = 3,
            selectedQualityPromptProfile = NpuS1PersistentCustomJniQualityPromptProfile.GEMMA_IT_USER_MODEL.wireValue,
            outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS,
            outputEmpty = "false",
            outputOnlyNewline = "false",
            outputContainsPlaceholder = "false",
            outputLooksBusinessTemplate = "false",
            outputQualityCandidateAssistantRepetition = "false",
            outputQualityCandidateQaContinuation = "false",
            failedQualityRunCount = "0",
            qualityGateAllRunsPassed = "true",
        )

        val gate = evaluateNpuS1QualityGate(state)

        assertEquals(NPU_S1_QUALITY_GATE_STATUS_FAIL, gate.status)
        assertTrue(gate.reason.contains("prompt_profile_not_gemma_it_user_model_full_20_quality"))
        assertTrue(gate.reason.contains("run_count_completed_not_20"))

        val readiness = evaluateNpuS1NormalChatUnblockReadiness(
            promotionGate = evaluateNpuS1PromotionGate(state),
            qualityGate = gate,
        )
        assertEquals(NPU_S1_NORMAL_CHAT_UNBLOCK_READINESS_NOT_READY, readiness.status)
        assertTrue(readiness.reason.contains("quality_gate_not_pass"))
    }

    @Test
    fun `quality gate is unknown before quality candidate evidence exists`() {
        val gate = evaluateNpuS1QualityGate(NpuS1PersistentCustomJniProbeState())

        assertEquals(NPU_S1_QUALITY_GATE_STATUS_UNKNOWN, gate.status)
        assertEquals("quality_candidate_not_run", gate.reason)
    }

    @Test
    fun `quality gate fails known bad prompt profile candidate outputs`() {
        val state = full20SuccessState().copy(
            selectedQualityPromptProfile = NpuS1PersistentCustomJniQualityPromptProfile.NO_BOS_NO_EOS.wireValue,
            outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL,
            outputEmpty = "false",
            outputOnlyNewline = "false",
            outputContainsPlaceholder = "true",
            outputLooksBusinessTemplate = "true",
            outputQualityCandidateAssistantRepetition = "false",
            outputQualityCandidateQaContinuation = "false",
            failedQualityRunCount = "1",
            qualityGateAllRunsPassed = "false",
        )

        val gate = evaluateNpuS1QualityGate(state)

        assertEquals(NPU_S1_QUALITY_GATE_STATUS_FAIL, gate.status)
        assertTrue(gate.reason.contains("prompt_profile_not_gemma_it_user_model"))
        assertTrue(gate.reason.contains("quality_gate_all_runs_not_passed"))
        assertTrue(gate.reason.contains("quality_candidate_not_pass"))
        assertTrue(gate.reason.contains("output_contains_placeholder_not_false"))
        assertTrue(gate.reason.contains("output_looks_business_template_not_false"))
    }

    @Test
    fun `quality gate fails when one of twenty quality runs fails`() {
        val state = full20SuccessState().copy(
            selectedQualityPromptProfile =
                NpuS1PersistentCustomJniQualityPromptProfile.GEMMA_IT_USER_MODEL_FULL_20_QUALITY.wireValue,
            outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL,
            outputEmpty = "false",
            outputOnlyNewline = "false",
            outputContainsPlaceholder = "false",
            outputLooksBusinessTemplate = "false",
            outputQualityCandidateAssistantRepetition = "false",
            outputQualityCandidateQaContinuation = "false",
            firstQualityFailureRunIndex = "17",
            firstQualityFailureReason = "self_intro_template_leak",
            failedQualityRunCount = "1",
            qualityGateAllRunsPassed = "false",
        )

        val gate = evaluateNpuS1QualityGate(state)
        val text = formatNpuS1PersistentCustomJniDiagnosticsForDev(state)

        assertEquals(NPU_S1_QUALITY_GATE_STATUS_FAIL, gate.status)
        assertTrue(gate.reason.contains("quality_candidate_not_pass"))
        assertTrue(gate.reason.contains("quality_gate_all_runs_not_passed"))
        assertTrue(gate.reason.contains("failed_quality_run_count_not_zero"))
        assertTrue(text.contains("first_quality_failure_run_index=17"))
        assertTrue(text.contains("first_quality_failure_reason=self_intro_template_leak"))
        assertTrue(text.contains("failed_quality_run_count=1"))
        assertTrue(text.contains("npu_s1_quality_gate_20_run_status=fail"))

        val readiness = evaluateNpuS1NormalChatUnblockReadiness(
            promotionGate = evaluateNpuS1PromotionGate(state),
            qualityGate = gate,
        )
        assertEquals(NPU_S1_NORMAL_CHAT_UNBLOCK_READINESS_NOT_READY, readiness.status)
        assertTrue(readiness.reason.contains("quality_gate_not_pass"))
    }

    @Test
    fun `quality gate fails alias profile even when candidate output passes`() {
        val state = full20SuccessState().copy(
            selectedQualityPromptProfile = NpuS1PersistentCustomJniQualityPromptProfile.AI_EDGE_GALLERY_LIKE.wireValue,
            outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS,
            outputEmpty = "false",
            outputOnlyNewline = "false",
            outputContainsPlaceholder = "false",
            outputLooksBusinessTemplate = "false",
            outputQualityCandidateAssistantRepetition = "false",
            outputQualityCandidateQaContinuation = "false",
            failedQualityRunCount = "0",
            qualityGateAllRunsPassed = "true",
        )

        val gate = evaluateNpuS1QualityGate(state)

        assertEquals(NPU_S1_QUALITY_GATE_STATUS_FAIL, gate.status)
        assertTrue(gate.reason.contains("prompt_profile_not_gemma_it_user_model_full_20_quality"))

        val readiness = evaluateNpuS1NormalChatUnblockReadiness(
            promotionGate = evaluateNpuS1PromotionGate(state),
            qualityGate = gate,
        )
        assertEquals(NPU_S1_NORMAL_CHAT_UNBLOCK_READINESS_NOT_READY, readiness.status)
        assertTrue(readiness.reason.contains("required_profile_not_gemma_it_user_model_full_20_quality"))
    }

    @Test
    fun `full 20 crash safety success with suspect quality does not pass quality readiness`() {
        val state = full20SuccessState(
            records = successRecords(
                rawOutput = "。お元気ですか。いつもお世話になっております。[あなたの名前]です。",
                qualityClassification = NPU_S1_OUTPUT_QUALITY_PROMPT_IGNORED_SUSPECT,
            ),
        )

        val gate = evaluateNpuS1PromotionGate(state)
        val text = formatNpuS1PersistentCustomJniDiagnosticsForDev(state)

        assertEquals(NPU_S1_PROMOTION_GATE_STATUS_PASS, gate.status)
        assertTrue(gate.normalChatUnblockAllowed)
        assertTrue(text.contains("npu_s1_promotion_gate_output_quality=suspect"))
        assertTrue(text.contains("npu_s1_promotion_gate_normal_chat_unblock=policy_allowed"))
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
            promptWrapperFamily = "none",
            promptProfileHypothesis = "baseline_current_probe_prompt",
            prefillTextOrTokenNote = "native_RunPrefill_receives_final_prompt_text",
            firstOutputChars = "。お元気ですか。",
            outputPrefixClassification = NPU_S1_OUTPUT_QUALITY_PUNCTUATION_START,
            outputQualityReason = "starts_with_punctuation",
            outputRepeatsSameAcrossRuns = "false",
            outputLooksBusinessTemplate = "false",
            outputStartsWithPunctuation = "true",
            outputContainsPlaceholder = "false",
            outputOnlyNewline = "false",
            outputEmpty = "false",
            outputEqualsAcrossRuns = "false",
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
        assertTrue(text.contains("selected_quality_prompt_profile=current_probe_quality"))
        assertTrue(text.contains("quality_comparison_prompt_set=current_probe_quality,raw_prompt_quality"))
        assertTrue(text.contains("npu_s1_recommended_prompt_profile=gemma_it_user_model"))
        assertTrue(text.contains("npu_s1_recommended_prompt_profile_reason=gemma_it_user_model_produced"))
        assertTrue(text.contains("npu_s1_prompt_profile_alias_note=ai_edge_gallery_like_is_currently_duplicate"))
        assertTrue(text.contains("npu_s1_unsafe_prompt_profile_note=bos_eos_like_if_supported_by_existing_code"))
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
        assertTrue(text.contains("prompt_wrapper_family=none"))
        assertTrue(text.contains("prompt_profile_hypothesis=baseline_current_probe_prompt"))
        assertTrue(text.contains("prefill_text_or_token_note=native_RunPrefill_receives_final_prompt_text"))
        assertTrue(text.contains("first_output_chars=。お元気ですか。"))
        assertTrue(text.contains("output_prefix_classification=punctuation_start"))
        assertTrue(text.contains("output_quality_reason=starts_with_punctuation"))
        assertTrue(text.contains("output_repeats_same_across_runs=false"))
        assertTrue(text.contains("output_equals_across_runs=false"))
        assertTrue(text.contains("output_looks_business_template=false"))
        assertTrue(text.contains("output_starts_with_punctuation=true"))
        assertTrue(text.contains("output_contains_placeholder=false"))
        assertTrue(text.contains("output_only_newline=false"))
        assertTrue(text.contains("output_empty=false"))
        assertTrue(text.contains("npu_s1_quality_gate_status=unknown"))
        assertTrue(text.contains("npu_s1_quality_gate_reason=quality_candidate_not_run"))
        assertTrue(text.contains("npu_s1_quality_gate_prompt_profile=current_probe_quality"))
        assertTrue(text.contains("output_quality_candidate_status=quality_candidate_unknown"))
        assertTrue(text.contains("output_quality_candidate_reason=unavailable"))
        assertTrue(text.contains("prefill_token_count=unavailable"))
        assertTrue(text.contains("decode_token_count=unavailable"))
        assertTrue(text.contains("first_output_token_id=unavailable"))
        assertTrue(text.contains("token_diagnostics_note=token_ids_not_exposed_by_current_custom_jni_probe_without_native_rebuild"))
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
                    prefillInputText = "こんにちは",
                    prefillInputChars = "5",
                    decodeFirstChunkText = "。お元気ですか。いつもお世話になっております。",
                    decodeFirstChunkChars = "24",
                    decodeFirstNonEmptyChunkText = "。お元気ですか。いつもお世話になっております。",
                    decodeFirstNonEmptyChunkChars = "24",
                    rawOutput = "。お元気ですか。いつもお世話になっております。[あなたの名前]です。",
                    sanitizedOutput = "。お元気ですか。いつもお世話になっております。[あなたの名前]です。",
                    outputPrefix20Chars = "。お元気ですか。いつもお世話に",
                    outputFirst1Char = "。",
                    outputFirst5Chars = "。お元気",
                    outputFirst20Chars = "。お元気ですか。いつもお世話に",
                    outputLast20Chars = "っております。[あなたの名前]です。",
                    outputLengthChars = "35",
                    outputNewlineCount = "0",
                    outputLeadingPunctuationCount = "1",
                    outputTrimmedFirstChars = "。お元気ですか。いつもお世話に",
                    outputAfterLstripFirstChars = "。お元気ですか。いつもお世話に",
                    outputEqualsAcrossRuns = "true",
                    startsWithPunctuation = "true",
                    containsBusinessPhrase = "true",
                    containsPlaceholder = "true",
                    outputOnlyNewline = "false",
                    outputEmpty = "false",
                    qualityClassification = NPU_S1_OUTPUT_QUALITY_PROMPT_IGNORED_SUSPECT,
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
        assertTrue(text.contains("prefill_input_text=こんにちは"))
        assertTrue(text.contains("prefill_input_chars=5"))
        assertTrue(text.contains("decode_first_chunk_text=。お元気ですか。いつもお世話になっております。"))
        assertTrue(text.contains("decode_first_non_empty_chunk_text=。お元気ですか。いつもお世話になっております。"))
        assertTrue(text.contains("output_prefix_20_chars=。お元気ですか。いつもお世話に"))
        assertTrue(text.contains("output_first_1_char=。"))
        assertTrue(text.contains("output_first_5_chars=。お元気"))
        assertTrue(text.contains("output_first_20_chars=。お元気ですか。いつもお世話に"))
        assertTrue(text.contains("output_last_20_chars=っております。[あなたの名前]です。"))
        assertTrue(text.contains("output_length_chars=35"))
        assertTrue(text.contains("output_newline_count=0"))
        assertTrue(text.contains("output_leading_punctuation_count=1"))
        assertTrue(text.contains("output_trimmed_first_chars=。お元気ですか。いつもお世話に"))
        assertTrue(text.contains("output_after_lstrip_first_chars=。お元気ですか。いつもお世話に"))
        assertTrue(text.contains("output_equals_across_runs=true"))
        assertTrue(text.contains("starts_with_punctuation=true"))
        assertTrue(text.contains("contains_business_phrase=true"))
        assertTrue(text.contains("contains_placeholder=true"))
        assertTrue(text.contains("output_only_newline=false"))
        assertTrue(text.contains("output_empty=false"))
        assertTrue(text.contains("prefill_token_count=unavailable"))
        assertTrue(text.contains("decode_token_count=unavailable"))
        assertTrue(text.contains("first_output_token_id=unavailable"))
        assertTrue(text.contains("first_5_output_token_ids=unavailable"))
        assertTrue(text.contains("eos_seen=unavailable"))
        assertTrue(text.contains("special_token_seen_in_output=unavailable"))
        assertTrue(text.contains("quality_classification=prompt_ignored_suspect"))
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
        assertTrue(text.contains("output_only_newline=unavailable"))
        assertTrue(text.contains("output_empty=unavailable"))
        assertTrue(text.contains("prefill_token_count=unavailable"))
        assertTrue(text.contains("decode_token_count=unavailable"))
        assertTrue(text.contains("first_output_token_id=unavailable"))
        assertTrue(text.contains("special_token_seen_in_output=unavailable"))
        assertTrue(text.contains("token_diagnostics_note=token_ids_not_exposed_by_current_custom_jni_probe_without_native_rebuild"))
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
