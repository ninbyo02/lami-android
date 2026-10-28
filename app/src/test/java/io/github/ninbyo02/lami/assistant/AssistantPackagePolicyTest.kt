package io.github.ninbyo02.lami.assistant

import org.junit.Assert.*
import org.junit.Test

class AssistantPackagePolicyTest {
    @Test fun combinedDeliveryAndExistingProbeAreEligible() {
        assertTrue(AssistantPackagePolicy.enabled("io.github.ninbyo02.lami.voiceintegrationtrial", true))
        assertTrue(AssistantPackagePolicy.enabled("io.github.ninbyo02.lami.assistantprobe", true))
    }

    @Test fun ordinaryAndOtherDiagnosticPackagesStayExcluded() {
        for (name in listOf("io.github.ninbyo02.lami", "other.voiceintegrationtrial",
                "io.github.ninbyo02.lami.pcmhybridfixedprobe", "io.github.ninbyo02.lami.npuvalidation")) {
            assertFalse(name, AssistantPackagePolicy.enabled(name, true))
        }
    }

    @Test fun releaseBuildCannotEnableHeadsetExperiment() {
        assertFalse(AssistantPackagePolicy.enabled("io.github.ninbyo02.lami.voiceintegrationtrial", false))
        assertFalse(AssistantPackagePolicy.enabled("io.github.ninbyo02.lami.assistantprobe", false))
    }
}
