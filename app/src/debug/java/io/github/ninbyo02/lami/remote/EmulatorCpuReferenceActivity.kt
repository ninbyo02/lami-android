package io.github.ninbyo02.lami.remote

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Debug-only, real host CPU inference. Does not emulate Qualcomm hardware. */
class EmulatorCpuReferenceActivity : ComponentActivity() {
    private var job: Job? = null
    @Volatile private var connection: HttpURLConnection? = null
    private lateinit var output: TextView
    private lateinit var prompt: EditText
    private lateinit var model: EditText
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("enable_main_chat", false)) { openMainChat(true); return }
        if (intent.getBooleanExtra("disable_main_chat", false)) { openMainChat(false); return }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        panel.addView(TextView(this).apply { text = "PC CPU reference / NPU未使用\nエミュレータ専用: 10.0.2.2:11434\n任意入力を実際に推論します。NPUモデルの数値一致・速度は検証しません。" })
        model = EditText(this).apply { setSingleLine(); hint = "PC Ollama model"; setText(intent.getStringExtra("model") ?: "sam860/LFM2:350m") }
        prompt = EditText(this).apply { hint = "入力"; setText(intent.getStringExtra("prompt") ?: "こんにちは。短く自己紹介してください。") }
        panel.addView(model); panel.addView(prompt)
        panel.addView(Button(this).apply { text = "通常チャットでCPU開発"; setOnClickListener { openMainChat(true, model.text.toString()) } })
        panel.addView(Button(this).apply { text = "CPU開発を無効化"; setOnClickListener { openMainChat(false) } })
        panel.addView(Button(this).apply { text = "CPUで推論"; setOnClickListener { infer() } })
        panel.addView(Button(this).apply { text = "停止"; setOnClickListener { cancelInference(); output.text = "中断しました"; save(JSONObject().put("status", "cancelled")) } })
        output = TextView(this).apply { setTextIsSelectable(true) }
        panel.addView(output)
        setContentView(ScrollView(this).apply { addView(panel) })
        if (intent.getBooleanExtra("auto_run", false) && savedInstanceState == null) infer()
    }

    private fun openMainChat(enabled: Boolean, selectedModel: String? = null) {
        check(io.github.ninbyo02.lami.ui.screens.home.EmulatorMainChatBackend.isEmulator()) { "Emulator only" }
        cancelInference()
        getSharedPreferences(io.github.ninbyo02.lami.ui.screens.home.EmulatorMainChatBackend.PREFS, MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled)
            .putInt("host_port", intent.getIntExtra("host_port", 11434).also { require(it in 1024..65535) })
            .putString("model", selectedModel ?: intent.getStringExtra("model") ?: "sam860/LFM2:350m").apply()
        startActivity(android.content.Intent(this, io.github.ninbyo02.lami.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun cancelInference() {
        generation++
        job?.cancel(); job = null
        connection?.disconnect(); connection = null
    }

    private fun infer() {
        cancelInference()
        val request = generation
        val text = prompt.text.toString()
        val selectedModel = model.text.toString().trim()
        if (text.isBlank() || selectedModel.isBlank()) { output.text = "入力とモデル名が必要です"; return }
        output.text = "CPUで推論中…"
        save(JSONObject().put("status", "running").put("model", selectedModel))
        job = lifecycleScope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    val started = android.os.SystemClock.elapsedRealtime()
                    val body = JSONObject().put("model", selectedModel).put("stream", true)
                        .put("think", false).put("keep_alive", 0)
                        .put("options", JSONObject().put("num_gpu", 0).put("num_ctx", 2048).put("num_predict", 64).put("temperature", 0))
                        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text)))
                    val conn = URL("http://10.0.2.2:11434/api/chat").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"; conn.connectTimeout = 10000; conn.readTimeout = 120000; conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    // Register on the main thread so cancellation cannot miss a newly created connection.
                    withContext(Dispatchers.Main) { if (request != generation) throw CancellationException(); connection = conn }
                    try {
                        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                        check(conn.responseCode in 200..299) { "HTTP " + conn.responseCode }
                        val result = StringBuilder()
                        var count = 0
                        var done = false
                        var firstMs: Long? = null
                        var finishReason = ""
                        conn.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                if (line.isNotBlank()) {
                                    val event = JSONObject(line)
                                    check(!event.has("error")) { event.optString("error") }
                                    val chunk = event.optJSONObject("message")?.optString("content").orEmpty()
                                    if (chunk.isNotEmpty()) {
                                        if (firstMs == null) firstMs = android.os.SystemClock.elapsedRealtime() - started
                                        count++; result.append(chunk)
                                        withContext(Dispatchers.Main) { if (request == generation) output.text = result.toString() }
                                    }
                                    if (event.optBoolean("done")) { done = true; finishReason = event.optString("done_reason") }
                                }
                            }
                        }
                        check(done && result.isNotBlank()) { "Incomplete or empty inference response" }
                        JSONObject().put("status", "success").put("model", selectedModel)
                            .put("input_sha256", MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
                            .put("response", result.toString()).put("content_chunks", count)
                            .put("first_content_ms", firstMs).put("total_ms", android.os.SystemClock.elapsedRealtime() - started)
                            .put("done_reason", finishReason).put("requested_num_gpu", 0)
                    } finally {
                        conn.disconnect()
                        withContext(NonCancellable + Dispatchers.Main) { if (connection === conn) connection = null }
                    }
                }
                if (request == generation) { save(report); output.text = report.getString("response") + "\n\nPC CPU reference / NPU未使用" }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (request == generation) { output.text = "失敗: " + failure.message; save(JSONObject().put("status", "failed").put("error", failure.toString())) }
            }
        }
    }

    private fun save(report: JSONObject) {
        report.put("run_id", intent.getStringExtra("run_id").orEmpty())
            .put("backend", "host_cpu_reference").put("npu_execution", "not_run")
            .put("npu_numerical_parity", "not_verified")
        File(filesDir, "emulator_cpu_reference.json").writeText(report.toString(2))
    }

    override fun onDestroy() { cancelInference(); super.onDestroy() }
}
