package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChatScreenTtsOrderingSourceTest {
    @Test
    fun `normal success path queues TTS before resetting UiState`() {
        val source = File("src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt").readText()
        val successBranchStart = source.indexOf("is UiState.Success ->")
        assertTrue("Success branch must exist", successBranchStart >= 0)
        val errorBranchStart = source.indexOf("is UiState.Error ->", successBranchStart)
        assertTrue("Error branch must follow Success branch", errorBranchStart > successBranchStart)
        val successBranch = source.substring(successBranchStart, errorBranchStart)

        val normalPathStart = successBranch.indexOf("val response =")
        assertTrue("Normal success path must exist", normalPathStart >= 0)
        val normalPath = successBranch.substring(normalPathStart)

        val speechTailIndex = normalPath.indexOf("speakStreamingTailIfNeeded(response)")
        val speechFullIndex = normalPath.indexOf("ttsController.speak(speechText)")
        val resetIndex = normalPath.indexOf("viewModel.resetUiState()")
        val declarationCount = Regex("var streamingSpeechStateResetForQueuedTail = false").findAll(normalPath).count()

        assertTrue("Streaming final-tail TTS should be queued in normal Success path", speechTailIndex >= 0)
        assertTrue("Full response TTS should be queued in normal Success path", speechFullIndex >= 0)
        assertTrue("UiState reset should happen in normal Success path", resetIndex >= 0)
        assertTrue("Streaming final-tail TTS must be queued before resetUiState", speechTailIndex < resetIndex)
        assertTrue("Full response TTS must be queued before resetUiState", speechFullIndex < resetIndex)
        assertTrue("streamingSpeechStateResetForQueuedTail must be declared once", declarationCount == 1)
    }

    @Test
    fun `normal success path has no yield before TTS scheduling`() {
        val source = File("src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt").readText()
        val successBranchStart = source.indexOf("is UiState.Success ->")
        assertTrue("Success branch must exist", successBranchStart >= 0)
        val errorBranchStart = source.indexOf("is UiState.Error ->", successBranchStart)
        assertTrue("Error branch must follow Success branch", errorBranchStart > successBranchStart)
        val successBranch = source.substring(successBranchStart, errorBranchStart)

        val normalPathStart = successBranch.indexOf("val response =")
        assertTrue("Normal success path must exist", normalPathStart >= 0)
        val normalPath = successBranch.substring(normalPathStart)

        val speechTailIndex = normalPath.indexOf("speakStreamingTailIfNeeded(response)")
        val resetIndex = normalPath.indexOf("viewModel.resetUiState()")
        val yieldIndex = normalPath.indexOf("yield()")

        assertTrue("Streaming final-tail TTS should be queued in normal Success path", speechTailIndex >= 0)
        assertTrue("UiState reset should happen in normal Success path", resetIndex >= 0)
        assertTrue(
            "yield() must not run before TTS scheduling in the normal Success path",
            yieldIndex < 0 || yieldIndex > resetIndex || yieldIndex > speechTailIndex,
        )
    }
}
