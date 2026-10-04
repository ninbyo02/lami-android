package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainGenerationOwnershipSourceContractTest {
    private val source by lazy {
        File("src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt").readText()
    }

    @Test
    fun `server npu and local share the durable assistant lifecycle entry point`() {
        val calls = Regex("""startStreamingAssistantLifecycleSerialized\(""")
            .findAll(source)
            .count()

        // One declaration plus Server, NPU and generic Local call sites.
        assertTrue("expected shared lifecycle entry point for all backends", calls >= 4)
        assertTrue(source.contains("Failed to start server generation"))
        assertTrue(source.contains("Failed to start NPU generation"))
        assertTrue(source.contains("Failed to start local generation"))
    }

    @Test
    fun `placeholder start and completion resolve session owned message id`() {
        val ownershipCalls = Regex("""assistantGenerationSessionController\.ownedMessageId\(""")
            .findAll(source)
            .count()

        assertEquals(2, ownershipCalls)
    }

    @Test
    fun `terminal generation session cannot trigger stale placeholder recovery`() {
        assertTrue(
            source.contains(
                "assistantGenerationSessionController.session?.isTerminal != true",
            ),
        )
    }
}
