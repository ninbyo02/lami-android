package io.github.ninbyo02.lami.ui.screens.home

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.ninbyo02.lami.BuildConfig
import java.io.File

class NpuDiagnosticChatActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.CURRENT_FLAVOR == "customBuildExperiment") {
            "NPU Diagnostic Chat is customBuildExperimentDebug-only; currentFlavor=${BuildConfig.CURRENT_FLAVOR}"
        }

        val filesDir = applicationContext.filesDir
        val modelPath = filesDir.resolve("local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm").absolutePath
        val resultFile = filesDir.resolve("qairt244_single_token_smoke_result.txt")
        val nativeDiagFile = filesDir.resolve("qairt244_native_diag.txt")

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
            ),
        )
        content.addSection(
            "Model",
            listOf(
                "path=$modelPath",
                "exists=${File(modelPath).exists()}",
                "maxOutputTokens=1",
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

        content.addView(
            Button(this).apply {
                text = "Run 1-token smoke disabled"
                isEnabled = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            },
        )

        content.addSection("Last result", readKeyValueSummary(resultFile))
        content.addSection("Timing", readTimingSummary(resultFile))
        content.addSection("Native diag", readNativeDiagSummary(nativeDiagFile))
        content.addSection(
            "Safety",
            listOf(
                "customBuildExperimentDebug only",
                "no selectedPath=npu normal route",
                "no ChatScreen inference path change",
                "no high-level generateResponse",
                "no streaming generation",
            ),
        )

        setContentView(
            ScrollView(this).apply {
                addView(content)
            },
        )
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
        addView(
            TextView(context).apply {
                text = lines.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: "-"
                textSize = 13f
                setPadding(0, 0, 0, 8)
            },
        )
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

    private fun parseKeyValues(file: File): Map<String, String> =
        file.readLines()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
}
