package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuKotlinConversationQualityPolicyTest {
    @Test
    fun `Conversation API accepts a natural extended greeting`() {
        val response = "こんにちは。何かお手伝いできることはありますか？"

        val candidate = evaluateNpuStandardRouteQualityCandidate(
            rawOutput = response,
            sanitizedOutput = response,
            inputPrompt = "こんにちは",
            conversationApiUsed = true,
        )

        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS, candidate.status)
        assertEquals("conversation_api_natural_greeting", candidate.reason)
        assertEquals(response, candidate.preparedOutput)
    }

    @Test
    fun `legacy adapter keeps exact greeting quality contract`() {
        val response = "こんにちは。何かお手伝いできることはありますか？"

        val candidate = evaluateNpuStandardRouteQualityCandidate(
            rawOutput = response,
            sanitizedOutput = response,
            inputPrompt = "こんにちは",
            conversationApiUsed = false,
        )

        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, candidate.status)
        assertTrue(candidate.reason.contains("greeting_response_mismatch"))
    }

    @Test
    fun `Kotlin NPU failure routes to local backend instead of safe greeting`() {
        val result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = FailureNpuStandardRouteS1Provider.STATUS_FAILURE,
                reason = "adapter_failure:kotlin_conversation_product_route:test_failure",
                selectedModelName = "gemma-4-E2B-it_qualcomm_sm8750.litertlm",
                selectedModelFile = "/tmp/model.litertlm",
                npuModelEligible = true,
                inputPrompt = "こんにちは",
            ),
        )

        val decision = LocalInferenceOutputPolicy.evaluateNpu(
            userPrompt = "こんにちは",
            result = result,
            localStopRequested = false,
        )

        assertTrue(decision.shouldRunGenericFallback)
        assertNull(decision.transientFallback)
    }
}
