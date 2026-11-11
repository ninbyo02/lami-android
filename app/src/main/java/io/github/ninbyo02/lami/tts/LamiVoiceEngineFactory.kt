package io.github.ninbyo02.lami.tts

import android.content.Context
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object LamiVoiceEngineFactory {
    fun create(context: Context): LamiVoiceEngine {
        val app = context.applicationContext
        return LamiSelectedVoiceEngine(
            selections = SettingsPreferences(app).ttsEngineFlow,
            create = { createSelected(app, it) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }

    fun availability(context: Context, engine: TtsEngineChoice): TtsEngineAvailability {
        if (engine == TtsEngineChoice.ANDROID) return TtsEngineAvailability(engine, true, "端末に設定された音声を使えます")
        return try {
            val factory = Class.forName("io.github.ninbyo02.lami.tts.LamiNeuralChatVoiceFactory")
            factory.getMethod("availability", Context::class.java, TtsEngineChoice::class.java)
                .invoke(null, context.applicationContext, engine) as TtsEngineAvailability
        } catch (_: ClassNotFoundException) {
            TtsEngineAvailability(engine, false, "このビルドには含まれていません")
        } catch (_: Exception) {
            TtsEngineAvailability(engine, false, "音声モデルを確認できませんでした")
        }
    }

    /** Explicit choices never substitute another engine or voice on failure. */
    internal fun createSelected(context: Context, engine: TtsEngineChoice): LamiVoiceEngine {
        // Kokoro's settings readiness hashes the whole model. Creation checks
        // the manifest and the resident worker verifies the bytes before load.
        if (engine != TtsEngineChoice.KOKORO) {
            val available = availability(context, engine)
            check(available.available) { available.message }
        }
        if (engine == TtsEngineChoice.ANDROID) return AndroidTtsController(context.applicationContext)
        val factory = try {
            Class.forName("io.github.ninbyo02.lami.tts.LamiNeuralChatVoiceFactory")
        } catch (_: ClassNotFoundException) {
            error("このビルドには${engine.label}が含まれていません")
        }
        return factory.getMethod("createSelected", Context::class.java, TtsEngineChoice::class.java)
            .invoke(null, context.applicationContext, engine) as? LamiVoiceEngine
            ?: error("${engine.label}の音声モデルを起動できませんでした")
    }
}
