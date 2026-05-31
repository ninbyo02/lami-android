package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuS2DbStabilityReportFormatterTest {
    @Test
    fun `defines the S2 DB stability prompt set`() {
        assertEquals(
            listOf(
                "こんにちは",
                "ああああ",
                "明日の天気は",
                "Pythonについて一言で教えて",
                "1+1は？",
                "自己紹介して",
                "日本語で短く返答してください",
                "箇条書きで3つ教えて",
                "今日の予定を確認したい",
                "ありがとう",
            ),
            NpuS2DbStabilityReportFormatter.prompts,
        )
    }

    @Test
    fun `markdown report contains required columns and scope`() {
        val markdown = NpuS2DbStabilityReportFormatter.toMarkdown(
            timestamp = "20260601_120000",
            maxOutputTokens = 128,
            rows = listOf(sampleRow()),
            promptIndex = 0,
            timeoutMs = 180_000L,
        )

        assertTrue(markdown.contains("automation_scope: `s2_decoding_and_save_decision_logic`"))
        assertTrue(markdown.contains("ui_db_integration: `false`"))
        assertTrue(markdown.contains("execution_mode: `single_prompt`"))
        assertTrue(markdown.contains("prompt_index: `0`"))
        assertTrue(markdown.contains("prompt_timeout_ms: `180000`"))
        assertTrue(
            markdown.contains(
                "| No | prompt | status | reason | quality_classification | sanitized_output | " +
                    "raw_role_contamination | db | conversation_history_saved | run_decode_reached | " +
                    "fallback_used | timeout | fresh_crash | npu_s1_decode_ms | " +
                    "npu_s1_tokens_per_second | judgement | notes |",
            ),
        )
        assertTrue(markdown.contains("| 1 | こんにちは | success | success | natural_japanese | こんにちは。 |"))
    }

    @Test
    fun `csv report quotes all required fields`() {
        val csv = NpuS2DbStabilityReportFormatter.toCsv(
            rows = listOf(
                sampleRow(
                    sanitizedOutput = "a,b\"c",
                    notes = "s2_db_reason=success",
                ),
            ),
        )

        assertTrue(csv.startsWith("\"No\",\"prompt\",\"status\",\"reason\",\"quality_classification\""))
        assertTrue(csv.contains("\"a,b\"\"c\""))
        assertTrue(csv.contains("\"s2_db_reason=success\""))
    }

    private fun sampleRow(
        sanitizedOutput: String = "こんにちは。",
        notes: String = "automation_scope=s2_decoding_and_save_decision_logic",
    ): NpuS2DbStabilityReportRow =
        NpuS2DbStabilityReportRow(
            number = 1,
            prompt = "こんにちは",
            status = "success",
            reason = "success",
            qualityClassification = "natural_japanese",
            sanitizedOutput = sanitizedOutput,
            rawRoleContamination = false,
            db = true,
            conversationHistorySaved = true,
            runDecodeReached = true,
            fallbackUsed = false,
            timeout = false,
            freshCrash = false,
            npuS1DecodeMs = "123",
            npuS1TokensPerSecond = "20.0",
            judgement = "pass_saved",
            notes = notes,
        )
}
