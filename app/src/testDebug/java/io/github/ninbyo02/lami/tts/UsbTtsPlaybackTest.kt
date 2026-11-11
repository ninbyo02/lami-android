package io.github.ninbyo02.lami.tts

import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class UsbTtsPlaybackTest {
    @Test fun `starts from callbacks before synthesis completion and closes only after drain`() = runTest {
        val events = mutableListOf<String>()
        val progress = mutableListOf<TtsStreamEvent>()
        val drain = CompletableDeferred<Unit>()
        val playback = UsbTtsPlayback(RuntimeEnvironment.getApplication(), {}, backgroundScope) { format, _, _, started ->
            assertEquals(AndroidTtsPcmFormat(24_000, 2, 1), format)
            object : AndroidTtsPcmSink {
                override suspend fun write(samples: ShortArray) { events += "write"; started() }
                override suspend fun drain() { events += "drain"; drain.await() }
                override fun close() { events += "closed" }
            }
        }
        playback.setListener({ id, success, error -> events += "complete:$id:$success:$error" },
            { events += "started" }, { _, event -> progress += event.event })
        val file = playback.begin("first", null)
        playback.beginSynthesis("first", 24_000, 2, 1)
        playback.audioAvailable("first", byteArrayOf(1, 0, 2, 0))
        runCurrent()
        assertEquals(listOf("write", "started"), events)
        assertTrue(playback.isActive())
        assertFalse(progress.contains(TtsStreamEvent.GENERATION_COMPLETED))
        playback.synthesized("first")
        runCurrent()
        assertTrue(playback.isActive())
        assertTrue(file.exists())
        assertEquals("drain", events.last())
        drain.complete(Unit)
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(file.exists())
        assertFalse(playback.isActive())
        assertEquals(listOf("write", "started", "drain", "closed", "complete:first:true:null"), events)
        assertEquals(listOf(TtsStreamEvent.GENERATION_STARTED, TtsStreamEvent.PCM_CHUNK,
            TtsStreamEvent.PLAYBACK_STARTED, TtsStreamEvent.GENERATION_COMPLETED, TtsStreamEvent.PLAYBACK_COMPLETED), progress)
    }

    @Test fun `stop deletes capture file and ignores late PCM synthesis and callbacks`() = runTest {
        var completions = 0
        val playback = UsbTtsPlayback(RuntimeEnvironment.getApplication(), {}, backgroundScope)
        playback.setListener({ _, _, _ -> completions++ }, {}, { _, _ -> })
        val file = playback.begin("first", null)
        playback.stop()
        assertFalse(file.exists())
        assertFalse(playback.beginSynthesis("first", 24_000, 2, 1))
        assertFalse(playback.audioAvailable("first", byteArrayOf(1, 0)))
        assertFalse(playback.synthesized("first"))
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, completions)
    }

    @Test fun `replacement waits for canceled writer cleanup before opening next sink`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val playback = UsbTtsPlayback(RuntimeEnvironment.getApplication(), {}, backgroundScope) { _, _, _, _ ->
            val ordinal = events.count { it.startsWith("open") } + 1
            events += "open$ordinal"
            object : AndroidTtsPcmSink {
                override suspend fun write(samples: ShortArray) {
                    try { awaitCancellation() } finally { withContext(NonCancellable) { cleanup.await() } }
                }
                override suspend fun drain() = Unit
                override fun close() { events += "close$ordinal" }
            }
        }
        playback.begin("first", null)
        playback.beginSynthesis("first", 24_000, 2, 1)
        playback.audioAvailable("first", byteArrayOf(1, 0))
        runCurrent()
        val second = playback.begin("second", null)
        playback.beginSynthesis("second", 24_000, 2, 1)
        playback.audioAvailable("second", byteArrayOf(2, 0))
        runCurrent()
        assertEquals(listOf("open1"), events)
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("open1", "close1", "open2"), events)
        assertTrue(second.exists())
        playback.stop()
        runCurrent()
        playback.awaitCleanup()
        assertEquals("close2", events.last())
    }

    @Test fun `source timeout cleans capture and reports failure once`() = runTest {
        var completions = 0
        var success: Boolean? = null
        val playback = UsbTtsPlayback(RuntimeEnvironment.getApplication(), {}, backgroundScope)
        playback.setListener({ _, succeeded, _ -> completions++; success = succeeded }, {}, { _, _ -> })
        val file = playback.begin("first", null)
        advanceTimeBy(60_001)
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(file.exists())
        assertEquals(1, completions)
        assertEquals(false, success)
        assertFalse(playback.failed("first", "late"))
    }
}
