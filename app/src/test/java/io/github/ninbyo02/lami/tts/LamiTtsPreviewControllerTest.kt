package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LamiTtsPreviewControllerTest {
    @Test fun `preview uses the same code readings as chat while retaining the typed result text`() = runTest {
        val raw = "### 基本的なコードの骨組み（概念）\nprintで表示し、returnで値を返します。"
        val voice = PreviewTestVoice()
        val controller = LamiTtsPreviewController(backgroundScope, ::available, { voice },
            { 100L }, StandardTestDispatcher(testScheduler))
        controller.play(TtsEngineChoice.SUPERTONIC3, raw)
        voice.speakCalled.await()
        assertEquals(listOf("基本的なコードの骨組み、概念。\nプリントで表示し、リターンで値を返します。"), voice.texts)
        voice.complete()
        val result = controller.state.first { !it.busy }.results.single()
        assertEquals(raw, result.text)
        assertNull(result.error)
        controller.shutdown()
        runCurrent()
    }

    @Test fun `available engines speak the same text and measure actual callback boundaries`() = runTest {
        var clock = 1000L
        val attempts = mutableListOf<TtsEngineChoice>()
        val voices = TtsEngineChoice.entries.associateWith { PreviewTestVoice() }
        val controller = LamiTtsPreviewController(backgroundScope, ::available, {
            attempts += it
            voices.getValue(it)
        }, { clock }, StandardTestDispatcher(testScheduler))
        controller.state.first { it.availability.keys == TtsEngineChoice.entries.toSet() }

        for (choice in TtsEngineChoice.entries) {
            val voice = voices.getValue(choice)
            controller.play(choice, "  同じ文章です。  ")
            voice.speakCalled.await()
            assertEquals(listOf("同じ文章です。"), voice.texts)
            assertTrue(controller.state.value.busy)
            assertTrue(controller.state.value.results.none { it.engine == choice })
            clock += 40
            voice.audioCallback()
            clock += 10
            // Only the first start belongs to the request-to-playback measurement.
            voice.audioCallback()
            clock += 50
            voice.complete()
            val state = controller.state.first { !it.busy }
            val result = state.results.last()
            assertEquals(choice, result.engine)
            assertEquals("同じ文章です。", result.text)
            assertEquals(40L, result.playbackStartMs)
            assertEquals(100L, result.totalMs)
            assertNull(result.error)
            assertEquals(1, voice.shutdownCount)
            clock += 1000
        }
        assertEquals(TtsEngineChoice.entries.toList(), attempts)
        controller.shutdown()
        runCurrent()
    }

    @Test fun `unavailable explicit engine records error without creating another voice`() = runTest {
        val attempts = mutableListOf<TtsEngineChoice>()
        val unavailable = listOf(TtsEngineChoice.SUPERTONIC3, TtsEngineChoice.KOKORO)
        val controller = LamiTtsPreviewController(backgroundScope, {
            if (it in unavailable) TtsEngineAvailability(it, false, "モデルが未導入です") else available(it)
        }, { attempts += it; PreviewTestVoice() }, { 100L }, StandardTestDispatcher(testScheduler))

        for ((index, choice) in unavailable.withIndex()) {
            controller.play(choice, "比較します。")
            val state = controller.state.first { !it.busy && it.results.size == index + 1 }
            assertEquals(choice, state.results.last().engine)
            assertEquals("モデルが未導入です", state.error)
            assertNull(state.results.last().playbackStartMs)
        }
        assertTrue(attempts.isEmpty())
        controller.shutdown()
        runCurrent()
    }

    @Test fun `source timestamps preserve streamed timing despite delayed callbacks and cleanup`() = runTest {
        var clock = 1000L
        val voice = PreviewTestVoice()
        val cleanup = CompletableDeferred<Unit>()
        voice.cleanup = cleanup
        val controller = LamiTtsPreviewController(backgroundScope, ::available, { voice }, { clock }, StandardTestDispatcher(testScheduler))
        controller.play(TtsEngineChoice.ANDROID, "生成中から順に読みます。")
        voice.speakCalled.await()

        // Delivery is delayed; metrics must use the source event timestamps, not this clock.
        clock = 1200
        voice.streamingCallback(TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, sampleCount = 240, timestampNs = 1020_000_000))
        voice.streamingCallback(TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, sampleCount = 240, timestampNs = 1030_000_000))
        clock = 1250
        voice.audioCallback()
        voice.streamingCallback(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_STARTED, timestampNs = 1040_000_000))
        voice.streamingCallback(TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, sampleCount = 240, timestampNs = 1090_000_000))
        voice.streamingCallback(TtsStreamingProgress(TtsStreamEvent.GENERATION_COMPLETED, timestampNs = 1150_000_000))
        // A delayed older generation-completion event must not move the final source boundary back.
        voice.streamingCallback(TtsStreamingProgress(TtsStreamEvent.GENERATION_COMPLETED, timestampNs = 1140_000_000))
        voice.streamingCallback(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_COMPLETED, timestampNs = 1300_000_000, underruns = 2))
        clock = 1300
        voice.complete()
        runCurrent()
        assertTrue(controller.state.value.busy)
        clock = 2000
        cleanup.complete(Unit)
        val result = controller.state.first { !it.busy }.results.single()

        assertEquals(20L, result.firstPcmMs)
        assertEquals(40L, result.playbackStartMs)
        assertEquals(150L, result.generationCompletedMs)
        assertEquals(300L, result.totalMs)
        assertEquals(3, result.audioChunks)
        assertTrue(result.streamedBeforeGenerationCompleted)
        assertEquals(2, result.underruns)
        assertNull(result.error)
        controller.shutdown()
        runCurrent()
    }

    @Test fun `many PCM chunks generated before playback do not count as streaming overlap`() = runTest {
        var clock = 1000L
        val voice = PreviewTestVoice()
        val controller = LamiTtsPreviewController(backgroundScope, ::available, { voice }, { clock }, StandardTestDispatcher(testScheduler))
        controller.play(TtsEngineChoice.SUPERTONIC3, "全体を生成してから再生する例です。")
        voice.speakCalled.await()
        for (offset in listOf(10L, 20L, 30L, 40L)) {
            voice.streamingCallback(TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, sampleCount = 480,
                timestampNs = (1000 + offset) * 1_000_000))
        }
        voice.streamingCallback(TtsStreamingProgress(TtsStreamEvent.GENERATION_COMPLETED, timestampNs = 1050_000_000))
        voice.streamingCallback(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_STARTED, timestampNs = 1100_000_000))
        clock = 1200
        voice.audioCallback()
        voice.streamingCallback(TtsStreamingProgress(TtsStreamEvent.PLAYBACK_COMPLETED, timestampNs = 1300_000_000))
        clock = 1300
        voice.complete()
        val result = controller.state.first { !it.busy }.results.single()

        assertEquals(10L, result.firstPcmMs)
        assertEquals(50L, result.generationCompletedMs)
        assertEquals(100L, result.playbackStartMs)
        assertEquals(4, result.audioChunks)
        assertFalse(result.streamedBeforeGenerationCompleted)
        assertEquals(0, result.underruns)
        controller.shutdown()
        runCurrent()
    }

    @Test fun `engine error produces failed result without fabricated playback start`() = runTest {
        val voice = PreviewTestVoice()
        val controller = LamiTtsPreviewController(backgroundScope, ::available, { voice }, { 100L }, StandardTestDispatcher(testScheduler))
        controller.play(TtsEngineChoice.LAMI_QWEN3, "比較します。")
        voice.speakCalled.await()
        voice.errorCallback("音声生成に失敗しました")
        val state = controller.state.first { !it.busy }

        assertEquals("音声生成に失敗しました", state.error)
        assertNull(state.results.single().playbackStartMs)
        assertEquals(1, voice.shutdownCount)
        controller.shutdown()
        runCurrent()
    }

    @Test fun `stop waits native cleanup and blocks next comparison until disposal`() = runTest {
        val voice = PreviewTestVoice()
        val cleanup = CompletableDeferred<Unit>()
        voice.cleanup = cleanup
        var creates = 0
        val controller = LamiTtsPreviewController(backgroundScope, ::available, { creates++; voice }, { 100L }, StandardTestDispatcher(testScheduler))
        controller.play(TtsEngineChoice.SUPERTONIC3, "比較します。")
        voice.speakCalled.await()

        controller.stop()
        runCurrent()
        assertTrue(controller.state.value.busy)
        assertTrue(controller.state.value.stopping)
        assertEquals(0, voice.shutdownCount)
        controller.play(TtsEngineChoice.ANDROID, "重なってはいけません。")
        runCurrent()
        assertEquals(1, creates)

        cleanup.complete(Unit)
        val state = controller.state.first { !it.busy }
        assertFalse(state.stopping)
        assertEquals("停止しました", state.results.single().error)
        assertEquals(1, voice.shutdownCount)
        controller.shutdown()
        runCurrent()
    }

    @Test fun `navigation cancellation and shutdown retain cleanup ownership until native completion`() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val voice = PreviewTestVoice()
        val cleanup = CompletableDeferred<Unit>()
        voice.cleanup = cleanup
        val controller = LamiTtsPreviewController(owner, ::available, { voice }, { 100L }, StandardTestDispatcher(testScheduler))
        controller.play(TtsEngineChoice.SUPERTONIC3, "比較します。")
        voice.speakCalled.await()

        owner.cancel()
        controller.shutdown()
        runCurrent()
        assertEquals(0, voice.shutdownCount)
        assertTrue(controller.state.value.busy)

        cleanup.complete(Unit)
        controller.state.first { !it.busy }
        runCurrent()
        assertEquals(1, voice.shutdownCount)
        controller.play(TtsEngineChoice.ANDROID, "終了後は読みません。")
        runCurrent()
        assertEquals(listOf("比較します。"), voice.texts)
    }

    private fun available(engine: TtsEngineChoice) = TtsEngineAvailability(engine, true, "利用できます")
}

