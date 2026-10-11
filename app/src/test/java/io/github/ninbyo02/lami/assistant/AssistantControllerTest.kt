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
    private class FakeConversationAudio : AssistantConversationAudio {
        var starts = 0
        var stops = 0
        var failStart = false
        override suspend fun startConversation() {
            starts++
            if (failStart) error("route failed")
        }
        override suspend fun stopConversation() { stops++ }
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

    @Test fun audioRoute_isHeldAcrossResponseAndNextListeningTurn() = runBlocking {
        val asr = FakeAsr()
        val response = FakeResponse()
        val audio = FakeConversationAudio()
        val controller = AssistantController(asr, response, audio)

        controller.start()
        controller.submitTranscript("最初の質問")
        assertEquals(AssistantState.IDLE, controller.state)
        assertEquals(true, controller.conversationActive)
        assertEquals(1, audio.starts)
        assertEquals(0, audio.stops)

        controller.start()
        assertEquals(2, asr.starts)
        assertEquals(1, audio.starts)
        controller.stop()
        assertEquals(1, audio.stops)
        assertEquals(false, controller.conversationActive)
    }

    @Test fun audioRouteFailure_doesNotStartAsrAndRollsBack() = runBlocking {
        val asr = FakeAsr()
        val audio = FakeConversationAudio().apply { failStart = true }
        val controller = AssistantController(asr, FakeResponse(), audio)

        runCatching { controller.start() }
        assertEquals(0, asr.starts)
        assertEquals(1, audio.starts)
        assertEquals(1, audio.stops)
        assertEquals(false, controller.conversationActive)
        assertEquals(AssistantState.IDLE, controller.state)
    }

    @Test fun responseFailure_releasesHeldAudioRoute() = runBlocking {
        val asr = FakeAsr()
        val audio = FakeConversationAudio()
        val response = object : AssistantResponseAdapter {
            override suspend fun respond(transcript: String) = error("response failed")
            override suspend fun stopResponse() = Unit
        }
        val controller = AssistantController(asr, response, audio)

        controller.start()
        runCatching { controller.submitTranscript("質問") }
        assertEquals(1, audio.stops)
        assertEquals(false, controller.conversationActive)
        assertEquals(AssistantState.IDLE, controller.state)
    }
}
