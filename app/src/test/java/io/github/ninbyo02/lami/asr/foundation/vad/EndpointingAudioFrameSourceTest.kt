package io.github.ninbyo02.lami.asr.foundation.vad

import io.github.ninbyo02.lami.asr.foundation.audio.AudioFrameSource
import io.github.ninbyo02.lami.asr.foundation.audio.Pcm16Frame
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointingAudioFrameSourceTest {
    @Test
    fun `confirmed speech emits pre-roll and speech frames in order`() = runBlocking {
        val frames = (1..28).map(::frame)
        val events = List(15) { VadEvent.Silence } +
            listOf(VadEvent.SpeechStarted) +
            List(12) { VadEvent.Speech }
        val source = QueueSource(frames)
        val vad = QueueVad(events)
        val endpointing = EndpointingAudioFrameSource(source, vad, VadEndpointPolicy())

        val emitted = List(28) { endpointing.read()!!.copySamples()[0].toInt() }

        assertEquals((1..28).toList(), emitted)
        endpointing.close()
        assertEquals(1, source.stopCount)
        assertEquals(1, source.closeCount)
        assertEquals(1, vad.closeCount)
    }

    @Test
    fun `no speech terminates as discard and cannot publish an empty final`() {
        val config = VadEndpointConfig(
            preRollFrames = 1,
            postRollFrames = 1,
            minimumSpeechFrames = 1,
            endSilenceFrames = 2,
            noSpeechTimeoutFrames = 3,
            hardLimitFrames = 10,
        )
        val endpointing = EndpointingAudioFrameSource(
            QueueSource(List(3) { frame(it + 1) }),
            QueueVad(List(3) { VadEvent.Silence }),
            VadEndpointPolicy(config),
        )

        val failure = assertThrows(VadInputDiscardedException::class.java) {
            runBlocking { endpointing.read() }
        }

        assertEquals(DiscardReason.NO_SPEECH, failure.reason)
        endpointing.close()
    }

    @Test
    fun `terminal observer distinguishes no speech and cannot alter capture`() = runBlocking {
        val terminals = mutableListOf<VadEndpointTerminal>()
        val config = VadEndpointConfig(preRollFrames = 1, postRollFrames = 1,
            minimumSpeechFrames = 1, endSilenceFrames = 2, noSpeechTimeoutFrames = 3, hardLimitFrames = 10)
        val endpointing = EndpointingAudioFrameSource(
            QueueSource(List(3) { frame(it + 1) }), QueueVad(List(3) { VadEvent.Silence }),
            VadEndpointPolicy(config, onTerminal = { terminals += it; error("observer failure") }),
        )
        assertThrows(VadInputDiscardedException::class.java) { runBlocking { endpointing.read() } }
        endpointing.close()
        assertEquals(listOf(VadEndpointTerminal.Discard(DiscardReason.NO_SPEECH)), terminals)
    }

    @Test
    fun `end silence drains post-roll and then ends the ASR stream`() = runBlocking {
        val config = VadEndpointConfig(
            preRollFrames = 1,
            postRollFrames = 1,
            minimumSpeechFrames = 1,
            endSilenceFrames = 2,
            noSpeechTimeoutFrames = 5,
            hardLimitFrames = 10,
        )
        val endpointing = EndpointingAudioFrameSource(
            QueueSource(listOf(frame(1), frame(2), frame(3))),
            QueueVad(listOf(VadEvent.SpeechStarted, VadEvent.SpeechEnded, VadEvent.Silence)),
            VadEndpointPolicy(config),
        )

        assertEquals(1, endpointing.read()!!.copySamples()[0].toInt())
        assertEquals(2, endpointing.read()!!.copySamples()[0].toInt())
        assertNull(endpointing.read())
        endpointing.close()
    }

    private fun frame(value: Int) = Pcm16Frame(16_000, ShortArray(320) { value.toShort() })

    private class QueueSource(frames: List<Pcm16Frame>) : AudioFrameSource {
        private val frames = ArrayDeque(frames)
        var stopCount = 0
        var closeCount = 0

        override suspend fun read(): Pcm16Frame? = frames.pollFirst()

        override fun stop() {
            stopCount += 1
        }

        override fun close() {
            closeCount += 1
        }
    }

    private class QueueVad(events: List<VadEvent>) : VadEngine {
        private val events = ArrayDeque(events)
        var closeCount = 0

        override fun accept(frame: Pcm16Frame): VadEvent =
            checkNotNull(events.pollFirst())

        override fun reset() = Unit

        override fun close() {
            closeCount += 1
        }
    }
}
