package io.github.ninbyo02.lami.ui.screens.home

import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Message

@Suppress("unused")
private fun __compileProbeLiteRtLmTypes(
    engine: Engine?,
    conversation: Conversation?,
    message: Message?,
) {
    val sink = listOf(engine, conversation, message)
    check(sink.size >= 0)
}
