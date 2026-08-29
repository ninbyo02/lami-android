package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalConversationPolicyTest {
    @Test
    fun `history is bounded and shared with LiteRT and NPU routes`() {
        val turns = (1..14).map { index ->
            LocalConversationTurn(
                role = if (index % 2 == 0) LocalConversationRole.MODEL else LocalConversationRole.USER,
                text = "message-$index",
            )
        }

        val bounded = LocalConversationHistoryPolicy.bounded(turns)
        val npuContext = LocalConversationHistoryPolicy.npuContext(turns)

        assertEquals(LocalConversationHistoryPolicy.MAX_HISTORY_MESSAGES, bounded.size)
        assertEquals("message-3", bounded.first().text)
        assertTrue(npuContext.startsWith("ユーザー: message-3"))
        assertTrue(npuContext.endsWith("アシスタント: message-14"))
        assertTrue(!npuContext.contains("message-2\n"))
    }

    @Test
    fun `stable local sampler and thinking policy are explicit`() {
        assertEquals("lami_stable_v1", LocalConversationPolicy.SAMPLER_PROFILE)
        assertEquals(40, LocalConversationPolicy.SAMPLER_TOP_K)
        assertEquals(0.9, LocalConversationPolicy.SAMPLER_TOP_P, 0.0)
        assertEquals(0.3, LocalConversationPolicy.SAMPLER_TEMPERATURE, 0.0)
        assertEquals(42, LocalConversationPolicy.SAMPLER_SEED)
        assertEquals(false, LocalConversationPolicy.THINKING_ENABLED)
        assertEquals(
            false,
            LocalConversationPolicy.generationExtraContext["enable_thinking"],
        )
    }

    @Test
    fun `normal route delegates prompt templates to model metadata through Conversation API`() {
        assertEquals("model_metadata", LocalConversationPolicy.PROMPT_TEMPLATE_OWNER)
        assertEquals(
            "litert_lm_conversation_api",
            LocalConversationPolicy.PROMPT_TEMPLATE_EVALUATOR,
        )
        assertEquals(true, LocalConversationPolicy.CONVERSATION_API_USED)
        assertEquals(false, LocalConversationPolicy.APP_TEMPLATE_USED)
        assertEquals(true, LocalConversationPolicy.TEMPLATE_OWNERSHIP_UNIFIED)
        assertEquals(
            "prompt_template_owner=model_metadata " +
                "prompt_template_evaluator=litert_lm_conversation_api " +
                "conversation_api_used=true app_template_used=false " +
                "template_ownership_unified=true",
            LocalConversationPolicy.promptTemplateOwnershipDiagnostics(),
        )
    }
}
