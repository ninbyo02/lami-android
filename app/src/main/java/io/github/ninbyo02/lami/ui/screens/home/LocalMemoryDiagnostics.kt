package io.github.ninbyo02.lami.ui.screens.home

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import java.util.Locale

internal data class MemorySnapshot(
    val timestampMs: Long,
    val stage: String,
    val javaHeapUsedMb: Long?,
    val javaHeapMaxMb: Long?,
    val totalRssMb: Long? = null,
    val totalSwapPssMb: Long? = null,
    val nativeHeapPssMb: Long? = null,
    val nativeHeapRssMb: Long? = null,
    val nativeHeapAllocatedMb: Long?,
    val nativeHeapSizeMb: Long?,
    val dalvikHeapPssMb: Long? = null,
    val dalvikHeapRssMb: Long? = null,
    val dalvikHeapAllocatedMb: Long? = null,
    val dalvikHeapSizeMb: Long? = null,
    val totalPssMb: Long?,
    val privateDirtyMb: Long?,
    val privateCleanMb: Long?,
    val graphicsPssMb: Long? = null,
    val stackPssMb: Long? = null,
    val codePssMb: Long? = null,
    val systemPssMb: Long? = null,
    val unknownPssMb: Long? = null,
    val availableSystemMemoryMb: Long?,
    val systemMemoryThresholdMb: Long?,
    val lowMemory: Boolean?,
    val threadName: String,
    val gcCount: Long? = null,
)

internal data class MemorySnapshotDelta(
    val fromStage: String,
    val toStage: String,
    val totalPssDeltaMb: Long?,
    val totalRssDeltaMb: Long?,
    val totalSwapPssDeltaMb: Long?,
    val nativeHeapAllocatedDeltaMb: Long?,
    val nativeHeapPssDeltaMb: Long?,
    val dalvikHeapPssDeltaMb: Long?,
    val availableSystemMemoryDeltaMb: Long?,
)

internal fun bytesToWholeMbForMemoryDiagnostics(bytes: Long?): Long? =
    bytes?.takeIf { it >= 0L }?.div(BYTES_PER_MB)

internal fun kbToWholeMbForMemoryDiagnostics(kb: Int?): Long? =
    kb?.takeIf { it >= 0 }?.toLong()?.div(KB_PER_MB)

