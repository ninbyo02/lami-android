package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.*
import org.junit.Test

class StandardGpuOpenClRouteTest {
    @Test fun `verified OpenCL route uses incremental callback instead of forced blocking`() {
        assertFalse(shouldUseHeldOfficialBlockingFastPath("standard", PreferredBackendDryRunSetting.GPU, GPU_GENERATE_PROBE_MODE_NORMAL, true, true))
    }
    @Test fun `unverified runtime retains blocking route`() {
        assertTrue(shouldUseHeldOfficialBlockingFastPath("standard", PreferredBackendDryRunSetting.GPU, GPU_GENERATE_PROBE_MODE_NORMAL, true, false))
    }
    @Test fun `explicitly disabled callback retains blocking route`() {
        assertTrue(shouldUseHeldOfficialBlockingFastPath("standard", PreferredBackendDryRunSetting.GPU, GPU_GENERATE_PROBE_MODE_NORMAL, false, true))
    }
    @Test fun `verified runtime selects GPU callback without reading Android properties`() {
        assertTrue(isGpuNormalRouteUseCallbackStreamingRequestedForDebug(
            preferredBackend = PreferredBackendDryRunSetting.GPU,
            propertyReader = { error("verified runtime must not depend on debug properties") },
            verifiedOpenClRuntime = true,
        ))
        assertFalse(isGpuNormalRouteUseCallbackStreamingRequestedForDebug(
            preferredBackend = PreferredBackendDryRunSetting.CPU,
            propertyReader = { error("CPU must not read GPU debug properties") },
            verifiedOpenClRuntime = true,
        ))
    }
}
