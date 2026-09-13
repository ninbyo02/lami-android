package io.github.ninbyo02.lami.ui.screens.home

import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal object StandaloneSentencePieceJni {
    private val loaded by lazy { System.loadLibrary("lami_tokenizer_only") }
    external fun countPair(model: ByteArray, input: ByteArray, output: ByteArray): LongArray
    fun count(model: ByteArray, input: String, output: String): StandaloneTokenCounts {
        require(input.length <= 1024 * 1024 && output.length <= 1024 * 1024) { "text-too-large" }
        loaded
        val values = countPair(model, input.toByteArray(Charsets.UTF_8), output.toByteArray(Charsets.UTF_8))
        require(values.size == 4 && values[0] in 0..Int.MAX_VALUE.toLong() && values[1] in 0..Int.MAX_VALUE.toLong())
        return StandaloneTokenCounts(values[0].toInt(), values[1].toInt(), values[2], values[3])
    }
}

internal suspend fun recordStandaloneTokenCountComparison(
    modelPath: String?, input: String, output: String, snapshot: LocalInferenceMeasuredTokenSnapshot,
) {
    if (!BuildConfig.TOKENIZER_ONLY_DIAGNOSTIC) return
    currentCoroutineContext().ensureActive()
    val started = System.nanoTime()
    var tokenizerHash = "unavailable"
    val result = compareStandaloneTokenCounts(true, snapshot.inputTokens, snapshot.outputTokens) {
        requireNotNull(modelPath)
        val bytes = LitertLmSentencePieceSection.read(File(modelPath))
        tokenizerHash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        StandaloneSentencePieceJni.count(bytes, input, output)
    }
    currentCoroutineContext().ensureActive()
    // No model path, input/output text or exception message is written to this diagnostic.
    Log.d("LamiTokenizerOnly", "status=${result.status} tokenizer_sha256=$tokenizerHash " +
        "expected_input=${snapshot.inputTokens} expected_output=${snapshot.outputTokens} " +
        "candidate_input=${result.counts?.input} candidate_output=${result.counts?.output} " +
        "load_ns=${result.counts?.loadNs} count_ns=${result.counts?.countNs} elapsed_ns=${System.nanoTime() - started}")
}
