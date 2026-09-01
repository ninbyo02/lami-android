package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInferenceOutputPolicyTest {
    @Test
    fun `successful NPU output is the only accepted display text`() {
        val decision = LocalInferenceOutputPolicy.evaluateNpu(
            userPrompt = "こんにちは",
            result = successResult(),
            localStopRequested = false,
        )

        assertEquals(LocalInferenceOutputDisposition.ACCEPT, decision.disposition)
        assertEquals("こんにちは。", decision.acceptedText)
        assertNull(decision.terminalText)
        assertNull(decision.transientFallback)
        assertFalse(decision.shouldRunGenericFallback)
    }

    @Test
    fun `mixed-language NPU greeting becomes deterministic safe fallback`() {
        val decision = LocalInferenceOutputPolicy.evaluateNpu(
            userPrompt = "こんにちは",
            result = mixedLanguageFailureResult(),
            localStopRequested = false,
        )

        assertEquals(LocalInferenceOutputDisposition.SAFE_FALLBACK, decision.disposition)
        assertEquals("こんにちは。", decision.acceptedText)
        assertEquals("こんにちは。", decision.terminalText)
        assertEquals(
            NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING,
            decision.transientFallback?.kind,
        )
        assertTrue(decision.shouldFinalizeImmediately)
        assertTrue(decision.shouldFinalizeAsAssistantResponse)
        assertFalse(decision.shouldRunGenericFallback)
    }

    @Test
    fun `non-greeting quality failure requests generic backend fallback`() {
        val decision = LocalInferenceOutputPolicy.evaluateNpu(
            userPrompt = "この文章を要約して",
            result = questionEchoFailureResult(),
            localStopRequested = false,
        )

        assertEquals(LocalInferenceOutputDisposition.GENERIC_FALLBACK, decision.disposition)
        assertTrue(decision.shouldRunGenericFallback)
        assertFalse(decision.shouldFinalizeImmediately)
        assertTrue(decision.acceptedText.isBlank())
        assertNull(decision.terminalText)
    }

    @Test
    fun `stop request suppresses new fallback work`() {
        val decision = LocalInferenceOutputPolicy.evaluateNpu(
            userPrompt = "この文章を要約して",
            result = questionEchoFailureResult(),
            localStopRequested = true,
        )

        assertEquals(LocalInferenceOutputDisposition.STOPPED, decision.disposition)
        assertFalse(decision.shouldRunGenericFallback)
        assertFalse(decision.shouldFinalizeImmediately)
    }

    @Test
    fun `non-fallback NPU failure remains a terminal rejection`() {
        val decision = LocalInferenceOutputPolicy.evaluateNpu(
            userPrompt = "質問です",
            result = buildNpuStandardRouteS1ModelNotCompatibleResult(
                eligibility = NpuStandardRouteS1ModelEligibility(
                    selectedModelName = "unsupported",
                    selectedModelFile = "unsupported.task",
                    npuModelEligible = false,
                ),
            ),
            localStopRequested = false,
        )

        assertEquals(LocalInferenceOutputDisposition.REJECT, decision.disposition)
        assertEquals(
            NpuStandardRouteS1Contract.MODEL_NOT_NPU_COMPATIBLE_MESSAGE,
            decision.terminalText,
        )
        assertTrue(decision.shouldFinalizeImmediately)
        assertFalse(decision.shouldFinalizeAsAssistantResponse)
    }

    @Test
    fun `local candidate quality gate is shared by GPU and CPU chain`() {
        val bad = LocalInferenceOutputPolicy.evaluateLocalCandidate(
            userPrompt = "こんにちは",
            response = "日本語の前置きです。" + "든지".repeat(48),
        )
        val good = LocalInferenceOutputPolicy.evaluateLocalCandidate(
            userPrompt = "こんにちは",
            response = "こんにちは。何をお手伝いしましょうか。",
        )

        assertEquals(LocalInferenceOutputDisposition.REJECT, bad.disposition)
        assertTrue(bad.shouldFallbackToNextBackend)
        assertEquals("degenerate_repetition", bad.rejectionReason)
        assertEquals(LocalInferenceOutputDisposition.ACCEPT, good.disposition)
        assertEquals("こんにちは。何をお手伝いしましょうか。", good.acceptedText)
        assertFalse(good.shouldFallbackToNextBackend)
    }

    @Test
    fun `final local acceptance also requires a supported successful backend`() {
        val unsupported = LocalInferenceOutputPolicy.evaluateLocalCompletion(
            userPrompt = "こんにちは",
            successfulBackend = "NPU",
            response = "こんにちは。",
        )
        val gpu = LocalInferenceOutputPolicy.evaluateLocalCompletion(
            userPrompt = "こんにちは",
            successfulBackend = "gpu",
            response = "こんにちは。",
        )

        assertEquals(LocalInferenceOutputDisposition.REJECT, unsupported.disposition)
        assertEquals("unsupported_backend", unsupported.rejectionReason)
        assertEquals(LocalInferenceOutputDisposition.ACCEPT, gpu.disposition)
        assertEquals("こんにちは。", gpu.acceptedText)
    }

    @Test
    fun `ChatScreen routes NPU GPU and CPU decisions through the unified policy`() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src").isDirectory }
        val source = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()

        assertTrue(source.contains("LocalInferenceOutputPolicy.evaluateNpu("))
        assertEquals(
            "GPU/CPU fallback must validate both provisional streaming partials and final candidates.",
            4,
            "LocalInferenceOutputPolicy.evaluateLocalCandidate(".countIn(source),
        )
        assertTrue(source.contains("npuOutputDecision.acceptedText.trim()"))
        assertFalse(source.contains("localInferenceResponseRejectionReason(requestPrompt, result.response)"))
    }

    private fun successResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            rawResult(
                rawOutput = "こんにちは。",
                sanitizedOutput = "こんにちは。",
                inputPrompt = "こんにちは",
            ),
        )

    private fun mixedLanguageFailureResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            rawResult(
                status = "failure",
                result = "failure",
                success = false,
                reason = NpuStandardRouteS1Contract.REASON_MIXED_LANGUAGE,
                rawOutput = "안녕하세요.",
                sanitizedOutput = "",
                qualityClassification = NpuStandardRouteS1Contract.REASON_MIXED_LANGUAGE,
                inputPrompt = "こんにちは",
            ),
        )

    private fun questionEchoFailureResult(): NpuStandardRouteS1Result =
        NpuStandardRouteS1Mapper.map(
            rawResult(
                status = "failure",
                result = "failure",
                success = false,
                reason = NpuStandardRouteS1Contract.REASON_QUESTION_ECHO,
                rawOutput = "この文章を要約して\nユーザー: 続きを入力",
                sanitizedOutput = "この文章を要約して",
                qualityClassification = NpuStandardRouteS1Contract.REASON_QUESTION_ECHO,
                inputPrompt = "この文章を要約して",
            ),
        )

    private fun rawResult(
        status: String = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        result: String = "",
        success: Boolean? = null,
        reason: String = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput: String,
        sanitizedOutput: String,
        qualityClassification: String = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        inputPrompt: String,
    ): NpuStandardRouteS1RawResult = NpuStandardRouteS1RawResult(
        status = status,
        result = result,
        success = success,
        reason = reason,
        rawOutput = rawOutput,
        sanitizedOutput = sanitizedOutput,
        qualityClassification = qualityClassification,
        runDecodeReached = true,
        npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed = false,
        timeout = false,
        freshCrash = false,
        requestedMaxOutputTokens = 32,
        effectiveMaxOutputTokens = 32,
        inputPrompt = inputPrompt,
    )

    private fun String.countIn(text: String): Int {
        var count = 0
        var start = 0
        while (true) {
            val index = text.indexOf(this, start)
            if (index < 0) return count
            count += 1
            start = index + length
        }
    }
}
