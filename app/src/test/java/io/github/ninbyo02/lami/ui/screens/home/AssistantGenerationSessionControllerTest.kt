package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantGenerationSessionControllerTest {
    @Test
    fun `finalizing blocks delayed streaming and completion stays terminal`() {
        val controller = AssistantGenerationSessionController()
        controller.start(requestId = 10L, chatId = 3)
        controller.claimMessage(44)
        assertTrue(controller.acceptsStreamingUpdate(10L))

        controller.beginFinalizing()
        assertFalse(controller.acceptsStreamingUpdate(10L))

        controller.complete(44)
        assertTrue(controller.session?.isTerminal == true)
        assertFalse(controller.acceptsStreamingUpdate(10L))
    }

    @Test
    fun `native NPU owns streaming persistence instead of generic UI`() {
        val controller = AssistantGenerationSessionController()
        controller.start(12L, 3)
        assertTrue(controller.session?.genericUiOwnsPersistence == true)
        controller.setStreamingOwner(AssistantGenerationSession.StreamingOwner.NPU_NATIVE)
        assertFalse(controller.session?.genericUiOwnsPersistence == true)
    }

    @Test
    fun `generic backend keeps generic UI persistence ownership`() {
        val controller = AssistantGenerationSessionController()
        controller.start(13L, 4)
        controller.claimMessage(46)
        assertTrue(controller.session?.genericUiOwnsPersistence == true)
        assertTrue(controller.acceptsStreamingUpdate(13L))
    }

    @Test
    fun `stop cancels an active generic backend session`() {
        val controller = AssistantGenerationSessionController()
        controller.start(14L, 4)
        controller.claimMessage(47)
        controller.cancel()
        assertTrue(controller.session?.phase == AssistantGenerationSession.Phase.CANCELLED)
        assertFalse(controller.acceptsStreamingUpdate(14L))
    }

    @Test
    fun `terminal session can be cleared after consumers observe completion`() {
        val controller = AssistantGenerationSessionController()
        controller.start(11L, 3)
        controller.claimMessage(45)
        controller.beginFinalizing()
        controller.complete(45)
        controller.clearTerminal()
        assertNull(controller.session)
    }
}
