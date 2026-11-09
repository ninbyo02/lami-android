package io.github.ninbyo02.lami.tts

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object LamiSupertonicVoiceFactory {
    const val PREFS = "lami_supertonic_debug"
    fun modelDirectory(context: Context) = File(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("model_path", null) ?: context.filesDir.resolve("supertonic3").absolutePath)

    fun createOrNull(context: Context): LamiVoiceEngine? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return null
        val directory = modelDirectory(context)
        if (LamiSupertonicSession.FILES.any { !directory.resolve(it).isFile }) {
            Log.e("LamiSupertonic", "Missing model directory: $directory")
            return null
        }
        val sid = prefs.getInt("sid", 0)
        val steps = prefs.getInt("steps", 5)
        val threads = prefs.getInt("threads", 4)
        if (sid !in 0..9 || steps !in 1..30 || threads !in 1..16) {
            Log.e("LamiSupertonic", "Invalid saved voice configuration")
            return null
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = LamiSupertonicSession(directory, speakerId = sid, steps = steps, threads = threads,
            measured = { Log.i("LamiSupertonicMetric", it.toString()) })
        scope.launch { runCatching { session.prewarm() }.onFailure { Log.e("LamiSupertonic", "Prewarm failed", it) } }
        return LamiNeuralPcmVoiceEngine(scope, session, LamiPcmPlayer::play) { _, event -> Log.d("LamiSupertonic", event) }
    }
}
