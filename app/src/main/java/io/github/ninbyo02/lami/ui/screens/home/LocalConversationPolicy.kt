package io.github.ninbyo02.lami.ui.screens.home

import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig

internal object LocalConversationPolicy {
    const val SYSTEM_INSTRUCTION =
        "あなたは端末内で動作するアシスタントです。ユーザーが別の言語を明示的に求めない限り、自然で簡潔な日本語で回答してください。"
    const val SAMPLER_PROFILE = "lami_stable_v1"
    const val SAMPLER_TOP_K = 40
    const val SAMPLER_TOP_P = 0.9
    const val SAMPLER_TEMPERATURE = 0.3
    const val SAMPLER_SEED = 42
    const val THINKING_ENABLED = false

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
        extraContext = mapOf("enable_thinking" to THINKING_ENABLED),
    )
}
