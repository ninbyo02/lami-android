package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class LamiVoiceCodecSamplerTest {
    @Test fun specialTokensCannotEscapeAllowedCodecSet() {
        val sampler = LamiVoiceCodecSampler()
        val logits = FloatArray(3072) { 1000f }.also { it[0] = 0f; it[1] = 1f }
        repeat(100) { assertTrue(sampler.choose(logits, setOf(0, 1)) in 0..1) }
    }
    @Test fun repetitionPenaltyHandlesBothPositiveAndNegativeLogits() {
        val sampler = LamiVoiceCodecSampler(topK = 1)
        assertEquals(1, sampler.choose(floatArrayOf(10f, 9.8f), setOf(0, 1), setOf(0)))
        assertEquals(1, sampler.choose(floatArrayOf(-10f, -10.2f), setOf(0, 1), setOf(0)))
    }
    @Test fun sameSeedReproducesCodecSequence() {
        val first = LamiVoiceCodecSampler()
        val second = LamiVoiceCodecSampler()
        val logits = floatArrayOf(0f, 1f, 2f, 3f)
        val allowed = logits.indices.toSet()
        repeat(30) { assertEquals(first.choose(logits, allowed), second.choose(logits, allowed)) }
    }
    @Test fun matchesHostJavaRandomReference() {
        val sampler = LamiVoiceCodecSampler()
        val logits = floatArrayOf(0f, 1f, 2f, 3f)
        val expected = intArrayOf(2, 2, 3, 3, 3, 1, 3, 3, 3, 2, 1, 3, 2, 3, 3, 3, 3, 2, 3, 3)
        assertArrayEquals(expected, IntArray(expected.size) { sampler.choose(logits, logits.indices.toSet()) })
    }
    @Test fun rejectsNonFiniteCodecLogits() {
        assertThrows(IllegalArgumentException::class.java) {
            LamiVoiceCodecSampler().choose(floatArrayOf(Float.NaN), setOf(0))
        }
    }
    @Test fun boundedSelectionMatchesFullSortAcrossTiesAndChangingSets() {
        for (k in listOf(1, 2, 50, 80)) {
            val actual = LamiVoiceCodecSampler(seed = 73L, topK = k)
            val referenceRandom = java.util.Random(73L)
            val inputs = java.util.Random(91L)
            repeat(200) { step ->
                val logits = FloatArray(127) { (inputs.nextInt(17) - 8) / 3f }
                logits[0] = -0.0f; logits[1] = 0.0f
                val allowed = logits.indices.shuffled(kotlin.random.Random(step)).take(1 + step % 127).toSet()
                val repeated = allowed.filter { it % 3 == 0 }.toSet()
                val sorted = allowed.map { token ->
                    var score = logits[token]
                    if (token in repeated) score = if (score < 0f) score * 1.05f else score / 1.05f
                    token to score.toDouble() / 0.9
                }.sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first }).take(k)
                val weights = sorted.map { kotlin.math.exp(it.second - sorted.first().second) }
                val target = referenceRandom.nextDouble() * weights.sum()
                var cumulative = 0.0
                var expected = sorted.last().first
                for (i in sorted.indices) {
                    cumulative += weights[i]
                    if (target < cumulative) { expected = sorted[i].first; break }
                }
                assertEquals("k=$k step=$step", expected, actual.choose(logits, allowed, repeated))
            }
        }
    }
}
