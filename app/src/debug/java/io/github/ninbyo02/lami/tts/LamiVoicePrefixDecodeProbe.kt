package io.github.ninbyo02.lami.tts

import android.content.Context
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Compare prefix decode against full-sentence PCM; never plays provisional chunks. */
internal object LamiVoicePrefixDecodeProbe {
    suspend fun compare(context: Context, root: File, codes: LamiVoiceCodes, full: FloatArray, progress: (String) -> Unit) {
        val counts = listOf(8, 12, 16, 24).filter { it < codes.frames }
        for (count in counts) {
            currentCoroutineContext().ensureActive()
            val prefixCodes = LamiVoiceChunkProbeMath.prefixCodes(codes.values, codes.frames, count)
            val pcm = LamiVoiceDecoderProcess.decode(context, root, LamiVoiceCodes(prefixCodes, count)) {
                progress("prefix_frames=$count $it")
            }
            for (tail in listOf(0, 1, 2, 4, 8).filter { it < count }) {
                val difference = LamiVoiceChunkProbeMath.difference(full, pcm, (count - tail) * 1920)
                progress("metric=prefix_difference frames=$count tail_frames=$tail samples=${difference.samples} max_abs=${difference.maxAbsolute} rms=${difference.rms}")
            }
        }
    }
}
