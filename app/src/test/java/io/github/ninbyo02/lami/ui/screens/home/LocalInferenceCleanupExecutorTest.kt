package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalInferenceCleanupExecutorTest {
    @Test fun `stale generation side effects keep established order`() {
        val calls = mutableListOf<String>()
        val effects = object : LocalInferenceCleanupEffects {
            override fun cancelGpuWatchdog() { calls += "watchdog" }
            override fun cancelInferenceJob() { calls += "job" }
            override fun resetStreamingPlaceholder(reason: String) { calls += "placeholder:$reason" }
            override fun stopTts() { calls += "tts" }
        }
        LocalInferenceCleanupExecutor.executeStaleGenerationSideEffects("stale", effects)
        assertEquals(listOf("watchdog", "job", "placeholder:stale", "tts"), calls)
    }
}
