package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.SystemClock
import java.io.File

/** Fixed-code, separate-process CPU diagnostic; never changes normal chat settings. */
internal object LamiVoicePcmThreadProbe {
    suspend fun compare(context: Context, root: File, codes: LamiVoiceCodes, reference: FloatArray, progress: (String) -> Unit) {
        for (trial in 0..2) {
            val order = if (trial % 2 == 0) listOf(1, 2, 4) else listOf(4, 2, 1)
            for (threads in order) {
                val warm = LamiVoiceDecoderProcess.decodeWithThreads(context, root, codes, threads) { progress("pcm_trial=$trial warmup=true $it") }
                check(warm.contentEquals(reference)) { "PCM thread warmup differs from default" }
                repeat(2) { repeat ->
                    val started = SystemClock.elapsedRealtime()
                    val actual = LamiVoiceDecoderProcess.decodeWithThreads(context, root, codes, threads) { progress("pcm_trial=$trial repeat=$repeat $it") }
                    val elapsed = SystemClock.elapsedRealtime() - started
                    check(actual.contentEquals(reference)) { "PCM thread result differs from default" }
                    progress("metric=pcm_threads trial=$trial threads=$threads repeat=$repeat frames=${codes.frames} total_ms=$elapsed bit_equal=true")
                }
            }
        }
    }
}
