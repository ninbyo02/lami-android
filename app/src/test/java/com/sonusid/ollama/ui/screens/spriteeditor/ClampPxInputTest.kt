package com.sonusid.ollama.ui.screens.spriteeditor

import org.junit.Assert.assertEquals
import org.junit.Test

class ClampPxInputTest {
    @Test
    fun clampForThreeDigitMax() {
        assertEquals("2", clampPxInput("2", 288))
        assertEquals("28", clampPxInput("28", 288))
        assertEquals("288", clampPxInput("288", 288))
        assertEquals("288", clampPxInput("2888", 288))
    }

    @Test
    fun clampForUpperLimit() {
        assertEquals("999", clampPxInput("1000", 999))
    }

    @Test
    fun clampForFourDigitMax() {
        assertEquals("102", clampPxInput("102", 1024))
        assertEquals("1024", clampPxInput("1024", 1024))
        assertEquals("1024", clampPxInput("9999", 1024))
    }

    @Test
    fun clampForEmptyInput() {
        assertEquals("", clampPxInput("", 288))
    }

    @Test
    fun clampForMixedInput() {
        assertEquals("123", clampPxInput("12a3", 9999))
    }

    @Test
    fun clampForTwoDigitMax() {
        assertEquals("99", clampPxInput("100", 99))
    }
}
