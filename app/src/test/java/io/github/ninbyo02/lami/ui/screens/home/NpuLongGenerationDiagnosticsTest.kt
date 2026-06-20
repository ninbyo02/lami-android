package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class NpuLongGenerationDiagnosticsTest {
    @Test
    fun `summary exposes NPU Beta long generation keys in token order`() {
        val text = formatNpuLongGenerationDiagnosticsForDev(
            NpuLongGenerationState(
                status = NPU_LONG_GENERATION_STATUS_COMPLETED,
                prompt = NPU_LONG_GENERATION_DEFAULT_PROMPT,
                tokenPlan = NPU_LONG_GENERATION_TOKEN_PLAN,
                selectedBackend = NPU_S1_BACKEND_NPU_S5,
                requestedBackend = NPU_S1_BACKEND_NPU,
                effectiveBackend = NPU_S1_BACKEND_NPU,
                backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                routeFamily = NPU_S1_ROUTE_FAMILY_NPU_S5,
                cases = listOf(
                    case(1, requestedMaxOutputTokens = 32, tokensPerSecond = 2.0),
                    case(2, requestedMaxOutputTokens = 128, tokensPerSecond = 4.0),
                    case(3, requestedMaxOutputTokens = 512, tokensPerSecond = 6.0),
                ),
            ),
        )

        assertTrue(text.contains("test_name=NPU Beta Long Generation Test"))
        assertTrue(text.contains("prompt=$NPU_LONG_GENERATION_DEFAULT_PROMPT"))
        assertTrue(text.contains("token_plan=32,128,512"))
        assertTrue(text.contains("completed_cases=3"))
        assertTrue(text.contains("success_count=3"))
        assertTrue(text.contains("failed_count=0"))
        assertTrue(text.contains("fallback_used_count=0"))
        assertTrue(text.contains("timeout_count=0"))
        assertTrue(text.contains("fresh_crash_count=0"))
        assertTrue(text.contains("run_decode_reached_count=3"))
        assertTrue(text.contains("average_tokens_per_second=4.00"))
        assertTrue(text.contains("first_failure_reason=unavailable"))
        assertTrue(text.contains("backend_evidence_summary=QNN_HTP_V79_FastRPC_native_diag:3"))
        assertTrue(text.contains("quality_classification_summary=natural_japanese:3"))
        assertTrue(text.indexOf("requested_max_output_tokens=32") < text.indexOf("requested_max_output_tokens=128"))
        assertTrue(text.indexOf("requested_max_output_tokens=128") < text.indexOf("requested_max_output_tokens=512"))
    }

    @Test
    fun `per case fields keep unavailable stop and eos telemetry unavailable`() {
        val text = formatNpuLongGenerationDiagnosticsForDev(
            NpuLongGenerationState(
                tokenPlan = NPU_LONG_GENERATION_TOKEN_PLAN,
                cases = listOf(case(1, requestedMaxOutputTokens = 32)),
            ),
        )

        assertTrue(text.contains("[DEV診断: NPU Beta Long Generation case]"))
        assertTrue(text.contains("requested_max_output_tokens=32"))
        assertTrue(text.contains("effective_max_output_tokens=32"))
        assertTrue(text.contains("status=success"))
        assertTrue(text.contains("reason=success"))
        assertTrue(text.contains("fallback_used=false"))
        assertTrue(text.contains("timeout=false"))
        assertTrue(text.contains("fresh_crash=false"))
        assertTrue(text.contains("run_decode_reached=true"))
        assertTrue(text.contains("total_ms=100"))
        assertTrue(text.contains("decode_ms=90"))
        assertTrue(text.contains("output_tokens=30"))
        assertTrue(text.contains("token_count_mode=estimated_code_points"))
        assertTrue(text.contains("tokens_per_second=3.00"))
        assertTrue(text.contains("raw_output=raw 32"))
        assertTrue(text.contains("sanitized_output=sanitized 32"))
        assertTrue(text.contains("quality_classification=natural_japanese"))
        assertTrue(text.contains("backend_evidence=QNN_HTP_V79_FastRPC_native_diag"))
        assertTrue(text.contains("finish_reason=unavailable"))
        assertTrue(text.contains("stop_reason=unavailable"))
        assertTrue(text.contains("eos_detected=unavailable"))
        assertTrue(text.contains("tokenizer_output_tokens=unavailable"))
    }

    @Test
    fun `Copy Long Summary includes summary keys without cases`() {
        val text = buildNpuLongGenerationSummaryCopyText(
            NpuLongGenerationState(
                status = NPU_LONG_GENERATION_STATUS_COMPLETED,
                tokenPlan = NPU_LONG_GENERATION_TOKEN_PLAN,
                cases = listOf(case(1, requestedMaxOutputTokens = 32)),
            ),
        )

        assertTrue(text.contains("[DEV診断: NPU Beta Long Generation summary]"))
        assertTrue(text.contains("test_name=NPU Beta Long Generation Test"))
        assertTrue(text.contains("status=completed"))
        assertTrue(text.contains("token_plan=32,128,512"))
        assertTrue(text.contains("completed_cases=1"))
        assertTrue(text.contains("selected_backend="))
        assertTrue(text.contains("finished_at_ms=unavailable"))
        assertFalse(text.contains("[DEV診断: NPU Beta Long Generation case]"))
        assertFalse(text.contains("requested_max_output_tokens=32"))
    }

    @Test
    fun `Copy Long Full Dump includes summary and case keys`() {
        val text = buildNpuLongGenerationFullDumpCopyText(
            NpuLongGenerationState(
                status = NPU_LONG_GENERATION_STATUS_COMPLETED,
                tokenPlan = NPU_LONG_GENERATION_TOKEN_PLAN,
                cases = listOf(case(1, requestedMaxOutputTokens = 32)),
            ),
        )

        assertTrue(text.contains("[DEV診断: NPU Beta Long Generation summary]"))
        assertTrue(text.contains("[DEV診断: NPU Beta Long Generation case]"))
        assertTrue(text.contains("case_index=1"))
        assertTrue(text.contains("requested_max_output_tokens=32"))
        assertTrue(text.contains("tokenizer_output_tokens=unavailable"))
    }

    @Test
    fun `summary counts failed fallback timeout fresh crash and decode reached`() {
        val text = formatNpuLongGenerationDiagnosticsForDev(
            NpuLongGenerationState(
                tokenPlan = NPU_LONG_GENERATION_TOKEN_PLAN,
                cases = listOf(
                    case(1, requestedMaxOutputTokens = 32),
                    case(
                        caseIndex = 2,
                        requestedMaxOutputTokens = 128,
                        status = "failure",
                        reason = "timeout",
                        fallbackUsed = true,
                        timeout = true,
                        freshCrash = true,
                        runDecodeReached = false,
                        qualityClassification = "empty_output",
                    ),
                    case(
                        caseIndex = 3,
                        requestedMaxOutputTokens = 512,
                        status = "failure",
                        reason = "quality_failure",
                        qualityClassification = "mojibake",
                    ),
                ),
            ),
        )

        assertTrue(text.contains("completed_cases=3"))
        assertTrue(text.contains("success_count=1"))
        assertTrue(text.contains("failed_count=2"))
        assertTrue(text.contains("fallback_used_count=1"))
        assertTrue(text.contains("timeout_count=1"))
        assertTrue(text.contains("fresh_crash_count=1"))
        assertTrue(text.contains("run_decode_reached_count=2"))
        assertTrue(text.contains("first_failure_reason=timeout"))
        assertTrue(text.contains("quality_classification_summary=empty_output:1,mojibake:1,natural_japanese:1"))
    }

    @Test
    fun `case builder uses result telemetry without inferring finish reason`() {
        val result = NpuStandardRouteS1Result(
            selection = NpuStandardRouteS1Selection(
                enabled = true,
                requestedMaxOutputTokens = 128,
                effectiveMaxOutputTokens = 128,
            ),
            status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
            reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
            rawOutput = "raw",
            sanitizedOutput = "sanitized",
            qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
            runDecodeReached = true,
            npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
            fallbackUsed = false,
            timeout = false,
            freshCrash = false,
            timing = NpuStandardRouteS1Timing(
                totalMs = 120L,
                decodeMs = 100L,
                outputTokens = 60,
                tokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS,
                tokensPerSecond = 6.0,
            ),
            inputPrompt = NPU_LONG_GENERATION_DEFAULT_PROMPT,
        )

        val case = npuLongGenerationCaseFromResult(
            caseIndex = 1,
            requestedMaxOutputTokens = 128,
            result = result,
            backendDiagnostics = NpuS1BackendDiagnostics(
                selectedBackend = NPU_S1_BACKEND_NPU_S5,
                requestedBackend = NPU_S1_BACKEND_NPU,
                effectiveBackend = NPU_S1_BACKEND_NPU,
                backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                routeFamily = NPU_S1_ROUTE_FAMILY_NPU_S5,
            ),
        )

        assertTrue(case.finishReason == "unavailable")
        assertTrue(case.stopReason == "unavailable")
        assertTrue(case.eosDetected == "unavailable")
        assertTrue(case.tokenizerOutputTokens == "unavailable")
    }

    private fun case(
        caseIndex: Int,
        requestedMaxOutputTokens: Int,
        status: String = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason: String = NpuStandardRouteS1Contract.REASON_SUCCESS,
        fallbackUsed: Boolean = false,
        timeout: Boolean = false,
        freshCrash: Boolean = false,
        runDecodeReached: Boolean = true,
        tokensPerSecond: Double? = 3.0,
        qualityClassification: String = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
    ): NpuLongGenerationCase = NpuLongGenerationCase(
        caseIndex = caseIndex,
        requestedMaxOutputTokens = requestedMaxOutputTokens,
        effectiveMaxOutputTokens = requestedMaxOutputTokens,
        status = status,
        reason = reason,
        fallbackUsed = fallbackUsed,
        timeout = timeout,
        freshCrash = freshCrash,
        runDecodeReached = runDecodeReached,
        totalMs = 100L,
        decodeMs = 90L,
        outputTokens = 30,
        tokenCountMode = NpuStandardRouteS1Contract.TOKEN_COUNT_MODE_ESTIMATED_CODE_POINTS,
        tokensPerSecond = tokensPerSecond,
        rawOutput = "raw $requestedMaxOutputTokens",
        sanitizedOutput = "sanitized $requestedMaxOutputTokens",
        qualityClassification = qualityClassification,
        backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
    )
}
