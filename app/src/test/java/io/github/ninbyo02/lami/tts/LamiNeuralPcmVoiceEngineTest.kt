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

    @Test fun `unpunctuated bursts are bounded without dropping text or splitting surrogate pairs`() {
        for (text in listOf("あ".repeat(321), "a".repeat(321), "あ".repeat(79) + "😀".repeat(85) + "い")) {
            val pieces = LamiTtsTextChunks.split(text)
            assertTrue(pieces.size > 1)
            assertEquals(text, pieces.joinToString(""))
            assertTrue(pieces.all { it.codePointCount(0, it.length) <= LamiTtsTextChunks.MAX_PIECE_CODE_POINTS })
            assertTrue(pieces.none { Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first()) })
        }
        assertEquals(listOf(80, 10), LamiTtsTextChunks.split("𓀂".repeat(90)).map { it.codePointCount(0, it.length) })
    }

    @Test fun `hard bound prefers preceding whitespace rather than splitting an ordinary Latin word`() {
        val first = "a".repeat(20)
        val word = "longword".repeat(8)
        assertEquals(listOf(first, word), LamiTtsTextChunks.split("$first $word"))
    }

    @Test fun `audio started event waits for player callback after synthesis`() = runTest {
        val synthesis = CompletableDeferred<FloatArray>()
        val allowPlayback = CompletableDeferred<Unit>()
        var started = 0
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : LamiResidentPcmSession {
            override suspend fun synthesize(text: String) = synthesis.await()
            override suspend fun close() = Unit
        }, { _, onStarted -> allowPlayback.await(); onStarted() })
        engine.setOnAudioStarted { started++ }

        engine.speak("こんにちは。")
        runCurrent()
        assertEquals(0, started)
        synthesis.complete(pcm)
        runCurrent()
        assertEquals(0, started)
        allowPlayback.complete(Unit)
        runCurrent()

        assertEquals(1, started)
        assertFalse(engine.isSpeaking.value)
    }

    @Test fun `synthesis failure reports error without reporting audio started`() = runTest {
        val errors = mutableListOf<String>()
        var started = 0
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : LamiResidentPcmSession {
            override suspend fun synthesize(text: String): FloatArray = error("Synthesis unavailable")
            override suspend fun close() = Unit
        }, { _, _ -> fail("Failed synthesis must never play") })
        engine.setOnError(errors::add)
        engine.setOnAudioStarted { started++ }

        engine.speak("こんにちは。")
        runCurrent()

        assertEquals(1, errors.size)
        assertTrue(errors.single().isNotBlank())
        assertEquals(0, started)
        assertFalse(engine.isSpeaking.value)
    }

    @Test fun `playback failure reports error after started event and releases busy state`() = runTest {
        val errors = mutableListOf<String>()
        var started = 0
        val engine = LamiNeuralPcmVoiceEngine(backgroundScope, object : LamiResidentPcmSession {
            override suspend fun synthesize(text: String) = pcm
            override suspend fun close() = Unit
        }, { _, onStarted -> onStarted(); error("Audio device unavailable") })
        engine.setOnError(errors::add)
        engine.setOnAudioStarted { started++ }

        engine.speak("こんにちは。")
        runCurrent()

        assertEquals(1, started)
        assertEquals(1, errors.size)
        assertTrue(errors.single().isNotBlank())
        assertFalse(engine.isSpeaking.value)
    }
}
