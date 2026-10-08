package io.github.ninbyo02.lami.tts

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
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
                    else LamiVoiceDiagnostic.synthesizeText(context, root, requestedText) { report.appendText("$it elapsed_ms=${android.os.SystemClock.elapsedRealtime() - started}\n") }
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

    /** Explicit A/B device diagnostic through the same synthesis, queue and playback primitives. */
    suspend fun runPipelineProbe(context: Context, texts: List<String>, serial: Boolean, reuseCpWorkspace: Boolean = true, prefixDecodeProbe: Boolean = false, pcmThreadProbe: Boolean = false, gpuDecoderProbe: Boolean = false) = withContext(Dispatchers.Default) {
        require(texts.size in 2..4 && texts.all { it.isNotBlank() && it.length <= 120 })
        val root = context.filesDir.resolve("local_models/lami_tts/prepared_hai")
        val report = context.filesDir.resolve("neural_tts_pipeline_probe.txt")
        val started = android.os.SystemClock.elapsedRealtime()
        val reportLock = Any()
        fun trace(event: String) = synchronized(reportLock) {
            report.appendText("$event elapsed_ms=${android.os.SystemClock.elapsedRealtime() - started}\n")
        }
        report.writeText("mode=${if (serial) "serial" else "pipeline"} status=started\n")
        try {
            withTimeout(600_000L) {
                suspend fun prepare(index: Int): Pair<Int, FloatArray> {
                    trace("request=$index synthesis=started")
                    val pcm = LamiVoiceModuleCache.withSession(root) { session ->
                        val codes = LamiPreparedVoiceSynthesizer.generateText(root, texts[index], session, reuseCpWorkspace) { trace("request=$index $it") }
                        val codeBytes = java.nio.ByteBuffer.allocate(codes.values.size * 8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        codes.values.forEach(codeBytes::putLong)
                        trace("request=$index codes_sha256=${sha256(codeBytes.array())} frames=${codes.frames}")
                        val full = LamiVoiceDecoderProcess.decode(context, root, codes) { trace("request=$index $it") }
                        if (gpuDecoderProbe) LamiVoiceGpuDecoderProbe.compare(context, root, codes) { trace("request=$index $it") }
                        if (pcmThreadProbe) LamiVoicePcmThreadProbe.compare(context, root, codes, full) { trace("request=$index $it") }
                        if (prefixDecodeProbe) LamiVoicePrefixDecodeProbe.compare(context, root, codes, full) { trace("request=$index $it") }
                        full
                    }
                    val pcmBytes = java.nio.ByteBuffer.allocate(pcm.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    pcm.forEach(pcmBytes::putFloat)
                    trace("request=$index pcm_sha256=${sha256(pcmBytes.array())}")
                    trace("request=$index synthesis=complete samples=${pcm.size}")
                    return index to pcm
                }
                suspend fun play(clip: Pair<Int, FloatArray>) {
                    LamiPcmPlayer.play(clip.second) { trace("request=${clip.first} playback=started") }
                    trace("request=${clip.first} playback=complete")
                }
                if (serial) {
                    texts.indices.forEach { play(prepare(it)) }
                } else coroutineScope {
                    val complete = CompletableDeferred<Unit>()
                    val pipeline = LamiSpeechPipeline(this, {},
                        prepare = { prepare(it.toInt()) },
                        play = { clip ->
                            play(clip)
                            if (clip.first == texts.lastIndex) complete.complete(Unit)
                        })
                    try {
                        texts.indices.forEach { pipeline.enqueue(it.toString()) }
                        complete.await()
                    } finally { pipeline.stop() }
                }
                trace("status=complete")
            }
        } catch (cancelled: CancellationException) {
            trace("status=cancelled")
            throw cancelled
        } catch (failure: Exception) {
            trace("status=failure class=${failure.javaClass.simpleName}")
        }
    }

    suspend fun runStreamingProbe(context: Context, texts: List<String>) = withContext(Dispatchers.Default) {
        require(texts.size in 2..4 && texts.all { it.isNotBlank() && it.length <= 120 })
        val root = context.filesDir.resolve("local_models/lami_tts/prepared_hai")
        val report = context.filesDir.resolve("neural_tts_pipeline_probe.txt")
        val started = android.os.SystemClock.elapsedRealtime()
        val lock = Any()
        fun trace(event: String) = synchronized(lock) {
            report.appendText("$event elapsed_ms=${android.os.SystemClock.elapsedRealtime() - started}\n")
        }
        report.writeText("mode=streaming status=started\n")
        try {
            withTimeout(600_000L) {
                texts.forEachIndexed { index, text ->
                    trace("request=$index synthesis=started")
                    LamiVoiceStreamingProbe.run(context, root, text) { trace("request=$index $it") }
                }
                trace("status=complete")
            }
        } catch (cancelled: CancellationException) {
            trace("status=cancelled")
            throw cancelled
        } catch (failure: Exception) {
            trace("status=failure class=${failure.javaClass.simpleName} message=${failure.message}")
        }
    }

    private fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeWav(file: java.io.File, pcm: FloatArray) {
        val bytes = java.nio.ByteBuffer.allocate(44 + pcm.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVEfmt ".toByteArray())
        bytes.putInt(16).putShort(1).putShort(1).putInt(24_000).putInt(48_000).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(pcm.size * 2)
        for (sample in pcm) bytes.putShort((sample * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        file.writeBytes(bytes.array())
    }
}
