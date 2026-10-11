package io.github.ninbyo02.lami.assistant

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JabraUsbReportsTest {
    @Test fun previousPressAndRelease() {
        val decoder = JabraUsbReports()
        assertEquals(listOf(JabraUsbKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, true)),
            decoder.decode(byteArrayOf(1, 0x20, 0)))
        assertEquals(emptyList<JabraUsbKey>(), decoder.decode(byteArrayOf(1, 0x20, 0)))
        assertEquals(listOf(JabraUsbKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, false)),
            decoder.decode(byteArrayOf(1, 0, 0)))
    }

    @Test fun volumeAndPlayPauseBits() {
        val decoder = JabraUsbReports()
        assertEquals(listOf(JabraUsbKey(KeyEvent.KEYCODE_VOLUME_DOWN, true),
            JabraUsbKey(KeyEvent.KEYCODE_VOLUME_UP, true),
            JabraUsbKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true)),
            decoder.decode(byteArrayOf(1, 0x83.toByte(), 0)))
    }

    @Test fun telephonyDoesNotResetHeldConsumerKey() {
        val decoder = JabraUsbReports()
        decoder.decode(byteArrayOf(1, 0x20, 0))
        assertEquals(emptyList<JabraUsbKey>(), decoder.decode(byteArrayOf(2, 0, 0)))
        assertEquals(listOf(JabraUsbKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, false)),
            decoder.decode(byteArrayOf(1, 0, 0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun truncatedReportRejected() { JabraUsbReports().decode(byteArrayOf(1, 0x20)) }

    @Test(expected = IllegalArgumentException::class)
    fun unexpectedPaddingRejected() { JabraUsbReports().decode(byteArrayOf(1, 0x20, 1)) }

    @Test fun unknownDescriptorRejected() {
        assertFalse(matchesJabraDescriptor(byteArrayOf(1, 2, 3)))
    }
}
