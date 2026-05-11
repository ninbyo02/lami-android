package io.github.ninbyo02.lami.debug

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.system.Os
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class QnnDirectProbeActivity : Activity() {
    private lateinit var runner: QnnDirectProbeRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        runner = QnnDirectProbeRunner(this)
        runner.begin(intent)
        try {
            super.onCreate(savedInstanceState)
            runner.start(intent) {
                runOnUiThread { finish() }
            }
        } catch (throwable: Throwable) {
            runner.error(stage = "activity-onCreate", throwable = throwable)
            finish()
        }
    }
}

private class QnnDirectProbeRunner(
    private val context: Context,
) {
    private val resultFile = File(context.filesDir, RESULT_FILE_NAME)
    private val lastRunFile = File(context.filesDir, LAST_RUN_FILE_NAME)
    private val runStartedAtEpochMs = System.currentTimeMillis()
    private val runId = "$runStartedAtEpochMs-${android.os.Process.myPid()}"

    @Volatile
    private var lastStage: String = "not-started"

    fun begin(intent: Intent?) {
        val previous = readPreviousRun()
        synchronized(this) {
            resultFile.parentFile?.mkdirs()
            resultFile.writeText("", Charsets.UTF_8)
        }
        append("QNN_DIRECT_PROBE_BEGIN runId=$runId intent=${intent.describeForLog()}")
        if (previous != null && previous.result.isNullOrBlank()) {
            append(
                "QNN_DIRECT_ERROR previousRunIncomplete=true previousRunId=${previous.runId} " +
                    "previousPid=${previous.pid} previousLastStage=${previous.lastStage} " +
                    "classification=native-crash-or-abort-likely",
            )
        }
        stage("activity-onCreate")
    }

    fun start(intent: Intent?, onFinished: () -> Unit) {
        stage("thread-start-requested")
        Thread({
            var result = "FAILED"
            try {
                result = run(intent)
            } catch (throwable: Throwable) {
                error(stage = lastStage, throwable = throwable)
                append("QNN_DIRECT_RESULT result=FAILED classification=${classify(throwable)}")
            } finally {
                appendProcMaps(reason = "final")
                append("QNN_DIRECT_END result=$result lastStage=$lastStage")
                writeLastRun(stage = lastStage, result = result)
                onFinished()
            }
        }, "QnnDirectProbe").start()
    }

    fun error(stage: String, throwable: Throwable) {
        append(
            "QNN_DIRECT_ERROR stage=$stage class=${throwable.javaClass.name} " +
                "message=${throwable.message.orEmpty().sanitize()} classification=${classify(throwable)}",
        )
        throwable.stackTrace.take(MAX_STACK_FRAMES).forEachIndexed { index, frame ->
            append("QNN_DIRECT_STACK source=throwable frame=$index value=${frame.toString().sanitize()}")
        }
        writeLastRun(stage = stage, result = "FAILED")
    }

    private fun run(intent: Intent?): String {
        stage("parse-input")
        val probeLevel = parseProbeLevel(intent)
        val runtimeDirExtra = intent?.getStringExtra(EXTRA_RUNTIME_DIR)?.trim().orEmpty()
        val copyPrivate = intent?.getBooleanExtra(EXTRA_COPY_PRIVATE, true) ?: true
        val requestedRuntimeDir = File(runtimeDirExtra.ifBlank { QAIRT_STAGE_DIR.absolutePath })
        append("QNN_DIRECT_STAGE probeLevel=$probeLevel copyPrivate=$copyPrivate requestedRuntimeDir=${requestedRuntimeDir.absolutePath}")

        stage("environment-diagnostics")
        appendEnvironment()

        stage("prepare-runtime-dir")
        val runtimeDir = if (copyPrivate) {
            val target = if (runtimeDirExtra.isBlank()) {
                File(context.codeCacheDir, PRIVATE_NATIVE_RUNTIME_DIR_NAME)
            } else {
                requestedRuntimeDir
            }
            copyRuntimeToPrivateDir(sourceDir = QAIRT_STAGE_DIR, targetDir = target)
        } else {
            requestedRuntimeDir
        }
        append("QNN_DIRECT_STAGE effectiveRuntimeDir=${runtimeDir.absolutePath}")

        stage("runtime-file-diagnostics")
        QNN_RUNTIME_LIBS.forEach { libName ->
            appendFileStatus(File(runtimeDir, libName))
        }

        stage("setenv-adsp")
        Os.setenv("ADSP_LIBRARY_PATH", runtimeDir.absolutePath, true)
        append("QNN_DIRECT_STAGE ADSP_LIBRARY_PATH=${System.getenv("ADSP_LIBRARY_PATH").orEmpty()}")
        append("QNN_DIRECT_STAGE LD_LIBRARY_PATH=${System.getenv("LD_LIBRARY_PATH").orEmpty().ifBlank { "unset" }}")

        stage("load-debug-jni")
        val nativeAvailable = QnnDirectProbeNative.load(this::append)
        if (!nativeAvailable) {
            append("QNN_DIRECT_RESULT result=FAILED classification=dlsym-failure reason=debug-jni-load-failed")
            return "FAILED"
        }

        val watchdogDone = AtomicBoolean(false)
        val watchdog = if (probeLevel == "backend" || probeLevel == "device") {
            startWatchdog(target = Thread.currentThread(), done = watchdogDone)
        } else {
            null
        }

        return try {
            stage("native-run-$probeLevel")
            val code = QnnDirectProbeNative.runProbe(
                runtimeDir = runtimeDir.absolutePath,
                probeLevel = probeLevel,
                resultPath = resultFile.absolutePath,
                lastRunPath = lastRunFile.absolutePath,
                runId = runId,
            )
            stage("native-return-$code")
            val result = if (code == 0 && probeLevel == "symbols") {
                "SUCCESS_WITH_WARNINGS"
            } else if (code == 0) {
                "SUCCESS"
            } else {
                "FAILED"
            }
            append("QNN_DIRECT_RESULT result=$result nativeCode=$code classification=${if (code == 0) "none" else "provider-call-failure"}")
            result
        } finally {
            watchdogDone.set(true)
            watchdog?.interrupt()
        }
    }

    private fun parseProbeLevel(intent: Intent?): String {
        return when (val value = intent?.getStringExtra(EXTRA_PROBE_LEVEL)?.trim()?.lowercase().orEmpty()) {
            "", "symbols" -> "symbols"
            "system" -> "system"
            "backend" -> "backend"
            "device" -> "device"
            else -> {
                append("QNN_DIRECT_ERROR invalidProbeLevel=$value fallback=symbols")
                "symbols"
            }
        }
    }

    private fun appendEnvironment() {
        append(
            "QNN_DIRECT_STAGE pid=${android.os.Process.myPid()} uid=${android.os.Process.myUid()} " +
                "processName=${Application.getProcessName()} threadName=${Thread.currentThread().name}",
        )
        append(
            "QNN_DIRECT_STAGE Build.MODEL=${Build.MODEL.sanitize()} " +
                "Build.SOC_MODEL=${Build.SOC_MODEL.sanitize()} SDK_INT=${Build.VERSION.SDK_INT}",
        )
        append("QNN_DIRECT_STAGE filesDir=${context.filesDir.absolutePath} codeCacheDir=${context.codeCacheDir.absolutePath}")
    }

    private fun copyRuntimeToPrivateDir(sourceDir: File, targetDir: File): File {
        require(targetDir.mkdirs() || targetDir.isDirectory) {
            "failed to create runtimeDir: ${targetDir.absolutePath}"
        }
        targetDir.setReadable(true, false)
        targetDir.setExecutable(true, false)
        append(
            "QNN_DIRECT_STAGE copyPrivate sourceDir=${sourceDir.absolutePath} targetDir=${targetDir.absolutePath} " +
                "targetExists=${targetDir.exists()} targetCanRead=${targetDir.canRead()} targetCanExecute=${targetDir.canExecute()}",
        )
        QNN_RUNTIME_LIBS.forEach { libName ->
            val source = File(sourceDir, libName)
            val target = File(targetDir, libName)
            if (!source.isFile || !source.canRead()) {
                append(
                    "QNN_DIRECT_FILE copyResult=missing-source name=$libName source=${source.absolutePath} " +
                        "sourceExists=${source.exists()} sourceCanRead=${source.canRead()} target=${target.absolutePath}",
                )
            } else {
                val copied = copyIfNeeded(source = source, target = target)
                target.setReadable(true, false)
                target.setExecutable(true, false)
                append(
                    "QNN_DIRECT_FILE copyResult=${if (copied) "copied" else "up-to-date"} " +
                        "name=$libName source=${source.absolutePath} target=${target.absolutePath}",
                )
            }
        }
        return targetDir
    }

    private fun copyIfNeeded(source: File, target: File): Boolean {
        if (target.isFile && target.length() == source.length()) return false
        target.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(target, false).use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
        return true
    }

    private fun appendFileStatus(file: File) {
        val stat = runCatching { Os.stat(file.absolutePath) }.getOrNull()
        val inspection = runCatching { QairtElfInspector.inspect(file) }.getOrNull()
        val sha256Prefix = runCatching { sha256Prefix(file) }.getOrElse { "unavailable:${it.javaClass.simpleName}" }
        append(
            "QNN_DIRECT_FILE name=${file.name} path=${file.absolutePath} exists=${file.exists()} " +
                "canRead=${file.canRead()} canExecute=${file.canExecute()} size=${file.length()} " +
                "inode=${stat?.st_ino ?: "unavailable"} mode=${stat?.st_mode ?: "unavailable"} " +
                "BuildId=${inspection?.buildId.orEmpty().ifBlank { "missing" }} sha256Prefix=$sha256Prefix",
        )
    }

    private fun sha256Prefix(file: File): String {
        require(file.isFile && file.canRead()) { "unreadable file: ${file.absolutePath}" }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }.take(SHA256_PREFIX_CHARS)
    }

    private fun startWatchdog(target: Thread, done: AtomicBoolean): Thread {
        return Thread({
            val start = SystemClock.elapsedRealtime()
            while (!done.get()) {
                val elapsed = SystemClock.elapsedRealtime() - start
                append("QNN_DIRECT_WATCHDOG heartbeat elapsedMs=$elapsed lastStage=$lastStage")
                if (elapsed >= WATCHDOG_TIMEOUT_MS) {
                    append("QNN_DIRECT_WATCHDOG timeout elapsedMs=$elapsed lastStage=$lastStage classification=hang-likely")
                    target.stackTrace.take(MAX_STACK_FRAMES).forEachIndexed { index, frame ->
                        append("QNN_DIRECT_STACK source=watchdog-target frame=$index value=${frame.toString().sanitize()}")
                    }
                    appendProcState(reason = "watchdog-timeout")
                    appendOpenFds(reason = "watchdog-timeout")
                    appendProcMaps(reason = "watchdog-timeout")
                    writeLastRun(stage = "$lastStage-watchdog-timeout", result = null)
                    return@Thread
                }
                try {
                    Thread.sleep(WATCHDOG_HEARTBEAT_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "QnnDirectProbeWatchdog").apply {
            isDaemon = true
            start()
        }
    }

    private fun appendProcState(reason: String) {
        runCatching {
            File("/proc/self/status").readLines(Charsets.UTF_8).take(MAX_PROC_LINES)
        }.onSuccess { lines ->
            lines.forEachIndexed { index, line ->
                append("QNN_DIRECT_WATCHDOG procStatus reason=$reason line=$index value=${line.sanitize()}")
            }
        }.onFailure { throwable ->
            append("QNN_DIRECT_ERROR procStatus reason=$reason class=${throwable.javaClass.name} message=${throwable.message.orEmpty().sanitize()}")
        }
    }

    private fun appendOpenFds(reason: String) {
        runCatching {
            File("/proc/self/fd").listFiles().orEmpty()
                .sortedBy { it.name.toIntOrNull() ?: Int.MAX_VALUE }
                .take(MAX_FD_LINES)
        }.onSuccess { fds ->
            append("QNN_DIRECT_WATCHDOG openFdCount reason=$reason count=${fds.size}")
            fds.forEachIndexed { index, fd ->
                val target = runCatching { Os.readlink(fd.absolutePath) }.getOrElse { "unreadable:${it.javaClass.simpleName}" }
                append("QNN_DIRECT_WATCHDOG openFd reason=$reason index=$index fd=${fd.name} target=${target.sanitize()}")
            }
        }.onFailure { throwable ->
            append("QNN_DIRECT_ERROR openFds reason=$reason class=${throwable.javaClass.name} message=${throwable.message.orEmpty().sanitize()}")
        }
    }

    private fun appendProcMaps(reason: String) {
        runCatching {
            File("/proc/self/maps").readLines(Charsets.UTF_8)
                .filter { line ->
                    val lower = line.lowercase()
                    lower.contains("libqnn") ||
                        lower.contains("litertdispatch") ||
                        lower.contains("qairt_native_runtime") ||
                        lower.contains("/data/local/tmp/qairt")
                }
                .distinct()
                .take(MAX_PROC_LINES)
        }.onSuccess { lines ->
            append("QNN_DIRECT_STAGE procMaps reason=$reason matchedLines=${lines.size}")
            lines.forEachIndexed { index, line ->
                append("QNN_DIRECT_STAGE procMap reason=$reason line=$index value=${line.sanitize()}")
            }
        }.onFailure { throwable ->
            append("QNN_DIRECT_ERROR procMaps reason=$reason class=${throwable.javaClass.name} message=${throwable.message.orEmpty().sanitize()}")
        }
    }

    private fun stage(stage: String) {
        lastStage = stage
        append("QNN_DIRECT_STAGE stage=$stage")
        writeLastRun(stage = stage, result = null)
    }

    private fun append(line: String) {
        val text = "${System.currentTimeMillis()} $line\n"
        synchronized(this) {
            resultFile.parentFile?.mkdirs()
            FileOutputStream(resultFile, true).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }
        Log.i(LOG_TAG, line)
    }

    private fun writeLastRun(stage: String, result: String?) {
        synchronized(this) {
            lastRunFile.parentFile?.mkdirs()
            lastRunFile.writeText(
                buildString {
                    appendLine("runId=$runId")
                    appendLine("pid=${android.os.Process.myPid()}")
                    appendLine("startedAtEpochMs=$runStartedAtEpochMs")
                    appendLine("lastUpdatedAtEpochMs=${System.currentTimeMillis()}")
                    appendLine("lastStage=$stage")
                    if (result != null) appendLine("result=$result")
                },
                Charsets.UTF_8,
            )
        }
    }

    private fun readPreviousRun(): PreviousRun? {
        return runCatching {
            if (!lastRunFile.isFile) return@runCatching null
            val values = lastRunFile.readLines(Charsets.UTF_8)
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
                }
                .toMap()
            PreviousRun(
                runId = values["runId"].orEmpty(),
                pid = values["pid"].orEmpty(),
                lastStage = values["lastStage"].orEmpty(),
                result = values["result"],
            )
        }.getOrNull()
    }

    private fun classify(throwable: Throwable): String {
        val text = "${throwable.javaClass.name} ${throwable.message.orEmpty()} $lastStage".lowercase()
        return when {
            "dlopen" in text -> "dlopen-failure"
            "dlsym" in text || "symbol" in text -> "dlsym-failure"
            "provider" in text -> "provider-call-failure"
            "backend" in text -> "backend-create-failure"
            "device" in text -> "device-create-failure"
            "permission" in text || "namespace" in text || "permitted" in text -> "permission-or-namespace-likely"
            else -> "native-crash-or-abort-likely"
        }
    }

    private data class PreviousRun(
        val runId: String,
        val pid: String,
        val lastStage: String,
        val result: String?,
    )

    private companion object {
        private const val LOG_TAG = "QnnDirectProbe"
        private const val EXTRA_RUNTIME_DIR = "runtimeDir"
        private const val EXTRA_COPY_PRIVATE = "copyPrivate"
        private const val EXTRA_PROBE_LEVEL = "probeLevel"
        private const val RESULT_FILE_NAME = "qnn_direct_probe_result.txt"
        private const val LAST_RUN_FILE_NAME = "qnn_direct_probe_last_run.txt"
        private const val PRIVATE_NATIVE_RUNTIME_DIR_NAME = "qairt_native_runtime"
        private const val WATCHDOG_HEARTBEAT_MS = 500L
        private const val WATCHDOG_TIMEOUT_MS = 10_000L
        private const val MAX_STACK_FRAMES = 80
        private const val MAX_PROC_LINES = 160
        private const val MAX_FD_LINES = 120
        private const val SHA256_PREFIX_CHARS = 16
        private val QAIRT_STAGE_DIR = File("/data/local/tmp/qairt")
        private val QNN_RUNTIME_LIBS = listOf(
            "libQnnSystem.so",
            "libQnnHtp.so",
            "libLiteRtDispatch_Qualcomm.so",
            "libQnnHtpPrepare.so",
            "libQnnHtpV79Stub.so",
            "libQnnHtpV79Skel.so",
            "libcdsprpc.so",
        )
    }
}

private object QnnDirectProbeNative {
    @Volatile
    private var loaded = false

    @Volatile
    private var attempted = false

    fun load(logger: (String) -> Unit): Boolean {
        if (loaded) return true
        if (attempted) return false
        attempted = true
        return runCatching {
            System.loadLibrary("qnn_direct_probe_debug")
            loaded = true
            logger("QNN_DIRECT_STAGE debugJniLoad=success library=libqnn_direct_probe_debug.so")
            true
        }.getOrElse { throwable ->
            logger(
                "QNN_DIRECT_ERROR debugJniLoad=failed class=${throwable.javaClass.name} " +
                    "message=${throwable.message.orEmpty().sanitize()} classification=dlopen-failure",
            )
            false
        }
    }

    external fun runProbe(
        runtimeDir: String,
        probeLevel: String,
        resultPath: String,
        lastRunPath: String,
        runId: String,
    ): Int
}

private fun Intent?.describeForLog(): String {
    if (this == null) return "null"
    val keys = extras?.keySet().orEmpty().sorted()
    return "action=${action.orEmpty()} data=${dataString.orEmpty()} extras=${keys.joinToString(",") { key -> "$key=${extras?.get(key)}" }}"
        .sanitize()
}

private fun String.sanitize(): String = replace('\n', ' ').replace('\r', ' ').take(4000)
