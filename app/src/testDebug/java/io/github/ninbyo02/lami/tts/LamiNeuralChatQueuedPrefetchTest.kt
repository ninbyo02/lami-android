package io.github.ninbyo02.lami.tts

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LamiNeuralChatQueuedPrefetchTest {
    @Test fun fiveShortQueueAddsOverlapPreparationAndPreserveTextWithOnePreparedPcmAhead() = runTest {
        val texts = listOf("最初の短い文です。", "次の短い文です。", "三つ目の短い文です。", "四つ目の短い文です。", "最後の短い文です。")
        val ready = mutableMapOf<FloatArray, String>()
        val generated = mutableListOf<Pair<String, Long>>()
        val played = mutableListOf<String>()
        val starts = mutableListOf<Long>()
        val ends = mutableListOf<Long>()
        val errors = mutableListOf<String>()
        var maximumReady = 0
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, preferEarlyStart = false, scope = backgroundScope,
            planText = { text, _ -> listOf(text) },
            streamSpeech = { _, _, _ -> error("QUEUE_ADD must use EOS preparation across requests") },
            synthesizeSpeech = { text, _ ->
                generated += text to testScheduler.currentTime
                delay(1_000)
                FloatArray(48_000) { 0.1f }.also {
                    ready[it] = text
                    maximumReady = maxOf(maximumReady, ready.size)
                }
            }, playPreparedSpeech = { pcm, started, progress ->
                played += checkNotNull(ready.remove(pcm))
                starts += testScheduler.currentTime
                started()
                progress(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_STARTED, timestampNs = testScheduler.currentTime * 1_000_000))
                delay(2_000)
                ends += testScheduler.currentTime
                progress(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_COMPLETED, pcm.size,
                    timestampNs = testScheduler.currentTime * 1_000_000))
            })
        engine.setOnError(errors::add)
        texts.forEach(engine::speakQueued)
        advanceTimeBy(12_000)
        runCurrent()
        assertEquals(texts, played)
        assertEquals(texts, generated.map { it.first })
        assertEquals(listOf(1_000L, 3_000L, 5_000L, 7_000L, 9_000L), starts)
        assertEquals(listOf(3_000L, 5_000L, 7_000L, 9_000L, 11_000L), ends)
        assertTrue(generated[1].second < ends[0])
        assertEquals(1, maximumReady)
        assertTrue(errors.isEmpty())
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun microphoneBarrierJoinsQueuedPrefetchCleanupAndNeverStartsItsAudio() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val generated = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var starts = 0
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, preferEarlyStart = false, scope = backgroundScope,
            planText = { text, _ -> listOf(text) }, synthesizeSpeech = { text, _ ->
                generated += text
                if (text == "second") {
                    try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
                }
                floatArrayOf(0.1f)
            }, playPreparedSpeech = { _, started, _ -> started(); awaitCancellation() })
        engine.setOnAudioStarted { starts++ }
        engine.setOnError(errors::add)
        listOf("first", "second", "discard").forEach(engine::speakQueued)
        runCurrent()
        assertEquals(listOf("first", "second"), generated)
        assertEquals(1, starts)
        val barrier = async { engine.stopForVoiceInput() }
        runCurrent()
        assertFalse(barrier.isCompleted)
        cleanup.complete(Unit)
        barrier.await()
        assertEquals(1, starts)
        assertEquals(listOf("first", "second"), generated)
        assertTrue(errors.isEmpty())
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }
}
