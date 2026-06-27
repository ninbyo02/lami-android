package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS1ContractTest {
    @Test
    fun `selection defaults keep S1 disabled and side effects disconnected`() {
        val selection = NpuStandardRouteS1Selection()

        assertFalse(selection.enabled)
        assertFalse(selection.selectable)
        assertEquals("standard_chat_screen_s1_npu_display_only", selection.routeType)
        assertEquals("gemma_it_user_model", selection.promptTailVariant)
        assertEquals(32, selection.requestedMaxOutputTokens)
        assertEquals(32, selection.effectiveMaxOutputTokens)
        assertTrue(selection.sideEffects.allDisconnected)
        assertFalse(selection.sideEffects.db)
        assertFalse(selection.sideEffects.tts)
        assertFalse(selection.sideEffects.markdown)
        assertFalse(selection.sideEffects.streaming)
        assertFalse(selection.sideEffects.backendNpuPersisted)
        assertFalse(selection.sideEffects.conversationHistorySaved)
    }

    @Test
    fun `enabled selection is selectable only with fixed S1 contract`() {
        val selection = NpuStandardRouteS1Selection(enabled = true)

        assertTrue(selection.selectable)
        assertTrue(
            selection.copy(
                requestedMaxOutputTokens = 128,
                effectiveMaxOutputTokens = 128,
            ).selectable,
        )
        assertFalse(selection.copy(requestedMaxOutputTokens = 16).selectable)
        assertFalse(selection.copy(effectiveMaxOutputTokens = 16).selectable)
        assertFalse(selection.copy(requestedMaxOutputTokens = 17).selectable)
        assertFalse(selection.copy(promptTailVariant = "raw_dialog_tail_variant_a").selectable)
        assertFalse(selection.copy(promptTailVariant = "raw_dialog_tail_variant_b").selectable)
        assertFalse(selection.copy(sideEffects = NpuStandardRouteS1SideEffects(db = true)).selectable)
        assertFalse(selection.copy(sideEffects = NpuStandardRouteS1SideEffects(tts = true)).selectable)
        assertFalse(selection.copy(sideEffects = NpuStandardRouteS1SideEffects(markdown = true)).selectable)
        assertFalse(selection.copy(sideEffects = NpuStandardRouteS1SideEffects(streaming = true)).selectable)
        assertFalse(selection.copy(sideEffects = NpuStandardRouteS1SideEffects(backendNpuPersisted = true)).selectable)
        assertFalse(selection.copy(sideEffects = NpuStandardRouteS1SideEffects(conversationHistorySaved = true)).selectable)
    }

    @Test
    fun `natural Japanese NPU result satisfies S1 success criteria`() {
        val result = successResult()

        assertTrue(result.successCriteriaMet)
        assertTrue(result.displayText.contains("NPU STANDARD ROUTE S1"))
        assertTrue(result.displayText.contains("standard_route_connected=true"))
        assertTrue(result.displayText.contains("status=success"))
        assertTrue(result.displayText.contains("reason=success"))
        assertTrue(result.displayText.contains("prompt_tail_variant=gemma_it_user_model"))
        assertTrue(result.displayText.contains("prompt_wrapper_used=gemma_it_user_model"))
        assertTrue(result.displayText.contains("requested_max_output_tokens=32"))
        assertTrue(result.displayText.contains("effective_max_output_tokens=32"))
        assertTrue(result.displayText.contains("max_output_tokens=32"))
        assertTrue(result.displayText.contains("run_decode_reached=true"))
        assertTrue(result.displayText.contains("npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag"))
        assertTrue(result.displayText.contains("fallback_used=false"))
        assertTrue(result.displayText.contains("timeout=false"))
        assertTrue(result.displayText.contains("fresh_crash=false"))
        assertTrue(result.displayText.contains("raw_output=こんにちは。"))
        assertTrue(result.displayText.contains("sanitized_output=こんにちは。"))
        assertTrue(result.displayText.contains("quality_classification=natural_japanese"))
        assertTrue(result.displayText.contains("output_quality_candidate_status=quality_candidate_pass"))
        assertTrue(result.displayText.contains("output_quality_candidate_prepared_output=こんにちは。"))
        assertTrue(result.displayText.contains("db=false"))
        assertTrue(result.displayText.contains("tts=false"))
        assertTrue(result.displayText.contains("markdown=false"))
        assertTrue(result.displayText.contains("streaming=false"))
        assertTrue(result.displayText.contains("backend_npu_persisted=false"))
        assertTrue(result.displayText.contains("conversation_history_saved=false"))
    }

    @Test
    fun `fallback timeout fresh crash and quality failures fail S1 criteria`() {
        assertFalse(successResult(fallbackUsed = true).successCriteriaMet)
        assertFalse(successResult(timeout = true).successCriteriaMet)
        assertFalse(successResult(freshCrash = true).successCriteriaMet)
        assertFalse(successResult(runDecodeReached = false).successCriteriaMet)
        assertFalse(successResult(npuBackendEvidence = "").successCriteriaMet)
        assertFalse(successResult(rawOutput = "", sanitizedOutput = "").successCriteriaMet)
        assertFalse(successResult(selection = NpuStandardRouteS1Selection(enabled = false)).successCriteriaMet)
    }

    @Test
    fun `failure display includes native link diagnostics`() {
        val result = NpuStandardRouteS1Result(
            selection = NpuStandardRouteS1Selection(enabled = true),
            status = "failure",
            reason = "adapter_failure:UnsatisfiedLinkError",
            rawOutput = "",
            sanitizedOutput = "",
            qualityClassification = NPU_S1_OUTPUT_QUALITY_UNKNOWN,
            runDecodeReached = false,
            npuBackendEvidence = "",
            fallbackUsed = false,
            timeout = false,
            freshCrash = false,
            nativeDiagnostics = NpuS1NativeStageDiagnostics(
                nativeErrorClass = "UnsatisfiedLinkError",
                nativeErrorMessage = "dlopen failed: library \"libLiteRt.so\" not found",
                nativeErrorStage = "native_call",
                nativeErrorSource = "throwable",
                nativeLinkFailureDetected = "true",
                nativeLinkFailureLibrary = "libLiteRt.so",
                nativeLoadOrder = "litertlm_jni>lami_npu_persistent_holder_stub",
                javaLibraryPath = "/data/app/lib/arm64",
                supportedAbis = "arm64-v8a",
            ),
        )

        assertTrue(result.displayText.contains("reason=adapter_failure:UnsatisfiedLinkError"))
        assertTrue(result.displayText.contains("native_error_class=UnsatisfiedLinkError"))
        assertTrue(result.displayText.contains("native_error_message=dlopen failed: library \"libLiteRt.so\" not found"))
        assertTrue(result.displayText.contains("native_link_failure_library=libLiteRt.so"))
        assertTrue(result.displayText.contains("native_load_order=litertlm_jni>lami_npu_persistent_holder_stub"))
    }

    @Test
    fun `mixed language classification can pass when quality candidate is safe`() {
        val result = successResult(
            rawOutput = "私はLamiです。よろしくお願いします。",
            sanitizedOutput = "私はLamiです。よろしくお願いします。",
            qualityClassification = NpuStandardRouteS1Contract.QUALITY_MIXED_LANGUAGE,
        )

        assertTrue(result.successCriteriaMet)
        assertEquals("quality_candidate_pass", result.outputQualityCandidateStatus)
    }

    private fun successResult(
        selection: NpuStandardRouteS1Selection = NpuStandardRouteS1Selection(enabled = true),
        rawOutput: String = "こんにちは。",
        sanitizedOutput: String = "こんにちは。",
        qualityClassification: String = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        runDecodeReached: Boolean = true,
        npuBackendEvidence: String = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed: Boolean = false,
        timeout: Boolean = false,
        freshCrash: Boolean = false,
    ): NpuStandardRouteS1Result = NpuStandardRouteS1Result(
        selection = selection,
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput = rawOutput,
        sanitizedOutput = sanitizedOutput,
        qualityClassification = qualityClassification,
        runDecodeReached = runDecodeReached,
        npuBackendEvidence = npuBackendEvidence,
        fallbackUsed = fallbackUsed,
        timeout = timeout,
        freshCrash = freshCrash,
    )
}
