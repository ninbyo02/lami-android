package io.github.ninbyo02.lami.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal object LamiKokoroScratchVoiceFactory {
    private fun bundle(context: Context) =
        LamiKokoroScratchBundle.read(context.filesDir.resolve(LamiKokoroScratchBundle.DIRECTORY))

    fun availability(context: Context): TtsEngineAvailability {
        val problem = runCatching {
            val model = bundle(context)
            check(LamiKokoroScratchNative.available && LamiKokoroJapaneseFrontend.available) {
                "このビルドでKokoroの読み上げを起動できません。"
            }
            model.verifyAndReadVocabulary()
        }.exceptionOrNull()
        return TtsEngineAvailability(TtsEngineChoice.KOKORO, problem == null,
            problem?.message ?: "再生を検証した学習版です。概念など一般語の一部に読み違いが残ります。")
    }

    fun createSelected(context: Context): LamiVoiceEngine {
        // Full asset hashing and native model load run on the session worker.
        // The selected voice actor must remain responsive to stop/switch events.
        val model = bundle(context)
        check(LamiKokoroJapaneseFrontend.available) { "このビルドでKokoroの読み上げを起動できません。" }
        return LamiNeuralPcmVoiceEngine(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            session = LamiKokoroScratchSession(model),
            playPcm = { pcm, started -> LamiPcmPlayer.play(context.applicationContext, pcm, started) },
            onEvent = { _, event -> Log.d("LamiKokoroScratch", event) },
        )
    }
}
