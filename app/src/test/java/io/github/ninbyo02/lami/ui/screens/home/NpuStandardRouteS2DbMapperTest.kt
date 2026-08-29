package io.github.ninbyo02.lami.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpuStandardRouteS2DbMapperTest {
    @Test
    fun `successful S1 result creates DB save candidate`() {
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = " こんにちは ",
            s1Result = successResult(),
        )
        val candidate = requireNotNull(mapping.saveCandidate)

        assertTrue(mapping.hasSaveCandidate)
        assertNull(mapping.failureReason)
        assertEquals("こんにちは", candidate.userMessage.text)
        assertTrue(candidate.userMessage.isSendByMe)
        assertEquals("こんにちは。", candidate.assistantMessage.text)
        assertEquals("こんにちは。", candidate.assistantMessage.sourceDisplayText)
        assertFalse(candidate.assistantMessage.isSendByMe)
        assertTrue(candidate.sideEffects.dbConnected)
        assertTrue(candidate.sideEffects.conversationHistorySaved)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.markdown)
        assertFalse(candidate.sideEffects.streaming)
        assertFalse(candidate.sideEffects.backendNpuPersisted)
        assertTrue(candidate.readyToPersist)
    }

    @Test
    fun `assistant candidate uses sanitized output and source display text only`() {
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "こんにちは",
            s1Result = successResult(
                rawOutput = "raw diagnostic text",
                sanitizedOutput = "保存する応答。",
                displayText = "保存する応答。",
            ),
        )
        val assistant = requireNotNull(mapping.saveCandidate).assistantMessage

        assertEquals("保存する応答。", assistant.text)
        assertEquals("保存する応答。", assistant.sourceDisplayText)
        assertFalse(assistant.text.contains("raw diagnostic text"))
        assertFalse(assistant.sourceDisplayText.contains("raw diagnostic text"))
    }

    @Test
    fun `assistant candidate uses prepared output when quality candidate passes`() {
        val s1Result = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                result = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                success = true,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = ">2</start_of_turn>\n<end_of_turn>\n<start_of_turn>user>次の計算に日本語で",
                sanitizedOutput = "2</start_of_turn>\n\n次の計算に日本語で",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_TEMPLATE_ARTIFACT,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
                inputPrompt = "1+1は？",
            ),
        )

        val assistant = requireNotNull(
            NpuStandardRouteS2DbMapper.map(
                userPrompt = "1+1は？",
                s1Result = s1Result,
            ).saveCandidate,
        ).assistantMessage

        assertEquals("2", assistant.text)
        assertEquals("2", s1Result.actualDisplayText)
        assertFalse(assistant.text.contains("<start_of_turn>"))
        assertFalse(assistant.text.contains("次の計算"))
    }

    @Test
    fun `failed S1 result creates no DB save candidate`() {
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "こんにちは",
            s1Result = successResult(fallbackUsed = true),
        )

        assertFalse(mapping.hasSaveCandidate)
        assertNull(mapping.saveCandidate)
        assertEquals(NpuStandardRouteS2DbContract.FAILURE_S1_NOT_SUCCESS, mapping.failureReason)
    }

    @Test
    fun `sanitized Japanese answer before a trailing user turn creates DB save candidate`() {
        assertRawRoleTailRepaired("どうしましたか。\nユーザー: ああああ")
    }

    @Test
    fun `mismatched sanitized text keeps trailing user turn blocked`() {
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "こんにちは",
            s1Result = successResult(
                rawOutput = "どうしましたか。\nユーザー: ああああ",
                sanitizedOutput = "異なる応答。",
            ),
        )

        assertFalse(mapping.hasSaveCandidate)
        assertEquals(NpuStandardRouteS2DbContract.FAILURE_RAW_ROLE_CONTAMINATION, mapping.failureReason)
    }

    @Test
    fun `raw output containing Japanese assistant role marker creates no DB save candidate`() {
        assertRawRoleContaminationBlocked("どうしましたか。\nアシスタント: 何か困っていますか。")
    }

    @Test
    fun `sanitized answer before a trailing English user turn creates DB save candidate`() {
        assertRawRoleTailRepaired("どうしましたか。\nUser: test")
    }

    @Test
    fun `raw output containing English assistant role marker creates no DB save candidate`() {
        assertRawRoleContaminationBlocked("Hello.\nAssistant: How can I help?")
    }

    @Test
    fun `natural sanitized prefix is accepted when raw output continues into a user turn`() {
        val contaminated = NpuStandardRouteS1Mapper.map(
            NpuStandardRouteS1RawResult(
                status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                result = NpuStandardRouteS1Contract.STATUS_SUCCESS,
                success = true,
                reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
                rawOutput = "どうしましたか。\nユーザー: ああああ\nアシスタント: 何か困っていますか。",
                sanitizedOutput = "どうしましたか。",
                qualityClassification = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
                runDecodeReached = true,
                npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                fallbackUsed = false,
                timeout = false,
                freshCrash = false,
            ),
        )
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "こんにちは",
            s1Result = contaminated,
        )

        assertTrue(contaminated.successCriteriaMet)
        assertEquals(NpuStandardRouteS1Contract.STATUS_SUCCESS, contaminated.status)
        assertEquals(NpuStandardRouteS1Contract.REASON_SUCCESS, contaminated.reason)
        assertEquals(NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE, contaminated.qualityClassification)
        assertEquals("どうしましたか。", contaminated.actualDisplayText)
        assertTrue(mapping.hasSaveCandidate)
        assertEquals("どうしましたか。", mapping.saveCandidate?.assistantMessage?.text)
        assertNull(mapping.failureReason)
    }

    @Test
    fun `blank user prompt creates no DB save candidate`() {
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "   ",
            s1Result = successResult(),
        )

        assertFalse(mapping.hasSaveCandidate)
        assertNull(mapping.saveCandidate)
        assertEquals(NpuStandardRouteS2DbContract.FAILURE_BLANK_USER_MESSAGE, mapping.failureReason)
    }

    @Test
    fun `S1 contract remains disconnected while S2 candidate connects DB only`() {
        val s1Result = successResult()
        val candidate = requireNotNull(
            NpuStandardRouteS2DbMapper.map(
                userPrompt = "こんにちは",
                s1Result = s1Result,
            ).saveCandidate,
        )

        assertFalse(s1Result.selection.sideEffects.db)
        assertFalse(s1Result.selection.sideEffects.tts)
        assertFalse(s1Result.selection.sideEffects.markdown)
        assertFalse(s1Result.selection.sideEffects.streaming)
        assertTrue(candidate.sideEffects.dbConnected)
        assertTrue(candidate.sideEffects.conversationHistorySaved)
        assertFalse(candidate.sideEffects.tts)
        assertFalse(candidate.sideEffects.markdown)
        assertFalse(candidate.sideEffects.streaming)
    }

    private fun assertRawRoleTailRepaired(rawOutput: String) {
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "こんにちは",
            s1Result = successResult(
                rawOutput = rawOutput,
                sanitizedOutput = "どうしましたか。",
            ),
        )

        assertTrue(mapping.hasSaveCandidate)
        assertEquals("どうしましたか。", mapping.saveCandidate?.assistantMessage?.text)
        assertNull(mapping.failureReason)
    }

    private fun assertRawRoleContaminationBlocked(rawOutput: String) {
        val mapping = NpuStandardRouteS2DbMapper.map(
            userPrompt = "こんにちは",
            s1Result = successResult(
                rawOutput = rawOutput,
                sanitizedOutput = "どうしましたか。",
            ),
        )

        assertFalse(mapping.hasSaveCandidate)
        assertNull(mapping.saveCandidate)
        assertEquals(NpuStandardRouteS2DbContract.FAILURE_RAW_ROLE_CONTAMINATION, mapping.failureReason)
    }

    private fun successResult(
        rawOutput: String = "こんにちは。",
        sanitizedOutput: String = "こんにちは。",
        displayText: String = sanitizedOutput,
        qualityClassification: String = NpuStandardRouteS1Contract.QUALITY_NATURAL_JAPANESE,
        fallbackUsed: Boolean = false,
    ): NpuStandardRouteS1Result = NpuStandardRouteS1Result(
        selection = NpuStandardRouteS1Selection(enabled = true),
        status = NpuStandardRouteS1Contract.STATUS_SUCCESS,
        reason = NpuStandardRouteS1Contract.REASON_SUCCESS,
        rawOutput = rawOutput,
        sanitizedOutput = sanitizedOutput,
        qualityClassification = qualityClassification,
        runDecodeReached = true,
        npuBackendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
        fallbackUsed = fallbackUsed,
        timeout = false,
        freshCrash = false,
        displayText = displayText,
    )
}
