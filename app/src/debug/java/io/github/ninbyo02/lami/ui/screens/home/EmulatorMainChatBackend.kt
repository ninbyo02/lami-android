package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import android.os.Build
import android.os.SystemClock
import io.github.ninbyo02.lami.ui.components.LocalInferenceEngineState
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** Explicit emulator-only dependency injection; never a silent NPU fallback. */
internal object EmulatorMainChatBackend {
    const val PREFS = "emulator_cpu_development"
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
    fun isEmulator(): Boolean = Build.HARDWARE in setOf("ranchu", "goldfish") &&
        Build.SUPPORTED_ABIS.any { it == "x86_64" || it == "x86" }
    fun isEnabled(context: Context): Boolean = isEmulator() &&
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun modelName(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("model", "sam860/LFM2:350m").orEmpty()

    suspend fun run(context: Context, prompt: String, initialTurns: List<LocalConversationTurn>, onPartial: (String) -> Unit): LocalInferenceRunResult? {
        if (!isEnabled(context)) return null
        require(initialTurns.none { it.imageFilePaths.isNotEmpty() }) { "CPU reference currently supports text-only history" }
        val requestId = UUID.randomUUID().toString()
        val inputHash = MessageDigest.getInstance("SHA-256").digest(prompt.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val start = SystemClock.elapsedRealtime()
        val model = modelName(context)
        val port = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("host_port", 11434)
        require(port in 1024..65535)
        val messages = JSONArray()
        LocalConversationHistoryPolicy.bounded(initialTurns).forEach { turn ->
            messages.put(JSONObject().put("role", if (turn.role == LocalConversationRole.USER) "user" else "assistant").put("content", turn.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        val body = JSONObject().put("model", model).put("messages", messages).put("stream", true)
            .put("think", false).put("keep_alive", 0)
            .put("options", JSONObject().put("num_gpu", 0).put("num_ctx", 2048).put("num_predict", 128).put("temperature", 0))
        val request = Request.Builder().url("http://10.0.2.2:" + port + "/api/chat")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val text = StringBuilder()
        var first: Long? = null
        var chunks = 0
        var done = false
        var referenceBackend: JSONObject? = null
        fun save(status: String, error: String? = null) {
            val report = JSONObject().put("status", status).put("backend", "host_cpu_reference")
                .put("request_id", requestId).put("input_sha256", inputHash)
                .put("model", model).put("response", text.toString()).put("history_messages", messages.length() - 1)
                .put("npu_execution", "not_run").put("npu_numerical_parity", "not_verified")
                .put("requested_num_gpu", 0).put("host_port", port).put("content_chunks", chunks)
                .put("reference_backend", referenceBackend)
                .put("first_content_ms", first?.minus(start)).put("total_ms", SystemClock.elapsedRealtime() - start)
            if (error != null) report.put("error", error)
            val temporary = File(context.filesDir, "emulator_main_chat_reference.json.tmp")
            temporary.writeText(report.toString(2))
            check(temporary.renameTo(File(context.filesDir, "emulator_main_chat_reference.json")))
        }
        save("running")
        try {
            callbackFlow<String> {
                val call = client.newCall(request)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { close(e) }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use {
                                check(it.isSuccessful) { "HTTP " + it.code }
                                val stream = requireNotNull(it.body).source()
                                while (!stream.exhausted()) {
                                    val line = stream.readUtf8Line() ?: break
                                    if (line.isBlank()) continue
                                    val event = JSONObject(line)
                                    check(!event.has("error")) { event.optString("error") }
                                    val chunk = event.optJSONObject("message")?.optString("content").orEmpty()
                                    if (chunk.isNotEmpty()) sendChunk(chunk)
                                    if (event.optBoolean("done")) { referenceBackend = event.optJSONObject("reference_backend"); done = true; break }
                                }
                                check(done) { "Inference stream ended without a terminal event" }
                            }
                            close()
                        } catch (e: Exception) { close(e) }
                    }
                    private fun sendChunk(chunk: String) {
                        // Backpressure keeps every generated delta; cancellation closes the socket.
                        this@callbackFlow.trySendBlocking(chunk).getOrThrow()
                    }
                })
                awaitClose { call.cancel() }
            }.collect { chunk ->
                if (first == null) first = SystemClock.elapsedRealtime()
                chunks++; text.append(chunk); onPartial(text.toString())
            }
            check(text.isNotBlank()) { "Empty inference response" }
            save("success")
            val end = SystemClock.elapsedRealtime()
            return LocalInferenceRunResult(
                state = LocalInferenceEngineState.READY, response = text.toString(),
                trace = LocalInferenceTrace(
                    localModelDisplayName = "PC CPU reference: " + model,
                    selectedAssistantResponseSource = "host_cpu_reference",
                    requestedPreferredBackend = "HOST_CPU_REFERENCE", appliedPreferredBackend = "HOST_CPU_REFERENCE",
                    preferredBackendApplyResult = "explicit_emulator_development_backend",
                    localTraceStartElapsedRealtimeMs = start, localTraceFirstResponseElapsedRealtimeMs = first,
                    localTraceCompletedElapsedRealtimeMs = end, wallClockTotalInferenceDurationNs = (end - start) * 1_000_000,
                    realPartialReceived = chunks > 0, realPartialChunkCount = chunks,
                    localFailureDiagnosticsText = "PC CPU reference; NPU not run; numerical parity not verified",
                ),
            )
        } catch (e: CancellationException) { save("cancelled"); throw e }
        catch (e: Exception) { save("failed", e.toString()); throw e }
    }
}
