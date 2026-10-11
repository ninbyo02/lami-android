package io.github.ninbyo02.lami.tts

import android.content.Context

object LamiVoiceEngineFactory {
    fun create(context: Context): LamiVoiceEngine {
        val app = context.applicationContext
        return runCatching {
            val factory = Class.forName("io.github.ninbyo02.lami.tts.LamiNeuralChatVoiceFactory")
            factory.getMethod("createOrNull", Context::class.java).invoke(null, app) as? LamiVoiceEngine
        }.getOrNull() ?: AndroidTtsController(app)
    }
}