internal fun captureLocalMemorySnapshot(
    context: Context?,
    stage: String,
): MemorySnapshot {
    val runtime = Runtime.getRuntime()
    val javaHeapUsedBytes = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
    val javaHeapSizeBytes = runtime.totalMemory().coerceAtLeast(0L)
    val processMemoryInfo = Debug.MemoryInfo()
    runCatching {
        Debug.getMemoryInfo(processMemoryInfo)
    }

    val activityManager = context
        ?.applicationContext
        ?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val systemMemoryInfo = ActivityManager.MemoryInfo()
    val hasSystemMemoryInfo = runCatching {
        activityManager?.getMemoryInfo(systemMemoryInfo)
        activityManager != null
    }.getOrDefault(false)

    return MemorySnapshot(
        timestampMs = System.currentTimeMillis(),
        stage = stage.ifBlank { "unknown" },
        javaHeapUsedMb = bytesToWholeMbForMemoryDiagnostics(javaHeapUsedBytes),
        javaHeapMaxMb = bytesToWholeMbForMemoryDiagnostics(runtime.maxMemory()),
        totalRssMb = readMemoryStatMbOrNull(processMemoryInfo, MEMORY_STAT_TOTAL_RSS),
        totalSwapPssMb = readMemoryStatMbOrNull(processMemoryInfo, MEMORY_STAT_TOTAL_SWAP_PSS),
        nativeHeapPssMb = kbToWholeMbForMemoryDiagnostics(processMemoryInfo.nativePss),
        nativeHeapRssMb = null,
        nativeHeapAllocatedMb = bytesToWholeMbForMemoryDiagnostics(Debug.getNativeHeapAllocatedSize()),
        nativeHeapSizeMb = bytesToWholeMbForMemoryDiagnostics(Debug.getNativeHeapSize()),
        dalvikHeapPssMb = kbToWholeMbForMemoryDiagnostics(processMemoryInfo.dalvikPss),
        dalvikHeapRssMb = null,
        dalvikHeapAllocatedMb = bytesToWholeMbForMemoryDiagnostics(javaHeapUsedBytes),
        dalvikHeapSizeMb = bytesToWholeMbForMemoryDiagnostics(javaHeapSizeBytes),
        totalPssMb = kbToWholeMbForMemoryDiagnostics(processMemoryInfo.totalPss),
        privateDirtyMb = kbToWholeMbForMemoryDiagnostics(processMemoryInfo.totalPrivateDirty),
        privateCleanMb = kbToWholeMbForMemoryDiagnostics(processMemoryInfo.totalPrivateClean),
        graphicsPssMb = readMemoryStatMbOrNull(processMemoryInfo, MEMORY_STAT_GRAPHICS),
        stackPssMb = readMemoryStatMbOrNull(processMemoryInfo, MEMORY_STAT_STACK),
        codePssMb = readMemoryStatMbOrNull(processMemoryInfo, MEMORY_STAT_CODE),
        systemPssMb = readMemoryStatMbOrNull(processMemoryInfo, MEMORY_STAT_SYSTEM),
        unknownPssMb = kbToWholeMbForMemoryDiagnostics(processMemoryInfo.otherPss),
        availableSystemMemoryMb = if (hasSystemMemoryInfo) {
            bytesToWholeMbForMemoryDiagnostics(systemMemoryInfo.availMem)
        } else {
            null
        },
        systemMemoryThresholdMb = if (hasSystemMemoryInfo) {
            bytesToWholeMbForMemoryDiagnostics(systemMemoryInfo.threshold)
        } else {
            null
        },
        lowMemory = if (hasSystemMemoryInfo) systemMemoryInfo.lowMemory else null,
        threadName = Thread.currentThread().name,
        gcCount = readGcCountOrNull(),
    )
}

