package io.github.ninbyo02.lami.assistant

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioFocusRequest
import android.os.Looper
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.shadows.ShadowAudioTrack
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [BluetoothConversationRouteTest.CallTrackShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class BluetoothConversationRouteTest {
    // Robolectric has no native short-PCM writer or device selection backend.
    @Implements(AudioTrack::class)
    class CallTrackShadow : ShadowAudioTrack() {
        @Implementation
        protected fun native_setOutputDevice(deviceId: Int): Boolean = true

        @Implementation
        protected fun native_write_short(data: ShortArray, offset: Int, count: Int,
            format: Int, blocking: Boolean): Int = count
    }

    private lateinit var audio: AudioManager
    private lateinit var probe: BluetoothMicProbe
    private val states = mutableListOf<String>()

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        audio = context.getSystemService(AudioManager::class.java)
        val pioneer = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP).build()
        val port = ReflectionHelpers.getField<Any>(pioneer, "mPort")
        ReflectionHelpers.setField(port, "mName", "SE-MS7BT")
        val sco = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO).build()
        ReflectionHelpers.setField(ReflectionHelpers.getField<Any>(sco, "mPort"), "mRole", 2)
        ShadowAudioTrack.setRoutedDevice(sco)
        shadowOf(audio).setOutputDevices(listOf(pioneer, sco))
        shadowOf(audio).setAvailableCommunicationDevices(listOf(sco))
        probe = BluetoothMicProbe(context) { state, _, _ -> states += state }
    }

    private fun begin() {
        probe.beginConversation()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(states.joinToString("; "), probe.isConversationActive)
        assertEquals(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, audio.communicationDevice?.type)
        assertTrue(states.last().contains("マイク停止"))
    }

    private fun assertReleased() {
        assertFalse(probe.isConversationActive)
        assertNull(audio.communicationDevice)
        assertEquals(AudioManager.MODE_NORMAL, audio.mode)
        assertNotNull(shadowOf(audio).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun timeout_releasesConnectionAtSixtySecondsWithoutOpeningMicrophone() {
        begin()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(59))
        assertTrue(probe.isConversationActive)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertReleased()
        assertTrue(states.last().contains("60秒"))
    }

    @Test
    fun explicitStop_releasesEvenWhenPublicModeAlreadyReportsNormal() {
        begin()
        audio.mode = AudioManager.MODE_NORMAL
        probe.cancel()
        assertReleased()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertEquals("停止中", states.last())
    }

    @Test
    fun musicStarts_releasesConnectionInsteadOfCompetingWithPlayer() {
        begin()
        shadowOf(audio).setIsMusicActive(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        assertReleased()
    }

    @Test
    fun routeDisappears_releasesWithoutReconnecting() {
        begin()
        audio.clearCommunicationDevice()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        assertReleased()
    }

    @Test
    fun otherCallMode_releasesConnection() {
        begin()
        audio.mode = AudioManager.MODE_IN_CALL
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        assertReleased()
    }

    @Test
    fun audioFocusLoss_releasesConnectionImmediately() {
        begin()
        val request = ReflectionHelpers.getField<AudioFocusRequest>(probe, "conversationFocus")
        ReflectionHelpers.callInstanceMethod<AudioManager.OnAudioFocusChangeListener>(
            request, "getOnAudioFocusChangeListener").onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertReleased()
    }

    @Test
    fun holdingConnection_doesNotOpenRecorderOrRetainSpeech() {
        begin()
        assertNull(ReflectionHelpers.getField<Any?>(probe, "recorder"))
        assertNull(ReflectionHelpers.getField<Any?>(probe, "samples"))
        assertEquals(AudioTrack.PLAYSTATE_PLAYING,
            ReflectionHelpers.getField<AudioTrack>(probe, "conversationHold").playState)
        probe.cancel()
        assertNull(ReflectionHelpers.getField<Any?>(probe, "conversationHold"))
        assertReleased()
    }

    @Test
    fun startingWhileMusicPlays_doesNotAcquireRouteOrFocus() {
        shadowOf(audio).setIsMusicActive(true)
        probe.beginConversation()
        assertFalse(probe.isConversationActive)
        assertNull(audio.communicationDevice)
        assertNull(shadowOf(audio).lastAudioFocusRequest)
    }
}
