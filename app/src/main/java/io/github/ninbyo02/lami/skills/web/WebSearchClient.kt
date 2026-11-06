package io.github.ninbyo02.lami.skills.web

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class WebSearchHit(val title: String, val url: String, val snippet: String)

data class WebSearchResponse(val query: String, val hits: List<WebSearchHit>)

interface WebSearchClient {
    suspend fun search(query: String): WebSearchResponse
}

enum class WebSearchFailureReason {
    INVALID_QUERY, BLOCKED, HTTP_ERROR, NETWORK_ERROR, RESPONSE_TOO_LARGE, INVALID_RESPONSE,
}

/** Contains no query, URL, response content, or underlying transport exception. */
class WebSearchException(val reason: WebSearchFailureReason) : IOException(
    when (reason) {
        WebSearchFailureReason.INVALID_QUERY -> "検索語は制御文字を含まない1〜200文字で指定してください。"
        WebSearchFailureReason.BLOCKED -> "検索サービスがアクセスを制限しています。時間をおいてお試しください。"
        WebSearchFailureReason.HTTP_ERROR -> "検索サービスから結果を取得できませんでした。"
        WebSearchFailureReason.NETWORK_ERROR -> "検索サービスに接続できませんでした。"
        WebSearchFailureReason.RESPONSE_TOO_LARGE -> "検索結果の応答が大きすぎるため処理を中止しました。"
        WebSearchFailureReason.INVALID_RESPONSE -> "検索結果の形式を確認できませんでした。"
    },
)

/**
 * Fetches the official non-JavaScript results once. No browser, cookies, provider
 * fallback, result-page fetch, or CAPTCHA solving is involved. Results are untrusted data.
 */
