package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.InferenceTarget
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import io.github.ninbyo02.lami.ui.screens.settings.effectiveNpuStandardRouteModeForBackendSelection
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
    fun `CPU and GPU backend selections block stale NPU standard route mode for normal chat`() {
        listOf(PreferredBackendDryRunSetting.CPU, PreferredBackendDryRunSetting.GPU).forEach { backend ->
            val effectiveMode = effectiveNpuStandardRouteModeForBackendSelection(
                preferredBackend = backend,
                npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY,
            )
            val rawNpuRequested = NpuStandardRouteS1GateConfig.isEnabledForMode(NpuStandardRouteMode.S1_ONLY)
            val effectiveNpuRequested = NpuStandardRouteS1GateConfig.isEnabledForMode(effectiveMode)
            val shouldEnterNpuS1 = shouldEnterNpuStandardRouteS1(
                enabled = effectiveNpuRequested,
                selectedInferenceTarget = InferenceTarget.LOCAL,
                hasImageInput = false,
                requestPrompt = "こんにちは",
            )
            val trace = buildLocalRouteDiagnosticTrace(
                stage = "route_decision",
                context = buildLocalRouteDiagnosticContext(
                    selectedModelName = "gemma",
                    selectedModelFile = "/models/gemma.litertlm",
                    preferredBackend = backend.name,
                    npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
                    effectiveNpuStandardRouteMode = effectiveMode.name,
                    shouldEnterNpuS1 = shouldEnterNpuS1,
                    localRouteEntered = true,
                    normalChatNativeRouteBlocked = rawNpuRequested && !effectiveNpuRequested,
                    blockedReason = NPU_S1_REPEATED_RUN_BLOCKED_SELECTED_BACKEND_NOT_NPU,
                ),
            )

            assertEquals(NpuStandardRouteMode.OFF, effectiveMode)
            assertFalse(shouldEnterNpuS1)
            assertTrue(trace.contains("normal_chat_native_route_blocked=true"))
            assertTrue(trace.contains("blocked_reason=selected_backend_not_npu"))
        }
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
    fun `S4A tts false blocks streaming sentence TTS and explicit S5 speak`() {
        val s4Result = buildNpuStandardRouteS4APseudoStreamingSavedResult(
            s1Result = s1SuccessResult(),
            finalText = "こんにちは。今日は段階表示します。",
        )
        val s5Mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。今日は段階表示します。",
            ttsEnabled = true,
        )

        assertTrue(s4Result.displayText.contains("route_type=standard_chat_screen_s4a_npu_pseudo_streaming"))
        assertTrue(s4Result.displayText.contains("tts=false"))
        assertFalse(
            shouldEnableStreamingSentenceTts(
                ttsEnabled = true,
                devEnableStreamingSentenceTts = true,
                blockedByNpuStandardRoute = true,
            ),
        )
        assertFalse(
            shouldSpeakNpuStandardRouteS5Tts(
                enabled = NpuStandardRouteMode.S4A_PSEUDO_STREAMING.isS5Enabled(),
                mapping = s5Mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
    }

    @Test
    fun `streaming sentence TTS remains enabled for non NPU standard routes`() {
        assertTrue(
            shouldEnableStreamingSentenceTts(
                ttsEnabled = true,
                devEnableStreamingSentenceTts = true,
                blockedByNpuStandardRoute = false,
            ),
        )
        assertFalse(
            shouldEnableStreamingSentenceTts(
                ttsEnabled = false,
                devEnableStreamingSentenceTts = true,
                blockedByNpuStandardRoute = false,
            ),
        )
        assertFalse(
            shouldEnableStreamingSentenceTts(
                ttsEnabled = true,
                devEnableStreamingSentenceTts = false,
                blockedByNpuStandardRoute = false,
            ),
        )
    }

    @Test
    fun `FULL mode tts true matches explicit S5 speak gate`() {
        val s5Result = buildNpuStandardRouteS5TtsSavedResult(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。読み上げます。",
        )
        val s5Mapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。読み上げます。",
            ttsEnabled = true,
        )

        assertTrue(s5Result.displayText.contains("route_type=standard_chat_screen_s5_npu_tts"))
        assertTrue(s5Result.displayText.contains("db=true"))
        assertTrue(s5Result.displayText.contains("conversation_history_saved=true"))
        assertTrue(s5Result.displayText.contains("markdown=true"))
        assertTrue(s5Result.displayText.contains("streaming=true"))
        assertTrue(s5Result.displayText.contains("tts=true"))
        assertTrue(s5Result.displayText.contains("s5_tts_reason=success"))
        assertTrue(s5Result.displayText.contains("tts_requested=true"))
        assertTrue(s5Result.displayText.contains("tts_started=true"))
        assertTrue(s5Result.displayText.contains("tts_completed=true"))
        assertTrue(s5Result.displayText.contains("tts_skipped=false"))
        assertTrue(s5Result.displayText.contains("tts_text_length=${"こんにちは。読み上げます。".length}"))
        assertTrue(s5Result.displayText.contains("tts_input_source=sanitized_output"))
        assertTrue(
            shouldSpeakNpuStandardRouteS5Tts(
                enabled = NpuStandardRouteMode.FULL.isS5Enabled(),
                mapping = s5Mapping,
                ttsEnabled = true,
                streamingActive = false,
                assistantId = 42,
                suppressedForAssistant = false,
                inCooldown = false,
            ),
        )
    }

    @Test
    fun `NPU standard route dev diagnostics are collapsed by default`() {
        val s5Result = buildNpuStandardRouteS5TtsSavedResult(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。読み上げます。",
        )

        assertTrue(hasNpuStandardRouteDevDiagnostics(s5Result.displayText))
        assertEquals("▶ DEV診断を表示", npuStandardRouteDevDiagnosticsToggleLabel(expanded = false))
        assertFalse(shouldShowNpuStandardRouteDevDiagnosticsContent(expanded = false))
        assertTrue(s5Result.displayText.contains("route_type=standard_chat_screen_s5_npu_tts"))
        assertTrue(s5Result.displayText.contains("tts=true"))
    }

    @Test
    fun `NPU standard route dev diagnostics show existing text when expanded`() {
        val s5Result = buildNpuStandardRouteS5TtsSavedResult(
            s1Result = s1SuccessResult(),
            finalAssistantText = "こんにちは。読み上げます。",
        )

        assertEquals("▼ DEV診断を隠す", npuStandardRouteDevDiagnosticsToggleLabel(expanded = true))
        assertTrue(shouldShowNpuStandardRouteDevDiagnosticsContent(expanded = true))
        assertTrue(hasNpuStandardRouteDevDiagnostics(s5Result.displayText, "input=こんにちは"))
        assertTrue(s5Result.displayText.contains("db=true"))
        assertTrue(s5Result.displayText.contains("markdown=true"))
        assertTrue(s5Result.displayText.contains("streaming=true"))
    }

    @Test
    fun `NPU standard route dev diagnostics are absent for normal route without diagnostics`() {
        assertFalse(hasNpuStandardRouteDevDiagnostics(null, "", "   "))
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
        val roleContaminationMapping = NpuStandardRouteS5TtsMapping(
            ttsCandidate = null,
            failureReason = NpuStandardRouteS5TtsContract.FAILURE_ROLE_CONTAMINATION,
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
        assertEquals(
            NPU_STANDARD_ROUTE_S5_TTS_SKIP_ROLE_CONTAMINATION,
            classifyNpuStandardRouteS5TtsSkipReason(
                enabled = true,
                mapping = roleContaminationMapping,
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
    fun `S1 mode remains display only with DB disconnected`() {
        val result = s1SuccessResult().withTiming(NpuStandardRouteS1Timing(totalMs = 1))

        assertEquals(NpuStandardRouteS1Contract.ROUTE_TYPE, result.selection.routeType)
        assertTrue(result.displayText.contains("route_type=standard_chat_screen_s1_npu_display_only"))
        assertTrue(result.displayText.contains("db=false"))
        assertTrue(result.displayText.contains("conversation_history_saved=false"))
    }

    @Test
    fun `S2 saved result marks DB and conversation history connected`() {
        val s2Result = buildNpuStandardRouteS2DbSavedResult(
            s1SuccessResult().withTiming(
                NpuStandardRouteS1Timing(
                    totalMs = 1200,
                    decodeMs = 1000,
                    outputTokens = 20,
                    tokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS,
                    tokensPerSecond = 20.0,
                ),
            ),
        )

        assertEquals(NpuStandardRouteS1Contract.ROUTE_TYPE_S2_DB_SAVE, s2Result.selection.routeType)
        assertTrue(s2Result.displayText.contains("route_type=standard_chat_screen_s2_npu_db_save"))
        assertTrue(s2Result.displayText.contains("db=true"))
        assertTrue(s2Result.displayText.contains("conversation_history_saved=true"))
        assertTrue(s2Result.displayText.contains("tts=false"))
        assertTrue(s2Result.displayText.contains("markdown=false"))
        assertTrue(s2Result.displayText.contains("streaming=false"))
        assertTrue(s2Result.displayText.contains("npu_s1_total_ms=1200"))
        assertTrue(s2Result.displayText.contains("npu_s1_decode_ms=1000"))
        assertTrue(s2Result.displayText.contains("npu_s1_tokens_per_second=20.0"))
        assertTrue(s2Result.displayText.contains("fallback_used=false"))
        assertTrue(s2Result.displayText.contains("fresh_crash=false"))
        assertTrue(s2Result.displayText.contains("timeout=false"))
    }

    @Test
    fun `S2 skipped result keeps DB disconnected and explains reason`() {
        val failureResult = s1EmptyAfterSanitizeFailureResult()
        val mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "こんにちは",
            s1Result = failureResult,
        )
        val s2Result = buildNpuStandardRouteS2DbSkippedResult(
            s1Result = failureResult,
            failureReason = mapping.failureReason,
        )

        assertFalse(mapping.hasSaveCandidate)
        assertTrue(s2Result.displayText.contains("route_type=standard_chat_screen_s2_npu_db_save"))
        assertTrue(s2Result.displayText.contains("db=false"))
        assertTrue(s2Result.displayText.contains("conversation_history_saved=false"))
        assertTrue(s2Result.displayText.contains("s2_db_reason=s1_success_criteria_not_met"))
    }

    @Test
    fun `S2 skipped result reports raw role contamination reason`() {
        val contaminated = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                result = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                success = true,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = "どうしましたか。\nユーザー: ああああ\nアシスタント: 何か困っていますか。",
                sanitizedOutput = "どうしましたか。",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "こんにちは",
            s1Result = contaminated,
        )
        val s2Result = buildNpuStandardRouteS2DbSkippedResult(
            s1Result = contaminated,
            failureReason = mapping.failureReason,
        )

        assertFalse(mapping.hasSaveCandidate)
        assertTrue(s2Result.displayText.contains("db=false"))
        assertTrue(s2Result.displayText.contains("conversation_history_saved=false"))
        assertTrue(s2Result.displayText.contains("reason=raw_role_contamination"))
        assertTrue(s2Result.displayText.contains("quality_classification=role_contamination"))
        assertTrue(s2Result.displayText.contains("s2_db_reason=raw_role_contamination"))
    }

    @Test
    fun `S3 saved result marks DB markdown and conversation history connected`() {
        val s3Result = buildNpuStandardRouteS3MarkdownSavedResult(
            s1Result = s1SuccessResult().withTiming(
                NpuStandardRouteS1Timing(
                    totalMs = 1200,
                    decodeMs = 1000,
                    outputTokens = 20,
                    tokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS,
                    tokensPerSecond = 20.0,
                ),
            ),
            finalizedText = "こんにちは。\n- Markdown",
        )

        assertEquals(NpuStandardRouteS1Contract.ROUTE_TYPE_S3_MARKDOWN, s3Result.selection.routeType)
        assertTrue(s3Result.displayText.contains("route_type=standard_chat_screen_s3_markdown"))
        assertTrue(s3Result.displayText.contains("db=true"))
        assertTrue(s3Result.displayText.contains("conversation_history_saved=true"))
        assertTrue(s3Result.displayText.contains("markdown=true"))
        assertTrue(s3Result.displayText.contains("streaming=false"))
        assertTrue(s3Result.displayText.contains("tts=false"))
        assertTrue(s3Result.displayText.contains("npu_s1_total_ms=1200"))
        assertTrue(s3Result.displayText.contains("npu_s1_decode_ms=1000"))
        assertTrue(s3Result.displayText.contains("npu_s1_tokens_per_second=20.0"))
        assertTrue(s3Result.displayText.contains("fallback_used=false"))
        assertTrue(s3Result.displayText.contains("fresh_crash=false"))
        assertTrue(s3Result.displayText.contains("timeout=false"))
    }

    @Test
    fun `S2 successful flow saves one user message and one assistant message`() = runTest {
        val events = mutableListOf<String>()
        val run = runNpuInferenceAfterImmediateUserMessage(
            requestPrompt = "こんにちは",
            currentChatId = 42,
            createChat = {
                events += "create_chat"
                42
            },
            onChatCreated = { chatId ->
                events += "chat_created:$chatId"
            },
            insertUserMessage = { chatId, promptText ->
                events += "insert_user:$chatId:$promptText"
            },
            runInference = {
                s1SuccessResult()
            },
        )
        val mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "こんにちは",
            s1Result = run.result,
        )
        if (shouldPersistNpuStandardRouteS2Db(enabled = true, mapping = mapping)) {
            events += "insert_assistant:${run.chatId}:${requireNotNull(mapping.saveCandidate).assistantMessage.text}"
        }

        assertEquals(1, events.count { it.startsWith("insert_user:") })
        assertEquals(1, events.count { it.startsWith("insert_assistant:") })
        assertFalse(events.contains("create_chat"))
    }

    @Test
    fun `S2 arithmetic tail leak flow saves actual display text as assistant message`() = runTest {
        val events = mutableListOf<String>()
        val run = runNpuInferenceAfterImmediateUserMessage(
            requestPrompt = "1+1は？",
            currentChatId = 42,
            createChat = {
                events += "create_chat"
                42
            },
            onChatCreated = { chatId ->
                events += "chat_created:$chatId"
            },
            insertUserMessage = { chatId, promptText ->
                events += "insert_user:$chatId:$promptText"
            },
            runInference = {
                s1SafeArithmeticTailLeakResult()
            },
        )
        val mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "1+1は？",
            s1Result = run.result,
        )
        if (shouldPersistNpuStandardRouteS2Db(enabled = true, mapping = mapping)) {
            events += "insert_assistant:${run.chatId}:${requireNotNull(mapping.saveCandidate).assistantMessage.text}"
        }

        assertEquals("2", run.result.actualDisplayText)
        assertEquals("2です。", run.result.ttsText)
        val ttsMapping = NpuStandardRouteS5TtsBridge().prepareTtsCandidate(
            s1Result = run.result,
            finalAssistantText = run.result.ttsText,
            ttsEnabled = true,
        )
        assertEquals("2です。", requireNotNull(ttsMapping.ttsCandidate).speakText)
        assertEquals(1, events.count { it.startsWith("insert_user:") })
        assertTrue(events.contains("insert_assistant:42:2"))
        assertFalse(events.any { it.contains("<start_of_turn>") })
        assertFalse(events.any { it.contains("次の計算") })
    }

    @Test
    fun `S3 successful flow saves finalized markdown text with S3 diagnostics`() = runTest {
        val events = mutableListOf<String>()
        val run = runNpuInferenceAfterImmediateUserMessage(
            requestPrompt = "Markdownで答えて",
            currentChatId = 42,
            createChat = {
                events += "create_chat"
                42
            },
            onChatCreated = { chatId ->
                events += "chat_created:$chatId"
            },
            insertUserMessage = { chatId, promptText ->
                events += "insert_user:$chatId:$promptText"
            },
            runInference = {
                s1SuccessResult(sanitizedOutput = "見出し\\n- 項目")
            },
        )
        val s2Mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "Markdownで答えて",
            s1Result = run.result,
        )
        val s3Mapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
            s1Result = run.result,
            finalizeMarkdown = { it.replace("\\n", "\n") },
        )
        if (shouldPersistNpuStandardRouteS2Db(enabled = true, mapping = s2Mapping)) {
            val saveCandidate = requireNotNull(s2Mapping.saveCandidate)
            val s2Result = buildNpuStandardRouteS2DbSavedResult(run.result)
            val s3Candidate = s3Mapping
                .takeIf { shouldRenderNpuStandardRouteS3Markdown(enabled = true, mapping = it) }
                ?.markdownCandidate
            val assistantText = s3Candidate?.finalizedText ?: saveCandidate.assistantMessage.text
            val routeResult = if (s3Candidate != null) {
                buildNpuStandardRouteS3MarkdownSavedResult(
                    s1Result = run.result,
                    finalizedText = s3Candidate.finalizedText,
                )
            } else {
                s2Result
            }
            events += "insert_assistant:${run.chatId}:$assistantText"
            events += "source:${routeResult.displayText}"
        }

        assertEquals(1, events.count { it.startsWith("insert_user:") })
        assertTrue(events.contains("insert_assistant:42:見出し\n- 項目"))
        val source = requireNotNull(events.firstOrNull { it.startsWith("source:") })
        assertTrue(source.contains("route_type=standard_chat_screen_s3_markdown"))
        assertTrue(source.contains("db=true"))
        assertTrue(source.contains("conversation_history_saved=true"))
        assertTrue(source.contains("markdown=true"))
        assertTrue(source.contains("streaming=false"))
        assertTrue(source.contains("tts=false"))
    }

    @Test
    fun `S4A successful flow streams chunks and saves one finalized assistant message`() = runTest {
        val events = mutableListOf<String>()
        val displayedChunks = mutableListOf<String>()
        val run = runNpuInferenceAfterImmediateUserMessage(
            requestPrompt = "箇条書きで教えて",
            currentChatId = 42,
            createChat = {
                events += "create_chat"
                42
            },
            onChatCreated = { chatId ->
                events += "chat_created:$chatId"
            },
            insertUserMessage = { chatId, promptText ->
                events += "insert_user:$chatId:$promptText"
            },
            runInference = {
                s1SuccessResult(sanitizedOutput = "- 箇条書きの項目1\\n- 箇条書きの項目2\\n- 箇条書きの項目3")
            },
        )
        val s2Mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "箇条書きで教えて",
            s1Result = run.result,
        )
        val s3Mapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
            s1Result = run.result,
            finalizeMarkdown = { it.replace("\\n", "\n") },
        )
        if (shouldPersistNpuStandardRouteS2Db(enabled = true, mapping = s2Mapping)) {
            val saveCandidate = requireNotNull(s2Mapping.saveCandidate)
            val s3Candidate = s3Mapping
                .takeIf { shouldRenderNpuStandardRouteS3Markdown(enabled = true, mapping = it) }
                ?.markdownCandidate
            val s3Result = if (s3Candidate != null) {
                buildNpuStandardRouteS3MarkdownSavedResult(
                    s1Result = run.result,
                    finalizedText = s3Candidate.finalizedText,
                )
            } else {
                buildNpuStandardRouteS2DbSavedResult(run.result)
            }
            val s4Mapping = s3Candidate?.let {
                NpuStandardRouteS4PseudoStreamingBridge().preparePseudoStreamingCandidate(
                    s1Result = run.result,
                    finalText = it.finalizedText,
                    sourceDisplayText = s3Result.displayText,
                )
            }
            val s4Candidate = s4Mapping
                ?.takeIf { shouldStartNpuStandardRouteS4APseudoStreaming(enabled = true, mapping = it) }
                ?.pseudoStreamingCandidate
            val assistantText = s4Candidate?.finalText ?: s3Candidate?.finalizedText ?: saveCandidate.assistantMessage.text
            val routeResult = if (s4Candidate != null) {
                displayedChunks += s4Candidate.chunks
                buildNpuStandardRouteS4APseudoStreamingSavedResult(
                    s1Result = run.result,
                    finalText = s4Candidate.finalText,
                )
            } else {
                s3Result
            }
            events += "insert_assistant:${run.chatId}:$assistantText"
            events += "source:${routeResult.displayText}"
        }

        val expectedFinalText = "- 箇条書きの項目1\n- 箇条書きの項目2\n- 箇条書きの項目3"
        assertEquals(1, events.count { it.startsWith("insert_user:") })
        assertEquals(1, events.count { it.startsWith("insert_assistant:") })
        assertTrue(events.contains("insert_assistant:42:$expectedFinalText"))
        assertEquals(expectedFinalText, displayedChunks.last())
        assertTrue(displayedChunks.zipWithNext().all { (previous, next) -> next.startsWith(previous) })
        assertTrue(displayedChunks.last().contains("\n- 箇条書きの項目2\n- 箇条書きの項目3"))
        val source = requireNotNull(events.firstOrNull { it.startsWith("source:") })
        assertTrue(source.contains("route_type=standard_chat_screen_s4a_npu_pseudo_streaming"))
        assertTrue(source.contains("db=true"))
        assertTrue(source.contains("conversation_history_saved=true"))
        assertTrue(source.contains("markdown=true"))
        assertTrue(source.contains("streaming=true"))
        assertTrue(source.contains("tts=false"))
    }

    @Test
    fun `S4A stop guard blocks old chunk appends`() {
        assertTrue(
            shouldContinueNpuStandardRouteS4APseudoStreaming(
                localStopRequested = false,
                runGuardEpoch = 7L,
                currentGuardEpoch = 7L,
                expectedChatId = 42,
                currentChatId = 42,
            ),
        )
        assertFalse(
            shouldContinueNpuStandardRouteS4APseudoStreaming(
                localStopRequested = true,
                runGuardEpoch = 7L,
                currentGuardEpoch = 7L,
                expectedChatId = 42,
                currentChatId = 42,
            ),
        )
        assertFalse(
            shouldContinueNpuStandardRouteS4APseudoStreaming(
                localStopRequested = false,
                runGuardEpoch = 7L,
                currentGuardEpoch = 8L,
                expectedChatId = 42,
                currentChatId = 42,
            ),
        )
        assertFalse(
            shouldContinueNpuStandardRouteS4APseudoStreaming(
                localStopRequested = false,
                runGuardEpoch = 7L,
                currentGuardEpoch = 7L,
                expectedChatId = 42,
                currentChatId = 43,
            ),
        )
    }

    @Test
    fun `S2 failure flow keeps user message and saves no assistant success message`() = runTest {
        val events = mutableListOf<String>()
        val run = runNpuInferenceAfterImmediateUserMessage(
            requestPrompt = "こんにちは",
            currentChatId = 42,
            createChat = {
                events += "create_chat"
                42
            },
            onChatCreated = { chatId ->
                events += "chat_created:$chatId"
            },
            insertUserMessage = { chatId, promptText ->
                events += "insert_user:$chatId:$promptText"
            },
            runInference = {
                s1EmptyAfterSanitizeFailureResult()
            },
        )
        val mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "こんにちは",
            s1Result = run.result,
        )
        if (shouldPersistNpuStandardRouteS2Db(enabled = true, mapping = mapping)) {
            events += "insert_assistant:${run.chatId}:${requireNotNull(mapping.saveCandidate).assistantMessage.text}"
        }

        assertEquals(1, events.count { it.startsWith("insert_user:") })
        assertEquals(0, events.count { it.startsWith("insert_assistant:") })
        assertFalse(mapping.hasSaveCandidate)
    }

    @Test
    fun `S2 through S5 phase gates follow NPU standard route mode`() {
        val s2Mapping = NpuStandardRouteS2DbBridge().prepareSaveCandidate(
            userPrompt = "こんにちは",
            s1Result = s1SuccessResult(),
        )
        val s3Mapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
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
        assertFalse(shouldRenderNpuStandardRouteS3Markdown(NpuStandardRouteMode.S2_DB.isS3Enabled(), s3Mapping))
        assertTrue(shouldRenderNpuStandardRouteS3Markdown(NpuStandardRouteMode.S3_MARKDOWN.isS3Enabled(), s3Mapping))
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
    fun `NPU standard route phase1 dev gate off emits no diagnostic-only keys`() {
        val context = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-npu",
            selectedModelFile = "/models/gemma-npu.litertlm",
            preferredBackend = "NPU",
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            shouldEnterNpuS1 = true,
            localRouteEntered = false,
        )

        val diagnostics = buildNpuStandardRoutePhase1Diagnostics(
            context = context,
            propertyReader = { null },
        )

        assertTrue(diagnostics.isEmpty())
    }

    @Test
    fun `NPU standard route phase1 dev gate on emits diagnostic-only connection keys`() {
        val context = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-npu",
            selectedModelFile = "/models/gemma-npu.litertlm",
            preferredBackend = "NPU",
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            effectiveNpuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            shouldEnterNpuS1 = true,
            localRouteEntered = false,
        )

        val diagnostics = buildNpuStandardRoutePhase1Diagnostics(
            context = context,
            propertyReader = { key ->
                if (key == NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY) "true" else null
            },
        )

        assertEquals("true", diagnostics["npu_standard_route_dev_gate_enabled"])
        assertEquals("1", diagnostics["npu_standard_route_phase"])
        assertEquals("1_route_entry_diagnostic", diagnostics["npu_standard_route_phase_name"])
        assertEquals("true", diagnostics["npu_standard_route_connected"])
        assertEquals("false", diagnostics["conversation_created"])
        assertEquals("false", diagnostics["generate_response"])
        assertEquals("false", diagnostics["npu_standard_route_ui_append_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_tts_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_db_save_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_markdown_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_streaming_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_rollback_required"])
        assertEquals("none", diagnostics["npu_standard_route_rollback_reason"])
    }

    @Test
    fun `NPU standard route explicit phase1 keeps conversation uncreated`() {
        val context = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-npu",
            selectedModelFile = "/models/gemma-npu.litertlm",
            preferredBackend = "NPU",
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            effectiveNpuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            shouldEnterNpuS1 = true,
            localRouteEntered = false,
        )

        val diagnostics = buildNpuStandardRoutePhase1Diagnostics(
            context = context,
            propertyReader = { key ->
                when (key) {
                    NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                    NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "1"
                    else -> null
                }
            },
        )

        assertEquals("1", diagnostics["npu_standard_route_phase"])
        assertEquals("1_route_entry_diagnostic", diagnostics["npu_standard_route_phase_name"])
        assertEquals("false", diagnostics["conversation_created"])
        assertEquals("false", diagnostics["generate_response"])
    }

    @Test
    fun `NPU standard route phase2 emits conversation created diagnostics without generate`() {
        val context = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-npu",
            selectedModelFile = "/models/gemma-npu.litertlm",
            preferredBackend = "NPU",
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            effectiveNpuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            shouldEnterNpuS1 = true,
            localRouteEntered = false,
        )

        val diagnostics = buildNpuStandardRoutePhase1Diagnostics(
            context = context,
            propertyReader = { key ->
                when (key) {
                    NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                    NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "2"
                    else -> null
                }
            },
        )

        assertEquals("true", diagnostics["npu_standard_route_dev_gate_enabled"])
        assertEquals("2", diagnostics["npu_standard_route_phase"])
        assertEquals("2_conversation_created_diagnostic", diagnostics["npu_standard_route_phase_name"])
        assertEquals("true", diagnostics["npu_standard_route_connected"])
        assertEquals("true", diagnostics["conversation_created"])
        assertEquals("false", diagnostics["generate_response"])
        assertEquals("false", diagnostics["npu_standard_route_ui_append_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_tts_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_db_save_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_markdown_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_streaming_allowed"])
    }

    @Test
    fun `NPU standard route phase2 quality candidate fail remains suppressed`() {
        val context = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-npu",
            selectedModelFile = "/models/gemma-npu.litertlm",
            preferredBackend = "NPU",
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            effectiveNpuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            shouldEnterNpuS1 = true,
            localRouteEntered = false,
        )

        val diagnostics = buildNpuStandardRoutePhase1Diagnostics(
            context = context,
            outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL,
            propertyReader = { key ->
                when (key) {
                    NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                    NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "2"
                    else -> null
                }
            },
        )

        assertEquals("2", diagnostics["npu_standard_route_phase"])
        assertEquals("true", diagnostics["conversation_created"])
        assertEquals("false", diagnostics["generate_response"])
        assertEquals("false", diagnostics["npu_standard_route_quality_gate_passed"])
        assertEquals("true", diagnostics["npu_standard_route_output_suppressed"])
        assertEquals("quality_candidate_fail", diagnostics["npu_standard_route_suppression_reason"])
        assertEquals("true", diagnostics["npu_standard_route_rollback_required"])
    }

    @Test
    fun `NPU standard route invalid phase falls back to phase1`() {
        val context = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-npu",
            selectedModelFile = "/models/gemma-npu.litertlm",
            preferredBackend = "NPU",
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            effectiveNpuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            shouldEnterNpuS1 = true,
            localRouteEntered = false,
        )

        val diagnostics = buildNpuStandardRoutePhase1Diagnostics(
            context = context,
            propertyReader = { key ->
                when (key) {
                    NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY -> "true"
                    NPU_STANDARD_ROUTE_PHASE_PROPERTY -> "banana"
                    else -> null
                }
            },
        )

        assertEquals("1", diagnostics["npu_standard_route_phase"])
        assertEquals("1_route_entry_diagnostic", diagnostics["npu_standard_route_phase_name"])
        assertEquals("false", diagnostics["conversation_created"])
        assertEquals("false", diagnostics["generate_response"])
    }

    @Test
    fun `NPU standard route phase1 quality candidate fail is suppressed`() {
        val context = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-npu",
            selectedModelFile = "/models/gemma-npu.litertlm",
            preferredBackend = "NPU",
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            shouldEnterNpuS1 = true,
            localRouteEntered = false,
        )

        val diagnostics = buildNpuStandardRoutePhase1Diagnostics(
            context = context,
            outputQualityCandidateStatus = NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL,
            propertyReader = { key ->
                if (key == NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY) "true" else null
            },
        )

        assertEquals("false", diagnostics["npu_standard_route_quality_gate_passed"])
        assertEquals("true", diagnostics["npu_standard_route_output_suppressed"])
        assertEquals("quality_candidate_fail", diagnostics["npu_standard_route_suppression_reason"])
        assertEquals("false", diagnostics["npu_standard_route_ui_append_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_tts_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_db_save_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_markdown_allowed"])
        assertEquals("false", diagnostics["npu_standard_route_streaming_allowed"])
        assertEquals("true", diagnostics["npu_standard_route_rollback_required"])
        assertEquals(
            "quality_gate_output_must_not_reach_ui_tts_db",
            diagnostics["npu_standard_route_rollback_reason"],
        )
    }

    @Test
    fun `NPU standard route phase1 diagnostics render as route diagnostic lines`() {
        val context = buildLocalRouteDiagnosticContext(
            selectedModelName = "gemma-npu",
            selectedModelFile = "/models/gemma-npu.litertlm",
            preferredBackend = "NPU",
            npuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            effectiveNpuStandardRouteMode = NpuStandardRouteMode.S1_ONLY.name,
            shouldEnterNpuS1 = true,
            localRouteEntered = false,
        )
        val trace = buildNpuStandardRoutePhase1DiagnosticLines(
            buildNpuStandardRoutePhase1Diagnostics(
                context = context,
                propertyReader = { key ->
                    if (key == NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY) "true" else null
                },
            ),
        ).joinToString(" ")

        assertTrue(trace.contains("npu_standard_route_dev_gate_enabled=true"))
        assertTrue(trace.contains("npu_standard_route_phase=1"))
        assertTrue(trace.contains("npu_standard_route_connected=true"))
        assertTrue(trace.contains("conversation_created=false"))
        assertTrue(trace.contains("generate_response=false"))
        assertTrue(trace.contains("npu_standard_route_ui_append_allowed=false"))
        assertTrue(trace.contains("npu_standard_route_tts_allowed=false"))
        assertTrue(trace.contains("npu_standard_route_db_save_allowed=false"))
        assertTrue(trace.contains("npu_standard_route_markdown_allowed=false"))
        assertTrue(trace.contains("npu_standard_route_streaming_allowed=false"))
    }

    @Test
    fun `NPU standard route phase1 dev gate does not affect CPU or GPU diagnostics`() {
        listOf("CPU", "GPU").forEach { backend ->
            val context = buildLocalRouteDiagnosticContext(
                selectedModelName = "gemma",
                selectedModelFile = "/models/gemma.litertlm",
                preferredBackend = backend,
                npuStandardRouteMode = NpuStandardRouteMode.OFF.name,
                shouldEnterNpuS1 = false,
                localRouteEntered = true,
            )

            val diagnostics = buildNpuStandardRoutePhase1Diagnostics(
                context = context,
                propertyReader = { key ->
                    if (key == NPU_STANDARD_ROUTE_DEV_GATE_PROPERTY) "true" else null
                },
            )

            assertTrue("$backend route should not emit NPU standard route phase1 diagnostics", diagnostics.isEmpty())
        }
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
        assertTrue(trace.contains("elapsed_ms=60000"))
        assertTrue(trace.contains("gpu_watchdog_timeout_ms=60000"))
        assertTrue(trace.contains("gpu_watchdog_mode=extended_dev_60s"))
        assertTrue(trace.contains("gpu_timeout_stage=engine_constructor"))
        assertTrue(trace.contains("gpu_timeout_elapsed_ms=60000"))
        assertTrue(trace.contains("gpu_engine_create_duration_ms=60000"))
        assertTrue(trace.contains("gpu_engine_create_started=true"))
        assertTrue(trace.contains("gpu_engine_create_finished=false"))
        assertTrue(trace.contains("gpu_engine_create_timeout_suspected=true"))
        assertTrue(trace.contains("guard_recommendation=switch_to_cpu_or_npu"))
        assertTrue(trace.contains("gpu_compatibility_mode=edge_gallery_like"))
        assertTrue(trace.contains("gpu_engine_config_profile=edge_gallery_like_text_only"))
        assertTrue(trace.contains("gpu_cache_dir_mode=gallery_like_null_for_app_model_path"))
        assertTrue(trace.contains("gpu_sampler_config_profile=gallery_defaults_64_0.95_1.0"))
        assertTrue(trace.contains("gpu_conversation_config_profile=gallery_like_sampler_config_non_npu"))
        assertTrue(trace.contains("gpu_thinking_enabled=false"))
        assertTrue(trace.contains("gpu_speculative_decoding_enabled=false"))
        assertTrue(trace.contains("gpu_max_tokens=1024"))
        assertTrue(trace.contains("gpu_top_k=64"))
        assertTrue(trace.contains("gpu_top_p=0.95"))
        assertTrue(trace.contains("gpu_temperature=1.0"))
        assertTrue(trace.contains("gpu_dispatcher=Dispatchers.IO"))
        assertTrue(trace.contains("gpu_engine_initialize_api=Engine.initialize"))
        assertTrue(trace.contains("gpu_edge_gallery_diff_applied=true"))
        assertTrue(trace.contains("gpu_generate_started=false"))
        assertTrue(trace.contains("gpu_first_token_received=false"))
        assertTrue(trace.contains("gpu_model_kind=generic-litertlm"))
        assertTrue(trace.contains("gpu_backend_setting=GPU"))
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
        assertTrue(trace.contains("elapsed_ms=60000"))
        assertTrue(trace.contains("gpu_timeout_stage=engine_constructor"))
        assertTrue(trace.contains("gpu_watchdog_timeout_ms=60000"))
        assertTrue(trace.contains("gpu_watchdog_mode=extended_dev_60s"))
        assertTrue(trace.contains("gpu_stale_callback_ignored=true"))
    }

    @Test
    fun `Generic GPU timeout diagnostics can report standard 20s watchdog mode`() {
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
                engineCreateStarted = true,
                engineCreateFinished = false,
                failureStage = "engine_create_timeout",
            ),
            elapsedMs = GPU_EXPERIMENTAL_STAGE_TIMEOUT_STANDARD_MS,
            gpuWatchdogTimeoutMs = GPU_EXPERIMENTAL_STAGE_TIMEOUT_STANDARD_MS,
        )

        assertTrue(trace.contains("gpu_watchdog_timeout_ms=20000"))
        assertTrue(trace.contains("gpu_watchdog_mode=standard_20s"))
        assertTrue(trace.contains("gpu_timeout_stage=engine_constructor"))
        assertTrue(trace.contains("gpu_engine_create_timeout_suspected=true"))
        assertTrue(trace.contains("guard_recommendation=switch_to_cpu_or_npu"))
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
        assertTrue(
            listOf("engine_constructor", "generate_start", "generate_before_first_token").contains(
                resolveGpuExperimentalTimeoutStage("engine_create_timeout"),
            ),
        )
        assertTrue(
            listOf("engine_constructor", "generate_start", "generate_before_first_token").contains(
                resolveGpuExperimentalTimeoutStage("generate_start_timeout"),
            ),
        )
        assertTrue(
            listOf("engine_constructor", "generate_start", "generate_before_first_token").contains(
                resolveGpuExperimentalTimeoutStage("first_token_timeout"),
            ),
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
    fun `S1 success result displays timing fields for NPU speed diagnostics`() {
        val result = s1SuccessResult().withTiming(
            NpuStandardRouteS1Timing(
                totalMs = 1200,
                decodeMs = 1000,
                ttftMs = null,
                outputTokens = 20,
                tokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS,
                tokensPerSecond = 20.0,
            ),
        )

        assertTrue(result.displayText.contains("[DEV診断: NPU Standard Route S1 Timing]"))
        assertTrue(result.displayText.contains("npu_s1_total_ms=1200"))
        assertTrue(result.displayText.contains("npu_s1_decode_ms=1000"))
        assertTrue(result.displayText.contains("npu_s1_ttft_ms=n/a"))
        assertTrue(result.displayText.contains("npu_s1_output_tokens=20"))
        assertTrue(result.displayText.contains("npu_s1_token_count_mode=estimated_code_points"))
        assertTrue(result.displayText.contains("npu_s1_tokens_per_second=20.0"))
        assertTrue(result.displayText.contains("run_decode_reached=true"))
        assertTrue(result.displayText.contains("fallback_used=false"))
        assertTrue(result.displayText.contains("fresh_crash=false"))
        assertTrue(result.displayText.contains("timeout=false"))
        assertTrue(result.displayText.contains("quality_classification=natural_japanese"))
    }

    @Test
    fun `S1 failure result displays timing fields without success criteria`() {
        val result = s1EmptyAfterSanitizeFailureResult().withTiming(
            NpuStandardRouteS1Timing(
                totalMs = 850,
                decodeMs = 800,
                ttftMs = null,
                outputTokens = null,
                tokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_UNAVAILABLE,
                tokensPerSecond = null,
            ),
        )

        assertFalse(result.successCriteriaMet)
        assertTrue(result.displayText.contains("status=failure"))
        assertTrue(result.displayText.contains("reason=empty_after_sanitize"))
        assertTrue(result.displayText.contains("npu_s1_total_ms=850"))
        assertTrue(result.displayText.contains("npu_s1_decode_ms=800"))
        assertTrue(result.displayText.contains("npu_s1_ttft_ms=n/a"))
        assertTrue(result.displayText.contains("npu_s1_output_tokens=n/a"))
        assertTrue(result.displayText.contains("npu_s1_tokens_per_second=n/a"))
        assertTrue(result.displayText.contains("run_decode_reached=true"))
        assertTrue(result.displayText.contains("fallback_used=false"))
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
        val blockedResult = s1NativeRouteBlockedFailureResult()
        assertEquals(
            NPU_STANDARD_ROUTE_S1_NORMAL_CHAT_BLOCKED_USER_MESSAGE,
            resolveNpuStandardRouteFailureAssistantMessage(
                result = blockedResult,
                transientFallback = null,
            ),
        )
        assertEquals(
            NpuStandardRouteS1ProviderSelector.REASON_NATIVE_ROUTE_BLOCKED_FOR_NORMAL_CHAT,
            blockedResult.reason,
        )
        assertNull(
            resolveNpuStandardRouteFailureAssistantMessage(
                result = s1SuccessResult(),
                transientFallback = null,
            ),
        )
        assertNull(
            resolveNpuStandardRouteFailureAssistantMessage(
                result = s1TemplateArtifactSanitizedSuccessResult(),
                transientFallback = null,
            ),
        )
        assertNull(
            resolveNpuStandardRouteFailureAssistantMessage(
                result = s1SuccessResultWithQuality(NpuStandardRouteS1Contract.QUALITY_MIXED_LANGUAGE),
                transientFallback = null,
            ),
        )
        assertNull(
            resolveNpuStandardRouteFailureAssistantMessage(
                result = s1SafeArithmeticEndTurnVariantResult(),
                transientFallback = null,
            ),
        )
        assertEquals("2", s1SafeArithmeticEndTurnVariantResult().displayText)
        assertNull(
            resolveNpuStandardRouteFailureAssistantMessage(
                result = s1SafeArithmeticTailLeakResult(),
                transientFallback = null,
            ),
        )
        assertEquals("2", s1SafeArithmeticTailLeakResult().displayText)
        val brokenArithmetic = s1BrokenArithmeticTurnLeakResult()
        val brokenArithmeticMessage = resolveNpuStandardRouteFailureAssistantMessage(
            result = brokenArithmetic,
            transientFallback = null,
        )
        assertEquals(
            "NPU推論の応答生成に失敗しました: " +
                "raw_unexpected_start_turn+user_turn_leak+prompt_repetition_only+arithmetic_answer_missing",
            brokenArithmeticMessage,
        )
        assertFalse(brokenArithmetic.successCriteriaMet)
        assertFalse(brokenArithmeticMessage.orEmpty().contains("<start_of_turn>user"))
        assertFalse(brokenArithmeticMessage.orEmpty().contains("<end_of_turn"))
    }

    private fun s1SuccessResult(
        sanitizedOutput: String = "こんにちは。",
    ): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                reason = "success",
                rawOutput = sanitizedOutput,
                sanitizedOutput = sanitizedOutput,
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

    private fun s1TemplateArtifactSanitizedSuccessResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                reason = "success",
                rawOutput = ">こんにちは！何かお手伝いできることはありますか？<end_of_turn>",
                sanitizedOutput = "こんにちは！何かお手伝いできることはありますか？",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
            ),
        )

    private fun s1SuccessResultWithQuality(qualityClassification: String): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                reason = "success",
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                qualityClassification = qualityClassification,
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
            ),
        )

    private fun s1BrokenArithmeticTurnLeakResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                reason = "success",
                rawOutput = "１＋１は？<end_of_turn>\n<start_of_turn>user１＋１は？<end_of_turn",
                sanitizedOutput = "１＋１は？\n１＋１は？<end_of_turn",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
                inputPrompt = "１＋１は？",
            ),
        )

    private fun s1SafeArithmeticEndTurnVariantResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                reason = "success",
                rawOutput = ">\n2\n< end_of_turn>",
                sanitizedOutput = "2\n< end_of_turn>",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_MIXED_LANGUAGE,
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
                inputPrompt = "１＋１は？",
            ),
        )

    private fun s1SafeArithmeticTailLeakResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "success",
                reason = "success",
                rawOutput = ">2</start_of_turn>\n<end_of_turn>\n<start_of_turn>user>次の計算に日本語で",
                sanitizedOutput = "2</start_of_turn>\n\n次の計算に日本語で",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                runDecodeReached = true,
                npuBackendEvidence = "QNN_HTP_V79_FastRPC_native_diag",
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                requestedMaxOutputTokens = 32,
                effectiveMaxOutputTokens = 32,
                inputPrompt = "1+1は？",
            ),
        )

    private fun s1NativeRouteBlockedFailureResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = "failure",
                result = "failure",
                success = false,
                reason = NpuStandardRouteS1ProviderSelector.REASON_NATIVE_ROUTE_BLOCKED_FOR_NORMAL_CHAT,
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
