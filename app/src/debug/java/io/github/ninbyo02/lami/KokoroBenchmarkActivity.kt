package io.github.ninbyo02.lami

import android.app.Activity
import android.content.Intent
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.ninbyo02.lami.tts.LamiKokoroMediaPipeSession
import io.github.ninbyo02.lami.tts.LamiKokoroVoiceFactory
import io.github.ninbyo02.lami.tts.LamiPcmPlayer
import io.github.ninbyo02.lami.tts.LamiResidentPcmSession
import io.github.ninbyo02.lami.tts.LamiSupertonicSession
import io.github.ninbyo02.lami.tts.LamiSupertonicVoiceFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Debug-only, offline probe. A repeat loop uses ONE resident engine instance. */
class KokoroBenchmarkActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var active: LamiResidentPcmSession? = null
    private lateinit var status: TextView
    private lateinit var input: EditText
    private var running = false
    private var benchmarkJob: Job? = null
    private val isSupertonic get() = intent.getStringExtra("engine") == "supertonic3"
    private val engineName get() = if (isSupertonic) "Supertonic 3" else "Kokoro LiteRT"
    private lateinit var voices: Spinner
    private lateinit var stepsInput: EditText
    private fun speakerId() = if (::voices.isInitialized) voices.selectedItemPosition else intent.getIntExtra("sid", 0)
    private fun steps() = if (::stepsInput.isInitialized) stepsInput.text.toString().toIntOrNull() ?: 5 else intent.getIntExtra("steps", 5)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        status = TextView(this).apply { text = "$engineName / オフライン比較\nモデル: ${modelFile().absolutePath}" }
        input = EditText(this).apply { setText(intent.getStringExtra("text") ?: "こんにちは。今日の予定を一緒に確認しましょう。") }
        layout.addView(status); layout.addView(input)
        if (isSupertonic) {
            layout.addView(TextView(this).apply { text = "女性プリセット / 推論ステップ数（速度・品質）" })
            voices = Spinner(this).apply {
                adapter = ArrayAdapter(this@KokoroBenchmarkActivity, android.R.layout.simple_spinner_dropdown_item,
                    listOf("F1", "F2", "F3", "F4", "F5"))
                setSelection(intent.getIntExtra("sid", 0).coerceIn(0, 4))
            }
            stepsInput = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(intent.getIntExtra("steps", 5).toString()) }
            layout.addView(voices); layout.addView(stepsInput)
        }
        fun button(title: String, action: () -> Unit) = Button(this).apply { text = title; setOnClickListener { action() }; layout.addView(this) }
        button("Kokoroの比較画面") { openEngine("kokoro") }
        button("Supertonic 3の比較画面") { openEngine("supertonic3") }
        button("合成・保存して試聴（同じモデルで3回）") { runBenchmark(play = true, repeats = 3) }
        button("停止") {
            if (running) status.text = if (isSupertonic) "停止待ち（現在の文の推論完了を待ちます）" else "停止処理中…"
            benchmarkJob?.cancel(); active?.cancel()
        }
        button("チャットで${engineName}を使用（次回起動から）") {
            val model = modelFile()
            val present = if (isSupertonic) LamiSupertonicSession.FILES.all { model.resolve(it).isFile } else model.isFile
            if (!present) { status.text = "モデルがありません: $model"; return@button }
            if (isSupertonic) {
                if (intent.getIntExtra("threads", 4) !in 1..16) { status.text = "スレッド数は1〜16です"; return@button }
                if (steps() !in 1..30) { status.text = "ステップ数は1〜30です"; return@button }
                getSharedPreferences(LamiSupertonicVoiceFactory.PREFS, MODE_PRIVATE).edit()
                    .putBoolean("enabled", true).putString("model_path", model.absolutePath)
                    .putInt("sid", speakerId()).putInt("steps", steps()).putInt("threads", intent.getIntExtra("threads", 4)).apply()
                getSharedPreferences(LamiKokoroVoiceFactory.PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            } else {
                getSharedPreferences(LamiKokoroVoiceFactory.PREFS, MODE_PRIVATE).edit()
                    .putBoolean("enabled", true).putString("model_path", model.absolutePath)
                    .putBoolean("gpu", intent.getStringExtra("backend") == "gpu")
                    .putString("voice", intent.getStringExtra("voice") ?: "jf_tebukuro").apply()
                getSharedPreferences(LamiSupertonicVoiceFactory.PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            }
            status.text = "$engineName を選択しました。LAMIを再起動してください。"
        }
        button("チャットを標準の音声選択に戻す（次回起動から）") {
            getSharedPreferences(LamiKokoroVoiceFactory.PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            getSharedPreferences(LamiSupertonicVoiceFactory.PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            status.text = "標準の音声選択に戻しました。LAMIを再起動してください。"
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        if (intent.hasExtra("text") && intent.getBooleanExtra("auto_run", true)) runBenchmark(intent.getBooleanExtra("play", false), intent.getIntExtra("repeats", 1))
    }

    private fun modelFile() = (intent.getStringExtra("model_dir") ?: intent.getStringExtra("model_path"))?.let(::File)
        ?: if (isSupertonic) LamiSupertonicVoiceFactory.modelDirectory(this) else LamiKokoroVoiceFactory.modelFile(this)

    private fun openEngine(name: String) {
        if (running) return
        startActivity(Intent(this, KokoroBenchmarkActivity::class.java)
            .putExtra("engine", name).putExtra("text", input.text.toString()).putExtra("auto_run", false))
        finish()
    }

    private fun runBenchmark(play: Boolean, repeats: Int) {
        if (running) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        running = true
        benchmarkJob = scope.launch {
            var session: LamiResidentPcmSession? = null
            var directory: File? = null
            try {
                val id = intent.getStringExtra("request_id") ?: "manual-${SystemClock.elapsedRealtime()}"
                require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid request_id" }
                val prefix = if (isSupertonic) "supertonic3" else "kokoro"
                val output = getExternalFilesDir(null)!!.resolve("$prefix/bench/$id")
                check(!output.exists()) { "request_id already exists" }
                check(output.mkdirs()) { "Cannot create app-owned output directory: $output" }
                directory = output
                var metric: JSONObject? = null
                session = if (isSupertonic) LamiSupertonicSession(modelFile(), speakerId = speakerId(), steps = steps(),
                    threads = intent.getIntExtra("threads", 4), measured = { metric = it })
                else LamiKokoroMediaPipeSession(applicationContext, modelFile(),
                    gpu = intent.getStringExtra("backend") == "gpu",
                    voice = intent.getStringExtra("voice") ?: "jf_tebukuro", measured = { metric = it })
                active = session
                val current = session
                if (intent.getBooleanExtra("prewarm", false)) {
                    status.text = "モデルを事前読込中…"
                    current.prewarm()
                }
                for (repeat in 1..repeats.coerceIn(1, 20)) {
                    status.text = "合成中 $repeat / ${repeats.coerceIn(1, 20)}"
                    val before = withContext(Dispatchers.Default) { pssKb() }
                    metric = null
                    val pcm = current.synthesize(text)
                    val row = checkNotNull(metric)
                    row.put("schema_version", 1).put("record_type", "measurement").put("status", "ok")
                        .put("request_id", id).put("repeat", repeat).put("text", text)
                        .put("text_id", intent.getStringExtra("text_id") ?: "manual")
                        .put("device_fingerprint", Build.FINGERPRINT).put("device_model", Build.MODEL)
                        .put("soc_model", Build.SOC_MODEL).put("android_sdk", Build.VERSION.SDK_INT)
                        .put("llm_condition", "unknown").put("filesystem_cache_state", "unknown")
                        .put("pss_before_kb", before).put("pss_after_kb", withContext(Dispatchers.Default) { pssKb() })
                    val wav = output.resolve("repeat-$repeat.wav")
                    withContext(Dispatchers.IO) { writeWav(wav, pcm) }
                    row.put("wav_path", wav.absolutePath)
                    if (play) LamiPcmPlayer.play(pcm) { row.put("play_return_at_ns", SystemClock.elapsedRealtimeNanos()) }
                    else row.put("play_return_at_ns", JSONObject.NULL)
                    withContext(Dispatchers.IO) { output.resolve("metrics.jsonl").appendText(row.toString() + "\n") }
                    Log.i(TAG, row.toString())
                }
                val done = JSONObject().put("status", "ok").put("request_id", id)
                withContext(Dispatchers.IO) { writeDone(output, done) }
                status.text = "完了: ${output.absolutePath}"
            } catch (failure: Exception) {
                if (failure is CancellationException) {
                    status.text = "停止しました。"
                    withContext(NonCancellable + Dispatchers.IO) {
                        directory?.let { writeDone(it, JSONObject().put("status", "cancelled")) }
                    }
                    throw failure
                }
                val error = JSONObject().put("status", "error").put("error", failure.toString())
                withContext(Dispatchers.IO) { directory?.let { writeDone(it, error) } }
                Log.e(TAG, error.toString(), failure)
                status.text = "失敗: $failure"
            } finally {
                active = null
                withContext(NonCancellable) { session?.close() }
                running = false
            }
        }
    }

    override fun onDestroy() {
        active?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun writeDone(directory: File, result: JSONObject) {
        val temporary = directory.resolve("done.json.tmp")
        temporary.writeText(result.toString())
        check(temporary.renameTo(directory.resolve("done.json"))) { "Cannot publish completion marker" }
    }

    private fun pssKb(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss

    private fun writeWav(file: File, samples: FloatArray) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVEfmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(1).putInt(24_000).putInt(48_000).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(samples.size * 2)
        FileOutputStream(file).use { out ->
            out.write(header.array())
            val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { data.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
            out.write(data.array())
        }
    }
    companion object { private const val TAG = "LamiKokoroBenchmark" }
}
