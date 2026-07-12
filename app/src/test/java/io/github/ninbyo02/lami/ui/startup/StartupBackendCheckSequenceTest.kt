package io.github.ninbyo02.lami.ui.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import io.github.ninbyo02.lami.ui.screens.settings.LocalBackendRuntimeEvidence
import org.junit.Test

class StartupBackendCheckSequenceTest {
    @Test
    fun `initial sequence checks NPU GPU CPU in order`() {
        val sequence = StartupBackendCheckSequence.initial()

        assertEquals(
            listOf(StartupBackend.NPU, StartupBackend.GPU, StartupBackend.CPU),
            sequence.items.map { it.backend },
        )
        assertTrue(sequence.items.all { it.status == StartupBackendStatus.CHECKING })
        assertFalse(sequence.canContinue)
    }

    @Test
    fun `sequence can continue when every backend is resolved`() {
        val sequence = StartupBackendCheckSequence.initial()
            .resolve(StartupBackend.NPU, available = true)
            .resolve(StartupBackend.GPU, available = false)
            .resolve(StartupBackend.CPU, available = true)

        assertEquals(
            listOf(
                StartupBackendStatus.AVAILABLE,
                StartupBackendStatus.UNAVAILABLE,
                StartupBackendStatus.AVAILABLE,
            ),
            sequence.items.map { it.status },
        )
        assertTrue(sequence.canContinue)
        assertFalse(sequence.timedOut)
    }

    @Test
    fun `timeout never blocks app startup and leaves unresolved backends unavailable`() {
        val sequence = StartupBackendCheckSequence.initial()
            .resolve(StartupBackend.NPU, available = true)
            .timeout()

        assertEquals(StartupBackendStatus.AVAILABLE, sequence.item(StartupBackend.NPU).status)
        assertEquals(StartupBackendStatus.UNAVAILABLE, sequence.item(StartupBackend.GPU).status)
        assertEquals(StartupBackendStatus.UNAVAILABLE, sequence.item(StartupBackend.CPU).status)
        assertTrue(sequence.canContinue)
        assertTrue(sequence.timedOut)
    }

    @Test
    fun `status labels are concise Japanese startup copy`() {
        assertEquals("確認中", StartupBackendStatus.CHECKING.label)
        assertEquals("利用可能", StartupBackendStatus.AVAILABLE.label)
        assertEquals("利用不可", StartupBackendStatus.UNAVAILABLE.label)
    }

    @Test
    fun `runtime evidence maps availability in display order`() {
        val availability = startupBackendAvailability(
            LocalBackendRuntimeEvidence(
                npuSupported = true,
                npuHealthy = true,
                gpuSupported = true,
                gpuHealthy = false,
                cpuSupported = true,
                cpuHealthy = true,
            ),
        )

        assertEquals(
            listOf(
                StartupBackend.NPU to true,
                StartupBackend.GPU to false,
                StartupBackend.CPU to true,
            ),
            availability,
        )
    }

    @Test
    fun `cold start shows branded startup splash but activity recreation skips it`() {
        assertEquals(
            StartupPresentation.BACKEND_CHECK_SPLASH,
            initialStartupPresentation(isActivityRecreation = false),
        )
        assertEquals(
            StartupPresentation.APP_CONTENT,
            initialStartupPresentation(isActivityRecreation = true),
        )
    }

    @Test
    fun `resolved sequence transitions to app content only after hold`() {
        val checking = StartupSplashContract.initial(isActivityRecreation = false)
        val resolved = checking.copy(
            sequence = checking.sequence
                .resolve(StartupBackend.NPU, true)
                .resolve(StartupBackend.GPU, true)
                .resolve(StartupBackend.CPU, true),
        )

        assertEquals(StartupPresentation.BACKEND_CHECK_SPLASH, resolved.presentation)
        assertEquals(StartupPresentation.APP_CONTENT, resolved.finish().presentation)
    }

    @Test
    fun `timeout resolves checks and always permits app transition`() {
        val timedOut = StartupSplashContract.initial(isActivityRecreation = false).timeout()

        assertTrue(timedOut.sequence.canContinue)
        assertTrue(timedOut.sequence.timedOut)
        assertEquals(StartupPresentation.APP_CONTENT, timedOut.finish().presentation)
    }
}
