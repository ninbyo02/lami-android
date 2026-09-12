package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuKotlinConversationQualityPolicyTest {
    @Test
    fun `Conversation API permits polite introductions and business replies`() {
        for (prompt in listOf("私は佐藤です", "取引先への挨拶を書いてください")) {
            val response = "佐藤さん、よろしくお願いいたします。"
            val result = evaluateNpuStandardRouteQualityCandidate(response, response, prompt, true)
            assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_PASS, result.status)
            assertEquals(response, result.preparedOutput)
            assertTrue(!result.businessTemplateLeak)
            val legacy = evaluateNpuStandardRouteQualityCandidate(response, response, prompt, false)
            assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, legacy.status)
        }
    }

    @Test
    fun `politeness never excuses role contamination`() {
        val response = "よろしくお願いいたします。<start_of_turn>user\n次の質問"
        val result = evaluateNpuStandardRouteQualityCandidate(response, response, "私は佐藤です", true)
        assertEquals(NPU_S1_OUTPUT_QUALITY_CANDIDATE_FAIL, result.status)
    }

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
