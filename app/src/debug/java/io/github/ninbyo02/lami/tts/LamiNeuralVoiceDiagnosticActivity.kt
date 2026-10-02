package io.github.ninbyo02.lami.tts

import android.os.Bundle
import io.github.ninbyo02.lami.R
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Voice-only screen: never constructs the chat/NPU startup pipeline. */
class LamiNeuralVoiceDiagnosticActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = EditText(this).apply {
            id = R.id.lami_voice_text_input
            hint = "読み上げる文章"
            setText(intent.getStringExtra("lami_neural_tts_text_probe") ?: "こんにちは。")
            maxLines = 5
        }
        val speak = Button(this).apply { setText("発話する") }
        val stop = Button(this).apply { setText("停止") }
        val status = TextView(this).apply { textSize = 16f }
        val contents = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(TextView(this@LamiNeuralVoiceDiagnosticActivity).apply {
                setText("LAMI 音声テスト")
                textSize = 22f
            })
            addView(TextView(this@LamiNeuralVoiceDiagnosticActivity).apply {
                setText("端末内で音声を生成します。生成には時間がかかります。画面を閉じても続き、通知から停止できます。")
            })
            addView(text)
            addView(speak)
            addView(stop)
            addView(status)
        }
        val screen = ScrollView(this).apply { addView(contents) }
        setContentView(screen)
        val density = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(screen) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            contents.setPadding((16 * density).toInt(), bars.top + (16 * density).toInt(),
                (16 * density).toInt(), bars.bottom + (16 * density).toInt())
            insets
        }
        ViewCompat.requestApplyInsets(screen)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        fun request(value: String?) {
            speak.isEnabled = false
            val service = Intent(this, LamiNeuralVoiceDiagnosticService::class.java)
            value?.let { service.putExtra("lami_neural_tts_text_probe", it) }
            startForegroundService(service)
        }
        speak.setOnClickListener { if (text.text.isNotBlank()) request(text.text.toString()) }
        stop.setOnClickListener {
            startService(Intent(this, LamiNeuralVoiceDiagnosticService::class.java).setAction("STOP"))
        }
        if (savedInstanceState == null && intent.getBooleanExtra("lami_neural_tts_stop_probe", false)) {
            startService(Intent(this, LamiNeuralVoiceDiagnosticService::class.java).setAction("STOP"))
        } else if (savedInstanceState == null) {
            request(if (intent.getBooleanExtra("lami_neural_tts_hai_probe", false)) null else text.text.toString())
        }
        lifecycleScope.launch {
            val report = filesDir.resolve("neural_tts_hai_probe.txt")
            while (true) {
                val result = if (report.exists()) report.readText() else ""
                val finished = listOf("playback=complete", "status=failure", "status=cancelled").any(result::contains)
                speak.isEnabled = finished
                stop.isEnabled = !finished
                status.text = when {
                    "playback=complete" in result -> "発話が完了しました。"
                    "status=failure" in result -> "発話できませんでした。\n" + result.lineSequence().lastOrNull { it.startsWith("status=failure") }
                    "status=cancelled" in result -> "停止しました。"
                    "playback=started" in result -> "再生中です。"
                    "stage=pcm_decode" in result -> "音声を仕上げています…"
                    else -> "音声を生成しています…"
                }
                delay(1000)
            }
        }
    }
}
