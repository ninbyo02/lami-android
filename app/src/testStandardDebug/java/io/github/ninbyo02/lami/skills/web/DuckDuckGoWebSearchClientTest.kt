package io.github.ninbyo02.lami.skills.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DuckDuckGoWebSearchClientTest {
    private fun MockWebServer.endpoint(): HttpUrl = url("/html/").newBuilder().host("127.0.0.1").build()

    private fun card(url: String, title: String = "技術解説", snippet: String = "日本語の検索結果です", classes: String = ""): String =
        """<div class="result results_links web-result $classes"><h2><a class="result__a" href="$url">$title</a></h2><a class="result__snippet">$snippet</a></div>"""

    private fun page(vararg cards: String): String =
        """<!doctype html><html><head><title>検索 at DuckDuckGo</title></head><body><form action="/html/"><input name="q"></form><div id="links">${cards.joinToString("")}</div></body></html>"""

    private fun response(html: String): MockResponse = MockResponse().setHeader("Content-Type", "text/html; charset=UTF-8").setBody(html)

    private fun failure(block: suspend () -> Unit): WebSearchException = runBlocking {
        try {
            withTimeout(5000) { block() }
            fail("Expected a safe search failure")
            throw AssertionError()
        } catch (error: WebSearchException) {
            assertNull(error.cause)
            error
        }
    }

    @Test
    fun `Japanese result and encoded DuckDuckGo destination preserve title snippet and order`() = runBlocking {
        MockWebServer().use { server ->
            val destination = "https://developer.android.com/kotlin?hl=ja&source=search"
            val wrapped = "//duckduckgo.com/l/?uddg=" + URLEncoder.encode(destination, "UTF-8") + "&amp;rut=ignored"
            server.enqueue(response(page(card(wrapped, "Kotlin &amp; Android", "日本語 &lt;説明&gt;"), card("https://kotlinlang.org/docs/home.html"))))
            val result = DuckDuckGoWebSearchClient(baseUrl = server.endpoint()).search("  Kotlin 日本語  ")
            assertEquals("Kotlin 日本語", result.query)
            assertEquals(listOf(destination, "https://kotlinlang.org/docs/home.html"), result.hits.map { it.url })
            assertEquals("Kotlin & Android", result.hits[0].title)
            assertEquals("日本語 <説明>", result.hits[0].snippet)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("GET", request.method)
            assertEquals("Kotlin 日本語", request.requestUrl!!.queryParameter("q"))
            assertEquals("jp-jp", request.requestUrl!!.queryParameter("kl"))
            assertNull(request.getHeader("Cookie"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `results exclude ads deduplicate destinations and stop at five`() = runBlocking {
        MockWebServer().use { server ->
            val cards = listOf(card("https://ad.example.com/", classes = "result--ad"), card("https://result.example.com/1")) +
                (1..8).map { card("https://result.example.com/$it") }
            server.enqueue(response(page(*cards.toTypedArray())))
            val result = DuckDuckGoWebSearchClient(baseUrl = server.endpoint()).search("資料")
            assertEquals((1..5).map { "https://result.example.com/$it" }, result.hits.map { it.url })
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `public result URL validation rejects local credentials control and internal links`() = runBlocking {
        MockWebServer().use { server ->
            val rejected = listOf(
                "http://127.0.0.1/", "http://10.1.2.3/", "http://172.16.0.2/", "http://192.168.1.1/",
                "http://169.254.169.254/", "http://100.64.0.1/", "http://0.0.0.0/", "http://224.0.0.1/",
                "http://192.0.2.1/", "http://198.51.100.1/", "http://203.0.113.1/", "http://[::1]/",
                "http://2130706433/", "http://127.1/", "http://0177.0.0.1/", "http://0x7f.0.0.1/",
                "http://localhost/", "http://computer.local/", "http://metadata.google.internal/", "http://router.home.arpa/",
                "https://user:pass@example.com/", "https://@example.com/", "//@example.com/",
                "https://example.com/%0aheader", "https://example.com/has space", "javascript:alert(1)",
                "https://duckduckgo.com/about", "https://duckduckgo.com./about", "/html/?q=internal",
                "/l/?uddg=" + URLEncoder.encode("http://127.0.0.1/", "UTF-8"),
                "/l/?uddg=https%3A%2F%2Fexample.com&amp;uddg=https%3A%2F%2Fkotlinlang.org",
            )
            server.enqueue(response(page(*(rejected.map { card(it) } + card("https://www.android.com/docs#fragment")).toTypedArray())))
            val result = DuckDuckGoWebSearchClient(baseUrl = server.endpoint()).search("資料")
            assertEquals(listOf("https://www.android.com/docs"), result.hits.map { it.url })
        }
    }

    @Test
    fun `only explicit no-results markup can return empty success`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response(page("<div class=\"no-results\"><div class=\"no-results__message\">No results found for this query</div></div>")))
            val result = DuckDuckGoWebSearchClient(baseUrl = server.endpoint()).search("珍しい語")
            assertTrue(result.hits.isEmpty())
        }
    }

    @Test
    fun `layout changes and only unsafe links are failures rather than empty search`() {
        MockWebServer().use { server ->
            val client = DuckDuckGoWebSearchClient(baseUrl = server.endpoint())
            for (html in listOf("<html><body>unrecognized layout</body></html>", page(card("http://localhost/")))) {
                server.enqueue(response(html))
                assertEquals(WebSearchFailureReason.INVALID_RESPONSE, failure { client.search("資料") }.reason)
            }
        }
    }

    @Test
    fun `HTTP CAPTCHA and rate limit responses are blocked without following or retrying`() {
        MockWebServer().use { server ->
            val client = DuckDuckGoWebSearchClient(baseUrl = server.endpoint())
            for (code in listOf(202, 403, 429)) {
                server.enqueue(MockResponse().setResponseCode(code).setBody("private response query-secret"))
                val error = failure { client.search("query-secret") }
                assertEquals(WebSearchFailureReason.BLOCKED, error.reason)
                assertFalse(error.toString().contains("query-secret"))
                assertFalse(error.toString().contains(server.hostName))
            }
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun `HTTP 200 challenge form takes priority over apparent result links`() {
        MockWebServer().use { server ->
            server.enqueue(response(page("<form id=\"challenge-form\" action=\"//duckduckgo.com/anomaly.js\"></form>", card("https://www.android.com/"))))
            assertEquals(WebSearchFailureReason.BLOCKED, failure {
                DuckDuckGoWebSearchClient(baseUrl = server.endpoint()).search("資料")
            }.reason)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `redirect is not followed and unavailable status is not retried`() {
        MockWebServer().use { server ->
            val client = DuckDuckGoWebSearchClient(baseUrl = server.endpoint())
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/must-not-follow")))
            assertEquals(WebSearchFailureReason.HTTP_ERROR, failure { client.search("資料") }.reason)
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            assertEquals(WebSearchFailureReason.HTTP_ERROR, failure { client.search("資料") }.reason)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `JSON or a missing HTML content type is rejected`() {
        MockWebServer().use { server ->
            val client = DuckDuckGoWebSearchClient(baseUrl = server.endpoint())
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
            server.enqueue(MockResponse().setBody(page(card("https://www.android.com/"))))
            repeat(2) {
                assertEquals(WebSearchFailureReason.INVALID_RESPONSE, failure { client.search("資料") }.reason)
            }
        }
    }

    @Test
    fun `declared and chunked oversized HTML bodies stop at one MiB`() {
        MockWebServer().use { server ->
            val client = DuckDuckGoWebSearchClient(baseUrl = server.endpoint())
            val huge = "x".repeat(1024 * 1024 + 1)
            server.enqueue(response(huge))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setChunkedBody(huge, 4096))
            repeat(2) {
                assertEquals(WebSearchFailureReason.RESPONSE_TOO_LARGE, failure { client.search("資料") }.reason)
            }
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `invalid query never sends a request`() {
        MockWebServer().use { server ->
            val client = DuckDuckGoWebSearchClient(baseUrl = server.endpoint())
            for (query in listOf("", "   ", "x".repeat(201), "line\nbreak", "tab\tvalue", "null\u0000value")) {
                assertEquals(WebSearchFailureReason.INVALID_QUERY, failure { client.search(query) }.reason)
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `cancellation cancels the in-flight HTTP call rather than waiting for timeout`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val cancelled = CountDownLatch(1)
            val http = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.countDown() }
            }).build()
            val search = async(Dispatchers.Default) {
                DuckDuckGoWebSearchClient(http, server.endpoint()).search("資料")
            }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            withTimeout(2000) { search.cancelAndJoin() }
            assertTrue(cancelled.await(1, TimeUnit.SECONDS))
            assertTrue(search.isCancelled)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `transport failures are redacted typed errors without retry`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val error = failure { DuckDuckGoWebSearchClient(baseUrl = server.endpoint()).search("query-secret") }
            assertEquals(WebSearchFailureReason.NETWORK_ERROR, error.reason)
            assertFalse(error.toString().contains("query-secret"))
            assertFalse(error.toString().contains(server.hostName))
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `constructor refuses arbitrary provider override with a safe fixed message`() {
        for (url in listOf("https://example.com/html/?query-secret", "http://localhost:1234/", "http://127.0.0.1/?query-secret")) {
            try {
                DuckDuckGoWebSearchClient(baseUrl = url.toHttpUrl())
                fail("Expected provider override rejection")
            } catch (error: IllegalArgumentException) {
                assertEquals("検索サービスの設定を確認してください。", error.message)
                assertFalse(error.toString().contains("query-secret"))
            }
        }
    }
}
