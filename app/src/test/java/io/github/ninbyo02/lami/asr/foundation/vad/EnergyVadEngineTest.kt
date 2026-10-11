package io.github.ninbyo02.lami.asr.foundation.vad

import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EnergyVadEngineTest {
    @Test
    fun emitsStrictSpeechSequenceWithHysteresis() {
        val vad = EnergyVadEngine(
            startThresholdRms = 600.0,
            continueThresholdRms = 300.0,
        )

        assertEquals(VadEvent.Silence, vad.accept(frame(0)))
        assertEquals(VadEvent.Silence, vad.accept(frame(400)))
        assertEquals(VadEvent.SpeechStarted, vad.accept(frame(700)))
        assertEquals(VadEvent.Speech, vad.accept(frame(400)))
        assertEquals(VadEvent.SpeechEnded, vad.accept(frame(200)))
        assertEquals(VadEvent.Silence, vad.accept(frame(200)))
        assertEquals(VadEvent.SpeechStarted, vad.accept(frame(700)))
    }

    @Test
    fun resetAllowsNewSpeechStart() {
        val vad = EnergyVadEngine(startThresholdRms = 600.0, continueThresholdRms = 300.0)
        assertEquals(VadEvent.SpeechStarted, vad.accept(frame(700)))
        vad.reset()
        assertEquals(VadEvent.SpeechStarted, vad.accept(frame(700)))
    }

    @Test
    fun closeIsIdempotentAndRejectsFurtherUse() {
        val vad = EnergyVadEngine()
        vad.close()
        vad.close()

        assertThrows(IllegalStateException::class.java) {
            vad.accept(frame(0))
        }
        assertThrows(IllegalStateException::class.java) {
            vad.reset()
        }
    }

    @Test
    fun invalidThresholdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            EnergyVadEngine(startThresholdRms = 100.0, continueThresholdRms = 200.0)
        }
    }

    private fun frame(amplitude: Int): Pcm16Frame =
        Pcm16Frame(16_000, ShortArray(320) { amplitude.toShort() })
}
