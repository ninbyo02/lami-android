package io.github.ninbyo02.lami.ui.screens.home

import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig

internal object LocalConversationPolicy {
    const val SYSTEM_INSTRUCTION =
        "あなたは端末内で動作するアシスタントです。ユーザーが別の言語を明示的に求めない限り、自然で簡潔な日本語で回答してください。"
    const val PROMPT_TEMPLATE_OWNER = "model_metadata"
    const val PROMPT_TEMPLATE_EVALUATOR = "litert_lm_conversation_api"
    const val CONVERSATION_API_USED = true
    const val APP_TEMPLATE_USED = false
    const val TEMPLATE_OWNERSHIP_UNIFIED = true
    const val SAMPLER_PROFILE = "lami_stable_v1"
    const val SAMPLER_TOP_K = 40
    const val SAMPLER_TOP_P = 0.9
    const val SAMPLER_TEMPERATURE = 0.3
    const val SAMPLER_SEED = 42
    const val THINKING_ENABLED = false

    val generationExtraContext: Map<String, Any> =
        mapOf("enable_thinking" to THINKING_ENABLED)

    fun promptTemplateOwnershipDiagnostics(): String = listOf(
        "prompt_template_owner=$PROMPT_TEMPLATE_OWNER",
        "prompt_template_evaluator=$PROMPT_TEMPLATE_EVALUATOR",
        "conversation_api_used=$CONVERSATION_API_USED",
        "app_template_used=$APP_TEMPLATE_USED",
        "template_ownership_unified=$TEMPLATE_OWNERSHIP_UNIFIED",
    ).joinToString(" ")

    val samplerConfig: SamplerConfig
        get() = SamplerConfig(
            topK = SAMPLER_TOP_K,
            topP = SAMPLER_TOP_P,
            temperature = SAMPLER_TEMPERATURE,
            seed = SAMPLER_SEED,
        )

    fun conversationConfig(
        initialTurns: List<LocalConversationTurn> = emptyList(),
        samplerOverride: SamplerConfig? = null,
    ): ConversationConfig = ConversationConfig(
        systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
        initialMessages = LocalConversationHistoryPolicy.bounded(initialTurns).map { turn ->
            when (turn.role) {
                LocalConversationRole.USER -> Message.user(turn.text)
                LocalConversationRole.MODEL -> Message.model(turn.text)
            }
        },
        samplerConfig = samplerOverride ?: samplerConfig,
    )
}
