package io.github.ninbyo02.lami.ui.screens.home

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
        val timeoutMs = 30_000L

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
                "prompt=Hi",
            ),
        )

        content.addLabel("Prompt")
        content.addView(
            EditText(this).apply {
                setText("Hi")
                isEnabled = false
                minLines = 1
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            },
        )

        val statusText = TextView(this).apply {
            text = "status=idle"
            textSize = 13f
            setPadding(0, 12, 0, 8)
        }
        val confirmCheck = CheckBox(this).apply {
            text = "DEV confirm isolated 3-token NPU smoke"
            isChecked = false
            setPadding(0, 14, 0, 6)
        }
        val runButton = Button(this).apply {
            text = "Run 3-token smoke"
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
        val safetySummary = content.addSectionView("Route guards", readRouteGuardSummary())
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
                    safetySummary.text = readRouteGuardSummary().joinToString("\n")
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
            runButton.isEnabled = isChecked && !running.get()
        }
        runButton.setOnClickListener {
            if (!confirmCheck.isChecked || running.get()) return@setOnClickListener
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
            )
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
    ) {
        if (!running.compareAndSet(false, true)) return

        val runId = "diag-chat-${System.currentTimeMillis()}-${UUID.randomUUID()}"
        statusText.text = "status=running runId=$runId"
        runButton.isEnabled = false
        confirmCheck.isEnabled = false
        resultFile.appendText(
            "qairt244_diagnostic_chat_guarded_run_v1 runId=$runId state=started max_output_tokens=3 prompt=Hi\n",
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
                Qairt244ShortMultitokenSmoke.run(
                    context = applicationContext,
                    modelPath = modelPath,
                    runId = runId,
                )
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
                runButton.isEnabled = confirmCheck.isChecked
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

    private fun readRouteGuardSummary(): List<String> =
        listOf(
            "normal ChatScreen route disabled=true",
            "selectedPath=npu disabled=true",
            "high-level generateResponse=false",
            "streaming=false",
            "refresh_runs_npu=false",
            "run_button_requires_dev_checkbox=true",
        )

    private fun latestCommittedRunnerValues(): Map<String, String> =
        mapOf(
            "artifact" to latestRunnerArtifactPath,
            "latest_artifact" to latestRunnerArtifactPath,
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
