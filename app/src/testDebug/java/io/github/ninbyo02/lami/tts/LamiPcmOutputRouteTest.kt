package io.github.ninbyo02.lami.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.shadows.ShadowAudioTrack

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [LamiPcmOutputRouteTest.Track::class, LamiPcmOutputRouteTest.Device::class])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LamiPcmOutputRouteTest {
    private lateinit var context: Context
    private lateinit var audio: AudioManager
    private lateinit var track: AudioTrack
    private lateinit var output: Track
    private var route: LamiPcmOutputRoute? = null

    @Before fun setUp() {
        Track.nextRoute = null
        Track.nextHead = 0
        context = RuntimeEnvironment.getApplication()
        audio = context.getSystemService(AudioManager::class.java)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        shadowOf(audio).setOutputDevices(emptyList())
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(24_000)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(4096).setTransferMode(AudioTrack.MODE_STREAM).build()
        output = Shadow.extract(track)
    }

    @After fun tearDown() {
        route?.close { track.release() } ?: track.release()
        shadowOf(audio).setOutputDevices(emptyList())
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        Track.nextRoute = null
        Track.nextHead = 0
    }

    @Test fun silentAndVibrateRejectBeforeAnySpeechWriteOrPlay() = runTest {
        for (mode in listOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE)) {
            audio.ringerMode = mode
            val current = LamiPcmOutputRoute(context, track)
            try {
                val error = runCatching { current.prepare() }.exceptionOrNull()
                assertTrue(error?.message.orEmpty().contains("silent/vibrate"))
                assertTrue(output.samples.isEmpty())
                assertFalse(output.operations.contains("play"))
                assertTrue(current.failures.tryReceive().isSuccess)
                assertThrows(IllegalStateException::class.java) { current.mutate { track.play() } }
            } finally { current.close {} }
        }
    }

    @Test fun modeBroadcastMutesPendingAudioBeforeWakingWorkerWithoutAnotherChunk() = runTest {
        val current = createRoute()
        assertEquals(0, current.prepare())
        current.mutate { track.write(floatArrayOf(0.2f), 0, 1, AudioTrack.WRITE_NON_BLOCKING); track.play() }
        val waiting = async {
            current.failures.receive()
            assertEquals(listOf("volume:0.0", "pause", "flush"), output.operations.takeLast(3))
        }
        runCurrent()
        assertFalse(waiting.isCompleted)

        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        context.sendBroadcast(Intent(AudioManager.RINGER_MODE_CHANGED_ACTION))
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()

        waiting.await()
        val accepted = output.samples.toList()
        assertThrows(IllegalStateException::class.java) {
            current.mutate { track.write(floatArrayOf(0.3f), 0, 1, AudioTrack.WRITE_NON_BLOCKING); track.play() }
        }
        assertEquals(accepted, output.samples)
    }

    @Test fun becomingNoisyMutesWithoutWaitingForAnotherWrite() = runTest {
        val current = createRoute()
        current.prepare()
        current.mutate { track.play() }
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(current.failures.tryReceive().isSuccess)
        assertEquals(listOf("volume:0.0", "pause", "flush"), output.operations.takeLast(3))
        assertThrows(IllegalStateException::class.java) { current.ensureAllowed() }
    }

    @Test fun incorrectUsbRouteRejectsWhileOnlySilenceHasBeenSubmitted() = runTest {
        installUsb()
        output.routeOnPlay = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Speaker")
        val current = createRoute()

        val error = runCatching { current.prepare() }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("route changed"))
        assertEquals(120, output.samples.size)
        assertTrue(output.samples.all { it == 0f })
        assertTrue(current.failures.tryReceive().isSuccess)
        assertEquals(listOf("volume:0.0", "pause", "flush"), output.operations.takeLast(3))
    }

    @Test fun usbPreflightStartsAfterItsFirstPartialWriteBeforeWaitingForRemainingSilence() = runTest {
        val usb = installUsb()
        output.routeOnPlay = usb
        output.writeLimits.addAll(listOf(0, 3, 0, 4, 0, 113))
        val current = createRoute()

        assertEquals(120, current.prepare())

        assertEquals(listOf("write", "write", "play", "write", "write", "write", "write"), output.operations)
        assertEquals(120, output.samples.size)
        assertTrue(output.samples.all { it == 0f })
        assertEquals(1, output.operations.count { it == "play" })
        assertTrue(output.writeLimits.isEmpty())
        current.ensureAllowed()
    }

    @Test fun usbStreamingEosExcludesPreflightFromStatsAndWaitsForEverySpeechFrame() = runTest {
        val usb = installUsb()
        Track.nextRoute = usb
        // Raw head includes 120 preflight frames and the first 100ms of speech.
        Track.nextHead = 120 + 2400
        val first = FloatArray(2400) { 0.2f }
        val tail = FloatArray(2400) { -0.2f }
        val chunks = Channel<FloatArray>(Channel.UNLIMITED)
        chunks.send(first)
        chunks.send(tail)
        chunks.close()
        val events = mutableListOf<TtsStreamingProgress>()
        val snapshots = mutableListOf<LamiPcmPlaybackSnapshot>()
        var stats: LamiPcmStreamPlayer.Stats? = null
        val playback = launch {
            stats = LamiPcmStreamPlayer.play(context, chunks, {}, events::add,
                stopOnEos = true, onSnapshot = snapshots::add,
                eosNowNs = { testScheduler.currentTime * 1_000_000 })
        }
        runCurrent()
        val playing = checkNotNull(Track.latest)
        assertNotSame(output, playing)
        assertEquals(120 + first.size + tail.size, playing.samples.size)
        assertEquals((first + tail).toList(), playing.samples.drop(120))
        assertEquals(2400, snapshots.single { it.phase == "eos_stop" }.headFrames)
        assertTrue(playing.operations.contains("stop"))
        assertFalse(playing.released)

        val minimum = AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val guardNs = maxOf(40_000_000L, (minimum / Float.SIZE_BYTES * 1_000_000_000L + 23_999L) / 24_000L)
        val expectedWaitMs = (100_000_000L + guardNs + 999_999L) / 1_000_000L
        advanceTimeBy(expectedWaitMs - 1)
        runCurrent()
        assertFalse("The silent preflight must not shorten the final speech tail", playing.released)
        assertTrue(playback.isActive)
        advanceUntilIdle()
        playback.join()

        assertTrue(playing.released)
        assertEquals(LamiPcmStreamPlayer.Stats(first.size + tail.size, 0), stats)
        assertEquals(first.size + tail.size, events.filter { it.event == TtsStreamEvent.PCM_CHUNK }.sumOf { it.sampleCount })
        assertEquals(first.size + tail.size, events.single { it.event == TtsStreamEvent.PLAYBACK_COMPLETED }.sampleCount)
    }

    @Test fun verifiedPrivateUsbCanPlayInSilentModeAndRemovalImmediatelyMutes() = runTest {
        val usb = installUsb()
        output.routeOnPlay = usb
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        val current = createRoute()
        assertEquals(120, current.prepare())
        assertSame(usb, output.preferred)
        current.mutate { track.write(floatArrayOf(0.2f, 0.3f), 0, 2, AudioTrack.WRITE_NON_BLOCKING) }

        shadowOf(audio).removeOutputDevice(usb, true)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(current.failures.tryReceive().isSuccess)
        assertEquals(listOf("volume:0.0", "pause", "flush"), output.operations.takeLast(3))
        assertThrows(IllegalStateException::class.java) { current.ensureAllowed() }
    }

    @Test fun normalEosAllowsNullRouteWhileDrainingButStillRejectsAnotherDevice() = runTest {
        val usb = installUsb()
        output.routeOnPlay = usb
        val current = createRoute()
        assertEquals(120, current.prepare())

        current.stopForEos()
        output.route = null
        output.listener!!.onRoutingChanged(track)
        current.ensureAllowed()
        assertFalse(current.failures.tryReceive().isSuccess)

        output.route = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Speaker")
        output.listener!!.onRoutingChanged(track)
        assertTrue(current.failures.tryReceive().isSuccess)
        assertEquals(listOf("volume:0.0", "pause", "flush"), output.operations.takeLast(3))
    }

    @Test fun usbRemovalDuringStoppedEosDrainStillMutesItsBufferedTail() = runTest {
        val usb = installUsb()
        output.routeOnPlay = usb
        val current = createRoute()
        current.prepare()
        current.stopForEos()
        output.route = null

        shadowOf(audio).removeOutputDevice(usb, true)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(current.failures.tryReceive().isSuccess)
        assertEquals(listOf("stop", "volume:0.0", "pause", "flush"), output.operations.takeLast(4))
    }

    @Test fun closeUnregistersAndLateRoutingCallbackCannotTouchReleasedTrack() = runTest {
        val usb = installUsb()
        output.routeOnPlay = usb
        val current = createRoute()
        current.prepare()
        val lateListener = checkNotNull(output.listener)
        var releases = 0
        current.close { releases++; track.release() }
        val finalOperations = output.operations.toList()

        output.route = null
        lateListener.onRoutingChanged(track)
        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(audio).removeOutputDevice(usb, true)
        shadowOf(Looper.getMainLooper()).idle()
        current.close { fail("must release only once") }

        assertEquals(1, releases)
        assertNull(output.listener)
        assertEquals(finalOperations, output.operations)
        assertTrue(current.failures.receiveCatching().isClosed)
    }

    private fun createRoute() = LamiPcmOutputRoute(context, track).also { route = it }

    private fun installUsb(): AudioDeviceInfo = device(11, AudioDeviceInfo.TYPE_USB_HEADSET, "Jabra Evolve2 40 SE")
        .also { shadowOf(audio).setOutputDevices(listOf(it)) }

    private fun device(id: Int, type: Int, name: String): AudioDeviceInfo =
        AudioDeviceInfoBuilder.newBuilder().setType(type).build().also {
            Shadow.extract<Device>(it).apply { deviceId = id; deviceType = type; label = name }
        }

    @Implements(AudioDeviceInfo::class)
    class Device {
        var deviceId = 0
        var deviceType = AudioDeviceInfo.TYPE_UNKNOWN
        var label: CharSequence = ""
        @Implementation fun getId() = deviceId
        @Implementation fun getType() = deviceType
        @Implementation fun getProductName() = label
        @Implementation fun isSink() = true
    }

    @Implements(AudioTrack::class)
    class Track : ShadowAudioTrack() {
        companion object {
            var nextRoute: AudioDeviceInfo? = null
            var nextHead = 0
            var latest: Track? = null
        }
        var routeOnPlay: AudioDeviceInfo? = nextRoute
        var route: AudioDeviceInfo? = null
        var preferred: AudioDeviceInfo? = null
        var listener: AudioRouting.OnRoutingChangedListener? = null
        val samples = mutableListOf<Float>()
        val operations = mutableListOf<String>()
        val writeLimits = ArrayDeque<Int>()
        var released = false
            private set
        private var head = nextHead
        init { latest = this }
        @Implementation fun setPreferredDevice(device: AudioDeviceInfo?): Boolean { preferred = device; return true }
        @Implementation override fun getRoutedDevice(): AudioDeviceInfo? = route
        @Implementation override fun addOnRoutingChangedListener(value: AudioRouting.OnRoutingChangedListener, handler: Handler?) { listener = value }
        @Implementation override fun removeOnRoutingChangedListener(value: AudioRouting.OnRoutingChangedListener) { if (listener === value) listener = null }
        @Implementation fun setStartThresholdInFrames(frames: Int) = frames
        @Implementation fun write(values: FloatArray, offset: Int, count: Int, mode: Int): Int {
            check(!released)
            val accepted = if (writeLimits.isNotEmpty()) minOf(writeLimits.removeFirst(), count) else count
            samples.addAll(values.slice(offset until offset + accepted))
            operations += "write"
            return accepted
        }
        @Implementation override fun getPlaybackHeadPosition() = head
        @Implementation fun getUnderrunCount() = 0
        @Implementation fun getTimestamp(timestamp: AudioTimestamp) = false
        @Implementation fun play() { check(!released); operations += "play"; route = routeOnPlay }
        @Implementation fun setVolume(volume: Float): Int { check(!released); operations += "volume:$volume"; return 0 }
        @Implementation fun pause() { check(!released); operations += "pause" }
        @Implementation override fun flush() { check(!released); operations += "flush" }
        @Implementation fun stop() { check(!released); operations += "stop"; route = null; head = 0 }
        @Implementation fun release() { check(!released); released = true; operations += "release" }
    }
}
