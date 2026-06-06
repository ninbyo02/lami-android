package io.github.ninbyo02.lami.ui.screens.home

import android.app.Application
import android.os.Build
import android.os.Process
import android.util.Log
import io.github.ninbyo02.lami.BuildConfig
import java.io.File

internal const val NPU_ENGINE_LOGCAT_TAG = "LamiNpuEngine"

internal object NpuEngineLogcatDiagnostics {
    fun i(
        event: String,
        route: String,
        probeName: String = "",
        modelPath: String? = null,
        modelFileSizeBytes: Long? = null,
        backendRequested: String? = null,
        maxOutputTokens: Int? = null,
        memorySnapshot: MemorySnapshot? = null,
        detail: String = "",
    ) {
        log(priority = Log.INFO, event = event, route = route, probeName = probeName, modelPath = modelPath,
            modelFileSizeBytes = modelFileSizeBytes, backendRequested = backendRequested,
            maxOutputTokens = maxOutputTokens, memorySnapshot = memorySnapshot, detail = detail)
    }

    fun w(
        event: String,
        route: String,
        probeName: String = "",
        modelPath: String? = null,
        modelFileSizeBytes: Long? = null,
        backendRequested: String? = null,
        maxOutputTokens: Int? = null,
        memorySnapshot: MemorySnapshot? = null,
        detail: String = "",
    ) {
        log(priority = Log.WARN, event = event, route = route, probeName = probeName, modelPath = modelPath,
            modelFileSizeBytes = modelFileSizeBytes, backendRequested = backendRequested,
            maxOutputTokens = maxOutputTokens, memorySnapshot = memorySnapshot, detail = detail)
    }

    fun e(
        event: String,
        route: String,
        throwable: Throwable,
        probeName: String = "",
        modelPath: String? = null,
        modelFileSizeBytes: Long? = null,
        backendRequested: String? = null,
        maxOutputTokens: Int? = null,
        memorySnapshot: MemorySnapshot? = null,
        detail: String = "",
    ) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.e(
                NPU_ENGINE_LOGCAT_TAG,
                buildMessage(
                    event = event,
                    route = route,
                    probeName = probeName,
                    modelPath = modelPath,
                    modelFileSizeBytes = modelFileSizeBytes,
                    backendRequested = backendRequested,
                    maxOutputTokens = maxOutputTokens,
                    memorySnapshot = memorySnapshot,
                    detail = detail,
                ),
                throwable,
            )
        }
    }

    private fun log(
        priority: Int,
        event: String,
        route: String,
        probeName: String,
        modelPath: String?,
        modelFileSizeBytes: Long?,
        backendRequested: String?,
        maxOutputTokens: Int?,
        memorySnapshot: MemorySnapshot?,
        detail: String,
    ) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.println(
                priority,
                NPU_ENGINE_LOGCAT_TAG,
                buildMessage(
                    event = event,
                    route = route,
                    probeName = probeName,
                    modelPath = modelPath,
                    modelFileSizeBytes = modelFileSizeBytes,
                    backendRequested = backendRequested,
                    maxOutputTokens = maxOutputTokens,
                    memorySnapshot = memorySnapshot,
                    detail = detail,
                ),
            )
        }
    }

    private fun buildMessage(
        event: String,
        route: String,
        probeName: String,
        modelPath: String?,
        modelFileSizeBytes: Long?,
        backendRequested: String?,
        maxOutputTokens: Int?,
        memorySnapshot: MemorySnapshot?,
        detail: String,
    ): String {
        val modelBasename = modelPath?.substringAfterLast('/')?.ifBlank { "unavailable" } ?: "unavailable"
        val size = modelFileSizeBytes ?: modelPath?.let { path ->
            runCatching { File(path).takeIf { it.isFile }?.length() }.getOrNull()
        }
        return listOf(
            "event=$event",
            "timestamp_ms=${System.currentTimeMillis()}",
            "pid=${runCatching { Process.myPid().toString() }.getOrDefault("unavailable")}",
            "thread_name=${Thread.currentThread().name.ifBlank { "unavailable" }}",
            "process_name=${processName()}",
            "route=${route.ifBlank { "unavailable" }}",
            "probe_name=${probeName.ifBlank { "unavailable" }}",
            "model_basename=$modelBasename",
            "model_file_size_bytes=${size?.toString() ?: "unavailable"}",
            "backend_requested=${backendRequested ?: "unavailable"}",
            "max_output_tokens=${maxOutputTokens?.toString() ?: "unavailable"}",
            "total_pss_mb=${formatNullableLong(memorySnapshot?.totalPssMb)}",
            "native_heap_pss_mb=${formatNullableLong(memorySnapshot?.nativeHeapPssMb)}",
            "native_heap_alloc_mb=${formatNullableLong(memorySnapshot?.nativeHeapAllocatedMb)}",
            "dalvik_heap_pss_mb=${formatNullableLong(memorySnapshot?.dalvikHeapPssMb)}",
            "system_available_memory_mb=${formatNullableLong(memorySnapshot?.availableSystemMemoryMb)}",
            "low_memory=${memorySnapshot?.lowMemory?.toString() ?: "unavailable"}",
            detail.takeIf { it.isNotBlank() } ?: "detail=none",
        ).joinToString(" ")
    }

    private fun processName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { Application.getProcessName() }.getOrDefault("unavailable")
        } else {
            "unavailable"
        }

    private fun formatNullableLong(value: Long?): String = value?.toString() ?: "unavailable"
}
