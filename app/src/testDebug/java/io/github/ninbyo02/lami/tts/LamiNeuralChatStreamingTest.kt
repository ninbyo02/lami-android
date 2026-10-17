package io.github.ninbyo02.lami.tts

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LamiNeuralChatStreamingTest {
    @Test fun queuedReplyUsesStreamingAndFlushesFinalPrefixBeforeIdle() = runTest {
        val eos = CompletableDeferred<Unit>()
        val heard = mutableListOf<Float>()
        val utterances = mutableListOf<String>()
        val engine = LamiNeuralChatVoiceEngine(
            RuntimeEnvironment.getApplication(), File("unused"), streamingEnabled = true,
            scope = backgroundScope,
            streamSpeech = { text, _, started ->
                utterances.add(text)
                LamiPcmStreamingPipeline.run(
                    frames = { count: Int -> count },
                    generate = { emit -> emit(2); eos.await(); 3 },
                    decode = { count -> FloatArray(count) { (it + 1) / 10f } },
                    play = { chunks ->
                        var first = true
                        for (chunk in chunks) {
                            if (first) { started(); first = false }
                            heard.addAll(chunk.toList())
                        }
                    },
                ).pcm
            },
        )
        engine.speakQueued("ordinary reply tail")
        runCurrent()
        assertEquals(listOf("ordinary reply tail"), utterances)
        assertEquals(listOf(0.1f, 0.2f), heard)
        assertTrue(engine.isSpeaking.value)
        eos.complete(Unit)
        runCurrent()
        assertEquals(listOf(0.1f, 0.2f, 0.3f), heard)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun stopCancelsStreamingChildrenAndDropsQueuedReply() = runTest {
        var generated = false
        var released = false
        val texts = mutableListOf<String>()
        val engine = LamiNeuralChatVoiceEngine(
            RuntimeEnvironment.getApplication(), File("unused"), streamingEnabled = true,
            scope = backgroundScope,
            streamSpeech = { text, _, started ->
                texts.add(text)
                LamiPcmStreamingPipeline.run(
                    frames = { count: Int -> count },
                    generate = { emit ->
                        try { emit(2); awaitCancellation() } finally { generated = true }
                    },
                    decode = { count -> FloatArray(count) { 0.1f } },
                    play = { chunks ->
                        try { for (chunk in chunks) started() } finally { released = true }
                    },
                ).pcm
            },
        )
        engine.speakQueued("first")
        engine.speakQueued("must be dropped")
        runCurrent()
        engine.stop()
        runCurrent()
        assertTrue(generated)
        assertTrue(released)
        assertFalse(engine.isSpeaking.value)
        assertEquals(listOf("first"), texts)
        engine.shutdown()
    }
}
