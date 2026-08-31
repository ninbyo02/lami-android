package io.github.ninbyo02.lami.ui.screens.home

internal fun interface NpuConversationMemoryProvider {
    fun verifiedFacts(history: List<LocalConversationTurn>): List<String>
}

internal object EmptyNpuConversationMemoryProvider : NpuConversationMemoryProvider {
    override fun verifiedFacts(history: List<LocalConversationTurn>): List<String> = emptyList()
}

internal data class NpuConversationPrefacePlan(
    val systemInstruction: String,
    val initialTurns: List<LocalConversationTurn>,
    val currentUserPrompt: String,
    val verifiedMemoryFacts: List<String>,
) {
    val completedHistoryPairCount: Int
        get() = initialTurns.size / 2
}

internal object NpuConversationPrefacePlanner {
    const val MAX_RECENT_COMPLETED_PAIRS = 3
    const val MAX_VERIFIED_MEMORY_FACTS = 6
    private const val MAX_MEMORY_FACT_CHARS = 80

    fun plan(
        history: List<LocalConversationTurn>,
        currentUserPrompt: String,
        memoryProvider: NpuConversationMemoryProvider = EmptyNpuConversationMemoryProvider,
    ): NpuConversationPrefacePlan {
        val normalizedPrompt = currentUserPrompt.trim()
        require(normalizedPrompt.isNotBlank()) { "currentUserPrompt must not be blank" }

        val normalizedHistory = LocalConversationHistoryPolicy.bounded(history)
        val recentCompletedTurns = completedPairs(normalizedHistory)
            .takeLast(MAX_RECENT_COMPLETED_PAIRS)
            .flatMap { it }
        val verifiedFacts = memoryProvider.verifiedFacts(normalizedHistory)
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { it.take(MAX_MEMORY_FACT_CHARS) }
            .distinct()
            .take(MAX_VERIFIED_MEMORY_FACTS)

        return NpuConversationPrefacePlan(
            systemInstruction = buildSystemInstruction(verifiedFacts),
            initialTurns = recentCompletedTurns,
            currentUserPrompt = normalizedPrompt,
            verifiedMemoryFacts = verifiedFacts,
        )
    }

    private fun completedPairs(
        turns: List<LocalConversationTurn>,
    ): List<List<LocalConversationTurn>> = buildList {
        var pendingUser: LocalConversationTurn? = null
        turns.forEach { turn ->
            when (turn.role) {
                LocalConversationRole.USER -> pendingUser = turn
                LocalConversationRole.MODEL -> {
                    val user = pendingUser
                    if (user != null) {
                        add(listOf(user, turn))
                        pendingUser = null
                    }
                }
            }
        }
    }

    private fun buildSystemInstruction(verifiedFacts: List<String>): String {
        if (verifiedFacts.isEmpty()) return LocalConversationPolicy.SYSTEM_INSTRUCTION
        return buildString {
            append(LocalConversationPolicy.SYSTEM_INSTRUCTION)
            append(" 検証済み記憶:")
            append(verifiedFacts.joinToString(separator = "、"))
            append("。")
        }
    }
}
