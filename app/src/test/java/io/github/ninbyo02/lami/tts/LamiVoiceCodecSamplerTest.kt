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
}
