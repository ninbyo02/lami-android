package io.github.ninbyo02.lami.util

import android.content.Context
import io.github.ninbyo02.lami.BuildConfig
import java.io.File

internal object DebugTraceFile {
    private const val MAX_TRACE_BYTES = 1_048_576L
    private const val TRACE_DIRECTORY = "debug"
    private const val TRACE_FILE = "local_reflection_trace.log"
    private const val PREVIOUS_TRACE_FILE = "local_reflection_trace.log.1"
    private val lock = Any()

    fun append(context: Context, line: String) {
        if (!BuildConfig.DEBUG) return

        synchronized(lock) {
            runCatching {
                val directory = File(context.filesDir, TRACE_DIRECTORY)
                if (!directory.exists() && !directory.mkdirs()) return@runCatching

                val traceFile = File(directory, TRACE_FILE)
                if (traceFile.exists() && traceFile.length() >= MAX_TRACE_BYTES) {
                    val previousTraceFile = File(directory, PREVIOUS_TRACE_FILE)
                    previousTraceFile.delete()
                    if (!traceFile.renameTo(previousTraceFile)) {
                        traceFile.writeText("", Charsets.UTF_8)
                    }
                }
                traceFile.appendText(line + "\n", Charsets.UTF_8)
            }
        }
    }
}
