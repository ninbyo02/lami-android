package com.sonusid.ollama.ui.screens.spriteeditor

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.text.input.TextFieldValue

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

    @Test
    fun clampFieldRejectsFourthDigitForThreeDigitMax() {
        val prev = TextFieldValue("288")
        val next = TextFieldValue("2889")
        val result = clampPxFieldValue(prev, next, 288)
        assertEquals("288", result.text)
    }

    @Test
    fun clampFieldAllowsFourDigitsWithinMax() {
        val prev = TextFieldValue("409")
        val next = TextFieldValue("4096")
        val result = clampPxFieldValue(prev, next, 4096)
        assertEquals("4096", result.text)
    }

    @Test
    fun clampFieldRejectsOverMax() {
        val prev = TextFieldValue("4096")
        val next = TextFieldValue("4097")
        val result = clampPxFieldValue(prev, next, 4096)
        assertEquals("4096", result.text)
    }

    @Test
    fun clampFieldClampsLargePaste() {
        val prev = TextFieldValue("")
        val next = TextFieldValue("99999")
        val result = clampPxFieldValue(prev, next, 288)
        assertEquals("288", result.text)
    }
}
