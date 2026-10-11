package io.github.ninbyo02.lami.assistant

import android.media.AudioManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothStandbyAudioPolicyTest {
    @Test fun ownSpeechDoesNotEndStandby() {
        assertNull(bluetoothStandbyAudioInterruption(true, AudioManager.MODE_NORMAL, true))
    }

    @Test fun initialConfirmationDoesNotEndStandbyBeforeArming() {
        assertNull(bluetoothStandbyAudioInterruption(true, AudioManager.MODE_NORMAL, false, armed = false))
    }

    @Test fun ownBluetoothMicrophoneRouteDoesNotEndStandby() {
        assertNull(bluetoothStandbyAudioInterruption(false, AudioManager.MODE_IN_COMMUNICATION, true))
    }

    @Test fun externalPlaybackEndsIdleStandby() {
        assertNotNull(bluetoothStandbyAudioInterruption(true, AudioManager.MODE_NORMAL, false))
    }

    @Test fun phoneCallStillEndsStandbyDuringAssistantWork() {
        assertNotNull(bluetoothStandbyAudioInterruption(false, AudioManager.MODE_IN_CALL, true))
        assertNotNull(bluetoothStandbyAudioInterruption(false, AudioManager.MODE_IN_COMMUNICATION, false))
    }
}
