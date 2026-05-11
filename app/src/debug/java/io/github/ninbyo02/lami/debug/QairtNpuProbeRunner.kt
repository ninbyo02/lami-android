package io.github.ninbyo02.lami.debug

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.system.Os
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import io.github.ninbyo02.lami.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

@OptIn(ExperimentalApi::class)
internal class QairtNpuProbeRunner(
    private val context: Context,
) {
    private val runStartedAtEpochMs = System.currentTimeMillis()
    private val runId = "$runStartedAtEpochMs-${android.os.Process.myPid()}"

    @Volatile
    private var tmpResultWritable = true

    @Volatile
    private var tmpWriteFailureLogged = false

    init {
        installProcessDiagnostics()
    }

    fun readPreviousRunSnapshot(): PreviousRunSnapshot? {
        return runCatching {
            val file = File(context.filesDir, LAST_RUN_NAME)
            if (!file.isFile) return@runCatching null
            val values = file.readLines(Charsets.UTF_8)
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) {
                        null
                    } else {
                        line.substring(0, separator) to line.substring(separator + 1)
                    }
                }
                .toMap()
            PreviousRunSnapshot(
                runId = values["runId"].orEmpty(),
                pid = values["pid"].orEmpty(),
                startedAtEpochMs = values["startedAtEpochMs"].orEmpty(),
                lastUpdatedAtEpochMs = values["lastUpdatedAtEpochMs"].orEmpty(),
                lastStage = values["lastStage"].orEmpty(),
                result = values["result"].orEmpty().ifBlank { null },
            )
        }.getOrNull()
    }

    fun reportPreviousRunIfIncomplete(previousRun: PreviousRunSnapshot?) {
        if (previousRun == null || previousRun.result != null) return
        appendResultLine(
            "PREVIOUS_RUN_INCOMPLETE previousRunId=${previousRun.runId} " +
                "previousPid=${previousRun.pid} previousStartedAtEpochMs=${previousRun.startedAtEpochMs} " +
                "previousLastUpdatedAtEpochMs=${previousRun.lastUpdatedAtEpochMs} " +
                "previousLastStage=${previousRun.lastStage} classification=${previousRun.classification()}",
        )
    }

    fun startProbeThread(
        intent: Intent?,
        completionStage: String,
        onFinished: () -> Unit,
    ) {
        stage("thread-start-requested")
        Thread({
            marker(stage = "thread-started", intent = intent, append = true)
            var success = false
            try {
                runProbe(intent)
                success = true
            } catch (throwable: Throwable) {
                logFailure(stage = lastStage, throwable = throwable)
            } finally {
                stage(completionStage)
                val result = if (success) "SUCCESS" else "FAILED"
                appendResultLine("RESULT=$result")
                writeLastRun(stage = completionStage, result = result)
                stage("thread-finished")
                onFinished()
            }
        }, "QairtNpuProbe").start()
    }

    fun runProbe(intent: Intent?) {
        try {
            stage("runProbe-start")
            val runPrompt = intent?.getBooleanExtra(EXTRA_RUN_PROMPT, false) ?: false
            val skipModelScan = intent?.getBooleanExtra(EXTRA_SKIP_MODEL_SCAN, false) ?: false
            val backendMode = parseBackendMode(intent)
            val modelPathOverride = intent?.getStringExtra(EXTRA_MODEL_PATH)?.trim().orEmpty()
            val modelFile = resolveModelFile(modelPathOverride)
            appendResultLine("runProbe intent=${intent.describeForLog()} cacheDir=${context.cacheDir.absolutePath}")
            appendResultLine(
                "probeMode runPrompt=$runPrompt backendMode=${backendMode.value} " +
                    "skipModelScan=$skipModelScan " +
                    "modelPathOverride=${modelPathOverride.ifBlank { "none" }} " +
                    "resolvedModelPath=${modelFile.absolutePath}",
            )
            appendResultLine("LITERTLM_ANDROID_VERSION actual=${BuildConfig.LITERTLM_ANDROID_VERSION}")

            if (backendMode == BackendMode.NPU_PRIVATE) {
                stage("stage-dir-check")
                require(QAIRT_STAGE_DIR.isDirectory) {
                    "QAIRT stage dir missing or not directory: ${QAIRT_STAGE_DIR.absolutePath}"
                }
                logInfo(
                    "stage-dir path=${QAIRT_STAGE_DIR.absolutePath} " +
                        "exists=${QAIRT_STAGE_DIR.exists()} canRead=${QAIRT_STAGE_DIR.canRead()} " +
                        "canExecute=${QAIRT_STAGE_DIR.canExecute()} size=${QAIRT_STAGE_DIR.length()}",
                )

                stage("file-check")
                REQUIRED_FILES.forEach(::logFileStatus)
            } else {
                stage("stage-dir-check-skipped")
                logInfo("stage-dir-check skipped backendMode=${backendMode.value}")
            }

            stage("model-file-check")
            logModelStatus(modelPathOverride = modelPathOverride, modelFile = modelFile)

            stage("model-read")
            readModelHeader(modelFile)

            stage("model-tensor-signature")
            if (skipModelScan) {
                appendResultLine("MODEL_TENSOR_SIGNATURE_SKIPPED reason=intent-extra-skipModelScan")
            } else {
                logModelTensorSignature(modelFile)
            }

            val privateNativeRuntimeDir = if (backendMode == BackendMode.NPU_PRIVATE) {
                stage("private-native-runtime-prepare")
                preparePrivateNativeRuntimeDir()
            } else {
                stage("private-native-runtime-skipped")
                logInfo("private native runtime skipped backendMode=${backendMode.value}")
                null
            }

            if (privateNativeRuntimeDir != null) {
                stage("qairt-version-diagnostics")
                logQairtVersionDiagnostics(
                    privateNativeRuntimeDir = privateNativeRuntimeDir,
                    modelFile = modelFile,
                    skipModelScan = skipModelScan,
                )
            }
            stage("runtime-compatibility-diagnostics")
            logRuntimeCompatibilityDiagnostics(
                privateNativeRuntimeDir = privateNativeRuntimeDir,
                modelFile = modelFile,
                skipModelScan = skipModelScan,
            )

            if (privateNativeRuntimeDir != null) {
                stage("setenv-adsp")
                Os.setenv("ADSP_LIBRARY_PATH", privateNativeRuntimeDir.absolutePath, true)
                logInfo("ADSP_LIBRARY_PATH=${System.getenv("ADSP_LIBRARY_PATH").orEmpty()}")
            } else {
                stage("setenv-adsp-skipped")
                logInfo("ADSP_LIBRARY_PATH skipped backendMode=${backendMode.value}")
            }

            stage("ld-library-path-log")
            logInfo("LD_LIBRARY_PATH=${System.getenv("LD_LIBRARY_PATH").orEmpty().ifBlank { "unset" }}")

            stage("litertlm-jni-diagnostics")
            logLiteRtLmJniDiagnostics()

            stage("backend-create")
            val backend = when (backendMode) {
                BackendMode.CPU -> Backend.CPU()
                BackendMode.GPU -> Backend.GPU()
                BackendMode.NPU_PRIVATE -> Backend.NPU(requireNotNull(privateNativeRuntimeDir).absolutePath)
            }
            logInfo(
                "backend=${backend.javaClass.name} backendMode=${backendMode.value} " +
                    "nativeLibraryDir=${privateNativeRuntimeDir?.absolutePath ?: "none"}",
            )

            stage("engine-config-create")
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                visionBackend = Backend.GPU(),
                audioBackend = Backend.CPU(),
                maxNumTokens = null,
                cacheDir = context.cacheDir.absolutePath,
            )
            logInfo("engine-config-created modelPath=${config.modelPath} cacheDir=${config.cacheDir}")
            appendResultLine(
                "QNN_GRAPH_INFO backendMode=${backendMode.value} modelPath=${modelFile.absolutePath} " +
                    "nativeLibraryDir=${privateNativeRuntimeDir?.absolutePath ?: "none"} " +
                    "cacheDir=${context.cacheDir.absolutePath} invocationContext=EngineConfig-created",
            )
            if (skipModelScan) {
                appendResultLine("QNN_GRAPH_INFO modelScanSkipped=true reason=intent-extra-skipModelScan")
            } else {
                logModelGraphInfo(modelFile)
            }

            var engine: Engine? = null
            var conversation: Conversation? = null
            try {
                stage("before-engine")

                stage("engine-create")
                val engineCreateStartMs = SystemClock.elapsedRealtime()
                appendResultLine(
                    "QNN_GRAPH_INFO nativeCreateEngine-before elapsedRealtime=$engineCreateStartMs " +
                        "backendMode=${backendMode.value}",
                )
                engine = Engine(config)
                val engineCreateDurationMs = SystemClock.elapsedRealtime() - engineCreateStartMs
                logInfo("engine-created class=${engine.javaClass.name} durationMs=$engineCreateDurationMs")
                appendResultLine(
                    "QNN_GRAPH_INFO nativeCreateEngine-after durationMs=$engineCreateDurationMs " +
                        "engineClass=${engine.javaClass.name}",
                )

                initializeEngine(engine = engine, config = config, nativeLibraryDir = privateNativeRuntimeDir)

                if (!runPrompt) {
                    stage("probe-success-initialize-only")
                    return
                }

                stage("conversation-create")
                conversation = engine.createConversation()
                logInfo("conversation-created class=${conversation.javaClass.name}")

                stage("send-message")
                val response = conversation.sendMessage("hi")
                val text = response.contents.toString().trim()
                logInfo("response=${text.take(MAX_LOG_TEXT)}")

                stage("benchmark-info")
                val benchmark = conversation.getBenchmarkInfo()
                logInfo(
                    "benchmark " +
                        "initTimeInSecond=${benchmark.initTimeInSecond} " +
                        "timeToFirstTokenInSecond=${benchmark.timeToFirstTokenInSecond} " +
                        "lastPrefillTokenCount=${benchmark.lastPrefillTokenCount} " +
                        "lastDecodeTokenCount=${benchmark.lastDecodeTokenCount} " +
                        "lastPrefillTokensPerSecond=${benchmark.lastPrefillTokensPerSecond} " +
                        "lastDecodeTokensPerSecond=${benchmark.lastDecodeTokensPerSecond}",
                )

                stage("probe-success")
            } finally {
                logInfo("stage=close")
                closeBestEffort("conversation", conversation)
                closeBestEffort("engine", engine)
            }
        } catch (throwable: Throwable) {
            logFailure(stage = lastStage, throwable = throwable)
            throw throwable
        }
    }

    private fun initializeEngine(
        engine: Engine,
        config: EngineConfig,
        nativeLibraryDir: File?,
    ) {
        val initializeDone = AtomicBoolean(false)
        val initializeStartElapsedMs = SystemClock.elapsedRealtime()
        val initializeTid = android.os.Process.myTid()
        nativeLibraryDir?.let {
            appendResultLine("PRIVATE_NATIVE_RUNTIME_READY path=${it.absolutePath}")
        }
        val initializeThreadCpuStartNs = Debug.threadCpuTimeNanos()
        stage("engine-initialize-before")
        appendResultLine(
            "engine-initialize-before " +
                "uptimeMillis=${SystemClock.uptimeMillis()} " +
                "elapsedRealtime=${SystemClock.elapsedRealtime()} " +
                "threadCpuTimeNanos=$initializeThreadCpuStartNs " +
                "tid=$initializeTid " +
                "modelPath=${config.modelPath} " +
                "nativeLibraryDir=${nativeLibraryDir?.absolutePath ?: "none"} " +
                "ADSP_LIBRARY_PATH=${System.getenv("ADSP_LIBRARY_PATH").orEmpty()} " +
                "LD_LIBRARY_PATH=${System.getenv("LD_LIBRARY_PATH").orEmpty().ifBlank { "unset" }}",
        )
        collectNativeCrashArtifacts(reason = "engine-initialize-before")
        appendResultLine(crashAnalysisMetadata(config = config, nativeLibraryDir = nativeLibraryDir))
        appendResultLine(
            "RESULT=UNKNOWN_CRASH_OR_HANG pending stage=engine-initialize-before " +
                "reason=initialize has not returned yet",
        )
        val procMapsBeforeInitialize = appendProcMapsSnapshot(stage = "proc-maps-before-initialize")
        startInitializeWatchdog(
            done = initializeDone,
            startElapsedMs = initializeStartElapsedMs,
            initializeThread = Thread.currentThread(),
            initializeTid = initializeTid,
            initializeThreadCpuStartNs = initializeThreadCpuStartNs,
            procMapsBeforeInitialize = procMapsBeforeInitialize,
        )
        try {
            stage("engine-initialize-enter")
            writeLastRun(stage = "engine-initialize-enter", result = null)
            appendResultLine(
                "ENGINE_INIT_WATCHDOG stage=engine-initialize-enter elapsedMs=0 " +
                    "thread=${Thread.currentThread().name} state=${Thread.currentThread().state} " +
                    "threadCpuTimeNanos=${Debug.threadCpuTimeNanos()}",
            )
            appendResultLine(
                "CRASH_WINDOW_BEGIN stage=engine-initialize-enter runId=$runId " +
                    "pid=${android.os.Process.myPid()} tid=${android.os.Process.myTid()}",
            )
            engine.initialize()
            val durationMs = SystemClock.elapsedRealtime() - initializeStartElapsedMs
            val cpuDurationNs = Debug.threadCpuTimeNanos() - initializeThreadCpuStartNs
            val procMapsAfterInitialize = appendProcMapsSnapshot(stage = "proc-maps-after-initialize")
            appendProcMapsDiff(
                before = procMapsBeforeInitialize,
                after = procMapsAfterInitialize,
                reason = "engine-initialize-returned",
            )
            val loadedQnnLibsCount = countLoadedQnnMapEntries(procMapsAfterInitialize)
            initializeDone.set(true)
            collectNativeCrashArtifacts(reason = "engine-initialize-returned")
            appendResultLine(
                "CRASH_WINDOW_END stage=engine-initialize-returned runId=$runId " +
                    "pid=${android.os.Process.myPid()} tid=${android.os.Process.myTid()}",
            )
            appendResultLine(
                "stage=engine-initialize-after durationMs=$durationMs result=success " +
                    "initialized=${engine.isInitialized()} threadCpuDurationNanos=$cpuDurationNs " +
                    "tid=$initializeTid loadedQnnLibsCount=$loadedQnnLibsCount",
            )
            appendResultLine("RESULT=SUCCESS_INITIALIZE")
            writeLastRun(stage = "engine-initialize-after", result = "SUCCESS_INITIALIZE")
            stage("engine-initialize-after")
        } catch (throwable: Throwable) {
            initializeDone.set(true)
            val procMapsAfterThrowable = appendProcMapsSnapshot(stage = "proc-maps-after-initialize")
            appendProcMapsDiff(
                before = procMapsBeforeInitialize,
                after = procMapsAfterThrowable,
                reason = "engine-initialize-throwable",
            )
            collectNativeCrashArtifacts(reason = "engine-initialize-throwable")
            appendResultLine("RESULT=FAILED failureClass=engine-initialize")
            writeLastRun(stage = "engine-initialize", result = "FAILED")
            logFailure(stage = "engine-initialize", throwable = throwable)
            throw throwable
        }
    }

    private fun startInitializeWatchdog(
        done: AtomicBoolean,
        startElapsedMs: Long,
        initializeThread: Thread,
        initializeTid: Int,
        initializeThreadCpuStartNs: Long,
        procMapsBeforeInitialize: List<String>,
    ) {
        Thread({
            var previousCpuTicks = readThreadCpuTicks(initializeTid)
            var elapsedMs = SystemClock.elapsedRealtime() - startElapsedMs
            while (!done.get() && elapsedMs < INITIALIZE_TIMEOUT_MS) {
                SystemClock.sleep(INITIALIZE_WATCHDOG_INTERVAL_MS)
                elapsedMs = SystemClock.elapsedRealtime() - startElapsedMs
                if (!done.get()) {
                    val cpuTicks = readThreadCpuTicks(initializeTid)
                    val cpuTickDelta = if (cpuTicks != null && previousCpuTicks != null) {
                        cpuTicks - requireNotNull(previousCpuTicks)
                    } else {
                        null
                    }
                    if (cpuTicks != null) previousCpuTicks = cpuTicks
                    val procState = readThreadProcState(initializeTid)
                    appendResultLine(
                        "stage=engine-initialize-watchdog-alive elapsedMs=$elapsedMs " +
                            "timeoutMs=$INITIALIZE_TIMEOUT_MS initializeThread=${initializeThread.name} " +
                            "initializeThreadState=${initializeThread.state}",
                    )
                    appendResultLine(
                        "ENGINE_INIT_WATCHDOG alive elapsedMs=$elapsedMs " +
                            "initializeThread=${initializeThread.name} initializeThreadState=${initializeThread.state} " +
                            "initializeTid=$initializeTid procState=${procState.ifBlank { "unavailable" }} " +
                            "threadCpuTicks=${cpuTicks ?: "unavailable"} threadCpuTickDelta=${cpuTickDelta ?: "unavailable"} " +
                            "watchdogThreadCpuTimeNanos=${Debug.threadCpuTimeNanos()} " +
                            "initializeThreadCpuStartNanos=$initializeThreadCpuStartNs " +
                            "javaStackTop=${initializeThread.stackTrace.firstOrNull()?.toString().orEmpty()}",
                    )
                    appendResultLine(
                        "stage=engine-initialize-thread-heartbeat elapsedMs=$elapsedMs " +
                            "thread=${initializeThread.name} state=${initializeThread.state}",
                    )
                    writeLastRun(stage = "engine-initialize-watchdog-alive", result = null)
                }
            }
            if (!done.get()) {
                appendResultLine(
                    "stage=engine-initialize-watchdog-timeout elapsedMs=$elapsedMs " +
                        "result=UNKNOWN_CRASH_OR_HANG",
                )
                appendResultLine("RESULT=UNKNOWN_CRASH_OR_HANG stage=engine-initialize-watchdog-timeout")
                appendResultLine(
                    "ENGINE_INIT_WATCHDOG timeout elapsedMs=$elapsedMs " +
                        "initializeThread=${initializeThread.name} initializeThreadState=${initializeThread.state} " +
                        "initializeTid=$initializeTid procState=${readThreadProcState(initializeTid).ifBlank { "unavailable" }} " +
                        "threadCpuTicks=${readThreadCpuTicks(initializeTid) ?: "unavailable"} " +
                        "watchdogThreadCpuTimeNanos=${Debug.threadCpuTimeNanos()}",
                )
                appendProcDiagnostics(reason = "engine-initialize-watchdog-timeout")
                val procMapsWatchdogTimeout = appendProcMapsSnapshot(stage = "proc-maps-watchdog-timeout")
                appendProcMapsDiff(
                    before = procMapsBeforeInitialize,
                    after = procMapsWatchdogTimeout,
                    reason = "engine-initialize-watchdog-timeout",
                )
                collectNativeCrashArtifacts(reason = "engine-initialize-watchdog-timeout")
                appendThreadStackDump(stage = "engine-initialize-watchdog-timeout", targetThread = initializeThread)
                writeLastRun(stage = "engine-initialize-watchdog-timeout", result = "UNKNOWN_CRASH_OR_HANG")
            }
        }, "QairtNpuProbeInitWatchdog").apply {
            isDaemon = true
            start()
        }
    }

    private fun logFileStatus(file: File) {
        val status = "file=${file.absolutePath} exists=${file.exists()} canRead=${file.canRead()} " +
            "canExecute=${file.canExecute()} size=${file.length()}"
        logInfo(status)
        require(file.exists()) { "missing file: ${file.absolutePath}" }
        require(file.canRead()) { "unreadable file: ${file.absolutePath}" }
    }

    private fun resolveModelFile(modelPathOverride: String): File {
        return if (modelPathOverride.isBlank()) MODEL_FILE else File(modelPathOverride)
    }

    private fun logModelStatus(modelPathOverride: String, modelFile: File) {
        logInfo(
            "modelPathOverride=${modelPathOverride.ifBlank { "none" }} " +
                "resolvedModelPath=${modelFile.absolutePath} exists=${modelFile.exists()} " +
                "canRead=${modelFile.canRead()} size=${modelFile.length()}",
        )
        require(modelFile.exists()) { "missing model file: ${modelFile.absolutePath}" }
        require(modelFile.canRead()) { "unreadable model file: ${modelFile.absolutePath}" }
    }

    private fun readModelHeader(modelFile: File) {
        FileInputStream(modelFile).use { input ->
            val buffer = ByteArray(4096)
            val read = input.read(buffer)
            logInfo("model-read bytes=$read")
            require(read > 0) { "model read returned $read bytes" }
        }
    }

    private fun logModelTensorSignature(modelFile: File) {
        val scanStartEpochMs = System.currentTimeMillis()
        val scanStartElapsedMs = SystemClock.elapsedRealtime()
        appendResultLine(
            "MODEL_TENSOR_SIGNATURE_SCAN_START timestamp=$scanStartEpochMs " +
                "elapsedRealtime=$scanStartElapsedMs path=${modelFile.absolutePath} " +
                "headBytes=$MODEL_SIGNATURE_REGION_BYTES tailBytes=$MODEL_SIGNATURE_REGION_BYTES " +
                "timeoutMs=$MODEL_SCAN_TIMEOUT_MS maxStringLength=$MODEL_SCAN_MAX_STRING_LENGTH " +
                "maxExtractedStrings=$MAX_MODEL_SCAN_EXTRACTED_STRINGS",
        )
        val strings = collectFilteredBinaryStringsFromModelEdges(
            file = modelFile,
            maxMatches = MAX_MODEL_SCAN_EXTRACTED_STRINGS,
        ) { text ->
            val lower = text.lowercase()
            TENSOR_SIGNATURE_MARKERS.any(lower::contains)
        }
        val durationMs = SystemClock.elapsedRealtime() - scanStartElapsedMs
        if (durationMs >= MODEL_SCAN_TIMEOUT_MS) {
            appendResultLine(
                "MODEL_TENSOR_SIGNATURE_TIMEOUT durationMs=$durationMs timeoutMs=$MODEL_SCAN_TIMEOUT_MS " +
                    "partialMatches=${strings.size}",
            )
        }
        val prefill = strings.filterContains("prefill")
        val decode = strings.filterContains("decode")
        val outputs = strings.filter { text ->
            val lower = text.lowercase()
            lower.contains("output") || lower.contains("logit") || lower.contains("token")
        }
        val kv = strings.filter { text ->
            val lower = text.lowercase()
            lower.contains("kv") || lower.contains("cache") || lower.contains("key") || lower.contains("value")
        }
        appendResultLine(
            "MODEL_TENSOR_SIGNATURE path=${modelFile.absolutePath} " +
                "scanMode=head-tail headBytes=$MODEL_SIGNATURE_REGION_BYTES tailBytes=$MODEL_SIGNATURE_REGION_BYTES " +
                "matched=${strings.size} prefillCount=${prefill.size} decodeCount=${decode.size} " +
                "outputLikeCount=${outputs.size} kvCacheLikeCount=${kv.size}",
        )
        appendResultLine("MODEL_TENSOR_SIGNATURE prefill=${prefill.joinToString("|").take(MAX_LOG_TEXT)}")
        appendResultLine("MODEL_TENSOR_SIGNATURE decode=${decode.joinToString("|").take(MAX_LOG_TEXT)}")
        appendResultLine("MODEL_TENSOR_SIGNATURE outputs=${outputs.joinToString("|").take(MAX_LOG_TEXT)}")
        appendResultLine("MODEL_TENSOR_SIGNATURE kvCache=${kv.joinToString("|").take(MAX_LOG_TEXT)}")
        appendResultLine(
            "MODEL_TENSOR_SIGNATURE_SCAN_END timestamp=${System.currentTimeMillis()} " +
                "durationMs=$durationMs matched=${strings.size}",
        )
    }

    private fun logModelGraphInfo(modelFile: File) {
        val graphStrings = collectFilteredBinaryStringsFromModelEdges(
            file = modelFile,
            maxMatches = MAX_MODEL_SCAN_EXTRACTED_STRINGS,
        ) { text ->
            val lower = text.lowercase()
            lower.contains("graph") || lower.contains("subgraph") || lower.contains("prefill") || lower.contains("decode")
        }
        appendResultLine(
            "QNN_GRAPH_INFO graphStringCount=${graphStrings.size} " +
                "graphNames=${graphStrings.joinToString("|").take(MAX_LOG_TEXT)}",
        )
    }

    private fun preparePrivateNativeRuntimeDir(): File {
        val privateDir = File(context.codeCacheDir, PRIVATE_NATIVE_RUNTIME_DIR_NAME)
        require(privateDir.mkdirs() || privateDir.isDirectory) {
            "failed to create private native runtime dir: ${privateDir.absolutePath}"
        }
        privateDir.setReadable(true, false)
        privateDir.setExecutable(true, false)
        appendResultLine(
            "PRIVATE_NATIVE_RUNTIME_DIR path=${privateDir.absolutePath} " +
                "exists=${privateDir.exists()} canRead=${privateDir.canRead()} " +
                "canExecute=${privateDir.canExecute()}",
        )

        PRIVATE_NATIVE_RUNTIME_LIBS.forEach { libName ->
            val source = File(QAIRT_STAGE_DIR, libName)
            val target = File(privateDir, libName)
            if (!source.isFile || !source.canRead()) {
                appendResultLine(
                    "PRIVATE_NATIVE_RUNTIME_COPY source=${source.absolutePath} target=${target.absolutePath} " +
                        "result=missing-source sourceExists=${source.exists()} sourceCanRead=${source.canRead()}",
                )
            } else {
                val copied = copyIfNeeded(source = source, target = target)
                target.setReadable(true, false)
                target.setExecutable(true, false)
                appendResultLine(
                    "PRIVATE_NATIVE_RUNTIME_COPY source=${source.absolutePath} target=${target.absolutePath} " +
                        "result=${if (copied) "copied" else "up-to-date"}",
                )
            }
            appendResultLine(
                "PRIVATE_NATIVE_RUNTIME_FILE file=${target.absolutePath} exists=${target.exists()} " +
                    "canRead=${target.canRead()} canExecute=${target.canExecute()} size=${target.length()}",
            )
        }

        return privateDir
    }

    private fun copyIfNeeded(source: File, target: File): Boolean {
        if (target.isFile && target.length() == source.length()) {
            return false
        }
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

    private fun logRuntimeCompatibilityDiagnostics(
        privateNativeRuntimeDir: File?,
        modelFile: File,
        skipModelScan: Boolean,
    ) {
        appendResultLine(
            "RUNTIME_COMPATIBILITY_DIAGNOSTICS_BEGIN " +
                "litertLmAndroidVersion=${BuildConfig.LITERTLM_ANDROID_VERSION} " +
                "enginePackageVersion=${Engine::class.java.`package`?.implementationVersion.orEmpty().ifBlank { "unavailable" }} " +
                "sdkInt=${Build.VERSION.SDK_INT} socManufacturer=${Build.SOC_MANUFACTURER} socModel=${Build.SOC_MODEL}",
        )
        appendResultLine(
            "LITERT_RUNTIME_VERSION source=BuildConfig value=${BuildConfig.LITERTLM_ANDROID_VERSION} " +
                "engineClass=${Engine::class.java.name} backendClass=${Backend::class.java.name}",
        )
        privateNativeRuntimeDir?.let { runtimeDir ->
            QNN_RUNTIME_VERIFICATION_LIBS.forEach { libName ->
                appendRuntimeLibraryVerification(label = libName.removeSuffix(".so"), file = File(runtimeDir, libName))
            }
        } ?: appendResultLine("QNN_RUNTIME_VERIFICATION result=skipped reason=no-private-native-runtime-dir")

        if (skipModelScan) {
            appendResultLine("POSSIBLE_MISMATCH_DETECTOR modelScanSkipped=true result=limited")
        } else {
            val modelVersionStrings = collectFilteredBinaryStringsFromModelEdges(
                file = modelFile,
                maxMatches = 80,
            ) { text ->
                val lower = text.lowercase()
                lower.contains("qnn") ||
                    lower.contains("runtime") ||
                    lower.contains("context") ||
                    lower.contains("graph") ||
                    lower.contains("tensor") ||
                    containsVersionLike(text)
            }
            appendResultLine(
                "POSSIBLE_MISMATCH_DETECTOR modelVersionHints=${modelVersionStrings.joinToString("|").take(MAX_LOG_TEXT)}",
            )
            appendMismatchWarnings(modelVersionStrings)
        }
        appendResultLine("RUNTIME_COMPATIBILITY_DIAGNOSTICS_END")
    }

    private fun appendRuntimeLibraryVerification(label: String, file: File) {
        val stat = runCatching { Os.stat(file.absolutePath) }.getOrNull()
        val inspection = runCatching { QairtElfInspector.inspect(file) }.getOrNull()
        val sha256Prefix = runCatching { sha256Prefix(file = file, prefixChars = SHA256_PREFIX_CHARS) }
            .getOrElse { throwable -> "unavailable:${throwable.javaClass.simpleName}" }
        appendResultLine(
            "QNN_RUNTIME_VERIFICATION label=$label path=${file.absolutePath} " +
                "exists=${file.exists()} canRead=${file.canRead()} size=${file.length()} " +
                "inode=${stat?.st_ino ?: "unavailable"} mode=${stat?.st_mode ?: "unavailable"} " +
                "buildId=${inspection?.buildId.orEmpty().ifBlank { "missing" }} sha256Prefix=$sha256Prefix",
        )
    }

    private fun sha256Prefix(file: File, prefixChars: Int): String {
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
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }.take(prefixChars)
    }

    private fun appendMismatchWarnings(modelVersionStrings: List<String>) {
        val joined = modelVersionStrings.joinToString(separator = " ").lowercase()
        if (BuildConfig.LITERTLM_ANDROID_VERSION < "0.11.0") {
            appendResultLine(
                "WARNING POSSIBLE_MISMATCH type=litert-api-older " +
                    "litertLmAndroidVersion=${BuildConfig.LITERTLM_ANDROID_VERSION} expectedAtLeast=0.11.0",
            )
        }
        if ("2.46" in joined) {
            appendResultLine(
                "WARNING POSSIBLE_MISMATCH type=context-binary-version-observed " +
                    "detail=${modelVersionStrings.joinToString("|").take(MAX_LOG_TEXT)}",
            )
        }
        listOf("unsupported graph", "unsupported tensor", "graph info", "tensor version").forEach { marker ->
            if (marker in joined) {
                appendResultLine("WARNING POSSIBLE_MISMATCH type=model-schema-marker marker=$marker")
            }
        }
        if ("older than current sdk" in joined || "newer than current sdk" in joined) {
            appendResultLine("WARNING POSSIBLE_MISMATCH type=context-binary-sdk-skew")
        }
    }

    private fun logQairtVersionDiagnostics(privateNativeRuntimeDir: File, modelFile: File, skipModelScan: Boolean) {
        appendResultLine("QAIRT_VERSION_DIAGNOSTICS_BEGIN privateDir=${privateNativeRuntimeDir.absolutePath}")
        listOf(
            "libQnnSystem.so" to "QNN_SYSTEM",
            "libQnnHtp.so" to "QNN_HTP",
            "libLiteRtDispatch_Qualcomm.so" to "LITERT_DISPATCH",
        ).forEach { (libName, label) ->
            val file = File(privateNativeRuntimeDir, libName)
            appendResultLine(
                "QAIRT_VERSION_FILE label=$label path=${file.absolutePath} " +
                    "exists=${file.exists()} canRead=${file.canRead()} size=${file.length()}",
            )
            val buildId = runCatching { QairtElfInspector.inspect(file).buildId }.getOrNull().orEmpty().ifBlank { "missing" }
            appendResultLine(
                "QNN_RUNTIME_VERSION label=$label path=${file.absolutePath} size=${file.length()} " +
                    "buildId=$buildId",
            )
            logFilteredBinaryStrings(label = label, file = file, maxBytes = Long.MAX_VALUE)
        }
        if (skipModelScan) {
            appendResultLine("QAIRT_MODEL_VERSION_SCAN_SKIPPED reason=intent-extra-skipModelScan")
            appendResultLine("QNN_RUNTIME_VERSION modelContext result=skipped reason=intent-extra-skipModelScan")
        } else {
            appendResultLine(
                "QAIRT_MODEL_VERSION_SCAN path=${modelFile.absolutePath} size=${modelFile.length()} " +
                    "scanMode=head-tail headBytes=$MODEL_SIGNATURE_REGION_BYTES tailBytes=$MODEL_SIGNATURE_REGION_BYTES",
            )
            val versionStrings = collectFilteredBinaryStringsFromModelEdges(
                file = modelFile,
                maxMatches = 40,
            ) { text ->
                val lower = text.lowercase()
                lower.contains("qnn") || lower.contains("context") || lower.contains("sdk") || containsVersionLike(text)
            }
            versionStrings.forEach { value ->
                appendResultLine("QAIRT_VERSION_STRING label=MODEL_QNN_CONTEXT value=${value.take(MAX_LOG_TEXT)}")
            }
            appendResultLine(
                "QNN_RUNTIME_VERSION modelContext path=${modelFile.absolutePath} " +
                    "versionStrings=${versionStrings.joinToString("|").take(MAX_LOG_TEXT)}",
            )
        }
        appendResultLine("QAIRT_VERSION_DIAGNOSTICS_END")
    }

    private fun logFilteredBinaryStrings(label: String, file: File, maxBytes: Long) {
        if (!file.isFile || !file.canRead()) {
            appendResultLine("QAIRT_VERSION_STRINGS label=$label result=unreadable")
            return
        }
        var scannedBytes = 0L
        var matched = 0
        var truncated = false
        FileInputStream(file).use { input ->
            val current = StringBuilder()
            val buffer = ByteArray(32 * 1024)
            while (scannedBytes < maxBytes) {
                val allowed = minOf(buffer.size.toLong(), maxBytes - scannedBytes).toInt()
                val read = input.read(buffer, 0, allowed)
                if (read <= 0) break
                scannedBytes += read
                for (index in 0 until read) {
                    val value = buffer[index].toInt() and 0xff
                    if (value in 32..126) {
                        if (current.length < MAX_VERSION_STRING_LENGTH) current.append(value.toChar())
                    } else {
                        if (emitVersionStringIfMatched(label = label, value = current.toString())) {
                            matched++
                            if (matched >= MAX_VERSION_STRINGS_PER_FILE) {
                                truncated = true
                                appendResultLine(
                                    "QAIRT_VERSION_STRINGS_TRUNCATED label=$label " +
                                        "shown=$matched scannedBytes=$scannedBytes",
                                )
                                appendResultLine(
                                    "QAIRT_VERSION_STRINGS_SUMMARY label=$label scannedBytes=$scannedBytes matched=$matched truncated=$truncated",
                                )
                                return
                            }
                        }
                        current.clear()
                    }
                }
            }
            if (emitVersionStringIfMatched(label = label, value = current.toString())) matched++
        }
        appendResultLine(
            "QAIRT_VERSION_STRINGS_SUMMARY label=$label scannedBytes=$scannedBytes matched=$matched truncated=$truncated",
        )
    }

    private fun emitVersionStringIfMatched(label: String, value: String): Boolean {
        val text = value.trim()
        if (text.length < 4) return false
        val lower = text.lowercase()
        val matches = VERSION_STRING_MARKERS.any(lower::contains) || VERSION_PATTERN.containsMatchIn(text)
        if (!matches) return false
        appendResultLine("QAIRT_VERSION_STRING label=$label value=${text.take(MAX_LOG_TEXT)}")
        return true
    }

    private fun logLiteRtExpectedGraphSignature(file: File) {
        val expectedStrings = collectFilteredBinaryStrings(
            file = file,
            maxBytes = Long.MAX_VALUE,
            maxMatches = MAX_SIGNATURE_STRINGS,
        ) { text ->
            val lower = text.lowercase()
            LITERT_EXPECTED_SIGNATURE_MARKERS.any(lower::contains)
        }
        appendResultLine(
            "LITERT_EXPECTED_GRAPH_SIGNATURE source=${file.absolutePath} " +
                "matched=${expectedStrings.size} values=${expectedStrings.joinToString("|").take(MAX_LOG_TEXT)}",
        )
    }

    private fun collectFilteredBinaryStrings(
        file: File,
        maxBytes: Long,
        maxMatches: Int,
        predicate: (String) -> Boolean,
    ): List<String> {
        if (!file.isFile || !file.canRead()) return emptyList()
        val matches = linkedSetOf<String>()
        var scannedBytes = 0L
        FileInputStream(file).use { input ->
            val current = StringBuilder()
            val buffer = ByteArray(32 * 1024)
            while (scannedBytes < maxBytes && matches.size < maxMatches) {
                val allowed = minOf(buffer.size.toLong(), maxBytes - scannedBytes).toInt()
                val read = input.read(buffer, 0, allowed)
                if (read <= 0) break
                scannedBytes += read
                for (index in 0 until read) {
                    val value = buffer[index].toInt() and 0xff
                    if (value in 32..126) {
                        if (current.length < MAX_VERSION_STRING_LENGTH) current.append(value.toChar())
                    } else {
                        current.addIfMatched(matches = matches, predicate = predicate)
                        if (matches.size >= maxMatches) break
                    }
                }
            }
            current.addIfMatched(matches = matches, predicate = predicate)
        }
        return matches.toList()
    }

    private fun collectFilteredBinaryStringsFromModelEdges(
        file: File,
        maxMatches: Int,
        predicate: (String) -> Boolean,
    ): List<String> {
        if (!file.isFile || !file.canRead()) return emptyList()
        val matches = linkedSetOf<String>()
        val fileSize = file.length()
        val scanStartElapsedMs = SystemClock.elapsedRealtime()
        val deadlineElapsedMs = scanStartElapsedMs + MODEL_SCAN_TIMEOUT_MS
        var lastHeartbeatElapsedMs = scanStartElapsedMs
        appendResultLine(
            "MODEL_SCAN_HEARTBEAT phase=start elapsedMs=0 fileSize=$fileSize " +
                "headBytes=$MODEL_SIGNATURE_REGION_BYTES tailBytes=$MODEL_SIGNATURE_REGION_BYTES",
        )

        fun heartbeat(phase: String, regionScannedBytes: Long) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHeartbeatElapsedMs >= MODEL_SCAN_HEARTBEAT_INTERVAL_MS) {
                lastHeartbeatElapsedMs = now
                appendResultLine(
                    "MODEL_SCAN_HEARTBEAT phase=$phase elapsedMs=${now - scanStartElapsedMs} " +
                        "regionScannedBytes=$regionScannedBytes matches=${matches.size}",
                )
            }
        }

        fun timedOut(): Boolean {
            return SystemClock.elapsedRealtime() >= deadlineElapsedMs
        }

        fun scanRegion(label: String, start: Long, length: Long): Boolean {
            if (length <= 0L || matches.size >= maxMatches || timedOut()) return timedOut()
            appendResultLine(
                "MODEL_SCAN_HEARTBEAT phase=$label-start elapsedMs=${SystemClock.elapsedRealtime() - scanStartElapsedMs} " +
                    "offset=$start length=$length matches=${matches.size}",
            )
            FileInputStream(file).use { input ->
                val channel = input.channel
                channel.position(start)
                val buffer = ByteBuffer.allocate(MODEL_SCAN_BUFFER_BYTES)
                val current = StringBuilder()
                var remaining = length
                var scanned = 0L
                while (remaining > 0L && matches.size < maxMatches && !timedOut()) {
                    buffer.clear()
                    buffer.limit(minOf(buffer.capacity().toLong(), remaining).toInt())
                    val read = channel.read(buffer)
                    if (read <= 0) break
                    remaining -= read.toLong()
                    scanned += read.toLong()
                    buffer.flip()
                    while (buffer.hasRemaining()) {
                        val value = buffer.get().toInt() and 0xff
                        if (value in 32..126) {
                            if (current.length < MODEL_SCAN_MAX_STRING_LENGTH) current.append(value.toChar())
                        } else {
                            current.addIfMatched(matches = matches, predicate = predicate)
                            if (matches.size >= maxMatches) break
                        }
                    }
                    heartbeat(phase = label, regionScannedBytes = scanned)
                }
                current.addIfMatched(matches = matches, predicate = predicate)
                appendResultLine(
                    "MODEL_SCAN_HEARTBEAT phase=$label-end elapsedMs=${SystemClock.elapsedRealtime() - scanStartElapsedMs} " +
                        "regionScannedBytes=$scanned matches=${matches.size} timedOut=${timedOut()}",
                )
            }
            return timedOut()
        }

        val headLength = minOf(MODEL_SIGNATURE_REGION_BYTES, fileSize)
        val headTimedOut = scanRegion(label = "head", start = 0L, length = headLength)
        if (headTimedOut) return matches.toList()

        val tailStart = maxOf(headLength, fileSize - MODEL_SIGNATURE_REGION_BYTES)
        val tailLength = fileSize - tailStart
        scanRegion(label = "tail", start = tailStart, length = tailLength)
        return matches.toList()
    }

    private fun StringBuilder.addIfMatched(
        matches: MutableSet<String>,
        predicate: (String) -> Boolean,
    ) {
        val text = toString().trim()
        if (text.length >= 3 && predicate(text)) {
            matches += text
        }
        clear()
    }

    private fun List<String>.filterContains(token: String): List<String> {
        return filter { it.contains(token, ignoreCase = true) }
    }

    private fun containsVersionLike(text: String): Boolean {
        var digitRun = 0
        var dotCount = 0
        text.forEach { char ->
            when {
                char.isDigit() -> digitRun++
                char == '.' && digitRun > 0 -> dotCount++
                else -> {
                    if (dotCount >= 1 && digitRun >= 2) return true
                    digitRun = 0
                    dotCount = 0
                }
            }
        }
        return dotCount >= 1 && digitRun >= 2
    }

    private fun collectNativeCrashArtifacts(reason: String) {
        appendResultLine("NATIVE_CRASH_COLLECTOR_BEGIN reason=$reason")
        appendCommandForDiagnostics(
            marker = "NATIVE_CRASH_LOGCAT",
            reason = reason,
            command = listOf("logcat", "-d", "-t", LOGCAT_TAIL_LINES.toString()),
            filter = ::isRelevantNativeCrashLine,
        )
        appendCommandForDiagnostics(
            marker = "NATIVE_CRASH_TOMBSTONES",
            reason = reason,
            command = listOf("sh", "-c", "ls -lt /data/tombstones 2>/dev/null | head -20"),
            filter = { true },
        )
        appendCommandForDiagnostics(
            marker = "NATIVE_CRASH_DROPBOX",
            reason = reason,
            command = listOf("sh", "-c", "dumpsys dropbox --print data_app_native_crash 2>/dev/null | tail -80"),
            filter = ::isRelevantNativeCrashLine,
        )
        appendResultLine("NATIVE_CRASH_COLLECTOR_END reason=$reason")
    }

    private fun appendCommandForDiagnostics(
        marker: String,
        reason: String,
        command: List<String>,
        filter: (String) -> Boolean,
    ) {
        val startMs = SystemClock.elapsedRealtime()
        runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                appendResultLine("$marker reason=$reason result=timeout command=${command.joinToString(" ")}")
                return
            }
            val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }
            val lines = output
                .lineSequence()
                .filter(filter)
                .take(MAX_COMMAND_OUTPUT_LINES)
                .toList()
            appendResultLine(
                "$marker reason=$reason result=exit exitCode=${process.exitValue()} " +
                    "durationMs=${SystemClock.elapsedRealtime() - startMs} matchedLines=${lines.size} " +
                    "command=${command.joinToString(" ")}",
            )
            lines.forEachIndexed { index, line ->
                appendResultLine("$marker line=$index ${line.take(MAX_LOG_TEXT)}")
            }
        }.onFailure { throwable ->
            appendResultLine(
                "$marker reason=$reason result=failed command=${command.joinToString(" ")} " +
                    "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
            )
        }
    }

    private fun isRelevantNativeCrashLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("fatal signal") ||
            lower.contains("sigabrt") ||
            lower.contains("abort") ||
            lower.contains("libc") ||
            lower.contains("qnn") ||
            lower.contains("qairt") ||
            lower.contains("litert") ||
            lower.contains("lami") ||
            lower.contains("tombstone") ||
            lower.contains("native crash")
    }

    private fun appendProcMapsSnapshot(stage: String): List<String> {
        appendResultLine("PROC_MAPS_SNAPSHOT_BEGIN stage=$stage command=cat /proc/self/maps")
        val startMs = SystemClock.elapsedRealtime()
        return runCatching {
            val process = Runtime.getRuntime().exec("cat /proc/self/maps")
            val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                appendResultLine(
                    "PROC_MAPS_SNAPSHOT_END stage=$stage result=timeout " +
                        "durationMs=${SystemClock.elapsedRealtime() - startMs}",
                )
                return emptyList()
            }
            val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }
            val errorOutput = process.errorStream.bufferedReader().use { reader -> reader.readText() }
            val lines = output
                .lineSequence()
                .filter(::isRelevantProcMapLine)
                .distinct()
                .toList()
            appendResultLine(
                "PROC_MAPS_SNAPSHOT_END stage=$stage result=exit exitCode=${process.exitValue()} " +
                    "durationMs=${SystemClock.elapsedRealtime() - startMs} matchedLines=${lines.size} " +
                    "stderr=${errorOutput.take(MAX_LOG_TEXT).replace('\n', ' ')}",
            )
            lines.take(MAX_PROC_LINES).forEachIndexed { index, line ->
                appendResultLine("PROC_MAPS_SNAPSHOT stage=$stage line=$index ${line.take(MAX_LOG_TEXT)}")
            }
            if (lines.size > MAX_PROC_LINES) {
                appendResultLine(
                    "PROC_MAPS_SNAPSHOT stage=$stage truncated=true " +
                        "emitted=$MAX_PROC_LINES total=${lines.size}",
                )
            }
            lines
        }.getOrElse { throwable ->
            appendResultLine(
                "PROC_MAPS_SNAPSHOT_END stage=$stage result=failed " +
                    "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
            )
            appendResultLine(
                "PROC_MAPS_SNAPSHOT_STACK stage=$stage " +
                    throwable.stackTraceToString().take(MAX_LOG_TEXT),
            )
            emptyList()
        }
    }

    private fun appendProcMapsDiff(before: List<String>, after: List<String>, reason: String) {
        val beforeKeys = before.map(::procMapDiffKey).toSet()
        val added = after
            .map(::procMapDiffKey)
            .filterNot(beforeKeys::contains)
            .distinct()
        appendResultLine("PROC_MAPS_DIFF reason=$reason addedCount=${added.size}")
        added.take(MAX_PROC_LINES).forEach { value ->
            appendResultLine("PROC_MAPS_DIFF added=${value.take(MAX_LOG_TEXT)}")
        }
    }

    private fun countLoadedQnnMapEntries(lines: List<String>): Int {
        return lines
            .mapNotNull(::procMapPathOrNull)
            .distinct()
            .count()
    }

    private fun isRelevantProcMapLine(line: String): Boolean {
        val lower = line.lowercase()
        return PROC_MAPS_SNAPSHOT_KEYWORDS.any(lower::contains)
    }

    private fun procMapDiffKey(line: String): String {
        return procMapPathOrNull(line) ?: line
    }

    private fun procMapPathOrNull(line: String): String? {
        val fields = line.trim().split(Regex("\\s+"), limit = 6)
        return fields.getOrNull(5)?.takeIf { it.startsWith("/") }
    }

    private fun appendProcDiagnostics(reason: String) {
        appendResultLine("PROC_DIAGNOSTICS_BEGIN reason=$reason")
        appendLoadedLibrariesFromMaps(reason = reason)
        appendProcMaps(reason = reason)
        appendOpenFds(reason = reason)
        appendResultLine("PROC_DIAGNOSTICS_END reason=$reason")
    }

    private fun appendLoadedLibrariesFromMaps(reason: String) {
        val mapsFile = File("/proc/self/maps")
        val lines = runCatching { mapsFile.readLines(Charsets.UTF_8) }.getOrElse { throwable ->
            appendResultLine(
                "PROC_LOADED_QNN_LIBS reason=$reason result=failed " +
                    "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
            )
            return
        }
        val matched = lines
            .filter { line ->
                val lower = line.lowercase()
                lower.contains("libqnn") ||
                    lower.contains("litertdispatch") ||
                    lower.contains("litertlm")
            }
            .distinct()
            .take(MAX_PROC_LINES)
        appendResultLine("PROC_LOADED_QNN_LIBS reason=$reason count=${matched.size}")
        matched.forEachIndexed { index, line ->
            appendResultLine("PROC_LOADED_QNN_LIB line=$index ${line.take(MAX_LOG_TEXT)}")
        }
    }

    private fun appendProcMaps(reason: String) {
        val mapsFile = File("/proc/self/maps")
        runCatching {
            mapsFile.readLines(Charsets.UTF_8)
                .filter { line ->
                    val lower = line.lowercase()
                    lower.contains(".so") ||
                        lower.contains("/data/local/tmp/qairt") ||
                        lower.contains("qairt_native_runtime")
                }
                .take(MAX_PROC_LINES)
        }.onSuccess { lines ->
            appendResultLine("PROC_MAPS reason=$reason filteredLines=${lines.size}")
            lines.forEachIndexed { index, line ->
                appendResultLine("PROC_MAP line=$index ${line.take(MAX_LOG_TEXT)}")
            }
        }.onFailure { throwable ->
            appendResultLine(
                "PROC_MAPS reason=$reason result=failed " +
                    "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
            )
        }
    }

    private fun appendOpenFds(reason: String) {
        val fdDir = File("/proc/self/fd")
        runCatching {
            fdDir.listFiles().orEmpty()
                .sortedBy { file -> file.name.toIntOrNull() ?: Int.MAX_VALUE }
                .take(MAX_FD_LINES)
                .map { fd ->
                    val target = runCatching { Os.readlink(fd.absolutePath) }.getOrElse { "unavailable" }
                    "${fd.name}=$target"
                }
        }.onSuccess { lines ->
            appendResultLine("PROC_OPEN_FD reason=$reason count=${lines.size}")
            lines.forEachIndexed { index, line ->
                appendResultLine("PROC_OPEN_FD line=$index ${line.take(MAX_LOG_TEXT)}")
            }
        }.onFailure { throwable ->
            appendResultLine(
                "PROC_OPEN_FD reason=$reason result=failed " +
                    "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
            )
        }
    }

    private fun readThreadCpuTicks(tid: Int): Long? {
        val stat = readSmallFile("/proc/self/task/$tid/stat") ?: return null
        val endComm = stat.lastIndexOf(')')
        if (endComm < 0 || endComm + 2 >= stat.length) return null
        val fieldsAfterComm = stat.substring(endComm + 2).trim().split(Regex("\\s+"))
        val userTicks = fieldsAfterComm.getOrNull(11)?.toLongOrNull() ?: return null
        val kernelTicks = fieldsAfterComm.getOrNull(12)?.toLongOrNull() ?: return null
        return userTicks + kernelTicks
    }

    private fun readThreadProcState(tid: Int): String {
        val status = readSmallFile("/proc/self/task/$tid/status").orEmpty()
        val state = status.lineSequence().firstOrNull { it.startsWith("State:") }.orEmpty().trim()
        val voluntarySwitches = status.lineSequence()
            .firstOrNull { it.startsWith("voluntary_ctxt_switches:") }
            .orEmpty()
            .trim()
        val nonvoluntarySwitches = status.lineSequence()
            .firstOrNull { it.startsWith("nonvoluntary_ctxt_switches:") }
            .orEmpty()
            .trim()
        val wchan = readSmallFile("/proc/self/task/$tid/wchan").orEmpty().trim()
        return listOf(state, voluntarySwitches, nonvoluntarySwitches, "wchan=$wchan")
            .filter { it.isNotBlank() }
            .joinToString(separator = ";")
            .take(MAX_LOG_TEXT)
    }

    private fun readSmallFile(path: String): String? {
        return runCatching { File(path).readText(Charsets.UTF_8).take(MAX_LOG_TEXT) }.getOrNull()
    }

    private fun appendThreadStackDump(stage: String, targetThread: Thread) {
        val stacks = Thread.getAllStackTraces()
        val targetStack = stacks[targetThread].orEmpty()
        appendResultLine(
            "THREAD_STACK_DUMP stage=$stage targetThread=${targetThread.name} " +
                "targetState=${targetThread.state} frameCount=${targetStack.size}",
        )
        targetStack.take(MAX_STACK_FRAMES).forEachIndexed { index, frame ->
            appendResultLine("THREAD_STACK target=${targetThread.name} frame=$index $frame")
        }
        stacks
            .filterKeys { thread -> thread.name.startsWith("Qairt") || thread.name.contains("LiteRt", ignoreCase = true) }
            .forEach { (thread, stack) ->
                appendResultLine("THREAD_STACK_RELATED thread=${thread.name} state=${thread.state} frameCount=${stack.size}")
                stack.take(MAX_RELATED_STACK_FRAMES).forEachIndexed { index, frame ->
                    appendResultLine("THREAD_STACK_RELATED_FRAME thread=${thread.name} frame=$index $frame")
                }
            }
    }

    private fun logLiteRtLmJniDiagnostics() {
        val appInfo = context.applicationInfo
        val apkFiles = listOfNotNull(appInfo.sourceDir, *appInfo.splitSourceDirs.orEmpty())
            .map(::File)
        val extractedLib = File(appInfo.nativeLibraryDir.orEmpty(), LITERTLM_JNI_LIB_NAME)
        val extractNativeLibs = appInfo.flags and android.content.pm.ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS != 0

        appendResultLine(
            "LITERTLM_JNI_APP_INFO " +
                "nativeLibraryDir=${appInfo.nativeLibraryDir.orEmpty()} " +
                "sourceDir=${appInfo.sourceDir.orEmpty()} " +
                "splitSourceDirs=${appInfo.splitSourceDirs.orEmpty().joinToString(separator = ",").ifBlank { "none" }} " +
                "flags=0x${appInfo.flags.toString(16)} extractNativeLibs=$extractNativeLibs " +
                "extractNativeLibsDebugOverride=true",
        )
        appendResultLine(
            "LITERTLM_JNI_EXTRACTED_PATH path=${extractedLib.absolutePath} " +
                "exists=${extractedLib.exists()} canRead=${extractedLib.canRead()} size=${extractedLib.length()}",
        )
        if (extractedLib.isFile && extractedLib.canRead()) {
            logLiteRtExpectedGraphSignature(extractedLib)
        } else {
            appendResultLine(
                "LITERT_EXPECTED_GRAPH_SIGNATURE source=${extractedLib.absolutePath} " +
                    "result=unavailable reason=extracted-lib-not-readable",
            )
        }
        apkFiles.forEach { apk ->
            appendResultLine(
                "LITERTLM_JNI_APK path=${apk.absolutePath} exists=${apk.exists()} canRead=${apk.canRead()} size=${apk.length()}",
            )
        }

        val apkEntries = findApkNativeEntries(apkFiles)
        if (apkEntries.isEmpty()) {
            appendResultLine("LITERTLM_JNI_APK_ENTRY none")
        } else {
            apkEntries.forEach { entry ->
                appendResultLine(
                    "LITERTLM_JNI_APK_ENTRY apk=${entry.apk.absolutePath} entry=${entry.entryName} " +
                        "size=${entry.size} compressedSize=${entry.compressedSize} method=${entry.method} crc=${entry.crc}",
                )
            }
        }

        val inspection = runCatching {
            if (extractedLib.isFile && extractedLib.canRead()) {
                QairtElfInspector.inspect(extractedLib)
            } else {
                val entry = apkEntries.firstOrNull() ?: error("no $LITERTLM_JNI_LIB_NAME in nativeLibraryDir or APK")
                QairtElfInspector.inspectApkEntry(apk = entry.apk, entryName = entry.entryName)
            }
        }
        inspection.onSuccess(::appendElfInspection)
            .onFailure { throwable ->
                appendResultLine(
                    "LITERTLM_JNI_ELF_INSPECT_FAILED class=${throwable.javaClass.name} " +
                        "message=${throwable.message.orEmpty()}",
                )
            }
    }

    private fun findApkNativeEntries(apkFiles: List<File>): List<ApkNativeEntry> {
        return apkFiles.flatMap { apk ->
            runCatching {
                ZipFile(apk).use { zip ->
                    zip.entries().asSequence()
                        .filter { entry ->
                            !entry.isDirectory &&
                                entry.name.startsWith("lib/") &&
                                entry.name.endsWith("/$LITERTLM_JNI_LIB_NAME")
                        }
                        .map { entry ->
                            ApkNativeEntry(
                                apk = apk,
                                entryName = entry.name,
                                size = entry.size,
                                compressedSize = entry.compressedSize,
                                method = when (entry.method) {
                                    java.util.zip.ZipEntry.STORED -> "stored"
                                    java.util.zip.ZipEntry.DEFLATED -> "deflated"
                                    else -> entry.method.toString()
                                },
                                crc = "0x${entry.crc.toString(16)}",
                            )
                        }
                        .toList()
                }
            }.getOrElse { throwable ->
                appendResultLine(
                    "LITERTLM_JNI_APK_SCAN_FAILED apk=${apk.absolutePath} " +
                        "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
                )
                emptyList()
            }
        }
    }

    private fun appendElfInspection(inspection: QairtElfInspector.ElfInspection) {
        appendResultLine(
            "LITERTLM_JNI_ELF source=${inspection.source} size=${inspection.size} " +
                "BuildId=${inspection.buildId.orEmpty().ifBlank { "missing" }} " +
                "elfClass=${inspection.elfClass} data=${inspection.dataEncoding} " +
                "machine=${inspection.machineName}(${inspection.machine}) entry=${inspection.entry} " +
                "sectionCount=${inspection.sectionCount}",
        )
        appendResultLine(
            "LITERTLM_JNI_SYMBOLS hasDynsym=${inspection.hasDynsym} " +
                "hasSymtab=${inspection.hasSymtab} stripped=${inspection.stripped} " +
                "dynamicSymbolCount=${inspection.dynamicSymbolCount} jniSymbolCount=${inspection.jniSymbols.size}",
        )
        appendResultLine(
            "LITERTLM_JNI_SECTIONS " +
                inspection.sectionNames.joinToString(separator = ",").take(MAX_LOG_TEXT),
        )
        if (inspection.jniSymbols.isEmpty()) {
            appendResultLine("LITERTLM_JNI_EXPORTED_JNI_SYMBOLS none")
        } else {
            inspection.jniSymbols.take(MAX_JNI_SYMBOLS).forEach { symbol ->
                appendResultLine("LITERTLM_JNI_EXPORTED_JNI_SYMBOL $symbol")
            }
            if (inspection.jniSymbols.size > MAX_JNI_SYMBOLS) {
                appendResultLine(
                    "LITERTLM_JNI_EXPORTED_JNI_SYMBOLS_TRUNCATED total=${inspection.jniSymbols.size} shown=$MAX_JNI_SYMBOLS",
                )
            }
        }
    }

    private fun closeBestEffort(label: String, closeable: AutoCloseable?) {
        if (closeable == null) {
            logInfo("close label=$label result=skip-null")
            return
        }
        runCatching {
            closeable.close()
        }.onSuccess {
            logInfo("close label=$label result=success")
        }.onFailure { throwable ->
            logFailure(stage = "close-$label", throwable = throwable)
        }
    }

    fun stage(stage: String) {
        lastStage = stage
        writeLastRun(stage = stage, result = null)
        logInfo("stage=$stage")
    }

    fun marker(stage: String, intent: Intent?, append: Boolean) {
        lastStage = stage
        writeLastRun(stage = stage, result = null)
        val line = "MARKER ${diagnosticPrefix()} stage=$stage intent=${intent.describeForLog()}"
        runCatching {
            writeAppResultLine(append = append, line = line)
        }.onFailure { throwable ->
            Log.e(LOG_TAG, "failed to write app marker stage=$stage", throwable)
        }
        runCatching {
            writeTmpResultLine(append = append, line = line)
        }.onFailure { throwable ->
            writeTmpFailureToApp(stage = stage, throwable = throwable)
            Log.e(LOG_TAG, "failed to write tmp marker stage=$stage", throwable)
        }
        Log.i(LOG_TAG, line)
    }

    fun logFailure(stage: String, throwable: Throwable) {
        val classification = QairtNpuProbeFailureClassifier.classify(throwable = throwable, stage = stage)
        appendResultLine(
            "ERROR ${diagnosticPrefix()} stage=$stage classification=$classification " +
                "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
        )
        throwable.cause?.let { cause ->
            appendResultLine(
                "CAUSE stage=$stage class=${cause.javaClass.name} message=${cause.message.orEmpty()}",
            )
        }
        appendResultLine("STACK stage=$stage ${throwable.stackTraceToString().take(MAX_LOG_TEXT)}")
        Log.e(
            LOG_TAG,
            "stage=$stage classification=$classification " +
                "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
            throwable,
        )
    }

    fun appendResultLine(line: String) {
        runCatching {
            writeAppResultLine(append = true, line = line)
        }.onFailure { throwable ->
            Log.e(
                LOG_TAG,
                "stage=app-result-file-append classification=file permission " +
                    "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
                throwable,
            )
        }
        runCatching {
            writeTmpResultLine(append = true, line = line)
        }.onFailure { throwable ->
            writeTmpFailureToApp(stage = lastStage, throwable = throwable)
            Log.e(
                LOG_TAG,
                "stage=tmp-result-file-append classification=file permission " +
                    "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
                throwable,
            )
        }
    }

    private fun logInfo(message: String) {
        appendResultLine("${diagnosticPrefix()} $message")
        Log.i(LOG_TAG, message)
    }

    private fun writeAppResultLine(append: Boolean, line: String) {
        writeResultLine(file = File(context.filesDir, APP_RESULT_NAME), append = append, line = line)
    }

    private fun writeTmpResultLine(append: Boolean, line: String) {
        if (!tmpResultWritable) return
        TMP_RESULT_FILE.parentFile?.mkdirs()
        writeResultLine(file = TMP_RESULT_FILE, append = append, line = line)
    }

    private fun writeResultLine(file: File, append: Boolean, line: String) {
        FileOutputStream(file, append).use { output ->
            output.write(line.toByteArray(Charsets.UTF_8))
            output.write('\n'.code)
            output.flush()
            output.fd.sync()
        }
    }

    private fun writeLastRun(stage: String, result: String?) {
        runCatching {
            val lines = buildList {
                add("runId=$runId")
                add("pid=${android.os.Process.myPid()}")
                add("uid=${android.os.Process.myUid()}")
                add("processName=${Application.getProcessName()}")
                add("startedAtEpochMs=$runStartedAtEpochMs")
                add("lastUpdatedAtEpochMs=${System.currentTimeMillis()}")
                add("lastStage=$stage")
                if (result != null) add("result=$result")
            }
            writeResultLine(
                file = File(context.filesDir, LAST_RUN_NAME),
                append = false,
                line = lines.joinToString(separator = "\n"),
            )
        }
    }

    private fun writeTmpFailureToApp(stage: String, throwable: Throwable) {
        if (tmpWriteFailureLogged) {
            runCatching {
                writeAppResultLine(
                    append = true,
                    line = "TMP_WRITE_SKIPPED ${diagnosticPrefix()} stage=$stage tmpResultWritable=false",
                )
            }
            return
        }
        tmpWriteFailureLogged = true
        tmpResultWritable = false
        runCatching {
            writeAppResultLine(
                append = true,
                line = "TMP_WRITE_FAILED ${diagnosticPrefix()} stage=$stage " +
                    "tmpResultWritable=false " +
                    "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
            )
            writeAppResultLine(
                append = true,
                line = "TMP_WRITE_STACK stage=$stage ${throwable.stackTraceToString().take(MAX_LOG_TEXT)}",
            )
        }
    }

    private fun diagnosticPrefix(): String {
        return "runId=$runId timestamp=${System.currentTimeMillis()} processName=${Application.getProcessName()} " +
            "uid=${android.os.Process.myUid()} pid=${android.os.Process.myPid()} " +
            "tid=${android.os.Process.myTid()} threadName=${Thread.currentThread().name} " +
            "uptimeMillis=${SystemClock.uptimeMillis()} elapsedRealtime=${SystemClock.elapsedRealtime()}"
    }

    private fun crashAnalysisMetadata(config: EngineConfig, nativeLibraryDir: File?): String {
        return "CRASH_METADATA runId=$runId " +
            "pid=${android.os.Process.myPid()} " +
            "tid=${android.os.Process.myTid()} " +
            "processName=${Application.getProcessName()} " +
            "threadName=${Thread.currentThread().name} " +
            "uid=${android.os.Process.myUid()} " +
            "modelPath=${config.modelPath} " +
            "nativeLibraryDir=${nativeLibraryDir?.absolutePath ?: "none"} " +
            "cacheDir=${context.cacheDir.absolutePath} " +
            "ADSP_LIBRARY_PATH=${System.getenv("ADSP_LIBRARY_PATH").orEmpty()} " +
            "LD_LIBRARY_PATH=${System.getenv("LD_LIBRARY_PATH").orEmpty().ifBlank { "unset" }} " +
            "Build.MODEL=${Build.MODEL} " +
            "Build.MANUFACTURER=${Build.MANUFACTURER} " +
            "Build.HARDWARE=${Build.HARDWARE} " +
            "Build.BOARD=${Build.BOARD} " +
            "Build.SOC_MANUFACTURER=${Build.SOC_MANUFACTURER} " +
            "Build.SOC_MODEL=${Build.SOC_MODEL} " +
            "SDK_INT=${Build.VERSION.SDK_INT} " +
            "SUPPORTED_ABIS=${Build.SUPPORTED_ABIS.joinToString(separator = ",")}"
    }

    private fun installProcessDiagnostics() {
        if (!processDiagnosticsInstalled.compareAndSet(false, true)) return
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                writeAppResultLine(
                    append = true,
                    line = "UNCAUGHT ${diagnosticPrefix()} thread=${thread.name} " +
                        "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
                )
                writeAppResultLine(
                    append = true,
                    line = "UNCAUGHT_STACK ${throwable.stackTraceToString().take(MAX_LOG_TEXT)}",
                )
                writeAppResultLine(append = true, line = "RESULT=FAILED failureClass=uncaught")
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
        runCatching {
            Runtime.getRuntime().addShutdownHook(
                Thread({
                    runCatching {
                        writeAppResultLine(
                            append = true,
                            line = "SHUTDOWN_HOOK ${diagnosticPrefix()} lastStage=$lastStage",
                        )
                    }
                }, "QairtNpuProbeShutdownHook"),
            )
            Log.i(LOG_TAG, "processDiagnostics defaultUncaughtExceptionHandler=installed shutdownHook=installed")
        }.onFailure { throwable ->
            Log.e(
                LOG_TAG,
                "processDiagnostics defaultUncaughtExceptionHandler=installed shutdownHook=unavailable " +
                    "class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
                throwable,
            )
        }
    }

    private fun Intent?.describeForLog(): String {
        if (this == null) return "null"
        val extrasKeys = extras?.keySet()?.sorted().orEmpty()
        return "component=${component?.flattenToShortString().orEmpty()} " +
            "action=${action.orEmpty()} data=${dataString.orEmpty()} flags=$flags extras=$extrasKeys"
    }

    private fun parseBackendMode(intent: Intent?): BackendMode {
        val raw = intent?.getStringExtra(EXTRA_BACKEND_MODE)?.trim().orEmpty()
        return BackendMode.entries.firstOrNull { mode -> mode.value == raw }
            ?: BackendMode.NPU_PRIVATE
    }

    companion object {
        private const val LOG_TAG = "QairtNpuProbe"
        private const val EXTRA_RUN_PROMPT = "runPrompt"
        private const val EXTRA_BACKEND_MODE = "backendMode"
        private const val EXTRA_MODEL_PATH = "modelPath"
        private const val EXTRA_SKIP_MODEL_SCAN = "skipModelScan"
        private const val MAX_LOG_TEXT = 1000
        private const val MAX_JNI_SYMBOLS = 80
        private const val MAX_VERSION_STRINGS_PER_FILE = 80
        private const val MAX_VERSION_STRING_LENGTH = 500
        private const val MAX_STACK_FRAMES = 80
        private const val MAX_RELATED_STACK_FRAMES = 20
        private const val MODEL_VERSION_SCAN_MAX_BYTES = 256L * 1024L * 1024L
        private const val MODEL_SIGNATURE_REGION_BYTES = 16L * 1024L * 1024L
        private const val MODEL_SCAN_TIMEOUT_MS = 5_000L
        private const val MODEL_SCAN_HEARTBEAT_INTERVAL_MS = 1_000L
        private const val MODEL_SCAN_MAX_STRING_LENGTH = 256
        private const val MODEL_SCAN_BUFFER_BYTES = 32 * 1024
        private const val MAX_MODEL_SCAN_EXTRACTED_STRINGS = 2_000
        private const val MAX_SIGNATURE_STRINGS = 120
        private const val LITERTLM_JNI_LIB_NAME = "liblitertlm_jni.so"
        private const val PRIVATE_NATIVE_RUNTIME_DIR_NAME = "qairt_native_runtime"
        private const val LAST_RUN_NAME = "qairt_npu_probe_last_run.txt"
        private const val INITIALIZE_WATCHDOG_INTERVAL_MS = 500L
        private const val INITIALIZE_TIMEOUT_MS = 30_000L
        private const val COMMAND_TIMEOUT_MS = 1_500L
        private const val LOGCAT_TAIL_LINES = 240
        private const val MAX_COMMAND_OUTPUT_LINES = 60
        private const val MAX_PROC_LINES = 120
        private const val MAX_FD_LINES = 80
        private const val SHA256_PREFIX_CHARS = 16
        private val TMP_RESULT_FILE = File("/data/local/tmp/lami_qairt_npu_probe_result.txt")
        private const val APP_RESULT_NAME = "qairt_npu_probe_result.txt"
        private val QAIRT_STAGE_DIR = File("/data/local/tmp/qairt")
        private val MODEL_FILE = File(QAIRT_STAGE_DIR, "model.litertlm")
        private val REQUIRED_FILES = listOf(
            File(QAIRT_STAGE_DIR, "litert_lm_main"),
            File(QAIRT_STAGE_DIR, "libLiteRtDispatch_Qualcomm.so"),
            File(QAIRT_STAGE_DIR, "libQnnHtp.so"),
            File(QAIRT_STAGE_DIR, "libQnnSystem.so"),
            File(QAIRT_STAGE_DIR, "libQnnHtpPrepare.so"),
            File(QAIRT_STAGE_DIR, "libQnnHtpV79Stub.so"),
            File(QAIRT_STAGE_DIR, "libQnnHtpV79Skel.so"),
        )
        private val PRIVATE_NATIVE_RUNTIME_LIBS = listOf(
            "libLiteRtDispatch_Qualcomm.so",
            "libQnnSystem.so",
            "libQnnHtp.so",
            "libQnnHtpPrepare.so",
            "libQnnHtpV79Stub.so",
            "libQnnHtpV79CalculatorStub.so",
            "libQnnHtpV79Skel.so",
            "libCalculator_skel.so",
        )
        private val QNN_RUNTIME_VERIFICATION_LIBS = listOf(
            "libLiteRtDispatch_Qualcomm.so",
            "libQnnSystem.so",
            "libQnnHtp.so",
            "libQnnHtpPrepare.so",
            "libQnnHtpV79Stub.so",
            "libQnnHtpV79CalculatorStub.so",
            "libQnnHtpV79Skel.so",
            "libCalculator_skel.so",
        )
        private val PROC_MAPS_SNAPSHOT_KEYWORDS = listOf(
            "qnn",
            "cdsprpc",
            "rpc",
            "adsprpc",
            "fastrpc",
            "liblitert",
            "htp",
            "stub",
            "skel",
        )

        @Volatile
        var lastStage: String = "not-started"
            private set

        private val processDiagnosticsInstalled = AtomicBoolean(false)
        private val VERSION_PATTERN = Regex("""\b\d+\.\d+(?:\.\d+)?\b""")
        private val VERSION_STRING_MARKERS = listOf(
            "qnn",
            "qairt",
            "sdk",
            "version",
            "context",
            "binary",
            "qualcomm",
            "htp",
            "graph",
        )
        private val TENSOR_SIGNATURE_MARKERS = listOf(
            "prefill",
            "decode",
            "input",
            "output",
            "tensor",
            "token",
            "logit",
            "kv",
            "cache",
            "key",
            "value",
            "mask",
            "position",
            "embedding",
        )
        private val LITERT_EXPECTED_SIGNATURE_MARKERS = listOf(
            "prefill",
            "decode",
            "input",
            "output",
            "tensor",
            "token",
            "logit",
            "kv",
            "cache",
            "graph",
            "subgraph",
            "signature",
        )
    }

    private data class ApkNativeEntry(
        val apk: File,
        val entryName: String,
        val size: Long,
        val compressedSize: Long,
        val method: String,
        val crc: String,
    )

    private enum class BackendMode(val value: String) {
        CPU("cpu"),
        GPU("gpu"),
        NPU_PRIVATE("npu-private"),
    }

    data class PreviousRunSnapshot(
        val runId: String,
        val pid: String,
        val startedAtEpochMs: String,
        val lastUpdatedAtEpochMs: String,
        val lastStage: String,
        val result: String?,
    ) {
        fun classification(): String {
            return when {
                lastStage == "engine-initialize-enter" -> "native-crash-or-abort-likely hard-native-crash-likely"
                lastStage == "engine-initialize-watchdog-timeout" -> "hang-likely"
                lastStage.startsWith("engine-initialize-watchdog") -> "hang-or-crash-after-watchdog-likely"
                else -> "incomplete-unknown"
            }
        }
    }
}
