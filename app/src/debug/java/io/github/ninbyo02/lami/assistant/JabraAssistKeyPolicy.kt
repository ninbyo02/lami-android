package io.github.ninbyo02.lami.assistant

import android.view.KeyEvent

internal fun isJabraAssistDevice(vendorId: Int, productId: Int): Boolean =
    vendorId == 0x0b0e && productId == 0x2e41

internal data class JabraKeyResult(val consume: Boolean, val launch: Boolean = false)

internal class JabraAssistKeyPolicy {
    private data class Press(val downTime: Long, var launchAllowed: Boolean)
    private val presses = mutableMapOf<Int, Press>()

    fun handle(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        deviceId: Int,
        downTime: Long,
        eventTime: Long,
        canceled: Boolean,
        eligible: Boolean,
    ): JabraKeyResult {
        if (keyCode != KeyEvent.KEYCODE_MEDIA_PREVIOUS) return JabraKeyResult(false)
        val press = presses[deviceId]?.takeIf { it.downTime == downTime }
        if (action == KeyEvent.ACTION_DOWN) {
            if (repeatCount == 0) {
                presses.remove(deviceId)
                if (!eligible || canceled) return JabraKeyResult(false)
                presses[deviceId] = Press(downTime, true)
                return JabraKeyResult(true)
            }
            if (press == null) return JabraKeyResult(false)
            press.launchAllowed = press.launchAllowed && eligible && !canceled
            return JabraKeyResult(true)
        }
        if (action != KeyEvent.ACTION_UP || press == null) return JabraKeyResult(false)
        presses.remove(deviceId)
        // Consume the matching UP even if playback starts after we consumed DOWN.
        return JabraKeyResult(
            consume = true,
            launch = press.launchAllowed && eligible && !canceled &&
                eventTime - downTime in 0L..10_000L,
        )
    }

    fun reset() = presses.clear()
}
