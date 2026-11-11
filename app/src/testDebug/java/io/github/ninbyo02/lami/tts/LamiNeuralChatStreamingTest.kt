package io.github.ninbyo02.lami.tts

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
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
    @Test fun explicitShortReplyUsesStreamingAndFlushesFinalPrefixBeforeIdle() = runTest {
        val eos = CompletableDeferred<Unit>()
        val heard = mutableListOf<Float>()
        val utterances = mutableListOf<String>()
        val engine = LamiNeuralChatVoiceEngine(
            RuntimeEnvironment.getApplication(), File("unused"), streamingEnabled = true,
            scope = backgroundScope,
            planText = { text, _ -> listOf(text) },
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
        engine.speak("ordinary reply tail")
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
            planText = { text, _ -> listOf(text) },
            synthesizeSpeech = { _, _ -> floatArrayOf(0.1f) },
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
        engine.speak("first")
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

    @Test fun audioStartedIsDeliveredOnlyAtPlayerStartBoundary() = runTest {
        val ready = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        var starts = 0
        val engine = LamiNeuralChatVoiceEngine(
            RuntimeEnvironment.getApplication(), File("unused"), streamingEnabled = true,
            scope = backgroundScope,
            planText = { text, _ -> listOf(text) },
            streamSpeech = { _, _, started ->
                ready.await()
                started()
                finished.await()
                floatArrayOf(0.1f)
            },
        )
        engine.setOnAudioStarted { starts++ }
        engine.speak("こんにちは。")
        runCurrent()
        assertEquals(0, starts)
        ready.complete(Unit)
        runCurrent()
        assertEquals(1, starts)
        assertTrue(engine.isSpeaking.value)
        finished.complete(Unit)
        runCurrent()
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun streamingFailureIsReportedWithoutClaimingAudioStarted() = runTest {
        var starts = 0
        val errors = mutableListOf<String>()
        val engine = LamiNeuralChatVoiceEngine(
            RuntimeEnvironment.getApplication(), File("unused"), streamingEnabled = true,
            scope = backgroundScope,
            planText = { text, _ -> listOf(text) },
            streamSpeech = { _, _, _ -> error("decoder unavailable") },
        )
        engine.setOnAudioStarted { starts++ }
        engine.setOnError(errors::add)
        engine.speak("こんにちは。")
        runCurrent()
        assertEquals(0, starts)
        assertEquals(1, errors.size)
        assertTrue(errors.single().contains("decoder unavailable"))
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun selectedStreamingReportsAudioBeforeFullPcmGenerationAndFlushesTailExactlyOnce() = runTest {
        val eos = CompletableDeferred<Unit>()
        val events = mutableListOf<TtsStreamEvent>()
        val heard = mutableListOf<Float>()
        var starts = 0
        val engine = LamiNeuralChatVoiceEngine(
            RuntimeEnvironment.getApplication(), File("unused"), streamingEnabled = true,
            scope = backgroundScope,
            planText = { text, _ -> listOf(text) },
            streamSpeechWithProgress = { _, _, started, progress ->
                progress(TtsStreamingProgress(TtsStreamEvent.GENERATION_STARTED, timestampNs = 1))
                LamiPcmStreamingPipeline.run(
                    frames = { count: Int -> count },
                    generate = { emit -> emit(2); eos.await(); 3 },
                    decode = { count -> FloatArray(count) { (it + 1) / 10f } },
                    play = { chunks ->
                        var first = true
                        for (chunk in chunks) {
                            if (first) {
                                started()
                                progress(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_STARTED, timestampNs = 2))
                                first = false
                            }
                            progress(TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, chunk.size, 3))
                            heard.addAll(chunk.toList())
                        }
                        progress(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_COMPLETED, heard.size, 5))
                    },
                    coalescePendingPrefixes = true,
                    onPcmComplete = { progress(TtsStreamingProgress(TtsStreamEvent.GENERATION_COMPLETED, it.size, 4)) },
                ).pcm
            },
        )
        engine.setOnAudioStarted { starts++ }
        engine.setOnStreamingProgress { events += it.event }
        engine.speak("全文を待たずに読み上げます。")
        runCurrent()
        assertEquals(1, starts)
        assertEquals(listOf(0.1f, 0.2f), heard)
        assertFalse(events.contains(TtsStreamEvent.GENERATION_COMPLETED))
        assertTrue(engine.isSpeaking.value)

        eos.complete(Unit)
        runCurrent()
        assertEquals(listOf(0.1f, 0.2f, 0.3f), heard)
        assertEquals(1, events.count { it == TtsStreamEvent.GENERATION_COMPLETED })
        assertTrue(events.indexOf(TtsStreamEvent.PLAYBACK_STARTED) < events.indexOf(TtsStreamEvent.GENERATION_COMPLETED))
        assertTrue(events.indexOf(TtsStreamEvent.GENERATION_COMPLETED) < events.indexOf(TtsStreamEvent.PLAYBACK_COMPLETED))
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun microphoneBarrierJoinsStreamingCleanupBeforeReturning() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        var released = false
        val engine = LamiNeuralChatVoiceEngine(
            RuntimeEnvironment.getApplication(), File("unused"), streamingEnabled = true,
            scope = backgroundScope,
            planText = { text, _ -> listOf(text) },
            streamSpeech = { _, _, started ->
                started()
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) { cleanup.await(); released = true }
                }
            },
        )
        engine.speak("停止を待ちます。")
        runCurrent()
        val barrier = async { engine.stopForVoiceInput() }
        runCurrent()
        assertFalse(barrier.isCompleted)
        assertFalse(released)
        cleanup.complete(Unit)
        barrier.await()
        assertTrue(released)
        engine.shutdown()
    }
}
