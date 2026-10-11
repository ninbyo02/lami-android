package io.github.ninbyo02.lami.assistant

import android.app.KeyguardManager
import android.content.Intent
import io.github.ninbyo02.lami.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantEntryActivityTest {
    @Test
    fun assistLaunch_opensMainActivityAndFinishesEntry() {
        val activity = Robolectric.buildActivity(AssistantEntryActivity::class.java).create().get()
        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull(launched)
        assertEquals(MainActivity::class.java.name, launched.component?.className)
        assertTrue(launched.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(launched.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertFalse(launched.getBooleanExtra(EXTRA_ASSISTANT_SHOW_WHEN_LOCKED, false))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun lockedAssistLaunch_requestsLockScreenWindowWithoutDismissingKeyguard() {
        val controller = Robolectric.buildActivity(AssistantEntryActivity::class.java)
        val activity = controller.get()
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        shadowOf(keyguard).setKeyguardLocked(true)
        controller.create()

        val launched = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, launched.component?.className)
        assertTrue(launched.getBooleanExtra(EXTRA_ASSISTANT_SHOW_WHEN_LOCKED, false))
        assertTrue(keyguard.isKeyguardLocked)
        assertTrue(activity.isFinishing)
    }
}
