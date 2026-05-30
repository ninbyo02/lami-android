package io.github.ninbyo02.lami.npu

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

enum class DevOnlyNpuTerminalTraceMarker(val value: String) {
    RECEIVER_ENTER("receiver_enter"),
    GO_ASYNC_STARTED("go_async_started"),
    WORKER_THREAD_STARTED("worker_thread_started"),
    RUN_FOR_CHATSCREEN_ENTER("run_for_chatscreen_enter"),
    BEFORE_NATIVE_ADAPTER_RUN("before_native_adapter_run"),
    BEFORE_RUN_DECODE_MARKER_SEEN("before_run_decode_marker_seen"),
    AFTER_NATIVE_ADAPTER_RUN("after_native_adapter_run"),
    BEFORE_TERMINAL_RESULT_WRITE("before_terminal_result_write"),
    AFTER_TERMINAL_RESULT_WRITE("after_terminal_result_write"),
    BEFORE_CLEANUP("before_cleanup"),
    AFTER_CLEANUP("after_cleanup"),
    THROWABLE_CAUGHT("throwable_caught"),
    FINALLY_ENTER("finally_enter"),
    FINALLY_EXIT("finally_exit"),
    WORKER_FINISHED("worker_finished"),
}

enum class DevOnlyNpuTerminalTraceClassification {
    WORKER_COMPLETED_CLEAN,
    WORKER_THROWABLE_CAUGHT,
    NATIVE_RETURNED_WITHOUT_RESULT,
    NATIVE_NON_RETURN_OR_PROCESS_DEATH,
    FINALLY_NOT_REACHED,
    TERMINAL_RESULT_WRITE_MISSING,
    CLEANUP_MISSING,
    RUN_ID_MISMATCH_REJECTED,
    STALE_TRACE_REJECTED,
    UNKNOWN,
}

data class DevOnlyNpuTerminalTraceEvent(
    val marker: DevOnlyNpuTerminalTraceMarker,
    val timestampMs: Long,
    val runId: String,
    val threadName: String,
    val processId: Int,
)

data class DevOnlyNpuTerminalTraceDecision(
    val classification: DevOnlyNpuTerminalTraceClassification,
    val runId: String,
    val runIdMismatchRejected: Boolean,
    val staleTraceRejected: Boolean,
    val suspectSession: Boolean,
    val reuseAllowed: Boolean,
    val hiddenPerRunIsolatedRequired: Boolean,
    val assistantMessageListInserted: Boolean = false,
    val selectedPathSaved: Boolean = false,
    val db: Boolean = false,
    val tts: Boolean = false,
    val markdown: Boolean = false,
    val streaming: Boolean = false,
)

object DevOnlyNpuTerminalTrace {
    const val FILE_PREFIX = "terminal_trace_"
    const val FILE_SUFFIX = ".txt"
    const val DEFAULT_TRACE_FRESHNESS_WINDOW_MS = 10 * 60 * 1000L

    fun traceFile(filesDir: File, runId: String): File =
        File(filesDir, "$FILE_PREFIX${safeRunId(runId)}$FILE_SUFFIX")

    fun append(
        context: Context,
        runId: String,
        marker: DevOnlyNpuTerminalTraceMarker,
        throwable: Throwable? = null,
    ) {
        append(
            file = traceFile(context.filesDir, runId),
            runId = runId,
            marker = marker,
            throwable = throwable,
            timestampMs = System.currentTimeMillis(),
            threadName = Thread.currentThread().name,
            processId = android.os.Process.myPid(),
        )
    }

    fun append(
        file: File,
        runId: String,
        marker: DevOnlyNpuTerminalTraceMarker,
        throwable: Throwable? = null,
        timestampMs: Long,
        threadName: String,
        processId: Int,
    ) {
        file.parentFile?.mkdirs()
        file.appendText(
            buildString {
                append("marker=${marker.value}")
                append(" timestamp_ms=$timestampMs")
                append(" runId=${escape(runId)}")
                append(" thread=${escape(threadName)}")
                append(" process_id=$processId")
                if (throwable != null) {
                    append(" exception_class=${escape(throwable.javaClass.name)}")
                    append(" exception_message=${escape(throwable.message.orEmpty())}")
                    append(" stacktrace=${escape(stackTraceOf(throwable))}")
                }
                append('\n')
            },
        )
    }

    fun parse(text: String): List<DevOnlyNpuTerminalTraceEvent> =
        text.lineSequence()
            .mapNotNull { line ->
                val values = line.split(' ')
                    .mapNotNull { token ->
                        val index = token.indexOf('=')
                        if (index <= 0) return@mapNotNull null
                        token.substring(0, index) to unescape(token.substring(index + 1))
                    }
                    .toMap()
                val markerValue = values["marker"] ?: return@mapNotNull null
                val marker = DevOnlyNpuTerminalTraceMarker.values()
                    .firstOrNull { it.value == markerValue }
                    ?: return@mapNotNull null
                DevOnlyNpuTerminalTraceEvent(
                    marker = marker,
                    timestampMs = values["timestamp_ms"]?.toLongOrNull() ?: return@mapNotNull null,
                    runId = values["runId"].orEmpty(),
                    threadName = values["thread"].orEmpty(),
                    processId = values["process_id"]?.toIntOrNull() ?: -1,
                )
            }
            .toList()