internal fun formatMemoryDiagnosticsForDev(
    snapshots: List<MemorySnapshot>,
    guardBlock: SafetyGuardConversationBlock? = null,
): String {
    val distinctSnapshots = snapshots
        .filter { it.stage.isNotBlank() }
        .distinctBy { it.stage }
    if (distinctSnapshots.isEmpty()) return ""

    return buildString {
        appendLine("App/System memory diagnostics:")
        appendLine("[DEV診断: App/System memory diagnostics]")
        formatSafetyGuardBlockedDiagnostics(guardBlock)
            .takeIf { it.isNotBlank() }
            ?.let { appendLine(it) }
        distinctSnapshots.forEach { snapshot ->
            appendLine("memory_stage=${snapshot.stage}")
            appendLine("timestamp_ms=${snapshot.timestampMs}")
            appendLine("java_heap_used_mb=${formatMb(snapshot.javaHeapUsedMb)}")
            appendLine("java_heap_max_mb=${formatMb(snapshot.javaHeapMaxMb)}")
            appendLine("native_heap_alloc_mb=${formatMb(snapshot.nativeHeapAllocatedMb)}")
            appendLine("native_heap_size_mb=${formatMb(snapshot.nativeHeapSizeMb)}")
            appendLine("total_pss_mb=${formatMb(snapshot.totalPssMb)}")
            appendLine("total_rss_mb=${formatMb(snapshot.totalRssMb)}")
            appendLine("total_swap_pss_mb=${formatMb(snapshot.totalSwapPssMb)}")
            appendLine("native_heap_pss_mb=${formatMb(snapshot.nativeHeapPssMb)}")
            appendLine("native_heap_rss_mb=${formatMb(snapshot.nativeHeapRssMb)}")
            appendLine("dalvik_heap_pss_mb=${formatMb(snapshot.dalvikHeapPssMb)}")
            appendLine("dalvik_heap_rss_mb=${formatMb(snapshot.dalvikHeapRssMb)}")
            appendLine("dalvik_heap_alloc_mb=${formatMb(snapshot.dalvikHeapAllocatedMb)}")
            appendLine("dalvik_heap_size_mb=${formatMb(snapshot.dalvikHeapSizeMb)}")
            appendLine("private_dirty_mb=${formatMb(snapshot.privateDirtyMb)}")
            appendLine("private_clean_mb=${formatMb(snapshot.privateCleanMb)}")
            appendLine("graphics_pss_mb=${formatMb(snapshot.graphicsPssMb)}")
            appendLine("stack_pss_mb=${formatMb(snapshot.stackPssMb)}")
            appendLine("code_pss_mb=${formatMb(snapshot.codePssMb)}")
            appendLine("system_pss_mb=${formatMb(snapshot.systemPssMb)}")
            appendLine("unknown_pss_mb=${formatMb(snapshot.unknownPssMb)}")
            appendLine("system_available_memory_mb=${formatMb(snapshot.availableSystemMemoryMb)}")
            appendLine("system_memory_threshold_mb=${formatMb(snapshot.systemMemoryThresholdMb)}")
            appendLine("low_memory=${snapshot.lowMemory?.toString() ?: "unavailable"}")
            appendLine("thread_name=${snapshot.threadName.ifBlank { "unavailable" }}")
            appendLine("source=android_debug_memoryinfo_api")
            appendLine("measurement_note=api_derived_approximate_may_not_match_adb_dumpsys_meminfo")
            appendLine("adb_compare_hint=compare_with_adb_shell_dumpsys_meminfo_package")
            snapshot.gcCount?.let { appendLine("gc_count=$it") }
        }
        val deltas = buildMemorySnapshotDeltas(distinctSnapshots)
        if (deltas.isNotEmpty()) {
            appendLine("[DEV診断: App/System memory delta]")
            deltas.forEach { delta ->
                appendLine("delta_from_stage=${delta.fromStage}")
                appendLine("delta_to_stage=${delta.toStage}")
                appendLine("total_pss_delta_mb=${formatSignedMb(delta.totalPssDeltaMb)}")
                appendLine("total_rss_delta_mb=${formatSignedMb(delta.totalRssDeltaMb)}")
                appendLine("total_swap_pss_delta_mb=${formatSignedMb(delta.totalSwapPssDeltaMb)}")
                appendLine("native_heap_alloc_delta_mb=${formatSignedMb(delta.nativeHeapAllocatedDeltaMb)}")
                appendLine("native_heap_pss_delta_mb=${formatSignedMb(delta.nativeHeapPssDeltaMb)}")
                appendLine("dalvik_heap_pss_delta_mb=${formatSignedMb(delta.dalvikHeapPssDeltaMb)}")
                appendLine("system_available_memory_delta_mb=${formatSignedMb(delta.availableSystemMemoryDeltaMb)}")
            }
        }
    }.trimEnd()
}

internal fun buildMemorySnapshotDeltas(
    snapshots: List<MemorySnapshot>,
): List<MemorySnapshotDelta> {
    val byStage = snapshots.associateBy { it.stage }
    val before = byStage[MEMORY_STAGE_BEFORE_GENERATE] ?: return emptyList()
    return MEMORY_DELTA_TARGET_STAGES.mapNotNull { targetStage ->
        val target = byStage[targetStage] ?: return@mapNotNull null
        MemorySnapshotDelta(
            fromStage = before.stage,
            toStage = target.stage,
            totalPssDeltaMb = target.totalPssMb.deltaFrom(before.totalPssMb),
            totalRssDeltaMb = target.totalRssMb.deltaFrom(before.totalRssMb),
            totalSwapPssDeltaMb = target.totalSwapPssMb.deltaFrom(before.totalSwapPssMb),
            nativeHeapAllocatedDeltaMb = target.nativeHeapAllocatedMb.deltaFrom(before.nativeHeapAllocatedMb),
            nativeHeapPssDeltaMb = target.nativeHeapPssMb.deltaFrom(before.nativeHeapPssMb),
            dalvikHeapPssDeltaMb = target.dalvikHeapPssMb.deltaFrom(before.dalvikHeapPssMb),
            availableSystemMemoryDeltaMb = target.availableSystemMemoryMb.deltaFrom(before.availableSystemMemoryMb),
        )
    }
}

