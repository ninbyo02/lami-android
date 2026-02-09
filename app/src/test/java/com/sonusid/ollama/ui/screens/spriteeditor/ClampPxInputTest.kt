package com.sonusid.ollama.ui.screens.spriteeditor

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextRange

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
    fun clampCanvasSizeToUpperLimit() {
        val parsed = "9999".toIntOrNull()
        val safe = (parsed ?: 288).coerceIn(1, 4096)
        assertEquals(4096, safe)
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
    fun clampFieldAllowsFourDigitsFromEmpty() {
        val prev = TextFieldValue("")
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

    @Test
    fun rejectFieldRejectsFifthDigit() {
        val prev = TextFieldValue("1024", selection = TextRange(4))
        val result = rejectPxFieldValueOverMaxDigits(prev, "10245", maxDigits = 4)
        assertEquals("1024", result.text)
        assertEquals(TextRange(4), result.selection)
    }

    @Test
    fun rejectFieldFiltersDigitsOnly() {
        val prev = TextFieldValue("1", selection = TextRange(1))
        val result = rejectPxFieldValueOverMaxDigits(prev, "12a3", maxDigits = 4)
        assertEquals("123", result.text)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun rejectFieldAllowsEmpty() {
        val prev = TextFieldValue("12", selection = TextRange(2))
        val result = rejectPxFieldValueOverMaxDigits(prev, "", maxDigits = 4)
        assertEquals("", result.text)
        assertEquals(TextRange(0), result.selection)
    }

    @Test
    fun rejectFieldAllowsExactMaxDigits() {
        val prev = TextFieldValue("999", selection = TextRange(3))
        val result = rejectPxFieldValueOverMaxDigits(prev, "9999", maxDigits = 4)
        assertEquals("9999", result.text)
        assertEquals(TextRange(4), result.selection)
    }

    @Test
    fun rejectFieldPreservesLeadingZeros() {
        val prev = TextFieldValue("0", selection = TextRange(1))
        val result = rejectPxFieldValueOverMaxDigits(prev, "0001", maxDigits = 4)
        assertEquals("0001", result.text)
        assertEquals(TextRange(4), result.selection)
    }
}
