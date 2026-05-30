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
