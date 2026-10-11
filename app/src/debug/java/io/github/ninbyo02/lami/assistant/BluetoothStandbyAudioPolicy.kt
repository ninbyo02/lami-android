package io.github.ninbyo02.lami.assistant

import android.media.AudioManager

internal fun bluetoothStandbyAudioInterruption(
    musicActive: Boolean,
    mode: Int,
    assistantActive: Boolean,
    armed: Boolean = true,
): String? = when {
    mode != AudioManager.MODE_NORMAL &&
        !(mode == AudioManager.MODE_IN_COMMUNICATION && assistantActive) -> "通話のため待機を終了しました"
    armed && musicActive && !assistantActive -> "音楽・音声再生のため待機を終了しました"
    else -> null
}