class DuckDuckGoWebSearchClient(
    client: OkHttpClient = OkHttpClient(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL,
) : WebSearchClient {
    private class Attempt {
        val started = AtomicBoolean(false)
    }

    private val http = client.newBuilder()
        .apply {
            // A shared application client may otherwise add credentials or log queries.
            interceptors().clear()
            networkInterceptors().clear()
        }
        .cookieJar(CookieJar.NO_COOKIES)
        .cache(null)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val attempt = chain.request().tag(Attempt::class.java)
            if (attempt == null || !attempt.started.compareAndSet(false, true)) {
                throw WebSearchException(WebSearchFailureReason.NETWORK_ERROR)
            }
            val response = chain.proceed(chain.request())
            // OkHttp 4.9 can follow 503 Retry-After:0 even with transport retries off.
            if (response.code == 503) response.newBuilder().removeHeader("Retry-After").build() else response
        }
        .build()

    init {
        // Override is solely for local fixtures. Production cannot silently change provider.
        require(baseUrl == DEFAULT_BASE_URL || (
            baseUrl.host in setOf("127.0.0.1", "::1") &&
                baseUrl.scheme in setOf("http", "https") &&
                baseUrl.username.isEmpty() && baseUrl.password.isEmpty() &&
                baseUrl.query == null && baseUrl.fragment == null
            )) { "検索サービスの設定を確認してください。" }
    }

    override suspend fun search(query: String): WebSearchResponse {
        if (query.length !in 1..200 || query.any { it.isISOControl() } || query.trim().isEmpty()) {
            throw WebSearchException(WebSearchFailureReason.INVALID_QUERY)
        }
        val normalized = query.trim()
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addQueryParameter("q", normalized).addQueryParameter("kl", "jp-jp").build())
            .header("Accept", "text/html")
            .header("Accept-Language", "ja,en;q=0.7")
            .header("User-Agent", "Lami/1.0 (Android; HTML search)")
            .tag(Attempt::class.java, Attempt())
            .build()
        val call = http.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(WebSearchException(WebSearchFailureReason.NETWORK_ERROR))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val result = try {
                            parseResponse(normalized, response)
                        } catch (error: WebSearchException) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                            return
                        } catch (_: IOException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(WebSearchException(WebSearchFailureReason.NETWORK_ERROR))
                            }
                            return
                        } catch (_: RuntimeException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(WebSearchException(WebSearchFailureReason.INVALID_RESPONSE))
                            }
                            return
                        }
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
            })
        }
    }

    private fun parseResponse(query: String, response: Response): WebSearchResponse {
        if (response.code in setOf(202, 403, 429)) fail(WebSearchFailureReason.BLOCKED)
        if (response.code != 200) fail(WebSearchFailureReason.HTTP_ERROR)
        val body = response.body ?: fail(WebSearchFailureReason.INVALID_RESPONSE)
        val contentType = body.contentType()
        if (contentType == null || !(contentType.type == "text" && contentType.subtype == "html" ||
                contentType.type == "application" && contentType.subtype == "xhtml+xml")) {
            fail(WebSearchFailureReason.INVALID_RESPONSE)
        }
        if (body.contentLength() > MAX_RESPONSE_BYTES) fail(WebSearchFailureReason.RESPONSE_TOO_LARGE)
        val source = body.source()
        source.request(MAX_RESPONSE_BYTES + 1)
        if (source.buffer.size > MAX_RESPONSE_BYTES) fail(WebSearchFailureReason.RESPONSE_TOO_LARGE)
        val html = source.readString(contentType.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8)
        val document = Jsoup.parse(html, DEFAULT_BASE_URL.toString())
        if (document.selectFirst(
                "#challenge-form, .anomaly-modal, #anomaly-modal, form[action*=anomaly], " +
                    "form[action*=captcha], input[name*=captcha], script[src*=anomaly.js]",
            ) != null || document.title().lowercase(Locale.ROOT).let {
                it.contains("captcha") || it.contains("verify you are human")
            }) {
            fail(WebSearchFailureReason.BLOCKED)
        }
        val cards = document.select(".result").filter {
            !it.hasClass("result--ad") && !it.hasClass("result--ads") && !it.hasClass("result--sponsored") &&
                it.selectFirst(".result__badge--ad, .badge--ad") == null
        }
        val hits = cards.mapNotNull { card ->
            val link = card.selectFirst("a.result__a") ?: return@mapNotNull null
            val title = cleanText(link.text(), 300)
            if (title.isEmpty()) return@mapNotNull null
            val url = publicResultUrl(link.attr("href")) ?: return@mapNotNull null
            WebSearchHit(title, url, cleanText(card.selectFirst(".result__snippet")?.text().orEmpty(), 1000))
        }.distinctBy { it.url }.take(MAX_RESULTS)
        if (hits.isEmpty()) {
            val noResults = document.selectFirst(".no-results, .no-results__message, .result--no-result, #no-results")
            if (noResults == null || document.selectFirst("a.result__a") != null) {
                fail(WebSearchFailureReason.INVALID_RESPONSE)
            }
        }
        return WebSearchResponse(query, hits)
    }

    private fun publicResultUrl(raw: String): String? {
        if (raw.length !in 1..8192 || unsafeUrlText(raw)) return null
        val initial = DEFAULT_BASE_URL.resolve(raw) ?: return null
        val url = if (isDuckDuckGo(initial.host) && initial.encodedPath in setOf("/l", "/l/")) {
            if (initial.queryParameterValues("uddg").size != 1) return null
            val destination = initial.queryParameter("uddg") ?: return null
            if (unsafeUrlText(destination)) return null
            destination.toHttpUrlOrNull() ?: return null
        } else initial
        if (url.scheme !in setOf("http", "https") || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            isDuckDuckGo(url.host) || !isPublicHost(url.host)) return null
        return url.newBuilder().fragment(null).build().toString()
    }

    private fun unsafeUrlText(value: String): Boolean =
        value.any { it.isISOControl() || it.isWhitespace() || it == '\\' } ||
            ENCODED_CONTROL.containsMatchIn(value) ||
            (if (value.startsWith("//")) value.drop(2) else value.substringAfter("://", ""))
                .substringBefore('/').substringBefore('?').substringBefore('#').contains('@')

    private fun isPublicHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.ROOT).removeSuffix(".")
        if (normalized.isEmpty() || ':' in normalized) return false // IPv6 literal results are unnecessary here.
        if (normalized.all { it.isDigit() || it == '.' }) {
            if (normalized.split('.').any { it.length > 1 && it.startsWith('0') }) return false
            val octets = normalized.split('.').map { it.toIntOrNull() ?: return false }
            if (octets.size != 4 || octets.any { it !in 0..255 }) return false
            val (a, b, c, _) = octets
            return a !in setOf(0, 10, 127) && a < 224 &&
                !(a == 100 && b in 64..127) && !(a == 169 && b == 254) &&
                !(a == 172 && b in 16..31) && !(a == 192 && b == 168) &&
                !(a == 192 && b == 0 && c in setOf(0, 2)) && !(a == 198 && b in setOf(18, 19)) &&
                !(a == 198 && b == 51 && c == 100) && !(a == 203 && b == 0 && c == 113)
        }
        if ('.' !in normalized || normalized.endsWith(".local") || normalized.endsWith(".localhost") ||
            normalized.endsWith(".internal") || normalized.endsWith(".lan") || normalized.endsWith(".home") ||
            normalized.endsWith(".localdomain") || normalized.endsWith(".home.arpa") ||
            normalized.endsWith(".test") || normalized.endsWith(".invalid") || normalized.endsWith(".onion")) return false
        // Exclude alternate numeric address spellings rather than guessing at their interpretation.
        if (normalized.split('.').all { it.matches(Regex("(?i)(?:[0-9]+|0x[0-9a-f]+)")) }) return false
        return normalized.split('.').all { label ->
            label.length in 1..63 && label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
        }
    }

    private fun isDuckDuckGo(host: String): Boolean = host.lowercase(Locale.ROOT).removeSuffix(".").let {
        it == "duckduckgo.com" || it.endsWith(".duckduckgo.com") || it == "duck.com" ||
            it.endsWith(".duck.com") || it == "ddg.gg" || it.endsWith(".ddg.gg")
    }

    private fun cleanText(text: String, limit: Int): String =
        text.filterNot { it.isISOControl() }.replace(Regex("\\s+"), " ").trim().take(limit)

    private fun fail(reason: WebSearchFailureReason): Nothing = throw WebSearchException(reason)

    companion object {
        private val DEFAULT_BASE_URL = "https://html.duckduckgo.com/html/".toHttpUrl()
        private val ENCODED_CONTROL = Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE)
        private const val MAX_RESPONSE_BYTES = 1024L * 1024L
        private const val MAX_RESULTS = 5
    }
}
