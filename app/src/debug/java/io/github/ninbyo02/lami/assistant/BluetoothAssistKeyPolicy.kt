package io.github.ninbyo02.lami.assistant

import android.view.KeyEvent

internal data class BluetoothKeyResult(val consume: Boolean, val launch: Boolean = false)

/** Only a matching short press can complete the one-shot opt-in trial. */
internal class BluetoothAssistKeyPolicy {
    private var held: KeyEvent? = null
    private var repeated = false

    fun handle(event: KeyEvent, eligible: Boolean): BluetoothKeyResult {
        if (event.keyCode !in setOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_HEADSETHOOK)) {
            return BluetoothKeyResult(false)
        }
        val previous = held
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount != 0) {
                if (previous?.keyCode == event.keyCode && previous.deviceId == event.deviceId) {
                    repeated = true
                    return BluetoothKeyResult(true)
                }
                return BluetoothKeyResult(false)
            }
            held = if (eligible && !event.isCanceled) KeyEvent(event) else null
            repeated = false
            return BluetoothKeyResult(held != null)
        }
        if (event.action != KeyEvent.ACTION_UP || previous == null ||
            previous.keyCode != event.keyCode || previous.deviceId != event.deviceId ||
            previous.downTime != event.downTime) return BluetoothKeyResult(false)
        held = null
        val shortPress = event.eventTime - previous.eventTime in 0L..1500L
        return BluetoothKeyResult(true, eligible && shortPress && !repeated && !event.isCanceled)
    }
}
