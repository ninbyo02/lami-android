package io.github.ninbyo02.lami.tts

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Real AudioTrack tests: normal EOS is distinct from a deliberately starved source. */
class LamiPcmEosInstrumentedTest {
    @Test fun normalEosStopsAfterAllWritesAndDrainsWithoutUnderflow() = runBlocking {
        val pcm = tone(600)
        val results = mutableListOf<LamiPcmStreamPlayer.Stats>()
        for (stopAtEos in listOf(false, true)) {
            val chunks = Channel<FloatArray>(1)
            chunks.send(pcm)
            chunks.close()
            val start = SystemClock.elapsedRealtime()
            val stats = withTimeout(10_000) {
                LamiPcmStreamPlayer.play(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, chunks, {}, {}, stopOnEos = stopAtEos,
                    onSnapshot = { snapshot -> Log.i("LamiPcmEosTest", "early_stop=$stopAtEos $snapshot") })
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            assertEquals(pcm.size, stats.samples)
            assertTrue("Stop must not treat the reset head as completed playback", elapsed >= 550)
            results += stats
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("eos_${stopAtEos}_result", "samples=${stats.samples} underruns=${stats.underruns} elapsed_ms=$elapsed")
            })
        }
        assertEquals("Explicit normal EOS must not read an empty stream after the tail", 0, results.last().underruns)
        assertTrue(results.first().underruns >= results.last().underruns)
    }

    @Test fun genuineMidstreamStarvationIsStillReportedAfterNormalEos() = runBlocking {
        val chunks = Channel<FloatArray>(1)
        val snapshots = mutableListOf<LamiPcmPlaybackSnapshot>()
        chunks.send(tone(160))
        val producer = launch {
            delay(700)
            chunks.send(tone(240))
            chunks.close()
        }
        val stats = withTimeout(10_000) {
            LamiPcmStreamPlayer.play(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, chunks, {}, {}, stopOnEos = true,
                onSnapshot = { snapshot ->
                    snapshots += snapshot
                    Log.i("LamiPcmEosTest", "forced_gap=true $snapshot")
                })
        }
        producer.join()
        assertEquals(24 * 400, stats.samples)
        assertTrue("A real source gap must not be subtracted from native counts", stats.underruns > 0)
        assertTrue("Observe the real underflow before EOS", snapshots.any {
            it.phase == "waiting" && it.observedUnderruns > 0 && it.headFrames == it.submittedFrames
        })
    }

    private fun tone(milliseconds: Int) = FloatArray(24 * milliseconds) { sample ->
        (sin(2 * PI * 220 * sample / 24_000) * 0.02).toFloat()
    }
}
