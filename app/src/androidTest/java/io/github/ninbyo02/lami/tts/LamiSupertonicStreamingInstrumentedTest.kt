package io.github.ninbyo02.lami.tts

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs

/** Requires the external Light5 bundle and the patched sherpa JNI, without playing audio. */
class LamiSupertonicStreamingInstrumentedTest {
    @Test
    fun sameSentenceStreamsBeforeNativeCompletionAndMatchesBufferedPcm() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = LamiSupertonicVoiceFactory.modelDirectory(context)
        assumeTrue("Supertonic model must be provisioned", LamiSupertonicSession.FILES.all { directory.resolve(it).isFile })
        assertTrue("Patched streaming JNI must be packaged", LamiSupertonicStreamingRuntime.available)
        val measured = mutableListOf<JSONObject>()
        val session = LamiSupertonicSession(directory, speakerId = 0, steps = 5, threads = 4, measured = { measured += it })
        try {
            withTimeout(120_000) {
                session.prewarm()
                // Upstream ChunkText merges this <120-character input into ONE
                // native text chunk. Multiple PCM callbacks therefore require
                // the intra-sentence vocoder path, not sentence splitting.
                val text = "こんにちは。ラミィがお手伝いします。"
                val reference = session.synthesize(text)
                measured.clear()
                val chunks = mutableListOf<FloatArray>()
                var firstConsumedAt = 0L
                session.stream(text) { pcm ->
                    if (firstConsumedAt == 0L) firstConsumedAt = SystemClock.elapsedRealtimeNanos()
                    chunks += pcm.copyOf()
                }
                val metrics = measured.single()
                assertTrue("Must decode several native PCM windows", metrics.getInt("pcm_callback_count") > 1)
                assertTrue("First PCM must be consumed while the same native call still generates",
                    firstConsumedAt > 0 && firstConsumedAt < metrics.getLong("native_synthesis_completed_at_ns"))
                assertTrue(metrics.getBoolean("intra_sentence_pcm_streaming"))
                assertEquals(reference.size, chunks.sumOf { it.size })
                var offset = 0
                var maxError = 0f
                for (chunk in chunks) {
                    for (sample in chunk) {
                        maxError = maxOf(maxError, abs(sample - reference[offset++]))
                    }
                }
                assertTrue("Streamed PCM differs from full vocoder inference: max_abs=$maxError", maxError <= 2e-5f)
            }
        } finally {
            session.close()
        }
    }
}
