package io.github.ninbyo02.lami.assistant

import kotlin.math.roundToInt

/** Only a real key-up rearms a key; speech ending must not rearm a held key. */
internal class AssistantVolumeKeyPolicy {
    private val pressed = mutableSetOf<Int>()

    fun isPressed(key: Int): Boolean = key in pressed

    fun press(
        key: Int,
        repeated: Boolean,
        direction: Int,
        currentVolume: Int,
        maxVolume: Int,
        blocked: Boolean,
    ): Int? {
        if (!pressed.add(key) || repeated || blocked || maxVolume <= 0) return null
        return nextAssistantMediaVolume(currentVolume, direction, maxVolume)
    }

    fun release(key: Int) {
        pressed.remove(key)
    }

    fun reset() {
        pressed.clear()
    }
}

internal const val ASSISTANT_VOLUME_MAX_TIER = 8

internal fun nextAssistantMediaVolume(current: Int, direction: Int, max: Int): Int {
    if (max <= 0) return 0
    val tier = (current.toDouble() * 10 / max).roundToInt().coerceIn(0, ASSISTANT_VOLUME_MAX_TIER)
    return (tier + direction).coerceIn(0, ASSISTANT_VOLUME_MAX_TIER) * max / 10
}
