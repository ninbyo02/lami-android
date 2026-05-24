package io.github.ninbyo02.lami.npu

import android.content.Context
import android.os.SystemClock
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticPromptValidator
import io.github.ninbyo02.lami.ui.screens.home.Qairt244ShortMultitokenSmoke
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class Qairt244DevOnlyNpuRouteAdapter(
    context: Context,
) : DevOnlyNpuRouteAdapter {
    private val appContext = context.applicationContext
    private val resultFile: File = appContext.filesDir.resolve(RESULT_FILE_NAME)
    private val nativeDiagFile: File = appContext.filesDir.resolve(NATIVE_DIAG_FILE_NAME)
    private val modelResolutionFile: File = appContext.filesDir.resolve(MODEL_RESOLUTION_FILE_NAME)
    private val runGuardFile: File = appContext.filesDir.resolve(RUN_GUARD_FILE_NAME)

    override suspend fun runOnce(
        prompt: String,
        maxOutputTokens: Int,
        timeoutMs: Long,
    ): DevOnlyNpuRouteResult {
        check(BuildConfig.CURRENT_FLAVOR == "customBuildExperiment") {
            "QAIRT DEV-only NPU route adapter is customBuildExperimentDebug-only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
        }

        if (maxOutputTokens != DevOnlyNpuRouteAdapter.DEFAULT_MAX_OUTPUT_TOKENS) {
            return blockedResult(
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = "invalid_max_output_tokens",
            )
        }

        val validation = NpuDiagnosticPromptValidator.validate(prompt)
        if (!validation.isValid) {
            appendRouteMarker(
                "state=invalid_prompt reason=${validation.reasonCode} engine_initialize=false run_decode=false",
            )
            return blockedResult(
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = "invalid_prompt:${validation.reasonCode}",
            )
        }

        val normalizedPrompt = validation.normalizedPrompt
        if (!runGuardFile.createNewFile()) {
            appendRouteMarker(
                "state=duplicate_run_blocked actual_prompt=$normalizedPrompt normalized_prompt=$normalizedPrompt " +
                    "max_output_tokens=3 engine_initialize=false run_decode=false db=false tts=false markdown=false stream=false",
            )
            return blockedResult(
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = "duplicate_run_blocked",
            )
        }
        runGuardFile.writeText("created_at_ms=${System.currentTimeMillis()}\n")

        val modelResolution = Qairt244ModelPathResolver.resolve(appContext)
        writeModelResolution(modelResolution)
        appendRouteMarker(
            "state=model_resolution reason=${modelResolution.reasonCode} " +
                "resolved_model_path=${modelResolution.path ?: "-"} candidate_count=${modelResolution.candidates.size}",
        )
        if (!modelResolution.resolved) {
            appendRouteMarker(
                "state=failure reason=${modelResolution.reasonCode} engine_initialize=false run_decode=false " +
                    "db=false tts=false markdown=false stream=false",
            )
            appendModelFailureResult(
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                resolution = modelResolution,
            )
            return blockedResult(
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                reasonCode = modelResolution.reasonCode,
            )
        }

        val runId = "chat-real-${System.currentTimeMillis()}-${UUID.randomUUID()}"
        appendRouteMarker(
            "runId=$runId state=started actual_prompt=$normalizedPrompt normalized_prompt=$normalizedPrompt " +
                "max_output_tokens=3 resolved_model_path=${modelResolution.path}",
        )

        val start = SystemClock.elapsedRealtime()
        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    Qairt244ShortMultitokenSmoke.runEditablePrompt(
                        context = appContext,
                        modelPath = checkNotNull(modelResolution.path),
                        runId = runId,
                        prompt = normalizedPrompt,
                    )
                }
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            val values = parseResultFile()
            val success = values["result"] == "success"
            val output = values["output"]
            appendRouteMarker(
                "runId=$runId state=${if (success) "success" else "failure"} elapsed_ms=$elapsed " +
                    "result=${values["result"] ?: "unknown"} output=${output ?: "-"} db=false tts=false markdown=false stream=false",
            )
            DevOnlyNpuRouteResult(
                success = success,
                output = output,
                reasonCode = if (success) "success" else "native_result:${values["result"] ?: "unknown"}",
                elapsedMs = values["elapsed_ms"]?.toLongOrNull() ?: elapsed,
                decodeElapsedMs = values["decode_elapsed_ms"]?.toLongOrNull(),
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                backendEvidence = values["npu_backend_evidence"] ?: nativeBackendEvidence(),
                artifactPath = resultFile.absolutePath,
                freshCrash = false,
                timeout = false,
            )
        } catch (timeout: TimeoutCancellationException) {
            val elapsed = SystemClock.elapsedRealtime() - start
            appendRouteMarker(
                "runId=$runId state=timeout elapsed_ms=$elapsed timeout_ms=$timeoutMs db=false tts=false markdown=false stream=false",
            )
            DevOnlyNpuRouteResult(
                success = false,
                output = null,
                reasonCode = "timeout",
                elapsedMs = elapsed,
                decodeElapsedMs = null,
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                backendEvidence = nativeBackendEvidence(),
                artifactPath = resultFile.absolutePath,
                freshCrash = false,
                timeout = true,
            )
        } catch (throwable: Throwable) {
            val elapsed = SystemClock.elapsedRealtime() - start
            appendRouteMarker(
                "runId=$runId state=failure elapsed_ms=$elapsed class=${throwable.javaClass.name} " +
                    "message=${throwable.message ?: "-"} db=false tts=false markdown=false stream=false",
            )
            DevOnlyNpuRouteResult(
                success = false,
                output = null,
                reasonCode = "adapter_failure:${throwable.javaClass.simpleName}",
                elapsedMs = elapsed,
                decodeElapsedMs = null,
                prompt = normalizedPrompt,
                maxOutputTokens = maxOutputTokens,
                backendEvidence = nativeBackendEvidence(),
                artifactPath = resultFile.absolutePath,
                freshCrash = false,
                timeout = false,
            )
        }
    }

    private fun blockedResult(
        prompt: String,
        maxOutputTokens: Int,
        reasonCode: String,
    ): DevOnlyNpuRouteResult =
        DevOnlyNpuRouteResult(
            success = false,
            output = null,
            reasonCode = reasonCode,
            elapsedMs = null,
            decodeElapsedMs = null,
            prompt = prompt,
            maxOutputTokens = maxOutputTokens,
            backendEvidence = null,
            artifactPath = resultFile.absolutePath,
            freshCrash = false,
            timeout = false,
        )

    private fun parseResultFile(): Map<String, String> {
        if (!resultFile.isFile) return emptyMap()
        return resultFile.readLines()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
    }

    private fun nativeBackendEvidence(): String? {
        if (!nativeDiagFile.isFile) return null
        val text = nativeDiagFile.readText()
        val hasQnnHtp = text.contains("QNN", ignoreCase = true) &&
            text.contains("HTP", ignoreCase = true)
        val hasFastRpc = text.contains("FastRPC", ignoreCase = true) ||
            text.contains("transport run [status = 0]", ignoreCase = true)
        val hasV79 = text.contains("V79", ignoreCase = true) ||
            text.contains("QNN stub", ignoreCase = true)
        return if (hasQnnHtp && hasFastRpc && hasV79) {
            "QNN_HTP_V79_FastRPC_native_diag"
        } else {
            null
        }
    }

    private fun appendRouteMarker(message: String) {
        resultFile.appendText("$ROUTE_MARKER $message\n")
    }

    private fun appendModelFailureResult(
        prompt: String,
        maxOutputTokens: Int,
        resolution: Qairt244ModelPathResolver.Resolution,
    ) {
        resultFile.appendText(
            listOf(
                "marker=$ROUTE_MARKER",
                "result=failure",
                "reasonCode=${resolution.reasonCode}",
                "actual_prompt=$prompt",
                "normalized_prompt=$prompt",
                "prompt_source=chat_screen_dev_route",
                "max_output_tokens=$maxOutputTokens",
                "resolved_model_path=${resolution.path ?: ""}",
                "checked_model_path=${resolution.checkedPath ?: ""}",
                "model_candidate_count=${resolution.candidates.size}",
                "checked_exists=${resolution.checkedExists ?: ""}",
                "checked_can_read=${resolution.checkedCanRead ?: ""}",
                "checked_length=${resolution.checkedLength ?: ""}",
                "engine_initialize=no",
                "run_decode=no",
                "db=false",
                "tts=false",
                "markdown=false",
                "streaming=false",
                "selected_path_npu_saved=false",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun writeModelResolution(resolution: Qairt244ModelPathResolver.Resolution) {
        modelResolutionFile.writeText(
            buildString {
                appendLine("reasonCode=${resolution.reasonCode}")
                appendLine("resolved=${resolution.resolved}")
                appendLine("resolved_model_path=${resolution.path ?: ""}")
                appendLine("checked_model_path=${resolution.checkedPath ?: ""}")
                appendLine("candidate_count=${resolution.candidates.size}")
                appendLine("checked_exists=${resolution.checkedExists ?: ""}")
                appendLine("checked_can_read=${resolution.checkedCanRead ?: ""}")
                appendLine("checked_length=${resolution.checkedLength ?: ""}")
                resolution.candidates.forEachIndexed { index, candidate ->
                    appendLine("candidate_$index=$candidate")
                }
                appendLine("saved_to_settings=false")
            },
        )
    }

    companion object {
        const val ROUTE_MARKER = "qairt244_chat_screen_real_npu_adapter_v1"
        private const val RESULT_FILE_NAME = "qairt244_short_multitoken_smoke_result.txt"
        private const val NATIVE_DIAG_FILE_NAME = "qairt244_native_diag.txt"
        private const val MODEL_RESOLUTION_FILE_NAME = "qairt244_chat_screen_model_path_resolution.txt"
        private const val RUN_GUARD_FILE_NAME = "qairt244_chat_screen_real_npu_once_guard.txt"
    }
}
