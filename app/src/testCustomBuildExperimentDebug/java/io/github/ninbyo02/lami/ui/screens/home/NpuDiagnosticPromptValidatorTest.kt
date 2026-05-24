package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuDiagnosticPromptValidatorTest {
    @Test
    fun `Hi is valid`() {
        val result = NpuDiagnosticPromptValidator.validate("Hi")

        assertTrue(result.isValid)
        assertEquals("Hi", result.normalizedPrompt)
        assertEquals("ok", result.reasonCode)
        assertEquals(NpuDiagnosticPromptValidator.ASCII_DIAGNOSTIC_MODE, result.promptValidationMode)
    }

    @Test
    fun `empty prompt is rejected`() {
        assertRejected("", "empty")
    }

    @Test
    fun `blank prompt is rejected`() {
        assertRejected("   ", "empty")
    }

    @Test
    fun `thirty three characters is rejected`() {
        assertRejected("a".repeat(33), "too_long")
    }

    @Test
    fun `newline is rejected`() {
        assertRejected("Hello\nLami", "contains_newline")
    }

    @Test
    fun `tab is rejected`() {
        assertRejected("Hello\tLami", "contains_tab")
    }

    @Test
    fun `japanese text is rejected`() {
        assertRejected("こんにちは", "contains_non_ascii")
    }

    @Test
    fun `emoji is rejected`() {
        assertRejected("Hi🙂", "contains_non_ascii")
    }

    @Test
    fun `control character is rejected`() {
        assertRejected("Hi\u0007", "contains_control_char")
    }

    @Test
    fun `nul character is rejected with specific reason`() {
        assertRejected("Hi\u0000", "contains_nul")
    }

    @Test
    fun `common diagnostic punctuation is valid`() {
        val result = NpuDiagnosticPromptValidator.validate("Hello, Lami!")

        assertTrue(result.isValid)
        assertEquals("Hello, Lami!", result.normalizedPrompt)
        assertEquals("ok", result.reasonCode)
    }

    @Test
    fun `underscore dash and period are valid`() {
        val result = NpuDiagnosticPromptValidator.validate("A_B-C.")

        assertTrue(result.isValid)
        assertEquals("A_B-C.", result.normalizedPrompt)
        assertEquals("ok", result.reasonCode)
    }

    @Test
    fun `disallowed ascii symbol is rejected`() {
        assertRejected("Hello/Lami", "contains_disallowed_char")
    }

    @Test
    fun `prompt is trimmed before validation`() {
        val result = NpuDiagnosticPromptValidator.validate("  Hi  ")

        assertTrue(result.isValid)
        assertEquals("Hi", result.normalizedPrompt)
    }


    @Test
    fun `utf8 internal intent accepts non ascii text`() {
        val result = NpuDiagnosticPromptValidator.validateUtf8InternalIntent("こんにちは")

        assertTrue(result.isValid)
        assertEquals("こんにちは", result.normalizedPrompt)
        assertEquals("ok", result.reasonCode)
        assertEquals(NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE, result.promptValidationMode)
    }

    @Test
    fun `utf8 internal intent rejects unpaired surrogate`() {
        val result = NpuDiagnosticPromptValidator.validateUtf8InternalIntent(String(charArrayOf('\uD800')))

        assertFalse(result.isValid)
        assertEquals("invalid_utf8", result.reasonCode)
        assertEquals(NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE, result.promptValidationMode)
    }

    private fun assertRejected(
        input: String,
        reasonCode: String,
    ) {
        val result = NpuDiagnosticPromptValidator.validate(input)

        assertFalse(result.isValid)
        assertEquals(reasonCode, result.reasonCode)
    }
}
