package io.github.ninbyo02.lami.assistant

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothMicProbeTest {
    @Test
    fun communicationCapture_allowsDelayedNormalModeReport() {
        assertTrue(BluetoothMicProbe.acceptsCaptureMode(
            AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_NORMAL, false))
    }

    @Test
    fun communicationCapture_allowsActiveCommunicationMode() {
        assertTrue(BluetoothMicProbe.acceptsCaptureMode(
            AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_IN_COMMUNICATION, false))
    }

    @Test
    fun communicationCapture_rejectsCallAndRingtoneModes() {
        listOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_RINGTONE,
            AudioManager.MODE_CALL_SCREENING, AudioManager.MODE_CALL_REDIRECT,
            AudioManager.MODE_COMMUNICATION_REDIRECT).forEach { reported ->
            assertFalse(BluetoothMicProbe.acceptsCaptureMode(
                AudioManager.MODE_IN_COMMUNICATION, reported, false))
        }
    }

    @Test
    fun routeOnlyCapture_rejectsAnotherCommunicationModeOwner() {
        assertTrue(BluetoothMicProbe.acceptsCaptureMode(
            AudioManager.MODE_NORMAL, AudioManager.MODE_NORMAL, false))
        assertFalse(BluetoothMicProbe.acceptsCaptureMode(
            AudioManager.MODE_NORMAL, AudioManager.MODE_IN_COMMUNICATION, false))
    }

    @Test
    fun musicPlayback_rejectsBothNormalAndCommunicationReports() {
        listOf(AudioManager.MODE_NORMAL, AudioManager.MODE_IN_COMMUNICATION).forEach { reported ->
            assertFalse(BluetoothMicProbe.acceptsCaptureMode(
                AudioManager.MODE_IN_COMMUNICATION, reported, true))
        }
    }
}
