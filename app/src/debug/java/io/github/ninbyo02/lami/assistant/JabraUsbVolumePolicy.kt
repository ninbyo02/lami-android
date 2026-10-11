package io.github.ninbyo02.lami.assistant

import android.view.KeyEvent

internal data class JabraUsbVolumeResult(val consume: Boolean, val target: Int? = null)

/** A consumed down owns its up, even if the chat or audio state changes meanwhile. */
internal class JabraUsbVolumePolicy {
    private val keys = AssistantVolumeKeyPolicy()

    fun handle(
        code: Int,
        down: Boolean,
        eligible: Boolean,
        blocked: Boolean,
        current: Int,
        max: Int,
    ): JabraUsbVolumeResult {
        if (code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_UP) {
            return JabraUsbVolumeResult(false)
        }
        if (!down) {
            val owned = keys.isPressed(code)
            keys.release(code)
            return JabraUsbVolumeResult(owned)
        }
        if (!eligible && !keys.isPressed(code)) return JabraUsbVolumeResult(false)
        val target = keys.press(code, false,
            if (code == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1, current, max, blocked)
        return JabraUsbVolumeResult(true, target)
    }

    fun reset() = keys.reset()
}
