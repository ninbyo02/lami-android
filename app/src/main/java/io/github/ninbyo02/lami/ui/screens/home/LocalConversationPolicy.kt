package io.github.ninbyo02.lami.ui.screens.home

import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig

internal object LocalConversationPolicy {
    const val SYSTEM_INSTRUCTION =
        "あなたは端末内で動作するアシスタントです。ユーザーが別の言語を明示的に求めない限り、自然で簡潔な日本語で回答してください。"

    val samplerConfig: SamplerConfig
        get() = SamplerConfig(
            topK = 40,
            topP = 0.9,
            temperature = 0.3,
            seed = 42,
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
        extraContext = mapOf("enable_thinking" to false),
    )
}
