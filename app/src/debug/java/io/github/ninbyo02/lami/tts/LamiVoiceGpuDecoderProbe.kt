package io.github.ninbyo02.lami.tts

import android.content.Context
import java.io.File
import kotlin.math.abs

/** Prefix-only GPU feasibility measurement; not streaming quality approval. */
internal object LamiVoiceGpuDecoderProbe {
    suspend fun compare(context: Context, root: File, full: LamiVoiceCodes, progress: (String) -> Unit) {
        require(full.frames >= 16)
        val codes = LamiVoiceCodes(LongArray(256) { full.values[(it / 16) * full.frames + it % 16] }, 16)
        val names = listOf("speech-decoder-fixed16-xnnpack.pte", "speech-decoder-fixed16-vulkan.pte")
        val hashes = listOf("9a458b1029bd3d515f8febfc2b6114f9a7cb1a51dffea004e82173a3124f7dff", "5aac6bebf8cdf35f7dd5029b74a1d9d1485b0a9113bd328b13b73438a14b0a72")
        names.zip(hashes).forEach { (name, expected) ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            root.resolve(name).inputStream().use { stream ->
                val block = ByteArray(65536)
                while (true) { val count = stream.read(block); if (count < 0) break; digest.update(block, 0, count) }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == expected)
        }
        val reference = LamiVoiceDecoderProcess.decodeModel(context, root, codes, names[0], 4) { progress("gpu_reference=true $it") }
        for (trial in 0..1) {
            for (backend in if (trial == 0) listOf(0, 1) else listOf(1, 0)) {
                val warm = LamiVoiceDecoderProcess.decodeModel(context, root, codes, names[backend], 4) { progress("gpu_trial=$trial backend=$backend warmup=true $it") }
                require(warm.size == reference.size)
                repeat(2) { repeat ->
                    val actual = LamiVoiceDecoderProcess.decodeModel(context, root, codes, names[backend], 4) { progress("gpu_trial=$trial backend=$backend repeat=$repeat $it") }
                    require(actual.size == reference.size)
                    var maximum = 0.0
                    var squared = 0.0
                    for (i in actual.indices) { val error = (actual[i] - reference[i]).toDouble(); maximum = maxOf(maximum, abs(error)); squared += error * error }
                    progress("metric=pcm_gpu trial=$trial backend=$backend repeat=$repeat frames=16 max_abs=$maximum rmse=${kotlin.math.sqrt(squared / actual.size)} bit_equal=${actual.contentEquals(reference)}")
                    check(maximum < 0.002) { "GPU prefix numeric smoke tolerance exceeded" }
                }
            }
        }
    }
}
