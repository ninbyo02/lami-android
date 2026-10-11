package io.github.ninbyo02.lami.assistant

import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JabraUsbTrialGuardTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun trialExpiresAndReleasesCpuLock() {
        var expired = false
        val guard = JabraUsbTrialGuard(context) { expired = true }
        guard.start()
        assertTrue(guard.held)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(299_999))
        assertTrue(guard.held)
        assertFalse(expired)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertFalse(guard.held)
        assertTrue(expired)
    }

    @Test fun disconnectReleasesAndCancelsExpiry() {
        var expired = false
        val guard = JabraUsbTrialGuard(context) { expired = true }
        guard.start()
        guard.close()
        guard.close()
        assertFalse(guard.held)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(6))
        assertFalse(expired)
    }
}
