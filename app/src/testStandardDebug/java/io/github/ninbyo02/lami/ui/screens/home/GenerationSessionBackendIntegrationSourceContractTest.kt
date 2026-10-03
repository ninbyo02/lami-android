package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSessionBackendIntegrationSourceContractTest {
    private val source: String by lazy {
        File("src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt").readText()
    }

    @Test
    fun serverRequestStartsGenerationSession() {
        assertTrue(source.contains("remoteStopRequested = false"))
        assertTrue(source.contains("assistantGenerationSessionController.start("))
    }

    @Test
    fun genericCompletionUsesSessionTerminalTransition() {
        assertTrue(source.contains("assistantGenerationSessionController.beginFinalizing()"))
        assertTrue(source.contains("assistantGenerationSessionController.complete(assistantId)"))
    }

    @Test
    fun stopCancelsActiveGenerationSession() {
        assertTrue(
            source.contains(
                "if (reason == \"stop\") assistantGenerationSessionController.cancel()"
            )
        )
    }

    @Test
    fun nativeNpuKeepsExclusiveStreamingPersistenceOwnership() {
        assertTrue(
            source.contains("AssistantGenerationSession.StreamingOwner.NPU_NATIVE")
        )
        assertTrue(
            source.contains("if (session != null && !session.genericUiOwnsPersistence)")
        )
    }
}
