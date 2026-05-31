package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.InferenceTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1ChatScreenGateTest {
    @Test
    fun `gate off preserves existing route`() {
        assertFalse(
            shouldEnterNpuStandardRouteS1(
                enabled = false,
                selectedInferenceTarget = InferenceTarget.LOCAL,
                hasImageInput = false,
                requestPrompt = "こんにちは",
            ),
        )
    }

    @Test
    fun `gate on enters bridge path only for local text prompt`() {
        assertTrue(
            shouldEnterNpuStandardRouteS1(
                enabled = true,
                selectedInferenceTarget = InferenceTarget.LOCAL,
                hasImageInput = false,
                requestPrompt = "こんにちは",
            ),
        )
        assertFalse(
            shouldEnterNpuStandardRouteS1(
                enabled = true,
                selectedInferenceTarget = InferenceTarget.SERVER,
                hasImageInput = false,
                requestPrompt = "こんにちは",
            ),
        )
        assertFalse(
            shouldEnterNpuStandardRouteS1(
                enabled = true,
                selectedInferenceTarget = InferenceTarget.LOCAL,
                hasImageInput = true,
                requestPrompt = "こんにちは",
            ),
        )
        assertFalse(
            shouldEnterNpuStandardRouteS1(
                enabled = true,
                selectedInferenceTarget = InferenceTarget.LOCAL,
                hasImageInput = false,
                requestPrompt = "",
            ),
        )
    }

    @Test
    fun `legacy QAIRT ChatScreen route hard gate blocks developer toggle by default`() {
        assertFalse(
            shouldEnterLegacyQairt244ChatScreenRoute(
                hardGateEnabled = false,
                debugBuild = true,
                customBuildExperiment = false,
                developerAccessEnabled = true,
                legacyToggleEnabled = true,
            ),
        )
        assertFalse(
            shouldEnterLegacyQairt244ChatScreenRoute(
                hardGateEnabled = false,
                debugBuild = true,
                customBuildExperiment = true,
                developerAccessEnabled = true,
                legacyToggleEnabled = true,
            ),
        )
    }

    @Test
    fun `legacy QAIRT ChatScreen route hard gate restores previous guarded conditions`() {
        assertTrue(
            shouldEnterLegacyQairt244ChatScreenRoute(
                hardGateEnabled = true,
                debugBuild = true,
                customBuildExperiment = false,
                developerAccessEnabled = true,
                legacyToggleEnabled = true,
            ),
        )
        assertFalse(
            shouldEnterLegacyQairt244ChatScreenRoute(
                hardGateEnabled = true,
                debugBuild = true,
                customBuildExperiment = false,
                developerAccessEnabled = false,
                legacyToggleEnabled = true,
            ),
        )
        assertFalse(
            shouldEnterLegacyQairt244ChatScreenRoute(
                hardGateEnabled = true,
                debugBuild = true,
                customBuildExperiment = false,
                developerAccessEnabled = true,
                legacyToggleEnabled = false,
            ),
        )
        assertTrue(
            shouldEnterLegacyQairt244ChatScreenRoute(
                hardGateEnabled = true,
                debugBuild = true,
                customBuildExperiment = true,
                developerAccessEnabled = false,
                legacyToggleEnabled = true,
            ),
        )
    }

    @Test
    fun `empty after sanitize failure shows fallback without marking S1 success`() {
        val result = s1EmptyAfterSanitizeFailureResult()

        assertTrue(shouldShowNpuStandardRouteS1Fallback(result))
        assertEquals(
            "こんばんは。",
            resolveNpuStandardRouteS1Fallback(
                userPrompt = "こんばんは",
                result = result,
            )?.text,
        )
        assertEquals(
            NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING,
            resolveNpuStandardRouteS1Fallback(
                userPrompt = "こんばんは",
                result = result,
            )?.kind,
        )
        assertEquals(
            "すみません、応答を生成できませんでした。",
            NPU_STANDARD_ROUTE_S1_EMPTY_AFTER_SANITIZE_FALLBACK_TEXT,
        )
        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("empty_after_sanitize", result.reason)
    }

    @Test
    fun `safe greeting fallback handles only short greeting failures`() {
        val result = s1EmptyAfterSanitizeFailureResult()

        val expectedFallbacks = mapOf(
            "こんにちは" to "こんにちは。",
            "おはよう" to "おはようございます。",
            "こんばんは" to "こんばんは。",
            "ハロー" to "こんにちは。",
            "hello" to "こんにちは。",
            "hi" to "こんにちは。",
        )

        expectedFallbacks.forEach { (prompt, expectedText) ->
            val fallback = resolveNpuStandardRouteS1SafeGreetingFallback(
                userPrompt = prompt,
                result = result,
            )

            assertEquals(expectedText, fallback?.text)
            assertEquals(NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING, fallback?.kind)
        }
    }

    @Test
    fun `safe greeting fallback also handles mixed language failure reason`() {
        val fallback = resolveNpuStandardRouteS1SafeGreetingFallback(
            userPrompt = "こんばんは",
            result = s1MixedLanguageFailureResult(),
        )

        assertEquals("こんばんは。", fallback?.text)
        assertEquals(NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING, fallback?.kind)
    }

    @Test
    fun `safe greeting fallback does not apply to long prompt question echo or assistant stub`() {
        assertNull(
            resolveNpuStandardRouteS1SafeGreetingFallback(
                userPrompt = "こんばんは、今日の予定について相談したいです",
                result = s1EmptyAfterSanitizeFailureResult(),
            ),
        )
        assertNull(
            resolveNpuStandardRouteS1SafeGreetingFallback(
                userPrompt = "明日の天気は",
                result = s1QuestionEchoFailureResult(),
            ),
        )
        assertNull(
            resolveNpuStandardRouteS1SafeGreetingFallback(
                userPrompt = "こんにちは",
                result = s1AssistantStubFailureResult(),
            ),
        )
    }

    @Test
    fun `empty after sanitize fallback does not enable DB S4A or TTS`() {
        val result = s1EmptyAfterSanitizeFailureResult()
        val s2Mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "こんばんは",
            s1Result = result,
        )
        val s4Mapping = NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
            s1Result = result,
            finalText = NPU_STANDARD_ROUTE_S1_EMPTY_AFTER_SANITIZE_FALLBACK_TEXT,
        )
        val s5Mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = result,
            finalAssistantText = NPU_STANDARD_ROUTE_S1_EMPTY_AFTER_SANITIZE_FALLBACK_TEXT,
            ttsEnabled = true,
        )

        assertFalse(shouldPersistNpuStandardRouteS2Db(enabled = true, mapping = s2Mapping))
        assertNull(s2Mapping.saveCandidate)
        assertFalse(shouldStartNpuStandardRouteS4APseudoStreaming(enabled = true, mapping = s4Mapping))
        assertNull(s4Mapping.pseudoStreamingCandidate)
        assertFalse(shouldPrepareNpuStandardRouteS5Tts(enabled = true, mapping = s5Mapping))
        assertNull(s5Mapping.ttsCandidate)
        assertEquals(NpuStandardRouteS5TtsContract.FAILURE_S1_NOT_SUCCESS, s5Mapping.failureReason)
    }

    @Test
    fun `question echo failure shows fallback without enabling DB S4A or TTS`() {
        val result = s1QuestionEchoFailureResult()
        val s2Mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "明日の天気は",
            s1Result = result,
        )
        val s4Mapping = NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
            s1Result = result,
            finalText = NPU_STANDARD_ROUTE_S1_EMPTY_AFTER_SANITIZE_FALLBACK_TEXT,
        )
        val s5Mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = result,
            finalAssistantText = NPU_STANDARD_ROUTE_S1_EMPTY_AFTER_SANITIZE_FALLBACK_TEXT,
            ttsEnabled = true,
        )

        assertTrue(shouldShowNpuStandardRouteS1Fallback(result))
        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("question_echo", result.reason)
        assertEquals("question_echo", result.qualityClassification)
        assertFalse(shouldPersistNpuStandardRouteS2Db(enabled = true, mapping = s2Mapping))
        assertNull(s2Mapping.saveCandidate)
        assertFalse(shouldStartNpuStandardRouteS4APseudoStreaming(enabled = true, mapping = s4Mapping))
        assertNull(s4Mapping.pseudoStreamingCandidate)
        assertFalse(shouldPrepareNpuStandardRouteS5Tts(enabled = true, mapping = s5Mapping))
        assertNull(s5Mapping.ttsCandidate)
    }

    @Test
    fun `assistant stub failure shows fallback without enabling DB S4A or TTS`() {
        val result = s1AssistantStubFailureResult()
        val s2Mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "おはよう",
            s1Result = result,
        )
        val s4Mapping = NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
            s1Result = result,
            finalText = NPU_STANDARD_ROUTE_S1_EMPTY_AFTER_SANITIZE_FALLBACK_TEXT,
        )
        val s5Mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = result,
            finalAssistantText = NPU_STANDARD_ROUTE_S1_EMPTY_AFTER_SANITIZE_FALLBACK_TEXT,
            ttsEnabled = true,
        )

        assertTrue(shouldShowNpuStandardRouteS1Fallback(result))
        assertFalse(result.successCriteriaMet)
        assertEquals("failure", result.status)
        assertEquals("assistant_stub", result.reason)
        assertEquals("assistant_stub", result.qualityClassification)
        assertFalse(shouldPersistNpuStandardRouteS2Db(enabled = true, mapping = s2Mapping))
        assertNull(s2Mapping.saveCandidate)
        assertFalse(shouldStartNpuStandardRouteS4APseudoStreaming(enabled = true, mapping = s4Mapping))
        assertNull(s4Mapping.pseudoStreamingCandidate)
        assertFalse(shouldPrepareNpuStandardRouteS5Tts(enabled = true, mapping = s5Mapping))
        assertNull(s5Mapping.ttsCandidate)
    }

    @Test
    fun `bridge path keeps side effects disconnected`() {
        val result = NpuStandardRouteS1Bridge().run(userPrompt = "好きな色を一つだけ答えてください")

        assertFalse(result.selection.sideEffects.db)
        assertFalse(result.selection.sideEffects.tts)
        assertFalse(result.selection.sideEffects.markdown)
        assertFalse(result.selection.sideEffects.streaming)
        assertFalse(result.selection.sideEffects.backendNpuPersisted)
        assertFalse(result.selection.sideEffects.conversationHistorySaved)
    }

    @Test
    fun `S2 DB gate off preserves S1 display only route`() {
        val mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "こんにちは",
            s1Result = NpuStandardRouteS1Mapper.map(
                NpuStandardRouteS1RawResult(
                    status = "success",
                    reason = "success",
                    rawOutput = "こんにちは。",
                    sanitizedOutput = "こんにちは。",
                    qualityClassification = "natural_japanese",
                    runDecodeReached = true,
                    npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                    fallbackUsed = false,
                    timeout = false,
                    freshCrash = false,
                    requestedMaxOutputTokens = 32,
                    effectiveMaxOutputTokens = 32,
                ),
            ),
        )

        assertTrue(mapping.hasSaveCandidate)
        assertFalse(
            shouldPersistNpuStandardRouteS2Db(
                enabled = false,
                mapping = mapping,
            ),
        )
    }

    @Test
    fun `S2 DB gate on persists only when save candidate is available`() {
        val successMapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "こんにちは",
            s1Result = NpuStandardRouteS1Mapper.map(
                NpuStandardRouteS1RawResult(
                    status = "success",
                    reason = "success",
                    rawOutput = "こんにちは。",
                    sanitizedOutput = "こんにちは。",
                    qualityClassification = "natural_japanese",
                    runDecodeReached = true,
                    npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                    fallbackUsed = false,
                    timeout = false,
                    freshCrash = false,
                    requestedMaxOutputTokens = 32,
                    effectiveMaxOutputTokens = 32,
                ),
            ),
        )
        val failureMapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "こんにちは",
            s1Result = NpuStandardRouteS1Mapper.map(
                NpuStandardRouteS1RawResult(
                    status = "failure",
                    result = "failure",
                    success = false,
                    reason = "test_failure",
                    rawOutput = "",
                    sanitizedOutput = "",
                    qualityClassification = "unknown",
                    runDecodeReached = false,
                    npuBackendEvidence = "",
                    fallbackUsed = false,
                    timeout = false,
                    freshCrash = false,
                    requestedMaxOutputTokens = 32,
                    effectiveMaxOutputTokens = 32,
                ),
            ),
        )

        assertTrue(
            shouldPersistNpuStandardRouteS2Db(
                enabled = true,
                mapping = successMapping,
            ),
        )
        assertFalse(
            shouldPersistNpuStandardRouteS2Db(
                enabled = true,
                mapping = failureMapping,
            ),
        )
    }

    @Test
    fun `S4A pseudo streaming gate off preserves final text without starting chunks`() {
        val mapping = NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
            s1Result = s1SuccessResult(),
            finalText = "こんにちは。今日はNPU応答を段階表示します。最終保存は全文だけです。",
        )

        assertTrue(mapping.hasPseudoStreamingCandidate)
        assertFalse(
            shouldStartNpuStandardRouteS4APseudoStreaming(
                enabled = false,
                mapping = mapping,
            ),
        )
        assertEquals(
            "こんにちは。今日はNPU応答を段階表示します。最終保存は全文だけです。",
            mapping.pseudoStreamingCandidate?.dbPersistedText,
        )
    }

    @Test
    fun `S4A pseudo streaming gate on starts only when candidate is available`() {
        val mapping = NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
            s1Result = s1SuccessResult(),
            finalText = "こんにちは。今日はNPU応答を段階表示します。最終保存は全文だけです。",
        )

        assertTrue(
            shouldStartNpuStandardRouteS4APseudoStreaming(
                enabled = true,
                mapping = mapping,
            ),
        )
        assertEquals(
            mapping.pseudoStreamingCandidate?.finalText,
            mapping.pseudoStreamingCandidate?.chunks?.last(),
        )
        assertFalse(mapping.pseudoStreamingCandidate?.sideEffects?.realTokenStreaming ?: true)
        assertFalse(mapping.pseudoStreamingCandidate?.sideEffects?.tts ?: true)
        assertFalse(mapping.pseudoStreamingCandidate?.sideEffects?.backendNpuPersisted ?: true)
    }

    @Test
    fun `S4A pseudo streaming does not start for failed S1 result`() {
        val mapping = NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
            s1Result = NpuStandardRouteS1Mapper.map(
                NpuStandardRouteS1RawResult(
                    status = "failure",
                    result = "failure",
                    success = false,
                    reason = "test_failure",
                    rawOutput = "",
                    sanitizedOutput = "",
                    qualityClassification = "unknown",
                    runDecodeReached = false,
                    npuBackendEvidence = "",
                    fallbackUsed = false,
                    timeout = false,
                    freshCrash = false,
                    requestedMaxOutputTokens = 32,
                    effectiveMaxOutputTokens = 32,
                ),
            ),
            finalText = "この本文は使われません。",
        )

        assertFalse(mapping.hasPseudoStreamingCandidate)
        assertFalse(
            shouldStartNpuStandardRouteS4APseudoStreaming(
                enabled = true,
                mapping = mapping,
            ),
        )
    }

    @Test
    fun `S5 TTS gate off preserves existing final text path`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertTrue(mapping.hasTtsCandidate)
        assertFalse(
            shouldPrepareNpuStandardRouteS5Tts(
                enabled = false,
                mapping = mapping,
            ),
        )
        assertEquals("こんにちは。", mapping.ttsCandidate?.speakText)
    }

    @Test
    fun `S5 TTS gate on prepares candidate without invoking TTS`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
            sanitizeForTts = { it },
        )

        assertTrue(
            shouldPrepareNpuStandardRouteS5Tts(
                enabled = true,
                mapping = mapping,
            ),
        )
        assertFalse(mapping.ttsCandidate?.sideEffects?.ttsInvoked ?: true)
        assertFalse(mapping.ttsCandidate?.sideEffects?.streaming ?: true)
        assertFalse(mapping.ttsCandidate?.sideEffects?.backendNpuPersisted ?: true)
    }

    @Test
    fun `S5 TTS speak gate requires candidate and assistant ownership`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertTrue(
            shouldSpeakNpuStandardRouteS5Tts(
                enabled = true,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
    }

    @Test
    fun `S5 TTS speak gate stays off when phase gate is off`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertFalse(
            shouldSpeakNpuStandardRouteS5Tts(
                enabled = false,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
    }

    @Test
    fun `S5 TTS speak gate rejects missing assistant id`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertFalse(
            shouldSpeakNpuStandardRouteS5Tts(
                enabled = true,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = null,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
    }

    @Test
    fun `S5 TTS speak gate rejects cooldown suppressed and streaming states`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertFalse(
            shouldSpeakNpuStandardRouteS5Tts(
                enabled = true,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = true,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
        assertFalse(
            shouldSpeakNpuStandardRouteS5Tts(
                enabled = true,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = true,
                inCooldown = false,
            ),
        )
        assertFalse(
            shouldSpeakNpuStandardRouteS5Tts(
                enabled = true,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = true,
            ),
        )
    }

    @Test
    fun `S5 TTS skip reason classifies gate and candidate failures`() {
        val candidateMapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )
        val emptySpeakMapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "。",
            ttsEnabled = true,
            sanitizeForTts = { "" },
        )

        assertEquals(
            NPU_STANDARD_ROUTE_S5_TTS_SKIP_GATE_OFF,
            classifyNpuStandardRouteS5TtsSkipReason(
                enabled = false,
                mapping = candidateMapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
        assertEquals(
            NPU_STANDARD_ROUTE_S5_TTS_SKIP_EMPTY_AFTER_SANITIZE,
            classifyNpuStandardRouteS5TtsSkipReason(
                enabled = true,
                mapping = emptySpeakMapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
    }

    @Test
    fun `S5 TTS skip reason classifies runtime ownership states`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertEquals(
            NPU_STANDARD_ROUTE_S5_TTS_SKIP_TTS_DISABLED,
            classifyNpuStandardRouteS5TtsSkipReason(
                enabled = true,
                mapping = mapping,
                ttsEnabled = false,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
        assertEquals(
            NPU_STANDARD_ROUTE_S5_TTS_SKIP_STREAMING_ACTIVE,
            classifyNpuStandardRouteS5TtsSkipReason(
                enabled = true,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = true,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
        assertEquals(
            NPU_STANDARD_ROUTE_S5_TTS_SKIP_ASSISTANT_ID_NULL,
            classifyNpuStandardRouteS5TtsSkipReason(
                enabled = true,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = null,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
        assertEquals(
            NPU_STANDARD_ROUTE_S5_TTS_SKIP_COOLDOWN,
            classifyNpuStandardRouteS5TtsSkipReason(
                enabled = true,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = true,
            ),
        )
        assertEquals(
            NPU_STANDARD_ROUTE_S5_TTS_SKIP_STOP_SUPPRESSED,
            classifyNpuStandardRouteS5TtsSkipReason(
                enabled = true,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = true,
                inCooldown = false,
            ),
        )
        assertEquals(
            NPU_STANDARD_ROUTE_S5_TTS_SKIP_NONE,
            classifyNpuStandardRouteS5TtsSkipReason(
                enabled = true,
                mapping = mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
    }

    @Test
    fun `S5 TTS trace builders include required fields`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        val candidateTrace = buildNpuStandardRouteS5TtsCandidateTrace(
            mapping = mapping,
            finalTextLength = 6,
            ttsEnabled = true,
            streamingActive = false,
            assistantId = 42,
        )
        val skipTrace = buildNpuStandardRouteS5TtsSkipTrace(
            reason = NPU_STANDARD_ROUTE_S5_TTS_SKIP_GATE_OFF,
            assistantId = null,
        )
        val speakTrace = buildNpuStandardRouteS5TtsSpeakTrace(
            stage = "before",
            assistantId = 42,
            speakTextLength = 6,
        )

        assertTrue(candidateTrace.contains("NPU_S5_TTS ttsCandidate_created=true"))
        assertTrue(candidateTrace.contains("speak_text_length=6"))
        assertTrue(candidateTrace.contains("backend_npu_persisted=false"))
        assertTrue(skipTrace.contains("tts_speak_invoked=false"))
        assertTrue(skipTrace.contains("tts_skipped_reason=gate_off"))
        assertTrue(speakTrace.contains("tts_speak_invoked=true"))
        assertTrue(speakTrace.contains("stage=before"))
    }

    @Test
    fun `S5 TTS gate on does not prepare candidate for failed S1 result`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = NpuStandardRouteS1Mapper.map(
                NpuStandardRouteS1RawResult(
                    status = "failure",
                    result = "failure",
                    success = false,
                    reason = "test_failure",
                    rawOutput = "",
                    sanitizedOutput = "",
                    qualityClassification = "unknown",
                    runDecodeReached = false,
                    npuBackendEvidence = "",
                    fallbackUsed = false,
                    timeout = false,
                    freshCrash = false,
                    requestedMaxOutputTokens = 32,
                    effectiveMaxOutputTokens = 32,
                ),
            ),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertFalse(
            shouldPrepareNpuStandardRouteS5Tts(
                enabled = true,
                mapping = mapping,
            ),
        )
    }

    @Test
    fun `S5 TTS gate on does not prepare candidate while streaming is active`() {
        val mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
            streamingActive = true,
        )

        assertFalse(mapping.hasTtsCandidate)
        assertNull(mapping.ttsCandidate)
        assertFalse(
            shouldPrepareNpuStandardRouteS5Tts(
                enabled = true,
                mapping = mapping,
            ),
        )
    }

    @Test
    fun `S2 through S5 phase gates follow NPU standard route mode`() {
        val s2Mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "こんにちは",
            s1Result = s1SuccessResult(),
        )
        val s4Mapping = NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
            s1Result = s1SuccessResult(),
            finalText = "こんにちは。今日はNPU応答を段階表示します。",
        )
        val s5Mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。",
            ttsEnabled = true,
        )

        assertFalse(shouldPersistNpuStandardRouteS2Db(NpuStandardRouteMode.S1_ONLY.isS2Enabled(), s2Mapping))
        assertTrue(shouldPersistNpuStandardRouteS2Db(NpuStandardRouteMode.S2_DB.isS2Enabled(), s2Mapping))
        assertFalse(shouldStartNpuStandardRouteS4APseudoStreaming(NpuStandardRouteMode.S3_MARKDOWN.isS4AEnabled(), s4Mapping))
        assertTrue(shouldStartNpuStandardRouteS4APseudoStreaming(NpuStandardRouteMode.S4A_PSEUDO_STREAMING.isS4AEnabled(), s4Mapping))
        assertFalse(shouldPrepareNpuStandardRouteS5Tts(NpuStandardRouteMode.S4A_PSEUDO_STREAMING.isS5Enabled(), s5Mapping))
        assertTrue(shouldPrepareNpuStandardRouteS5Tts(NpuStandardRouteMode.FULL.isS5Enabled(), s5Mapping))
    }

    @Test
    fun `NPU immediate send update clears composer state`() {
        val update = prepareImmediateNpuSendUiStateUpdate("こんにちは")

        assertEquals("", update.prompt)
        assertEquals("", update.userPrompt)
        assertTrue(update.selectedImageUriStrings.isEmpty())
    }

    @Test
    fun `NPU off Generic GPU enters normal local LiteRT route diagnostics`() {
        val shouldEnterNpuS1 = shouldEnterNpuStandardRouteS1(
            enabled = false,
            selectedInferenceTarget = InferenceTarget.LOCAL,
            hasImageInput = false,
            requestPrompt = "こんにちは",
        )
        val shouldEnterLocalRoute = shouldEnterLocalLiteRtRouteAfterNpuS1Decision(
            shouldEnterNpuS1 = shouldEnterNpuS1,
            selectedInferenceTarget = InferenceTarget.LOCAL,
            hasImageInput = false,
            requestPrompt = "こんにちは",
        )
        val diagnosticContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = shouldEnterNpuS1,
            localRouteEntered = shouldEnterLocalRoute,
        )
        val trace = buildLocalRouteDiagnosticTrace(
            stage = "local_route_entered",
            context = diagnosticContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = false,
                heldEngineReused = false,
                engineCreateStarted = true,
                engineCreateFinished = false,
                failureStage = "none",
            ),
            elapsedMs = 12,
        )

        assertFalse(shouldEnterNpuS1)
        assertTrue(shouldEnterLocalRoute)
        assertTrue(trace.contains("selected_model_name=gemma-4-E2B-it"))
        assertTrue(trace.contains("selected_model_file=gemma-4-E2B-it.litertlm"))
        assertTrue(trace.contains("model_kind=generic-litertlm"))
        assertTrue(trace.contains("preferred_backend=GPU"))
        assertTrue(trace.contains("baseline_role=gpu_experimental"))
        assertTrue(trace.contains("generic_model_cpu_baseline=false"))
        assertTrue(trace.contains("npu_standard_route_mode=OFF"))
        assertTrue(trace.contains("should_enter_npu_s1=false"))
        assertTrue(trace.contains("local_route_entered=true"))
        assertTrue(trace.contains("held_engine_exists=false"))
        assertTrue(trace.contains("held_engine_reused=false"))
        assertTrue(trace.contains("engine_create_started=true"))
        assertTrue(trace.contains("engine_create_finished=false"))
        assertTrue(trace.contains("conversation_create_started=unknown"))
        assertTrue(trace.contains("failure_stage=none"))
        assertTrue(trace.contains("elapsed_ms=12"))
    }

    @Test
    fun `Generic GPU local route enables experimental stage timeout diagnostics`() {
        val diagnosticContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val trace = buildLocalRouteDiagnosticTrace(
            stage = "timeout_failure",
            context = diagnosticContext,
            flags = LocalRouteDiagnosticFlags(
                heldEngineExists = false,
                heldEngineReused = false,
                engineCreateStarted = true,
                engineCreateFinished = false,
                conversationCreateStarted = false,
                conversationCreateFinished = false,
                generateStarted = false,
                firstTokenReceived = false,
                failureStage = "engine_create_timeout",
                fallbackUsed = false,
                staleCallbackIgnored = false,
            ),
            elapsedMs = GPU_EXPERIMENTAL_STAGE_TIMEOUT_MS,
        )

        assertTrue(shouldApplyGpuExperimentalStageTimeout(diagnosticContext))
        assertTrue(trace.contains("preferred_backend=GPU"))
        assertTrue(trace.contains("baseline_role=gpu_experimental"))
        assertTrue(trace.contains("generic_model_cpu_baseline=false"))
        assertTrue(trace.contains("local_route_entered=true"))
        assertTrue(trace.contains("failure_stage=engine_create_timeout"))
        assertTrue(trace.contains("fallback_used=false"))
        assertTrue(trace.contains("stale_callback_ignored=false"))
        assertTrue(trace.contains("elapsed_ms=20000"))
        assertEquals(
            "GPU backend の初期化または生成開始がタイムアウトしました。Generic LiteRT-LMモデルではCPU backendを選択してください。",
            GPU_EXPERIMENTAL_TIMEOUT_MESSAGE,
        )
    }

    @Test
    fun `Generic GPU watchdog timeout diagnostics mark stale callback ignored`() {
        val diagnosticContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "GPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )
        val trace = buildLocalRouteDiagnosticTrace(
            stage = "timeout_failure",
            context = diagnosticContext,
            flags = LocalRouteDiagnosticFlags(
                failureStage = "gpu_watchdog_timeout",
                fallbackUsed = false,
                staleCallbackIgnored = true,
            ),
            elapsedMs = GPU_EXPERIMENTAL_STAGE_TIMEOUT_MS,
        )

        assertTrue(trace.contains("preferred_backend=GPU"))
        assertTrue(trace.contains("baseline_role=gpu_experimental"))
        assertTrue(trace.contains("failure_stage=gpu_watchdog_timeout"))
        assertTrue(trace.contains("fallback_used=false"))
        assertTrue(trace.contains("stale_callback_ignored=true"))
        assertTrue(trace.contains("elapsed_ms=20000"))
    }

    @Test
    fun `Generic CPU local route does not enable GPU experimental timeout`() {
        val diagnosticContext = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            preferredBackend = "CPU",
            npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
            shouldEnterNpuS1 = false,
            localRouteEntered = true,
        )

        assertFalse(shouldApplyGpuExperimentalStageTimeout(diagnosticContext))
        assertEquals("cpu_stable_baseline", diagnosticContext.baselineRole)
        assertTrue(diagnosticContext.genericModelCpuBaseline)
    }

    @Test
    fun `GPU experimental timeout failure stage follows last reached stage`() {
        assertEquals(
            "engine_create_timeout",
            resolveGpuExperimentalTimeoutFailureStage("engine_create_started"),
        )
        assertEquals(
            "conversation_create_timeout",
            resolveGpuExperimentalTimeoutFailureStage("conversation_create_started"),
        )
        assertEquals(
            "generate_start_timeout",
            resolveGpuExperimentalTimeoutFailureStage("conversation_create_finished"),
        )
        assertEquals(
            "first_token_timeout",
            resolveGpuExperimentalTimeoutFailureStage("generate_started"),
        )
    }

    @Test
    fun `Generic LiteRT-LM model is blocked before NPU S1 decode`() = runTest {
        val eligibility = resolveNpuStandardRouteS1ModelEligibility(
            selectedModelName = "gemma-4-E2B-it",
            selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
        )
        val events = mutableListOf<String>()

        val run = runNpuInferenceAfterImmediateUserMessage(
            requestPrompt = "こんにちは",
            currentChatId = 7,
            createChat = {
                events += "create_chat"
                7
            },
            onChatCreated = { chatId ->
                events += "chat_created:$chatId"
            },
            insertUserMessage = { chatId, promptText ->
                events += "insert_user:$chatId:$promptText"
            },
            runInference = {
                if (eligibility.npuModelEligible) {
                    events += "run_decode"
                    s1SuccessResult()
                } else {
                    events += "blocked_before_decode"
                    buildNpuStandardRouteS1ModelNotCompatibleResult(
                        eligibility = eligibility,
                        maxOutputTokens = 128,
                    )
                }
            },
        )

        assertEquals(listOf("insert_user:7:こんにちは", "blocked_before_decode"), events)
        assertFalse(eligibility.npuModelEligible)
        assertEquals("gemma-4-E2B-it", run.result.selectedModelName)
        assertEquals("gemma-4-E2B-it.litertlm", run.result.selectedModelFile)
        assertEquals(false, run.result.npuModelEligible)
        assertEquals(NpuStandardRouteS1Contract.REASON_MODEL_NOT_NPU_COMPATIBLE, run.result.reason)
        assertFalse(run.result.runDecodeReached)
        assertFalse(run.result.fallbackUsed)
        assertFalse(run.result.successCriteriaMet)
    }

    @Test
    fun `Generic LiteRT-LM model failure uses explicit compatibility diagnostics`() {
        val result = buildNpuStandardRouteS1ModelNotCompatibleResult(
            eligibility = resolveNpuStandardRouteS1ModelEligibility(
                selectedModelName = "gemma-4-E2B-it",
                selectedModelFile = "/models/gemma-4-E2B-it.litertlm",
            ),
            maxOutputTokens = 512,
        )
        val trace = buildNpuStandardRouteS1DevTraceText(
            input = "こんにちは",
            result = result,
            maxOutputTokens = 512,
        )
        val resultTrace = buildNpuRealPromptResultTrace(
            status = result.status,
            reason = result.reason,
            maxOutputTokens = result.selection.effectiveMaxOutputTokens,
            rawOutput = result.rawOutput,
            sanitizedOutput = result.sanitizedOutput,
            qualityClassification = result.qualityClassification,
            runDecodeReached = result.runDecodeReached,
            fallbackUsed = result.fallbackUsed,
            timeout = result.timeout,
            freshCrash = result.freshCrash,
            selectedModelName = result.selectedModelName,
            selectedModelFile = result.selectedModelFile,
            npuModelEligible = result.npuModelEligible,
        )

        assertEquals("failure", result.status)
        assertEquals(NpuStandardRouteS1Contract.REASON_MODEL_NOT_NPU_COMPATIBLE, result.reason)
        assertEquals("", result.rawOutput)
        assertEquals("", result.sanitizedOutput)
        assertTrue(result.displayText.contains("selected_model_name=gemma-4-E2B-it"))
        assertTrue(result.displayText.contains("selected_model_file=gemma-4-E2B-it.litertlm"))
        assertTrue(result.displayText.contains("npu_model_eligible=false"))
        assertTrue(result.displayText.contains("run_decode_reached=false"))
        assertTrue(result.displayText.contains("fallback_used=false"))
        assertTrue(trace.contains("raw_output_preview=n/a"))
        assertTrue(trace.contains("sanitized_output_preview=n/a"))
        assertTrue(trace.contains("fallback=false"))
        assertTrue(resultTrace.contains("raw_output_preview=n/a"))
        assertTrue(resultTrace.contains("sanitized_output_preview=n/a"))
        assertEquals(
            NpuStandardRouteS1Contract.MODEL_NOT_NPU_COMPATIBLE_MESSAGE,
            resolveNpuStandardRouteFailureAssistantMessage(
                result = result,
                transientFallback = null,
            ),
        )
    }

    @Test
    fun `Qualcomm sm8750 qnn or npu model names are eligible for NPU S1 decode`() {
        listOf(
            "gemma-4-E2B-it-qualcomm.litertlm",
            "gemma-4-E2B-it-sm8750.litertlm",
            "gemma-4-E2B-it-qnn.litertlm",
            "gemma-4-E2B-it-npu.litertlm",
        ).forEach { modelFile ->
            val eligibility = resolveNpuStandardRouteS1ModelEligibility(
                selectedModelName = "gemma-4-E2B-it",
                selectedModelFile = "/models/$modelFile",
            )

            assertTrue("$modelFile should be eligible", eligibility.npuModelEligible)
        }
    }

    @Test
    fun `NPU on Qualcomm sm8750 model enters S1 route`() {
        val shouldEnterNpuS1 = shouldEnterNpuStandardRouteS1(
            enabled = true,
            selectedInferenceTarget = InferenceTarget.LOCAL,
            hasImageInput = false,
            requestPrompt = "こんにちは",
        )
        val eligibility = resolveNpuStandardRouteS1ModelEligibility(
            selectedModelName = "gemma-4-E2B-it-sm8750",
            selectedModelFile = "/models/gemma-4-E2B-it-sm8750-qualcomm.litertlm",
        )

        assertTrue(shouldEnterNpuS1)
        assertTrue(eligibility.npuModelEligible)
        assertFalse(
            shouldEnterLocalLiteRtRouteAfterNpuS1Decision(
                shouldEnterNpuS1 = shouldEnterNpuS1,
                selectedInferenceTarget = InferenceTarget.LOCAL,
                hasImageInput = false,
                requestPrompt = "こんにちは",
            ),
        )
    }

    @Test
    fun `NPU inference starts after immediate user message persistence`() = runTest {
        val events = mutableListOf<String>()

        val run = runNpuInferenceAfterImmediateUserMessage(
            requestPrompt = "こんにちは",
            currentChatId = null,
            createChat = {
                events += "create_chat"
                7
            },
            onChatCreated = { chatId ->
                events += "chat_created:$chatId"
            },
            insertUserMessage = { chatId, promptText ->
                events += "insert_user:$chatId:$promptText"
            },
            runInference = { chatId ->
                events += "run_npu:$chatId"
                "assistant"
            },
        )

        assertEquals(7, run.chatId)
        assertEquals("assistant", run.result)
        assertEquals(
            listOf(
                "create_chat",
                "chat_created:7",
                "insert_user:7:こんにちは",
                "run_npu:7",
            ),
            events,
        )
    }

    @Test
    fun `NPU failure assistant message uses fallback or explicit error`() {
        val fallback = NpuStandardRouteS1TransientFallback(
            text = "すみません、応答を生成できませんでした。",
            kind = "generic_failure_fallback",
        )

        assertEquals(
            "すみません、応答を生成できませんでした。",
            resolveNpuStandardRouteFailureAssistantMessage(
                result = s1QuestionEchoFailureResult(),
                transientFallback = fallback,
            ),
        )
        assertEquals(
            "NPU推論の応答生成に失敗しました: assistant_stub",
            resolveNpuStandardRouteFailureAssistantMessage(
                result = s1AssistantStubFailureResult(),
                transientFallback = null,
            ),
        )
        assertNull(
            resolveNpuStandardRouteFailureAssistantMessage(
                result = s1SuccessResult(),
                transientFallback = null,
            ),
        )
    }

    private fun s1SuccessResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = "natural_japanese",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
            ),
        )

    private fun s1EmptyAfterSanitizeFailureResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "failure",
                result = "failure",
                success = false,
                reason = "empty_after_sanitize",
                rawOutput = "૩です|",
                sanitizedOutput = "",
                qualityClassification = "mixed_language",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
            ),
        )

    private fun s1MixedLanguageFailureResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "failure",
                result = "failure",
                success = false,
                reason = "mixed_language",
                rawOutput = "૩です|",
                sanitizedOutput = "",
                qualityClassification = "mixed_language",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
            ),
        )

    private fun s1QuestionEchoFailureResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "failure",
                result = "failure",
                success = false,
                reason = "question_echo",
                rawOutput = "明日の天気は\nユーザー: どんな感じですか",
                sanitizedOutput = "明日の天気は",
                qualityClassification = "question_echo",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
            ),
        )

    private fun s1AssistantStubFailureResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "failure",
                result = "failure",
                success = false,
                reason = "assistant_stub",
                rawOutput = "アシスタント。\nユーザー: おやす",
                sanitizedOutput = "アシスタント。",
                qualityClassification = "assistant_stub",
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
            ),
        )
}
