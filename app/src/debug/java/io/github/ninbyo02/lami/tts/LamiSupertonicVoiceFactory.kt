package io.github.ninbyo02.lami.tts

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONObject

internal object LamiSupertonicVoiceFactory {
    const val PREFS = "lami_supertonic_debug"
    fun modelDirectory(context: Context): File {
        val light = context.filesDir.resolve("supertonic3-light5")
        if (light.isDirectory) return light
        val configured = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("model_path", null)
        return configured?.takeIf { it.isNotBlank() }?.let(::File) ?: context.filesDir.resolve("supertonic3")
    }

    fun availability(context: Context): TtsEngineAvailability {
        val directory = modelDirectory(context)
        val missing = LamiSupertonicSession.FILES.filter { name ->
            directory.resolve(name).let { !it.isFile || it.length() == 0L }
        }
        val configuration = configuration(context, directory)
        val identity = voiceIdentity(directory)
        val message = when {
            missing.isNotEmpty() -> "Supertonic 3の音声モデルが未導入、または不足しています: ${missing.joinToString()}"
            !configuration.valid -> "Supertonic 3の音声設定が不正です。"
            configuration.sid == 0 && identity == VoiceIdentity.LEARNED_LAMI -> "学習済みLAMI声・Supertonic 3（${configuration.steps}回）を利用できます。"
            identity == VoiceIdentity.STOCK -> "既成音声・Supertonic 3（${if (configuration.sid < 5) "F${configuration.sid + 1}" else "M${configuration.sid - 4}"}、${configuration.steps}回）を利用できます。"
            else -> "Supertonic 3（話者${configuration.sid + 1}、${configuration.steps}回）を利用できます。"
        }
        return TtsEngineAvailability(TtsEngineChoice.SUPERTONIC3, missing.isEmpty() && configuration.valid, message)
    }

    private data class Configuration(val sid: Int, val steps: Int, val threads: Int) {
        val valid get() = sid in 0..9 && steps in 1..30 && threads in 1..16
    }

    private fun configuration(context: Context, directory: File): Configuration {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // The comparison path fixes sid0/five steps; its name does not identify the voice.
        val light = directory == context.filesDir.resolve("supertonic3-light5")
        return Configuration(if (light) 0 else prefs.getInt("sid", 0), if (light) 5 else prefs.getInt("steps", 5), prefs.getInt("threads", 4))
    }

    private enum class VoiceIdentity { LEARNED_LAMI, STOCK, UNKNOWN }

    private fun voiceIdentity(directory: File): VoiceIdentity {
        return runCatching {
            val manifest = JSONObject(directory.resolve("manifest.json").readText())
            val stockHash = "6197735e54412143817dde72a3fb0a5eaa6884360bc94c3afb2950a913dfc201"
            val hashes = listOf(manifest.optString("bundle_sha256"), manifest.optString("runtime_model_sha256"))
            val descriptions = listOf(manifest.optString("kind"), manifest.optString("runtime_voice_alias"),
                manifest.optJSONObject("recommended_runtime_options")?.optString("voice_alias").orEmpty())
            when {
                hashes.any { it.equals(stockHash, ignoreCase = true) } -> VoiceIdentity.STOCK
                descriptions.any { it.contains("trained LAMI", ignoreCase = true) || it.contains("learned LAMI", ignoreCase = true) } -> VoiceIdentity.LEARNED_LAMI
                else -> VoiceIdentity.UNKNOWN
            }
        }.getOrDefault(VoiceIdentity.UNKNOWN)
    }

    fun createOrNull(context: Context): LamiVoiceEngine? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return null
        return createSelected(context)
    }

    fun createSelected(context: Context): LamiVoiceEngine? {
        val directory = modelDirectory(context)
        if (!availability(context).available) {
            Log.e("LamiSupertonic", "Unavailable model or configuration: $directory")
            return null
        }
        val configuration = configuration(context, directory)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = LamiSupertonicSession(directory, speakerId = configuration.sid, steps = configuration.steps, threads = configuration.threads,
            adaptiveWindow = true,
            measured = { Log.i("LamiSupertonicMetric", it.toString()) })
        // Both optional prewarm and first-speech loading belong to the engine,
        // so stop/engine switching waits for an in-flight native model load.
        return LamiNeuralPcmVoiceEngine(
            scope = scope,
            session = session,
            playPcm = { pcm, started -> LamiPcmPlayer.play(context.applicationContext, pcm, started) },
            onEvent = { _, event -> Log.d("LamiSupertonic", event) },
            streamPcm = { chunks, onStarted, report ->
                LamiPcmStreamPlayer.play(context.applicationContext, chunks, onStarted, report)
                Unit
            },
            splitText = LamiSupertonicJapaneseText::split,
        )
    }
}
