package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qairt244NpuDiagnosticPromptValidatorTest {
    @Test
    fun `hidden template experiment allows bounded multiline input`() {
        val input = "あなたは親切なAIアシスタントです。\nユーザー: こんにちは\nアシスタント:"

        val result = NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(input)

        assertTrue(result.isValid)
        assertEquals(NpuDiagnosticPromptValidator.UTF8_HIDDEN_TEMPLATE_EXPERIMENT_MODE, result.promptValidationMode)
        assertEquals(38, result.promptInputCodePoints)
        assertEquals(128, result.promptInputCodePointLimit)
        assertEquals(NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_INPUT_LIMIT_MODE, result.promptInputLimitMode)
    }

    @Test
    fun `hidden template experiment rejects inputs above one hundred twenty eight code points`() {
        val input = "あ".repeat(129)

        val result = NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment(input)

        assertFalse(result.isValid)
        assertEquals("too_long", result.reasonCode)
        assertEquals(129, result.promptInputCodePoints)
        assertEquals(128, result.promptInputCodePointLimit)
    }

    @Test
    fun `existing hidden experimental guard remains thirty two code points`() {
        val result = NpuDiagnosticPromptValidator.validateUtf8HiddenExperimental("あ".repeat(33))

        assertFalse(result.isValid)
        assertEquals("too_long", result.reasonCode)
        assertEquals(33, result.promptInputCodePoints)
        assertEquals(32, result.promptInputCodePointLimit)
        assertEquals(NpuDiagnosticPromptValidator.DEFAULT_INPUT_LIMIT_MODE, result.promptInputLimitMode)
    }

    @Test
    fun `hidden template experiment still rejects nul and carriage return`() {
        val nul = NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment("hello\u0000")
        val carriageReturn = NpuDiagnosticPromptValidator.validateUtf8HiddenTemplateExperiment("hello\rworld")

        assertFalse(nul.isValid)
        assertEquals("contains_nul", nul.reasonCode)
        assertFalse(carriageReturn.isValid)
        assertEquals("contains_newline", carriageReturn.reasonCode)
    }
}
