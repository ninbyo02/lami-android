package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LamiSelectedVoiceEngineTest {
    @Test fun `prewarm creates only the selected voice and reuses it without claiming playback`() = runTest {
        val fake = SelectedTestVoice()
        val attempts = mutableListOf<TtsEngineChoice>()
        val engine = LamiSelectedVoiceEngine(MutableStateFlow(TtsEngineChoice.SUPERTONIC3), {
            attempts += it; fake
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        runCurrent()
        assertTrue(attempts.isEmpty())
        engine.prewarm()
        runCurrent()
        assertEquals(listOf(TtsEngineChoice.SUPERTONIC3), attempts)
        assertEquals(1, fake.prewarmCount)
        assertFalse(engine.isSpeaking.value)
        assertTrue(fake.spoken.isEmpty())
        engine.speakQueued("準備した声で話します。")
        runCurrent()
        assertEquals(1, attempts.size)
        assertEquals(1, fake.spoken.size)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `stop discards a prewarm requested before the first preference arrives`() = runTest {
        val choices = MutableSharedFlow<TtsEngineChoice>()
        val fake = SelectedTestVoice()
        var creates = 0
        val engine = LamiSelectedVoiceEngine(choices, { creates++; fake },
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        engine.prewarm()
        runCurrent()
        engine.stop()
        runCurrent()
        choices.emit(TtsEngineChoice.SUPERTONIC3)
        runCurrent()
        assertEquals(0, creates)
        engine.prewarm()
        runCurrent()
        assertEquals(1, creates)
        assertEquals(1, fake.prewarmCount)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `selection waits for prewarmed native cleanup before warming another voice`() = runTest {
        val choices = MutableStateFlow(TtsEngineChoice.SUPERTONIC3)
        val old = SelectedTestVoice().apply { cleanup = CompletableDeferred() }
        val next = SelectedTestVoice()
        val attempts = mutableListOf<TtsEngineChoice>()
        val engine = LamiSelectedVoiceEngine(choices, {
            attempts += it
            if (it == TtsEngineChoice.SUPERTONIC3) old else next
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        engine.prewarm()
        runCurrent()
        choices.value = TtsEngineChoice.LAMI_QWEN3
        runCurrent()
        engine.prewarm()
        runCurrent()
        assertEquals(listOf(TtsEngineChoice.SUPERTONIC3), attempts)
        assertEquals(0, next.prewarmCount)
        checkNotNull(old.cleanup).complete(Unit)
        runCurrent()
        assertEquals(listOf(TtsEngineChoice.SUPERTONIC3, TtsEngineChoice.LAMI_QWEN3), attempts)
        assertEquals(1, next.prewarmCount)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `selection waits for resident model release before creating the next voice`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val enteredClose = CompletableDeferred<Unit>()
        val finishClose = CompletableDeferred<Unit>()
        val previous = LamiNeuralPcmVoiceEngine(
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            session = object : LamiResidentPcmSession {
                override suspend fun synthesize(text: String) = floatArrayOf(0.1f)
                override suspend fun close() {
                    enteredClose.complete(Unit)
                    finishClose.await()
                }
            },
            playPcm = { _, _ -> },
        )
        val next = SelectedTestVoice()
        val choices = MutableStateFlow(TtsEngineChoice.KOKORO)
        val attempts = mutableListOf<TtsEngineChoice>()
        val engine = LamiSelectedVoiceEngine(choices, { choice ->
            attempts += choice
            if (choice == TtsEngineChoice.KOKORO) previous else next
        }, CoroutineScope(SupervisorJob() + dispatcher))
        engine.prewarm()
        runCurrent()
        choices.value = TtsEngineChoice.SUPERTONIC3
        runCurrent()
        assertTrue(enteredClose.isCompleted)
        engine.prewarm()
        runCurrent()
        assertEquals(listOf(TtsEngineChoice.KOKORO), attempts)
        assertEquals(0, next.prewarmCount)
        finishClose.complete(Unit)
        runCurrent()
        assertEquals(listOf(TtsEngineChoice.KOKORO, TtsEngineChoice.SUPERTONIC3), attempts)
        assertEquals(1, next.prewarmCount)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `Supertonic speech resumes after an idle generation gap without recreating its voice`() = runTest {
        val fake = SelectedTestVoice()
        val attempts = mutableListOf<TtsEngineChoice>()
        val engine = LamiSelectedVoiceEngine(MutableStateFlow(TtsEngineChoice.SUPERTONIC3), {
            attempts += it
            fake
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        engine.speakQueued("最初の説明。")
        runCurrent()
        assertTrue(engine.isSpeaking.value)

        fake.busyCallback(false)
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertFalse(engine.isSpeaking.value)

        engine.speakQueued("生成再開後の説明。")
        runCurrent()
        assertTrue(engine.isSpeaking.value)
        assertEquals(listOf(TtsEngineChoice.SUPERTONIC3), attempts)
        assertEquals(listOf("最初の説明。" to false, "生成再開後の説明。" to false), fake.spoken)
        assertEquals(0, fake.shutdownCount)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `speech waits for initial choice and preserves replacement and queued tail`() = runTest {
        val choices = MutableSharedFlow<TtsEngineChoice>()
        val attempts = mutableListOf<TtsEngineChoice>()
        val fake = SelectedTestVoice()
        val engine = LamiSelectedVoiceEngine(choices, { attempts += it; fake },
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        engine.speakQueued("古い文章。")
        engine.speak("新しい文章。")
        engine.speakQueued("続き。")
        runCurrent()
        assertTrue(attempts.isEmpty())

        choices.emit(TtsEngineChoice.LAMI_QWEN3)
        runCurrent()

        assertEquals(listOf(TtsEngineChoice.LAMI_QWEN3), attempts)
        assertEquals(listOf("新しい文章。" to true, "続き。" to false), fake.spoken)
        assertTrue(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `failed explicit selection reports error without creating another engine`() = runTest {
        val attempts = mutableListOf<TtsEngineChoice>()
        val errors = mutableListOf<String>()
        val engine = LamiSelectedVoiceEngine(MutableStateFlow(TtsEngineChoice.SUPERTONIC3), {
            attempts += it
            error("Supertonic model missing")
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        engine.setOnError(errors::add)
        engine.speakQueued("同じ声でお願いします。")
        runCurrent()

        assertEquals(listOf(TtsEngineChoice.SUPERTONIC3), attempts)
        assertEquals(listOf("Supertonic model missing"), errors)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `selection waits for old native cleanup before creating new engine`() = runTest {
        val choices = MutableStateFlow(TtsEngineChoice.ANDROID)
        val events = mutableListOf<String>()
        val old = SelectedTestVoice(events, "old")
        val next = SelectedTestVoice(events, "next")
        val attempts = mutableListOf<TtsEngineChoice>()
        val engine = LamiSelectedVoiceEngine(choices, {
            attempts += it
            events += "create:$it"
            if (it == TtsEngineChoice.ANDROID) old else next
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        engine.speakQueued("最初。")
        runCurrent()
        val cleanup = CompletableDeferred<Unit>()
        old.cleanup = cleanup

        choices.value = TtsEngineChoice.SUPERTONIC3
        runCurrent()
        engine.speakQueued("切替後。")
        runCurrent()
        assertEquals(listOf(TtsEngineChoice.ANDROID), attempts)
        assertEquals(0, old.shutdownCount)
        assertTrue(next.spoken.isEmpty())

        cleanup.complete(Unit)
        runCurrent()

        assertEquals(listOf(TtsEngineChoice.ANDROID, TtsEngineChoice.SUPERTONIC3), attempts)
        assertEquals(listOf("切替後。" to false), next.spoken)
        assertTrue(events.indexOf("old:cleaned") < events.indexOf("old:shutdown"))
        assertTrue(events.indexOf("old:shutdown") < events.indexOf("create:SUPERTONIC3"))
        engine.shutdown()
        runCurrent()
    }

    @Test fun `callbacks from previous engine cannot change current playback`() = runTest {
        val choices = MutableStateFlow(TtsEngineChoice.ANDROID)
        val old = SelectedTestVoice()
        val next = SelectedTestVoice()
        val engine = LamiSelectedVoiceEngine(choices, {
            if (it == TtsEngineChoice.ANDROID) old else next
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        val errors = mutableListOf<String>()
        val streaming = mutableListOf<TtsStreamingProgress>()
        var starts = 0
        engine.setOnError(errors::add)
        engine.setOnAudioStarted { starts++ }
        engine.setOnStreamingProgress(streaming::add)
        engine.speakQueued("最初。")
        runCurrent()
        val oldBusy = old.busyCallback
        val oldAudio = old.audioCallback
        val oldError = old.errorCallback
        val oldStreaming = old.streamingCallback

        choices.value = TtsEngineChoice.LAMI_QWEN3
        runCurrent()
        engine.speakQueued("新しい声。")
        runCurrent()
        oldBusy(false)
        oldAudio()
        oldError("old failure")
        oldStreaming(TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, sampleCount = 240, timestampNs = 1_000_000))
        runCurrent()

        assertTrue(engine.isSpeaking.value)
        assertEquals(0, starts)
        assertTrue(errors.isEmpty())
        assertTrue(streaming.isEmpty())
        next.audioCallback()
        next.errorCallback("new failure")
        val currentProgress = TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, sampleCount = 480, timestampNs = 2_000_000)
        next.streamingCallback(currentProgress)
        runCurrent()
        assertEquals(1, starts)
        assertEquals(listOf("new failure"), errors)
        assertEquals(listOf(currentProgress), streaming)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `voice input barrier waits cleanup and preserves later queued speech`() = runTest {
        val fake = SelectedTestVoice()
        val engine = LamiSelectedVoiceEngine(MutableStateFlow(TtsEngineChoice.ANDROID), { fake },
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        engine.speakQueued("最初。")
        runCurrent()
        val cleanup = CompletableDeferred<Unit>()
        fake.cleanup = cleanup
        val barrier = async { engine.stopForVoiceInput() }
        runCurrent()
        engine.speakQueued("停止後。")
        runCurrent()
        assertFalse(barrier.isCompleted)
        assertEquals(1, fake.spoken.size)

        cleanup.complete(Unit)
        barrier.await()
        runCurrent()

        assertEquals(listOf("最初。" to false, "停止後。" to false), fake.spoken)
        assertEquals(0, fake.shutdownCount)
        assertTrue(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `shutdown rejects later speech and waits native cleanup before disposal`() = runTest {
        val fake = SelectedTestVoice()
        val engine = LamiSelectedVoiceEngine(MutableStateFlow(TtsEngineChoice.ANDROID), { fake },
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        engine.speakQueued("最初。")
        runCurrent()
        val cleanup = CompletableDeferred<Unit>()
        fake.cleanup = cleanup
        engine.shutdown()
        engine.speakQueued("終了後は読みません。")
        val barrier = async { engine.stopForVoiceInput() }
        runCurrent()
        assertFalse(barrier.isCompleted)
        assertEquals(0, fake.shutdownCount)

        cleanup.complete(Unit)
        barrier.await()
        runCurrent()

        assertEquals(1, fake.shutdownCount)
        assertEquals(1, fake.spoken.size)
        assertFalse(engine.isSpeaking.value)
    }

    @Test fun `initial preference failure reports error and rejects later speech`() = runTest {
        var creates = 0
        val errors = mutableListOf<String>()
        val engine = LamiSelectedVoiceEngine(flow { error("storage unavailable") }, {
            creates++
            SelectedTestVoice()
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        engine.setOnError(errors::add)
        engine.speakQueued("設定待ちの文章。")
        runCurrent()
        assertEquals(1, errors.size)

        engine.speakQueued("設定に失敗した後。")
        runCurrent()

        assertEquals(2, errors.size)
        assertEquals(0, creates)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
        runCurrent()
    }

    @Test fun `failed old engine cleanup never creates overlapping selected engine`() = runTest {
        val choices = MutableStateFlow(TtsEngineChoice.ANDROID)
        val old = SelectedTestVoice()
        val next = SelectedTestVoice()
        val attempts = mutableListOf<TtsEngineChoice>()
        val errors = mutableListOf<String>()
        val engine = LamiSelectedVoiceEngine(choices, {
            attempts += it
            if (it == TtsEngineChoice.ANDROID) old else next
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        engine.setOnError(errors::add)
        engine.speakQueued("最初。")
        runCurrent()
        old.cleanupFailure = IllegalStateException("native cleanup failed")

        choices.value = TtsEngineChoice.SUPERTONIC3
        runCurrent()
        engine.speakQueued("重なってはいけません。")
        runCurrent()

        assertEquals(listOf(TtsEngineChoice.ANDROID), attempts)
        assertEquals(0, old.shutdownCount)
        assertTrue(next.spoken.isEmpty())
        assertEquals(2, errors.size)
        assertTrue(errors.all { it.contains("native cleanup failed") })

        old.cleanupFailure = null
        choices.value = TtsEngineChoice.LAMI_QWEN3
        runCurrent()
        engine.speakQueued("回復後。")
        runCurrent()
        assertEquals(listOf(TtsEngineChoice.ANDROID, TtsEngineChoice.LAMI_QWEN3), attempts)
        assertEquals(1, old.shutdownCount)
        assertEquals(listOf("回復後。" to false), next.spoken)
        engine.shutdown()
        runCurrent()
    }
}

private class SelectedTestVoice(
    private val events: MutableList<String> = mutableListOf(),
    private val name: String = "voice",
) : LamiVoiceEngine {
    private val speaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = speaking
    var busyCallback: (Boolean) -> Unit = {}
    var audioCallback: () -> Unit = {}
    var errorCallback: (String) -> Unit = {}
    var streamingCallback: (TtsStreamingProgress) -> Unit = {}
    val spoken = mutableListOf<Pair<String, Boolean>>()
    var cleanup: CompletableDeferred<Unit>? = null
    var cleanupFailure: Exception? = null
    var shutdownCount = 0
    var prewarmCount = 0
    override fun prewarm() { prewarmCount++ }
    override fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) { busyCallback = listener }
    override fun setOnAudioStarted(listener: () -> Unit) { audioCallback = listener }
    override fun setOnError(listener: (String) -> Unit) { errorCallback = listener }
    override fun setOnStreamingProgress(listener: (TtsStreamingProgress) -> Unit) { streamingCallback = listener }
    override fun speak(text: String) { spoken += text to true; setBusy(true) }
    override fun speakQueued(text: String) { spoken += text to false; setBusy(true) }
    override fun stop() { events += "$name:stop"; setBusy(false) }
    override suspend fun stopForVoiceInput() {
        stop()
        cleanup?.await()
        cleanupFailure?.let { throw it }
        events += "$name:cleaned"
    }
    override fun shutdown() { shutdownCount++; events += "$name:shutdown" }
    private fun setBusy(value: Boolean) { speaking.value = value; busyCallback(value) }
}
