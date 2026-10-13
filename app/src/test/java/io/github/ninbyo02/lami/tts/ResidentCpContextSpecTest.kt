package io.github.ninbyo02.lami.tts

import org.junit.Assert.*
import org.junit.Test

class ResidentCpContextSpecTest {
    private fun spec() = ResidentCpContextSpec("a".repeat(64), 1024, 16, 32, "cp-fp16-sm8750")
    @Test fun compatibleManifest() { spec().requireCompatible(spec()) }
    @Test fun rejectsChangedCacheLayout() {
        try { spec().requireCompatible(spec().copy(kvCacheCapacity = 16)); fail("expected mismatch") }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun rejectsMalformedContext() {
        try { spec().copy(sha256 = "unknown"); fail("expected hash failure") }
        catch (_: IllegalArgumentException) {}
        try { spec().copy(positionsPerFrame = 15); fail("expected step failure") }
        catch (_: IllegalArgumentException) {}
    }
}
