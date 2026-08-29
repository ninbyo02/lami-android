package io.github.ninbyo02.lami.ui.screens.home

internal enum class LocalConversationRole {
    USER,
    MODEL,
}

internal data class LocalConversationTurn(
    val role: LocalConversationRole,
    val text: String,
)

internal object LocalConversationHistoryPolicy {
    const val MAX_HISTORY_MESSAGES = 12
    private const val MAX_NPU_CONTEXT_CHARS = 4_000

    fun bounded(initialTurns: List<LocalConversationTurn>): List<LocalConversationTurn> =
        initialTurns
            .mapNotNull { turn ->
                turn.text.trim().takeIf(String::isNotBlank)?.let { text -> turn.copy(text = text) }
            }
            .takeLast(MAX_HISTORY_MESSAGES)

    fun npuContext(initialTurns: List<LocalConversationTurn>): String =
        bounded(initialTurns)
            .joinToString("\n") { turn ->
                val label = if (turn.role == LocalConversationRole.USER) "ユーザー" else "アシスタント"
                "$label: ${turn.text}"
            }
            .takeLast(MAX_NPU_CONTEXT_CHARS)
}
