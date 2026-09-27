package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException

internal const val STANDALONE_TOKEN_COUNT_MODE = "sentencepiece_tokenizer_recount"
internal const val VERIFIED_GPU_TOKENIZER_SHA256 = "e594c8a90eb08d8bda498ff4747977dc827ae0c3c56b5c0d41a605a22d02ef03"
internal fun isTokenizerRecountMode(mode: String?): Boolean =
    mode in setOf("tokenizer_recount", "mediapipe_tokenizer_recount", STANDALONE_TOKEN_COUNT_MODE)

internal data class StandaloneGpuCountAttempt(
    val counts: StandaloneTokenCounts? = null,
    val status: String,
    val cacheHit: Boolean = false,
)

internal fun exactCountKey(modelPath: String?, input: String, output: String): ExactTokenCountKey? {
    val file = modelPath?.let(::File) ?: return null
    if (!file.isFile || !file.canRead()) return null
    return ExactTokenCountKey(file.absolutePath, file.length(), file.lastModified(), tokenTextDigest(input), tokenTextDigest(output))
}

/** Called only by the serialized recount coordinator. Retains counts, never model bytes or native objects. */
internal class StandaloneGpuTokenCounter(
    private val read: (File) -> ByteArray = LitertLmSentencePieceSection::read,
    private val count: (ByteArray, String, String) -> StandaloneTokenCounts,
    private val fingerprint: (ByteArray) -> String = { bytes ->
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    },
) {
    private val cache = ExactTokenCountCache<StandaloneTokenCounts>(16)

    fun attempt(enabled: Boolean, forceFallback: Boolean, modelPath: String?, input: String, output: String): StandaloneGpuCountAttempt {
        if (!enabled) return StandaloneGpuCountAttempt(status = "disabled")
        if (forceFallback) return StandaloneGpuCountAttempt(status = "forced-fallback")
        return try {
            require(input.length <= 1024 * 1024 && output.length <= 1024 * 1024)
            val key = exactCountKey(modelPath, input, output)
                ?: return StandaloneGpuCountAttempt(status = "model-unavailable")
            cache.get(key)?.let { return StandaloneGpuCountAttempt(it, "success", true) }
            val bytes = read(File(key.modelPath))
            if (fingerprint(bytes) != VERIFIED_GPU_TOKENIZER_SHA256) {
                return StandaloneGpuCountAttempt(status = "unsupported-tokenizer")
            }
            val result = count(bytes, input, output)
            require(result.input >= 0 && result.output >= 0 && result.input.toLong() + result.output <= Int.MAX_VALUE)
            require(result.loadNs >= 0 && result.countNs >= 0)
            if (exactCountKey(modelPath, input, output) != key) {
                return StandaloneGpuCountAttempt(status = "model-changed")
            }
            cache.put(key, result)
            StandaloneGpuCountAttempt(result, "success")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LinkageError) {
            StandaloneGpuCountAttempt(status = "native-unavailable")
        } catch (_: Exception) {
            StandaloneGpuCountAttempt(status = "failed")
        }
    }
}
