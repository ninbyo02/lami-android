package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DevSessionAsyncPocRemovalContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `production source excludes the removed dev session async poc`() {
        val forbiddenTokens = listOf(
            "ENABLE_DEV_LLM_SESSION_ASYNC_POC",
            "DEV_LLM_SESSION_ASYNC_POC",
            "DEV_POC",
            "sessionAsyncPoc",
            "DevSessionAsyncPoc",
            "enableDevLlmSessionAsyncPoc",
            "session-async-poc",
            "1+1を短く答えてください。",
        )
        val productionSourceRoot = File(root, "app/src/main")
        val matches = productionSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "kts", "xml") }
            .flatMap { file ->
                val source = file.readText()
                forbiddenTokens.asSequence()
                    .filter { token -> source.contains(token) }
                    .map { token -> "${file.relativeTo(root)}: $token" }
            }
            .toList()
        val failureMessage = buildString {
            appendLine("Removed DEV session async PoC leaked into production sources:")
            matches.forEach { match -> appendLine(match) }
        }

        assertTrue(failureMessage, matches.isEmpty())
    }

    @Test
    fun `legacy reflection returns only the response generated for the user prompt`() {
        val source = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val generationBlock = source
            .substringAfter("private fun generateLiteRtStringResponseOnceViaReflection(")
            .substringBefore("private fun localReflectionTraceLine(")

        assertTrue(
            "legacy reflection must label the selected response as one-shot",
            "selectedAssistantResponseSource = LOCAL_ASSISTANT_RESPONSE_SOURCE_ONE_SHOT" in generationBlock,
        )
        assertTrue(
            "legacy reflection must return the one-shot response generated from the user prompt",
            "return LocalLiteRtGeneratedResponse(response = oneShotResponse" in generationBlock,
        )
    }
}
