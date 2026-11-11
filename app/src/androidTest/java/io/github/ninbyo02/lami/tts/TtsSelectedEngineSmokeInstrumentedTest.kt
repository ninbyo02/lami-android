package io.github.ninbyo02.lami.tts

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the existing comparison set or one explicitly requested engine. */
@RunWith(AndroidJUnit4::class)
class TtsSelectedEngineSmokeInstrumentedTest {
    @Test
    fun explicitChoicesUseTheirOwnRuntimeAndCompleteTheSameJapanesePreview() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actualTypes = mutableMapOf<TtsEngineChoice, String>()
        val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val controller = LamiTtsPreviewController(
            scope = previewScope,
            available = { LamiVoiceEngineFactory.availability(context, it) },
            create = { choice ->
                LamiVoiceEngineFactory.createSelected(context, choice).also {
                    actualTypes[choice] = it.javaClass.name
                }
            },
            nowMs = SystemClock::elapsedRealtime,
        )
        val expectedTypes = mapOf(
            TtsEngineChoice.ANDROID to "io.github.ninbyo02.lami.tts.AndroidTtsController",
            TtsEngineChoice.LAMI_QWEN3 to "io.github.ninbyo02.lami.tts.LamiNeuralChatVoiceEngine",
            TtsEngineChoice.SUPERTONIC3 to "io.github.ninbyo02.lami.tts.LamiNeuralPcmVoiceEngine",
            TtsEngineChoice.KOKORO to "io.github.ninbyo02.lami.tts.LamiNeuralPcmVoiceEngine",
        )
        try {
            val availability = withTimeout(30_000) {
                controller.state.first { it.availability.size == TtsEngineChoice.entries.size }.availability
            }
            val requestedEngine = InstrumentationRegistry.getArguments().getString("ttsSmokeEngine")
            val choices = if (requestedEngine == null) {
                listOf(TtsEngineChoice.ANDROID, TtsEngineChoice.LAMI_QWEN3, TtsEngineChoice.SUPERTONIC3)
            } else {
                TtsEngineChoice.entries.filter { it.storedValue == requestedEngine }
            }
            require(choices.isNotEmpty()) { "Unknown ttsSmokeEngine" }
            for (choice in choices) {
                val ready = availability.getValue(choice)
                assertTrue("${choice.label}: ${ready.message}; provision the real voice model before this smoke test", ready.available)
            }
            for (choice in choices) {
                val previousResults = controller.state.value.results.size
                withContext(Dispatchers.Main.immediate) { controller.play(choice, TEXT) }
                val state = withTimeout(if (choice == TtsEngineChoice.LAMI_QWEN3) 300_000 else 120_000) {
                    controller.state.first { !it.busy && it.results.size > previousResults }
                }
                val result = state.results.last()
                assertEquals("Explicit engine selection must not substitute another runtime", expectedTypes[choice], actualTypes[choice])
                assertEquals(choice, result.engine)
                assertEquals(TEXT, result.text)
                assertNull("${choice.label} failed: ${result.error}", result.error)
                assertNotNull("${choice.label} must report actual playback start", result.playbackStartMs)
                assertNotNull("${choice.label} must finish playback", result.totalMs)
                assertTrue(checkNotNull(result.playbackStartMs) >= 0L)
                assertTrue(checkNotNull(result.totalMs) >= checkNotNull(result.playbackStartMs))
                if (choice != TtsEngineChoice.KOKORO) {
                    assertNotNull("${choice.label} must report its first PCM chunk", result.firstPcmMs)
                    assertNotNull("${choice.label} must report waveform generation completion", result.generationCompletedMs)
                    assertTrue("${choice.label} must deliver multiple audio chunks in one sentence", result.audioChunks >= 2)
                }
                // Android engines may finish all synthesis callbacks while the
                // playback worker initializes AudioTrack. That scheduling is
                // outside our control; record the observation without requiring
                // an overlap the Android API does not promise. Local streaming
                // engines retain their explicit generation/playback contract.
                if (choice == TtsEngineChoice.LAMI_QWEN3 || choice == TtsEngineChoice.SUPERTONIC3) {
                    assertTrue("${choice.label} must start playback before waveform generation completes: $result", result.streamedBeforeGenerationCompleted)
                }
                Log.i("LamiTtsSmoke", "engine=${choice.storedValue} runtime=${actualTypes[choice]} playback_start_ms=${result.playbackStartMs} total_ms=${result.totalMs} first_pcm_ms=${result.firstPcmMs} generation_complete_ms=${result.generationCompletedMs} chunks=${result.audioChunks} early_play=${result.streamedBeforeGenerationCompleted} underruns=${result.underruns} status=ok")
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { controller.shutdown() }
            previewScope.cancel()
        }
    }

    companion object {
        // No sentence/space boundary: this must exercise audio chunking within one sentence.
        private const val TEXT = "こんにちはラミィがお手伝いします"
    }
}
