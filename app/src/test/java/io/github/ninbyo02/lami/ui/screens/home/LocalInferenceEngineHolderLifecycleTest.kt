package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        holder.recordGpuGenerationStartedForDiagnostics(nowElapsedMs = 1_500L)
        holder.recordGpuUiAppendFinishedForDiagnostics(nowElapsedMs = 1_800L)
        holder.recordGpuGenerationFinishedForDiagnostics(success = true, nowElapsedMs = 1_900L)

        holder.notifyAppBackgrounded(nowElapsedMs = 2_000L)

        val snapshot = holder.getDevDiagnosticSnapshot()
        assertNull(snapshot.heldEngineHash)
        assertFalse(snapshot.appInForeground)
        assertEquals("app-backgrounded", snapshot.lastLifecycleEventReason)
        assertEquals("CLOSE_AND_RECREATE", snapshot.lastLifecycleDecisionAction)
        assertEquals(1, closeCount)
        assertEquals("clear_after_success", snapshot.gpuHolderLifecycleEventAfterSuccess)
        assertEquals("background", snapshot.gpuHolderLifecycleLastActivityState)
        assertEquals("app-backgrounded", snapshot.gpuHolderLifecycleClearReasonDetail)
        assertEquals(100L, snapshot.gpuHolderLifecycleClearAfterSuccessMs)
        assertEquals(false, snapshot.gpuHolderLifecycleClearDuringActiveGenerate)
        assertEquals(true, snapshot.gpuHolderLifecycleClearAfterUiAppend)
        assertEquals("HeldEngineLifecycleBridge.onStop", snapshot.gpuHolderLifecycleBackgroundDetectionSource)
    }

    @Test
    fun `transient onStop after GPU success keeps held engine for next turn`() = runTest {
        val holder = LocalInferenceEngineHolder(
            appContext = RuntimeEnvironment.getApplication(),
            gpuTransientOnStopProtectionOverrideForTest = true,
        )
        var closeCount = 0
        holder.setHeldForTest(createHeldEngineForTest { closeCount += 1 })
        holder.recordGpuGenerationStartedForDiagnostics(nowElapsedMs = 1_000L)
        holder.recordGpuUiAppendFinishedForDiagnostics(nowElapsedMs = 1_200L)
        holder.recordGpuGenerationFinishedForDiagnostics(success = true, nowElapsedMs = 1_300L)

        holder.notifyAppBackgrounded(nowElapsedMs = 2_000L)

        val snapshot = holder.getDevDiagnosticSnapshot()
        assertTrue(snapshot.heldEngineHash != null)
        assertFalse(snapshot.appInForeground)
        assertEquals("app-backgrounded", snapshot.lastLifecycleEventReason)
        assertEquals("KEEP_HELD", snapshot.lastLifecycleDecisionAction)
        assertEquals(0, closeCount)
        assertEquals(true, snapshot.gpuHolderLifecycleOnStopDeferred)
        assertEquals("transient_onstop_after_success_ui_append", snapshot.gpuHolderLifecycleOnStopDeferReason)
        assertEquals(true, snapshot.gpuHolderLifecycleClearSuppressedAfterSuccess)
        assertEquals(false, snapshot.gpuHolderLifecycleActualBackgroundConfirmed)
        assertEquals(true, snapshot.gpuHolderLifecycleReuseExpectedNextTurn)
    }

    @Test
    fun `confirmed background after suppress window releases held engine`() = runTest {
        val holder = LocalInferenceEngineHolder(
            appContext = RuntimeEnvironment.getApplication(),
            gpuTransientOnStopProtectionOverrideForTest = true,
        )
        var closeCount = 0
        holder.setHeldForTest(createHeldEngineForTest { closeCount += 1 })
        holder.recordGpuGenerationStartedForDiagnostics(nowElapsedMs = 1_000L)
        holder.recordGpuUiAppendFinishedForDiagnostics(nowElapsedMs = 1_200L)
        holder.recordGpuGenerationFinishedForDiagnostics(success = true, nowElapsedMs = 1_300L)

        holder.notifyAppBackgrounded(nowElapsedMs = 7_000L)

        val snapshot = holder.getDevDiagnosticSnapshot()
        assertNull(snapshot.heldEngineHash)
        assertEquals(1, closeCount)
        assertEquals(false, snapshot.gpuHolderLifecycleOnStopDeferred)
        assertEquals(false, snapshot.gpuHolderLifecycleClearSuppressedAfterSuccess)
        assertEquals(true, snapshot.gpuHolderLifecycleActualBackgroundConfirmed)
        assertEquals(false, snapshot.gpuHolderLifecycleReuseExpectedNextTurn)
    }

    @Test
    fun `active GPU generate defers onStop close`() = runTest {
        val holder = LocalInferenceEngineHolder(
            appContext = RuntimeEnvironment.getApplication(),
            gpuTransientOnStopProtectionOverrideForTest = true,
        )
        var closeCount = 0
        holder.setHeldForTest(createHeldEngineForTest { closeCount += 1 })
        holder.recordGpuGenerationStartedForDiagnostics(nowElapsedMs = 1_000L)

        holder.notifyAppBackgrounded(nowElapsedMs = 2_000L)

        val snapshot = holder.getDevDiagnosticSnapshot()
        assertTrue(snapshot.heldEngineHash != null)
        assertEquals(0, closeCount)
        assertEquals(true, snapshot.gpuHolderLifecycleOnStopDeferred)
        assertEquals("active_generate", snapshot.gpuHolderLifecycleOnStopDeferReason)
        assertEquals(true, snapshot.gpuHolderLifecycleClearDuringActiveGenerate)
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

    @Test
    fun `model switch invalidates held engine safely`() = runTest {
        val holder = LocalInferenceEngineHolder(RuntimeEnvironment.getApplication())
        var closeCount = 0
        holder.setHeldForTest(createHeldEngineForTest { closeCount += 1 })

        holder.clearIfModelChanged("/models/gemma-next.litertlm")

        val snapshot = holder.getDevDiagnosticSnapshot()
        assertNull(snapshot.heldEngineHash)
        assertEquals("clear-model-changed", snapshot.heldEngineDestroyReason)
        assertEquals(1, closeCount)
    }

    @Test
    fun `backend switch invalidates held engine safely`() = runTest {
        val holder = LocalInferenceEngineHolder(RuntimeEnvironment.getApplication())
        var closeCount = 0
        holder.setHeldForTest(createHeldEngineForTest { closeCount += 1 })

        holder.notifyLifecycleEvent(reason = "backend-changed")

        val snapshot = holder.getDevDiagnosticSnapshot()
        assertNull(snapshot.heldEngineHash)
        assertEquals("backend-changed", snapshot.lastLifecycleEventReason)
        assertEquals("CLOSE_AND_RECREATE", snapshot.lastLifecycleDecisionAction)
        assertEquals(1, closeCount)
    }

    @Test
    fun `CPU acquire key does not match existing GPU held engine`() = runTest {
        val holder = LocalInferenceEngineHolder(RuntimeEnvironment.getApplication())
        holder.setHeldForTest(createHeldEngineForTest {})

        assertTrue(
            holder.hasReusableHeldEngineForKey(
                HeldEngineKey(
                    modelPath = "/models/gemma.litertlm",
                    backendKey = "gpu",
                    cacheDirPath = "/cache",
                ),
            ),
        )
        assertFalse(
            holder.hasReusableHeldEngineForKey(
                HeldEngineKey(
                    modelPath = "/models/gemma.litertlm",
                    backendKey = "cpu",
                    cacheDirPath = "/cache",
                ),
            ),
        )
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
            preferredBackendDryRunSetting = PreferredBackendDryRunSetting.GPU,
            closeEngine = { onClose() },
        )
    }
}
