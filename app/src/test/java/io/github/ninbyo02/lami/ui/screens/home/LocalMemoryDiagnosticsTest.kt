package io.github.ninbyo02.lami.ui.screens.home

import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMemoryDiagnosticsTest {
    @Test
    fun `memory snapshot MB conversion uses whole MiB units`() {
        assertEquals(2L, bytesToWholeMbForMemoryDiagnostics(2L * 1024L * 1024L))
        assertEquals(3L, kbToWholeMbForMemoryDiagnostics(3 * 1024))
        assertEquals(null, bytesToWholeMbForMemoryDiagnostics(-1L))
        assertEquals(null, kbToWholeMbForMemoryDiagnostics(-1))
    }

    @Test
    fun `formatted diagnostics include low memory threshold available memory and stage`() {
        val text = formatMemoryDiagnosticsForDev(
            listOf(
                snapshot(
                    stage = MEMORY_STAGE_SAFETY_GUARD_TRIGGERED,
                    availableSystemMemoryMb = 512,
                    systemMemoryThresholdMb = 256,
                    lowMemory = false,
                ),
            ),
        )

        assertTrue(text.contains("App/System memory diagnostics"))
        assertTrue(text.contains("[DEV診断: App/System memory diagnostics]"))
        assertTrue(text.contains("memory_stage=safety_guard_triggered"))
        assertTrue(text.contains("system_available_memory_mb=512"))
        assertTrue(text.contains("system_memory_threshold_mb=256"))
        assertTrue(text.contains("low_memory=false"))
        assertTrue(text.contains("adb_compare_hint=compare_with_adb_shell_dumpsys_meminfo_package"))
    }

    @Test
    fun `formatted diagnostics include adb comparable memory fields with unavailable values`() {
        val text = formatMemoryDiagnosticsForDev(
            listOf(
                snapshot(
                    stage = MEMORY_STAGE_BEFORE_GENERATE,
                    totalPssMb = 321,
                    totalRssMb = null,
                    totalSwapPssMb = null,
                    nativeHeapAllocatedMb = 24,
                    nativeHeapSizeMb = 64,
                    nativeHeapPssMb = null,
                    dalvikHeapPssMb = 17,
                ),
            ),
        )

        assertTrue(text.contains("total_pss_mb=321"))
        assertTrue(text.contains("total_rss_mb=unavailable"))
        assertTrue(text.contains("total_swap_pss_mb=unavailable"))
        assertTrue(text.contains("native_heap_alloc_mb=24"))
        assertTrue(text.contains("native_heap_size_mb=64"))
        assertTrue(text.contains("native_heap_pss_mb=unavailable"))
        assertTrue(text.contains("dalvik_heap_pss_mb=17"))
    }

    @Test
    fun `safety guard snapshot can be retained in local trace diagnostics`() {
        val trace = LocalInferenceTrace(
            memorySnapshots = listOf(
                snapshot(
                    stage = MEMORY_STAGE_BEFORE_GENERATE,
                    nativeHeapAllocatedMb = 10,
                    nativeHeapPssMb = 4,
                    dalvikHeapPssMb = 20,
                    totalPssMb = 100,
                    totalRssMb = null,
                    totalSwapPssMb = null,
                    availableSystemMemoryMb = 900,
                ),
                snapshot(
                    stage = MEMORY_STAGE_SAFETY_GUARD_TRIGGERED,
                    nativeHeapAllocatedMb = 14,
                    nativeHeapPssMb = 7,
                    dalvikHeapPssMb = 24,
                    totalPssMb = 112,
                    totalRssMb = null,
                    totalSwapPssMb = null,
                    availableSystemMemoryMb = 850,
                ),
            ),
        )

        val sections = buildInferenceDetailSections(
            stats = InferenceStats(modelName = "local-dev"),
            displayMode = InferenceStatsDisplayMode.DEVELOPER,
            localTraceForDev = trace,
        )
        val memoryText = sections
            .first { it.title == "DEV診断" }
            .items
            .first { it.label == "App/System memory diagnostics" }
            .value

        assertTrue(memoryText.contains("memory_stage=safety_guard_triggered"))
        assertTrue(memoryText.contains("native_heap_alloc_delta_mb=+4"))
        assertTrue(memoryText.contains("native_heap_pss_delta_mb=+3"))
        assertTrue(memoryText.contains("dalvik_heap_pss_delta_mb=+4"))
        assertTrue(memoryText.contains("total_pss_delta_mb=+12"))
        assertTrue(memoryText.contains("total_rss_delta_mb=unavailable"))
        assertTrue(memoryText.contains("total_swap_pss_delta_mb=unavailable"))
        assertTrue(memoryText.contains("system_available_memory_delta_mb=-50"))
    }

    @Test
    fun `memory diagnostics label does not imply npu dedicated memory`() {
        val text = formatMemoryDiagnosticsForDev(
            listOf(snapshot(stage = MEMORY_STAGE_BEFORE_GENERATE)),
        )

        assertFalse(text.contains("NPU memory"))
    }

    @Test
    fun `memory recovery check retains stages and displays current based deltas`() {
        val text = formatMemoryRecoveryCheckForDev(
            MemoryRecoveryCheckState(
                status = MEMORY_RECOVERY_STATUS_COMPLETED,
                startedAtMs = 456L,
                snapshots = listOf(
                    snapshot(
                        stage = MEMORY_STAGE_MEMORY_RECOVERY_CURRENT,
                        totalPssMb = 300,
                        nativeHeapPssMb = 100,
                        nativeHeapAllocatedMb = 24,
                        dalvikHeapPssMb = 50,
                        availableSystemMemoryMb = 1000,
                    ),
                    snapshot(
                        stage = MEMORY_STAGE_MEMORY_RECOVERY_DELAYED_1S,
                        totalPssMb = 292,
                        nativeHeapPssMb = 86,
                        nativeHeapAllocatedMb = 23,
                        dalvikHeapPssMb = 49,
                        availableSystemMemoryMb = 1012,
                    ),
                    snapshot(
                        stage = MEMORY_STAGE_MEMORY_RECOVERY_DELAYED_3S,
                        totalPssMb = 280,
                        nativeHeapPssMb = 70,
                        nativeHeapAllocatedMb = 22,
                        dalvikHeapPssMb = 48,
                        availableSystemMemoryMb = 1024,
                    ),
                    snapshot(
                        stage = MEMORY_STAGE_MEMORY_RECOVERY_DELAYED_5S,
                        totalPssMb = 276,
                        nativeHeapPssMb = 66,
                        nativeHeapAllocatedMb = 22,
                        dalvikHeapPssMb = 48,
                        availableSystemMemoryMb = 1030,
                    ),
                ),
            ),
        )

        assertTrue(text.contains("[DEV診断: App/System memory recovery check]"))
        assertTrue(text.contains("recovery_check_status=completed"))
        assertTrue(text.contains("recovery_check_started_at_ms=456"))
        assertTrue(text.contains("memory_stage=memory_recovery_current"))
        assertTrue(text.contains("memory_stage=memory_recovery_delayed_1s"))
        assertTrue(text.contains("memory_stage=memory_recovery_delayed_3s"))
        assertTrue(text.contains("memory_stage=memory_recovery_delayed_5s"))
        assertTrue(text.contains("total_pss_mb=300"))
        assertTrue(text.contains("native_heap_pss_mb=100"))
        assertTrue(text.contains("native_heap_alloc_mb=24"))
        assertTrue(text.contains("dalvik_heap_pss_mb=50"))
        assertTrue(text.contains("system_available_memory_mb=1000"))
        assertTrue(text.contains("delta_from_stage=memory_recovery_current"))
        assertTrue(text.contains("delta_to_stage=memory_recovery_delayed_1s"))
        assertTrue(text.contains("total_pss_delta_mb=-8"))
        assertTrue(text.contains("native_heap_pss_delta_mb=-14"))
        assertTrue(text.contains("native_heap_alloc_delta_mb=-1"))
        assertTrue(text.contains("dalvik_heap_pss_delta_mb=-1"))
        assertTrue(text.contains("system_available_memory_delta_mb=+12"))
        assertFalse(text.contains("NPU memory"))
    }

    @Test
    fun `memory recovery check displays valid statuses`() {
        listOf(
            MEMORY_RECOVERY_STATUS_IDLE,
            MEMORY_RECOVERY_STATUS_RUNNING,
            MEMORY_RECOVERY_STATUS_COMPLETED,
            MEMORY_RECOVERY_STATUS_CANCELLED,
        ).forEach { status ->
            val text = formatMemoryRecoveryCheckForDev(
                MemoryRecoveryCheckState(status = status, startedAtMs = 1L),
            )

            assertTrue(text.contains("recovery_check_status=$status"))
        }
    }

    @Test
    fun `memory recovery check tolerates unavailable values`() {
        val text = formatMemoryRecoveryCheckForDev(
            MemoryRecoveryCheckState(
                status = MEMORY_RECOVERY_STATUS_RUNNING,
                startedAtMs = 789L,
                snapshots = listOf(
                    snapshot(
                        stage = MEMORY_STAGE_MEMORY_RECOVERY_CURRENT,
                        totalPssMb = null,
                        nativeHeapPssMb = null,
                        nativeHeapAllocatedMb = null,
                        dalvikHeapPssMb = null,
                        availableSystemMemoryMb = null,
                        lowMemory = null,
                    ),
                ),
            ),
        )

        assertTrue(text.contains("total_pss_mb=unavailable"))
        assertTrue(text.contains("native_heap_pss_mb=unavailable"))
        assertTrue(text.contains("native_heap_alloc_mb=unavailable"))
        assertTrue(text.contains("dalvik_heap_pss_mb=unavailable"))
        assertTrue(text.contains("system_available_memory_mb=unavailable"))
        assertTrue(text.contains("low_memory=unavailable"))
    }

    @Test
    fun `memory recovery check button visibility and start policy are safe`() {
        assertTrue(shouldShowMemoryRecoveryCheckButton("DEVELOPER"))
        assertFalse(shouldShowMemoryRecoveryCheckButton("SIMPLE"))
        assertFalse(isMemoryRecoveryCheckButtonEnabled(isInferenceRunning = true, isRecoveryCheckRunning = false))
        assertFalse(isMemoryRecoveryCheckButtonEnabled(isInferenceRunning = false, isRecoveryCheckRunning = true))
        assertTrue(isMemoryRecoveryCheckButtonEnabled(isInferenceRunning = false, isRecoveryCheckRunning = false))
        assertEquals(
            MemoryRecoveryCheckStartPolicy.CANCEL_PREVIOUS_AND_START,
            resolveMemoryRecoveryCheckStartPolicy(existingJobActive = true),
        )
        assertEquals(
            MemoryRecoveryCheckStartPolicy.START_NEW,
            resolveMemoryRecoveryCheckStartPolicy(existingJobActive = false),
        )
    }

    @Test
    fun `memory recovery check delay schedule is isolated from normal generation`() {
        assertEquals(
            listOf(0L, 1_000L, 3_000L, 5_000L),
            memoryRecoveryCheckDelayScheduleMs(),
        )
        assertEquals(
            listOf(
                MEMORY_STAGE_MEMORY_RECOVERY_CURRENT,
                MEMORY_STAGE_MEMORY_RECOVERY_DELAYED_1S,
                MEMORY_STAGE_MEMORY_RECOVERY_DELAYED_3S,
                MEMORY_STAGE_MEMORY_RECOVERY_DELAYED_5S,
            ),
            MEMORY_RECOVERY_CHECK_STAGES,
        )
    }

    private fun snapshot(
        stage: String,
        javaHeapUsedMb: Long? = 1,
        javaHeapMaxMb: Long? = 2,
        totalRssMb: Long? = 10,
        totalSwapPssMb: Long? = 11,
        nativeHeapPssMb: Long? = 12,
        nativeHeapRssMb: Long? = null,
        nativeHeapAllocatedMb: Long? = 3,
        nativeHeapSizeMb: Long? = 4,
        dalvikHeapPssMb: Long? = 13,
        dalvikHeapRssMb: Long? = null,
        dalvikHeapAllocatedMb: Long? = 14,
        dalvikHeapSizeMb: Long? = 15,
        totalPssMb: Long? = 5,
        privateDirtyMb: Long? = 6,
        privateCleanMb: Long? = 7,
        graphicsPssMb: Long? = 16,
        stackPssMb: Long? = 17,
        codePssMb: Long? = 18,
        systemPssMb: Long? = 19,
        unknownPssMb: Long? = 20,
        availableSystemMemoryMb: Long? = 8,
        systemMemoryThresholdMb: Long? = 9,
        lowMemory: Boolean? = false,
    ): MemorySnapshot = MemorySnapshot(
        timestampMs = 123L,
        stage = stage,
        javaHeapUsedMb = javaHeapUsedMb,
        javaHeapMaxMb = javaHeapMaxMb,
        totalRssMb = totalRssMb,
        totalSwapPssMb = totalSwapPssMb,
        nativeHeapPssMb = nativeHeapPssMb,
        nativeHeapRssMb = nativeHeapRssMb,
        nativeHeapAllocatedMb = nativeHeapAllocatedMb,
        nativeHeapSizeMb = nativeHeapSizeMb,
        dalvikHeapPssMb = dalvikHeapPssMb,
        dalvikHeapRssMb = dalvikHeapRssMb,
        dalvikHeapAllocatedMb = dalvikHeapAllocatedMb,
        dalvikHeapSizeMb = dalvikHeapSizeMb,
        totalPssMb = totalPssMb,
        privateDirtyMb = privateDirtyMb,
        privateCleanMb = privateCleanMb,
        graphicsPssMb = graphicsPssMb,
        stackPssMb = stackPssMb,
        codePssMb = codePssMb,
        systemPssMb = systemPssMb,
        unknownPssMb = unknownPssMb,
        availableSystemMemoryMb = availableSystemMemoryMb,
        systemMemoryThresholdMb = systemMemoryThresholdMb,
        lowMemory = lowMemory,
        threadName = "test-thread",
    )
}
