package io.github.ninbyo02.lami.tts

import android.media.AudioDeviceInfo
import android.os.Looper
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.AudioDeviceInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class UsbTtsPlaybackTest {
    private lateinit var playback: UsbTtsPlayback
    private val device = AudioDeviceInfoBuilder.newBuilder()
        .setType(AudioDeviceInfo.TYPE_USB_HEADSET).build()
    private var completions = 0
    private val events = mutableListOf<String>()

    @Before
    fun setUp() {
        playback = UsbTtsPlayback(RuntimeEnvironment.getApplication(),
            completed = { completions++ }, trace = { events += it })
    }

    @Test
    fun stopDeletesFileAndIgnoresLateSynthesis() {
        val file = playback.begin("first", device)
        playback.stop()
        assertFalse(file.exists())
        assertFalse(playback.synthesized("first"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        assertEquals(0, completions)
    }

    @Test
    fun replacementDeletesOldFileAndKeepsNewRequest() {
        val first = playback.begin("first", device)
        val second = playback.begin("second", device)
        assertFalse(first.exists())
        assertTrue(second.exists())
        assertFalse(playback.failed("first"))
        assertTrue(playback.isActive())
        playback.stop()
    }

    @Test
    fun disconnectedOutputEndsWithoutStartingPlayer() {
        val file = playback.begin("first", device)
        assertTrue(playback.synthesized("first"))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(file.exists())
        assertFalse(playback.isActive())
        assertEquals(1, completions)
        assertFalse(events.any { it.startsWith("usb_pcm_started") })
    }

    @Test
    fun synthesisTimeoutReleasesRequestOnce() {
        val file = playback.begin("first", device)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        assertFalse(file.exists())
        assertEquals(1, completions)
        assertEquals(listOf("usb_pcm_end reason=synthesis_timeout"), events)
        assertFalse(playback.failed("first"))
    }
}
