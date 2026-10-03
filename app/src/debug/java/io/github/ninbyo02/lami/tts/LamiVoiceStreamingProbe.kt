package io.github.ninbyo02.lami.tts

import android.content.Context
import java.io.File
import android.os.SystemClock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel

/** Provisional audio is allowed only by the explicit debug probe, never normal chat. */
internal object LamiVoiceStreamingProbe {
    suspend fun run(context: Context, root: File, text: String, prefixFrames: Int, progress: (String) -> Unit) = coroutineScope {
        require(prefixFrames in listOf(2, 4, 8))
        val started = SystemClock.elapsedRealtime()
        progress("metric=stream_prefix_cadence frames=$prefixFrames")
        val codes = Channel<LamiVoiceCodes>(1)
        val pcm = Channel<FloatArray>(1)
        val producer = async {
            var sent = 0
            val full = LamiVoiceModuleCache.withSession(root) { session ->
                LamiPreparedVoiceSynthesizer.generateText(root, text, session, prefixFrames = prefixFrames, onPrefix = { prefix ->
                    progress("stage=stream_codes frames=${prefix.frames} generated_ms=${SystemClock.elapsedRealtime() - started} audio_ms=${prefix.frames * 80L}")
                    codes.send(prefix)
                    sent = prefix.frames
                }, progress = progress)
            }
            if (full.frames > sent) codes.send(full)
            progress("stage=stream_eos frames=${full.frames}")
            codes.close()
            full
        }
        val decoder = async {
            var previous = FloatArray(0)
            for (prefix in codes) {
                val decodeStarted = SystemClock.elapsedRealtime()
                val next = LamiVoiceDecoderProcess.decode(context, root, prefix, progress)
                progress("metric=prefix_decode frames=${prefix.frames} ms=${SystemClock.elapsedRealtime() - decodeStarted}")
                val added = LamiVoiceChunkProbeMath.appendedPcm(previous, next)
                progress("stage=stream_pcm frames=${prefix.frames} added_samples=${added.size}")
                pcm.send(added)
                previous = next
            }
            pcm.close()
            previous
        }
        val player = async {
            LamiPcmStreamPlayer.play(pcm) { progress("playback=started start_delay_ms=${SystemClock.elapsedRealtime() - started}") }
        }
        try {
            val fullCodes = producer.await() // Natural EOS remains required for probe success.
            val assembled = decoder.await()
            val stats = player.await()
            progress("playback=complete samples=${stats.samples} underruns=${stats.underruns}")
            val full = LamiVoiceDecoderProcess.decode(context, root, fullCodes, progress)
            require(assembled.size == full.size && stats.samples == full.size)
            val codeBytes = java.nio.ByteBuffer.allocate(fullCodes.values.size * 8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            fullCodes.values.forEach(codeBytes::putLong)
            val pcmBytes = java.nio.ByteBuffer.allocate(full.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            full.forEach(pcmBytes::putFloat)
            fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            progress("codes_sha256=${hash(codeBytes.array())} frames=${fullCodes.frames}")
            progress("pcm_sha256=${hash(pcmBytes.array())}")
            val difference = LamiVoiceChunkProbeMath.difference(full, assembled, full.size)
            check(difference.maxAbsolute == 0.0) { "Stream PCM differs from full decode" }
            progress("metric=stream_parity max_abs=${difference.maxAbsolute} rms=${difference.rms} frames=${fullCodes.frames}")
        } finally {
            codes.cancel()
            pcm.cancel()
            producer.cancel()
            decoder.cancel()
            player.cancel()
        }
    }
}
