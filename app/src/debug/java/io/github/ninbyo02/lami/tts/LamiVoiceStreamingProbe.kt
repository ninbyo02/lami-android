package io.github.ninbyo02.lami.tts

import android.content.Context
import java.io.File
import android.os.SystemClock

/** Provisional audio is restricted to the probe and explicit isolated debug chat trial. */
internal object LamiVoiceStreamingProbe {
    suspend fun run(context: Context, root: File, text: String, progress: (String) -> Unit) {
        runSpeech(context, root, text, progress, verifyFullDecode = true)
    }

    suspend fun runSpeech(
        context: Context, root: File, text: String, progress: (String) -> Unit,
        onStarted: () -> Unit = {}, verifyFullDecode: Boolean = false,
    ): FloatArray {
        val started = SystemClock.elapsedRealtime()
        val result = LamiPcmStreamingPipeline.run(
            frames = { codes: LamiVoiceCodes -> codes.frames },
            generate = { emit ->
                LamiVoiceModuleCache.withSession(root) { session ->
                    LamiPreparedVoiceSynthesizer.generateText(root, text, session, onPrefix = { prefix ->
                        progress("stage=stream_codes frames=${prefix.frames} generated_ms=${SystemClock.elapsedRealtime() - started} audio_ms=${prefix.frames * 80L}")
                        emit(prefix)
                    }, progress = progress)
                }.also { progress("stage=stream_eos frames=${it.frames}") }
            },
            decode = { prefix ->
                val decodeStarted = SystemClock.elapsedRealtime()
                LamiVoiceDecoderProcess.decode(context, root, prefix, progress).also {
                    progress("metric=prefix_decode frames=${prefix.frames} ms=${SystemClock.elapsedRealtime() - decodeStarted}")
                }
            },
            play = { chunks ->
                LamiPcmStreamPlayer.play(chunks) {
                    onStarted()
                    progress("playback=started start_delay_ms=${SystemClock.elapsedRealtime() - started}")
                }
            },
        )
        val fullCodes = result.codes
        val assembled = result.pcm
        val stats = result.playback
        progress("playback=complete samples=${stats.samples} underruns=${stats.underruns}")
        require(stats.samples == assembled.size)
        val full = assembled
        val codeBytes = java.nio.ByteBuffer.allocate(fullCodes.values.size * 8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        fullCodes.values.forEach(codeBytes::putLong)
        val pcmBytes = java.nio.ByteBuffer.allocate(full.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        full.forEach(pcmBytes::putFloat)
        fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        progress("codes_sha256=${hash(codeBytes.array())} frames=${fullCodes.frames}")
        progress("pcm_sha256=${hash(pcmBytes.array())}")
        if (verifyFullDecode) {
            val decoded = LamiVoiceDecoderProcess.decode(context, root, fullCodes, progress)
            require(decoded.size == assembled.size)
            val difference = LamiVoiceChunkProbeMath.difference(decoded, assembled, decoded.size)
            check(difference.maxAbsolute == 0.0) { "Stream PCM differs from full decode" }
            progress("metric=stream_parity max_abs=${difference.maxAbsolute} rms=${difference.rms} frames=${fullCodes.frames}")
        }
        return assembled
    }
}
