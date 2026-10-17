package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class LamiVoiceTrialPolicyTest {
    @Test fun deliveryPackageStreamsWithoutReadingOrWritingPreferences() {
        assertTrue(LamiVoiceTrialPolicy.streamingEnabled(
            "io.github.ninbyo02.lami.voiceintegrationtrial",
        ) { error("Delivery trial must not depend on existing settings") })
    }

    @Test fun ordinaryPackagesRemainNonStreamingEvenWithStaleOptIn() {
        for (name in listOf(
            "io.github.ninbyo02.lami",
            "io.github.ninbyo02.lami.assistantprobe",
            "io.github.ninbyo02.lami.cpdiagnostic",
            "other.voiceintegrationtrial",
            "io.github.ninbyo02.lami.voiceintegrationtrial.assistantprobe",
        )) {
            assertFalse(name, LamiVoiceTrialPolicy.streamingEnabled(name) {
                error("Unrelated packages must not read the trial setting")
            })
        }
    }

    @Test fun existingValidationOptInIsPreserved() {
        val name = "io.github.ninbyo02.lami.npuvalidation"
        assertFalse(LamiVoiceTrialPolicy.streamingEnabled(name) { false })
        assertTrue(LamiVoiceTrialPolicy.streamingEnabled(name) { true })
    }
}
