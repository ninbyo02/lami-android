package io.github.ninbyo02.lami.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenOrientationModeTest {
    @Test
    fun `screen orientation default is portrait to avoid TTS interruption by rotation`() {
        assertEquals(ScreenOrientationMode.PORTRAIT, ScreenOrientationMode.fromStorage(null))
        assertEquals(ScreenOrientationMode.PORTRAIT, SettingsData().screenOrientationMode)
    }

    @Test
    fun `screen orientation parses storage values`() {
        assertEquals(ScreenOrientationMode.PORTRAIT, ScreenOrientationMode.fromStorage("portrait"))
        assertEquals(ScreenOrientationMode.LANDSCAPE, ScreenOrientationMode.fromStorage("landscape"))
        assertEquals(ScreenOrientationMode.AUTO, ScreenOrientationMode.fromStorage("auto"))
    }
}
