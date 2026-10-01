package io.github.ninbyo02.lami.tts

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal object LamiNeuralVoiceDiagnostics {
    fun start(activity: ComponentActivity, savedInstanceState: Bundle?) {
        // Debug-only, explicit request. Never block activity startup with model inference.
        if (savedInstanceState == null && activity.intent.getBooleanExtra("lami_neural_tts_hai_probe", false)) {
            val requestedText = activity.intent.getStringExtra("lami_neural_tts_text_probe")
            activity.lifecycleScope.launch { runProbe(activity.applicationContext, requestedText) }
        }
    }

    suspend fun runProbe(context: Context, requestedText: String?) = withContext(Dispatchers.Default) {
        val root = context.filesDir.resolve("local_models/lami_tts/prepared_hai")
        val report = context.filesDir.resolve("neural_tts_hai_probe.txt")
        report.writeText("mode=${if (requestedText == null) "fixed_hai" else "arbitrary_text"} status=started\n")
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            withTimeout(600_000L) {
                val pcm = if (requestedText == null) LamiVoiceDiagnostic.synthesize(root) { report.appendText("$it elapsed_ms=${android.os.SystemClock.elapsedRealtime() - started}\n") }
                    else LamiVoiceDiagnostic.synthesizeText(root, requestedText) { report.appendText("$it elapsed_ms=${android.os.SystemClock.elapsedRealtime() - started}\n") }
                report.appendText("synthesis=success samples=${pcm.size} elapsed_ms=${android.os.SystemClock.elapsedRealtime() - started}\n")
                val wav = context.filesDir.resolve("neural_tts_last.wav")
                writeWav(wav, pcm)
                report.appendText("wav=neural_tts_last.wav\n")
                LamiPcmPlayer.play(pcm) { report.appendText("playback=started\n") }
                report.appendText("playback=complete\n")
            }
        } catch (cancelled: CancellationException) {
            report.appendText("status=cancelled\n")
            throw cancelled
        } catch (failure: LinkageError) {
            report.appendText("status=failure class=${failure.javaClass.name} message=${failure.cause?.message ?: failure.message}\n")
        } catch (failure: Exception) {
            report.appendText("status=failure class=${failure.javaClass.name} message=${failure.message}\n")
        }
    }

    private fun writeWav(file: java.io.File, pcm: FloatArray) {
        val bytes = java.nio.ByteBuffer.allocate(44 + pcm.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVEfmt ".toByteArray())
        bytes.putInt(16).putShort(1).putShort(1).putInt(24_000).putInt(48_000).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(pcm.size * 2)
        for (sample in pcm) bytes.putShort((sample * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        file.writeBytes(bytes.array())
    }
}
