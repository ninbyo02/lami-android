package io.github.ninbyo02.lami.ui.screens.home

/** Replace only recount metadata; preserve the completed response's route and timing. */
internal fun mergePostTerminalTokenCountDiagnostics(
    sourceSummary: String,
    snapshot: LocalInferenceMeasuredTokenSnapshot,
): String {
    val exact = snapshot.inputTokens != null && snapshot.outputTokens != null
    val fields = linkedMapOf(
        "tokenizer_count_started_at_elapsed_ms" to snapshot.tokenizerCountStartedAtElapsedMs,
        "tokenizer_count_finished_at_elapsed_ms" to snapshot.tokenizerCountFinishedAtElapsedMs,
        "tokenizer_count_duration_ms" to snapshot.tokenizerCountDurationMs,
        "stats_final_display_used_tokenizer_tokens" to exact,
        "tokenizer_count_delayed_stats_update" to true,
        "stats_token_metrics_final_source" to if (exact) "tokenizer_tokens" else "estimated_tokens",
        "tokenizer_recount_policy" to "post_terminal_no_native_retention",
        "tokenizer_result_cache_hit" to (snapshot.mediaPipeTokenizerSummary?.contains("result cache hit: true") == true),
    )
    val retained = sourceSummary.lineSequence().filter { it.substringBefore('=') !in fields }.toList()
    return (retained + fields.map { (key, value) -> "$key=${value ?: "unavailable"}" }).joinToString("\n")
}
