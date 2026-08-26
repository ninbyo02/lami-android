package io.github.ninbyo02.lami.npu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticPromptValidator
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRoutePersistentProbeRunner
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1Contract
import io.github.ninbyo02.lami.ui.screens.home.Qairt244ShortMultitokenSmoke
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking

class DevOnlyNpuOneTurnConversationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        setResultCode(DevOnlyNpuOneTurnConversationContract.RECEIVER_RESULT_CODE_RECEIVED)
        val appContext = context.applicationContext
        val resultFile = File(
            appContext.filesDir,
            DevOnlyNpuOneTurnConversationContract.RECEIVER_RESULT_FILE_NAME,
        )
        try {
            handleReceive(
                appContext = appContext,
                intent = intent,
                resultFile = resultFile,
            )
        } catch (throwable: Throwable) {
            writeFailure(
                resultFile = resultFile,
                reason = "receiver_on_receive_failure:${throwable.javaClass.simpleName}",
                message = throwable.message.orEmpty(),
            )
        }
    }

    private fun handleReceive(
        appContext: Context,
        intent: Intent,
        resultFile: File,
    ) {
        val action = intent.action.orEmpty()
        val userPromptPresent = intent.hasExtra(DevOnlyNpuOneTurnConversationContract.EXTRA_USER_PROMPT)
        val receiverSequence = sequence.incrementAndGet()
        val receiverRunId = "${System.currentTimeMillis()}-$receiverSequence"
        val receivedAtElapsedMs = SystemClock.elapsedRealtime()
        writeProgress(
            resultFile = resultFile,
            status = "received",
            action = action,
            packageName = appContext.packageName,
            className = javaClass.name,
            userPromptPresent = userPromptPresent,
        )
        appendReceiverLifecycle(
            resultFile = resultFile,
            receiverRunId = receiverRunId,
            receiverSequence = receiverSequence,
            lifecycle = "received",
            receivedAtElapsedMs = receivedAtElapsedMs,
            runningGuardBefore = running.get(),
        )
        if (action != DevOnlyNpuOneTurnConversationContract.RECEIVER_ACTION) {
            writeProgress(
                resultFile = resultFile,
                status = "ignored_action",
                action = action,
                packageName = appContext.packageName,
                className = javaClass.name,
                userPromptPresent = userPromptPresent,
            )
            appendReceiverLifecycle(
                resultFile = resultFile,
                receiverRunId = receiverRunId,
                receiverSequence = receiverSequence,
                lifecycle = "ignored_action",
                receivedAtElapsedMs = receivedAtElapsedMs,
                runningGuardBefore = running.get(),
            )
            return
        }
        val runningGuardBeforeCompare = running.get()
        if (!running.compareAndSet(false, true)) {
            writeFailure(
                resultFile = resultFile,
                reason = "already_running",
                message = "dev-only one-turn conversation receiver is already running",
            )
            appendReceiverLifecycle(
                resultFile = resultFile,
                receiverRunId = receiverRunId,
                receiverSequence = receiverSequence,
                lifecycle = "already_running",
                receivedAtElapsedMs = receivedAtElapsedMs,
                runningGuardBefore = runningGuardBeforeCompare,
                runningGuardAfter = running.get(),
            )
            return
        }

        val pendingResult = goAsync()
        Thread({
            val workerStartedAtElapsedMs = SystemClock.elapsedRealtime()
            try {
                writeProgress(
                    resultFile = resultFile,
                    status = "running",
                    action = action,
                    packageName = appContext.packageName,
                    className = javaClass.name,
                    userPromptPresent = userPromptPresent,
                )
                appendReceiverLifecycle(
                    resultFile = resultFile,
                    receiverRunId = receiverRunId,
                    receiverSequence = receiverSequence,
                    lifecycle = "worker_started",
                    receivedAtElapsedMs = receivedAtElapsedMs,
                    workerStartedAtElapsedMs = workerStartedAtElapsedMs,
                    runningGuardBefore = runningGuardBeforeCompare,
                    runningGuardAfter = running.get(),
                )
                val nativeProbeMode = intent.getStringExtra(
                    DevOnlyNpuOneTurnConversationContract.EXTRA_NATIVE_PROBE_MODE,
                ).orEmpty()
                if (nativeProbeMode == DevOnlyNpuOneTurnConversationContract.NATIVE_PROBE_MODE_FULL_20) {
                    resultFile.writeText(
                        runPersistentFull20Probe(
                            appContext = appContext,
                            intent = intent,
                            timestampMs = System.currentTimeMillis(),
                        ),
                    )
                } else {
                    val request = DevOnlyNpuOneTurnConversationRequest(
                    userPrompt = intent.getStringExtra(
                        DevOnlyNpuOneTurnConversationContract.EXTRA_USER_PROMPT,
                    ).orEmpty().ifBlank {
                        DevOnlyNpuOneTurnConversationContract.DEFAULT_USER_PROMPT
                    },
                    contextText = DevOnlyNpuOneTurnConversationContract.decodeContextTransport(
                        encodedContext = intent.getStringExtra(
                            DevOnlyNpuOneTurnConversationContract.EXTRA_CONTEXT_BASE64,
                        ),
                        plainContext = intent.getStringExtra(
                            DevOnlyNpuOneTurnConversationContract.EXTRA_CONTEXT,
                        ),
                    ),
                    unsafeDevBypassPromptLengthGate = intent.getBooleanExtra(
                        DevOnlyNpuOneTurnConversationContract.EXTRA_UNSAFE_DEV_BYPASS_PROMPT_LENGTH_GATE,
                        true,
                    ),
                    maxOutputTokens = DevOnlyNpuOneTurnConversationContract.sanitizeMaxOutputTokens(
                        intent.getIntExtra(
                            DevOnlyNpuOneTurnConversationContract.EXTRA_MAX_OUTPUT_TOKENS,
                            DevOnlyNpuOneTurnConversationContract.DEFAULT_MAX_OUTPUT_TOKENS,
                        ),
                    ),
                    promptTailVariant = DevOnlyNpuOneTurnConversationContract.sanitizePromptTailVariant(
                        intent.getStringExtra(
                            DevOnlyNpuOneTurnConversationContract.EXTRA_PROMPT_TAIL_VARIANT,
                        ),
                    ),
                )
                val display = if (BuildConfig.CURRENT_FLAVOR == "customBuildExperiment") {
                    NpuStandardRoutePersistentProbeRunner.run(
                        context = appContext,
                        request = request,
                    )
                } else {
                    runBlocking {
                        DevOnlyNpuOneTurnConversationEntry(appContext).run(request)
                    }
                }
                    resultFile.writeText(
                        DevOnlyNpuOneTurnConversationContract.receiverResultText(
                            display = display,
                            timestampMs = System.currentTimeMillis(),
                            safety = DevOnlyNpuOneTurnConversationContract
                                .safety(request.promptTailVariant)
                                .copy(
                                    standardRouteConnected = display.status == "success",
                                    backendNpuPersisted = display.status == "success" &&
                                        display.npuEvidence == NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
                                ),
                        ),
                    )
                }
                val workerFinishedAtElapsedMs = SystemClock.elapsedRealtime()
                appendReceiverLifecycle(
                    resultFile = resultFile,
                    receiverRunId = receiverRunId,
                    receiverSequence = receiverSequence,
                    lifecycle = "worker_success",
                    receivedAtElapsedMs = receivedAtElapsedMs,
                    workerStartedAtElapsedMs = workerStartedAtElapsedMs,
                    workerFinishedAtElapsedMs = workerFinishedAtElapsedMs,
                    runningGuardBefore = runningGuardBeforeCompare,
                    runningGuardAfter = running.get(),
                )
            } catch (throwable: Throwable) {
                val workerFinishedAtElapsedMs = SystemClock.elapsedRealtime()
                writeFailure(
                    resultFile = resultFile,
                    reason = "receiver_failure:${throwable.javaClass.simpleName}",
                    message = throwable.message.orEmpty(),
                )
                appendReceiverLifecycle(
                    resultFile = resultFile,
                    receiverRunId = receiverRunId,
                    receiverSequence = receiverSequence,
                    lifecycle = "worker_failure",
                    receivedAtElapsedMs = receivedAtElapsedMs,
                    workerStartedAtElapsedMs = workerStartedAtElapsedMs,
                    workerFinishedAtElapsedMs = workerFinishedAtElapsedMs,
                    runningGuardBefore = runningGuardBeforeCompare,
                    runningGuardAfter = running.get(),
                    failureClass = throwable.javaClass.simpleName,
                )
            } finally {
                val finallyEnteredAtElapsedMs = SystemClock.elapsedRealtime()
                val runningBeforeFinally = running.get()
                running.set(false)
                appendReceiverLifecycle(
                    resultFile = resultFile,
                    receiverRunId = receiverRunId,
                    receiverSequence = receiverSequence,
                    lifecycle = "finally_entered",
                    receivedAtElapsedMs = receivedAtElapsedMs,
                    workerStartedAtElapsedMs = workerStartedAtElapsedMs,
                    workerFinishedAtElapsedMs = finallyEnteredAtElapsedMs,
                    finallyEntered = true,
                    runningGuardBefore = runningBeforeFinally,
                    runningGuardAfter = running.get(),
                    pendingResultFinishCalled = false,
                )
                pendingResult.finish()
                appendReceiverLifecycle(
                    resultFile = resultFile,
                    receiverRunId = receiverRunId,
                    receiverSequence = receiverSequence,
                    lifecycle = "pending_result_finished",
                    receivedAtElapsedMs = receivedAtElapsedMs,
                    workerStartedAtElapsedMs = workerStartedAtElapsedMs,
                    workerFinishedAtElapsedMs = SystemClock.elapsedRealtime(),
                    finallyEntered = true,
                    runningGuardBefore = runningBeforeFinally,
                    runningGuardAfter = running.get(),
                    pendingResultFinishCalled = true,
                )
            }
        }, "DevOnlyNpuOneTurnConversationReceiver").start()
    }

    private fun runPersistentFull20Probe(
        appContext: Context,
        intent: Intent,
        timestampMs: Long,
    ): String {
        val runCount = intent.getIntExtra(
            DevOnlyNpuOneTurnConversationContract.EXTRA_NATIVE_PROBE_RUN_COUNT,
            20,
        ).coerceIn(1, 20)
        val prompt = intent.getStringExtra(
            DevOnlyNpuOneTurnConversationContract.EXTRA_USER_PROMPT,
        ).orEmpty().ifBlank { DevOnlyNpuOneTurnConversationContract.DEFAULT_USER_PROMPT }
        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        val modelPath = modelResolution.path.orEmpty()
        if (modelPath.isBlank()) {
            return persistentFull20ReceiverText(
                timestampMs = timestampMs,
                status = "failure",
                reason = "model_resolution_failed:${modelResolution.reasonCode}",
                runCount = runCount,
                nativeText = "resolved_model_path=\nmodel_resolution_reason=${modelResolution.reasonCode}\n",
            )
        }
        val result = Qairt244ShortMultitokenSmoke.runPersistentProbe(
            context = appContext,
            modelPath = modelPath,
            runId = "dev_receiver_full20_${SystemClock.elapsedRealtime()}",
            prompt = prompt,
            maxOutputTokens = 16,
            runCount = runCount,
            holderKey = listOf(
                appContext.packageName,
                modelPath,
                appContext.applicationInfo.nativeLibraryDir,
                appContext.cacheDir.absolutePath,
                DevOnlyNpuOneTurnConversationContract.NATIVE_PROBE_MODE_FULL_20,
            ).joinToString(separator = "|"),
            nativeProbeMode = DevOnlyNpuOneTurnConversationContract.NATIVE_PROBE_MODE_FULL_20,
            promptValidationMode = NpuDiagnosticPromptValidator.UTF8_INTERNAL_INTENT_MODE,
            unsafeDevBypassPromptLengthGate = true,
        )
        val nativeValues = parseKeyValueLines(result.resultText)
        val nativeStatus = nativeValues["persistent_custom_jni_status"].orEmpty()
        val failureCount = nativeValues["failure_count"]?.toIntOrNull() ?: 1
        val decodeCount = nativeValues["decode_count"]?.toIntOrNull()
            ?: nativeValues["decode_success_count"]?.toIntOrNull()
            ?: 0
        val success = result.throwableClass == "unavailable" &&
            (result.nativeReturn == "completed" || nativeStatus == "completed") &&
            failureCount == 0 &&
            decodeCount >= runCount
        val reason = if (success) {
            "persistent_full_20_success"
        } else {
            result.throwableMessage.takeIf { it != "unavailable" }
                ?: nativeValues["first_failure_reason"]
                ?: nativeValues["persistent_custom_jni_hypothesis_result"]
                ?: "persistent_full_20_failure"
        }
        return persistentFull20ReceiverText(
            timestampMs = timestampMs,
            status = if (success) "success" else "failure",
            reason = reason,
            runCount = runCount,
            nativeText = result.resultText,
            diagText = result.diagText,
            throwableClass = result.throwableClass,
            throwableMessage = result.throwableMessage,
        )
    }

    private fun persistentFull20ReceiverText(
        timestampMs: Long,
        status: String,
        reason: String,
        runCount: Int,
        nativeText: String,
        diagText: String = "",
        throwableClass: String = "unavailable",
        throwableMessage: String = "unavailable",
    ): String {
        val values = parseKeyValueLines(nativeText)
        val success = status == "success"
        val decodeCount = values["decode_count"]?.toIntOrNull()
            ?: values["decode_success_count"]?.toIntOrNull()
            ?: 0
        val backendEvidence = values["npu_backend_evidence"].orEmpty().ifBlank { "QNN_HTP_V79_FastRPC_native_diag" }
        return buildList {
            add("timestamp=$timestampMs")
            add("status=$status")
            add("result=$status")
            add("success=$success")
            add("reason=$reason")
            add("requested_max_output_tokens=16")
            add("effective_max_output_tokens=16")
            add("max_output_tokens=16")
            add("native_max_output_tokens_limit=128")
            add("run_decode_reached=${decodeCount > 0}")
            add("npu_backend_evidence=$backendEvidence")
            add("fallback_used=false")
            add("timeout=false")
            add("fresh_crash=false")
            add("raw_len=0")
            add("sanitized_len=0")
            add("route_type=dev_only_persistent_full_20")
            add("native_probe_mode=${DevOnlyNpuOneTurnConversationContract.NATIVE_PROBE_MODE_FULL_20}")
            add("persistent_full_20_requested_count=$runCount")
            add("persistent_full_20_decode_count=$decodeCount")
            add("native_error_class=$throwableClass")
            add("native_error_message=${flattenValue(throwableMessage)}")
            add("quality_classification=diagnostic_only")
            add("sanitized_output=")
            add("output_first_200_chars=")
            add("native_result_begin")
            add(nativeText.trim())
            add("native_result_end")
            if (diagText.isNotBlank()) {
                add("native_diag_tail=${flattenValue(diagText.takeLast(1200))}")
            }
        }.joinToString(separator = "\n", postfix = "\n")
    }

    private fun parseKeyValueLines(text: String): Map<String, String> = text
        .lineSequence()
        .mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }
        .toMap()

    private fun flattenValue(value: String): String = value
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun appendReceiverLifecycle(
        resultFile: File,
        receiverRunId: String,
        receiverSequence: Long,
        lifecycle: String,
        receivedAtElapsedMs: Long,
        workerStartedAtElapsedMs: Long? = null,
        workerFinishedAtElapsedMs: Long? = null,
        finallyEntered: Boolean = false,
        runningGuardBefore: Boolean? = null,
        runningGuardAfter: Boolean? = null,
        pendingResultFinishCalled: Boolean = false,
        failureClass: String = "unavailable",
    ) {
        val durationMs = if (workerStartedAtElapsedMs != null && workerFinishedAtElapsedMs != null) {
            (workerFinishedAtElapsedMs - workerStartedAtElapsedMs).toString()
        } else {
            "unavailable"
        }
        resultFile.appendText(
            listOf(
                "receiver_run_id=$receiverRunId",
                "receiver_sequence=$receiverSequence",
                "receiver_lifecycle=$lifecycle",
                "receiver_thread_name=${Thread.currentThread().name}",
                "receiver_received_at_elapsed_ms=$receivedAtElapsedMs",
                "receiver_worker_started_at_elapsed_ms=${workerStartedAtElapsedMs?.toString() ?: "unavailable"}",
                "receiver_worker_finished_at_elapsed_ms=${workerFinishedAtElapsedMs?.toString() ?: "unavailable"}",
                "receiver_worker_duration_ms=$durationMs",
                "receiver_finally_entered=$finallyEntered",
                "pending_result_finish_called=$pendingResultFinishCalled",
                "running_guard_before=${runningGuardBefore?.toString() ?: "unavailable"}",
                "running_guard_after=${runningGuardAfter?.toString() ?: "unavailable"}",
                "receiver_failure_class=$failureClass",
            ).joinToString(separator = "\n", prefix = "\n", postfix = "\n"),
        )
    }

    private fun writeProgress(
        resultFile: File,
        status: String,
        action: String,
        packageName: String,
        className: String,
        userPromptPresent: Boolean,
    ) {
        resultFile.writeText(
            DevOnlyNpuOneTurnConversationContract.receiverProgressText(
                status = status,
                action = action,
                packageName = packageName,
                className = className,
                userPromptPresent = userPromptPresent,
                timestampMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun writeFailure(
        resultFile: File,
        reason: String,
        message: String,
    ) {
        resultFile.writeText(
            DevOnlyNpuOneTurnConversationContract.receiverFailureText(
                reason = reason,
                message = message,
                timestampMs = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        private val running = AtomicBoolean(false)
        private val sequence = AtomicLong(0L)
    }
}
