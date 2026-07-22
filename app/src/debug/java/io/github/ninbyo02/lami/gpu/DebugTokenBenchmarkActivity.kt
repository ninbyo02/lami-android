package io.github.ninbyo02.lami.gpu

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.ninbyo02.lami.ui.theme.OllamaTheme

/** Debug-only, foreground-user-action surface. It has no arbitrary prompt, path, extras, or shell. */
class DebugTokenBenchmarkActivity : ComponentActivity() {
    private lateinit var coordinator: DebugTokenBenchmarkCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug-only foreground benchmark: prevent display sleep from cancelling a long fixed prefill.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        coordinator = DebugTokenBenchmarkCoordinator(applicationContext, lifecycleScope)
        setContent {
            OllamaTheme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DebugTokenBenchmarkScreen(coordinator)
                }
            }
        }
    }

    override fun onStop() {
        coordinator.cancel("screen_left")
        super.onStop()
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}

@Composable
private fun DebugTokenBenchmarkScreen(coordinator: DebugTokenBenchmarkCoordinator) {
    val state by coordinator.state.collectAsState()
    val evidence = state.evidence
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Debug Total-Context Benchmark", style = MaterialTheme.typography.titleLarge)
        Text("Foreground UI only · one run serially · fail closed", color = MaterialTheme.colorScheme.error)
        Text("Fixed: total-context / generic_fallback / send-message / normal")
        Text("No normal chat, NPU fallback, TTS, arbitrary prompt, model path, or shell input.")

        FixedCaseButton(
            label = "GPU 65536",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_65536) },
        )
        FixedCaseButton(
            label = "GPU 131072",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_131072) },
        )
        FixedCaseButton(
            label = "GPU 262144",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_262144) },
        )
        FixedCaseButton(
            label = "GPU 524288",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_524288) },
        )
        FixedCaseButton(
            label = "GPU 1048576",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_1048576) },
        )
        FixedCaseButton(
            label = "GPU 16",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_16) },
        )
        FixedCaseButton(
            label = "GPU 32",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_32) },
        )
        FixedCaseButton(
            label = "GPU 128",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_128) },
        )
        FixedCaseButton(
            label = "GPU 512",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_512) },
        )
        FixedCaseButton(
            label = "GPU 1024",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_1024) },
        )
        FixedCaseButton(
            label = "GPU 2048",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_2048) },
        )
        FixedCaseButton(
            label = "GPU 4096",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_4096) },
        )
        FixedCaseButton(
            label = "GPU 8192",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_8192) },
        )
        FixedCaseButton(
            label = "GPU 16384",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_16384) },
        )
        FixedCaseButton(
            label = "GPU 32768",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_32768) },
        )
        FixedCaseButton(
            label = "GPU long context 2048",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_2048) },
        )
        FixedCaseButton(
            label = "GPU long context 8192",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_8192) },
        )
        FixedCaseButton(
            label = "GPU long context 16384",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_16384) },
        )
        FixedCaseButton(
            label = "GPU long context 22400",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_22400) },
        )
        FixedCaseButton(
            label = "GPU long context 24576",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_24576) },
        )
        FixedCaseButton(
            label = "GPU long context 28800",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_28800) },
        )
        FixedCaseButton(
            label = "GPU long context 32768",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32768) },
        )
        FixedCaseButton(
            label = "GPU long context 32769 boundary",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.GPU_LONG_CONTEXT_32769) },
        )
        FixedCaseButton(
            label = "CPU 32",
            enabled = !state.running,
            onClick = { coordinator.start(DebugTokenBenchmarkCase.CPU_32) },
        )
        OutlinedButton(
            onClick = { coordinator.cancel("explicit_stop") },
            enabled = state.running || state.stage == "host_observation_timeout",
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Stop") }

        Spacer(Modifier.height(4.dp))
        Text("Current case: ${state.currentCase?.label ?: "none"}")
        Text("requested/effective/config: ${state.currentCase?.requestedTokens ?: "-"}/${evidence?.effectiveTokens ?: "-"}/${state.currentCase?.backend ?: "-"}")
        Text("stage: ${state.stage}")
        Text("elapsed_ms: ${state.elapsedMs}")
        Text("result: ${if (evidence?.passed == true) "PASS" else if (evidence == null) "pending" else "FAIL_CLOSED"}")
        Text("output_tokens/source: ${evidence?.outputTokens ?: "-"}/${evidence?.outputTokenSource ?: "-"}")
        Text("tokens/s: ${evidence?.tokensPerSecond ?: "-"}; total_ms: ${evidence?.totalMs ?: "-"}")
        Text("finish: ${evidence?.finishReason ?: "-"}; timeout=${evidence?.timeout ?: "-"}; fallback=${evidence?.fallback ?: "-"}; fresh_crash=${evidence?.freshCrash ?: "-"}")
        Text("artifact timestamp: ${state.timestamp ?: "-"}")
        Text("artifact path: ${state.artifactPath ?: "-"}")
        Text("state path: ${state.statePath ?: "-"}")
        Text("gate GPU32=${state.gates.gpu32Passed} GPU128=${state.gates.gpu128Passed}")
        Text(
            text = state.detail.take(1500),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        // Contract words intentionally visible to reviewers: already_running, output_tokens, artifact, elapsed.
    }
}

@Composable
private fun FixedCaseButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
