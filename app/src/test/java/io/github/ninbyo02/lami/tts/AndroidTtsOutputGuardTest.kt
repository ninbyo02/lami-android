package io.github.ninbyo02.lami.tts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidTtsOutputGuardTest {
    @Test fun `lost USB route immediately mutes queued audio and wakes consumer waiting for PCM`() = runTest {
        val stream = AndroidTtsPcmStream(usbStereo48k = false)
        var bufferedAudio = false
        var closed = false
        var failures = 0
        val output = AndroidTtsOutputGuard(silenceImmediately = { bufferedAudio = false }, onFailure = {
            failures++
            stream.fail(it)
        })
        val worker = async {
            runCatching {
                stream.play({ object : AndroidTtsPcmSink {
                    override suspend fun write(samples: ShortArray) { output.mutate { bufferedAudio = true } }
                    override suspend fun drain() = Unit
                    override fun close() { output.close { closed = true } }
                } })
            }
        }
        stream.begin(AndroidTtsPcmFormat(24_000, 2, 1))
        stream.offer(byteArrayOf(1, 0), 1)
        runCurrent()
        assertTrue(bufferedAudio)
        assertFalse(worker.isCompleted)
        // No source EOS or further PCM arrives: this must not wait for the 60-second source timeout.
        output.fail("USB output disconnected")
        assertFalse("The callback itself must mute pending private speech", bufferedAudio)
        runCurrent()
        assertTrue(worker.isCompleted)
        assertTrue(closed)
        assertTrue(worker.await().exceptionOrNull()?.message.orEmpty().contains("disconnected"))
        assertEquals(1, failures)
    }

    @Test fun `route abort prevents a racing writer from restarting playback`() {
        var playing = true
        var failures = 0
        val output = AndroidTtsOutputGuard({ playing = false }, { failures++ })
        output.fail("route changed")
        assertThrows(IllegalStateException::class.java) { output.mutate { playing = true } }
        output.fail("device removed")
        assertFalse(playing)
        assertEquals(1, failures)
    }

    @Test fun `close ignores a late routing callback and releases once`() {
        var released = false
        var silences = 0
        var failures = 0
        val output = AndroidTtsOutputGuard({ check(!released); silences++ }, { failures++ })
        output.close { released = true }
        output.close { fail("must release only once") }
        output.fail("late route notification after AudioTrack release")
        assertEquals(0, silences)
        assertEquals(0, failures)
    }
}
