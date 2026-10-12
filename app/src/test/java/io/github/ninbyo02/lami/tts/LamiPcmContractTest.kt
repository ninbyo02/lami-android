package io.github.ninbyo02.lami.tts

import org.junit.Assert.assertEquals
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

    @Test fun rejectsMixedInvalidSpeechAsInvalidPcm() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LamiPcmContract.validate(floatArrayOf(0.2f, Float.NaN))
        }
        assertEquals("Invalid decoder PCM", error.message)
    }

    @Test fun rejectsFiniteNearZeroPcmAsSilence() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LamiPcmContract.validate(floatArrayOf(0.000001f, -0.000001f))
        }
        assertEquals("Decoder returned silence", error.message)
    }

    @Test fun startupPrebufferUsesAboutFiftyMillisecondsOrPlatformMinimum() {
        assertEquals(1_200, LamiPcmContract.startupPrebufferSamples(totalSamples = 24_000, minBufferBytes = 128))
        assertEquals(4_096, LamiPcmContract.startupPrebufferSamples(totalSamples = 24_000, minBufferBytes = 4_096 * Float.SIZE_BYTES))
        assertEquals(1_000, LamiPcmContract.startupPrebufferSamples(totalSamples = 1_000, minBufferBytes = 4_096 * Float.SIZE_BYTES))
    }
}
