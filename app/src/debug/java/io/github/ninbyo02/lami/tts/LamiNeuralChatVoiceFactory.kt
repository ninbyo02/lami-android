package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.security.MessageDigest

object LamiNeuralChatVoiceFactory {
    @JvmStatic
    fun createOrNull(context: Context): LamiVoiceEngine? {
        val root = context.filesDir.resolve("local_models/lami_tts/prepared_hai")
        return if (root.resolve("voice-text-bundle.json").isFile) {
            LamiNeuralChatVoiceEngine(context.applicationContext, root)
        } else null
    }
    /** Explicit diagnostic API. Hashing may be expensive; call off the UI thread.
     * Fingerprints identify the supplied artifacts, not all dynamically loaded libraries.
     */
    @JvmStatic
    fun createDiagnosticOrNull(context: Context, modelArtifact: File, runtimeArtifact: File): LamiVoiceEngine? {
        val root = context.filesDir.resolve("local_models/lami_tts/prepared_hai")
        if (!root.resolve("voice-text-bundle.json").isFile) return null
        return try {
            fun hash(file: File): String {
                require(file.isFile)
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { stream ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
            val store = LamiSpeechTraceStore(context.filesDir.resolve("diagnostics/speech_traces"),
                hash(modelArtifact), hash(runtimeArtifact), SystemClock::elapsedRealtime)
            LamiNeuralChatVoiceEngine(context.applicationContext, root, store::capture)
        } catch (_: Exception) {
            // Diagnostic setup failure must still permit speech.
            LamiNeuralChatVoiceEngine(context.applicationContext, root)
        }
    }
}
