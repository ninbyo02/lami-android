package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class ResidentCpRoutingPolicyTest {
    private val hash = "a".repeat(64)
    private val voice = ResidentCpRoutingPolicy.VoiceIdentity(hash, "lami_cute", "qnn")
    @Test fun gpuAndServerLlmDoNotReloadCp() {
        for (route in ResidentCpRoutingPolicy.InferenceRoute.entries) {
            assertFalse(ResidentCpRoutingPolicy.shouldReload(voice, voice, route))
        }
    }
    @Test fun changedVoiceOrBackendMustReload() {
        assertTrue(ResidentCpRoutingPolicy.shouldReload(voice, voice.copy(voiceId = "other"), ResidentCpRoutingPolicy.InferenceRoute.SERVER))
        assertTrue(ResidentCpRoutingPolicy.shouldReload(voice, voice.copy(executor = "cpu"), ResidentCpRoutingPolicy.InferenceRoute.LOCAL_GPU))
        assertTrue(ResidentCpRoutingPolicy.shouldReload(null, voice, ResidentCpRoutingPolicy.InferenceRoute.SERVER))
    }
    @Test fun rejectsInvalidModelIdentity() {
        try {
            ResidentCpRoutingPolicy.VoiceIdentity("bad", "lami_cute", "qnn")
            fail("Expected rejected model")
        } catch (_: IllegalArgumentException) {}
    }
}
