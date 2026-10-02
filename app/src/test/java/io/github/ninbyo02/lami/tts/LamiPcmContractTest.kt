package io.github.ninbyo02.lami.tts

import org.junit.Assert.assertThrows
import org.junit.Test

class LamiPcmContractTest {
    @Test fun acceptsFiniteSpeech() { LamiPcmContract.validate(floatArrayOf(0f, -0.3f, 0.2f)) }
    @Test fun rejectsInvalidDecoderOutput() {
        listOf(floatArrayOf(), floatArrayOf(Float.NaN), floatArrayOf(Float.POSITIVE_INFINITY),
            floatArrayOf(1.1f), floatArrayOf(0f), FloatArray(720001) { 0.1f }).forEach { pcm ->
            assertThrows(IllegalArgumentException::class.java) { LamiPcmContract.validate(pcm) }
        }
    }
}
