package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LamiNeuralPcmVoiceEngineTest {
    private val pcm = floatArrayOf(0.1f, -0.2f)
    @Test fun `prepares next clause while the first is playing`() = runTest {
        val prepared = mutableListOf<String>()
        val playback = CompletableDeferred<Unit>()
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : LamiResidentPcmSession {
            override suspend fun synthesize(text: String): FloatArray { prepared += text; return pcm }
            override suspend fun close() = Unit
        }, { _, started -> started(); playback.await() })
        engine.speak("こんにちは。次の予定です。")
        runCurrent()
        assertEquals(listOf("こんにちは。", "次の予定です。"), prepared)
        assertTrue(engine.isSpeaking.value)
        playback.complete(Unit)
        runCurrent()
        assertFalse(engine.isSpeaking.value)
    }

    @Test fun `microphone barrier waits for cancelled synthesis cleanup`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        var cleanupDone = false
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : LamiResidentPcmSession {
            override suspend fun synthesize(text: String): FloatArray = try {
                delay(10000); pcm
            } finally {
                withContext(NonCancellable) { cleanup.await(); cleanupDone = true }
            }
            override suspend fun close() = Unit
        }, { _, _ -> fail("Cancelled PCM must never play") })
        engine.speak("取り消してください。")
        runCurrent()
        val barrier = async { engine.stopForVoiceInput() }
        runCurrent()
        assertFalse(barrier.isCompleted)
        cleanup.complete(Unit)
        barrier.await()
        assertTrue(cleanupDone)
        assertFalse(engine.isSpeaking.value)
    }

    @Test fun `invalid synthesis does not prevent following queued text`() = runTest {
        var attempt = 0
        var played = 0
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : LamiResidentPcmSession {
            override suspend fun synthesize(text: String) = if (++attempt == 1) floatArrayOf(Float.NaN) else pcm
            override suspend fun close() = Unit
        }, { _, _ -> played++ })
        engine.speak("最初。次。")
        runCurrent()
        assertEquals(2, attempt)
        assertEquals(1, played)
        assertFalse(engine.isSpeaking.value)
    }

    @Test fun `shutdown waits for playback cleanup before closing resident session`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : LamiResidentPcmSession {
            override suspend fun synthesize(text: String) = pcm
            override suspend fun close() { events += "closed" }
        }, { _, _ -> try { delay(10000) } finally {
            withContext(NonCancellable) { cleanup.await(); events += "released" }
        } })
        engine.speak("こんにちは。")
        runCurrent(); engine.shutdown(); runCurrent()
        assertTrue(events.isEmpty())
        cleanup.complete(Unit); runCurrent()
        assertEquals(listOf("released", "closed"), events)
    }

    @Test fun `does not split decimals or latin words and preserves text content`() {
        val text = "温度は23.5度です。 CPUの設定を確認します。"
        assertEquals(listOf("温度は23.5度です。", "CPUの設定を確認します。"), LamiTtsTextChunks.split(text))
    }
}
