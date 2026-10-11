package io.github.ninbyo02.lami.assistant

import android.view.KeyEvent

/** Preserve both pause and resume once the center button has been used for music. */
internal class JabraUsbCenterPolicy {
    private var shortPress = BluetoothAssistKeyPolicy()
    var musicReserved = false
        private set

    fun handle(event: KeyEvent, eligible: Boolean, musicPlaying: Boolean,
        canLaunch: Boolean = true): BluetoothKeyResult {
        if (event.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return BluetoothKeyResult(false)
        if (musicPlaying) musicReserved = true
        val result = shortPress.handle(event, eligible && !musicReserved)
        return result.copy(launch = result.launch && canLaunch)
    }

    fun reset() {
        shortPress = BluetoothAssistKeyPolicy()
        musicReserved = false
    }
}
