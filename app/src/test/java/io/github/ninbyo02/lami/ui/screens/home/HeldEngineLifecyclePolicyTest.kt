package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class HeldEngineLifecyclePolicyTest {
    private val gpuKey = HeldEngineKey(
        modelPath = "/models/gemma.litertlm",
        backendKey = "gpu",
        cacheDirPath = "/cache",
    )

    @Test
    fun `acquire decisions preserve create reuse model and backend behavior`() {
        assertDecision(
            decision = decideHeldEngineAcquireLifecycle(
                currentEngineKey = null,
                currentModelPath = null,
                currentUseCount = 0,
                requested = gpuKey,
            ),
            expectedReason = HeldEngineLifecycleReason.KEEP_HELD,
            expectedAction = HeldEngineLifecycleAction.NO_OP,
            expectedClearReason = "keep-held",
        )

        assertDecision(
            decision = decideHeldEngineAcquireLifecycle(
                currentEngineKey = gpuKey,
                currentModelPath = gpuKey.modelPath,
                currentUseCount = 3,
                requested = gpuKey,
            ),
            expectedReason = HeldEngineLifecycleReason.KEEP_HELD,
            expectedAction = HeldEngineLifecycleAction.KEEP_HELD,
            expectedClearReason = "keep-held",
        )

        val nextModel = gpuKey.copy(modelPath = "/models/gemma-next.litertlm")
        assertDecision(
            decision = decideHeldEngineAcquireLifecycle(
                currentEngineKey = gpuKey,
                currentModelPath = gpuKey.modelPath,
                currentUseCount = 1,
                requested = nextModel,
            ),
            expectedReason = HeldEngineLifecycleReason.MODEL_CHANGED,
            expectedAction = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
            expectedClearReason = "model-changed",
        )

        assertDecision(
            decision = decideHeldEngineAcquireLifecycle(
                currentEngineKey = gpuKey,
                currentModelPath = gpuKey.modelPath,
                currentUseCount = 1,
                requested = gpuKey.copy(backendKey = "cpu"),
            ),
            expectedReason = HeldEngineLifecycleReason.BACKEND_CHANGED,
            expectedAction = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
            expectedClearReason = "backend-changed",
        )
    }

    @Test
    fun `destructive lifecycle events all close and recreate`() {
        val expected = mapOf(
            "backend-changed" to HeldEngineLifecycleReason.BACKEND_CHANGED,
            "explicit-reset" to HeldEngineLifecycleReason.EXPLICIT_RESET,
            "fatal-error" to HeldEngineLifecycleReason.FATAL_ERROR,
            "low-memory" to HeldEngineLifecycleReason.LOW_MEMORY,
            "app-backgrounded" to HeldEngineLifecycleReason.APP_BACKGROUNDED,
            "tts-playback" to HeldEngineLifecycleReason.TTS_PLAYBACK,
            "background-timeout" to HeldEngineLifecycleReason.BACKGROUND_TIMEOUT,
            "idle-timeout" to HeldEngineLifecycleReason.IDLE_TIMEOUT,
        )

        expected.forEach { (event, reason) ->
            assertDecision(
                decision = resolveHeldEngineLifecycleDecision(event),
                expectedReason = reason,
                expectedAction = HeldEngineLifecycleAction.CLOSE_AND_RECREATE,
                expectedClearReason = event,
            )
        }
    }

    @Test
    fun `unknown lifecycle event clears conversations without invalidating engine`() {
        assertDecision(
            decision = resolveHeldEngineLifecycleDecision("conversation-reset"),
            expectedReason = HeldEngineLifecycleReason.KEEP_HELD,
            expectedAction = HeldEngineLifecycleAction.CLEAR_ONLY,
            expectedClearReason = "conversation-reset",
        )
    }

    @Test
    fun `GPU onStop policy distinguishes active recent and confirmed background`() {
        assertEquals(
            GpuOnStopLifecycleAction.DEFER_ACTIVE_GENERATE,
            resolveGpuOnStopLifecycleAction(
                gpuGenerateActive = true,
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                transientProtectionEnabled = true,
                recentSuccess = false,
            ),
        )
        assertEquals(
            GpuOnStopLifecycleAction.KEEP_FOREGROUND_REUSE,
            resolveGpuOnStopLifecycleAction(
                gpuGenerateActive = false,
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                transientProtectionEnabled = true,
                recentSuccess = true,
            ),
        )
        assertEquals(
            GpuOnStopLifecycleAction.RELEASE_CONFIRMED_BACKGROUND,
            resolveGpuOnStopLifecycleAction(
                gpuGenerateActive = false,
                preferredBackend = PreferredBackendDryRunSetting.GPU,
                transientProtectionEnabled = true,
                recentSuccess = false,
            ),
        )
        assertEquals(
            GpuOnStopLifecycleAction.RELEASE_CONFIRMED_BACKGROUND,
            resolveGpuOnStopLifecycleAction(
                gpuGenerateActive = true,
                preferredBackend = PreferredBackendDryRunSetting.NPU,
                transientProtectionEnabled = true,
                recentSuccess = true,
            ),
        )
    }

    private fun assertDecision(
        decision: HeldEngineLifecycleDecision,
        expectedReason: HeldEngineLifecycleReason,
        expectedAction: HeldEngineLifecycleAction,
        expectedClearReason: String,
    ) {
        assertEquals(expectedReason, decision.reason)
        assertEquals(expectedAction, decision.action)
        assertEquals(expectedClearReason, decision.clearReason)
    }
}
