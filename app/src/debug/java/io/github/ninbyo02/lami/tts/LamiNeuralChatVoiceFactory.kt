package io.github.ninbyo02.lami.tts

import android.content.Context

object LamiNeuralChatVoiceFactory {
    @JvmStatic
    fun createOrNull(context: Context): LamiVoiceEngine? {
        val root = context.filesDir.resolve("local_models/lami_tts/prepared_hai")
        return if (root.resolve("voice-text-bundle.json").isFile) {
            LamiNeuralChatVoiceEngine(context.applicationContext, root)
        } else null
    }
}
