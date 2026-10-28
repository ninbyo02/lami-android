package io.github.ninbyo02.lami.asr.foundation.vad

import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import org.junit.Assert.*
import org.junit.Test

class VoiceInputSilenceChoicesTest {
    @Test fun allFiveChoicesWaitForTheConfiguredSilence() {
        for (seconds in 1..5) {
            val config = AsrListeningSettings(endSilenceMillis = seconds * 1000).endpointConfig()
            val policy = VadEndpointPolicy(config)
            fun frame() = Pcm16Frame(16_000, ShortArray(320))
            policy.onFrame(frame(), VadEvent.SpeechStarted)
            repeat(12) { policy.onFrame(frame(), VadEvent.Speech) }
            policy.onFrame(frame(), VadEvent.SpeechEnded)
            repeat(config.endSilenceFrames - 2) { assertNull(policy.onFrame(frame(), VadEvent.Silence).terminal) }
            assertEquals(VadEndpointTerminal.Finalize(FinalizeReason.END_SILENCE),
                policy.onFrame(frame(), VadEvent.Silence).terminal)
            assertTrue(policy.maxRetainedFrameCountForTest <= 230)
            assertEquals(0, policy.retainedFrameCountForTest)
        }
    }
}
