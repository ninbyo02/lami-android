package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class LocalInferenceEngineHolderLifecycleTest {
    @Test
    fun `background releases held engine`() = runTest {
        val holder = LocalInferenceEngineHolder(RuntimeEnvironment.getApplication())
        var closeCount = 0
        holder.setHeldForTest(createHeldEngineForTest { closeCount += 1 })

        holder.notifyAppBackgrounded(nowElapsedMs = 2_000L)

        val snapshot = holder.getDevDiagnosticSnapshot()
        assertNull(snapshot.heldEngineHash)
        assertFalse(snapshot.appInForeground)
        assertEquals("app-backgrounded", snapshot.lastLifecycleEventReason)
        assertEquals("CLOSE_AND_RECREATE", snapshot.lastLifecycleDecisionAction)
        assertEquals(1, closeCount)
    }

    @Test
    fun `tts playback releases held engine`() = runTest {
        val holder = LocalInferenceEngineHolder(RuntimeEnvironment.getApplication())
        var closeCount = 0
        holder.setHeldForTest(createHeldEngineForTest { closeCount += 1 })

        holder.notifyLifecycleEvent(reason = "tts-playback")

        val snapshot = holder.getDevDiagnosticSnapshot()
        assertNull(snapshot.heldEngineHash)
        assertEquals("tts-playback", snapshot.lastLifecycleEventReason)
        assertEquals("CLOSE_AND_RECREATE", snapshot.lastLifecycleDecisionAction)
        assertEquals(1, closeCount)
    }

    private fun LocalInferenceEngineHolder.setHeldForTest(engine: HeldLocalEngine) {
        val field = LocalInferenceEngineHolder::class.java.getDeclaredField("held")
        field.isAccessible = true
        field.set(this, engine)
    }

    private fun createHeldEngineForTest(onClose: () -> Unit): HeldLocalEngine {
        return HeldLocalEngine(
            engineKey = HeldEngineKey(
                modelPath = "/models/gemma.litertlm",
                backendKey = "gpu",
                cacheDirPath = "/cache",
            ),
            modelPath = "/models/gemma.litertlm",
            engineInstance = Any(),
            namespace = null,
            createdAtElapsedMs = 1_000L,
            lastUsedAtElapsedMs = 1_000L,
            useCount = 1,
            closeEngine = { onClose() },
        )
    }
}
