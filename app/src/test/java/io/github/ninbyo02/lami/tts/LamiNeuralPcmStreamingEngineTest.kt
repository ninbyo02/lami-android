package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class LamiNeuralPcmStreamingEngineTest {
    @Test fun `reverse scheduling cannot let prefetch occupy native before current speech`() {
        val dispatcher = ReverseQueuedDispatcher()
        val native = Mutex()
        val entered = mutableListOf<String>()
        val heard = mutableListOf<Float>()
        val session = object : StreamingTestSession() {
            override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                // Like the real Supertonic executor, this resource stays owned
                // while its first PCM waits for the actual playback-start ack.
                native.withLock {
                    entered += text
                    emit(floatArrayOf(if (text == "最初。") 0.1f else 0.2f))
                }
            }
        }
        val engine = LamiNeuralPcmVoiceEngine(CoroutineScope(SupervisorJob() + dispatcher), session,
            { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, started, report ->
                consume(chunks, started, report, heard)
            })
        try {
            engine.speakQueued("最初。")
            engine.speakQueued("次。")
            dispatcher.drain()

            assertEquals(listOf("最初。", "次。"), entered)
            assertEquals(listOf(0.1f, 0.2f), heard)
            assertFalse(engine.isSpeaking.value)
        } finally {
            engine.shutdown()
            dispatcher.drain()
        }
        assertEquals(1, session.closeCount)
    }

    @Test fun `reverse scheduling stop cancels prepared native callback and preserves cleanup before reuse`() {
        val dispatcher = ReverseQueuedDispatcher()
        val native = Mutex()
        val finishFirstPlayback = CompletableDeferred<Unit>()
        val entered = mutableListOf<String>()
        val heard = mutableListOf<Float>()
        val released = mutableListOf<String>()
        val session = object : StreamingTestSession() {
            override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                native.withLock {
                    entered += text
                    try {
                        emit(floatArrayOf(if (text == "置換。") 0.3f else 0.1f))
                    } finally {
                        released += text
                    }
                }
            }
        }
        var playbacks = 0
        val engine = LamiNeuralPcmVoiceEngine(CoroutineScope(SupervisorJob() + dispatcher), session,
            { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, started, report ->
                val first = ++playbacks == 1
                consume(chunks, started, report, heard)
                if (first) finishFirstPlayback.await()
            })
        try {
            engine.speakQueued("最初。")
            engine.speakQueued("破棄。")
            dispatcher.drain()
            assertEquals(listOf("最初。", "破棄。"), entered)
            assertEquals(listOf("最初。"), released)
            assertEquals(listOf(0.1f), heard)
            assertTrue(engine.isSpeaking.value)

            engine.stop()
            engine.speakQueued("置換。")
            dispatcher.drain()

            assertEquals(listOf("最初。", "破棄。", "置換。"), released)
            assertEquals(listOf(0.1f, 0.3f), heard)
            assertFalse(engine.isSpeaking.value)
        } finally {
            engine.shutdown()
            dispatcher.drain()
        }
        assertEquals(1, session.closeCount)
    }

    @Test fun `queued streaming speech resumes after repeated long LLM supply gaps without replacing the session`() = runTest {
        val prepared = mutableListOf<String>()
        val heard = mutableListOf<Float>()
        val busy = mutableListOf<Boolean>()
        val session = object : StreamingTestSession() {
            override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                prepared += text
                emit(floatArrayOf(prepared.size / 10f))
            }
        }
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, session,
            { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, started, report ->
                consume(chunks, started, report, heard)
            })
        engine.setOnPlaybackStateChanged(busy::add)
        for (text in listOf("最初です。", "推論が再開しました。", "最後まで読みます。")) {
            engine.speakQueued(text)
            runCurrent()
            assertEquals(text, prepared.last())
            assertFalse(engine.isSpeaking.value)
            // No end-of-response signal is sent while the LLM has no new text.
            advanceTimeBy(65_000)
            runCurrent()
        }

        assertEquals(listOf(0.1f, 0.2f, 0.3f), heard)
        assertEquals(listOf(true, false, true, false, true, false), busy)
        assertEquals(0, session.cancelCount)
        assertEquals(0, session.closeCount)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `a failed streaming request does not prevent later queued speech after idle`() = runTest {
        var attempts = 0
        val heard = mutableListOf<Float>()
        val errors = mutableListOf<String>()
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : StreamingTestSession() {
            override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                if (++attempts == 1) error("temporary native failure")
                emit(floatArrayOf(0.2f))
            }
        }, { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, started, report ->
            consume(chunks, started, report, heard)
        })
        engine.setOnError(errors::add)
        engine.speakQueued("最初の文です。")
        runCurrent()
        assertFalse(engine.isSpeaking.value)
        assertTrue(errors.isNotEmpty())

        advanceTimeBy(65_000)
        engine.speakQueued("次の文は再開できます。")
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(listOf(0.2f), heard)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `large unpunctuated LLM bursts stay bounded and resume through native first-chunk handshakes`() = runTest {
        val prepared = mutableListOf<String>()
        val heard = mutableListOf<Float>()
        val errors = mutableListOf<String>()
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : StreamingTestSession() {
            override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                check(text.codePointCount(0, text.length) <= LamiTtsTextChunks.MAX_PIECE_CODE_POINTS) {
                    "Unbounded text must not reach native duration prediction"
                }
                prepared += text
                emit(floatArrayOf(0.1f))
                emit(floatArrayOf(0.2f))
            }
        }, { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, started, report ->
            consume(chunks, started, report, heard)
        })
        engine.setOnError(errors::add)
        val beforeGap = "あ".repeat(321)
        val afterGap = "い".repeat(161)
        engine.speakQueued(beforeGap)
        runCurrent()
        assertFalse(engine.isSpeaking.value)
        assertEquals(beforeGap, prepared.joinToString(""))

        advanceTimeBy(65_000)
        engine.speakQueued(afterGap)
        runCurrent()

        assertEquals(beforeGap + afterGap, prepared.joinToString(""))
        assertEquals(8, prepared.size)
        assertEquals(List(8) { listOf(0.1f, 0.2f) }.flatten(), heard)
        assertTrue(errors.isEmpty())
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `first audio plays before native generation finishes and short tail plays once`() = runTest {
        val finishGeneration = CompletableDeferred<Unit>()
        var nativeFinished = false
        val heard = mutableListOf<Float>()
        val progress = mutableListOf<TtsStreamEvent>()
        var starts = 0
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : StreamingTestSession() {
            override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                emit(floatArrayOf(0.1f, 0.2f))
                finishGeneration.await()
                emit(floatArrayOf(0.3f))
                nativeFinished = true
            }
        }, { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, started, report ->
            consume(chunks, started, report, heard)
        })
        engine.setOnAudioStarted { starts++ }
        engine.setOnStreamingProgress { progress += it.event }
        engine.speakQueued("全文を待たずに読みます。")
        runCurrent()

        assertEquals(listOf(0.1f, 0.2f), heard)
        assertEquals(1, starts)
        assertFalse(nativeFinished)
        assertFalse(progress.contains(TtsStreamEvent.GENERATION_COMPLETED))
        assertTrue(engine.isSpeaking.value)

        finishGeneration.complete(Unit)
        runCurrent()

        assertTrue(nativeFinished)
        assertEquals(listOf(0.1f, 0.2f, 0.3f), heard)
        assertEquals(1, starts)
        assertEquals(1, progress.count { it == TtsStreamEvent.GENERATION_COMPLETED })
        assertTrue(progress.indexOf(TtsStreamEvent.PLAYBACK_STARTED) < progress.indexOf(TtsStreamEvent.GENERATION_COMPLETED))
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `native callback cannot continue past first chunk before actual playback start`() = runTest {
        val allowStart = CompletableDeferred<Unit>()
        var nativeContinued = false
        val heard = mutableListOf<Float>()
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : StreamingTestSession() {
            override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                emit(floatArrayOf(0.1f))
                nativeContinued = true
                emit(floatArrayOf(0.2f))
            }
        }, { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, started, report ->
            allowStart.await()
            consume(chunks, started, report, heard)
        })
        engine.speakQueued("始まりを待ちます。")
        runCurrent()
        assertFalse(nativeContinued)
        assertTrue(heard.isEmpty())

        allowStart.complete(Unit)
        runCurrent()

        assertTrue(nativeContinued)
        assertEquals(listOf(0.1f, 0.2f), heard)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `only next clause is prepared while first clause is still playing`() = runTest {
        val firstPlaybackDone = CompletableDeferred<Unit>()
        val prepared = mutableListOf<String>()
        val heard = mutableListOf<Float>()
        var playbacks = 0
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : StreamingTestSession() {
            override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                prepared += text
                emit(floatArrayOf(prepared.size / 10f))
            }
        }, { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, started, report ->
            val first = ++playbacks == 1
            consume(chunks, started, report, heard)
            if (first) firstPlaybackDone.await()
        })
        engine.speak("最初。次。最後。")
        runCurrent()

        assertEquals(listOf("最初。", "次。"), prepared)
        assertEquals(listOf(0.1f), heard)
        assertEquals(1, playbacks)
        assertTrue(engine.isSpeaking.value)

        firstPlaybackDone.complete(Unit)
        runCurrent()

        assertEquals(listOf("最初。", "次。", "最後。"), prepared)
        assertEquals(listOf(0.1f, 0.2f, 0.3f), heard)
        assertEquals(3, playbacks)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `microphone barrier and session close wait for native cancellation cleanup`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        var nativeReleased = false
        val session = object : StreamingTestSession() {
            override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                try {
                    emit(floatArrayOf(0.1f))
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { cleanup.await(); nativeReleased = true }
                }
            }
        }
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, session,
            { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, started, report ->
                consume(chunks, started, report, mutableListOf())
            })
        engine.speakQueued("停止の完了を待ちます。")
        runCurrent()
        val barrier = async { engine.stopForVoiceInput() }
        runCurrent()

        assertFalse(barrier.isCompleted)
        assertFalse(nativeReleased)
        assertTrue(session.cancelCount > 0)
        assertEquals(0, session.closeCount)

        cleanup.complete(Unit)
        barrier.await()
        assertTrue(nativeReleased)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
        assertEquals(1, session.closeCount)
    }

    @Test fun `stop before first PCM waits for the initial native model load`() = runTest {
        val finishLoad = CompletableDeferred<Unit>()
        var loading = false
        var loaded = false
        var starts = 0
        val session = object : StreamingTestSession() {
            override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                withContext(NonCancellable) {
                    loading = true
                    finishLoad.await()
                    loaded = true
                }
                emit(floatArrayOf(0.1f))
            }
        }
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, session,
            { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, started, report ->
                consume(chunks, started, report, mutableListOf())
            })
        engine.setOnAudioStarted { starts++ }
        engine.speakQueued("初回ロード中に止めます。")
        runCurrent()
        assertTrue(loading)

        val barrier = async { engine.stopForVoiceInput() }
        runCurrent()
        assertFalse(barrier.isCompleted)
        assertFalse(loaded)
        assertEquals(0, starts)

        finishLoad.complete(Unit)
        barrier.await()
        assertTrue(loaded)
        assertEquals(0, starts)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
        assertEquals(1, session.closeCount)
    }

    @Test fun `playback failure cancels first callback handshake and joins producer cleanup`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        var nativeReleased = false
        val errors = mutableListOf<String>()
        val progress = mutableListOf<TtsStreamEvent>()
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : StreamingTestSession() {
            override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                try {
                    emit(floatArrayOf(0.1f))
                    fail("The failed player must never acknowledge the first callback")
                } finally {
                    withContext(NonCancellable) { cleanup.await(); nativeReleased = true }
                }
            }
        }, { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, _, _ ->
            chunks.receive()
            error("Audio device unavailable")
        })
        engine.setOnError(errors::add)
        engine.setOnStreamingProgress { progress += it.event }
        engine.speakQueued("再生できません。")
        runCurrent()

        assertTrue(errors.any { it.contains("Audio device unavailable") })
        assertFalse(nativeReleased)
        assertTrue(engine.isSpeaking.value)
        assertFalse(progress.contains(TtsStreamEvent.GENERATION_COMPLETED))

        cleanup.complete(Unit)
        runCurrent()

        assertTrue(nativeReleased)
        assertFalse(engine.isSpeaking.value)
        assertFalse(progress.contains(TtsStreamEvent.GENERATION_COMPLETED))
        engine.shutdown()
        runCurrent()
    }

    @Test fun `failed or empty native generation never reports successful audio or completion`() = runTest {
        for (empty in listOf(false, true)) {
            val errors = mutableListOf<String>()
            val progress = mutableListOf<TtsStreamEvent>()
            var starts = 0
            val engine = LamiNeuralPcmVoiceEngine(CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), object : StreamingTestSession() {
                override suspend fun stream(text: String, emit: suspend (FloatArray) -> Unit) {
                    if (!empty) error("Native generation failed")
                }
            }, { _, _ -> fail("Streaming must not use buffered playback") }, streamPcm = { chunks, started, report ->
                consume(chunks, started, report, mutableListOf())
            })
            engine.setOnError(errors::add)
            engine.setOnAudioStarted { starts++ }
            engine.setOnStreamingProgress { progress += it.event }
            engine.speakQueued("失敗を表示します。")
            runCurrent()

            assertTrue(errors.isNotEmpty())
            assertEquals(0, starts)
            assertFalse(progress.contains(TtsStreamEvent.GENERATION_COMPLETED))
            assertFalse(progress.contains(TtsStreamEvent.PLAYBACK_COMPLETED))
            assertFalse(engine.isSpeaking.value)
            engine.shutdown()
            runCurrent()
        }
    }

    private suspend fun consume(
        chunks: ReceiveChannel<FloatArray>,
        started: () -> Unit,
        report: (TtsStreamingProgress) -> Unit,
        heard: MutableList<Float>,
    ) {
        var first = true
        var samples = 0
        for (chunk in chunks) {
            if (first) {
                started()
                report(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_STARTED))
                first = false
            }
            heard.addAll(chunk.toList())
            samples += chunk.size
            report(TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, chunk.size))
        }
        if (!first) report(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_COMPLETED, samples))
    }
}

/** Deterministically runs a newly dispatched producer before an older pending one. */
private class ReverseQueuedDispatcher : CoroutineDispatcher() {
    private val pending = ArrayDeque<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) { pending.addLast(block) }
    fun drain() {
        var steps = 0
        while (pending.isNotEmpty()) {
            check(++steps < 10_000) { "Reverse dispatcher did not quiesce" }
            pending.removeLast().run()
        }
    }
}

private abstract class StreamingTestSession : LamiResidentPcmSession {
    var cancelCount = 0
    var closeCount = 0
    override suspend fun synthesize(text: String): FloatArray = error("Tests must use actual chunk generation")
    override fun cancel() { cancelCount++ }
    override suspend fun close() { closeCount++ }
}
