package io.github.ninbyo02.lami.npu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevOnlyNpuHiddenExperimentalModeGateTest {
    @Test
    fun `hidden per-run isolated 512 accepts only complete force-stop evidence`() {
        val result = DevOnlyNpuHiddenExperimentalModeGate.evaluate(validPerRun512())

        assertTrue(result.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.OK, result.reason)
    }

    @Test
    fun `sequential 512 is rollback`() {
        assertRejected(
            validPerRun512(executionIsolation = DevOnlyNpuExecutionIsolation.SEQUENTIAL),
            DevOnlyNpuHiddenExperimentalModeGateReason.SEQUENTIAL_512_ROLLBACK,
        )
    }

    @Test
    fun `activity restart only 512 is rollback`() {
        assertRejected(
            validPerRun512(executionIsolation = DevOnlyNpuExecutionIsolation.ACTIVITY_RESTART_ONLY),
            DevOnlyNpuHiddenExperimentalModeGateReason.ACTIVITY_RESTART_ONLY_512_ROLLBACK,
        )
    }

    @Test
    fun `512 requires force-stop before and after each prompt`() {
        assertRejected(
            validPerRun512(forceStopAfterEachPrompt = false),
            DevOnlyNpuHiddenExperimentalModeGateReason.FORCE_STOP_BEFORE_AFTER_REQUIRED,
        )
    }

    @Test
    fun `512 rejects missing native and cleanup evidence`() {
        assertRejected(
            validPerRun512(runDecodeReached = false),
            DevOnlyNpuHiddenExperimentalModeGateReason.RUN_DECODE_MISSING,
        )
        assertRejected(
            validPerRun512(setMaxOutputTokens512Evidence = false),
            DevOnlyNpuHiddenExperimentalModeGateReason.SET_MAX_OUTPUT_TOKENS_512_MISSING,
        )
        assertRejected(
            validPerRun512(engineCloseUniquePtrCleanup = false),
            DevOnlyNpuHiddenExperimentalModeGateReason.ENGINE_CLOSE_MISSING,
        )
        assertRejected(
            validPerRun512(cleanupEvidence = false),
            DevOnlyNpuHiddenExperimentalModeGateReason.CLEANUP_EVIDENCE_MISSING,
        )
    }

    @Test
    fun `512 rejects timeout crash fallback and missing qnn evidence`() {
        assertRejected(validPerRun512(timeout = true), DevOnlyNpuHiddenExperimentalModeGateReason.TIMEOUT)
        assertRejected(validPerRun512(freshCrash = true), DevOnlyNpuHiddenExperimentalModeGateReason.FRESH_CRASH)
        assertRejected(validPerRun512(fallbackUsed = true), DevOnlyNpuHiddenExperimentalModeGateReason.FALLBACK_USED)
        assertRejected(
            validPerRun512(qnnHtpFastRpcEvidence = false),
            DevOnlyNpuHiddenExperimentalModeGateReason.QNN_EVIDENCE_MISSING,
        )
    }

    @Test
    fun `512 rejects retained process memory and code display failures`() {
        assertRejected(
            validPerRun512(processPresentAfter10s = true),
            DevOnlyNpuHiddenExperimentalModeGateReason.AFTER_10S_PROCESS_RETAINED,
        )
        assertRejected(
            validPerRun512(memoryHighRetained = true),
            DevOnlyNpuHiddenExperimentalModeGateReason.MEMORY_HIGH_RETAINED,
        )
        assertRejected(
            validPerRun512(codeAwareSanitizer = false),
            DevOnlyNpuHiddenExperimentalModeGateReason.CODE_AWARE_SANITIZER_REQUIRED,
        )
        assertRejected(
            validPerRun512(codeIndentationPreserved = false),
            DevOnlyNpuHiddenExperimentalModeGateReason.CODE_INDENTATION_BROKEN,
        )
        assertRejected(
            validPerRun512(codeFenceCompleted = false),
            DevOnlyNpuHiddenExperimentalModeGateReason.CODE_FENCE_INCOMPLETE,
        )
    }

    @Test
    fun `512 rejects normal ChatScreen assistant list and side effects`() {
        assertRejected(
            validPerRun512(normalChatScreenPromotion = true),
            DevOnlyNpuHiddenExperimentalModeGateReason.NORMAL_CHAT_SCREEN_BLOCKED,
        )
        assertRejected(
            validPerRun512(assistantMessageListInserted = true),
            DevOnlyNpuHiddenExperimentalModeGateReason.ASSISTANT_MESSAGE_LIST_INSERTED,
        )
        assertRejected(
            validPerRun512(selectedPathSaved = true),
            DevOnlyNpuHiddenExperimentalModeGateReason.SELECTED_PATH_SAVED,
        )
        assertRejected(validPerRun512(db = true), DevOnlyNpuHiddenExperimentalModeGateReason.SIDE_EFFECT_INGRESS)
        assertRejected(validPerRun512(tts = true), DevOnlyNpuHiddenExperimentalModeGateReason.SIDE_EFFECT_INGRESS)
        assertRejected(validPerRun512(markdown = true), DevOnlyNpuHiddenExperimentalModeGateReason.SIDE_EFFECT_INGRESS)
        assertRejected(validPerRun512(streaming = true), DevOnlyNpuHiddenExperimentalModeGateReason.SIDE_EFFECT_INGRESS)
    }

    @Test
    fun `1024 and above remain blocked`() {
        assertRejected(
            validPerRun512(maxOutputTokens = 1024),
            DevOnlyNpuHiddenExperimentalModeGateReason.MAX_OUTPUT_TOKENS_ABOVE_512_BLOCKED,
        )
    }

    @Test
    fun `hidden 256 candidate remains distinct from 512 gate`() {
        val result = DevOnlyNpuHiddenExperimentalModeGate.evaluate(
            DevOnlyNpuHiddenExperimentalModeGateInput(
                mode = DevOnlyNpuHiddenExperimentalMode.HIDDEN_EXPERIMENTAL_256,
                executionIsolation = DevOnlyNpuExecutionIsolation.SEQUENTIAL,
                maxOutputTokens = DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_EXPERIMENTAL_BASELINE_CANDIDATE_TOKENS,
            ),
        )

        assertTrue(result.allowed)
        assertEquals(DevOnlyNpuHiddenExperimentalModeGateReason.OK, result.reason)
    }

    @Test
    fun `H1 remains pinned to 128 and is not the 512 mode`() {
        assertEquals(128, DevOnlyNpuRouteAdapter.QAIRT244_H1_PINNED_MAX_OUTPUT_TOKENS)
        assertEquals(
            DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS,
            DevOnlyNpuRouteAdapter.QAIRT244_H1_PINNED_MAX_OUTPUT_TOKENS,
        )
        assertEquals(512, DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS)
    }

    private fun assertRejected(
        input: DevOnlyNpuHiddenExperimentalModeGateInput,
        reason: DevOnlyNpuHiddenExperimentalModeGateReason,
    ) {
        val result = DevOnlyNpuHiddenExperimentalModeGate.evaluate(input)

        assertFalse(result.allowed)
        assertEquals(reason, result.reason)
    }

    private fun validPerRun512(
        executionIsolation: DevOnlyNpuExecutionIsolation = DevOnlyNpuExecutionIsolation.PER_RUN_FORCE_STOP,
        maxOutputTokens: Int = DevOnlyNpuRouteAdapter.QAIRT244_HIDDEN_PER_RUN_ISOLATED_MAX_OUTPUT_TOKENS,
        hiddenOnly: Boolean = true,
        normalChatScreenPromotion: Boolean = false,
        forceStopBeforeEachPrompt: Boolean = true,
        forceStopAfterEachPrompt: Boolean = true,
        runDecodeReached: Boolean = true,
        setMaxOutputTokens512Evidence: Boolean = true,
        timeout: Boolean = false,
        freshCrash: Boolean = false,
        fallbackUsed: Boolean = false,
        qnnHtpFastRpcEvidence: Boolean = true,
        engineCloseUniquePtrCleanup: Boolean = true,
        cleanupEvidence: Boolean = true,
        processPresentAfter10s: Boolean = false,
        memoryHighRetained: Boolean = false,
        codeAwareSanitizer: Boolean = true,
        codeIndentationPreserved: Boolean = true,
        codeFenceCompleted: Boolean = true,
        selectedPathSaved: Boolean = false,
        assistantMessageListInserted: Boolean = false,
        db: Boolean = false,
        tts: Boolean = false,
        markdown: Boolean = false,
        streaming: Boolean = false,
    ): DevOnlyNpuHiddenExperimentalModeGateInput = DevOnlyNpuHiddenExperimentalModeGateInput(
        mode = DevOnlyNpuHiddenExperimentalMode.HIDDEN_PER_RUN_ISOLATED_512,
        executionIsolation = executionIsolation,
        maxOutputTokens = maxOutputTokens,
        hiddenOnly = hiddenOnly,
        normalChatScreenPromotion = normalChatScreenPromotion,
        forceStopBeforeEachPrompt = forceStopBeforeEachPrompt,
        forceStopAfterEachPrompt = forceStopAfterEachPrompt,
        runDecodeReached = runDecodeReached,
        setMaxOutputTokens512Evidence = setMaxOutputTokens512Evidence,
        timeout = timeout,
        freshCrash = freshCrash,
        fallbackUsed = fallbackUsed,
        qnnHtpFastRpcEvidence = qnnHtpFastRpcEvidence,
        engineCloseUniquePtrCleanup = engineCloseUniquePtrCleanup,
        cleanupEvidence = cleanupEvidence,
        processPresentAfter10s = processPresentAfter10s,
        memoryHighRetained = memoryHighRetained,
        codeAwareSanitizer = codeAwareSanitizer,
        codeIndentationPreserved = codeIndentationPreserved,
        codeFenceCompleted = codeFenceCompleted,
        selectedPathSaved = selectedPathSaved,
        assistantMessageListInserted = assistantMessageListInserted,
        db = db,
        tts = tts,
        markdown = markdown,
        streaming = streaming,
    )
}
