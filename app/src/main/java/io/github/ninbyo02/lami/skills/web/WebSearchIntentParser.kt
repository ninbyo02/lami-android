package io.github.ninbyo02.lami.skills.web

internal sealed interface WebSearchIntent {
    data class Search(val query: String) : WebSearchIntent
    data object Rejected : WebSearchIntent
}

/** Match only the user's explicit request, never model output or text inside search results. */
internal object WebSearchIntentParser {
    const val MAX_QUERY_LENGTH = 200
    const val REJECTED_REPLY = "Web検索は、添付なしで「Webで○○を検索して」または「/web ○○」と指定してください。引用・否定・質問は検索しません。"
    private val indicator = Regex("/web(?:\\b|\\s)|(?:Web|DuckDuckGo)で", RegexOption.IGNORE_CASE)
    private val command = Regex("^/web[ \\t]+(.+)$", RegexOption.IGNORE_CASE)
    private val japanese = Regex("^(?:Web|DuckDuckGo)で(.+?)を検索して(?:ください|下さい)?[。!！]?$", RegexOption.IGNORE_CASE)
    private val forbidden = Regex("検索しない|検索しなく|検索しません|検索するな|検索せず|検索は不要|検索をやめ|検索しないで|例文|例えば|例[:：]")

    fun parse(utterance: String, hasAttachments: Boolean = false): WebSearchIntent? {
        if (!indicator.containsMatchIn(utterance)) return null
        val text = utterance.trim()
        if (hasAttachments || text.length > 260 || text.any { it.isISOControl() } ||
            text.any { it in "「」『』\"'“”‘’?？" } || forbidden.containsMatchIn(text)) return WebSearchIntent.Rejected
        val query = (command.matchEntire(text) ?: japanese.matchEntire(text))?.groupValues?.get(1)?.trim()
            ?: return WebSearchIntent.Rejected
        if (!validQuery(query) || query.contains("を検索して") || indicator.containsMatchIn(query)) return WebSearchIntent.Rejected
        return WebSearchIntent.Search(query)
    }

    fun validQuery(query: String): Boolean = query.length in 1..MAX_QUERY_LENGTH &&
        query == query.trim() && query.none { it.isISOControl() }
}
