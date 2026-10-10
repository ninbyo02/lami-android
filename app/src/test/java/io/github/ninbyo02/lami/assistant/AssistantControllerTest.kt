package io.github.ninbyo02.lami.assistant

import io.github.ninbyo02.lami.assistant.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantControllerTest {
    private class FakeAsr : AssistantAsrAdapter {
        var starts = 0
        var stops = 0
        override suspend fun startListening() { starts++ }
        override suspend fun stopListening() { stops++ }
    }
    private class FakeResponse : AssistantResponseAdapter {
        val messages = mutableListOf<String>()
        var stops = 0
        override suspend fun respond(transcript: String) { messages += transcript }
        override suspend fun stopResponse() { stops++ }
    }

    @Test fun transcribedTextFlowsToResponseAndReturnsIdle() = runBlocking {
        val asr = FakeAsr()
        val response = FakeResponse()
        val controller = AssistantController(asr, response)
        controller.start()
        assertEquals(AssistantState.LISTENING, controller.state)
        controller.submitTranscript("LAMI、こんにちは")
        assertEquals(listOf("LAMI、こんにちは"), response.messages)
        assertEquals(1, asr.stops)
        assertEquals(AssistantState.IDLE, controller.state)
    }

    @Test fun cancellationAndBlankTranscriptsAreSafe() = runBlocking {
        val asr = FakeAsr()
        val response = FakeResponse()
        val controller = AssistantController(asr, response)
        controller.start()
        controller.start()
        controller.submitTranscript("  ")
        assertEquals(1, asr.starts)
        assertEquals(emptyList<String>(), response.messages)
        controller.stop()
        assertEquals(1, asr.stops)
        assertEquals(AssistantState.IDLE, controller.state)
    }
}