private class PreviewTestVoice : LamiVoiceEngine {
    private val speaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = speaking
    var busyCallback: (Boolean) -> Unit = {}
    var audioCallback: () -> Unit = {}
    var errorCallback: (String) -> Unit = {}
    var streamingCallback: (TtsStreamingProgress) -> Unit = {}
    val speakCalled = CompletableDeferred<Unit>()
    val texts = mutableListOf<String>()
    var cleanup: CompletableDeferred<Unit>? = null
    var shutdownCount = 0
    override fun setOnPlaybackStateChanged(listener: (Boolean) -> Unit) { busyCallback = listener }
    override fun setOnAudioStarted(listener: () -> Unit) { audioCallback = listener }
    override fun setOnError(listener: (String) -> Unit) { errorCallback = listener }
    override fun setOnStreamingProgress(listener: (TtsStreamingProgress) -> Unit) { streamingCallback = listener }
    override fun speak(text: String) {
        texts += text
        speaking.value = true
        busyCallback(true)
        speakCalled.complete(Unit)
    }
    override fun speakQueued(text: String) = speak(text)
    override fun stop() { complete() }
    override suspend fun stopForVoiceInput() { stop(); cleanup?.await() }
    override fun shutdown() { shutdownCount++ }
    fun complete() { speaking.value = false; busyCallback(false) }
}
