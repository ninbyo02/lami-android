package io.github.ninbyo02.lami.tts

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LamiNeuralChatLongTextTest {
    private val longText = "長い文章が続きます、内容を省略せず順番に読み上げます。".repeat(5)

    private suspend fun plan(text: String): List<String> = LamiQwenTextChunks.split(text, 256) {
        11 + it.codePointCount(0, it.length)
    }

    @Test fun streamingSegmentsPreserveAllTextAndKeepQueuedRequestsInOrder() = runTest {
        val spoken = mutableListOf<String>()
        val busy = mutableListOf<Boolean>()
        val errors = mutableListOf<String>()
        val clips = mutableMapOf<FloatArray, String>()
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, preferEarlyStart = false, scope = backgroundScope,
            planText = { text, _ -> plan(text) },
            synthesizeSpeech = { text, _ -> floatArrayOf(0.1f).also { clips[it] = text } },
            playPreparedSpeech = { pcm, started, _ -> spoken += checkNotNull(clips.remove(pcm)); started() },
            streamSpeech = { text, _, started -> spoken += text; started(); floatArrayOf(0.1f) })
        engine.setOnPlaybackStateChanged(busy::add)
        engine.setOnError(errors::add)
        engine.speak(longText)
        engine.speakQueued("最後の追加です。")
        runCurrent()
        assertEquals(longText + "最後の追加です。", spoken.joinToString(""))
        assertTrue(spoken.size > 3)
        assertTrue(spoken.all { it.codePointCount(0, it.length) <= LamiQwenTextChunks.MAX_CODE_POINTS })
        assertEquals(listOf(true, false), busy)
        assertTrue(errors.isEmpty())
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun bufferedCompatibilityPathAlsoGeneratesAndPlaysOneBoundedSegmentAtATime() = runTest {
        val generated = mutableListOf<String>()
        val played = mutableListOf<String>()
        val clips = mutableMapOf<FloatArray, String>()
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = false, scope = backgroundScope,
            planText = { text, _ -> plan(text) },
            synthesizeSpeech = { text, _ ->
                generated += text
                floatArrayOf(0.1f).also { clips[it] = text }
            }, playBufferedSpeech = { pcm, started -> played += checkNotNull(clips.remove(pcm)); started() },
            playPreparedSpeech = { pcm, started, _ -> played += checkNotNull(clips.remove(pcm)); started() })
        engine.speak(longText)
        engine.speakQueued("末尾です。")
        runCurrent()
        assertEquals(longText + "末尾です。", played.joinToString(""))
        assertEquals(generated.size, played.size)
        assertTrue(generated.all { it.codePointCount(0, it.length) <= LamiQwenTextChunks.MAX_CODE_POINTS })
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun stopDropsEveryRemainingSegmentAndQueuedRequest() = runTest {
        val spoken = mutableListOf<String>()
        var cleaned = false
        val clips = mutableMapOf<FloatArray, String>()
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, preferEarlyStart = false, scope = backgroundScope,
            planText = { text, _ -> plan(text) },
            synthesizeSpeech = { text, _ -> floatArrayOf(0.1f).also { clips[it] = text } },
            playPreparedSpeech = { pcm, started, _ ->
                spoken += checkNotNull(clips.remove(pcm))
                started()
                try { awaitCancellation() } finally { cleaned = true }
            })
        engine.speak(longText)
        engine.speakQueued("停止後に読み上げてはいけません。")
        runCurrent()
        engine.stop()
        runCurrent()
        assertEquals(1, spoken.size)
        assertTrue(cleaned)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun flushWaitsForCleanupAndRejectsOldAudioAndProgressCallbacks() = runTest {
        val cleaned = CompletableDeferred<Unit>()
        val spoken = mutableListOf<String>()
        val events = mutableListOf<TtsStreamingProgress>()
        var lateStarted: () -> Unit = {}
        var lateProgress: (TtsStreamingProgress) -> Unit = {}
        var starts = 0
        val clips = mutableMapOf<FloatArray, String>()
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, preferEarlyStart = false, scope = backgroundScope,
            planText = { text, _ -> if (text == "old request") listOf("old-first", "old-tail") else listOf(text) },
            synthesizeSpeech = { text, _ -> floatArrayOf(0.1f).also { clips[it] = text } },
            playPreparedSpeech = { pcm, started, progress ->
                spoken += checkNotNull(clips.remove(pcm))
                lateStarted = started
                lateProgress = progress
                started()
                try { awaitCancellation() } finally { withContext(NonCancellable) { cleaned.await() } }
            },
            streamSpeechWithProgress = { text, _, started, progress ->
                spoken += text
                started()
                progress(TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, 1, 10))
                floatArrayOf(0.1f)
            })
        engine.setOnAudioStarted { starts++ }
        engine.setOnStreamingProgress(events::add)
        engine.speak("old request")
        runCurrent()
        assertEquals(1, starts)
        engine.speak("new request")
        runCurrent()
        events.clear()
        lateStarted()
        lateProgress(TtsStreamingProgress(TtsStreamEvent.GENERATION_COMPLETED, 1, 999))
        assertEquals(1, starts)
        assertTrue(events.isEmpty())
        assertEquals(listOf("old-first"), spoken)
        cleaned.complete(Unit)
        runCurrent()
        assertEquals(listOf("old-first", "new request"), spoken)
        assertEquals(2, starts)
        assertEquals(listOf(TtsStreamEvent.PCM_CHUNK), events.map { it.event })
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun unfinishedNaturalEosReportsFailureAndAbortsTheRestOfThatRequest() = runTest {
        val spoken = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, preferEarlyStart = false, scope = backgroundScope,
            planText = { text, _ -> plan(text) },
            synthesizeSpeech = { text, _ ->
                spoken += text
                error("Speech stream did not reach EOS; provisional audio stopped")
            }, playPreparedSpeech = { _, _, _ -> fail("An unfinished EOS must never reach playback") })
        engine.setOnError(errors::add)
        engine.speak(longText)
        runCurrent()
        assertEquals(1, spoken.size)
        assertEquals(1, errors.size)
        assertTrue(errors.single().contains("did not reach EOS"))
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun completedSegmentsCannotEmitLateEventsDuringTheNextSegment() = runTest {
        val finishSecond = CompletableDeferred<Unit>()
        val events = mutableListOf<TtsStreamingProgress>()
        var lateStarted: () -> Unit = {}
        var lateProgress: (TtsStreamingProgress) -> Unit = {}
        var starts = 0
        val clips = mutableMapOf<FloatArray, String>()
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, preferEarlyStart = false, scope = backgroundScope,
            planText = { text, _ -> if (text == "all") listOf("first", "second") else listOf(text) },
            synthesizeSpeech = { text, _ -> floatArrayOf(0.1f).also { clips[it] = text } },
            playPreparedSpeech = { pcm, started, progress ->
                val text = checkNotNull(clips.remove(pcm))
                started()
                if (text == "first") {
                    lateStarted = started
                    lateProgress = progress
                } else finishSecond.await()
            })
        engine.setOnAudioStarted { starts++ }
        engine.setOnStreamingProgress(events::add)
        engine.speak("all")
        runCurrent()
        assertEquals(2, starts)
        events.clear()
        lateStarted()
        lateProgress(TtsStreamingProgress(TtsStreamEvent.GENERATION_COMPLETED, 1, 999))
        assertEquals(2, starts)
        assertTrue(events.isEmpty())
        finishSecond.complete(Unit)
        runCurrent()
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun pendingSegmentIsReplannedBeforeSynthesisWhenItsBudgetShrinks() = runTest {
        val spoken = mutableListOf<String>()
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, preferEarlyStart = false, scope = backgroundScope,
            planText = { text, _ -> when (text) {
                "abcdefghABCDEFGH" -> listOf("abcdefgh", "ABCDEFGH")
                "ABCDEFGH" -> listOf("ABCD", "EFGH")
                else -> listOf(text)
            } }, synthesizeSpeech = { text, _ -> spoken += text; floatArrayOf(0.1f) },
            playPreparedSpeech = { _, started, _ -> started() })
        engine.speak("abcdefghABCDEFGH")
        runCurrent()
        assertEquals(listOf("abcdefgh", "ABCD", "EFGH"), spoken)
        engine.shutdown()
    }

    @Test fun microphoneBarrierAlsoJoinsCancelledTextPlanning() = runTest {
        val ready = CompletableDeferred<Unit>()
        var planned = false
        var generated = false
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, preferEarlyStart = false, scope = backgroundScope,
            planText = { text, _ ->
                withContext(NonCancellable) { ready.await(); planned = true }
                listOf(text)
            }, streamSpeech = { _, _, _ -> generated = true; floatArrayOf(0.1f) })
        engine.speak(longText)
        runCurrent()
        val stopped = async { engine.stopForVoiceInput() }
        runCurrent()
        assertFalse(stopped.isCompleted)
        ready.complete(Unit)
        stopped.await()
        assertTrue(planned)
        assertFalse(generated)
        engine.shutdown()
    }

    @Test fun plannerUsesTheSelectedVerifiedMainCacheCapacity() {
        val ctx = JSONObject().put("sha256", JSONObject().put("stateless-28-int4-cache256-et14.pte", "0".repeat(64)))
        assertEquals(256, LamiPreparedVoiceSynthesizer.mainCacheCapacity(ctx))
        ctx.put("main_program", "main-int8-grouped-cache128.pte")
        ctx.getJSONObject("sha256").put("main-int8-grouped-cache128.pte", "83ef62e9ac80c6db43d532b43c7e85d0586e5860e6718c552054f321d8da5dca")
        assertEquals(128, LamiPreparedVoiceSynthesizer.mainCacheCapacity(ctx))
        ctx.getJSONObject("sha256").put("main-int8-grouped-cache128.pte", "0".repeat(64))
        assertTrue(runCatching { LamiPreparedVoiceSynthesizer.mainCacheCapacity(ctx) }.isFailure)
    }
}
