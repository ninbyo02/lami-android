package io.github.ninbyo02.lami.npu

import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1Contract
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1Result
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1Selection
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS2DbMapper
import io.github.ninbyo02.lami.ui.screens.home.buildNpuStandardRouteS2DbSavedResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            promptIndex = 1,
            timeoutMs = 180_000L,
        )

        assertTrue(markdown.contains("automation_scope: `s2_decoding_and_save_decision_logic`"))
        assertTrue(markdown.contains("ui_db_integration: `false`"))
        assertTrue(markdown.contains("execution_mode: `single_prompt`"))
        assertTrue(markdown.contains("prompt_index: `1`"))
        assertTrue(markdown.contains("prompt_no: `1`"))
        assertTrue(markdown.contains("prompt_timeout_ms: `180000`"))
        assertTrue(
            markdown.contains(
                "| prompt_index | prompt_no | prompt_text | status | reason | quality_classification | sanitized_output | " +
                    "raw_role_contamination | db | conversation_history_saved | run_decode_reached | " +
                    "fallback_used | timeout | fresh_crash | npu_s1_decode_ms | " +
                    "npu_s1_tokens_per_second | judgement | notes |",
            ),
        )
        assertTrue(markdown.contains("| 1 | 1 | こんにちは | success | success | natural_japanese | こんにちは。 |"))
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

        assertTrue(csv.startsWith("\"prompt_index\",\"prompt_no\",\"prompt_text\",\"status\",\"reason\",\"quality_classification\""))
        assertTrue(csv.contains("\"a,b\"\"c\""))
        assertTrue(csv.contains("\"s2_db_reason=success\""))
    }

    @Test
    fun `fallback report row can be built from unsafe state text`() {
        val stateText = """
            status=failure
            reason=unsafe_prompt_result
            prompt_index=2
            prompt_no=2
            prompt_text=ああああ
            judgement=blocked
            notes=automation_scope=s2_decoding_and_save_decision_logic; ui_db_integration=false; source=state
        """.trimIndent()

        val row = NpuS2DbStabilityFallbackReport.rowFromStateText(stateText)
        val markdown = NpuS2DbStabilityReportFormatter.toMarkdown(
            timestamp = "20260601_120000",
            maxOutputTokens = 128,
            rows = listOf(row),
            promptIndex = 2,
            timeoutMs = 180_000L,
        )
        val csv = NpuS2DbStabilityReportFormatter.toCsv(listOf(row))

        assertEquals(2, row.number)
        assertEquals("ああああ", row.prompt)
        assertEquals("failure", row.status)
        assertEquals("unsafe_prompt_result", row.reason)
        assertEquals("blocked", row.judgement)
        assertTrue(markdown.contains("| 2 | 2 | ああああ | failure | unsafe_prompt_result |"))
        assertTrue(csv.contains("\"2\",\"2\",\"ああああ\",\"failure\",\"unsafe_prompt_result\""))
    }

    @Test
    fun `report formatter normalizes Japanese internal spaces in markdown and csv`() {
        val spacedOutput = "承 知いたしました。\n1. 箇条書きの作成\n2. 3つの項目を提示\n3. 短くまとめ る"
        val markdown = NpuS2DbStabilityReportFormatter.toMarkdown(
            timestamp = "20260601_120000",
            maxOutputTokens = 128,
            rows = listOf(sampleRow(sanitizedOutput = spacedOutput)),
            promptIndex = 8,
            timeoutMs = 180_000L,
        )
        val csv = NpuS2DbStabilityReportFormatter.toCsv(
            rows = listOf(sampleRow(sanitizedOutput = spacedOutput)),
        )

        assertTrue(markdown.contains("承知いたしました。"))
        assertTrue(markdown.contains("短くまとめる"))
        assertFalse(markdown.contains("承 知いたしました。"))
        assertFalse(markdown.contains("短くまとめ る"))
        assertTrue(csv.contains("承知いたしました。"))
        assertTrue(csv.contains("短くまとめる"))
        assertFalse(csv.contains("承 知いたしました。"))
        assertFalse(csv.contains("短くまとめ る"))
    }

    @Test
    fun `report formatter normalizes literal newline sanitized output cell before markdown and csv rows`() {
        val spacedOutput = "承 知いたしました。\\n1. 箇条書きの作成\\n3. 短くまとめ る"
        val normalizedOutput = "承知いたしました。\\n1. 箇条書きの作成\\n3. 短くまとめる"
        val row = sampleRow(sanitizedOutput = spacedOutput)

        val cells = NpuS2DbStabilityReportFormatter.reportCells(row)
        val markdown = NpuS2DbStabilityReportFormatter.toMarkdown(
            timestamp = "20260601_120000",
            maxOutputTokens = 128,
            rows = listOf(row),
            promptIndex = 8,
            timeoutMs = 180_000L,
        )
        val csv = NpuS2DbStabilityReportFormatter.toCsv(rows = listOf(row))

        assertEquals(normalizedOutput, cells[6])
        assertTrue(
            markdown.contains(
                "| 1 | 1 | こんにちは | success | success | natural_japanese | " +
                    "承知いたしました。\\\\n1. 箇条書きの作成\\\\n3. 短くまとめる | false | true | true | " +
                    "true | false | false | false | 123 | 20.0 | pass_saved | " +
                    "automation_scope=s2_decoding_and_save_decision_logic |",
            ),
        )
        assertTrue(
            csv.contains(
                "\"1\",\"1\",\"こんにちは\",\"success\",\"success\",\"natural_japanese\"," +
                    "\"承知いたしました。\\n1. 箇条書きの作成\\n3. 短くまとめる\",\"false\"," +
                    "\"true\",\"true\",\"true\",\"false\",\"false\",\"false\",\"123\",\"20.0\"," +
                    "\"pass_saved\",\"automation_scope=s2_decoding_and_save_decision_logic\"",
            ),
        )
        assertFalse(markdown.contains("承 知"))
        assertFalse(markdown.contains("まとめ る"))
        assertFalse(csv.contains("承 知"))
        assertFalse(csv.contains("まとめ る"))
    }

    @Test
    fun `report row from S2 result normalizes prompt 8 Japanese internal spaces`() {
        val spacedOutput = "承 知いたしました。\n1. 箇条書きの作成\n2. 3つの項目を提示\n3. 短くまとめ る"
        val s2Result = buildNpuStandardRouteS2DbSavedResult(
            successResult(sanitizedOutput = spacedOutput),
        )

        val row = NpuS2DbStabilityReportRow.fromResult(
            number = 8,
            prompt = "箇条書きで3つ教えて",
            result = s2Result,
            saveCandidateReady = true,
            s2DbReason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        )

        assertTrue(row.sanitizedOutput.contains("承知いたしました。"))
        assertTrue(row.sanitizedOutput.contains("短くまとめる"))
        assertFalse(row.sanitizedOutput.contains("承 知いたしました。"))
        assertFalse(row.sanitizedOutput.contains("短くまとめ る"))
    }

    @Test
    fun `receiver normalization feeds S2 DB candidate without changing raw output`() {
        val spacedOutput = "承 知いたしました。\n1. 箇条書きの作成\n2. 3つの項目を提示\n3. 短くまとめ る"
        val normalizedOutput = "承知いたしました。\n1. 箇条書きの作成\n2. 3つの項目を提示\n3. 短くまとめる"

        val normalizedResult = normalizeNpuS2DbStabilityResult(
            successResult(
                rawOutput = spacedOutput,
                sanitizedOutput = spacedOutput,
            ),
        )
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "箇条書きで3つ教えて",
            s1Result = normalizedResult,
        )
        val candidate = requireNotNull(mapping.saveCandidate)

        assertEquals(spacedOutput, normalizedResult.rawOutput)
        assertEquals(normalizedOutput, normalizedResult.sanitizedOutput)
        assertEquals(normalizedOutput, candidate.assistantMessage.text)
        assertTrue(candidate.assistantMessage.sourceDisplayText.contains("raw_output=$spacedOutput"))
        assertTrue(candidate.assistantMessage.sourceDisplayText.contains("sanitized_output=$normalizedOutput"))
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

    private fun successResult(
        rawOutput: String = "こんにちは。",
        sanitizedOutput: String = "こんにちは。",
    ): NpuStandardRouteS1Result = NpuStandardRouteS1Result(
        selection = NpuStandardRouteS1Selection(enabled = true),
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput = rawOutput,
        sanitizedOutput = sanitizedOutput,
        qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        runDecodeReached = true,
        npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed = false,
        timeout = false,
        freshCrash = false,
    )
}
