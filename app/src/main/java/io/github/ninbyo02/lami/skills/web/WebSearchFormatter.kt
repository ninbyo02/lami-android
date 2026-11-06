package io.github.ninbyo02.lami.skills.web

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Search snippets are untrusted text. This formatter never fetches a result URL. */
internal object WebSearchFormatter {
    fun format(response: WebSearchResponse): String {
        val hits = response.hits.mapNotNull { hit ->
            safeLink(hit.url)?.let { url -> Triple(escape(hit.title, 160), url, escape(hit.snippet, 360)) }
        }.filter { it.first.isNotBlank() }.distinctBy { it.second }.take(5)
        return buildString {
            append("Web検索結果（DuckDuckGo）: ").append(escape(response.query, 200)).append("\n\n")
            if (hits.isEmpty()) append("表示できる検索結果は見つかりませんでした。\n")
            hits.forEachIndexed { index, (title, url, snippet) ->
                append(index + 1).append(". [").append(title).append("](<").append(url).append(">)\n")
                if (snippet.isNotBlank()) append("   ").append(snippet).append("\n")
                append("   出典: <").append(url).append(">\n\n")
            }
            append("検索結果の抜粋です。掲載内容や公開日時の真偽は確認していません。リンク先の本文は自動取得していません。")
        }
    }

    private fun escape(value: String, limit: Int): String = buildString {
        val plain = value.map { if (it.isISOControl()) ' ' else it }.joinToString("")
            .replace(Regex("\\s+"), " ").trim().take(limit)
        plain.forEach { character ->
            if (character in "\\`*_{}[]()#+-.!|><&") append('\\')
            append(character)
        }
    }

    private fun safeLink(value: String): String? {
        if (value.length !in 1..2048 || value.any { it.isISOControl() || it.isWhitespace() }) return null
        val url = value.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val host = url.host.lowercase()
        if (!host.contains('.') || host.all { it.isDigit() || it == '.' } || host.contains(':') ||
            host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return null
        return url.toString().replace("(", "%28").replace(")", "%29")
            .replace("<", "%3C").replace(">", "%3E").replace("\\", "%5C")
    }
}
