package io.github.ninbyo02.lami.assistant

import android.view.KeyEvent
import java.security.MessageDigest

internal const val JABRA_HID_DESCRIPTOR_SHA256 =
    "c8f27a590fccec786c758fecb26e8c93fd641c0f04c9b06e9d8e24c99cabf085"

internal fun matchesJabraDescriptor(bytes: ByteArray): Boolean =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) } == JABRA_HID_DESCRIPTOR_SHA256

internal data class JabraUsbKey(val code: Int, val down: Boolean)

/** Report 1 layout verified against the physical Evolve2 40 SE descriptor. */
internal class JabraUsbReports {
    private var held = 0
    private val keys = intArrayOf(
        KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    )

    fun decode(report: ByteArray): List<JabraUsbKey> {
        if (report.firstOrNull()?.toInt() != 1) return emptyList()
        require(report.size == 3 && report[2] == 0.toByte()) { "Unexpected consumer report" }
        val next = report[1].toInt() and 255
        val changes = keys.indices.filter { ((next xor held) and (1 shl it)) != 0 }
            .map { JabraUsbKey(keys[it], (next and (1 shl it)) != 0) }
        held = next
        return changes
    }
}
