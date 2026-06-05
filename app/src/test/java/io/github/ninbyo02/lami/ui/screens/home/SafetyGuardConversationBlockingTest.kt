package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.npu.Qairt244NpuOutputSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGuardConversationBlockingTest {
    @Test
    fun `safety guard triggered creates blocked conversation state`() {
        val block = safetyGuardConversationBlockOrNull(
            chatId = 42,
            reasonCode = "invalid_prompt_too_long",
            failureStage = "prompt_validation",
            stopReason = "guard",
        )

        assertNotNull(block)
        assertEquals(42, block?.chatId)
        assertTrue(block?.blocked == true)
        assertEquals(MEMORY_STAGE_SAFETY_GUARD_TRIGGERED, block?.lastSafetyStage)
    }

    @Test
    fun `blocked conversation does not invoke generate`() {
        var called = false
        val block = SafetyGuardConversationBlock(
            chatId = 7,
            reasonCode = "invalid_prompt_too_long",
            failureStage = "prompt_validation",
            stopReason = "guard",
        )

        val decision = runUnlessConversationBlockedBySafetyGuard(
            chatId = 7,
            blockedConversations = mapOf(7 to block),
        ) {
            called = true
            "generated"
        }

        assertFalse(called)
        assertFalse(decision.generated)
        assertEquals(null, decision.value)
        assertEquals(SAFETY_GUARD_BLOCKED_USER_MESSAGE, decision.blockedMessage)
    }

    @Test
    fun `blocked user message asks user to continue in a new conversation`() {
        assertTrue(SAFETY_GUARD_BLOCKED_USER_MESSAGE.contains("安全停止後"))
        assertTrue(SAFETY_GUARD_BLOCKED_USER_MESSAGE.contains("新しい会話"))
    }

    @Test
    fun `safety guard triggered is classified separately from normal stop`() {
        assertTrue(
            isSafetyGuardTriggered(
                reasonCode = "gate_blocked_prompt_validation",
                failureStage = "prompt_validation",
                stopReason = "guard",
            ),
        )
        assertFalse(
            isSafetyGuardTriggered(
                reasonCode = "success",
                failureStage = "generation_finished",
                stopReason = "stop",
            ),
        )
        assertFalse(
            isSafetyGuardTriggered(
                reasonCode = "user_cancel",
                failureStage = "after_cancel",
                stopReason = "cancel",
            ),
        )
    }

    @Test
    fun `guard cleanup stages include cancel runner dispose and engine recycle snapshots`() {
        val stages = safetyGuardCleanupMemoryStages()

        assertTrue(stages.contains(MEMORY_STAGE_AFTER_CANCEL))
        assertTrue(stages.contains(MEMORY_STAGE_AFTER_RUNNER_DISPOSE))
        assertTrue(stages.contains(MEMORY_STAGE_AFTER_ENGINE_RECYCLE))
    }

    @Test
    fun `old active local job cancels instead of starting a second generation`() {
        assertEquals(
            ExistingLocalGenerationJobPolicy.CANCEL_STALE_AND_WAIT,
            resolveExistingLocalGenerationJobPolicy(
                isLocalInferenceRunning = false,
                existingJobActive = true,
            ),
        )
        assertEquals(
            ExistingLocalGenerationJobPolicy.ALREADY_RUNNING,
            resolveExistingLocalGenerationJobPolicy(
                isLocalInferenceRunning = true,
                existingJobActive = false,
            ),
        )
        assertEquals(
            ExistingLocalGenerationJobPolicy.START_NEW,
            resolveExistingLocalGenerationJobPolicy(
                isLocalInferenceRunning = false,
                existingJobActive = false,
            ),
        )
    }

    @Test
    fun `dev diagnostics include blocked guard state and memory snapshots`() {
        val block = SafetyGuardConversationBlock(
            chatId = 11,
            reasonCode = "invalid_prompt_too_long",
            failureStage = "prompt_validation",
            stopReason = "guard",
        )
        val text = formatMemoryDiagnosticsForDev(
            snapshots = listOf(
                snapshot(
                    stage = MEMORY_STAGE_BEFORE_GENERATE,
                    nativeHeapAllocatedMb = 100,
                    totalPssMb = 500,
                    availableSystemMemoryMb = 1200,
                ),
                snapshot(
                    stage = MEMORY_STAGE_SAFETY_GUARD_TRIGGERED,
                    nativeHeapAllocatedMb = 116,
                    totalPssMb = 548,
                    availableSystemMemoryMb = 1130,
                ),
                snapshot(stage = MEMORY_STAGE_AFTER_CANCEL),
                snapshot(stage = MEMORY_STAGE_AFTER_RUNNER_DISPOSE),
            ),
            guardBlock = block,
        )

        assertTrue(text.contains("guard state: blocked"))
        assertTrue(text.contains("last safety stage: safety_guard_triggered"))
        assertTrue(text.contains("memory_stage=safety_guard_triggered"))
        assertTrue(text.contains("memory_stage=after_cancel"))
        assertTrue(text.contains("memory_stage=after_runner_dispose"))
        assertTrue(text.contains("native_heap_alloc_delta_mb=+16"))
        assertTrue(text.contains("total_pss_delta_mb=+48"))
        assertTrue(text.contains("system_available_memory_delta_mb=-70"))
        assertTrue(text.contains("adb_compare_hint=compare_with_adb_shell_dumpsys_meminfo_package"))
        assertFalse(text.contains("NPU memory"))
    }

    @Test
    fun `guard blocking changes do not modify npu prompt sanitizer token and fallback contracts`() {
        val sanitized = Qairt244NpuOutputSanitizer.sanitize(
            rawOutput = "こんにちは！<end_of_turn>",
            prompt = "こんにちは",
        )

        assertEquals(32, NpuStandardRouteS1Contract.MAX_OUTPUT_TOKENS)
        assertEquals("raw_dialog_tail_variant_c", NpuStandardRouteS1Contract.PROMPT_TAIL_VARIANT)
        assertEquals("safe_greeting_fallback", NpuStandardRouteS1Contract.FALLBACK_SAFE_GREETING)
        assertEquals(128, NpuDiagnosticPromptValidator.HIDDEN_TEMPLATE_MAX_LENGTH)
        assertEquals("short_prompt_guard", NpuDiagnosticPromptValidator.DEFAULT_INPUT_LIMIT_MODE)
        assertEquals("こんにちは！", sanitized.sanitizedOutput)
        assertTrue(sanitized.sanitizerApplied)
    }

    private fun snapshot(
        stage: String,
        javaHeapUsedMb: Long? = 1,
        javaHeapMaxMb: Long? = 2,
        nativeHeapAllocatedMb: Long? = 3,
        nativeHeapSizeMb: Long? = 4,
        totalPssMb: Long? = 5,
        privateDirtyMb: Long? = 6,
        privateCleanMb: Long? = 7,
        availableSystemMemoryMb: Long? = 8,
        systemMemoryThresholdMb: Long? = 9,
        lowMemory: Boolean? = false,
    ): MemorySnapshot = MemorySnapshot(
        timestampMs = 123L,
        stage = stage,
        javaHeapUsedMb = javaHeapUsedMb,
        javaHeapMaxMb = javaHeapMaxMb,
        nativeHeapAllocatedMb = nativeHeapAllocatedMb,
        nativeHeapSizeMb = nativeHeapSizeMb,
        totalPssMb = totalPssMb,
        privateDirtyMb = privateDirtyMb,
        privateCleanMb = privateCleanMb,
        availableSystemMemoryMb = availableSystemMemoryMb,
        systemMemoryThresholdMb = systemMemoryThresholdMb,
        lowMemory = lowMemory,
        threadName = "test-thread",
    )
}
