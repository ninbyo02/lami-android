package io.github.ninbyo02.lami.ui.screens.home

/** Admission estimate, not a tokenizer or a native output limit. */
internal data class NpuConversationBudget(
    val initialTurns: List<LocalConversationTurn>,
    val estimatedInputTokens: Int,
    val reservedOutputTokens: Int,
    val admitted: Boolean,
)

internal object NpuConversationBudgetPolicy {
    private const val TEMPLATE_ALLOWANCE = 64
    private const val MESSAGE_ALLOWANCE = 8

    // Deliberately overestimates typical Japanese text. Tokenization is model-dependent.
    internal fun estimate(text: String): Int =
        ((text.codePointCount(0, text.length).toLong() * 3 + 1) / 2)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    fun plan(
        history: List<LocalConversationTurn>,
        prompt: String,
        requestedOutputTokens: Int,
        totalTokens: Int = NPU_S1_PERSISTENT_ENGINE_OFFICIAL_TOTAL_TOKEN_LIMIT,
    ): NpuConversationBudget {
        val reserve = requestedOutputTokens.coerceIn(128, 256)
        var input = estimate(LocalConversationPolicy.SYSTEM_INSTRUCTION).toLong() +
            TEMPLATE_ALLOWANCE + MESSAGE_ALLOWANCE + estimate(prompt)
        val pairs = mutableListOf<List<LocalConversationTurn>>()
        var pending: LocalConversationTurn? = null
        for (turn in history) {
            if (turn.text.isBlank()) continue
            when (turn.role) {
                LocalConversationRole.USER -> pending = turn
                LocalConversationRole.MODEL -> {
                    pending?.let { pairs.add(listOf(it, turn)) }
                    pending = null
                }
            }
        }
        val selected = mutableListOf<LocalConversationTurn>()
        for (pair in pairs.asReversed()) {
            val cost = pair.sumOf { estimate(it.text).toLong() + MESSAGE_ALLOWANCE }
            if (input + cost + reserve > totalTokens ||
                selected.size + 2 > LocalConversationHistoryPolicy.MAX_HISTORY_MESSAGES) break
            selected.addAll(0, pair)
            input += cost
        }
        return NpuConversationBudget(
            selected.toList(), input.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), reserve,
            input + reserve <= totalTokens,
        )
    }
}
