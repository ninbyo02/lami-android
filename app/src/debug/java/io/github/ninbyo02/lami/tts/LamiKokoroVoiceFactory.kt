package io.github.ninbyo02.lami.tts

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Explicit debug opt-in. Qwen remains the default until device validation. */
internal object LamiKokoroVoiceFactory {
    const val PREFS = "lami_kokoro_debug"
    fun modelFile(context: Context): File = File(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("model_path", null)
            ?: context.filesDir.resolve("kokoro/kokoro_cpu.litertlm").absolutePath,
    )
    fun createOrNull(context: Context): LamiVoiceEngine? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return null
        val file = modelFile(context)
        if (!file.isFile) {
            Log.e("LamiKokoro", "Opt-in model missing: ${file.absolutePath}")
            return null
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = LamiKokoroMediaPipeSession(context.applicationContext, file,
            gpu = prefs.getBoolean("gpu", false), voice = prefs.getString("voice", "jf_tebukuro")!!,
            measured = { Log.i("LamiKokoroMetric", it.toString()) })
        scope.launch { runCatching { session.prewarm() }.onFailure { Log.e("LamiKokoro", "Prewarm failed", it) } }
        return LamiNeuralPcmVoiceEngine(scope, session, LamiPcmPlayer::play) { _, event ->
            Log.d("LamiKokoro", event)
        }
    }
}
