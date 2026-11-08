package io.github.ninbyo02.lami.asr.foundation.vad

import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AsrListeningSettingsTest {
    @Test
    fun shortWakePhraseIsAcceptedWithoutLoweringOrdinaryUtteranceMinimum() {
        val settings = AsrListeningSettings()
        assertEquals(13, settings.endpointConfig().minimumSpeechFrames)
        val policy = VadEndpointPolicy(settings.wakeEndpointConfig())
        val vad = settings.createVad()
        var emittedFrames = 0
        repeat(6) {
            emittedFrames += policy.onFrame(frame(900), vad.accept(frame(900))).framesForAsr.size
        }
        assertEquals(6, emittedFrames)
        repeat(39) { policy.onFrame(frame(0), vad.accept(frame(0))) }
        assertEquals(VadEndpointTerminal.Finalize(FinalizeReason.END_SILENCE),
            policy.onFrame(frame(0), vad.accept(frame(0))).terminal)
    }

    @Test
    fun defaultsPreserveAcceptedEndpointPolicyAndImmediateVad() {
        val settings = AsrListeningSettings()
        assertEquals(VadEndpointConfig(), settings.endpointConfig())
        assertEquals(VadEvent.SpeechStarted, settings.createVad().accept(frame(700)))
    }

    @Test
    fun customPauseAndNoSpeechLimitsAreApplied() {
        val settings = AsrListeningSettings(endSilenceMillis = 600, noSpeechTimeoutMillis = 3_000)
        val policy = VadEndpointPolicy(settings.endpointConfig())
        val vad = settings.createVad()
        repeat(20) { policy.onFrame(frame(900), vad.accept(frame(900))) }
        repeat(29) { assertEquals(null, policy.onFrame(frame(0), vad.accept(frame(0))).terminal) }
        assertEquals(VadEndpointTerminal.Finalize(FinalizeReason.END_SILENCE),
            policy.onFrame(frame(0), vad.accept(frame(0))).terminal)
    }

    @Test
    fun customNoSpeechEndsWithoutSendingAudioForRecognition() {
        val settings = AsrListeningSettings(noSpeechTimeoutMillis = 3_000)
        val policy = VadEndpointPolicy(settings.endpointConfig())
        repeat(149) { assertEquals(emptyList<Pcm16Frame>(), policy.onFrame(frame(0), VadEvent.Silence).framesForAsr) }
        assertEquals(VadEndpointTerminal.Discard(DiscardReason.NO_SPEECH),
            policy.onFrame(frame(0), VadEvent.Silence).terminal)
        assertEquals(0, policy.retainedFrameCountForTest)
    }

    @Test
    fun rejectsInvalidOrUnboundedSettings() {
        listOf<() -> AsrListeningSettings>(
            { AsrListeningSettings(startThresholdRms = 0) },
            { AsrListeningSettings(endSilenceMillis = 400) },
            { AsrListeningSettings(endSilenceMillis = 801) },
            { AsrListeningSettings(noSpeechTimeoutMillis = 30_000) },
            { AsrListeningSettings(noSpeechTimeoutMillis = 3_001) },
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }

    @Test
    fun stableModeDelaysSpeechStartAndSurvivesFourQuietFrames() {
        val vad = AsrListeningSettings(stableDetection = true).createVad()
        repeat(2) { assertEquals(VadEvent.Silence, vad.accept(frame(900))) }
        assertEquals(VadEvent.SpeechStarted, vad.accept(frame(900)))
        repeat(4) { assertEquals(VadEvent.Speech, vad.accept(frame(0))) }
        assertEquals(VadEvent.SpeechEnded, vad.accept(frame(0)))
    }

    private fun frame(amplitude: Int) = Pcm16Frame(16_000, ShortArray(320) { amplitude.toShort() })
}
