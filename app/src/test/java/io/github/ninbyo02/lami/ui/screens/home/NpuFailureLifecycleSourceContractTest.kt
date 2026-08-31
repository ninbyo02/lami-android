package io.github.ninbyo02.lami.ui.screens.home

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuFailureLifecycleSourceContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src").isDirectory }

    @Test
    fun `terminal NPU result cannot fall through into later UI and DB phases`() {
        val source = File(
            root,
            "app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt",
        ).readText()
        val failureBlock = source
            .substringAfter("val npuFailureAssistantText =")
            .substringBefore("npuStandardRouteS1DevTraceText = if")
        val completeIndex = failureBlock.indexOf("finalizeStreamingAssistantMessageSerialized(")
        val failureIndex = failureBlock.indexOf("finalizeStreamingAssistantFailureSerialized(")
        val returnIndex = failureBlock.indexOf("return@launch")

        assertTrue("safe greeting fallback must use completed lifecycle", completeIndex >= 0)
        assertTrue("other NPU failures must use failed lifecycle", failureIndex >= 0)
        assertTrue("terminal NPU lifecycle must return from the launch", returnIndex >= 0)
        assertTrue("completion must precede terminal return", completeIndex < returnIndex)
        assertTrue("failure finalization must precede terminal return", failureIndex < returnIndex)
        assertTrue(
            "safe greeting completion must persist NPU inference statistics",
            "latestInferenceStats = safeGreetingInferenceStats" in failureBlock,
        )
    }
}