internal fun appendMemoryDiagnosticsForDev(
    text: String,
    snapshots: List<MemorySnapshot>,
    guardBlock: SafetyGuardConversationBlock? = null,
): String = listOf(
    text,
    formatMemoryDiagnosticsForDev(
        snapshots = snapshots,
        guardBlock = guardBlock,
    ),
).filter { it.isNotBlank() }.joinToString("\n\n")

internal fun List<MemorySnapshot>.withMemorySnapshot(snapshot: MemorySnapshot?): List<MemorySnapshot> {
    if (snapshot == null) return this
    return this + snapshot
}

private fun readGcCountOrNull(): Long? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
    return runCatching {
        Debug.getRuntimeStats()["art.gc.gc-count"]?.toLongOrNull()
    }.getOrNull()
}

private fun readMemoryStatMbOrNull(
    memoryInfo: Debug.MemoryInfo,
    key: String,
): Long? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
    return runCatching {
        memoryInfo.getMemoryStat(key)?.toIntOrNull()
    }.getOrNull()?.let(::kbToWholeMbForMemoryDiagnostics)
}

private fun Long?.deltaFrom(base: Long?): Long? =
    if (this != null && base != null) this - base else null

private fun formatMb(value: Long?): String =
    value?.toString() ?: "unavailable"

private fun formatSignedMb(value: Long?): String =
    value?.let { String.format(Locale.US, "%+d", it) } ?: "unavailable"

internal const val MEMORY_STAGE_BEFORE_GENERATE = "before_generate"
internal const val MEMORY_STAGE_AFTER_PROMPT_BUILD = "after_prompt_build"
internal const val MEMORY_STAGE_BEFORE_ENGINE_CALL = "before_engine_call"
internal const val MEMORY_STAGE_SAFETY_GUARD_TRIGGERED = "safety_guard_triggered"
internal const val MEMORY_STAGE_GENERATION_FINISHED = "generation_finished"
internal const val MEMORY_STAGE_GENERATION_FAILED = "generation_failed"
internal const val MEMORY_STAGE_AFTER_CANCEL = "after_cancel"
internal const val MEMORY_STAGE_AFTER_ENGINE_RECYCLE = "after_engine_recycle"
internal const val MEMORY_STAGE_AFTER_RUNNER_DISPOSE = "after_runner_dispose"

private val MEMORY_DELTA_TARGET_STAGES = listOf(
    MEMORY_STAGE_SAFETY_GUARD_TRIGGERED,
    MEMORY_STAGE_AFTER_ENGINE_RECYCLE,
    MEMORY_STAGE_AFTER_RUNNER_DISPOSE,
    MEMORY_STAGE_GENERATION_FINISHED,
    MEMORY_STAGE_GENERATION_FAILED,
    MEMORY_STAGE_AFTER_CANCEL,
)

private const val BYTES_PER_MB = 1024L * 1024L
private const val KB_PER_MB = 1024L
private const val MEMORY_STAT_TOTAL_RSS = "summary.total-rss"
private const val MEMORY_STAT_TOTAL_SWAP_PSS = "summary.total-swap-pss"
private const val MEMORY_STAT_GRAPHICS = "summary.graphics"
private const val MEMORY_STAT_STACK = "summary.stack"
private const val MEMORY_STAT_CODE = "summary.code"
private const val MEMORY_STAT_SYSTEM = "summary.system"
