package io.github.ninbyo02.lami.tts

import java.util.Random
import kotlin.math.exp

/** Bounded seeded sampling with the model's default temperature/top-k and repetition penalty. */
internal class LamiVoiceCodecSampler(seed: Long = 42L, private val topK: Int = 50) {
    private val random = Random(seed)
    fun choose(logits: FloatArray, allowed: Set<Int>, repeated: Set<Int> = emptySet()): Int {
        require(topK > 0 && allowed.isNotEmpty())
        val candidates = allowed.map { token ->
            require(token in logits.indices && logits[token].isFinite())
            var score = logits[token]
            if (token in repeated) score = if (score < 0f) score * 1.05f else score / 1.05f
            token to score.toDouble() / 0.9
        }.sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first }).take(topK)
        val peak = candidates.first().second
        val weights = candidates.map { exp(it.second - peak) }
        val target = random.nextDouble() * weights.sum()
        var cumulative = 0.0
        for (i in candidates.indices) {
            cumulative += weights[i]
            if (target < cumulative) return candidates[i].first
        }
        return candidates.last().first
    }
}
