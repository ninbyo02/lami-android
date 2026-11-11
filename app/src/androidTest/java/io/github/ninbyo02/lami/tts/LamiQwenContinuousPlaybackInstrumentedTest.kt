package io.github.ninbyo02.lami.tts

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real-model cold, warm and longer-sentence playback; no synthetic TTS substitute. */
class LamiQwenContinuousPlaybackInstrumentedTest {
    @Test fun repeatedAndLongerQwenSpeechHasNoNativeUnderflowsAndKeepsItsTail() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(LamiVoiceEngineFactory.availability(context, TtsEngineChoice.LAMI_QWEN3).available)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val controller = LamiTtsPreviewController(context, scope)
        try {
            val texts = listOf(
                "こんにちはラミィがお手伝いします",
                "こんにちはラミィがお手伝いします",
                "今日は天気がいいので一緒にお散歩に出かけましょうか",
            )
            for ((index, text) in texts.withIndex()) {
                val previous = controller.state.value.results.size
                withContext(Dispatchers.Main.immediate) { controller.play(TtsEngineChoice.LAMI_QWEN3, text) }
                val state = withTimeout(300_000) {
                    controller.state.first { !it.busy && it.results.size > previous }
                }
                val result = state.results.last()
                assertNull(result.error)
                assertEquals(TtsEngineChoice.LAMI_QWEN3, result.engine)
                assertEquals("Normal Qwen playback must not underflow", 0, result.underruns)
                assertTrue(result.audioChunks > 1)
                assertTrue("Start before the final decoder finishes: $result", result.streamedBeforeGenerationCompleted)
                val summary = "run=$index text_chars=${text.length} start_ms=${result.playbackStartMs} first_pcm_ms=${result.firstPcmMs} generation_complete_ms=${result.generationCompletedMs} total_ms=${result.totalMs} chunks=${result.audioChunks} underruns=${result.underruns} early_play=${result.streamedBeforeGenerationCompleted}"
                Log.i("LamiQwenContinuous", summary)
                instrumentation.sendStatus(0, Bundle().apply { putString("qwen_continuous_$index", summary) })
            }
        } finally {
            withContext(Dispatchers.Main.immediate) { controller.shutdown() }
            scope.cancel()
        }
    }
}
