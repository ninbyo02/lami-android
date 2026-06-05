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
