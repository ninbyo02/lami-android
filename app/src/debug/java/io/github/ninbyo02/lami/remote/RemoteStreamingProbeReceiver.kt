package io.github.ninbyo02.lami.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

class RemoteStreamingProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        val app = context.applicationContext
        val provider = intent.getStringExtra("provider")?.lowercase() ?: "lemonade"
        val baseUrl = intent.getStringExtra("base_url") ?: if (provider == "ollama") "http://192.168.52.99:11434" else "http://192.168.52.99:13305"
        val model = intent.getStringExtra("model").orEmpty()
        val prompt = intent.getStringExtra("prompt") ?: "1から50まで数字だけを空白区切りで答えてください。"
        thread(name = "remote-stream-probe") {
            val out = File(app.filesDir, RESULT_FILE)
            runCatching { runProbe(provider, baseUrl, model, prompt, out) }
                .onFailure { e -> out.writeText("status=failed\nerror=${e.javaClass.name}:${e.message}\n") }
            pending.finish()
        }
    }

    private fun runProbe(provider: String, rawBaseUrl: String, requestedModel: String, prompt: String, out: File) {
        val start = SystemClock.elapsedRealtime()
        val events = mutableListOf<String>()
        var chunkCount = 0
        var contentChunkCount = 0
        var cumulativeChars = 0
        var firstContentMs: Long? = null
        fun record(kind: String, text: String = "") {
            val elapsed = SystemClock.elapsedRealtime() - start
            if (text.isNotEmpty()) {
                contentChunkCount++
                cumulativeChars += text.length
                if (firstContentMs == null) firstContentMs = elapsed
            }
            chunkCount++
            val safe = text.replace("\n", "\\n").take(80)
            events += "chunk=$chunkCount elapsed_ms=$elapsed kind=$kind len=${text.length} cumulative_chars=$cumulativeChars text=$safe"
            Log.i(TAG, events.last())
        }
        val normalized = rawBaseUrl.trimEnd('/')
        if (provider == "ollama") {
            val model = requestedModel.ifBlank { fetchFirstOllamaModel(normalized) }
            val body = JSONObject().put("model", model).put("stream", true).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            val connection = openPost("$normalized/api/chat", body.toString(), null)
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val json = JSONObject(line)
                    val text = json.optJSONObject("message")?.optString("content").orEmpty()
                    if (text.isNotEmpty()) record("content", text)
                    if (json.optBoolean("done", false)) record("done")
                }
            }
        } else {
            val apiBase = if (normalized.endsWith("/api/v1")) normalized else "$normalized/api/v1"
            val model = requestedModel.ifBlank { fetchFirstOpenAiModel(apiBase) }
            val body = JSONObject().put("model", model).put("stream", true).put("max_tokens", 96).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            val connection = openPost("$apiBase/chat/completions", body.toString(), "lemonade")
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (!line.startsWith("data:")) return@forEach
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") { record("done"); return@forEach }
                    val json = JSONObject(payload)
                    val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                    val content = delta?.takeIf { it.has("content") && !it.isNull("content") }?.optString("content").orEmpty()
                    val reasoning = delta?.takeIf { it.has("reasoning_content") && !it.isNull("reasoning_content") }?.optString("reasoning_content").orEmpty()
                    val text = content.ifEmpty { reasoning }
                    if (text.isNotEmpty()) record("content", text)
                }
            }
        }
        val total = SystemClock.elapsedRealtime() - start
        out.writeText(buildString {
            appendLine("status=success")
            appendLine("provider=$provider")
            appendLine("base_url=$normalized")
            appendLine("first_content_ms=${firstContentMs ?: -1}")
            appendLine("content_chunks=$contentChunkCount")
            appendLine("total_events=$chunkCount")
            appendLine("cumulative_chars=$cumulativeChars")
            appendLine("total_ms=$total")
            val contentTimes = events.filter { "kind=content" in it }.mapNotNull { Regex("elapsed_ms=(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull() }
            appendLine("distinct_content_timestamps=${contentTimes.distinct().size}")
            appendLine("true_streaming_observed=${contentChunkCount >= 2 && contentTimes.distinct().size >= 2}")
            events.forEach(::appendLine)
        })
    }

    private fun openPost(url: String, json: String, bearer: String?): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 120_000; doOutput = true
        setRequestProperty("Content-Type", "application/json")
        if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        outputStream.use { it.write(json.toByteArray()) }
        if (responseCode !in 200..299) error("HTTP $responseCode: ${errorStream?.bufferedReader()?.readText()}")
    }

    private fun fetchFirstOllamaModel(base: String): String = JSONObject(URL("$base/api/tags").readText()).getJSONArray("models").getJSONObject(0).getString("name")
    private fun fetchFirstOpenAiModel(base: String): String = JSONObject(URL("$base/models").readText()).getJSONArray("data").getJSONObject(0).getString("id")

    companion object {
        const val ACTION = "io.github.ninbyo02.lami.action.REMOTE_STREAMING_PROBE"
        const val RESULT_FILE = "remote_streaming_probe_result.txt"
        const val TAG = "RemoteStreamProbe"
    }
}
