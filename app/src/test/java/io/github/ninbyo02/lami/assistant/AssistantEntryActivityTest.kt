package io.github.ninbyo02.lami.assistant

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
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
        assertTrue(launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(launched.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertFalse(launched.getBooleanExtra(EXTRA_ASSISTANT_SHOW_WHEN_LOCKED, false))
        assertTrue(launched.getBooleanExtra(EXTRA_ASSISTANT_START_LISTENING, false))
        assertFalse(launched.getBooleanExtra(EXTRA_ASSISTANT_AUTO_SUBMIT, true))
        assertTrue(activity.isFinishing)
    }

    @Test fun reusedEntryForwardsEachExplicitInvocation() {
        val controller = Robolectric.buildActivity(AssistantEntryActivity::class.java).create()
        val activity = controller.get()
        assertTrue(shadowOf(activity).nextStartedActivity.getBooleanExtra(EXTRA_ASSISTANT_START_LISTENING, false))
        controller.newIntent(Intent(Intent.ACTION_ASSIST))
        assertTrue(shadowOf(activity).nextStartedActivity.getBooleanExtra(EXTRA_ASSISTANT_START_LISTENING, false))
    }

    @Test fun recreationDoesNotRestartAnOldInvocation() {
        val activity = Robolectric.buildActivity(AssistantEntryActivity::class.java).create(Bundle()).get()
        assertEquals(null, shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test fun freshExplicitAssistWithRestorationStillForwards() {
        val activity = Robolectric.buildActivity(AssistantEntryActivity::class.java,
            Intent(Intent.ACTION_ASSIST)).create(Bundle()).get()
        assertTrue(shadowOf(activity).nextStartedActivity.getBooleanExtra(EXTRA_ASSISTANT_START_LISTENING, false))
        assertEquals(null, shadowOf(activity).nextStartedActivity)
    }

    @Test fun restoringConsumedAssistCannotStartAnotherRecording() {
        val request = Intent(Intent.ACTION_ASSIST)
        val first = Robolectric.buildActivity(AssistantEntryActivity::class.java, request).create().get()
        assertNotNull(shadowOf(first).nextStartedActivity)
        val restored = Robolectric.buildActivity(AssistantEntryActivity::class.java, request).create(Bundle()).get()
        assertEquals(null, shadowOf(restored).nextStartedActivity)
    }

    @Test fun duplicateConsumedIntentIsNotForwardedButNextFreshIntentIs() {
        val request = Intent(Intent.ACTION_ASSIST)
        val controller = Robolectric.buildActivity(AssistantEntryActivity::class.java, request).create()
        val activity = controller.get()
        assertNotNull(shadowOf(activity).nextStartedActivity)
        controller.newIntent(request)
        assertEquals(null, shadowOf(activity).nextStartedActivity)
        controller.newIntent(Intent(Intent.ACTION_ASSIST))
        assertNotNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun savedConsumedStateBlocksReplayEvenIfOsRestoresOriginalUnmarkedIntent() {
        val controller = Robolectric.buildActivity(AssistantEntryActivity::class.java,
            Intent(Intent.ACTION_ASSIST)).create()
        assertNotNull(shadowOf(controller.get()).nextStartedActivity)
        val state = Bundle()
        controller.saveInstanceState(state)
        val restored = Robolectric.buildActivity(AssistantEntryActivity::class.java,
            Intent(Intent.ACTION_ASSIST)).create(state).get()
        assertEquals(null, shadowOf(restored).nextStartedActivity)
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
