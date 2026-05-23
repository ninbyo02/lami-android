package io.github.ninbyo02.lami.ui.screens.home

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.ninbyo02.lami.BuildConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class NpuDiagnosticChatActivity : Activity() {
    private val running = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val latestRunnerArtifactPath =
        "artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.CURRENT_FLAVOR == "customBuildExperiment") {
            "NPU Diagnostic Chat is customBuildExperimentDebug-only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
        }

        val filesDir = applicationContext.filesDir
        val modelPath = filesDir.resolve("local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm").absolutePath
        val resultFile = filesDir.resolve("qairt244_short_multitoken_smoke_result.txt")
        val nativeDiagFile = filesDir.resolve("qairt244_native_diag.txt")
        val latestRunnerSummaryFile = filesDir.resolve("qairt244_diagnostic_runner_summary.txt")
        val promptPreviewStateFile = filesDir.resolve("qairt244_editable_prompt_preview_state.txt")
        val timeoutMs = 30_000L
        val guardedRunAllowed = intent.getBooleanExtra("allowGuardedNpuRun", false)
        val editablePromptPreviewAllowed = intent.getBooleanExtra("allowEditablePromptPreview", false)
        val editablePromptExecutionAllowed = intent.getBooleanExtra("allowEditablePromptExecution", false)
        val editablePromptNativeSupported = Qairt244ShortMultitokenSmoke.supportsEditablePromptExecution()
        val initialPrompt = if (editablePromptPreviewAllowed) {
            intent.getStringExtra("editablePromptInitialValue") ?: "Hi"
        } else {
            "Hi"
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        content.addTitle("NPU Diagnostic Chat")
        content.addSection(
            "Backend",
            listOf(
                "flavor=${BuildConfig.CURRENT_FLAVOR}",
                "applicationId=${BuildConfig.APPLICATION_ID}",
                "backend=NPU diagnostic only",
                "nativeLibraryDir=${applicationInfo.nativeLibraryDir}",
                "normal UI route=disconnected",
                "selectedPath=npu normal route=not used",
            ),
        )
        content.addSection(
            "Model",
            listOf(
                "path=$modelPath",
                "exists=${File(modelPath).exists()}",
                "maxOutputTokens=3",
                "prompt=$initialPrompt",
            ),
        )

        content.addLabel("Prompt")
        val promptPreviewLines = readPromptPreviewSummary(
            prompt = initialPrompt,
            editablePromptPreviewAllowed = editablePromptPreviewAllowed,
            editablePromptExecutionAllowed = editablePromptExecutionAllowed,
            editablePromptNativeSupported = editablePromptNativeSupported,
        )
        writePromptPreviewState(
            file = promptPreviewStateFile,
            prompt = initialPrompt,
            editablePromptPreviewAllowed = editablePromptPreviewAllowed,
            editablePromptExecutionAllowed = editablePromptExecutionAllowed,
            editablePromptNativeSupported = editablePromptNativeSupported,
        )
        val promptPreviewSummary = content.addSectionView("Short prompt input preview", promptPreviewLines)
        val promptInput = EditText(this).apply {
            setText(initialPrompt)
            isEnabled = editablePromptPreviewAllowed
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(NpuDiagnosticPromptValidator.MAX_LENGTH))
            minLines = 1
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        content.addView(promptInput)

        val statusText = TextView(this).apply {
            text = "status=idle"
            textSize = 13f
            setPadding(0, 12, 0, 8)
        }
        val confirmCheck = CheckBox(this).apply {
            text = "DEV confirm isolated 3-token NPU smoke"
            isChecked = false
            isEnabled = guardedRunAllowed
            setPadding(0, 14, 0, 6)
        }
        val runButton = Button(this).apply {
            text = if (guardedRunAllowed) {
                "Run 3-token smoke"
            } else {
                "Run 3-token smoke disabled"
            }
            isEnabled = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        content.addView(confirmCheck)
        content.addView(runButton)
        content.addView(statusText)

        content.addView(
            Button(this).apply {
                text = "Normal ChatScreen NPU route disabled"
                isEnabled = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            },
        )

        val resultSummary = content.addSectionView("Last result", readKeyValueSummary(resultFile))
        val timingSummary = content.addSectionView("Timing", readTimingSummary(resultFile))
        val nativeDiagSummary = content.addSectionView("Native diag", readNativeDiagSummary(nativeDiagFile))
        val runnerSummary = content.addSectionView("Latest runner", readLatestRunnerSummary(latestRunnerSummaryFile))
        val safetySummary = content.addSectionView(
            "Route guards",
            readRouteGuardSummary(
                editablePromptPreviewAllowed,
                editablePromptExecutionAllowed,
                editablePromptNativeSupported,
            ),
        )
        content.addView(
            Button(this).apply {
                text = "Refresh result view"
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setOnClickListener {
                    resultSummary.text = readKeyValueSummary(resultFile).joinToString("\n")
                    timingSummary.text = readTimingSummary(resultFile).joinToString("\n")
                    nativeDiagSummary.text = readNativeDiagSummary(nativeDiagFile).joinToString("\n")
                    runnerSummary.text = readLatestRunnerSummary(latestRunnerSummaryFile).joinToString("\n")
                    safetySummary.text = readRouteGuardSummary(
                        editablePromptPreviewAllowed,
                        editablePromptExecutionAllowed,
                        editablePromptNativeSupported,
                    ).joinToString("\n")
                    statusText.text = listOf(
                        "status=refreshed",
                        "result_file=${resultFile.exists()}",
                        "native_diag=${nativeDiagFile.exists()}",
                        "runner_summary=${latestRunnerSummaryFile.exists()}",
                    ).joinToString(" ")
                }
            },
        )
        confirmCheck.setOnCheckedChangeListener { _, isChecked ->
            runButton.isEnabled = canEnableRunButton(
                guardedRunAllowed = guardedRunAllowed,
                editablePromptExecutionAllowed = editablePromptExecutionAllowed,
                editablePromptNativeSupported = editablePromptNativeSupported,
                devConfirmed = isChecked,
                prompt = promptInput.text?.toString().orEmpty(),
            )
        }
        promptInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    val prompt = s?.toString().orEmpty()
                    promptPreviewSummary.text = readPromptPreviewSummary(
                        prompt = prompt,
                        editablePromptPreviewAllowed = editablePromptPreviewAllowed,
                        editablePromptExecutionAllowed = editablePromptExecutionAllowed,
                        editablePromptNativeSupported = editablePromptNativeSupported,
                    ).joinToString("\n")
                    writePromptPreviewState(
                        file = promptPreviewStateFile,
                        prompt = prompt,
                        editablePromptPreviewAllowed = editablePromptPreviewAllowed,
                        editablePromptExecutionAllowed = editablePromptExecutionAllowed,
                        editablePromptNativeSupported = editablePromptNativeSupported,
                    )
                    runButton.isEnabled = canEnableRunButton(
                        guardedRunAllowed = guardedRunAllowed,
                        editablePromptExecutionAllowed = editablePromptExecutionAllowed,
                        editablePromptNativeSupported = editablePromptNativeSupported,
                        devConfirmed = confirmCheck.isChecked,
                        prompt = prompt,
                    )
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
        if (guardedRunAllowed) {
            runButton.setOnClickListener {
                if (!confirmCheck.isChecked || running.get()) return@setOnClickListener
                if (editablePromptExecutionAllowed) {
                    val validation = NpuDiagnosticPromptValidator.validate(promptInput.text?.toString().orEmpty())
                    if (!editablePromptNativeSupported || !validation.isValid) {
                        statusText.text = listOf(
                            "status=preflight_blocked",
                            "native_editable_prompt_supported=$editablePromptNativeSupported",
                            "reasonCode=${validation.reasonCode}",
                        ).joinToString(" ")
                        return@setOnClickListener
                    }
                }
                val validation = NpuDiagnosticPromptValidator.validate(promptInput.text?.toString().orEmpty())
                val promptSource = if (editablePromptExecutionAllowed) "editable_prompt" else "fixed_hi"
                val prompt = if (editablePromptExecutionAllowed) validation.normalizedPrompt else "Hi"
                startGuardedShortMultitokenSmoke(
                    modelPath = modelPath,
                    resultFile = resultFile,
                    nativeDiagFile = nativeDiagFile,
                    statusText = statusText,
                    resultSummary = resultSummary,
                    timingSummary = timingSummary,
                    nativeDiagSummary = nativeDiagSummary,
                    runButton = runButton,
                    confirmCheck = confirmCheck,
                    timeoutMs = timeoutMs,
                    prompt = prompt,
                    promptSource = promptSource,
                )
            }
        }
        content.addSection(
            "Memory cleanup",
            listOf(
                "warm profile=pass",
                "cold-start force-stop profile=pass",
                "app process retained after force-stop=false",
            ),
        )
        content.addSection(
            "Safety",
            listOf(
                "customBuildExperimentDebug only",
                "DEV confirmation required before run",
                "guarded run requires allowGuardedNpuRun=true intent extra",
                "editable execution requires allowEditablePromptExecution=true intent extra",
                "native editable prompt supported=$editablePromptNativeSupported",
                "running lock prevents double run",
                "timeout=${timeoutMs / 1000}s",
                "no selectedPath=npu normal route",
                "no ChatScreen inference path change",
                "no high-level generateResponse",
                "no streaming generation",
                "Refresh result view does not run NPU",
            ),
        )

        setContentView(
            ScrollView(this).apply {
                addView(content)
            },
        )
    }

    private fun canEnableRunButton(
        guardedRunAllowed: Boolean,
        editablePromptExecutionAllowed: Boolean,
        editablePromptNativeSupported: Boolean,
        devConfirmed: Boolean,
        prompt: String,
    ): Boolean {
        if (!guardedRunAllowed || !devConfirmed || running.get()) return false
        if (!editablePromptExecutionAllowed) return true
        return editablePromptNativeSupported && NpuDiagnosticPromptValidator.validate(prompt).isValid
    }

    private fun startGuardedShortMultitokenSmoke(
        modelPath: String,
        resultFile: File,
        nativeDiagFile: File,
        statusText: TextView,
        resultSummary: TextView,
        timingSummary: TextView,
        nativeDiagSummary: TextView,
        runButton: Button,
        confirmCheck: CheckBox,
        timeoutMs: Long,
        prompt: String,
        promptSource: String,
    ) {
        if (!running.compareAndSet(false, true)) return

        val runId = "diag-chat-${System.currentTimeMillis()}-${UUID.randomUUID()}"
        statusText.text = "status=running runId=$runId"
        runButton.isEnabled = false
        confirmCheck.isEnabled = false
        resultFile.appendText(
            "qairt244_diagnostic_chat_guarded_run_v1 runId=$runId state=started max_output_tokens=3 prompt_source=$promptSource actual_prompt=$prompt\n",
        )

        handler.postDelayed(
            {
                if (running.get()) {
                    resultFile.appendText(
                        "qairt244_diagnostic_chat_guarded_run_v1 runId=$runId state=timeout timeout_ms=$timeoutMs\n",
                    )
                    statusText.text = "status=timeout runId=$runId"
                }
            },
            timeoutMs,
        )

        Thread {
            val start = SystemClock.elapsedRealtime()
            val outcome = runCatching {
                if (promptSource == "editable_prompt") {
                    Qairt244ShortMultitokenSmoke.runEditablePrompt(
                        context = applicationContext,
                        modelPath = modelPath,
                        runId = runId,
                        prompt = prompt,
                    )
                } else {
                    Qairt244ShortMultitokenSmoke.run(
                        context = applicationContext,
                        modelPath = modelPath,
                        runId = runId,
                    )
                }
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            outcome.onSuccess { result ->
                resultFile.appendText(
                    "qairt244_diagnostic_chat_guarded_run_v1 runId=$runId state=success elapsed_ms=$elapsed result=$result\n",
                )
            }.onFailure { throwable ->
                resultFile.appendText(
                    "qairt244_diagnostic_chat_guarded_run_v1 runId=$runId state=failure elapsed_ms=$elapsed class=${throwable.javaClass.name} message=${throwable.message ?: "-"}\n",
                )
            }
            handler.post {
                running.set(false)
                confirmCheck.isEnabled = true
                confirmCheck.isChecked = false
                runButton.isEnabled = false
                statusText.text = listOf(
                    "status=finished",
                    "runId=$runId",
                    "elapsed_ms=$elapsed",
                    "result_file=${resultFile.exists()}",
                    "native_diag=${nativeDiagFile.exists()}",
                ).joinToString(" ")
                resultSummary.text = readKeyValueSummary(resultFile).joinToString("\n")
                timingSummary.text = readTimingSummary(resultFile).joinToString("\n")
                nativeDiagSummary.text = readNativeDiagSummary(nativeDiagFile).joinToString("\n")
            }
        }.start()
    }

    private fun LinearLayout.addTitle(text: String) {
        addView(
            TextView(context).apply {
                this.text = text
                textSize = 24f
                gravity = Gravity.START
                setPadding(0, 0, 0, 20)
            },
        )
    }

    private fun LinearLayout.addLabel(text: String) {
        addView(
            TextView(context).apply {
                this.text = text
                textSize = 14f
                setPadding(0, 18, 0, 6)
            },
        )
    }

    private fun LinearLayout.addSection(title: String, lines: List<String>) {
        addLabel(title)
        addView(sectionTextView(lines))
    }

    private fun LinearLayout.addSectionView(title: String, lines: List<String>): TextView {
        addLabel(title)
        return sectionTextView(lines).also(::addView)
    }

    private fun LinearLayout.sectionTextView(lines: List<String>): TextView =
        TextView(context).apply {
            text = lines.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: "-"
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }

    private fun readKeyValueSummary(file: File): List<String> {
        if (!file.isFile) return listOf("file=${file.absolutePath}", "status=missing")
        val values = parseKeyValues(file)
        return listOf(
            "file=${file.absolutePath}",
            "result=${values["result"] ?: "unknown"}",
            "output=${values["output"] ?: "unknown"}",
            "elapsed_ms=${values["elapsed_ms"] ?: "unknown"}",
            "npu_backend=${values["npu_backend"] ?: "unknown"}",
        )
    }

    private fun readTimingSummary(file: File): List<String> {
        if (!file.isFile) return listOf("status=missing")
        val values = parseKeyValues(file)
        return listOf(
            "model_assets_elapsed_ms=${values["model_assets_elapsed_ms"] ?: "unknown"}",
            "engine_settings_elapsed_ms=${values["engine_settings_elapsed_ms"] ?: "unknown"}",
            "engine_create_elapsed_ms=${values["engine_create_elapsed_ms"] ?: "unknown"}",
            "session_create_elapsed_ms=${values["session_create_elapsed_ms"] ?: "unknown"}",
            "prefill_elapsed_ms=${values["prefill_elapsed_ms"] ?: "unknown"}",
            "decode_elapsed_ms=${values["decode_elapsed_ms"] ?: "unknown"}",
            "cleanup_elapsed_ms=${values["cleanup_elapsed_ms"] ?: "unknown"}",
        )
    }

    private fun readNativeDiagSummary(file: File): List<String> {
        if (!file.isFile) return listOf("file=${file.absolutePath}", "status=missing")
        val text = file.readText()
        return listOf(
            "file=${file.absolutePath}",
            "QNN=${text.contains("qairt244_qnn_provider_trace_v1")}",
            "HTP=${text.contains("qairt244_htp_backend_trace_v1")}",
            "V79Stub=${text.contains("First connection to QNN stub established")}",
            "FastRPC=${text.contains("transport run [status = 0]")}",
            "RunDecode=${text.contains("RunDecode")}",
        )
    }

    private fun readLatestRunnerSummary(file: File): List<String> {
        val values = if (file.isFile) parseKeyValues(file) else latestCommittedRunnerValues()
        fun value(vararg keys: String, default: String): String =
            keys.firstNotNullOfOrNull(values::get) ?: default
        return listOf(
            "artifact=${value("latest_artifact", "artifact", default = latestRunnerArtifactPath)}",
            "source=${if (file.isFile) "app_private_file" else "committed_latest_verification"}",
            "synced_at=${value("synced_at_local", default = "unknown")}",
            "source_artifact=${value("source_artifact", "latest_artifact", "artifact", default = latestRunnerArtifactPath)}",
            "source_artifact_timestamp=${value("source_artifact_timestamp", default = "unknown")}",
            "source_artifact_age=${value("source_artifact_age_human", default = "unknown")}",
            "source_artifact_age_seconds=${value("source_artifact_age_seconds", default = "unknown")}",
            "freshness_status=${value("freshness_status", default = "unknown")}",
            "freshness_warning=${value("freshness_warning", default = "unknown")}",
            "run1_result=${value("run1_result", default = "unknown")}",
            "run1_output=${value("run1_output", default = "unknown")}",
            "run1_elapsed_ms=${value("run1_elapsed_ms", default = "unknown")}",
            "run1_decode_elapsed_ms=${value("run1_decode_elapsed_ms", default = "unknown")}",
            "run1_last_guard_marker_state=${value("run1_last_guard_marker_state", "final_guard_state", default = "unknown")}",
            "run1_state_started_final=${value("run1_state_started_final", "state_started_final", default = "unknown")}",
            "run2_result=${value("run2_result", default = "unknown")}",
            "run2_output=${value("run2_output", default = "unknown")}",
            "run2_elapsed_ms=${value("run2_elapsed_ms", default = "unknown")}",
            "run2_decode_elapsed_ms=${value("run2_decode_elapsed_ms", default = "unknown")}",
            "run2_last_guard_marker_state=${value("run2_last_guard_marker_state", "final_guard_state", default = "unknown")}",
            "run2_state_started_final=${value("run2_state_started_final", "state_started_final", default = "unknown")}",
            "final_guard_state=${value("final_guard_state", "run2_last_guard_marker_state", "run1_last_guard_marker_state", default = "unknown")}",
            "state_started_final=${value("state_started_final", "run2_state_started_final", "run1_state_started_final", default = "unknown")}",
            "after_10s_total_pss_kb=${value("after_10s_total_pss_kb", default = "unknown")}",
            "after_10s_native_heap_pss_kb=${value("after_10s_native_heap_pss_kb", "after_10s_native_heap_kb", default = "unknown")}",
            "tombstone=${value("tombstone", "tombstone_classification", default = "stale-tombstone-ignored")}",
            "fresh_crash=${value("fresh_crash", default = "false")}",
        )
    }

    private fun readRouteGuardSummary(
        editablePromptPreviewAllowed: Boolean,
        editablePromptExecutionAllowed: Boolean,
        editablePromptNativeSupported: Boolean,
    ): List<String> =
        listOf(
            "normal ChatScreen route disabled=true",
            "selectedPath=npu disabled=true",
            "high-level generateResponse=false",
            "streaming=false",
            "refresh_runs_npu=false",
            "prompt_input_execution=disabled",
            "editable_prompt_preview=$editablePromptPreviewAllowed",
            "editable_prompt_execution_extra=$editablePromptExecutionAllowed",
            "native_editable_prompt_supported=$editablePromptNativeSupported",
            "editable_prompt_phase=${if (editablePromptExecutionAllowed) "preflight_blocked_native_fixed_hi" else "preview_only"}",
            "run_button_uses_fixed_prompt=${if (editablePromptExecutionAllowed) "preflight_blocked" else "Hi"}",
            "guarded_run_intent_extra_required=true",
            "editable_prompt_execution_intent_extra_required=true",
            "run_button_requires_dev_checkbox=true",
        )

    private fun readPromptPreviewSummary(
        prompt: String,
        editablePromptPreviewAllowed: Boolean,
        editablePromptExecutionAllowed: Boolean,
        editablePromptNativeSupported: Boolean,
    ): List<String> {
        val result = NpuDiagnosticPromptValidator.validate(prompt)
        val promptExecutionConnected = editablePromptExecutionAllowed && editablePromptNativeSupported && result.isValid
        return listOf(
            "label=Diagnostic prompt preview",
            "value=$prompt",
            "input_enabled=$editablePromptPreviewAllowed",
            "preview_only=true",
            "isValid=${result.isValid}",
            "reasonCode=${result.reasonCode}",
            "normalizedPrompt=${result.normalizedPrompt}",
            "message=${result.message}",
            "editable_prompt_preview=$editablePromptPreviewAllowed",
            "editable_prompt_execution_extra=$editablePromptExecutionAllowed",
            "native_editable_prompt_supported=$editablePromptNativeSupported",
            "prompt_execution_connected=$promptExecutionConnected",
            "prompt_source=${if (promptExecutionConnected) "editable_prompt" else "fixed_hi"}",
            "run_button_uses_fixed_prompt=${if (promptExecutionConnected) "false" else "Hi"}",
            "run_button_connected=$promptExecutionConnected",
            "maxOutputTokens=3",
            "npu_generation=false",
        )
    }

    private fun writePromptPreviewState(
        file: File,
        prompt: String,
        editablePromptPreviewAllowed: Boolean,
        editablePromptExecutionAllowed: Boolean,
        editablePromptNativeSupported: Boolean,
    ) {
        val result = NpuDiagnosticPromptValidator.validate(prompt)
        val promptExecutionConnected = editablePromptExecutionAllowed && editablePromptNativeSupported && result.isValid
        file.writeText(
            listOf(
                "qairt244_editable_prompt_preview_v1",
                "input_enabled=$editablePromptPreviewAllowed",
                "editable_prompt_preview=$editablePromptPreviewAllowed",
                "editable_prompt_execution_extra=$editablePromptExecutionAllowed",
                "native_editable_prompt_supported=$editablePromptNativeSupported",
                "value=$prompt",
                "isValid=${result.isValid}",
                "reasonCode=${result.reasonCode}",
                "normalizedPrompt=${result.normalizedPrompt}",
                "message=${result.message}",
                "prompt_execution_connected=$promptExecutionConnected",
                "prompt_source=${if (promptExecutionConnected) "editable_prompt" else "fixed_hi"}",
                "run_button_uses_fixed_prompt=${if (promptExecutionConnected) "false" else "Hi"}",
                "run_button_connected=$promptExecutionConnected",
                "max_output_tokens=3",
                "npu_generation=false",
                "engine_initialize=false",
                "run_decode=false",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun latestCommittedRunnerValues(): Map<String, String> =
        mapOf(
            "artifact" to latestRunnerArtifactPath,
            "latest_artifact" to latestRunnerArtifactPath,
            "source_artifact" to latestRunnerArtifactPath,
            "source_artifact_timestamp" to "20260523_114243",
            "source_artifact_age_seconds" to "unknown",
            "source_artifact_age_human" to "unknown",
            "freshness_status" to "unknown",
            "freshness_warning" to "sync script has not populated freshness metadata",
            "synced_at_local" to "unknown",
            "run1_result" to "success",
            "run1_output" to "! How Hi",
            "run1_elapsed_ms" to "1907",
            "run1_decode_elapsed_ms" to "96",
            "run1_last_guard_marker_state" to "success",
            "run1_state_started_final" to "false",
            "run2_result" to "success",
            "run2_output" to "! How Hi",
            "run2_elapsed_ms" to "1661",
            "run2_decode_elapsed_ms" to "70",
            "run2_last_guard_marker_state" to "success",
            "run2_state_started_final" to "false",
            "final_guard_state" to "success",
            "state_started_final" to "false",
            "after_10s_total_pss_kb" to "78536",
            "after_10s_native_heap_pss_kb" to "20571",
            "after_10s_native_heap_kb" to "20571",
            "tombstone" to "stale-tombstone-ignored",
            "tombstone_classification" to "stale-tombstone-ignored",
            "fresh_crash" to "false",
        )

    private fun parseKeyValues(file: File): Map<String, String> =
        file.readLines()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
}
