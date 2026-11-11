package io.github.ninbyo02.lami.tts

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
class LamiNeuralChatEarlyBurstTest {
    @Test fun firstOfFiveQueuedSentencesStartsBeforeEosAndOnlyLaterRequestsUsePreparedPcm() = runTest {
        val eos = CompletableDeferred<Unit>()
        val drain = CompletableDeferred<Unit>()
        val module = Mutex()
        val clips = mutableMapOf<FloatArray, String>()
        val prefixes = mutableListOf<String>()
        val prepared = mutableListOf<String>()
        val played = mutableListOf<String>()
        var firstCompleted = false
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, scope = backgroundScope, planText = { text, _ -> listOf(text) },
            streamSpeechWithProgress = { text, _, started, progress ->
                prefixes += text
                module.withLock {
                    played += text
                    started()
                    progress(TtsStreamingProgress(TtsStreamEvent.PCM_CHUNK, 15_360, 1))
                    eos.await()
                    firstCompleted = true
                }
                drain.await()
                floatArrayOf(0.1f)
            }, synthesizeSpeech = { text, _ -> module.withLock {
                prepared += text
                floatArrayOf(0.1f).also { clips[it] = text }
            } }, playPreparedSpeech = { pcm, started, _ -> played += checkNotNull(clips.remove(pcm)); started() })
        listOf("one", "two", "three", "four", "five").forEach(engine::speakQueued)
        runCurrent()
        assertEquals(listOf("one"), played)
        assertFalse(firstCompleted)
        assertTrue(prepared.isEmpty()) // The real shared model mutex cannot be used before EOS.
        eos.complete(Unit)
        runCurrent()
        assertEquals(listOf("two"), prepared)
        assertEquals(listOf("one"), played)
        drain.complete(Unit)
        runCurrent()
        assertEquals(listOf("one"), prefixes)
        assertEquals(listOf("two", "three", "four", "five"), prepared)
        assertEquals(listOf("one", "two", "three", "four", "five"), played)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun directLongRequestUsesPrefixForItsFirstSegmentAndPreparedPcmForEveryTail() = runTest {
        val clips = mutableMapOf<FloatArray, String>()
        val prefixes = mutableListOf<String>()
        val prepared = mutableListOf<String>()
        val played = mutableListOf<String>()
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, scope = backgroundScope,
            planText = { text, _ -> if (text == "all") listOf("first", "middle", "last") else listOf(text) },
            streamSpeech = { text, _, started -> prefixes += text; played += text; started(); floatArrayOf(0.1f) },
            synthesizeSpeech = { text, _ -> prepared += text; floatArrayOf(0.1f).also { clips[it] = text } },
            playPreparedSpeech = { pcm, started, _ -> played += checkNotNull(clips.remove(pcm)); started() })
        engine.speak("all")
        runCurrent()
        assertEquals(listOf("first"), prefixes)
        assertEquals(listOf("middle", "last"), prepared)
        assertEquals(listOf("first", "middle", "last"), played)
        engine.shutdown()
    }

    @Test fun sourceIdleStartsANewPrefixBurstWithoutRetiringTheQueue() = runTest {
        val prefixes = mutableListOf<String>()
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, scope = backgroundScope, planText = { text, _ -> listOf(text) },
            streamSpeech = { text, _, started -> prefixes += text; started(); floatArrayOf(0.1f) },
            synthesizeSpeech = { _, _ -> error("Every request after source idle starts a new prefix burst") })
        engine.speakQueued("before idle")
        runCurrent()
        assertFalse(engine.isSpeaking.value)
        engine.speakQueued("after idle")
        runCurrent()
        assertEquals(listOf("before idle", "after idle"), prefixes)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun flushWaitsForOldPrefixCleanupThenStartsANewEarlyBurst() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val prefixes = mutableListOf<String>()
        var lateStarted: () -> Unit = {}
        var starts = 0
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, scope = backgroundScope, planText = { text, _ -> listOf(text) },
            streamSpeech = { text, _, started ->
                prefixes += text
                started()
                if (text == "old") {
                    lateStarted = started
                    try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
                }
                floatArrayOf(0.1f)
            }, synthesizeSpeech = { _, _ -> floatArrayOf(0.1f) },
            playPreparedSpeech = { _, _, _ -> fail("The old queued tail must not play after FLUSH") })
        engine.setOnAudioStarted { starts++ }
        engine.speakQueued("old")
        engine.speakQueued("discard")
        runCurrent()
        engine.speak("replacement")
        runCurrent()
        lateStarted()
        assertEquals(1, starts)
        assertEquals(listOf("old"), prefixes)
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("old", "replacement"), prefixes)
        assertEquals(2, starts)
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }

    @Test fun earlyAudioDoesNotTurnMissingNaturalEosIntoSuccess() = runTest {
        val errors = mutableListOf<String>()
        var starts = 0
        var preparedPlayed = false
        val engine = LamiNeuralChatVoiceEngine(RuntimeEnvironment.getApplication(), File("unused"),
            streamingEnabled = true, scope = backgroundScope,
            planText = { text, _ -> if (text == "all") listOf("first", "tail") else listOf(text) },
            streamSpeech = { _, _, started -> started(); error("Speech stream did not reach EOS") },
            synthesizeSpeech = { _, _ -> floatArrayOf(0.1f) },
            playPreparedSpeech = { _, _, _ -> preparedPlayed = true })
        engine.setOnAudioStarted { starts++ }
        engine.setOnError(errors::add)
        engine.speak("all")
        runCurrent()
        assertEquals(1, starts)
        assertFalse(preparedPlayed)
        assertEquals(1, errors.size)
        assertTrue(errors.single().contains("did not reach EOS"))
        assertFalse(engine.isSpeaking.value)
        engine.shutdown()
    }
}
