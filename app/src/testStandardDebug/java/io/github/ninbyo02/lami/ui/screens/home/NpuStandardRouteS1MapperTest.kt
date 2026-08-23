package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1MapperTest {
    @Test
    fun `natural Japanese dev-only result maps to successful S1 result`() {
        val result = NpuStandardRouteS1Mapper.map(successRaw())

        assertTrue(result.successCriteriaMet)
        assertEquals("こんにちは。", result.displayText)
        assertEquals("こんにちは。", result.sanitizedOutput)
        assertEquals("natural_japanese", result.qualityClassification)
        assertEquals("QNN_HTP_V79_FastRPC_native_diag", result.npuBackendEvidence)
        assertEquals(32, result.selection.requestedMaxOutputTokens)
        assertEquals(32, result.selection.effectiveMaxOutputTokens)
        assertFalse(result.selection.sideEffects.db)
        assertFalse(result.selection.sideEffects.tts)
        assertFalse(result.selection.sideEffects.markdown)
        assertFalse(result.selection.sideEffects.streaming)
        assertFalse(result.selection.sideEffects.backendNpuPersisted)
        assertFalse(result.selection.sideEffects.conversationHistorySaved)
    }

    @Test
    fun `result success string is accepted as success equivalent`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(status = "", result = "success", success = null),
        )

        assertTrue(result.successCriteriaMet)
    }

    @Test
    fun `success boolean is accepted as success equivalent`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(status = "", result = "", success = true),
        )

        assertTrue(result.successCriteriaMet)
    }

    @Test
    fun `FastRPC evidence alone is normalized to S1 NPU evidence`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(npuBackendEvidence = "FastRPC native diag"),
        )

        assertTrue(result.successCriteriaMet)
        assertEquals("QNN_HTP_V79_FastRPC_native_diag", result.npuBackendEvidence)
    }

    @Test
    fun `failure conditions do not meet S1 success criteria`() {
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(fallbackUsed = true)).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(timeout = true)).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(freshCrash = true)).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(runDecodeReached = false)).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(npuBackendEvidence = "")).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(rawOutput = "", sanitizedOutput = "")).successCriteriaMet)
        assertFalse(NpuStandardRouteS1Mapper.map(successRaw(status = "failure", result = "failure", success = false)).successCriteriaMet)
    }

    @Test
    fun `template artifact raw output can pass when sanitized output is a quality candidate`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">こんにちは！何かお手伝いできることはありますか？<end_of_turn>",
                sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
            ),
        )

        assertTrue(result.successCriteriaMet)
        assertEquals("こんにちは！何かお手伝いできることはありますか？", result.displayText)
        assertEquals("quality_candidate_pass", result.outputQualityCandidateStatus)
        assertTrue(result.outputQualityCandidateReason.contains("natural_japanese_after_safe_leading_gt"))
    }

    @Test
    fun `safe end turn artifact raw output can pass even when sanitized output is empty`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">こんにちは！<end_of_turn>",
                sanitizedOutput = "",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
            ),
        )

        assertTrue(result.successCriteriaMet)
        assertEquals("こんにちは！", result.displayText)
        assertEquals("quality_candidate_pass", result.outputQualityCandidateStatus)
    }

    @Test
    fun `mixed language classification does not fail when quality candidate passes`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = "私はLamiです。よろしくお願いします。",
                sanitizedOutput = "私はLamiです。よろしくお願いします。",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_MIXED_LANGUAGE,
            ),
        )

        assertTrue(result.successCriteriaMet)
        assertEquals("quality_candidate_pass", result.outputQualityCandidateStatus)
        assertEquals("私はLamiです。よろしくお願いします。", result.displayText)
    }

    @Test
    fun `non arithmetic prompt keeps TTS text equal to actual display text`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = "私はLamiです。よろしくお願いします。",
                sanitizedOutput = "私はLamiです。よろしくお願いします。",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                inputPrompt = "あなたは誰ですか？",
            ),
        )

        assertTrue(result.successCriteriaMet)
        assertEquals("私はLamiです。よろしくお願いします。", result.actualDisplayText)
        assertEquals(result.actualDisplayText, result.ttsText)
    }

    @Test
    fun `ambiguous one character prompt uses a short rewritten NPU request`() {
        val prompt = "あ"

        val rewrite = NpuStandardRouteS1Contract.rewritePromptForNative(prompt)
        val request = RealNpuStandardRouteS1Provider.request(
            userPrompt = prompt,
            maxOutputTokens = 4096,
        )

        assertFalse(rewrite.arithmeticPromptDetected)
        assertTrue(rewrite.shortPromptRewriteApplied)
        assertTrue(rewrite.rewrittenPromptText.contains("ユーザーの入力は「$prompt」です。"))
        assertTrue(rewrite.rewrittenPromptText.contains("入力の続きを自然に促す"))
        assertEquals(prompt, request.userPrompt)
        assertEquals(128, request.maxOutputTokens)
    }

    @Test
    fun `three character prompt keeps the normal NPU request`() {
        val prompt = "あのね"

        val rewrite = NpuStandardRouteS1Contract.rewritePromptForNative(prompt)
        val request = RealNpuStandardRouteS1Provider.request(
            userPrompt = prompt,
            maxOutputTokens = 4096,
        )

        assertFalse(rewrite.arithmeticPromptDetected)
        assertFalse(rewrite.shortPromptRewriteApplied)
        assertEquals(prompt, rewrite.rewrittenPromptText)
        assertEquals(prompt, request.userPrompt)
        assertEquals(4096, request.maxOutputTokens)
    }

    @Test
    fun `arithmetic rewrite keeps the existing token budget`() {
        val prompt = "1+1"

        val rewrite = NpuStandardRouteS1Contract.rewritePromptForNative(prompt)
        val request = RealNpuStandardRouteS1Provider.request(
            userPrompt = prompt,
            maxOutputTokens = 4096,
        )

        assertTrue(rewrite.arithmeticPromptDetected)
        assertTrue(rewrite.shortPromptRewriteApplied)
        assertEquals(4096, request.maxOutputTokens)
        assertEquals(
            NpuStandardRoutePreferences.MAX_OUTPUT_TOKENS_CLAMP_REASON_NONE,
            NpuStandardRouteS1Contract.maxOutputTokensClampReasonForPrompt(
                userPrompt = prompt,
                requestedMaxOutputTokens = 4096,
                effectiveMaxOutputTokens = 4096,
            ),
        )
    }

    @Test
    fun `supplementary Unicode short prompts use code point boundaries`() {
        val oneCodePoint = "😀"
        val twoCodePoints = "😀😀"
        val threeCodePoints = "😀😀😀"

        listOf(oneCodePoint, twoCodePoints).forEach { prompt ->
            val rewrite = NpuStandardRouteS1Contract.rewritePromptForNative("  $prompt  ")
            assertTrue(rewrite.shortPromptRewriteApplied)
            assertEquals(128, NpuStandardRouteS1Contract.maxOutputTokensForPrompt(prompt, 4096))
            assertEquals(64, NpuStandardRouteS1Contract.maxOutputTokensForPrompt(prompt, 64))
        }

        val normalRewrite = NpuStandardRouteS1Contract.rewritePromptForNative(threeCodePoints)
        assertFalse(normalRewrite.shortPromptRewriteApplied)
        assertEquals(4096, NpuStandardRouteS1Contract.maxOutputTokensForPrompt(threeCodePoints, 4096))
    }

    @Test
    fun `ambiguous short prompt reports its own token clamp policy`() {
        assertEquals(
            128,
            NpuStandardRouteS1Contract.maxOutputTokensClampLimitForPrompt("あ"),
        )
        assertEquals(
            NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS_CLAMP_REASON_SHORT_PROMPT_LIMIT,
            NpuStandardRouteS1Contract.maxOutputTokensClampReasonForPrompt(
                userPrompt = "あ",
                requestedMaxOutputTokens = 4096,
                effectiveMaxOutputTokens = 128,
            ),
        )
    }

    @Test
    fun `arithmetic answer with safe spaced end turn token passes quality candidate`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">\n2\n< end_of_turn>",
                sanitizedOutput = "2\n< end_of_turn>",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "１＋１は？",
            ),
        )

        assertTrue(result.successCriteriaMet)
        assertEquals("2", result.displayText)
        assertEquals("2", result.actualDisplayText)
        assertEquals("2です。", result.ttsText)
        assertEquals("2", result.preparedOutput)
        assertEquals("quality_candidate_pass", result.outputQualityCandidateStatus)
        assertFalse(result.outputQualityCandidateReason.contains("special_token_leak"))
    }

    @Test
    fun `arithmetic answer prefix and closing end turn variants are prepared as answer only`() {
        val fullWidth = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">答え:2</end_of_turn>",
                sanitizedOutput = "答え:2</end_of_turn>",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "１＋１は",
            ),
        )
        val ascii = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">1+1は\n答え:2</end_of_turn>",
                sanitizedOutput = "1+1は\n答え:2</end_of_turn>",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "1+1は",
            ),
        )
        val asciiQuestion = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">1+1は?\n答え:2</end_of_turn>",
                sanitizedOutput = "1+1は?\n答え:2</end_of_turn>",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "1+1は?",
            ),
        )
        val fullWidthAnswer = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">１＋１は\n答え:２</end_of_turn>",
                sanitizedOutput = "１＋１は\n答え:２</end_of_turn>",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "１＋１は",
            ),
        )
        val fullWidthQuestionAnswer = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">１＋１は？\n答え:２</end_of_turn>",
                sanitizedOutput = "１＋１は？\n答え:２</end_of_turn>",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "１＋１は？",
            ),
        )
        val problemAnswer = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">問題: 1+1は\n答え:2</end_of_turn>",
                sanitizedOutput = "問題: 1+1は\n答え:2</end_of_turn>",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "1+1は",
            ),
        )
        val unclosedClosing = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">答え:2</ end_of_turn",
                sanitizedOutput = "答え:2</ end_of_turn",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "１＋１は？",
            ),
        )

        listOf(
            fullWidth,
            ascii,
            asciiQuestion,
            fullWidthAnswer,
            fullWidthQuestionAnswer,
            problemAnswer,
            unclosedClosing,
        ).forEach { result ->
            assertTrue(result.successCriteriaMet)
            assertEquals("quality_candidate_pass", result.outputQualityCandidateStatus)
            assertFalse(result.displayText.contains("end_of_turn"))
        }
        assertEquals("2", fullWidth.displayText)
        assertEquals("2", ascii.displayText)
        assertEquals("2", asciiQuestion.displayText)
        assertEquals("２", fullWidthAnswer.displayText)
        assertEquals("２", fullWidthQuestionAnswer.displayText)
        assertEquals("2", problemAnswer.displayText)
        assertEquals("2", unclosedClosing.displayText)
        assertEquals("2です。", asciiQuestion.ttsText)
        assertEquals("２です。", fullWidthQuestionAnswer.ttsText)
    }

    @Test
    fun `arithmetic answer before tail turn leak passes with prepared display`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">2</start_of_turn>\n<end_of_turn>\n<start_of_turn>user>次の計算に日本語で",
                sanitizedOutput = "2</start_of_turn>\n\n次の計算に日本語で",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "1+1は？",
            ),
        )

        assertTrue(result.successCriteriaMet)
        assertEquals("2", result.displayText)
        assertEquals("2", result.actualDisplayText)
        assertEquals("2です。", result.ttsText)
        assertEquals("2", result.preparedOutput)
        assertEquals("quality_candidate_pass", result.outputQualityCandidateStatus)
        assertEquals(
            "natural_japanese_after_arithmetic_answer_extraction_with_tail_leak_cleanup",
            result.outputQualityCandidateReason,
        )
        assertTrue(result.outputQualityCandidate.arithmeticTailLeakDetected)
        assertTrue(result.outputQualityCandidate.arithmeticTailLeakIgnoredForDisplay)
    }

    @Test
    fun `non arithmetic tail turn leak remains quality failure`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">こんにちは</start_of_turn>\n<start_of_turn>user>こんにちは",
                sanitizedOutput = "こんにちは</start_of_turn>\nこんにちは",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "こんにちは",
            ),
        )

        assertFalse(result.successCriteriaMet)
        assertEquals("quality_candidate_fail", result.outputQualityCandidateStatus)
        assertTrue(result.outputQualityCandidateReason.contains("special_token_leak"))
        assertFalse(result.outputQualityCandidate.arithmeticTailLeakIgnoredForDisplay)
    }

    @Test
    fun `arithmetic tail leak without answer remains quality failure`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">答え:三</start_of_turn>\n<start_of_turn>user>次の計算",
                sanitizedOutput = "答え:三</start_of_turn>\n次の計算",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "1+1は？",
            ),
        )

        assertFalse(result.successCriteriaMet)
        assertEquals("quality_candidate_fail", result.outputQualityCandidateStatus)
        assertTrue(result.outputQualityCandidateReason.contains("arithmetic_answer_missing"))
        assertFalse(result.outputQualityCandidate.arithmeticTailLeakIgnoredForDisplay)
    }

    @Test
    fun `arithmetic answer passes but prompt repetition fails quality candidate`() {
        val answered = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = "１＋１は２です",
                sanitizedOutput = "１＋１は２です",
                inputPrompt = "１＋１は？",
            ),
        )
        val answeredWithoutQuestionMark = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = "１＋１は２です",
                sanitizedOutput = "１＋１は２です",
                inputPrompt = "１＋１は",
            ),
        )
        val echoed = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = "１＋１は？",
                sanitizedOutput = "１＋１は？",
                inputPrompt = "１＋１は？",
            ),
        )

        assertTrue(answered.successCriteriaMet)
        assertEquals("quality_candidate_pass", answered.outputQualityCandidateStatus)
        assertTrue(answeredWithoutQuestionMark.successCriteriaMet)
        assertEquals("quality_candidate_pass", answeredWithoutQuestionMark.outputQualityCandidateStatus)
        assertFalse(echoed.successCriteriaMet)
        assertEquals("quality_candidate_fail", echoed.outputQualityCandidateStatus)
        assertTrue(echoed.outputQualityCandidateReason.contains("prompt_repetition_only"))
        assertTrue(echoed.outputQualityCandidateReason.contains("arithmetic_answer_missing"))
    }

    @Test
    fun `raw role contamination is classified as failure even with natural sanitized output`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = "どうしましたか。\nユーザー: ああああ\nアシスタント: 何か困っていますか。",
                sanitizedOutput = "どうしましたか。",
                qualityClassification = "natural_japanese",
            ),
        )

        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("raw_role_contamination", result.reason)
        assertEquals("role_contamination", result.qualityClassification)
        assertEquals("どうしましたか。", result.sanitizedOutput)
    }

    @Test
    fun `mapper keeps S1 side effects disconnected and max output fixed`() {
        val result = NpuStandardRouteS1Mapper.map(successRaw())
        val text = result.displayText

        assertEquals(32, result.selection.requestedMaxOutputTokens)
        assertEquals(32, result.selection.effectiveMaxOutputTokens)
        assertTrue(result.selection.sideEffects.allDisconnected)
        assertEquals("こんにちは。", text)
        assertFalse(result.selection.sideEffects.db)
        assertFalse(result.selection.sideEffects.tts)
        assertFalse(result.selection.sideEffects.markdown)
        assertFalse(result.selection.sideEffects.streaming)
    }

    @Test
    fun `natural Japanese answer before leaked user turn is revalidated and delivered as prepared prefix only`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = """
                    >はい、今日の予定を3つ提案します。
                    1. 午前のタスクを終える。
                    2. 午後に休憩する。
                    3. 夕方に連絡を整理する。
                    <start_of_turn>user
                    別の案も出してください。
                    <end_of_turn>
                """.trimIndent(),
                sanitizedOutput = """
                    はい、今日の予定を3つ提案します。
                    1. 午前のタスクを終える。
                    2. 午後に休憩する。
                    3. 夕方に連絡を整理する。
                    <start_of_turn>user
                    別の案も出してください。
                """.trimIndent(),
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "今日の予定を3つ短く提案して",
            ),
        )
        val expected = """
            はい、今日の予定を3つ提案します。
            1. 午前のタスクを終える。
            2. 午後に休憩する。
            3. 夕方に連絡を整理する。
        """.trimIndent()

        assertTrue(result.successCriteriaMet)
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS, result.outputQualityCandidateStatus)
        assertEquals(
            "natural_japanese_after_tail_turn_leak_prefix_revalidation",
            result.outputQualityCandidateReason,
        )
        assertEquals(expected, result.preparedOutput)
        assertEquals(expected, result.actualDisplayText)
        assertEquals(expected, result.ttsText)
        assertFalse(result.actualDisplayText.contains("start_of_turn"))
        assertFalse(result.actualDisplayText.contains("別の案"))
    }

    @Test
    fun `prompt echo before leaked user turn remains fail closed after prefix revalidation`() {
        val prompt = "今日の予定を3つ短く提案して"
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">$prompt\n<start_of_turn>user\n別の案も出してください。",
                sanitizedOutput = "$prompt\n<start_of_turn>user\n別の案も出してください。",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = prompt,
            ),
        )

        assertFalse(result.successCriteriaMet)
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, result.outputQualityCandidateStatus)
    }

    @Test
    fun `unclosed start turn marker remains fail closed`() {
        val result = NpuStandardRouteS1Mapper.map(
            successRaw(
                rawOutput = ">通常の回答です。<start_of_turn",
                sanitizedOutput = "通常の回答です。<start_of_turn",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                inputPrompt = "回答して",
            ),
        )

        assertFalse(result.successCriteriaMet)
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, result.outputQualityCandidateStatus)
        assertTrue(result.outputQualityCandidateReason.contains("raw_unclosed_special_token"))
    }

    private fun successRaw(
        status: String = "success",
        result: String = "",
        success: Boolean? = null,
        rawOutput: String = "こんにちは。",
        sanitizedOutput: String = "こんにちは。",
        qualityClassification: String = "natural_japanese",
        runDecodeReached: Boolean = true,
        npuBackendEvidence: String = "QNN_HTP_V79_FastRPC_native_diag",
        fallbackUsed: Boolean = false,
        timeout: Boolean = false,
        freshCrash: Boolean = false,
        inputPrompt: String = "",
        requestedMaxOutputTokens: Int = 32,
        effectiveMaxOutputTokens: Int = 32,
    ): NpuStandardRouteS1RawResult = NpuStandardRouteS1RawResult(
        status = status,
        result = result,
        success = success,
        reason = "success",
        rawOutput = rawOutput,
        sanitizedOutput = sanitizedOutput,
        qualityClassification = qualityClassification,
        runDecodeReached = runDecodeReached,
        npuBackendEvidence = npuBackendEvidence,
        fallbackUsed = fallbackUsed,
        timeout = timeout,
        freshCrash = freshCrash,
        requestedMaxOutputTokens = requestedMaxOutputTokens,
        effectiveMaxOutputTokens = effectiveMaxOutputTokens,
        inputPrompt = inputPrompt,
    )
}
