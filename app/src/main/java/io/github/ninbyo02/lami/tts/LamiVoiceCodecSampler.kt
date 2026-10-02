package io.github.ninbyo02.lami.tts

import java.util.Random
import kotlin.math.exp

/** Seeded top-k sampling; primitive worst-first heap preserves the full-sort ordering. */
internal class LamiVoiceCodecSampler(seed: Long = 42L, private val topK: Int = 50) {
    init { require(topK > 0) }
    private val random = Random(seed)
    private val tokens = IntArray(topK)
    private val scores = DoubleArray(topK)
    private val weights = DoubleArray(topK)

    private fun worse(aScore: Double, aToken: Int, bScore: Double, bToken: Int): Boolean {
        val comparison = java.lang.Double.compare(aScore, bScore)
        return comparison < 0 || (comparison == 0 && aToken > bToken)
    }
    private fun swap(a: Int, b: Int) {
        val token = tokens[a]; tokens[a] = tokens[b]; tokens[b] = token
        val score = scores[a]; scores[a] = scores[b]; scores[b] = score
    }
    private fun siftDown(size: Int) {
        var parent = 0
        while (parent * 2 + 1 < size) {
            var child = parent * 2 + 1
            if (child + 1 < size && worse(scores[child + 1], tokens[child + 1], scores[child], tokens[child])) child++
            if (!worse(scores[child], tokens[child], scores[parent], tokens[parent])) break
            swap(parent, child)
            parent = child
        }
    }
    fun choose(logits: FloatArray, allowed: Set<Int>, repeated: Set<Int> = emptySet()): Int {
        require(allowed.isNotEmpty())
        var size = 0
        for (token in allowed) {
            require(token in logits.indices && logits[token].isFinite())
            var value = logits[token]
            if (token in repeated) value = if (value < 0f) value * 1.05f else value / 1.05f
            val score = value.toDouble() / 0.9
            if (size < topK) {
                var child = size++
                tokens[child] = token; scores[child] = score
                while (child > 0) {
                    val parent = (child - 1) / 2
                    if (!worse(scores[child], tokens[child], scores[parent], tokens[parent])) break
                    swap(child, parent); child = parent
                }
            } else if (worse(scores[0], tokens[0], score, token)) {
                tokens[0] = token; scores[0] = score
                siftDown(size)
            }
        }
        // Worst candidates move to the end: final order is descending score,
        // ascending token, including Double.compare signed-zero semantics.
        for (end in size - 1 downTo 1) { swap(0, end); siftDown(end) }
        val peak = scores[0]
        var sum = 0.0
        for (i in 0 until size) { weights[i] = exp(scores[i] - peak); sum += weights[i] }
        val target = random.nextDouble() * sum
        var cumulative = 0.0
        for (i in 0 until size) {
            cumulative += weights[i]
            if (target < cumulative) return tokens[i]
        }
        return tokens[size - 1]
    }
}