    fun classify(
        expectedRunId: String,
        traceText: String,
        nowMs: Long,
        freshnessWindowMs: Long = DEFAULT_TRACE_FRESHNESS_WINDOW_MS,
    ): DevOnlyNpuTerminalTraceDecision {
        val events = parse(traceText)
        if (events.isEmpty()) {
            return decision(
                classification = DevOnlyNpuTerminalTraceClassification.UNKNOWN,
                expectedRunId = expectedRunId,
            )
        }
        if (events.any { it.runId != expectedRunId }) {
            return decision(
                classification = DevOnlyNpuTerminalTraceClassification.RUN_ID_MISMATCH_REJECTED,
                expectedRunId = expectedRunId,
                runIdMismatchRejected = true,
            )
        }
        val newestTimestamp = events.maxOf { it.timestampMs }
        if (nowMs - newestTimestamp > freshnessWindowMs) {
            return decision(
                classification = DevOnlyNpuTerminalTraceClassification.STALE_TRACE_REJECTED,
                expectedRunId = expectedRunId,
                staleTraceRejected = true,
            )
        }

        val markers = events.map { it.marker }
        val classification = when {
            DevOnlyNpuTerminalTraceMarker.THROWABLE_CAUGHT in markers ->
                DevOnlyNpuTerminalTraceClassification.WORKER_THROWABLE_CAUGHT
            DevOnlyNpuTerminalTraceMarker.BEFORE_NATIVE_ADAPTER_RUN in markers &&
                DevOnlyNpuTerminalTraceMarker.AFTER_NATIVE_ADAPTER_RUN !in markers &&
                DevOnlyNpuTerminalTraceMarker.FINALLY_ENTER !in markers ->
                DevOnlyNpuTerminalTraceClassification.NATIVE_NON_RETURN_OR_PROCESS_DEATH
            DevOnlyNpuTerminalTraceMarker.FINALLY_ENTER !in markers ->
                DevOnlyNpuTerminalTraceClassification.FINALLY_NOT_REACHED
            DevOnlyNpuTerminalTraceMarker.AFTER_NATIVE_ADAPTER_RUN in markers &&
                DevOnlyNpuTerminalTraceMarker.BEFORE_TERMINAL_RESULT_WRITE !in markers ->
                DevOnlyNpuTerminalTraceClassification.TERMINAL_RESULT_WRITE_MISSING
            DevOnlyNpuTerminalTraceMarker.BEFORE_TERMINAL_RESULT_WRITE in markers &&
                DevOnlyNpuTerminalTraceMarker.AFTER_TERMINAL_RESULT_WRITE !in markers ->
                DevOnlyNpuTerminalTraceClassification.TERMINAL_RESULT_WRITE_MISSING
            DevOnlyNpuTerminalTraceMarker.AFTER_TERMINAL_RESULT_WRITE in markers &&
                DevOnlyNpuTerminalTraceMarker.AFTER_CLEANUP !in markers ->
                DevOnlyNpuTerminalTraceClassification.CLEANUP_MISSING
            hasCleanCompletion(markers) ->
                DevOnlyNpuTerminalTraceClassification.WORKER_COMPLETED_CLEAN
            else -> DevOnlyNpuTerminalTraceClassification.UNKNOWN
        }
        return decision(classification = classification, expectedRunId = expectedRunId)
    }

    private fun hasCleanCompletion(markers: List<DevOnlyNpuTerminalTraceMarker>): Boolean {
        val required = listOf(
            DevOnlyNpuTerminalTraceMarker.RECEIVER_ENTER,
            DevOnlyNpuTerminalTraceMarker.GO_ASYNC_STARTED,
            DevOnlyNpuTerminalTraceMarker.WORKER_THREAD_STARTED,
            DevOnlyNpuTerminalTraceMarker.RUN_FOR_CHATSCREEN_ENTER,
            DevOnlyNpuTerminalTraceMarker.BEFORE_NATIVE_ADAPTER_RUN,
            DevOnlyNpuTerminalTraceMarker.AFTER_NATIVE_ADAPTER_RUN,
            DevOnlyNpuTerminalTraceMarker.BEFORE_TERMINAL_RESULT_WRITE,
            DevOnlyNpuTerminalTraceMarker.AFTER_TERMINAL_RESULT_WRITE,
            DevOnlyNpuTerminalTraceMarker.BEFORE_CLEANUP,
            DevOnlyNpuTerminalTraceMarker.AFTER_CLEANUP,
            DevOnlyNpuTerminalTraceMarker.FINALLY_ENTER,
            DevOnlyNpuTerminalTraceMarker.FINALLY_EXIT,
            DevOnlyNpuTerminalTraceMarker.WORKER_FINISHED,
        )
        var previousIndex = -1
        required.forEach { marker ->
            val index = markers.indexOf(marker)
            if (index <= previousIndex) return false
            previousIndex = index
        }
        return true
    }

    private fun decision(
        classification: DevOnlyNpuTerminalTraceClassification,
        expectedRunId: String,
        runIdMismatchRejected: Boolean = false,
        staleTraceRejected: Boolean = false,
    ): DevOnlyNpuTerminalTraceDecision {
        val suspect = classification != DevOnlyNpuTerminalTraceClassification.WORKER_COMPLETED_CLEAN
        return DevOnlyNpuTerminalTraceDecision(
            classification = classification,
            runId = expectedRunId,
            runIdMismatchRejected = runIdMismatchRejected,
            staleTraceRejected = staleTraceRejected,
            suspectSession = suspect,
            reuseAllowed = !suspect,
            hiddenPerRunIsolatedRequired = suspect,
        )
    }

    private fun safeRunId(runId: String): String =
        runId.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "unknown" }

    private fun escape(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace(" ", "\\s")

    private fun unescape(value: String): String =
        value
            .replace("\\s", " ")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
