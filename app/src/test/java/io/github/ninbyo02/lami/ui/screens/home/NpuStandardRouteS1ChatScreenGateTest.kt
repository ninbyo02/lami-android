package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.components.InferenceTarget
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
    fun `bridge path keeps side effects disconnected`() {
        val result = NpuStandardRouteS1Bridge().run()

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
}
