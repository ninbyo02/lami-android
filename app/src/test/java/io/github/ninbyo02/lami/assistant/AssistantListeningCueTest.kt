package io.github.ninbyo02.lami.assistant

import io.github.ninbyo02.lami.tts.LamiVoiceEngine
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantListeningCueTest {
    private class Voice : LamiVoiceEngine {
        override val isSpeaking = MutableStateFlow(false)
        var listener: (Boolean) -> Unit = {}
        val requests = mutableListOf<String>()
        var stops = 0
        override fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) { this.listener = listener }
        override fun speak(text: String) { requests.add(text) }
        override fun speakQueued(text: String) = speak(text)
        override fun stop() { stops++; state(false) }
        override fun shutdown() = stop()
        fun state(playing: Boolean) { isSpeaking.value = playing; listener(playing) }
    }

    @Test fun captureWaitsForCueCompletionNotJustStart() = runTest {
        val voice = Voice()
        var capturing = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            AssistantListeningCue(voice).play()
            capturing = true
        }
        voice.state(false)
        assertFalse(capturing)
        voice.state(true)
        assertFalse(capturing)
        voice.state(false)
        job.join()
        assertTrue(capturing)
        assertEquals(listOf("どうしましたか。"), voice.requests)
    }

    @Test fun repeatedTurnsEachHaveTheirOwnCueCompletion() = runTest {
        val voice = Voice()
        val cue = AssistantListeningCue(voice)
        repeat(2) {
            val job = async(start = CoroutineStart.UNDISPATCHED) { cue.play() }
            assertFalse(job.isCompleted)
            voice.state(true)
            voice.state(false)
            job.await()
        }
        assertEquals(2, voice.requests.size)
        assertEquals(2, voice.stops)
    }

    @Test fun initializationFailureTimesOutWithoutStartingCapture() = runTest {
        val voice = Voice()
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { AssistantListeningCue(voice, timeoutMillis = 100).play() }
        }
        voice.state(false)
        assertTrue(job.await().exceptionOrNull() is TimeoutCancellationException)
        assertEquals(1, voice.stops)
    }

    @Test fun cancellationStopsCueAndRemovesPlaybackListener() = runTest {
        val voice = Voice()
        var capturing = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            AssistantListeningCue(voice).play()
            capturing = true
        }
        voice.state(true)
        job.cancel()
        job.join()
        voice.state(false)
        assertFalse(capturing)
        assertEquals(1, voice.stops)
    }

    @Test fun playbackFailureDoesNotSignalReadiness() = runTest {
        val voice = Voice()
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { AssistantListeningCue(voice, completedNormally = { false }).play() }
        }
        voice.state(true)
        voice.state(false)
        assertTrue(job.await().exceptionOrNull() is IllegalStateException)
        assertEquals(1, voice.stops)
    }
}
